package bdv.util.source.process;

import bdv.util.DefaultInterpolators;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class VoxelProcessedSource<I,O extends NumericType<O>> implements Source<O> {

    protected final DefaultInterpolators< O > interpolators = new DefaultInterpolators<>();

    final Processor<I, O> processor;

    final Source<I> origin;

    final String name;

    final O o;

    public VoxelProcessedSource(String name, Source<I> origin, Processor<I, O> processor, O o) {
        this.name = name;
        this.origin = origin;
        this.processor = processor;
        this.o = o;
    }

    @Override
    public boolean isPresent(int t) {
        return origin.isPresent(t);
    }

    @Override
    public RandomAccessibleInterval<O> getSource(int t, int level) {
        return processor.process(origin.getSource(t,level),t,level);
    }

    @Override
    public RealRandomAccessible<O> getInterpolatedSource(int t, int level, Interpolation method) {
        final O zero = getType();
        zero.setZero();
        ExtendedRandomAccessibleInterval<O, RandomAccessibleInterval< O >>
                eView = Views.extendZero(getSource( t, level ));
        RealRandomAccessible< O > realRandomAccessible = Views.interpolate( eView, interpolators.get(method) );
        return realRandomAccessible;
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        origin.getSourceTransform(t,level,transform);
    }

    @Override
    public O getType() {
        return o.createVariable();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return origin.getVoxelDimensions();
    }

    @Override
    public int getNumMipmapLevels() {
        return origin.getNumMipmapLevels();
    }

    public interface Processor<I,O> {
            RandomAccessibleInterval<O> process(RandomAccessibleInterval<I> rai, int t, int level);
    }
}
