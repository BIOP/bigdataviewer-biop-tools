package bdv.util.source.field;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.position.transform.Floor;

public class RealPoint2DLinearInterpolator extends Floor<RandomAccess< RealPoint >> implements RealRandomAccess< RealPoint > {

    final protected ExtendedRealPoint accumulator;

    final protected ExtendedRealPoint tmp;

    protected RealPoint2DLinearInterpolator(final RealPoint2DLinearInterpolator interpolator )
    {
        super( interpolator.target.copyRandomAccess() );

        accumulator = new ExtendedRealPoint(2);
        tmp = new ExtendedRealPoint(2);

        for ( int d = 0; d < n; ++d )
        {
            position[ d ] = interpolator.position[ d ];
            discrete[ d ] = interpolator.discrete[ d ];
        }
    }

    protected RealPoint2DLinearInterpolator(final RandomAccessible<RealPoint> randomAccessible, final RealPoint type )
    {
        super( randomAccessible.randomAccess() );
        accumulator = new ExtendedRealPoint(2);
        tmp = new ExtendedRealPoint(2);
    }

    protected RealPoint2DLinearInterpolator(final RandomAccessible< RealPoint > randomAccessible )
    {
        this( randomAccessible, randomAccessible.randomAccess().get() );
    }

    @Override
    public RealPoint get() {

        final double w0 = position[ 0 ] - target.getLongPosition( 0 );
        final double w0Inv = 1.0d - w0;
        final double w1 = position[ 1 ] - target.getLongPosition( 1 );
        final double w1Inv = 1.0d - w1;

        double weights0 = w0Inv * w1Inv ;
        double weights1 = w0 * w1Inv ;
        double weights2 = w0Inv * w1 ;
        double weights3 = w0 * w1 ;

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
        target.bck( 1 );
        return accumulator;
    }

    @Override
    public RealPoint2DLinearInterpolator copy() {
        return new RealPoint2DLinearInterpolator( this );
    }

    @Override
    public RealPoint2DLinearInterpolator copyRealRandomAccess() {
        return copy();
    }

}
