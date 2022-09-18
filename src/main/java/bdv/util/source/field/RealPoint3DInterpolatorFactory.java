package bdv.util.source.field;

import net.imglib2.RandomAccessible;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.interpolation.InterpolatorFactory;

public class RealPoint3DInterpolatorFactory implements InterpolatorFactory<NativeRealPoint, RandomAccessible<NativeRealPoint>> {

    @Override
    public RealRandomAccess<NativeRealPoint> create(final RandomAccessible<NativeRealPoint> randomAccessible) {
        return new RealPoint3DLinearInterpolator(randomAccessible);
    }

    @Override
    public RealRandomAccess<NativeRealPoint> create(RandomAccessible<NativeRealPoint> randomAccessible, RealInterval interval) {
        return create( randomAccessible );
    }

}