package ch.epfl.biop.bdv.sampleimage;

import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import java.util.function.ToIntFunction;

public class Procedural3DImageShort extends RealPoint implements RealRandomAccess<UnsignedShortType> {
    final UnsignedShortType t;

    ToIntFunction<double[]> evalFunction;

    public Procedural3DImageShort(ToIntFunction<double[]> evalFunction)
    {
        super( 3 ); // number of dimensions is 3
        t = new UnsignedShortType();
        this.evalFunction=evalFunction;
    }

    public Procedural3DImageShort(UnsignedShortType t) {
        this.t = t;
    }

    @Override
    public RealRandomAccess<UnsignedShortType> copyRealRandomAccess() {
        return copy();
    }

    @Override
    public UnsignedShortType get() {
        t.set(
                evalFunction.applyAsInt(position)
        );
        return t;
    }

    @Override
    public Procedural3DImageShort copy() {
        Procedural3DImageShort a = new Procedural3DImageShort(evalFunction);
        a.setPosition( this );
        return a;
    }

    public RealRandomAccessible<UnsignedShortType> getRRA() {

        RealRandomAccessible<UnsignedShortType> rra = new RealRandomAccessible<UnsignedShortType>() {
            @Override
            public RealRandomAccess<UnsignedShortType> realRandomAccess() {
                return copy();
            }

            @Override
            public RealRandomAccess<UnsignedShortType> realRandomAccess(RealInterval realInterval) {
                return copy();
            }

            @Override
            public int numDimensions() {
                return 3;
            }
        };

        return rra;
    }

}
