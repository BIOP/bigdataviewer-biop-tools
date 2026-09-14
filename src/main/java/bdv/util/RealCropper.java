/*-
 * #%L
 * BigDataViewer tools and plugins for Fiji: fusion, deconvolution, registration and data processing.
 * %%
 * Copyright (C) 2018 - 2026 EPFL BIOP and University of Geneva
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
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
