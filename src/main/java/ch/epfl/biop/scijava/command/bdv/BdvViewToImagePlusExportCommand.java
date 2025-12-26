package ch.epfl.biop.scijava.command.bdv;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusSampler;
import ij.ImagePlus;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Export sources as an ImagePlus object according to the orientation
 * of a bdv window.
 *
 * Many options are available for a full control of the way the export from bdv source to
 * a resliced ImagePlus can be performed.
 *
 * See {@link BasicBdvViewToImagePlusExportCommand} for a more simpler command with
 * reasonable estimated default values
 *
 * Stack is virtual and cached by default
 */

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Current BDV View To ImagePlus",
        description = "Exports sources as ImagePlus with full control over sampling and region")
public class BdvViewToImagePlusExportCommand implements BdvPlaygroundActionCommand {

    private static final Logger logger = LoggerFactory.getLogger(BdvViewToImagePlusExportCommand.class);

    @Parameter(label = "BDV Window",
            description = "The BigDataViewer window to export from")
    public BdvHandle bdv_h;

    @Parameter(label = "Capture Name",
            description = "Name for the exported ImagePlus")
    String capturename = "Capture_00";

    @Parameter(label = "Select Source(s)",
            description = "The source(s) to export",
            required = false)
    SourceAndConverter[] sacs;

    @Parameter(label = "Match Window Size",
            description = "When checked, uses the BDV window dimensions for X and Y size",
            persist = false,
            callback = "matchXYBDVFrame")
    public boolean matchwindowsize = false;

    @Parameter(label = "Size X",
            description = "Total width in world coordinates units",
            callback = "matchXYBDVFrame",
            style = "format:0.#####E0")
    public double xsize = 100;

    @Parameter(label = "Size Y",
            description = "Total height in world coordinates units",
            callback = "matchXYBDVFrame",
            style = "format:0.#####E0")
    public double ysize = 100;

    @Parameter(label = "Half Thickness Z",
            description = "Half-thickness above and below the current plane (0 for single slice)",
            style = "format:0.#####E0")
    public double zsize = 100;

    @Parameter(label = "Select Range",
            callback = "updateMessage",
            visibility = ItemVisibility.MESSAGE,
            persist = false,
            required = false)
    String range = "You can use commas or colons to separate ranges. eg. '1:10' or '1,3,5,8' ";

    @Parameter(label = "Selected Timepoints",
            description = "Timepoints to export (e.g., '0:10' or '0,2,4'). Leave blank for all",
            required = false)
    private String selected_timepoints_str = "";

    @Parameter(label = "XY Pixel Size",
            description = "Output pixel size in XY (world coordinates units)",
            callback = "changePhysicalSampling",
            style = "format:0.#####E0")
    public double samplingxyinphysicalunit = 1;

    @Parameter(label = "Z Pixel Size",
            description = "Output pixel size in Z (world coordinates units)",
            callback = "changePhysicalSampling",
            style = "format:0.#####E0")
    public double samplingzinphysicalunit = 1;

    @Parameter(label = "Interpolate",
            description = "When checked, uses interpolation when resampling")
    boolean interpolate = true;

    @Parameter(label = "Export Mode",
            description = "Normal loads all data; Virtual creates a lazy-loading stack",
            choices = {"Normal", "Virtual", "Virtual no-cache"},
            required = false)
    String export_mode = "Non virtual";

    @Parameter(label = "Parallel Channels",
            description = "When checked, acquires channels in parallel (Normal mode only)",
            required = false)
    Boolean parallel_c = false;

    @Parameter(label = "Parallel Slices",
            description = "When checked, acquires Z-slices in parallel (Normal mode only)",
            required = false)
    Boolean parallel_z = false;

    @Parameter(label = "Parallel Timepoints",
            description = "When checked, acquires timepoints in parallel (Normal mode only)",
            required = false)
    Boolean parallel_t = false;

    //@Parameter( label = "Monitor loaded data")
    private Boolean monitor = true;

    @Parameter(label = "Unit",
            description = "Physical unit for the output image calibration")
    String unit = "px";

    @Parameter(type = ItemIO.OUTPUT,
            label = "Exported Images",
            description = "The exported ImagePlus images")
    public List<ImagePlus> images;

    @Parameter
    TaskService taskService;

    @Override
    public void run() {

        // At least one source
        if ((sacs==null)||(sacs.length==0)) {
            logger.info("No selected source. Abort command.");
            return;
        }

        SourceAndConverter<?> model = createModelSource();

        boolean cacheImage = false;
        boolean virtual;
        switch (export_mode) {
            case "Normal":
                virtual = false;
                break;
            case "Virtual":
                virtual = true;
                cacheImage = true;
                break;
            case "Virtual no-cache":
                virtual = true;
                cacheImage = false;
                break;
            default: throw new UnsupportedOperationException("Unrecognized export mode "+export_mode);
        }

        // New to split by type.

        boolean cacheImageFinal = cacheImage;
        boolean virtualFinal = virtual;


        images = new ArrayList<>();

        List<SourceAndConverter<?>> sourceList = new ArrayList<>();
        Arrays.asList(sacs).forEach(sac -> sourceList.add(sac));

        Map<Class<net.imglib2.type.Type>, List<SourceAndConverter<?>>>
                typeToSources = sourceList.stream().collect(Collectors.groupingBy(src -> (Class<net.imglib2.type.Type>)(src.getSpimSource().getType().getClass())));

        typeToSources.keySet().forEach(pixelType -> {

            try {
                if (pixelType.equals(ARGBType.class) || pixelType.equals(VolatileARGBType.class)) {
                    typeToSources.get(pixelType).forEach(source -> {
                        Task task = null;
                        if (monitor) task = taskService.createTask("Bdv View export:"+capturename);
                                try {
                                    images.add(ImagePlusSampler.Builder()
                                            .cache(cacheImageFinal)
                                            .virtual(virtualFinal)
                                            .unit(unit)
                                            .monitor(task)
                                            .title(capturename)
                                            .parallelC(parallel_c).parallelZ(parallel_z).parallelT(parallel_t)
                                            .setModel(model)
                                            .interpolate(interpolate)
                                            .rangeT(selected_timepoints_str)
                                            .sources(new SourceAndConverter[] {source})
                                            .get());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                    );
                } else {
                    Task task = null;
                    if (monitor) task = taskService.createTask("Bdv View export:"+capturename);
                    images.add(ImagePlusSampler.Builder()
                            .cache(cacheImageFinal)
                            .virtual(virtualFinal)
                            .unit(unit)
                            .monitor(task)
                            .title(capturename)
                            .setModel(model)
                            .parallelC(parallel_c).parallelZ(parallel_z).parallelT(parallel_t)
                            .interpolate(interpolate)
                            .rangeT(selected_timepoints_str)
                            .sources(typeToSources.get(pixelType).toArray(new SourceAndConverter[0]))
                            .get());

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }

    private SourceAndConverter<?> createModelSource() {
        // Origin is in fact the point 0,0,0 of the image
        // Get current big dataviewer transformation : source transform and viewer transform
        AffineTransform3D at3D = new AffineTransform3D(); // Empty Transform
        // viewer transform
        bdv_h.getViewerPanel().state().getViewerTransform(at3D); // Get current transformation by the viewer state and puts it into sourceToImgPlus
        //Center on the display center of the viewer ...
        double w = bdv_h.getViewerPanel().getDisplay().getWidth();
        double h = bdv_h.getViewerPanel().getDisplay().getHeight();
        // Center on the display center of the viewer ...
        at3D.translate(-w / 2, -h / 2, 0);
        // Getting an image independent of the view scaling unit (not sure)
        double xNorm = getNormTransform(0, at3D);//trans
        at3D.scale(1/xNorm);

        at3D.scale(1./ samplingxyinphysicalunit, 1./ samplingxyinphysicalunit, 1./ samplingzinphysicalunit);
        at3D.translate((xsize /(2* samplingxyinphysicalunit)), (ysize /(2* samplingxyinphysicalunit)), (zsize /(samplingzinphysicalunit)));

        long nPx = (long)(xsize / samplingxyinphysicalunit);
        long nPy = (long)(ysize / samplingxyinphysicalunit);
        long nPz;
        if (samplingzinphysicalunit ==0) {
            nPz = 1;
        } else {
            nPz = 1+(long)(zsize / (samplingzinphysicalunit /2.0)); // TODO : check div by 2
        }

        // At least a pixel in all directions
        if (nPz == 0) nPz = 1;
        if (nPx == 0) nPx = 1;
        if (nPy == 0) nPy = 1;

        return new EmptySourceAndConverterCreator(capturename, at3D.inverse(), nPx, nPy, nPz).get();
    }

    /**
     * Returns the norm of an axis after an affinetransform is applied
     * @param axis axis of the affine transform
     * @param t affine transform measured
     * @return the norm of an axis after an affinetransform is applied
     */
    static public double getNormTransform(int axis, AffineTransform3D t) {
        double f0 = t.get(axis,0);
        double f1 = t.get(axis,1);
        double f2 = t.get(axis,2);
        return Math.sqrt(f0 * f0 + f1 * f1 + f2 * f2);
    }

    /**
     * Returns the distance between two RealPoint pt1 and pt2
     * @param pt1 first point
     * @param pt2 second point
     * @return the distance between two RealPoint pt1 and pt2
     */
    static public double distance(RealPoint pt1, RealPoint pt2) {
        assert pt1.numDimensions()==pt2.numDimensions();
        double dsquared = 0;
        for (int i=0;i<pt1.numDimensions();i++) {
            double diff = pt1.getDoublePosition(i)-pt2.getDoublePosition(i);
            dsquared+=diff*diff;
        }
        return Math.sqrt(dsquared);
    }

    // -- Initializers --

    /**
     * Initializes xSize(Pix) and ySize(Pix) according to the current BigDataViewer window
     * Contrary to what intellij says, it IS used
     */
    public void matchXYBDVFrame() {
        if (matchwindowsize) {
            // Gets window size
            double w = bdv_h.getViewerPanel().getDisplay().getWidth();
            double h = bdv_h.getViewerPanel().getDisplay().getHeight();

            // Get global coordinates of the top left position  of the viewer
            RealPoint ptTopLeft = new RealPoint(3); // Number of dimension
            bdv_h.getViewerPanel().displayToGlobalCoordinates(0, 0, ptTopLeft);

            // Get global coordinates of the top right position  of the viewer
            RealPoint ptTopRight = new RealPoint(3); // Number of dimension
            bdv_h.getViewerPanel().displayToGlobalCoordinates(0, w, ptTopRight);

            // Get global coordinates of the top right position  of the viewer
            RealPoint ptBottomLeft = new RealPoint(3); // Number of dimension
            bdv_h.getViewerPanel().displayToGlobalCoordinates(h,0, ptBottomLeft);

            // Gets physical size of pixels based on window size, image sampling size and user requested pixel size
            this.xsize =distance(ptTopLeft, ptTopRight);
            this.ysize =distance(ptTopLeft, ptBottomLeft);
        }
    }

}
