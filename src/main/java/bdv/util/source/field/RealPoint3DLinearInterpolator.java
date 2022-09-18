package bdv.util.source.field;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.position.transform.Floor;

public class RealPoint3DLinearInterpolator extends Floor<RandomAccess< NativeRealPoint >> implements RealRandomAccess< NativeRealPoint > {

    final protected NativeRealPoint accumulator;

    final protected NativeRealPoint tmp;

    protected RealPoint3DLinearInterpolator(final RealPoint3DLinearInterpolator interpolator )
    {
        super( interpolator.target.copyRandomAccess() );

        accumulator = new NativeRealPoint(3);
        tmp = new NativeRealPoint(3);

        for ( int d = 0; d < n; ++d )
        {
            position[ d ] = interpolator.position[ d ];
            discrete[ d ] = interpolator.discrete[ d ];
        }
    }

    protected RealPoint3DLinearInterpolator(final RandomAccessible<NativeRealPoint> randomAccessible, final NativeRealPoint type )
    {
        super( randomAccessible.randomAccess() );
        accumulator = new NativeRealPoint(3);
        tmp = new NativeRealPoint(3);
    }

    protected RealPoint3DLinearInterpolator(final RandomAccessible< NativeRealPoint > randomAccessible )
    {
        this( randomAccessible, randomAccessible.randomAccess().get() );
    }

    @Override
    public NativeRealPoint get() {

        final double w0 = position[ 0 ] - target.getLongPosition( 0 );
        final double w0Inv = 1.0d - w0;
        final double w1 = position[ 1 ] - target.getLongPosition( 1 );
        final double w1Inv = 1.0d - w1;
        final double w2 = position[ 2 ] - target.getLongPosition( 2 );
        final double w2Inv = 1.0d - w2;

        double weights0 = w0Inv * w1Inv * w2Inv;
        double weights1 = w0 * w1Inv * w2Inv;
        double weights2 = w0Inv * w1 * w2Inv;
        double weights3 = w0 * w1 * w2Inv;
        double weights4 = w0Inv * w1Inv * w2;
        double weights5 = w0 * w1Inv * w2;
        double weights6 = w0Inv * w1 * w2;
        double weights7 = w0 * w1 * w2;

        accumulator.setPosition( target.get() );
        accumulator.mul( weights0 );
        target.fwd( 0 );
        tmp.setPosition( target.get() );
        tmp.mul( weights1 );
        accumulator.move( tmp );
        target.fwd( 1 );
        tmp.setPosition( target.get() );
        tmp.mul( weights3 );
        accumulator.move( tmp );
        target.bck( 0 );
        tmp.setPosition( target.get() );
        tmp.mul( weights2 );
        accumulator.move( tmp );
        target.fwd( 2 );
        tmp.setPosition( target.get() );
        tmp.mul( weights6 );
        accumulator.move( tmp );
        target.fwd( 0 );
        tmp.setPosition( target.get() );
        tmp.mul( weights7 );
        accumulator.move( tmp );
        target.bck( 1 );
        tmp.setPosition( target.get() );
        tmp.mul( weights5 );
        accumulator.move( tmp );
        target.bck( 0 );
        tmp.setPosition( target.get() );
        tmp.mul( weights4 );
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

}
