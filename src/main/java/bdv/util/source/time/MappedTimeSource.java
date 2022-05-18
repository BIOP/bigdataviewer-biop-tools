package bdv.util.source.time;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;

import java.util.function.Function;

/**
 * A source which applies an arbitrary transform on time
 * @param <T> the pixel type
 */
public class MappedTimeSource<T> implements Source<T> {

    final Source<T> origin;
    Function<Integer, Integer> mappedTime; // mappedTime.apply(t)
    final String name;

    public MappedTimeSource(Source<T> origin, String name, Function<Integer, Integer> mappedTime) {
        this.origin = origin;
        this.mappedTime = mappedTime;
        this.name = name;
    }

    @Override
    public boolean isPresent(int t) {
        return origin.isPresent(mappedTime.apply(t));
    }

    @Override
    public RandomAccessibleInterval<T> getSource(int t, int level) {
        return origin.getSource(mappedTime.apply(t), level);
    }

    @Override
    public RealRandomAccessible<T> getInterpolatedSource(final int t, final int level, final Interpolation method) {
        return origin.getInterpolatedSource(mappedTime.apply(t), level, method);
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        origin.getSourceTransform(mappedTime.apply(t), level, transform);
    }

    @Override
    public T getType() {
        return origin.getType();
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
}
