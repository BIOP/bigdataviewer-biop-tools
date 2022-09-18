package bdv.util.source.field;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealRandomAccess;
import net.imglib2.position.transform.Floor;

public class RealPoint3DLinearInterpolator extends Floor<RandomAccess< NativeRealPoint3D >> implements RealRandomAccess< NativeRealPoint3D > {

    final protected NativeRealPoint3D accumulator;

    final protected NativeRealPoint3D tmp;

    protected RealPoint3DLinearInterpolator(final RealPoint3DLinearInterpolator interpolator )
    {
        super( interpolator.target.copyRandomAccess() );

        accumulator = new NativeRealPoint3D();
        tmp = new NativeRealPoint3D();

        for ( int d = 0; d < n; ++d )
        {
            position[ d ] = interpolator.position[ d ];
            discrete[ d ] = interpolator.discrete[ d ];
        }
    }

    protected RealPoint3DLinearInterpolator(final RandomAccessible<NativeRealPoint3D> randomAccessible, final NativeRealPoint3D type )
    {
        super( randomAccessible.randomAccess() );
        accumulator = new NativeRealPoint3D();
        tmp = new NativeRealPoint3D();
    }

    protected RealPoint3DLinearInterpolator(final RandomAccessible< NativeRealPoint3D > randomAccessible )
    {
        this( randomAccessible, randomAccessible.randomAccess().get() );
    }

    @Override
    public NativeRealPoint3D get() {

        final double w0 = position[ 0 ] - target.getLongPosition( 0 );
        final double w0Inv = 1.0d - w0;
        final double w1 = position[ 1 ] - target.getLongPosition( 1 );
        final double w1Inv = 1.0d - w1;
        final double w2 = position[ 2 ] - target.getLongPosition( 2 );
        final double w2Inv = 1.0d - w2;

        float weights0 = (float) (w0Inv * w1Inv * w2Inv);
        float weights1 = (float) (w0 * w1Inv * w2Inv);
        float weights2 = (float) (w0Inv * w1 * w2Inv);
        float weights3 = (float) (w0 * w1 * w2Inv);
        float weights4 = (float) (w0Inv * w1Inv * w2);
        float weights5 = (float) (w0 * w1Inv * w2);
        float weights6 = (float) (w0Inv * w1 * w2);
        float weights7 = (float) (w0 * w1 * w2);

        accumulator.set( target.get() );
        accumulator.mul( weights0 );
        target.fwd( 0 );
        tmp.set( target.get() );
        tmp.mul( weights1 );
        accumulator.move( tmp );
        target.fwd( 1 );
        tmp.set( target.get() );
        tmp.mul( weights3 );
        accumulator.move( tmp );
        target.bck( 0 );
        tmp.set( target.get() );
        tmp.mul( weights2 );
        accumulator.move( tmp );
        target.fwd( 2 );
        tmp.set( target.get() );
        tmp.mul( weights6 );
        accumulator.move( tmp );
        target.fwd( 0 );
        tmp.set( target.get() );
        tmp.mul( weights7 );
        accumulator.move( tmp );
        target.bck( 1 );
        tmp.set( target.get() );
        tmp.mul( weights5 );
        accumulator.move( tmp );
        target.bck( 0 );
        tmp.set( target.get() );
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
