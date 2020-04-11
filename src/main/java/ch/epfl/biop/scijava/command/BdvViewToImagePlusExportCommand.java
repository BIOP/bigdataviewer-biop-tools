package ch.epfl.biop.scijava.command;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.util.ImagePlusHelper;
import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.RealPoint;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvHandleHelper;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

/**
 * TODO : wrap multichannel in virtualstack
 * option for caching or not
 * @param <T>
 */

@Plugin(type = Command.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Current View To ImagePlus")
public class BdvViewToImagePlusExportCommand<T extends RealType<T>> implements Command {

    @Parameter(label = "BigDataViewer Frame")
    public BdvHandle bdv_h;

    @Parameter(required = false)
    SourceAndConverter[] sacs;

    @Parameter
    boolean allSources;

    @Parameter(label="Mipmap level, 0 for highest resolution")
    public int mipmapLevel = 0;

    @Parameter(label="Match bdv frame window size", persist=false, callback = "matchXYBDVFrame")
    public boolean matchWindowSize=false;

    @Parameter(label = "Total Size X", callback = "matchXYBDVFrame")
    public double xSize = 100;

    @Parameter(label = "Total Size Y", callback = "matchXYBDVFrame")
    public double ySize = 100;

    @Parameter(label = "Half Thickness Z (above and below)")
    public double zSize = 100;

    @Parameter(label = "Start Timepoint")
    public int timepointBegin = 0;

    @Parameter(label = "End Timepoint")
    public int timepointEnd = 0;

    @Parameter(label = "XY Pixel size sampling (physical unit)", callback = "changePhysicalSampling")
    public double samplingXYInPhysicalUnit = 1;

    @Parameter(label = "Z Pixel size sampling (physical unit)", callback = "changePhysicalSampling")
    public double samplingZInPhysicalUnit = 1;

    @Parameter(label = "Interpolate")
    public boolean interpolate = true;

    @Parameter(label = "Ignore Source LUT (check for RGB)")
    public boolean ignoreSourceLut = false;

    @Parameter(label = "Make Composite")
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

    @Override
    public void run() {

        // Sanity checks
        // 1. Timepoints
        if (timepointEnd<=timepointBegin) {
            timepointEnd = timepointBegin+1;
        }

        // 2. At least one source
        if (allSources) {
            if (bdv_h.getViewerPanel().state().getSources().size()==0) {
                errlog.accept("No source present in Bdv. Abort command.");
                return;
            }
        } else {
            if ((sacs==null)||(sacs.length==0)) {
                errlog.accept("No selected source. Abort command.");
                return;
            }
        }

        // Stream is single threaded or multithreaded based on boolean parameter
        if (allSources) {
            sourceList = sorter.apply(bdv_h.getViewerPanel().state().getSources());
        } else {
            sourceList = sorter.apply(Arrays.asList(sacs));
        }

        SourceAndConverter model = createModelSource();

        if (makeComposite) {
            if (sourceList.stream().map(sac -> sac.getSpimSource().getType().getClass()).distinct().count()>1) {
                errlog.accept("Cannot make composite because sources are not of the same type");
                makeComposite = false;
            }
        }

        // The core of it : resampling each source with the model
        List<SourceAndConverter> resampledSourceList = sourceList
                .stream()
                .map(sac -> new SourceResampler(sac,model,true, interpolate).get())
                .collect(Collectors.toList());

        // Fetch the unit of the first source
        updateUnit();

        if ((makeComposite)&&(sourceList.size()>1)) {
            Map<SourceAndConverter, ConverterSetup> mapCS = new HashMap<>();
            sourceList.forEach(src -> mapCS.put(resampledSourceList.get(sourceList.indexOf(src)), bdv_h.getConverterSetups().getConverterSetup(src)));

            Map<SourceAndConverter, Integer> mapMipmap = new HashMap<>();
            sourceList.forEach(src -> mapMipmap.put(resampledSourceList.get(sourceList.indexOf(src)), mipmapLevel));

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
                ImagePlus singleChannel = ImagePlusHelper.wrap(
                        source,
                        bdv_h.getConverterSetups().getConverterSetup(sourceList.get(resampledSourceList.indexOf(source))),
                        mipmapLevel,
                        timepointBegin,
                        timepointEnd,
                        ignoreSourceLut);
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
        double xNorm = getNormTransform(0, at3D);//trans
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


        // Dummy ImageFactory
        // Make edge display on demand
        final int[] cellDimensions = new int[] { 32, 32, 32 };

        // Cached Image Factory Options
        final DiskCachedCellImgOptions factoryOptions = options()
                .cellDimensions( cellDimensions )
                .cacheType( DiskCachedCellImgOptions.CacheType.BOUNDED )
                .maxCacheSize( 1 );

        // Creates cached image factory of Type UnsignedShort
        final DiskCachedCellImgFactory<UnsignedShortType> factory = new DiskCachedCellImgFactory<>( new UnsignedShortType(), factoryOptions );

        // At least a pixel in all directions
        if (nPz == 0) nPz = 1;
        if (nPx == 0) nPx = 1;
        if (nPy == 0) nPy = 1;

        return new EmptySourceAndConverterCreator("Model", at3D.inverse(), nPx, nPy, nPz, factory).get();
    }

    /**
     * Returns the norm of an axis after an affinetransform is applied
     * @param axis
     * @param t
     * @return
     */
    static public double getNormTransform(int axis, AffineTransform3D t) {
        double f0 = t.get(axis,0);
        double f1 = t.get(axis,1);
        double f2 = t.get(axis,2);
        return Math.sqrt(f0 * f0 + f1 * f1 + f2 * f2);
    }

    /**
     * Returns the distance between two RealPoint pt1 and pt2
     * @param pt1
     * @param pt2
     * @return
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
            this.xSize=distance(ptTopLeft, ptTopRight);
            this.ySize=distance(ptTopLeft, ptBottomLeft);
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

    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacs1 -> defaultSorter(sacs1);

    /**
     * Default sorting order for SourceAndConverter
     * Because we want some consistency in channel ordering when exporting to an ImagePlus
     * AArrg indexes are back
     *
     * TODO : find a better way to order between spimdata
     * @param sacs
     * @return
     */
    public static List<SourceAndConverter<?>> defaultSorter(Collection<SourceAndConverter<?>> sacs) {
        List<SourceAndConverter<?>> sortedList = new ArrayList<>(sacs.size());
        sortedList.addAll(sacs);
        Set<AbstractSpimData> spimData = new HashSet<>();
        // Gets all SpimdataInfo
        sacs.forEach(sac -> {
            if (SourceAndConverterServices
                    .getSourceAndConverterService()
                    .getMetadata(sac, SourceAndConverterService.SPIM_DATA_INFO)!=null) {
                SourceAndConverterService.SpimDataInfo sdi = ((SourceAndConverterService.SpimDataInfo)(SourceAndConverterServices
                        .getSourceAndConverterService()
                        .getMetadata(sac, SourceAndConverterService.SPIM_DATA_INFO)));
                spimData.add(sdi.asd);
            }
        });

        Comparator<SourceAndConverter> sacComparator = (s1, s2) -> {
            // Those who do not belong to spimdata are last:
            SourceAndConverterService.SpimDataInfo sdi1 = null, sdi2 = null;
            if (SourceAndConverterServices
                    .getSourceAndConverterService()
                    .getMetadata(s1, SourceAndConverterService.SPIM_DATA_INFO)!=null) {
                 sdi1 = ((SourceAndConverterService.SpimDataInfo)(SourceAndConverterServices
                        .getSourceAndConverterService()
                        .getMetadata(s1, SourceAndConverterService.SPIM_DATA_INFO)));
            }

            if (SourceAndConverterServices
                    .getSourceAndConverterService()
                    .getMetadata(s2, SourceAndConverterService.SPIM_DATA_INFO)!=null) {
                sdi2 = ((SourceAndConverterService.SpimDataInfo)(SourceAndConverterServices
                        .getSourceAndConverterService()
                        .getMetadata(s2, SourceAndConverterService.SPIM_DATA_INFO)));
            }

            if ((sdi1==null)&&(sdi2!=null)) {
                return -1;
            }

            if ((sdi1!=null)&&(sdi2==null)) {
                return 1;
            }

            if ((sdi1!=null)&&(sdi2!=null)) {
                if (sdi1.asd==sdi2.asd) {
                    return sdi2.setupId-sdi1.setupId;
                } else {
                    return sdi2.toString().compareTo(sdi1.toString());
                }
            }

            return s2.getSpimSource().getName().compareTo(s1.getSpimSource().getName());
        };

        sortedList.sort(sacComparator);
        return sortedList;
    }

}
