package bdv.util;

import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealTransform;

/**
 * This RealTransform class wraps an {@link InvertibleRealTransform} and is used
 * to avoid computing the transform outside a bounding box defined by a final
 * {@link RealInterval}.
 *
 * Only the forward transform is limited in space
 * in the {@link BoundedRealTransform#apply(RealLocalizable, RealPositionable)}
 * method.
 *
 * Using this leads to drastic display speed increase if many sources are Warped but occupies
 * a limited amount of space in a bingdataviewer window. See usage of this class in
 * the Allen Brain BIOP Aligner.
 *
 */
public class BoundedRealTransform implements InvertibleRealTransform {

    final InvertibleRealTransform origin;
    final RealInterval interval;
    final int nDimSource, nDimTarget;

    public BoundedRealTransform(InvertibleRealTransform origin, RealInterval interval) {
        this.origin = origin;
        this.interval = interval;
        nDimSource = origin.numSourceDimensions();
        nDimTarget = origin.numTargetDimensions();
    }

    @Override
    public int numSourceDimensions() {
        return nDimSource;
    }

    @Override
    public int numTargetDimensions() {
        return nDimTarget;
    }

    @Override
    public void apply(double[] source, double[] target) {

        boolean inBounds = true;
        for (int d = 0; d < nDimSource; d++) {
            if (source[d]<interval.realMin(d)) {
                inBounds = false;
                break;
            }
            if (source[d]>interval.realMax(d)) {
                inBounds = false;
                break;
            }
        }
        if (inBounds) {
            origin.apply(source, target);
        } else {
            for (int d = 0; d < nDimSource; d++) {
              target[d] = source[d];
            }
            //realPositionable.setPosition(realLocalizable);
        }


        //origin.apply(source,target);
    }

    @Override
    public void apply(RealLocalizable realLocalizable, RealPositionable realPositionable) {
        //realPositionable.setPosition(realLocalizable);
        boolean inBounds = true;
        for (int d = 0; d < nDimSource; d++) {
            if (realLocalizable.getFloatPosition(d)<interval.realMin(d)) {
                inBounds = false;
                break;
            }
            if (realLocalizable.getFloatPosition(d)>interval.realMax(d)) {
                inBounds = false;
                break;
            }
        }
        if (inBounds) {
            origin.apply(realLocalizable, realPositionable);
        } else {
            realPositionable.setPosition(realLocalizable);
        }
    }

    @Override
    public void applyInverse(double[] source, double[] target) {
        origin.applyInverse(source, target);
    }

    @Override
    public void applyInverse(RealPositionable realPositionable, RealLocalizable realLocalizable) {
        origin.applyInverse(realPositionable, realLocalizable);
    }

    @Override
    public InvertibleRealTransform inverse() {
        return origin.inverse();
    }

    @Override
    public InvertibleRealTransform copy() {
        return new BoundedRealTransform(origin.copy(), interval);
    }

    public RealInterval getInterval() {
        return interval;
    }

    public RealTransform getTransform() {
        return origin;
    }
}
