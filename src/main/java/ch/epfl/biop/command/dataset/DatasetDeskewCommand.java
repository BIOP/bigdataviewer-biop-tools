package ch.epfl.biop.command.dataset;

import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemVisibility;
import org.scijava.command.CommandService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.command.dataset.transform.DatasetTransformAddCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.SourceHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adds a deskew transform to the transform chain of SpimData backed sources.
 * <p>
 * A deskew is needed when the successive planes of a stack have not been acquired along the
 * normal of the image plane - typically in a light sheet acquisition where the sample is
 * scanned along a direction which is tilted with respect to the light sheet plane. The
 * transform maps the stack axis {@code s} of the raw data to
 * {@code sin(angle).s + cos(angle).h}, where {@code h} is the in plane axis along which the
 * successive planes are shifted, and leaves the two other axes untouched. Optionally the
 * data can be flipped along one axis before the deskew, and rotated afterwards so that the
 * deskewed stack axis ends up along Z.
 * <p>
 * For a Zeiss LLS7 dataset (CZI file opened with the Quick Start reader), the correct
 * settings can be filled in with the dedicated button.
 * <p>
 * The transform is computed in physical space, around the origin of each image (unless
 * specified otherwise), and added to the SpimData view registrations of each selected source
 * for all its timepoints, by delegating to {@link DatasetTransformAddCommand}. Sources which
 * are not backed by a SpimData dataset are ignored.
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.DatasetMenu, weight = BdvPgMenus.DatasetW),
                @Menu(label = "Transform Stack", weight = -1),
                @Menu(label = "Dataset - Add Deskew Transform", weight = 2.5)
        },
        description = "Adds a deskew transform to the transform chain of SpimData backed sources")
public class DatasetDeskewCommand implements BdvPlaygroundActionCommand {

    protected static final Logger logger = LoggerFactory.getLogger(DatasetDeskewCommand.class);

    /** The position choice of {@link DatasetTransformAddCommand} which adds the transform in
     * physical space, on top of all the already existing transforms. */
    private static final String ADD_IN_PHYSICAL_SPACE = "Prepend (most recent, applied last)";

    @Parameter(label = "Select source(s)",
            description = "Sources whose SpimData transforms will be modified",
            callback = "updateMessage")
    SourceAndConverter<?>[] sources;

    @Parameter(label = "Stack (scan) axis",
            description = "Axis of the raw data along which the planes are stacked, i.e. the scan direction",
            choices = {"X", "Y", "Z"},
            callback = "updateMessage",
            persist = false)
    String stack_axis = "Z";

    @Parameter(label = "Shear direction axis",
            description = "In plane axis along which the successive planes are shifted",
            choices = {"X", "-X", "Y", "-Y", "Z", "-Z"},
            callback = "updateMessage",
            persist = false)
    String shear_axis = "X";

    @Parameter(label = "Deskew angle (degrees)",
            description = "Angle between the scan direction and the image plane (30 degrees for a Zeiss LLS7)",
            style = "format:0.###",
            callback = "updateMessage",
            persist = false)
    double angle = 30;

    @Parameter(label = "Flip axis (before deskew)",
            description = "Axis flipped before the deskew is applied, if any",
            choices = {"None", "X", "Y", "Z"},
            callback = "updateMessage",
            persist = false)
    String flip_axis = "None";

    @Parameter(label = "Put the stack axis along Z",
            description = "Rotates the data after the deskew so that the deskewed stack axis points along Z",
            callback = "updateMessage",
            persist = false)
    boolean reorient_along_z = false;

    @Parameter(label = "Deskew around the image origin",
            description = "If checked, the deskew is applied around the origin of each image instead of the origin of the physical space",
            persist = false)
    boolean around_image_origin = true;

    @Parameter(label = "Set the parameters for a Zeiss LLS7 dataset",
            description = "Fills in the deskew parameters of a Zeiss Lattice Light Sheet 7 CZI acquisition",
            callback = "setLLS7Parameters")
    Button set_lls7_parameters;

    @Parameter(label = "", visibility = ItemVisibility.MESSAGE, required = false, persist = false)
    String message = "<html>Select the sources to deskew.</html>";

    @Parameter(label = "Transform name",
            description = "Name given to the transform added in the transform chain",
            persist = false)
    String transform_name = "Deskew";

    @Parameter
    CommandService command_service;

    @Override
    public void run() {
        if (sources == null || sources.length == 0) {
            logger.error("No sources selected!");
            return;
        }

        AffineTransform3D deskew;
        try {
            deskew = getDeskewTransform(stack_axis, shear_axis, angle, flip_axis, reorient_along_z);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid deskew parameters: {}", e.getMessage());
            return;
        }

        for (SourceAndConverter<?> source : sources) {
            if (getSpimDataInfo(source) == null) {
                logger.warn("Source '{}' has no associated SpimData, skipping",
                        source.getSpimSource().getName());
                continue;
            }

            int nTimepoints = SourceHelper.getMaxTimepoint(source) + 1;

            // The deskew is applied around the origin of the image, which may differ from one
            // timepoint to the next: timepoints sharing the same transform are grouped together
            Map<String, List<Integer>> matrixToTimepoints = new LinkedHashMap<>();
            for (int tp = 0; tp < nTimepoints; tp++) {
                matrixToTimepoints
                        .computeIfAbsent(toMatrixString(getPhysicalTransform(deskew, source, tp)),
                                key -> new ArrayList<>())
                        .add(tp);
            }

            for (Map.Entry<String, List<Integer>> entry : matrixToTimepoints.entrySet()) {
                try {
                    command_service.run(DatasetTransformAddCommand.class, true,
                            "sources", new SourceAndConverter[]{source},
                            "timepoint_range", toRangeString(entry.getValue()),
                            "position", ADD_IN_PHYSICAL_SPACE,
                            "transform_matrix", entry.getKey(),
                            "transform_name", transform_name).get();
                } catch (Exception e) {
                    logger.error("Could not add the deskew transform to source '{}': {}",
                            source.getSpimSource().getName(), e.getMessage());
                    return;
                }
            }
        }
    }

    // --------------------------------------------------------------- transform computation

    /**
     * Computes the deskew transform, in physical space, around the origin.
     *
     * @param stackAxis axis along which the planes are stacked ("X", "Y" or "Z")
     * @param shearAxis in plane axis along which the planes are shifted, optionally signed
     *                  ("X", "-X", "Y", "-Y", "Z" or "-Z")
     * @param angleDegrees angle between the scan direction and the image plane, in degrees
     * @param flipAxis axis flipped before the deskew ("None", "X", "Y" or "Z")
     * @param reorientAlongZ whether the deskewed stack axis should be rotated along Z
     * @return the deskew transform
     */
    public static AffineTransform3D getDeskewTransform(String stackAxis, String shearAxis,
                                                       double angleDegrees, String flipAxis,
                                                       boolean reorientAlongZ) {
        int stack = axisIndex(stackAxis);
        int shear = axisIndex(shearAxis);
        if (stack == shear) {
            throw new IllegalArgumentException(
                    "The stack axis and the shear direction axis have to be different");
        }
        double theta = angleDegrees / 180.0 * Math.PI;

        // Flip of the raw data, if any
        AffineTransform3D transform = new AffineTransform3D();
        if (!"None".equals(flipAxis)) {
            int flip = axisIndex(flipAxis);
            transform.set(-1, flip, flip);
        }

        // The stack axis is tilted by 'angle' from the image plane, towards the shear axis
        AffineTransform3D deskew = new AffineTransform3D();
        deskew.set(Math.sin(theta), stack, stack);
        deskew.set(axisSign(shearAxis) * Math.cos(theta), shear, stack);
        transform.preConcatenate(deskew);

        // Brings the deskewed stack axis along Z
        if (reorientAlongZ && stack != 2) {
            AffineTransform3D rotation = new AffineTransform3D();
            if (stack == 0) {
                rotation.rotate(1, -Math.PI / 2.0); // X -> Z
            } else {
                rotation.rotate(0, Math.PI / 2.0); // Y -> Z
            }
            transform.preConcatenate(rotation);
        }

        return transform;
    }

    /**
     * Conjugates the deskew transform with the position of the image, so that the deskew is
     * applied around the origin of the image and not around the origin of the physical space.
     */
    private AffineTransform3D getPhysicalTransform(AffineTransform3D deskew,
                                                   SourceAndConverter<?> source, int timepoint) {
        AffineTransform3D transform = deskew.copy();
        if (!around_image_origin) return transform;

        AffineTransform3D sourceTransform = new AffineTransform3D();
        source.getSpimSource().getSourceTransform(timepoint, 0, sourceTransform);

        AffineTransform3D addOffset = new AffineTransform3D();
        addOffset.set(sourceTransform.get(0, 3), 0, 3);
        addOffset.set(sourceTransform.get(1, 3), 1, 3);
        addOffset.set(sourceTransform.get(2, 3), 2, 3);

        transform.concatenate(addOffset.inverse());
        transform.preConcatenate(addOffset);

        return transform;
    }

    private static int axisIndex(String axis) {
        switch (axis.replace("-", "")) {
            case "X": return 0;
            case "Y": return 1;
            case "Z": return 2;
            default: throw new IllegalArgumentException("Unrecognized axis " + axis);
        }
    }

    private static double axisSign(String axis) {
        return axis.startsWith("-") ? -1 : 1;
    }

    /** Formats a transform the way {@link DatasetTransformAddCommand} expects it. */
    static String toMatrixString(AffineTransform3D transform) {
        StringBuilder builder = new StringBuilder();
        for (int row = 0; row < 3; row++) {
            if (row > 0) builder.append("\n");
            for (int col = 0; col < 4; col++) {
                if (col > 0) builder.append(", ");
                builder.append(String.format(Locale.US, "%s", transform.get(row, col)));
            }
        }
        return builder.toString();
    }

    /** Compacts a sorted list of timepoints into a range string, ex: 0:5,8 */
    static String toRangeString(List<Integer> timepoints) {
        StringBuilder builder = new StringBuilder();
        int i = 0;
        while (i < timepoints.size()) {
            int j = i;
            while (j + 1 < timepoints.size() && timepoints.get(j + 1) == timepoints.get(j) + 1) j++;
            if (builder.length() > 0) builder.append(",");
            builder.append(timepoints.get(i));
            if (j > i) builder.append(":").append(timepoints.get(j));
            i = j + 1;
        }
        return builder.toString();
    }

    private static SourceService.SpimDataInfo getSpimDataInfo(SourceAndConverter<?> source) {
        Object info = SourceServices.getSourceService()
                .getMetadata(source, SourceService.SPIM_DATA_INFO);
        return (info instanceof SourceService.SpimDataInfo)
                ? (SourceService.SpimDataInfo) info : null;
    }

    // ------------------------------------------------------------------------- ui callbacks

    /** Sets the deskew parameters of a Zeiss Lattice Light Sheet 7 acquisition. */
    @SuppressWarnings("unused") // Used as a callback
    public void setLLS7Parameters() {
        stack_axis = "Y";
        shear_axis = "-Z";
        angle = 30;
        flip_axis = "Z";
        reorient_along_z = true;
        around_image_origin = true;
        updateMessage();
    }

    @SuppressWarnings("unused") // Used as a callback
    public void updateMessage() {
        if (sources == null || sources.length == 0) {
            message = "<html>Select the sources to deskew.</html>";
            return;
        }

        int nNotSpimData = 0;
        for (SourceAndConverter<?> source : sources) {
            if (getSpimDataInfo(source) == null) nNotSpimData++;
        }

        StringBuilder builder = new StringBuilder("<html>");
        try {
            AffineTransform3D deskew =
                    getDeskewTransform(stack_axis, shear_axis, angle, flip_axis, reorient_along_z);
            builder.append("Deskew transform:<br>");
            for (String row : toMatrixString(deskew).split("\n")) {
                builder.append("&nbsp;").append(row).append("<br>");
            }
        } catch (IllegalArgumentException e) {
            builder.append("Invalid deskew parameters: ").append(e.getMessage()).append("<br>");
        }

        if (nNotSpimData > 0) {
            builder.append("Warning: ").append(nNotSpimData).append(" out of ")
                    .append(sources.length)
                    .append(" selected source(s) are not backed by a dataset and will be ignored.");
        }

        message = builder.append("</html>").toString();
    }
}
