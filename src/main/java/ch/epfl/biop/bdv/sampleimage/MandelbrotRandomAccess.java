package ch.epfl.biop.bdv.sampleimage;

import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.type.numeric.integer.UnsignedByteType;

/**
 * Example taken from ImageJ.net
 */

public class MandelbrotRandomAccess extends RealPoint implements RealRandomAccess<UnsignedByteType> {
    final UnsignedByteType t;

    public MandelbrotRandomAccess()
    {
        super( 2 ); // number of dimensions is 2
        t = new UnsignedByteType();
    }

    public MandelbrotRandomAccess(UnsignedByteType t) {
        this.t = t;
    }

    public final int mandelbrot( final double re0, final double im0,
                                 final int maxIterations )
    {
        double re = re0;
        double im = im0;
        int i = 0;
        for ( ; i < maxIterations; ++i )
        {
            final double squre = re * re;
            final double squim = im * im;
            if ( squre + squim > 4 )
                break;
            im = 2 * re * im + im0;
            re = squre - squim + re0;
        }
        return i;
    }

    @Override
    public UnsignedByteType get()
    {
        t.set( mandelbrot( position[ 0 ], position[ 1 ], 255 ));
        return t;
    }

    @Override
    public MandelbrotRandomAccess copyRealRandomAccess()
    {
        return copy();
    }

    @Override
    public MandelbrotRandomAccess copy()
    {
        final MandelbrotRandomAccess a = new MandelbrotRandomAccess();
        a.setPosition( this );
        return a;
    }
}
