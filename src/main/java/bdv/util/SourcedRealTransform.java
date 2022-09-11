package bdv.util;

import bdv.util.source.field.ITransformFieldSource;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.RealTransform;

/**
 * This RealTransform class takes a Source<RealPoint> and turns it into a RealTransform object
 * This allows to use caching, resampling and interpolation to speed up computation of complex
 * transformations.
 */
public class SourcedRealTransform implements RealTransform {

    final ITransformFieldSource source;
    final RealRandomAccessible<RealPoint> realRandomAccess;

    public SourcedRealTransform(ITransformFieldSource source) {
        this.source = source;
        this.realRandomAccess = source.getInterpolatedSource(0,0,null);
    }

    @Override
    public int numSourceDimensions() {
        return source.numSourceDimensions();
    }

    @Override
    public int numTargetDimensions() {
        return source.numTargetDimensions();
    }

    @Override
    public void apply(double[] source, double[] target) {
        double[] result = realRandomAccess.getAt(source).positionAsDoubleArray();
        System.arraycopy(result,0, target,0,target.length);
    }

    @Override
    public void apply(RealLocalizable realLocalizable, RealPositionable realPositionable) {
        realPositionable.setPosition(realRandomAccess.getAt(realLocalizable));
    }

    @Override
    public RealTransform copy() {
        return new SourcedRealTransform(source);
    }
}
