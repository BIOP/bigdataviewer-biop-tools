package bdv.util.source.field;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.lazy.Caches;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.util.Intervals;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.cache.GlobalLoaderCache;

import java.util.concurrent.ConcurrentHashMap;

import static net.imglib2.img.basictypeaccess.AccessFlags.DIRTY;
import static net.imglib2.type.PrimitiveType.FLOAT;

public class ResampledTransformFieldSource implements ITransformFieldSource<NativeRealPoint3D> {

    final RealTransform origin;
    final Source<?> resamplingModel;
    final String name;
    final RealPoint3DInterpolatorFactory interpolator = new RealPoint3DInterpolatorFactory();

    final ThreadLocal<RealTransform> localTransform = new ThreadLocal<>();

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

        // Get field of origin source
        final RealRandomAccessible<NativeRealPoint3D> ipimg =
                new FunctionRealRandomAccessible<>(3,
                        (location, value) -> {
                            if (localTransform.get()==null) {
                                localTransform.set(origin.copy());
                            }
                            localTransform.get().apply(location, value);
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

            int[] blockSize = { 128, 128, 32 };

            if (nonCached.dimension(0) < 128) blockSize[0] = (int) nonCached
                    .dimension(0);
            if (nonCached.dimension(1) < 128) blockSize[1] = (int) nonCached
                    .dimension(1);
            if (nonCached.dimension(2) < 32) blockSize[2] = (int) nonCached
                    .dimension(2);

            cachedRAIs.get(t).put(level, compute(
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

    // ABYSMAL PERFORMANCE
    /*public static RandomAccessibleInterval<NativeRealPoint3D>
    wrapAsVolatileCachedCellImg(final RandomAccessibleInterval<NativeRealPoint3D> source,
                                final int[] blockSize, Object objectSource, int timepoint, int level)
    {

        final long[] dimensions = Intervals.dimensionsAsLongArray(source);
        final CellGrid grid = new CellGrid(dimensions, blockSize);

        final Caches.RandomAccessibleLoader<NativeRealPoint3D> loader =
                new Caches.RandomAccessibleLoader<>(Views.zeroMin(source));

        final CachedCellImg<NativeRealPoint3D, ?> img;
        final Cache<Long, Cell<NativeRealPoint3D>> cache =
                new GlobalLoaderCache(objectSource, timepoint, level)
                          .withLoader(LoadedCellCacheLoader.get(grid, loader, new NativeRealPoint3D(),
                AccessFlags.setOf(DIRTY)));

        img = new CachedCellImg(grid, new NativeRealPoint3D(), cache, ArrayDataAccessFactory.get(
                FLOAT, AccessFlags.setOf(DIRTY)));

        return img;
    }*/

    public RandomAccessibleInterval<NativeRealPoint3D>
    compute(final RandomAccessibleInterval<NativeRealPoint3D> source,
                                final int[] blockSize, Object objectSource, int timepoint, int level)
    {

        final RandomAccessibleInterval< NativeRealPoint3D > deformationField = new ArrayImgFactory( new NativeRealPoint3D() ).create( source );
         Grids.collectAllContainedIntervals(source.dimensionsAsLongArray(), blockSize)
                .parallelStream()
                .forEach( blockinterval -> {
                            Cursor<NativeRealPoint3D> targetCursor = Views.interval(deformationField,blockinterval).localizingCursor();

                            RandomAccess< NativeRealPoint3D > sourceRandomAccess = source.randomAccess();

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
                        }
                    );
        return deformationField;
    }

}
