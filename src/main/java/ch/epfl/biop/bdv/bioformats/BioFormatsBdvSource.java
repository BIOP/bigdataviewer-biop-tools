package ch.epfl.biop.bdv.bioformats;

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
 * BigDataViewer multiresolution source built from BioFormat reader
 *
 * Limitations:
 * - 3D unsupported yet
 * - Unsigned Byte, Unsigned Short, RGB channel only
 * - Location of acquisition is supposed to be independent of time -> no live tracking object
 * - NumericType requirement is just for Zero extension out of bounds strategy
 *
 * Useful resources:
 * https://github.com/ome/bio-formats-examples/blob/master/src/main/java/ReadPhysicalSize.java
 * All the bio-formats-examples repository on GitHub
 *
 * @param <T> pixel type
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL, 2019
 */

public abstract class BioFormatsBdvSource<T extends NumericType< T > > implements Source<T> {

    protected final DefaultInterpolators< T > interpolators = new DefaultInterpolators<>();

    // Bioformat reader
    volatile ImageReader reader;

    // Inner VoxelDimensions, taken from BioFormats
    final VoxelDimensions voxelsDimensions;

    // 2D or 3D supported (3D TODO)
    final int numDimensions;

    // UnsignedByte channel flag
    final boolean is8bit;

    // UnsignedShort channel flag
    final boolean is16bits;

    // 24 bits RGB channel flag
    final boolean is24bitsRGB;

    // Fix BioFormat confusion between c and z in some file formats
    public boolean switchZandC;

    // Channel index of the current source
    public int cChannel;

    // Number of timepoints of the source
    public int numberOfTimePoints;

    // Position of dataset + voxel spacing
    public double pXmm, pYmm, pZmm, dXmm, dYmm, dZmm;

    // Affine transform of the highest resolution iamge
    AffineTransform3D rootTransform = new AffineTransform3D();

    // Concurrent HashMap containing the affine transforms of the source - limitation : they are not changing over time
    volatile ConcurrentHashMap<Integer, AffineTransform3D> transforms = new ConcurrentHashMap<>();

    // Concurrent HashMap containing the randomAccessibleInterval of the source
    volatile ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, RandomAccessibleInterval<T>>> raiMap = new ConcurrentHashMap<>();

    // Source name
    public String sourceName;

    /**
     * Bio Format source cosntructor
     * @param reader bio format reader -> flatten should be set to false to allow for multiresolution handling
     * @param image_index image index within source
     * @param channel_index channel index within source
     * @param swZC switch or not z and c
     */
    public BioFormatsBdvSource(ImageReader reader, int image_index, int channel_index, boolean swZC) {

        this.switchZandC=swZC;
        this.reader = reader;
        this.reader.setSeries(image_index);
        this.cChannel = channel_index;
        this.numberOfTimePoints = this.reader.getSizeT();

        // Get image size
        final IMetadata omeMeta = (IMetadata) reader.getMetadataStore();
        final Length physSizeX = omeMeta.getPixelsPhysicalSizeX(image_index);
        final Length physSizeY = omeMeta.getPixelsPhysicalSizeY(image_index);
        final Length physSizeZ = omeMeta.getPixelsPhysicalSizeZ(image_index);

        // SourceName
        this.sourceName = omeMeta.getImageName(image_index)+"_ch_"+omeMeta.getChannelName(image_index, channel_index);

        // Get image type
        is24bitsRGB = (omeMeta.getPixelsType(image_index) == PixelType.UINT8)&&(omeMeta.getChannelSamplesPerPixel(image_index, 0) == PositiveInteger.valueOf("3"));
        is8bit = (omeMeta.getPixelsType(image_index) == PixelType.UINT8)&&(!is24bitsRGB);
        is16bits = (omeMeta.getPixelsType(image_index) == PixelType.UINT16)&&(!is24bitsRGB);

        // Get image origin and spacing
        pXmm = omeMeta.getPlanePositionX(image_index, 0).value(UNITS.MILLIMETER).doubleValue();
        pYmm = omeMeta.getPlanePositionY(image_index, 0).value(UNITS.MILLIMETER).doubleValue();

        dXmm = omeMeta.getPixelsPhysicalSizeX(image_index).value(UNITS.MILLIMETER).doubleValue();
        dYmm = omeMeta.getPixelsPhysicalSizeY(image_index).value(UNITS.MILLIMETER).doubleValue();

        // In 3D if available
        if (physSizeZ==null) {
            pZmm=0;
            dZmm=1;
        } else {
            pZmm = omeMeta.getPlanePositionZ(image_index, 0).value(UNITS.MILLIMETER).doubleValue();
            dZmm = omeMeta.getPixelsPhysicalSizeZ(image_index).value(UNITS.MILLIMETER).doubleValue();
        }

        // Sets AffineTransform of the highest resolution image from the pyramid
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

        // Sets voxel dimension object
        if (numDimensions==2) {
            voxelsDimensions = new VoxelDimensions() {

                final Unit<Length> targetUnit = UNITS.MILLIMETER;

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

                final Unit<Length> targetUnit = UNITS.MILLIMETER;

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
    }

    /**
     * Returns true if the BioFormat reader specifies a higher number of sources
     * @param t
     * @return
     */
    @Override
    public boolean isPresent(int t) {
        return t<numberOfTimePoints;
    }

    /**
     * The core function of the source -> implemented in subclasses
     * @see BioFormatsBdvRGBSource
     * @see BioFormatsBdvUnsignedByteSource
     * @see BioFormatsBdvUnsignedShortSource
     * @param t // timepoint
     * @param level // resolution level
     * @return
     */
    abstract public RandomAccessibleInterval<T> createSource(int t, int level);

    public boolean fixedLevel = false;
    public boolean lowerLevel = false;
    public int minLevel = 2;
    public int cLevel = 4;

    /**
     * Returns stored RAI of requested timepoint and resolution level
     * @param t
     * @param level
     * @return
     */
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

    /**
     * Extends to zero out of bounds strategy
     * @param t
     * @param level
     * @param method
     * @return
     */
    @Override
    public RealRandomAccessible<T> getInterpolatedSource(int t, int level, Interpolation method) {
        final T zero = getType();
        zero.setZero();
        ExtendedRandomAccessibleInterval<T, RandomAccessibleInterval< T >>
                eView = Views.extendZero(getSource( t, level ));
        RealRandomAccessible< T > realRandomAccessible = Views.interpolate( eView, interpolators.get(method) );
        return realRandomAccessible;
    }

    /**
     * Finds transform based on the root transform (highest resolution transform)
     * And assuming all resolution level cover the same area of imaging
     * @param t
     * @param level
     * @param transform
     */
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
                transforms.put(0, this.rootTransform);
            } else {
                AffineTransform3D tr = new AffineTransform3D();
                tr.set(rootTransform);

                // Apply ratio in numbers of pixel
                long nPixXLvl0 = this.getSource(t,0).dimension(0);
                long nPixXCurrentLvl = this.getSource(t,level).dimension(0);

                long nPixYLvl0 = this.getSource(t,0).dimension(1);
                long nPixYCurrentLvl = this.getSource(t,level).dimension(1);

                long nPixZLvl0 = this.getSource(t,0).dimension(2);
                long nPixZCurrentLvl = this.getSource(t,level).dimension(2);

                tr.translate(-pXmm, -pYmm, -pZmm);

                tr.set(tr.get(0,0)*((double)nPixXLvl0/(double)nPixXCurrentLvl),0,0);
                tr.set(tr.get(1,1)*((double)nPixYLvl0/(double)nPixYCurrentLvl),1,1);
                tr.set(tr.get(2,2)*((double)nPixZLvl0/(double)nPixZCurrentLvl),2,2);

                tr.translate(pXmm, pYmm, pZmm);
                transforms.put(level, tr);
            }
        }
        transform.set(transforms.get(level));
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
        return this.sourceName;
    }

    public void setName(String newName) {
        this.sourceName = newName;
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
