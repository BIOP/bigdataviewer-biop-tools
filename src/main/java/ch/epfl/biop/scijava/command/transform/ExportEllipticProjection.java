package ch.epfl.biop.scijava.command.transform;

import bdv.img.WarpedSource;
import bdv.util.Elliptical3DTransform;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.exporter.CZTRange;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusSampler;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import org.scijava.widget.Button;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.persist.ScijavaGsonHelper;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static bdv.util.Elliptical3DTransform.RADIUS_X;
import static bdv.util.Elliptical3DTransform.RADIUS_Y;
import static bdv.util.Elliptical3DTransform.RADIUS_Z;

/**
 * Command used to export an elliptical transformed source
 */

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Export elliptic 3D transformed sources",
        description = "Exports elliptically-transformed sources to an ImagePlus with spherical coordinate sampling")
public class ExportEllipticProjection implements Command {

    @Parameter(label = "", visibility = ItemVisibility.MESSAGE, required = false, persist = false)
    String export_sources_message = "<html><h2>Exported Elliptic Transformed Source</h2></html>";

    @Parameter(label = "Select Source(s)",
            callback = "validateMessage",
            description = "Elliptically-transformed sources to export")
    SourceAndConverter<?>[] sacs;

    @Parameter(label = "Resolution Level",
            description = "Pyramid level to use (0 = highest resolution)")
    public int level;

    @Parameter(label = "Update", callback = "validateMessage")
    Button update_button1;

    @Parameter(label = "", visibility = ItemVisibility.MESSAGE, required = false, persist = false)
    String validate_message = "Please select the sources to export. Click update for more info.";

    @Parameter(label = "", visibility = ItemVisibility.MESSAGE, required = false, persist = false)
    String export_range_message = "<html><h2>Exported Range</h2></html>";

    @Parameter(callback = "validateMessage", style = "format:0.#####E0")
    double r_min = 0.8;

    @Parameter(callback = "validateMessage", style = "format:0.#####E0")
    double r_max = 1.2;

    @Parameter(callback = "validateMessage", style = "format:0.#####E0")
    double radius_step = 0.01;

    @Parameter(style = "slider, format:0.#####E0", stepSize = "1", min = "0", max = "180", callback = "validateMessage")
    double theta_min = 0;

    @Parameter(style = "slider, format:0.#####E0", stepSize = "1", min = "0", max = "180", callback = "validateMessage")
    double theta_max = 180;

    @Parameter(style = "slider, format:0.#####E0", stepSize = "1", min = "0", max = "360", callback = "validateMessage")
    double phi_min = 0;

    @Parameter(style = "slider, format:0.#####E0", stepSize = "1", min = "0", max = "360", callback = "validateMessage")
    double phi_max = 360;

    @Parameter(callback = "validateMessage", style="format:0.#####E0")
    double angle_step = 0.01;

    @Parameter(label = "", visibility = ItemVisibility.MESSAGE, required = false, persist = false)
    String exported_image_message = "<html><h2>Exported Image Parameters</h2></html>";

    @Parameter(label = "Exported Image Name")
    public String name = "Image_00";

    @Parameter( label = "Select Range", visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String range_message = "You can use commas or colons to separate ranges. eg. '1:10' or '1,3,5,8' ";

    @Parameter( label = "Selected Channels. Leave blank for all", required = false )
    String range_channels = "";

    @Parameter( label = "Selected Slices. Leave blank for all", required = false )
    String range_slices = "";

    @Parameter( label = "Selected Timepoints. Leave blank for all", required = false )
    String range_frames = "";

    @Parameter( label = "Export mode", choices = {"Normal", "Virtual", "Virtual no-cache"}, required = false )
    String export_mode = "Non virtual";

    @Parameter( label = "Monitor loaded data")
    Boolean monitor = false;

    @Parameter(label = "Interpolate")
    public boolean interpolate = false;

    @Parameter
    String unit="px";

    @Parameter(type = ItemIO.OUTPUT)
    public ImagePlus imp_out;

    @Parameter(label = "Update", callback = "validateMessage")
    Button update_button2;

    @Parameter( label = "Image Info", visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String message = "[SX: , SY:, SZ:, #C:, #T:], ? Mb";

    @Parameter
    Context context;

    Elliptical3DTransform e3dt;

    int maxTimepoint = 1;

    CZTRange range;

    @Parameter
    TaskService taskService;

    @Override
    public void run() {
        validateMessage();

        List<SourceAndConverter<?>> sources = sorter.apply(Arrays.asList(sacs));

        // At least one source
        if ((sacs==null)||(sacs.length==0)) {
            IJ.log("No selected source. Abort command.");
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
        DecimalFormat df = new DecimalFormat("#.00");
        String suffixName = "_R["+df.format(r_min)+"; "+df.format(r_max)+"]_Theta["+df.format(theta_min)+"; "+df.format(theta_max)+"]_Phi["+df.format(phi_min)+"; "+df.format(phi_max)+"]";
        try {
            Task task = null;
            if (monitor) task = taskService.createTask("Elliptic projection:"+name+suffixName);
            imp_out = ImagePlusSampler.Builder()
                    .cache(cacheImage)
                    .unit(unit)
                    .title(name+suffixName)
                    .setModel(model)
                    .virtual(virtual)
                    .interpolate(interpolate)
                    .rangeT(range_frames)
                    .rangeC(range_channels)
                    .rangeZ(range_slices)
                    .monitor(task)
                    .level(level)
                    .sources(sources.toArray(new SourceAndConverter[0]))
                    .get();

            imp_out.show();

            if (transform!=null) {
                String transformSerialized = ScijavaGsonHelper.getGson(context).toJson(transform);
                String info = imp_out.getInfoProperty();
                info = info + "\n" + transformSerialized + "\n";
                imp_out.setProperty("Info", info);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    Elliptical3DTransform transform = null;

    public void validateMessage() {
        if ((sacs==null)||(sacs.length==0)) {
            validate_message = "Please select the sources to export";
            return;
        }
        transform = null;
        boolean hasMultipleTransforms = false;
        for (SourceAndConverter source : sacs) {
            if (!(source.getSpimSource() instanceof WarpedSource)) {
                validate_message = source.getSpimSource().getName()+" is not a transformed source";
                return;
            }
            RealTransform rt = ((WarpedSource) source.getSpimSource()).getTransform();
            if (!(rt instanceof Elliptical3DTransform)) {
                validate_message = source.getSpimSource().getName()+" is not an elliptic transformed source";
                return;
            } else {
                if (transform == null) {
                    transform = (Elliptical3DTransform) rt;
                } else {
                    if (!rt.equals(transform)) {
                        hasMultipleTransforms = true;
                    }
                }
            }
        }

        e3dt = transform;

        validate_message = "<html>";
        if (hasMultipleTransforms) {
            validate_message +="Multiple transforms found. Potential incorrect sampling advice.<br>";
        }

        // Checks all on a single source
        SourceAndConverter modelSource = sacs[0];

        Source wrappedSource = ((WarpedSource)modelSource.getSpimSource()).getWrappedSource();

        double rMean = (e3dt.getParameters().get(RADIUS_X)+e3dt.getParameters().get(RADIUS_Y)+e3dt.getParameters().get(RADIUS_Z))/3.0;

        double dxy = rMean * angle_step * Math.PI / 180.0;
        double dz = rMean * radius_step;

        DecimalFormat df = new DecimalFormat("00.###E0");


        if ((dxy == 0)||(dz == 0)) {
            validate_message +="<font color=\"red\"> Error! Wrong step size. </font><br>";
        } else {
            int levelXY = SourceAndConverterHelper.bestLevel(wrappedSource, 0, dxy);
            validate_message +="Equatorial pixel size (xy): "+df.format(dxy)+"<br>";
            validate_message +="Recommended Level:"+levelXY+"<br>";
            int levelZ = SourceAndConverterHelper.bestLevel(wrappedSource, 0, dz);
            validate_message +="Equatorial pixel size (z): "+df.format(dz)+"<br>";
            validate_message +="Recommended Level:"+levelZ+"<br>";
        }

        maxTimepoint = SourceAndConverterHelper.getMaxTimepoint(sacs)+1;

        int maxTimeFrames = SourceAndConverterHelper.getMaxTimepoint(sacs)+1;

        int maxZSlices;

        if (r_min == r_max) {
            maxZSlices = 1;
        } else {
            if (radius_step <=0) {
                validate_message +="<font color=\"red\">Error : radius Step is null!</font><br></html>";
                return;
            } else {
                maxZSlices = (int) ((r_max - r_min) / radius_step)+1;
            }
        }

        if (angle_step <=0) {
            validate_message +="<font color=\"red\">Error : angle Step is null!</font><br></html>";
        }

        validate_message +="</html>";

        int imageWidth = (int) ((phi_max - phi_min) / angle_step);

        int imageHeight = (int) ((theta_max - theta_min) / angle_step);


        try {
            range = new CZTRange.Builder()
                    .setC(range_channels)
                    .setZ(range_slices)
                    .setT(range_frames)
                    .get(sacs.length, maxZSlices,maxTimeFrames);
        } catch (Exception e) {
            validate_message +="<font color=\"red\">"+e.getMessage()+"</font><br></html>";
            return;
        }
        long nBytesPerPlane = (long) imageWidth *imageHeight*2; // let's assume 16 bits
        int nc = range.getRangeC().size();
        int nz = range.getRangeZ().size();
        int nt = range.getRangeT().size();
        int bytesPerPix = getBytesPerPixel(sacs[0]);
        long nTotalBytes = (long) range.getTotalPlanes() * (long) nBytesPerPlane * (long) bytesPerPix;

        double totalMb = nTotalBytes / (1024.0*1024);

        DecimalFormat df2 = new DecimalFormat("###.0");
        message = "[SX:"+imageWidth+", SY:"+imageHeight+", SZ:"+nz+", #C:"+nc+", #T:"+nt+"], "+df2.format(totalMb)+" Mb";

    }

    private int getBytesPerPixel(SourceAndConverter sac) {
        Object o = Util.getTypeFromInterval(sac.getSpimSource().getSource(0,0));
        if (o instanceof UnsignedByteType) {
            return 1;
        } else if (o instanceof UnsignedShortType) {
            return 2;
        } else if (o instanceof ARGBType) {
            return 4;
        } else if (o instanceof FloatType) {
            return 4;
        } else {
            throw new UnsupportedOperationException("Unsupported pixel type "+o.getClass());
        }
    }

    public Function<Collection<SourceAndConverter<?>>, List<SourceAndConverter<?>>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultGeneric(sacslist);

    private SourceAndConverter<?> createModelSource() {
        // Origin is in fact the point 0,0,0 of the image
        // Get current big dataviewer transformation : source transform and viewer transform
        AffineTransform3D at3D = new AffineTransform3D(); // Empty Transform

        double samplingxyinphysicalunit = angle_step *Math.PI/180.0;
        double samplingzinphysicalunit = radius_step;

        at3D.set( samplingxyinphysicalunit,0,0);
        at3D.set( samplingxyinphysicalunit,1,1);
        at3D.set( samplingzinphysicalunit,2,2);
        at3D.rotate(1,-Math.PI/2.0);
        at3D.translate(r_max - radius_step, ((theta_min)*Math.PI/180.0), ((phi_min)*Math.PI/180.0));


        long nPx = (int) ((phi_max - phi_min) / angle_step);
        long nPy = (int) ((theta_max - theta_min) / angle_step);
        long nPz;
        if (r_min == r_max) {
            nPz = 1;
        } else {
            nPz = (int) ((r_max - r_min) / radius_step)+1;
        }

        // At least a pixel in all directions
        if (nPz == 0) nPz = 1;
        if (nPx == 0) nPx = 1;
        if (nPy == 0) nPy = 1;

        return new EmptySourceAndConverterCreator(name+"_Model", at3D, nPx, nPy, nPz).get();
    }

}
