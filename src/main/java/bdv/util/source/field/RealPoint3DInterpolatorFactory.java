package bdv.util.source.field;

import net.imglib2.RandomAccessible;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.interpolation.InterpolatorFactory;

public class RealPoint3DInterpolatorFactory implements InterpolatorFactory<RealPoint, RandomAccessible<RealPoint>> {

    @Override
    public RealRandomAccess<RealPoint> create(final RandomAccessible<RealPoint> randomAccessible) {
        return new RealPoint3DLinearInterpolator(randomAccessible);
    }

    @Override
    public RealRandomAccess<RealPoint> create(RandomAccessible<RealPoint> randomAccessible, RealInterval interval) {
        return create( randomAccessible );
    }

}