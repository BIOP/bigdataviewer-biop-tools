package bdv.util.coordinates;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class CoordinateSource implements Source<FloatType> {

    final int dim;
    final VectorFieldSource source;

    public CoordinateSource(VectorFieldSource source, int dim) {
        this.dim = dim;
        this.source = source;
    }

    @Override
    public boolean isPresent(int t) {
        return source.isPresent(t);
    }

    @Override
    public RandomAccessibleInterval<FloatType> getSource(int t, int level) {
        final RandomAccessibleInterval<RealPoint> rai = source.getSource(t, level);
        return Views.interval(new FunctionRandomAccessible<FloatType>(3,
                (loc, value) -> {
                    value.set(rai.getAt(loc).getFloatPosition(dim));
                }, FloatType::new), rai);
    }

    @Override
    public RealRandomAccessible<FloatType> getInterpolatedSource(int t, int level, Interpolation method) {
        final RealRandomAccessible<RealPoint> rra = source.getInterpolatedSource(t, level, method);
        return new FunctionRealRandomAccessible<>(3,
                (loc, value) -> {
                    value.set(rra.getAt(loc).getFloatPosition(dim));
                }, FloatType::new);
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        source.getSourceTransform(t, level, transform);
    }

    @Override
    public FloatType getType() {
        return new FloatType();
    }

    @Override
    public String getName() {
        return source.getName()+"_"+dim;
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return source.getVoxelDimensions();
    }

    @Override
    public int getNumMipmapLevels() {
        return source.getNumMipmapLevels();
    }
}
