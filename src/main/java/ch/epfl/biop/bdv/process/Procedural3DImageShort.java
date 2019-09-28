package ch.epfl.biop.bdv.process;

import bdv.util.BdvOptions;
import bdv.util.RealRandomAccessibleIntervalSource;
import bdv.viewer.Source;
import net.imglib2.*;
import net.imglib2.realtransform.AffineTransform3D;
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

    public Source<UnsignedShortType> getSource(String sourceName) {
        Interval interval = new FinalInterval(
                new long[]{ -1, -1, -1 },
                new long[]{ 1, 1, 1 });
        RealRandomAccessible<UnsignedShortType> rra = this.getRRA();
        //final AxisOrder axisOrder = AxisOrder.getAxisOrder( BdvOptions.options().values.axisOrder(), rra, false );
        final AffineTransform3D sourceTransform = BdvOptions.options().values.getSourceTransform();
        final UnsignedShortType type = rra.realRandomAccess().get();

        return new RealRandomAccessibleIntervalSource<>( rra, interval, type, sourceTransform, sourceName );
    }

}
