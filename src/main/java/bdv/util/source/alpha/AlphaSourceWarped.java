package bdv.util.source.alpha;

import bdv.img.WarpedSource;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import net.imglib2.*;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * Alpha Source of a {@link WarpedSource}
 *
 * The transformation is synchronized because this source knows which source it is the alpha of.
 *
 */

public class AlphaSourceWarped extends AlphaSource {

    final WarpedSource origin_warped;

    IAlphaSource origin_alpha;

    public AlphaSourceWarped(Source<?> origin) {
        super(origin);
        assert origin instanceof WarpedSource;
        origin_warped = (WarpedSource) origin;
    }

    public AlphaSourceWarped(Source<?> origin, float alpha) {
        super(origin, alpha);
        origin_warped = (WarpedSource) origin;
    }

    public IAlphaSource getAlpha() {
        if (origin_alpha==null) {
            origin_alpha = (IAlphaSource) AlphaSourceHelper.getOrBuildAlphaSource(((WarpedSource<?>) origin).getWrappedSource()).getSpimSource();
        }
        return origin_alpha;
    }

    @Override
    public RandomAccessibleInterval<FloatType> getSource(int t, int level) {
        final float finalAlpha = alpha;

        final RandomAccessible< FloatType > randomAccessible =
                new FunctionRandomAccessible<>( 3, () -> (loc, out) -> out.setReal( finalAlpha ), FloatType::new );
        // Giving singular matrix issue!!
        return Views.interval(randomAccessible, new FinalInterval(new long[]{0,0,0}, new long[]{1,1,1})); // CAUSES ERROR : SINGULAR MATRIX origin.getSource(t, level));
    }

    @Override
    public RealRandomAccessible<FloatType> getInterpolatedSource(int t, int level, Interpolation method) {
        RealRandomAccessible<FloatType> sourceRealAccessible =
                getAlpha().getInterpolatedSource(t, level, method);
        if (origin_warped.isTransformed()) {
            AffineTransform3D transform = new AffineTransform3D();
            getAlpha().getSourceTransform(t, level, transform);
            RealRandomAccessible<FloatType> srcRaTransformed = RealViews.affineReal(getAlpha().getInterpolatedSource(t, level, method), transform);
            return (RealRandomAccessible)(origin_warped.getTransform() == null ? srcRaTransformed : new RealTransformRealRandomAccessible(srcRaTransformed, origin_warped.getTransform()));
        } else {
            return sourceRealAccessible;
        }
    }

    @Override
    public boolean intersectBox(AffineTransform3D affineTransform, Interval cell, int timepoint) {
        // How to do better ? We know nothing about the warping
        return true;
    }
}
