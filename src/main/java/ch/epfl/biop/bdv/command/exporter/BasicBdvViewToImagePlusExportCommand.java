package ch.epfl.biop.bdv.command.exporter;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.util.ImagePlusHelper;
import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
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

    @Parameter(label = "BigDataViewer Frame")
    public BdvHandle bdv_h;

    @Parameter(label = "Capture Name")
    String captureName = "Capture_00";

    //@Parameter(label = "Include all sources from current Bdv Frame")
    boolean allSources = true;

    //@Parameter(label="Mipmap level, 0 for highest resolution")
    //public int mipmapLevel = -1; // Automatically found with best resolution

    //@Parameter(label="Match bdv frame window size", persist=false, callback = "matchXYBDVFrame")
    public boolean matchWindowSize=true;

    SourceAndConverter[] sacs;

    //@Parameter(label = "Total Size X (physical unit)", callback = "matchXYBDVFrame")
    //public double xSize = 100;

    //@Parameter(label = "Total Size Y (physical unit)", callback = "matchXYBDVFrame")
    //public double ySize = 100;

    @Parameter(label = "Half Thickness Z (above and below, physical unit, 0 for a single slice)")
    public double zSize = 100;

    @Parameter(label = "Start Timepoint (starts at 0)")
    public int timepointBegin = 0;

    @Parameter(label = "Number of Timepoints (min 1)", min="1")
    public int numberOfTimePoints = 1;

    //@Parameter(label = "End Timepoint (excluded)")
    //public int timepointEnd = 0;

    @Parameter(label = "Output pixel size (physical unit)")
    public double samplingXYInPhysicalUnit = 1;

    @Parameter(label = "Z Pixel size sampling (physical unit)")
    public double samplingZInPhysicalUnit = 1;

    @Parameter(label = "Interpolate")
    public boolean interpolate = true;

    //@Parameter(label = "Ignore Source LUT (check for RGB)")
    //public boolean ignoreSourceLut = false;

    //@Parameter(label = "Make Composite")
    public boolean makeComposite = true;

    // Output imageplus window
    @Parameter(type = ItemIO.OUTPUT)
    public ImagePlus compositeImage;

    @Parameter(type = ItemIO.OUTPUT)
    public Map<SourceAndConverter, ImagePlus> singleChannelImages = new HashMap<>();

    String unitOfFirstSource=" ";

    public Consumer<String> errlog = (s) -> System.err.println(s);

    List<SourceAndConverter<?>> sourceList;

    AffineTransform3D at3D;

    double xSize, ySize;

    @Override
    public void run() {

        if (numberOfTimePoints<1) {
            System.err.println("Cannot get a negative number of timepoints");
            return;
        }

        final int timepointEnd = timepointBegin+numberOfTimePoints;
        // Sanity checks
        // 1. Timepoints : at least one timepoint
        /*if (timepointEnd<=timepointBegin) {
            timepointEnd = timepointBegin+1;
        }*/

        // 2. At least one source
        if (allSources) {
            if (bdv_h.getViewerPanel().state().getSources().size()==0) {
                errlog.accept("No source present in Bdv. Abort command.");
                return;
            }
        } /*else {
            if ((sacs==null)||(sacs.length==0)) {
                errlog.accept("No selected source. Abort command.");
                return;
            }
        }*/

        if (allSources) {
            sourceList = sorter.apply(bdv_h.getViewerPanel().state().getSources());
        } else {
            sourceList = sorter.apply(Arrays.asList(sacs));
        }

        boolean timepointsOk = true;

        for (SourceAndConverter source : sourceList) {
            for (int tp = timepointBegin;tp<timepointEnd;tp++) {
                timepointsOk = timepointsOk&&source.getSpimSource().isPresent(tp);
            }
        }

        if (!timepointsOk) {
            System.err.println("Invalid number of timepoints");
            return;
        }

        boolean ignoreSourceLut;

        // Ignore LUT if a single source is ARGBType
        if (sourceList.stream().filter(src -> src.getSpimSource().getType() instanceof ARGBType).findFirst().isPresent()) {
            ignoreSourceLut = true;
        } else {
            ignoreSourceLut = false;
        }

        matchXYBDVFrame();

        SourceAndConverter model = createModelSource();

        if (makeComposite) {
            if (sourceList.stream().map(sac -> sac.getSpimSource().getType().getClass()).distinct().count()>1) {
                errlog.accept("Cannot make composite because all sources are not of the same type");
                makeComposite = false;
            }
        }

        // The core of it : resampling each source with the model
        List<SourceAndConverter> resampledSourceList = sourceList
                .stream()
                .map(sac -> new SourceResampler(sac,model,true, true, interpolate).get())
                .collect(Collectors.toList());

        resampledSourceList.forEach(sac -> {
            SourceAndConverterServices.getSourceAndConverterService().remove(sac);
        });

        SourceAndConverterServices.getSourceAndConverterService().register(model);

        // Fetch the unit of the first source
        updateUnit();

        if ((makeComposite)&&(sourceList.size()>1)) {
            Map<SourceAndConverter, ConverterSetup> mapCS = new HashMap<>();
            sourceList.forEach(src -> mapCS.put(resampledSourceList.get(sourceList.indexOf(src)), bdv_h.getConverterSetups().getConverterSetup(src)));


            Map<SourceAndConverter, Integer> mapMipmap = new HashMap<>();
            sourceList.forEach(src -> {
                int mipmapLevel = SourceAndConverterHelper.bestLevel(src, timepointBegin, samplingXYInPhysicalUnit);
                System.out.println("Mipmap level chosen for source ["+src.getSpimSource().getName()+"] : "+mipmapLevel);
                mapMipmap.put(resampledSourceList.get(sourceList.indexOf(src)), mipmapLevel);
            });

            compositeImage = ImagePlusHelper.wrap(
                    resampledSourceList,
                    mapCS,
                    mapMipmap,
                    timepointBegin,
                    timepointEnd,
                    ignoreSourceLut);

            compositeImage.setTitle(BdvHandleHelper.getWindowTitle(bdv_h));
            ImagePlusHelper.storeExtendedCalibrationToImagePlus(compositeImage, at3D.inverse(), unitOfFirstSource, timepointBegin);
        } else {
            resampledSourceList.forEach(source -> {
                int mipmapLevel = SourceAndConverterHelper.bestLevel(sourceList.get(0), timepointBegin, samplingXYInPhysicalUnit);
                System.out.println("Mipmap level chosen for source ["+source.getSpimSource().getName()+"] : "+mipmapLevel);
                ImagePlus singleChannel = ImagePlusHelper.wrap(
                        source,
                        bdv_h.getConverterSetups().getConverterSetup(sourceList.get(resampledSourceList.indexOf(source))),
                        mipmapLevel,
                        timepointBegin,
                        timepointEnd,
                        ignoreSourceLut);
                System.out.println("Done!");
                singleChannelImages.put(source, singleChannel);
                singleChannel.setTitle(source.getSpimSource().getName());
                ImagePlusHelper.storeExtendedCalibrationToImagePlus(singleChannel, at3D.inverse(), unitOfFirstSource, timepointBegin);
                if (resampledSourceList.size()>1) {
                    singleChannel.show();
                } else {
                    compositeImage = singleChannel;
                }
            });
        }
    }

    private SourceAndConverter createModelSource() {
        // Origin is in fact the point 0,0,0 of the image
        // Get current big dataviewer transformation : source transform and viewer transform
        at3D = new AffineTransform3D(); // Empty Transform
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

        at3D.scale(1./samplingXYInPhysicalUnit, 1./samplingXYInPhysicalUnit, 1./samplingZInPhysicalUnit);
        at3D.translate((xSize/(2*samplingXYInPhysicalUnit)), (ySize/(2*samplingXYInPhysicalUnit)), (zSize/(samplingZInPhysicalUnit)));

        long nPx = (long)(xSize / samplingXYInPhysicalUnit);
        long nPy = (long)(ySize / samplingXYInPhysicalUnit);
        long nPz;
        if (samplingZInPhysicalUnit==0) {
            nPz = 1;
        } else {
            nPz = 1+(long)(zSize / (samplingZInPhysicalUnit/2.0)); // TODO : check div by 2
        }

        // At least a pixel in all directions
        if (nPz == 0) nPz = 1;
        if (nPx == 0) nPx = 1;
        if (nPy == 0) nPy = 1;

        return new EmptySourceAndConverterCreator(captureName, at3D.inverse(), nPx, nPy, nPz).get();
    }

    // -- Initializers --

    /**
     * Initializes xSize(Pix) and ySize(Pix) according to the current BigDataViewer window
     */
    public void matchXYBDVFrame() {
        if (matchWindowSize) {
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
            this.xSize=BdvViewToImagePlusExportCommand.distance(ptTopLeft, ptTopRight);
            this.ySize=BdvViewToImagePlusExportCommand.distance(ptTopLeft, ptBottomLeft);
        }
    }

    // -- Initializers --

    public void updateUnit() {
        if ((sourceList.size()>0) && (sourceList.get(0)!=null)) {
            if (sourceList.get(0).getSpimSource().getVoxelDimensions() != null) {
                unitOfFirstSource = sourceList.get(0).getSpimSource().getVoxelDimensions().unit();
            }
        }
    }

    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacs1ist -> SourceAndConverterHelper.sortDefaultGeneric(sacs1ist);



}
