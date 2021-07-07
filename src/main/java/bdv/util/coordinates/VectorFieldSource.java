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
import net.imglib2.view.Views;

/**
 * A source which simply returns where it is localized
 */

public class VectorFieldSource implements Source<RealPoint> {

    final Source<?> origin;
    final String name;

    public VectorFieldSource(Source<?> origin, String name) {
        this.origin = origin;
        this.name = name;
    }

    @Override
    public boolean isPresent(int t) {
        return origin.isPresent(t);
    }

    @Override
    public RandomAccessibleInterval<RealPoint> getSource(int t, int level) {
        AffineTransform3D transform = new AffineTransform3D();
        getSourceTransform(t, level, transform);
        FunctionRandomAccessible<RealPoint> f = new FunctionRandomAccessible<>(3,
                (loc, value) -> {
                    //value.localize();
                    value.setPosition(new double[]{loc.getDoublePosition(0), loc.getDoublePosition(1), loc.getDoublePosition(2)});
                    transform.apply(value, value);
                }, () -> new RealPoint(3));
        return Views.interval(f, origin.getSource(t,level));
    }

    @Override
    public RealRandomAccessible<RealPoint> getInterpolatedSource(int t, int level, Interpolation method) {
        return new FunctionRealRandomAccessible<RealPoint>(3,
                (loc, value) -> {
                    //value.localize();
                    value.setPosition(new double[]{loc.getDoublePosition(0), loc.getDoublePosition(1), loc.getDoublePosition(2)});
                }, () -> new RealPoint(3));
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        origin.getSourceTransform(t,level,transform);
    }

    @Override
    public RealPoint getType() {
        return new RealPoint(3);
    }

    @Override
    public String getName() {
        return name==null? origin.getName()+"_coords":name;
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return origin.getVoxelDimensions();
    }

    @Override
    public int getNumMipmapLevels() {
        return origin.getNumMipmapLevels();
    }
}
