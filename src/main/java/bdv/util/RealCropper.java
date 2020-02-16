package bdv.util;

import net.imglib2.*;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

/**
 * Utilities function used to crop a rrai
 */
// -- Not the appropriate repository location

public class RealCropper {


    public static <T extends RealType<T>> RandomAccessibleInterval<T> getCroppedSampledRRAI(
            RealRandomAccessible<T> rra,
            AffineTransform3D at3D,
            RealInterval ri,
            double xPixelSize,
            double yPixelSize,
            double zPixelSize
    ) {

        at3D.scale(1./xPixelSize, 1./yPixelSize, 1./zPixelSize);

        // Gets randomAccessible view ...
        RandomAccessible<T> ra = RealViews.affine(rra, at3D); // Gets the view

        // ... interval
        RandomAccessibleInterval<T> view =
                Views.interval(ra,
                        new long[]{(long)(ri.realMin(0)/xPixelSize),
                                   (long)(ri.realMin(1)/yPixelSize),
                                   (long)(ri.realMin(2)/zPixelSize)},
                        new long[]{+(long)(ri.realMax(0)/xPixelSize),
                                   +(long)(ri.realMax(1)/yPixelSize),
                                   +(long)(ri.realMax(2)/zPixelSize)}); //Sets the interval

        return view;
    }

    public static <T extends RealType<T>> RandomAccessibleInterval<T> getCroppedSampledRRAI(
            RealRandomAccessible<T> rra,
            double px, double py, double pz,
            double real_w, double real_h, double real_depth,
            double pxSize
    ) {
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.identity();
        at3D.translate(px,py,pz);
        FinalRealInterval fi = new FinalRealInterval(new double[]{0,0,0}, new double[]{real_w, real_h, real_depth});
        return RealCropper.getCroppedSampledRRAI(rra,at3D,fi,pxSize,pxSize,pxSize);
    }

}
