package ch.epfl.biop.scijava.command;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.transform.SourceMosaicZSlicer;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.RealPoint;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;

import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Slice Source")
public class SliceSourceCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "BigDataViewer Frame")
    public BdvHandle bdv_h;

    @Parameter(required = false)
    SourceAndConverter[] sacs;

    @Parameter(label="Match bdv frame window size", persist=false, callback = "matchXYBDVFrame")
    public boolean matchWindowSize=false;

    @Parameter(label = "Total Size X (physical unit)", callback = "matchXYBDVFrame")
    public double xSize = 100;

    @Parameter(label = "Total Size Y (physical unit)", callback = "matchXYBDVFrame")
    public double ySize = 100;

    @Parameter(label = "Half Thickness Z (above and below, physical unit)")
    public double zSize = 100;

    @Parameter(label = "XY Pixel size sampling (physical unit)", callback = "changePhysicalSampling")
    public double samplingXYInPhysicalUnit = 1;

    @Parameter(label = "Z Pixel size sampling (physical unit)", callback = "changePhysicalSampling")
    public double samplingZInPhysicalUnit = 1;

    @Parameter(label = "Interpolate")
    public boolean interpolate = true;


    @Parameter(label = "ReUseMipMaps")
    public boolean reusemipmaps = true;


    @Parameter(label = "cache")
    public boolean cache = false;


    String unitOfFirstSource=" ";

    public Consumer<String> errlog = (s) -> System.err.println(s);

    java.util.List<SourceAndConverter<?>> sourceList;

    AffineTransform3D at3D;

    @Override
    public void run() {

        if ((sacs==null)||(sacs.length==0)) {
            errlog.accept("No selected source. Abort command.");
            return;
        }

        sourceList = sorter.apply(Arrays.asList(sacs));

        SourceAndConverter model = createModelSource();

        // The core of it : resampling each source with the model
        java.util.List<SourceAndConverter> resampledSourceList = sourceList
                .stream()
                .map(sac -> new SourceMosaicZSlicer(sac,model,reusemipmaps, cache, interpolate, () -> (long) 1).get())
                .collect(Collectors.toList());

        resampledSourceList.forEach(sac -> {
            SourceAndConverterServices.getSourceAndConverterService().register(sac);
        });

        SourceAndConverterServices.getSourceAndConverterService().register(model);

        // Fetch the unit of the first source
        updateUnit();

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

        // At least a pixel in all directions
        if (nPz == 0) nPz = 1;
        if (nPx == 0) nPx = 1;
        if (nPy == 0) nPy = 1;

        return new EmptySourceAndConverterCreator("SlicerModel", at3D.inverse(), nPx, nPy, nPz).get();
    }

    /**
     * Returns the norm of an axis after an affinetransform is applied
     * @param axis axis
     * @param t affine transform to measure
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

    public Function<Collection<SourceAndConverter<?>>, java.util.List<SourceAndConverter<?>>> sorter = sacs1ist -> sortDefault(sacs1ist);

    /**
     * Default sorting order for SourceAndConverter
     * Because we want some consistency in channel ordering when exporting to an ImagePlus
     * AArrg indexes are back
     *
     * TODO : find a better way to order between spimdata
     * @param sacs sources
     * @return sorted sources
     */
    public static java.util.List<SourceAndConverter<?>> sortDefault(Collection<SourceAndConverter<?>> sacs) {
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
                    return sdi1.setupId-sdi2.setupId;
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
