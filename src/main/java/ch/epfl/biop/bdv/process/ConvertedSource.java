package ch.epfl.biop.bdv.process;

import bdv.util.AbstractSource;
import bdv.util.DefaultInterpolators;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.display.LinearRange;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

import java.util.function.Supplier;

public class ConvertedSource<R,T extends NumericType<T>> implements Source< T > {

    private final Source< R > source;
    Supplier<T> typeSupplier;
    String name;
    protected final DefaultInterpolators<T> interpolators;
    final Converter<R,T> cvt;

    public ConvertedSource(Source< R > source, Supplier<T> typeSupplier, Converter<R,T> cvt, String name) {
        this.source = source;
        this.typeSupplier = typeSupplier;
        this.name = name;
        this.cvt = cvt;
        this.interpolators = new DefaultInterpolators();

        if ((this.cvt) instanceof LinearRange) {
            this.setLinearRange((LinearRange) cvt);
        }
    }

    @Override
    public boolean isPresent(int t) {
        return source.isPresent(t);
    }

    @Override
    public RandomAccessibleInterval<T> getSource(int t, int level) {
        return Converters.convert(source.getSource(t,level), cvt, typeSupplier.get());
    }

    @Override
    public RealRandomAccessible<T> getInterpolatedSource(int t, int level, Interpolation method) {
        final T zero = getType();
        zero.setZero();
        ExtendedRandomAccessibleInterval<T, RandomAccessibleInterval< T >>
                eView = Views.extendZero(getSource( t, level ));
        RealRandomAccessible< T > realRandomAccessible = Views.interpolate( eView, interpolators.get(method) );
        return realRandomAccessible;
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        source.getSourceTransform(t,level, transform);
    }

    @Override
    public T getType() {
        return typeSupplier.get();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return source.getVoxelDimensions();
    }

    @Override
    public int getNumMipmapLevels() {
        return source.getNumMipmapLevels();
    }


    public LinearRange lr;

    public void setLinearRange(LinearRange lr) {
        this.lr=lr;
    }

    public LinearRange getLinearRange() {
        return lr;
    }
}
