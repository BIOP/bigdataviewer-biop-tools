package bdv.util.source.alpha;

import bdv.tools.transformation.TransformedSource;
import bdv.viewer.Source;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;

/**
 * Alpha Source for a Transformed Source
 *
 * This class is made in order to identify the fact that it is an AlphaSource by
 * implementing the {@link IAlphaSource} interface.
 *
 * Otherwise, bdv would try to make an alpha source out of an alpha source out of
 * an alpha source etc. (stack overflow)
 *
 */

public class AlphaSourceTransformed extends TransformedSource<FloatType> implements IAlphaSource {

    final IAlphaSource originAlpha;
    final TransformedSource<?> origin;

    public AlphaSourceTransformed(IAlphaSource source, TransformedSource<?> shareTransform) {
        super(source, shareTransform);
        this.originAlpha = source;
        this.origin = shareTransform;
    }

    @Override
    public boolean intersectBox(AffineTransform3D affineTransform, Interval cell, int timepoint) {
        //return true;
        if (originAlpha.doBoundingBoxCulling()) {
            // Let's try a simplebox computation and see if there are intersections.
            AlphaSourceRAI.Box3D box_cell = new AlphaSourceRAI.Box3D(affineTransform, cell);
            AffineTransform3D affineTransform3D = new AffineTransform3D();
            getSourceTransform(timepoint, 0, affineTransform3D);
            AlphaSourceRAI.Box3D box_this = new AlphaSourceRAI.Box3D(affineTransform3D, this.getSource(timepoint, 0));
            return box_this.intersects(box_cell);
        } else {
            return true;
        }
        /*AlphaSourceRAI.Box3D box_cell = new AlphaSourceRAI.Box3D(affineTransform, cell);
        AffineTransform3D affineTransform3D = new AffineTransform3D();
        getSourceTransform(timepoint, 0, affineTransform3D);
        AlphaSourceRAI.Box3D box_this = new AlphaSourceRAI.Box3D(affineTransform3D, this.getSource(timepoint, 0));
        return box_this.intersects(box_cell);*/
        //return originAlpha.intersectBox(affineTransform, cell, timepoint);
    }
}
