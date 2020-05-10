package bdv.util;

import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.SourceAndConverter;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.HyperStackConverter;
import ij.process.LUT;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.img.Img;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.display.imagej.ImgToVirtualStack;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.*;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.imglib2.cache.img.DiskCachedCellImgOptions.CacheType;
import org.janelia.saalfeldlab.n5.imglib2.RandomAccessibleLoader;

import static net.imglib2.img.basictypeaccess.AccessFlags.VOLATILE;
import static net.imglib2.type.PrimitiveType.BYTE;
import static net.imglib2.type.PrimitiveType.DOUBLE;
import static net.imglib2.type.PrimitiveType.FLOAT;
import static net.imglib2.type.PrimitiveType.INT;
import static net.imglib2.type.PrimitiveType.LONG;
import static net.imglib2.type.PrimitiveType.SHORT;


import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

/**
 * Helper class that facilitate compatibility for going forth and back between bdv and ImagePlus
 * The affine transform located an ImagePlus in 3D cannot be properly defined using the ij.measure.Calibration class
 * - Inner trick used : store and retrieve the affine transform from within the ImagePlus "info" property
 * - This allows to support saving the image as tiff and being able to retrieve its location when loading it
 * - As well, cropping and scaling the image is allowed because the xOrigin, yOrigin (and maybe zOrigin, not tested)
 * allows to compute the offset relative to the original dataset
 * - Calibration is still used in order to store all the useful information that can be contained within it
 * (and is useful for the proper scaling retrieval)
 *
 * - Time Origin is another property which is stored in the image info property. This allows to export and import
 * dataset which are 'cropped in time'
 *
 * @author Nicolas Chiaruttini, EPFL, 2020
 */

public class ImagePlusHelper {

    /**
     * Regex matching the toString function of AffineTransform3D
     */
    final public static String regexAffineTransform3D = "(3d-affine: \\()(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.*)\\)";

    public static void storeExtendedCalibrationToImagePlus(ImagePlus imp, AffineTransform3D at3D, String unit, int timePointBegin) {
        storeMatrixToImagePlus(imp, at3D);
        setTimeOriginToImagePlus(imp, timePointBegin);
        if (unit!=null)
            imp.getCalibration().setUnit(unit);
    }

    static void storeMatrixToImagePlus(ImagePlus imp, AffineTransform3D at3D) {
        Calibration cal = new Calibration();

        double[] m = at3D.getRowPackedCopy();
        double voxX = Math.sqrt(m[0]*m[0]+m[4]*m[4]+m[8]*m[8]);
        double voxY = Math.sqrt(m[1]*m[1]+m[5]*m[5]+m[9]*m[9]);
        double voxZ = Math.sqrt(m[2]*m[2]+m[6]*m[6]+m[10]*m[10]);

        cal.pixelWidth = voxX;
        cal.pixelHeight = voxY;
        cal.pixelDepth = voxZ;

        cal.xOrigin = 1; // Ignored if set to zero
        cal.yOrigin = 1;
        cal.zOrigin = 1;

        imp.setCalibration(cal);

        // Calibration is not enough Inner trick : use ImagePlus info property to store matrix of transformation
        if (imp.getInfoProperty() == null) {
            imp.setProperty("Info", " "); // One character should be present
        }
        String info = imp.getInfoProperty();

        // Removes any previously existing stored affine transform
        info = info.replaceAll(regexAffineTransform3D, "");

        // Appends matrix data
        info += at3D.toString() + "\n";

        imp.setProperty("Info", info);

    }

    public static AffineTransform3D getMatrixFromImagePlus(ImagePlus imp) {

        // Checks whether the AffineTransform is defined in ImagePlus "info" property
        if (imp.getInfoProperty()!=null) {
            AffineTransform3D at3D = new AffineTransform3D();
            Pattern pattern = Pattern.compile(regexAffineTransform3D);
            Matcher matcher = pattern.matcher(imp.getInfoProperty());
            if (matcher.find()) {
                // Looks good, we have something that looks like an affine transform
                double[] m = new double[12];
                for (int i=0;i<12;i++) {
                    m[i] = Double.valueOf(matcher.group(i+2));
                }
                at3D.set(m);

                double[] offsetLocalCoordinates =
                        {imp.getCalibration().xOrigin-1,
                         imp.getCalibration().yOrigin-1,
                         imp.getCalibration().zOrigin-1};

                double[] offsetGlobalCoordinates = new double[3];

                double m03 = at3D.get(0,3);
                double m13 = at3D.get(1,3);
                double m23 = at3D.get(2,3);

                at3D.translate(-m03, -m13, -m23);

                at3D.apply(offsetLocalCoordinates, offsetGlobalCoordinates);

                at3D.translate(
                        m03 - offsetGlobalCoordinates[0],
                        m13 - offsetGlobalCoordinates[1],
                        m23 - offsetGlobalCoordinates[2]
                        );

                m03 = at3D.get(0,3);
                m13 = at3D.get(1,3);
                m23 = at3D.get(2,3);


                // Size
                double voxX = Math.sqrt(m[0]*m[0]+m[4]*m[4]+m[8]*m[8]);
                double voxY = Math.sqrt(m[1]*m[1]+m[5]*m[5]+m[9]*m[9]);
                double voxZ = Math.sqrt(m[2]*m[2]+m[6]*m[6]+m[10]*m[10]);

                double scaleX = imp.getCalibration().pixelWidth / voxX;
                double scaleY = imp.getCalibration().pixelHeight / voxY ;
                double scaleZ = imp.getCalibration().pixelDepth / voxZ;

                m[0]*=scaleX;m[4]*=scaleX;m[8]*=scaleX;
                m[1]*=scaleY;m[5]*=scaleY;m[9]*=scaleY;
                m[2]*=scaleZ;m[6]*=scaleZ;m[10]*=scaleZ;

                m[3] = m03;
                m[7] = m13;
                m[11] = m23;

                at3D.set(m);

                return at3D;
            } else {
               // Affine transform not found in ImagePlus Info
            }
        }

        // Otherwise : use Calibration from ImagePlus
        if (imp.getCalibration()!=null) {
            AffineTransform3D at3D = new AffineTransform3D();
            //Matrix built from calibration
            at3D.scale(imp.getCalibration().pixelWidth,
                       imp.getCalibration().pixelHeight,
                       imp.getCalibration().pixelDepth );
            at3D.translate(imp.getCalibration().xOrigin * imp.getCalibration().pixelWidth,
                    imp.getCalibration().yOrigin * imp.getCalibration().pixelHeight,
                    imp.getCalibration().zOrigin * imp.getCalibration().pixelDepth
                    );
            return at3D;
        }


        // Default : returns identity
        AffineTransform3D at3D = new AffineTransform3D();
        return at3D;
    }

    /**
     * Regex matching the toString function of AffineTransform3D
     */
    final public static String regexTimePointOrigin= "(TimePoint: \\()(.+)\\)";

    // TODO
    static void setTimeOriginToImagePlus(ImagePlus imp, int timePoint) {
         if (imp.getInfoProperty() == null) {
            imp.setProperty("Info", " "); // One character should be present
        }
        String info = imp.getInfoProperty();

        // Removes any previously existing stored time origin
        info = info.replaceAll(regexTimePointOrigin, "");

        // Appends time origin data
        info += "TimePoint: ("+timePoint+")\n";

        imp.setProperty("Info", info);
    }

    // TODO
    public static int getTimeOriginFromImagePlus(ImagePlus imp) {
        // Checks whether the time origin is defined in ImagePlus "info" property
        if (imp.getInfoProperty()!=null) {
            Pattern pattern = Pattern.compile(regexTimePointOrigin);
            Matcher matcher = pattern.matcher(imp.getInfoProperty());
            if (matcher.find()) {
                // Looks good, we have something that looks like an affine transform
                int timeOrigin = Integer.valueOf(matcher.group(2));
                return timeOrigin;
            }
        }
        return 0;
    }

    /**
     *
     * @param sac
     * @param mipmapLevel
     * @return
     */

    public static ImagePlus wrap(SourceAndConverter sac, ConverterSetup cs, int mipmapLevel, int beginTimePoint, int endTimePoint, boolean ignoreSourceLut) {

        // Avoids no mip map exception
        mipmapLevel = Math.min(mipmapLevel, sac.getSpimSource().getNumMipmapLevels()-1);
        RandomAccessibleInterval[] rais = new RandomAccessibleInterval[endTimePoint-beginTimePoint];
        for (int i=beginTimePoint;i<endTimePoint;i++) {
            rais[i] = sac.getSpimSource().getSource(i,mipmapLevel);
        }

        ImgPlus imgPlus;
        ImagePlus imp;
        if (true) {
            imgPlus = new ImgPlus(cacheRAI(Views.stack(rais)),
                sac.getSpimSource().getName(),
                new AxisType[] { Axes.X, Axes.Y, Axes.Z, Axes.TIME } );
            imp = ImageJFunctions.wrap(imgPlus, "");
        } else {
            imp = ImageJFunctions.wrap(Views.stack(rais), sac.getSpimSource().getName());
        }

        imp.setTitle(sac.getSpimSource().getName());

        imp.setDimensions(1, (int) rais[0].dimension(2), endTimePoint-beginTimePoint); // Set 3 dimension as Z, not as Channel

        System.out.println("ignoreSourceLut:"+ignoreSourceLut);
        System.out.println("cs:"+cs);
        // Simple Color LUT
        if ((!ignoreSourceLut)&&(cs!=null)) {
            System.out.println("Settings the settings");
            ARGBType c = cs.getColor();
            imp.setLut(LUT.createLutFromColor(new Color(ARGBType.red(c.get()), ARGBType.green(c.get()), ARGBType.blue(c.get()))));
            imp.setDisplayRange(cs.getDisplayRangeMin(),cs.getDisplayRangeMax());
        }

        return imp;
    }

    /**
     *
     * @return
     */
    public static ImagePlus wrap(List<SourceAndConverter> sacs,
                                 Map<SourceAndConverter,ConverterSetup> csMap,
                                 Map<SourceAndConverter,Integer> mipmapMap,
                                 int beginTimePoint,
                                 int endTimePoint,
                                 boolean ignoreSourceLut) {

        if (sacs.size()==1) {
            return wrap(sacs.get(0), csMap.get(sacs.get(0)), mipmapMap.get(sacs.get(0)), beginTimePoint, endTimePoint, ignoreSourceLut);
        }

        RandomAccessibleInterval[] raisList = new RandomAccessibleInterval[sacs.size()];

        for (int c=0;c<sacs.size();c++) {
            SourceAndConverter sac = sacs.get(c);
            RandomAccessibleInterval[] rais = new RandomAccessibleInterval[endTimePoint-beginTimePoint];
            int mipmapLevel = Math.min(mipmapMap.get(sac), sac.getSpimSource().getNumMipmapLevels()-1); // mipmap level should exist
            long xSize = 1, ySize = 1, zSize = 1;
            for (int t=beginTimePoint;t<endTimePoint;t++) {
                if (sac.getSpimSource().isPresent(t)) {
                    rais[t-beginTimePoint] = sac.getSpimSource().getSource(t, mipmapLevel);
                    xSize = rais[t-beginTimePoint].dimension(0);
                    ySize = rais[t-beginTimePoint].dimension(1);
                    zSize = rais[t-beginTimePoint].dimension(2);
                    break;
                }
            }

            for (int t=beginTimePoint;t<endTimePoint;t++) {
                if (sac.getSpimSource().isPresent(t)) {
                    rais[t-beginTimePoint] = sac.getSpimSource().getSource(t, mipmapLevel);
                } else {
                   rais[t-beginTimePoint] = new ZerosRAI((NumericType)sac.getSpimSource().getType(), new long[]{xSize,ySize,zSize});
                }
            }

            raisList[c] = Views.stack(rais);
        }
        /*
        wrapAsVolatileCachedCellImg(raisList, new int[]{(int) raisList[0].dimension(0),
                                                        (int) raisList[0].dimension(1),
                                                        })*/
        ImgPlus imgPlus = new ImgPlus(cacheRAI(Views.stack(raisList)),
                "",
                new AxisType[] { Axes.X, Axes.Y, Axes.Z, Axes.TIME, Axes.CHANNEL } );
        ImagePlus imp = HyperStackConverter.toHyperStack(ImgToVirtualStack.wrap(imgPlus),
                sacs.size(), (int) raisList[0].dimension(2), endTimePoint-beginTimePoint, "composite");

        if ((!ignoreSourceLut)) {
            LUT[] luts = new LUT[sacs.size()];
            for (SourceAndConverter sac:sacs) {
                if (csMap.get(sac)!=null) {
                    ARGBType c = csMap.get(sac).getColor();
                    LUT lut;
                    if (c!=null) {
                        lut = LUT.createLutFromColor(new Color(ARGBType.red(c.get()), ARGBType.green(c.get()), ARGBType.blue(c.get())));
                    } else {
                        lut = LUT.createLutFromColor(new Color(ARGBType.red(255), ARGBType.green(255), ARGBType.blue(255)));
                    }
                    luts[sacs.indexOf(sac)] = lut;
                    imp.setC(sacs.indexOf(sac)+1);
                    imp.getProcessor().setLut(lut);
                    imp.setDisplayRange(csMap.get(sac).getDisplayRangeMin(),csMap.get(sac).getDisplayRangeMax());
                }
            }
            ((CompositeImage)imp).setLuts(luts);

        }

        return imp;
    }
    //<T extends NativeType<T>>
    public static< T extends NumericType<T> & NativeType<T>> Img cacheRAI(RandomAccessibleInterval<T> source) {
        final int[] cellDimensions = new int[source.numDimensions()];
        cellDimensions[0] = (int) (source.dimension(0)); // X
        cellDimensions[1] = (int) (source.dimension(1)); // Y

        for (int d=2;d<source.numDimensions();d++){ // Z C T
            cellDimensions[d] = 1;
        }

        final DiskCachedCellImgFactory<T> factory = new DiskCachedCellImgFactory<>(Util.getTypeFromInterval(source),
                options()
                .cellDimensions( cellDimensions )
                .cacheType( CacheType.BOUNDED )
                .maxCacheSize( 100 ) );

        return factory.create(source, target -> {
            Cursor< T > targetCursor = target.localizingCursor();
            RandomAccess< T > sourceRandomAccess = source.randomAccess();
            // iterate over the input cursor
            while ( targetCursor.hasNext())
            {
                // move input cursor forward
                targetCursor.fwd();

                // set the output cursor to the position of the input cursor
                sourceRandomAccess.setPosition( targetCursor );

                // set the value of this pixel of the output image, every Type supports T.set( T type )
                targetCursor.get().set( sourceRandomAccess.get() );
            }

        }, options().initializeCellsAsDirty(true));
    }

    @SuppressWarnings( { "unchecked", "rawtypes" } )
    public static final <T extends NativeType<T>> RandomAccessibleInterval<T> wrapAsVolatileCachedCellImg(
            final RandomAccessibleInterval<T> source,
            final int[] blockSize) {

        final long[] dimensions = Intervals.dimensionsAsLongArray(source);
        final CellGrid grid = new CellGrid(dimensions, blockSize);

        final RandomAccessibleLoader<T> loader = new RandomAccessibleLoader<T>(Views.zeroMin(source));

        final T type = Util.getTypeFromInterval(source);

        final CachedCellImg<T, ?> img;
        final Cache<Long, Cell<?>> cache =
                new SoftRefLoaderCache().withLoader(LoadedCellCacheLoader.get(grid, loader, type, AccessFlags.setOf(VOLATILE)));

        if (GenericByteType.class.isInstance(type)) {
            img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(BYTE, AccessFlags.setOf(VOLATILE)));
        } else if (GenericShortType.class.isInstance(type)) {
            img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(SHORT, AccessFlags.setOf(VOLATILE)));
        } else if (GenericIntType.class.isInstance(type)) {
            img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(INT, AccessFlags.setOf(VOLATILE)));
        } else if (GenericLongType.class.isInstance(type)) {
            img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(LONG, AccessFlags.setOf(VOLATILE)));
        } else if (FloatType.class.isInstance(type)) {
            img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(FLOAT, AccessFlags.setOf(VOLATILE)));
        } else if (DoubleType.class.isInstance(type)) {
            img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(DOUBLE, AccessFlags.setOf(VOLATILE)));
        } else {
            img = null;
        }

        return img;
    }

}
