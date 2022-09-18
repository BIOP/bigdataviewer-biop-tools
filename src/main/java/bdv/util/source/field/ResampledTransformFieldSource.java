package bdv.util.source.field;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Sampler;
import net.imglib2.algorithm.lazy.Caches;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.converter.Converters;
import net.imglib2.converter.readwrite.SamplerConverter;
import net.imglib2.converter.readwrite.WriteConvertedRandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.GenericByteType;
import net.imglib2.type.numeric.integer.GenericIntType;
import net.imglib2.type.numeric.integer.GenericLongType;
import net.imglib2.type.numeric.integer.GenericShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;
import net.imglib2.view.composite.Composite;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.GenericComposite;
import sc.fiji.bdvpg.cache.GlobalLoaderCache;

import java.util.concurrent.ConcurrentHashMap;

import static net.imglib2.img.basictypeaccess.AccessFlags.VOLATILE;
import static net.imglib2.type.PrimitiveType.BYTE;
import static net.imglib2.type.PrimitiveType.DOUBLE;
import static net.imglib2.type.PrimitiveType.FLOAT;
import static net.imglib2.type.PrimitiveType.INT;
import static net.imglib2.type.PrimitiveType.LONG;
import static net.imglib2.type.PrimitiveType.SHORT;

public class ResampledTransformFieldSource implements ITransformFieldSource<NativeRealPoint3D> {

    final RealTransform origin;
    final Source<?> resamplingModel;
    final String name;
    final RealPoint3DInterpolatorFactory interpolator = new RealPoint3DInterpolatorFactory();

    /**
     * Hashmap to cache RAIs (mipmaps and timepoints)
     */
    final transient ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, RandomAccessibleInterval<NativeRealPoint3D>>> cachedRAIs =
            new ConcurrentHashMap<>();

    public ResampledTransformFieldSource(RealTransform origin, Source resamplingModel, String name) throws UnsupportedOperationException {
        if ((origin.numTargetDimensions()!=3)||(origin.numSourceDimensions()!=3)) throw new UnsupportedOperationException("Only 3d to 3d transforms are supported");
        this.origin = origin;
        this.resamplingModel = resamplingModel;
        this.name = name;
    }

    @Override
    public int numSourceDimensions() {
        return origin.numSourceDimensions();
    }

    @Override
    public int numTargetDimensions() {
        return origin.numTargetDimensions();
    }

    @Override
    public RealTransform getTransform() {
        return origin;
    }

    @Override
    public boolean isPresent(int t) {
        return resamplingModel.isPresent(t);
    }

    public RandomAccessibleInterval<NativeRealPoint3D> buildSource(int t, int level) {
        // Get current model source transformation
        AffineTransform3D at = new AffineTransform3D();
        resamplingModel.getSourceTransform(t, level, at);

        // Get bounds of model source RAI
        // TODO check if -1 is necessary
        long sx = resamplingModel.getSource(t, level).dimension(0) - 1;
        long sy = resamplingModel.getSource(t, level).dimension(1) - 1;
        long sz = resamplingModel.getSource(t, level).dimension(2) - 1;

        RealTransform transformCopy = origin.copy();

        // Get field of origin source
        final RealRandomAccessible<NativeRealPoint3D> ipimg =
                new FunctionRealRandomAccessible<>(3, (position, value) -> {
            transformCopy.apply(position, value);
        }, this::getType);

        // Gets randomAccessible... ( with appropriate transform )
        at = at.inverse();
        RandomAccessible<NativeRealPoint3D> ra = RealViews.affine(ipimg, at); // Gets the view

        // ... interval
        RandomAccessibleInterval<NativeRealPoint3D> view =
                Views.interval(ra, new long[] { 0, 0,
                0 }, new long[] { sx, sy, sz }); // Sets the interval

        return view;
    }

    @Override
    public RandomAccessibleInterval<NativeRealPoint3D> getSource(int t, int level) {
        if (!cachedRAIs.containsKey(t)) {
            cachedRAIs.put(t, new ConcurrentHashMap<>());
        }

        if (!cachedRAIs.get(t).containsKey(level)) {
            RandomAccessibleInterval<NativeRealPoint3D> nonCached = buildSource(t, level);

            int[] blockSize = { 64, 64, 1 };

            if (nonCached.dimension(0) < 64) blockSize[0] = (int) nonCached
                    .dimension(0);
            if (nonCached.dimension(1) < 64) blockSize[1] = (int) nonCached
                    .dimension(1);
            if (nonCached.dimension(2) < 64) blockSize[2] = (int) nonCached
                    .dimension(2);

            cachedRAIs.get(t).put(level, wrapAsVolatileCachedCellImg(
                    nonCached, blockSize, this, t, level));
        }
        return cachedRAIs.get(t).get(level);
    }

    @Override
    public RealRandomAccessible<NativeRealPoint3D> getInterpolatedSource(int t, int level, Interpolation method) {
        ExtendedRandomAccessibleInterval<NativeRealPoint3D, RandomAccessibleInterval<NativeRealPoint3D>> eView =
        Views.extendBorder(getSource(t, level));
        
        @SuppressWarnings("UnnecessaryLocalVariable")
        RealRandomAccessible<NativeRealPoint3D> realRandomAccessible = Views.interpolate(eView, interpolator);
        return realRandomAccessible;
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        resamplingModel.getSourceTransform(t, level, transform);
    }

    @Override
    public NativeRealPoint3D getType() {
        return new NativeRealPoint3D();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return resamplingModel.getVoxelDimensions();
    }

    @Override
    public int getNumMipmapLevels() {
        return resamplingModel.getNumMipmapLevels();
    }


    public static <T extends NativeType<T>> RandomAccessibleInterval<T>
    wrapAsVolatileCachedCellImg(final RandomAccessibleInterval<T> source,
                                final int[] blockSize, Object objectSource, int timepoint, int level)
    {

        final long[] dimensions = Intervals.dimensionsAsLongArray(source);
        final CellGrid grid = new CellGrid(dimensions, blockSize);

        final Caches.RandomAccessibleLoader<T> loader =
                new Caches.RandomAccessibleLoader<>(Views.zeroMin(source));

        final T type = Util.getTypeFromInterval(source);

        final CachedCellImg<T, ?> img;
        final Cache<Long, Cell<?>> cache = new GlobalLoaderCache(objectSource,
                timepoint, level).withLoader(LoadedCellCacheLoader.get(grid, loader, type,
                AccessFlags.setOf(VOLATILE)));

        img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(
                FLOAT, AccessFlags.setOf(VOLATILE)));

        return img;
    }

}
