package ch.epfl.biop.bdv.vsiopener;

import bdv.util.DefaultInterpolators;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import loci.formats.ImageReader;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.*;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;

import java.util.concurrent.ConcurrentHashMap;


/**
 * Useful resources:
 * https://github.com/ome/bio-formats-examples/blob/master/src/main/java/ReadPhysicalSize.java
 *
 * All the bio-formats-examples repository on GitHub
 *
 * @param <T>
 */

public abstract class VSIBdvSource<T extends NumericType< T > > implements Source<T> {

    protected final DefaultInterpolators< T > interpolators = new DefaultInterpolators<>();

    volatile ImageReader reader;

    final VoxelDimensions voxelsDimensions;

    final int numDimensions;

    final boolean is8bit;

    final boolean is16bits;

    final boolean is24bitsRGB;

    final boolean switchZandC;

    public int cChannel = 1;

    double pXmm, pYmm, pZmm, dXmm, dYmm, dZmm; // Location of acquisition

    AffineTransform3D rootTransform = new AffineTransform3D();

    public VSIBdvSource(ImageReader reader, int image_index, int channel_index, boolean swZC) {
        this.switchZandC=swZC;
        this.reader = reader;
        this.reader.setSeries(image_index);
        this.cChannel = channel_index;

        // Get image size
        final IMetadata omeMeta = (IMetadata) reader.getMetadataStore();
        final Length physSizeX = omeMeta.getPixelsPhysicalSizeX(image_index);
        final Length physSizeY = omeMeta.getPixelsPhysicalSizeY(image_index);
        final Length physSizeZ = omeMeta.getPixelsPhysicalSizeZ(image_index);
        //System.out.println("getchannelsamples="+omeMeta.getChannelSamplesPerPixel(image_index, 0));
        //System.out.println("getchannelcount"+omeMeta.getChannelCount(image_index));

        is24bitsRGB = (omeMeta.getPixelsType(image_index) == PixelType.UINT8)&&(omeMeta.getChannelSamplesPerPixel(image_index, 0) == PositiveInteger.valueOf("3"));
        is8bit = (omeMeta.getPixelsType(image_index) == PixelType.UINT8)&&(!is24bitsRGB);
        is16bits = (omeMeta.getPixelsType(image_index) == PixelType.UINT16)&&(!is24bitsRGB);

        pXmm = omeMeta.getPlanePositionX(image_index, 0).value(UNITS.MILLIMETER).doubleValue();
        pYmm = omeMeta.getPlanePositionY(image_index, 0).value(UNITS.MILLIMETER).doubleValue();

        dXmm = omeMeta.getPixelsPhysicalSizeX(image_index).value(UNITS.MILLIMETER).doubleValue();
        dYmm = omeMeta.getPixelsPhysicalSizeY(image_index).value(UNITS.MILLIMETER).doubleValue();

        if (physSizeZ==null) {
            pZmm=0;
            dZmm=1;
        } else {
            pZmm = omeMeta.getPlanePositionZ(image_index, 0).value(UNITS.MILLIMETER).doubleValue();
            dZmm = omeMeta.getPixelsPhysicalSizeZ(image_index).value(UNITS.MILLIMETER).doubleValue();
        }

        rootTransform.identity();

        rootTransform.set(
                dXmm,0   ,0   ,0,
                0   ,dYmm,0   ,0,
                0   ,0   ,dZmm,0,
                0   ,0   ,0   ,1
        );
        rootTransform.translate(pXmm, pYmm, pZmm);

        assert physSizeX!=null;
        assert physSizeY!=null;

        numDimensions = 2 + (physSizeZ!=null?1:0);

        //System.out.println("numDimensions = "+numDimensions);

        if (numDimensions==2) {
            voxelsDimensions = new VoxelDimensions() {

                final Unit<Length> targetUnit = UNITS.MICROMETER;

                double[] dims = {1,1};

                @Override
                public String unit() {
                    return targetUnit.getSymbol();
                }

                @Override
                public void dimensions(double[] doubles) {
                    doubles[0] = dims[0];
                    doubles[1] = dims[1];
                }

                @Override
                public double dimension(int i) {
                    return dims[i];
                }

                @Override
                public int numDimensions() {
                    return numDimensions;
                }
            };
        } else {
            assert numDimensions == 3;
            voxelsDimensions = new VoxelDimensions() {

                final Unit<Length> targetUnit = UNITS.MICROMETER;

                double[] dims = {1,1,1};

                @Override
                public String unit() {
                    return targetUnit.getSymbol();
                }

                @Override
                public void dimensions(double[] doubles) {
                    doubles[0] = dims[0];
                    doubles[1] = dims[1];
                    doubles[2] = dims[2];
                }

                @Override
                public double dimension(int i) {
                    return dims[i];
                }

                @Override
                public int numDimensions() {
                    return numDimensions;
                }
            };
        }

        // Get image transforms

    }

    @Override
    public boolean isPresent(int t) {
        return t==0; // TODO better handling of 0
    }



    /**
     * The core of the source...
     * @param t
     * @param level
     * @return
     */
    abstract public RandomAccessibleInterval<T> createSource(int t, int level);

    boolean fixedLevel = false;
    boolean lowerLevel = false;
    int minLevel = 2;
    int cLevel = 4;

    @Override
    public RandomAccessibleInterval<T> getSource(int t, int level) {
        if (fixedLevel) {level=cLevel;}
        if ((lowerLevel)&&(level<minLevel)) {level=minLevel;}
        if (raiMap.containsKey(t)) {
            if (raiMap.get(t).containsKey(level)) {
                return raiMap.get(t).get(level);
            }
        }
        return createSource(t,level);
    }

    @Override
    public RealRandomAccessible<T> getInterpolatedSource(int t, int level, Interpolation method) {

        final T zero = getType();
        zero.setZero();

        ExtendedRandomAccessibleInterval<T, RandomAccessibleInterval< T >>
                eView = Views.extendZero(getSource( t, level ));//extendValue(getSource( t, level ), zero);

        RealRandomAccessible< T > realRandomAccessible = Views.interpolate( eView, interpolators.get(method) );

        return realRandomAccessible;

    }

    volatile ConcurrentHashMap<Integer, AffineTransform3D> transforms = new ConcurrentHashMap<>();
    volatile ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, RandomAccessibleInterval<T>>> raiMap = new ConcurrentHashMap<>();

    // Finds transforms assuming scaling by factor 2 on each level

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        // Ignoring t parameters : assuming all transforms are identical over time
        // TODO How is the pyramid in 3D ?
        if (fixedLevel) {
            level=cLevel;
            transform.set(transforms.get(level));
        }
        if ((lowerLevel)&&(level<minLevel)) {
            level=minLevel;
            transform.set(transforms.get(level));
        }
        if (!transforms.contains(level)) {
            if (level==0) {
                //AffineTransform3D tr = new AffineTransform3D();
                //tr.identity();
                transforms.put(0, this.rootTransform);
            } else {
                // Recursive call to previous level, down to zero
                AffineTransform3D tr = new AffineTransform3D();
                tr.set(rootTransform);
                tr.translate(-pXmm, -pYmm, -pZmm);
                tr.scale(Math.pow(2, level)); // Assert powers of two scaling in x, y, z
                tr.translate(pXmm, pYmm, pZmm);
                transforms.put(level, tr);
            }
        }
        transform.set(transforms.get(level));
        /**/
    }

    @Override
    public T getType() {
        if (is8bit) return (T) new UnsignedByteType();
        if (is16bits) return (T) new UnsignedShortType();
        if (is24bitsRGB) return (T) new ARGBType();
        return null;
    }

    @Override
    public String getName() {
        return "NoName";
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return voxelsDimensions;
    }

    @Override
    public int getNumMipmapLevels() {
        return reader.getResolutionCount();
    }

}
