package bdv.util.source.field;
import bdv.util.DefaultInterpolators;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Sampler;
import net.imglib2.algorithm.lazy.Caches;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.converter.ColorChannelOrder;
import net.imglib2.converter.Converters;
import net.imglib2.converter.readwrite.ARGBChannelSamplerConverter;
import net.imglib2.converter.readwrite.CompositeARGBSamplerConverter;
import net.imglib2.converter.readwrite.SamplerConverter;
import net.imglib2.converter.readwrite.WriteConvertedRandomAccessible;
import net.imglib2.converter.readwrite.WriteConvertedRandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.basictypeaccess.ByteAccess;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolator;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.position.transform.Floor;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.GenericByteType;
import net.imglib2.type.numeric.integer.GenericIntType;
import net.imglib2.type.numeric.integer.GenericLongType;
import net.imglib2.type.numeric.integer.GenericShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
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

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static net.imglib2.img.basictypeaccess.AccessFlags.VOLATILE;
import static net.imglib2.type.PrimitiveType.BYTE;
import static net.imglib2.type.PrimitiveType.DOUBLE;
import static net.imglib2.type.PrimitiveType.FLOAT;
import static net.imglib2.type.PrimitiveType.INT;
import static net.imglib2.type.PrimitiveType.LONG;
import static net.imglib2.type.PrimitiveType.SHORT;

public class ResampledTransformFieldSource implements ITransformFieldSource {

    final ITransformFieldSource origin;
    final Source<?> resamplingModel;
    final String name;
    final RealPointInterpolatorFactory interpolator = new RealPointInterpolatorFactory();

    /**
     * Hashmap to cache RAIs (mipmaps and timepoints)
     */
    final transient ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, RandomAccessibleInterval<RealPoint>>> cachedRAIs =
            new ConcurrentHashMap<>();

    public ResampledTransformFieldSource(ITransformFieldSource origin, Source resamplingModel, String name) throws UnsupportedOperationException {
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
        RandomAccessible<RealPoint> ra = /*new FunctionRandomAccessible<>(3, (p,v) -> {
            //System.out.println("call");
            v.setPosition(new double[]{
                    Math.random() * 50.0+50.0,
                    Math.random() * 50.0+50.0,
                    Math.random() * 50.0+50.0
            });
        }, this::getType);*/

                RealViews.affine(ipimg, at); // Gets the view

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
        final RealPoint zero = getType();

        ExtendedRandomAccessibleInterval<RealPoint, RandomAccessibleInterval<RealPoint>> eView =
                //Views.extendValue(getSource(t, level), zero);

        Views.extendBorder(getSource(t, level));
        
        @SuppressWarnings("UnnecessaryLocalVariable")
        RealRandomAccessible<RealPoint> realRandomAccessible = Views.interpolate(eView, interpolator);
        return realRandomAccessible;
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        resamplingModel.getSourceTransform(t, level, transform);
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

    public static RandomAccessibleInterval<RealPoint>
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
    }

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
    
    static class RealPointInterpolatorFactory implements InterpolatorFactory<RealPoint, RandomAccessible<RealPoint>> {

        @Override
        public RealRandomAccess<RealPoint> create(final RandomAccessible<RealPoint> randomAccessible) {
            return new RealPointNLinearInterpolator(randomAccessible);
        }

        @Override
        public RealRandomAccess<RealPoint> create(RandomAccessible<RealPoint> randomAccessible, RealInterval interval) {
            return create( randomAccessible );
        }
        
    }

    static class RealPointNLinearInterpolator extends Floor<RandomAccess< RealPoint >> implements RealRandomAccess< RealPoint > {

        protected int code;
        
        final protected double[] weights;

        final protected ExtendedRealPoint accumulator;

        final protected ExtendedRealPoint tmp;

        protected RealPointNLinearInterpolator( final RealPointNLinearInterpolator interpolator )
        {
            super( interpolator.target.copyRandomAccess() );

            weights = interpolator.weights.clone();
            code = interpolator.code;
            accumulator = new ExtendedRealPoint(3);
            tmp = new ExtendedRealPoint(3);

            for ( int d = 0; d < n; ++d )
            {
                position[ d ] = interpolator.position[ d ];
                discrete[ d ] = interpolator.discrete[ d ];
            }
        }
        
        protected RealPointNLinearInterpolator( final RandomAccessible< RealPoint > randomAccessible, final RealPoint type )
        {
            super( randomAccessible.randomAccess() );
            weights = new double[ 1 << n ];
            code = 0;
            accumulator = new ExtendedRealPoint(3); // Assertions
            tmp = new ExtendedRealPoint(3); // Assertions
        }
        
        protected RealPointNLinearInterpolator( final RandomAccessible< RealPoint > randomAccessible )
        {
            this( randomAccessible, randomAccessible.randomAccess().get() );
        }

        /**
         * Fill the weights array.
         *
         * <p>
         * Let <em>w_d</em> denote the fraction of a pixel at which the sample
         * position <em>p_d</em> lies from the floored position <em>pf_d</em> in
         * dimension <em>d</em>. That is, the value at <em>pf_d</em> contributes
         * with <em>(1 - w_d)</em> to the sampled value; the value at
         * <em>( pf_d + 1 )</em> contributes with <em>w_d</em>.
         * </p>
         * <p>
         * At every pixel, the total weight results from multiplying the weights of
         * all dimensions for that pixel. That is, the "top-left" contributing pixel
         * (position floored in all dimensions) gets assigned weight
         * <em>(1-w_0)(1-w_1)...(1-w_n)</em>.
         * </p>
         * <p>
         * We work through the weights array starting from the highest dimension.
         * For the highest dimension, the first half of the weights contain the
         * factor <em>(1 - w_n)</em> because this first half corresponds to floored
         * pixel positions in the highest dimension. The second half contain the
         * factor <em>w_n</em>. In this first step, the first weight of the first
         * half gets assigned <em>(1 - w_n)</em>. The first element of the second
         * half gets assigned <em>w_n</em>
         * </p>
         * <p>
         * From their, we work recursively down to dimension 0. That is, each half
         * of weights is again split recursively into two partitions. The first
         * element of the second partitions is the first element of the half
         * multiplied with <em>(w_d)</em>. The first element of the first partitions
         * is multiplied with <em>(1 - w_d)</em>.
         * </p>
         * <p>
         * When we have reached dimension 0, all weights will have a value assigned.
         * </p>
         */
        protected void fillWeights()
        {
            weights[ 0 ] = 1.0d;

            for ( int d = n - 1; d >= 0; --d )
            {
                final double w = position[ d ] - target.getLongPosition( d );
                final double wInv = 1.0d - w;
                final int wInvIndexIncrement = 1 << d;
                final int loopCount = 1 << ( n - 1 - d );
                final int baseIndexIncrement = wInvIndexIncrement * 2;
                int baseIndex = 0;
                for ( int i = 0; i < loopCount; ++i )
                {
                    weights[ baseIndex + wInvIndexIncrement ] = weights[ baseIndex ] * w;
                    weights[ baseIndex ] *= wInv;
                    baseIndex += baseIndexIncrement;
                }
            }
        }

        @Override
        public RealPoint get() {
            fillWeights();

            accumulator.setPosition( target.get() );
            accumulator.mul( weights[ 0 ] );

            code = 0;
            graycodeFwdRecursive( n - 1 );
            target.bck( n - 1 );

            return accumulator;
        }

        @Override
        public RealPointNLinearInterpolator copy() {
            return new RealPointNLinearInterpolator( this );
        }

        @Override
        public RealPointNLinearInterpolator copyRealRandomAccess() {
            return copy();
        }

        final private void graycodeFwdRecursive( final int dimension )
        {
            if ( dimension == 0 )
            {
                target.fwd( 0 );
                code += 1;
                accumulate();
            }
            else
            {
                graycodeFwdRecursive( dimension - 1 );
                target.fwd( dimension );
                code += 1 << dimension;
                accumulate();
                graycodeBckRecursive( dimension - 1 );
            }
        }

        final private void graycodeBckRecursive( final int dimension )
        {
            if ( dimension == 0 )
            {
                target.bck( 0 );
                code -= 1;
                accumulate();
            }
            else
            {
                graycodeFwdRecursive( dimension - 1 );
                target.bck( dimension );
                code -= 1 << dimension;
                accumulate();
                graycodeBckRecursive( dimension - 1 );
            }
        }

        /**
         * multiply current target value with current weight and add to accumulator.
         */
        final private void accumulate()
        {
            tmp.setPosition( target.get() );
            tmp.mul( weights[ code ] );
            accumulator.move( tmp );
        }
        
    }
    
    static class ExtendedRealPoint extends RealPoint {
        public ExtendedRealPoint(int nDimensions) {
            super(nDimensions);
        }

        public void mul(double alpha) {
            for (int d = 0; d<this.n; d++) {
                position[d] = alpha * position[d];
            }
        }
    }

}
