package bdv.util.source.field;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Sampler;
import net.imglib2.converter.Converters;
import net.imglib2.converter.readwrite.SamplerConverter;
import net.imglib2.converter.readwrite.WriteConvertedRandomAccessibleInterval;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;
import net.imglib2.view.composite.Composite;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.GenericComposite;
import java.util.concurrent.ConcurrentHashMap;

public class ResampledTransformFromSourceFieldSource implements ITransformFieldSource {

    final ITransformFieldSource origin;
    final Source<?> resamplingModel;
    final String name;
    final RealPoint3DInterpolatorFactory interpolator = new RealPoint3DInterpolatorFactory();

    /**
     * Hashmap to cache RAIs (mipmaps and timepoints)
     */
    final transient ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, RandomAccessibleInterval<RealPoint>>> cachedRAIs =
            new ConcurrentHashMap<>();

    public ResampledTransformFromSourceFieldSource(ITransformFieldSource origin, Source resamplingModel, String name) throws UnsupportedOperationException {
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
        return origin.getTransform();
    }

    @Override
    public boolean isPresent(int t) {
        return origin.isPresent(t);
    }

    public RandomAccessibleInterval<RealPoint> buildSource(int t, int level) {
        // Get current model source transformation
        AffineTransform3D at = new AffineTransform3D();
        resamplingModel.getSourceTransform(t, level, at);

        // int mipmap = getModelToOriginMipMapLevel(level);

        // Get bounds of model source RAI
        // TODO check if -1 is necessary
        long sx = resamplingModel.getSource(t, level).dimension(0) - 1;
        long sy = resamplingModel.getSource(t, level).dimension(1) - 1;
        long sz = resamplingModel.getSource(t, level).dimension(2) - 1;

        // Get field of origin source
        final RealRandomAccessible<RealPoint> ipimg = origin.getInterpolatedSource(t,0, null);

        // Gets randomAccessible... ( with appropriate transform )
        at = at.inverse();
        AffineTransform3D atOrigin = new AffineTransform3D();
        origin.getSourceTransform(t, 0, atOrigin);
        at.concatenate(atOrigin);
        RandomAccessible<RealPoint> ra = RealViews.affine(ipimg, at); // Gets the view

        // ... interval
        RandomAccessibleInterval<RealPoint> view =
                Views.interval(ra, new long[] { 0, 0,
                0 }, new long[] { sx, sy, sz }); // Sets the interval

        return view;
    }

    @Override
    public RandomAccessibleInterval<RealPoint> getSource(int t, int level) {
        if (!cachedRAIs.containsKey(t)) {
            cachedRAIs.put(t, new ConcurrentHashMap<>());
        }

        if (!cachedRAIs.get(t).containsKey(level)) {
            RandomAccessibleInterval<RealPoint> nonCached = buildSource(t, level);

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

        //return buildSource(t, level);
    }

    @Override
    public RealRandomAccessible<RealPoint> getInterpolatedSource(int t, int level, Interpolation method) {
        ExtendedRandomAccessibleInterval<RealPoint, RandomAccessibleInterval<RealPoint>> eView =
        Views.extendBorder(getSource(t, level));
        
        @SuppressWarnings("UnnecessaryLocalVariable")
        RealRandomAccessible<RealPoint> realRandomAccessible = Views.interpolate(eView, interpolator);
        return realRandomAccessible;
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        //transform.identity();
        resamplingModel.getSourceTransform(t, level, transform);
        /*AffineTransform3D transform_orig = new AffineTransform3D();
        resamplingModel.getSourceTransform(t, level, transform_orig);
        transform.set(transform_orig.inverse());*/

    }

    @Override
    public RealPoint getType() {
        return origin.getType();
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

    public RandomAccessibleInterval<RealPoint>
    wrapAsVolatileCachedCellImg(final RandomAccessibleInterval<RealPoint> source,
                                final int[] blockSize, Object objectSource, int timepoint, int level)
    {

        /*final int[] blockSize = new int[]{blockSize_points[0], blockSize_points[1], blockSize_points[2], 3};
        RandomAccessibleInterval<FloatType> source = dimensionChannels(source_points);

        final long[] dimensions = Intervals.dimensionsAsLongArray(source);

        final CellGrid grid = new CellGrid(dimensions, blockSize);

        final Caches.RandomAccessibleLoader<FloatType> loader = new Caches.RandomAccessibleLoader<>(Views.zeroMin(source));

        final FloatType type = new FloatType();

        final Cache<Long, Cell<?>> cache = new GlobalLoaderCache(objectSource,
                timepoint, level).withLoader(LoadedCellCacheLoader.get(grid, loader, type,
                AccessFlags.setOf(VOLATILE)));

        final CachedCellImg<FloatType, ?> img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(
                FLOAT, AccessFlags.setOf(VOLATILE)));

        return mergeDimensions(img);*/

        int nElements = (int)(source.dimension(0)*source.dimension(1)*source.dimension(2)*3);

        //float[] backingArray = new float[nElements];

        Cursor<RealPoint> cursor = Views.flatIterable(source).localizingCursor();
        int i = 0;
        final float[][][][] backingArray = new float[(int)source.dimension(0)][(int)source.dimension(1)][(int)source.dimension(2)][3];
        float[] location = new float[3];

        AffineTransform3D transform3D = new AffineTransform3D();
        getSourceTransform(0,0,transform3D);
        while(cursor.hasNext()) {
            cursor.fwd();
            cursor.get().localize(location);
            int xp = cursor.getIntPosition(0);
            int yp = cursor.getIntPosition(1);
            int zp = cursor.getIntPosition(2);
            backingArray[xp][yp][zp][0] = location[0];
            backingArray[xp][yp][zp][1] = location[1];
            backingArray[xp][yp][zp][2] = location[2];
        }
        FunctionRandomAccessible<RealPoint> rai = new FunctionRandomAccessible<>(3,(loc, point) -> {
            float[] coordinates = backingArray[loc.getIntPosition(0)] [loc.getIntPosition(1)][loc.getIntPosition(2)];
            point.setPosition(coordinates);
        }, () -> new RealPoint(3));

        return Views.interval(rai, source);
    }

    /*public static RandomAccessibleInterval<RealPoint>
    wrapAsVolatileCachedCellImg(final RandomAccessibleInterval<RealPoint> source_points,
                                final int[] blockSize_points, Object objectSource, int timepoint, int level)
    {

        final int[] blockSize = new int[]{blockSize_points[0], blockSize_points[1], blockSize_points[2], 3};
        RandomAccessibleInterval<FloatType> source = dimensionChannels(source_points);

        final long[] dimensions = Intervals.dimensionsAsLongArray(source);

        final CellGrid grid = new CellGrid(dimensions, blockSize);
        
        final Caches.RandomAccessibleLoader<FloatType> loader = new Caches.RandomAccessibleLoader<>(Views.zeroMin(source));

        final FloatType type = new FloatType();
        
        final Cache<Long, Cell<?>> cache = new GlobalLoaderCache(objectSource,
                timepoint, level).withLoader(LoadedCellCacheLoader.get(grid, loader, type,
                AccessFlags.setOf(VOLATILE)));

        final CachedCellImg<FloatType, ?> img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(
                FLOAT, AccessFlags.setOf(VOLATILE)));
        
        return mergeDimensions(img);
    }*/

    static class RealPointSampleConverter implements SamplerConverter< RealPoint, FloatType>
    {

        final private int dimension;

        public RealPointSampleConverter( final int dimension )
        {
            this.dimension = dimension;
        }
        
        @Override
        public FloatType convert(Sampler<? extends RealPoint> sampler) {
            return new FloatType(sampler.get().getFloatPosition(dimension));
        }
    }

    final static public WriteConvertedRandomAccessibleInterval< RealPoint, FloatType > dimensionChannel(
            final RandomAccessibleInterval< RealPoint > source,
            final int dimension )
    {
        return Converters.convert(
                source,
                new RealPointSampleConverter( dimension ) );
    }

    final static public RandomAccessibleInterval< FloatType > dimensionChannels( final RandomAccessibleInterval< RealPoint > source )
    {
        return Views.stack(
                dimensionChannel( source, 0 ),
                dimensionChannel( source, 1 ),
                dimensionChannel( source, 2 ) );
    }

    final static public RandomAccessibleInterval< RealPoint > mergeDimensions( final RandomAccessibleInterval< FloatType > source ) {
        CompositeIntervalView<FloatType, ? extends GenericComposite<FloatType>> collapsed = Views.collapse(source);
        /*System.out.println("25 25 25 ");
        System.out.println(source.getAt(25,25,25,0));
        System.out.println(collapsed.getAt(25,25,25).get(0));
        System.out.println(source.getAt(25,25,25,1));
        System.out.println(collapsed.getAt(25,25,25).get(1));
        System.out.println(source.getAt(25,25,25,2));
        System.out.println(collapsed.getAt(25,25,25).get(2));

        System.out.println("26 26 26 ");
        System.out.println(source.getAt(26,27,26,0));
        System.out.println(collapsed.getAt(26,27,26).get(0));
        System.out.println(source.getAt(26,27,26,1));
        System.out.println(collapsed.getAt(26,27,26).get(1));
        System.out.println(source.getAt(26,27,26,2));
        System.out.println(collapsed.getAt(26,27,26).get(2));*/
        return Converters.convert( collapsed, (SamplerConverter) new CompositeRealPointSamplerConverter() );
    }

    static class CompositeRealPointSamplerConverter implements SamplerConverter< Composite< FloatType >, RealPoint >
    {
        @Override
        public RealPoint convert(Sampler<? extends Composite<FloatType>> sampler) {
            /*RealPoint pt = new RealPoint(3);
            pt.setPosition(Math.random()*150.0,0);
            pt.setPosition(Math.random()*150.0,1);
            pt.setPosition(Math.random()*150.0,2);*/
            Composite<FloatType> v = sampler.get();
            RealPoint pt = new RealPoint(v.get(0).get(),v.get(1).get(),v.get(2).get());
            /*if (Math.random()<0.00001) {
                System.out.println(pt);
            }*/
            return pt;
        }
    }


}
