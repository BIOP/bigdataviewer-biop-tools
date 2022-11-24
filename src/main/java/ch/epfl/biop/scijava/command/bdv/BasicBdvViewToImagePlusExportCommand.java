package ch.epfl.biop.scijava.command.bdv;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.imageplus.ImagePlusHelper;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusSampler;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * This command is a simplified version of {@link BdvViewToImagePlusExportCommand} where
 * reasonable default settings are being set.
 *
 * The mipmap levels necessary for the sampling are estimated, all sources present in a bdv
 * window are exported, the size of the export is the window size, a composite window is created
 * by default.
 *
 * The freedom is still let free for interpolation, z
 *
 * Stack is virtual and cached by default
 * @param <T> non-volatile pixel type
 */

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Current BDV View To ImagePlus (Basic)")
public class BasicBdvViewToImagePlusExportCommand<T extends RealType<T>> implements BdvPlaygroundActionCommand {

    private static Logger logger = LoggerFactory.getLogger(BasicBdvViewToImagePlusExportCommand.class);

    /**
     * Bigdataviewer handle
     */
    @Parameter(label = "BigDataViewer Frame")
    public BdvHandle bdv_h;

    @Parameter(label = "Capture Name")
    String capturename = "Capture_00";

    /**
     * Half Thickness Z (above and below, physical unit, 0 for a single slice)
     */
    @Parameter(label = "Half Thickness Z (above and below, physical unit, 0 for a single slice)", style = "format:0.#####E0")
    public double zsize = 100;

    /**
     * Output pixel size (physical unit)
     */
    @Parameter(label = "Output pixel size (physical unit)", style = "format:0.#####E0")
    public double samplingxyinphysicalunit = 1;

    /**
     * Z Pixel size sampling (physical unit)
     */
    @Parameter(label = "Z Pixel size sampling (physical unit)", style = "format:0.#####E0")
    public double samplingzinphysicalunit = 1;

    /**
     * INterpolate
     */
    @Parameter(label = "Interpolate")
    public boolean interpolate = true;

    @Parameter( label = "Select Range", callback = "updateMessage", visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String range = "You can use commas or colons to separate ranges. eg. '0:10' or '0,2,4,6' ";

    @Parameter( label = "Selected Timepoints. Leave blank for all", required = false )
    String selected_timepoints_str = "";

    @Parameter( label = "Export mode", choices = {"Normal", "Virtual", "Virtual no-cache"}, required = false )
    String export_mode = "Non virtual";

    @Parameter( label = "Acquire channels in parallel (Normal only)", required = false)
    Boolean parallelC = false;

    @Parameter( label = "Acquire slices in parallel (Normal only)", required = false)
    Boolean parallelZ = false;

    @Parameter( label = "Acquire timepoints in parallel (Normal only)", required = false)
    Boolean parallelT = false;

    //@Parameter( label = "Monitor loaded data")
    private Boolean monitor = true;

    @Parameter
    String unit="px";

    // Output imageplus window
    @Parameter(type = ItemIO.OUTPUT)
    public List<ImagePlus> images;

    @Parameter
    TaskService taskService;

    double xSize, ySize;

    @Override
    public void run() {
        if (bdv_h.getViewerPanel().state().getSources().size()==0) {
            logger.info("No source present in Bdv. Abort command.");
            return;
        }

        List<SourceAndConverter<?>> sourceList = bdv_h.getViewerPanel().state().getSources();

        sourceList = sourceList.stream().filter(source -> {
            if (source.getSpimSource() == null) {
                IJ.log("Null source excluded");
                return false;
            }
            if ((source.getSpimSource().getType() instanceof UnsignedShortType)
                ||(source.getSpimSource().getType() instanceof UnsignedByteType)
                ||(source.getSpimSource().getType() instanceof FloatType)
                ||(source.getSpimSource().getType() instanceof ARGBType)) {
                return true;
            } else {
                IJ.log("Unsupported export for source "+source.getSpimSource().getName());
                return false;
            }
        }).collect(Collectors.toList());

        matchXYBDVFrame();

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

        boolean cacheImageFinal = cacheImage;
        boolean virtualFinal = virtual;

        images = new ArrayList<>();

        // New to split by type.

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
                                        .setModel(model)
                                        .parallelC(parallelC).parallelZ(parallelZ).parallelT(parallelT)
                                        .interpolate(interpolate)
                                        .rangeT(selected_timepoints_str)
                                        .sources(new SourceAndConverter[] {source})
                                        .get());
                        } catch (Exception e) {
                            if (task!=null) {
                                task.cancel(e.getMessage());
                            }
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
                            .parallelC(parallelC).parallelZ(parallelZ).parallelT(parallelT)
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
        double xNorm = BdvViewToImagePlusExportCommand.getNormTransform(0, at3D);//trans
        at3D.scale(1/xNorm);

        at3D.scale(1./ samplingxyinphysicalunit, 1./ samplingxyinphysicalunit, 1./ samplingzinphysicalunit);
        at3D.translate((xSize/(2* samplingxyinphysicalunit)), (ySize/(2* samplingxyinphysicalunit)), (zsize /(samplingzinphysicalunit)));

        long nPx = (long)(xSize / samplingxyinphysicalunit);
        long nPy = (long)(ySize / samplingxyinphysicalunit);
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

    // -- Initializers --

    /**
     * Initializes xSize(Pix) and ySize(Pix) according to the current BigDataViewer window
     */
    public void matchXYBDVFrame() {
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

        // Gets physical size of bdv window 'view'
        this.xSize=BdvViewToImagePlusExportCommand.distance(ptTopLeft, ptTopRight);
        this.ySize=BdvViewToImagePlusExportCommand.distance(ptTopLeft, ptBottomLeft);
    }

}
