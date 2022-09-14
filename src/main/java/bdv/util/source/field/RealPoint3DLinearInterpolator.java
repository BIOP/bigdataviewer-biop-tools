package bdv.util.source.field;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.position.transform.Floor;

public class RealPoint3DLinearInterpolator extends Floor<RandomAccess< RealPoint >> implements RealRandomAccess< RealPoint > {

    //protected int code;

    final protected double[] weights;

    final protected ExtendedRealPoint accumulator;

    final protected ExtendedRealPoint tmp;

    protected RealPoint3DLinearInterpolator(final RealPoint3DLinearInterpolator interpolator )
    {
        super( interpolator.target.copyRandomAccess() );

        weights = interpolator.weights.clone();
        //code = interpolator.code;
        accumulator = new ExtendedRealPoint(3);
        tmp = new ExtendedRealPoint(3);

        for ( int d = 0; d < n; ++d )
        {
            position[ d ] = interpolator.position[ d ];
            discrete[ d ] = interpolator.discrete[ d ];
        }
    }

    protected RealPoint3DLinearInterpolator(final RandomAccessible<RealPoint> randomAccessible, final RealPoint type )
    {
        super( randomAccessible.randomAccess() );
        weights = new double[ 8 ];//1 << n ];
        //code = 0;
        accumulator = new ExtendedRealPoint(3); // Assertions
        tmp = new ExtendedRealPoint(3); // Assertions
    }

    protected RealPoint3DLinearInterpolator(final RandomAccessible< RealPoint > randomAccessible )
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
        /*weights[ 0 ] = 1.0d;

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
        }*/
        final double w0 = position[ 0 ] - target.getLongPosition( 0 );
        final double w0Inv = 1.0d - w0;
        final double w1 = position[ 1 ] - target.getLongPosition( 1 );
        final double w1Inv = 1.0d - w1;
        final double w2 = position[ 2 ] - target.getLongPosition( 2 );
        final double w2Inv = 1.0d - w2;

        weights[ 0 ] = w0Inv * w1Inv * w2Inv;
        weights[ 1 ] = w0 * w1Inv * w2Inv;
        weights[ 2 ] = w0Inv * w1 * w2Inv;
        weights[ 3 ] = w0 * w1 * w2Inv;
        weights[ 4 ] = w0Inv * w1Inv * w2;
        weights[ 5 ] = w0 * w1Inv * w2;
        weights[ 6 ] = w0Inv * w1 * w2;
        weights[ 7 ] = w0 * w1 * w2;
    }

    @Override
    public RealPoint get() {
        /*fillWeights();

        accumulator.setPosition( target.get() );
        accumulator.mul( weights[ 0 ] );

        code = 0;
        graycodeFwdRecursive( n - 1 );
        target.bck( n - 1 );

        return accumulator;*/

        fillWeights();

        accumulator.setPosition( target.get() );
        accumulator.mul( weights[ 0 ] );
        target.fwd( 0 );
        tmp.setPosition( target.get() );
        tmp.mul( weights[ 1 ] );
        accumulator.move( tmp );
        target.fwd( 1 );
        tmp.setPosition( target.get() );
        tmp.mul( weights[ 3 ] );
        accumulator.move( tmp );
        target.bck( 0 );
        tmp.setPosition( target.get() );
        tmp.mul( weights[ 2 ] );
        accumulator.move( tmp );
        target.fwd( 2 );
        tmp.setPosition( target.get() );
        tmp.mul( weights[ 6 ] );
        accumulator.move( tmp );
        target.fwd( 0 );
        tmp.setPosition( target.get() );
        tmp.mul( weights[ 7 ] );
        accumulator.move( tmp );
        target.bck( 1 );
        tmp.setPosition( target.get() );
        tmp.mul( weights[ 5 ] );
        accumulator.move( tmp );
        target.bck( 0 );
        tmp.setPosition( target.get() );
        tmp.mul( weights[ 4 ] );
        accumulator.move( tmp );
        target.bck( 2 );

        return accumulator;

    }

    @Override
    public RealPoint3DLinearInterpolator copy() {
        return new RealPoint3DLinearInterpolator( this );
    }

    @Override
    public RealPoint3DLinearInterpolator copyRealRandomAccess() {
        return copy();
    }

    /*final private void graycodeFwdRecursive( final int dimension )
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
    }*/

    /*final private void graycodeBckRecursive( final int dimension )
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
    }*/

    /**
     * multiply current target value with current weight and add to accumulator.
     */
    /*final private void accumulate()
    {
        tmp.setPosition( target.get() );
        tmp.mul( weights[ code ] );
        accumulator.move( tmp );
    }*/

}
