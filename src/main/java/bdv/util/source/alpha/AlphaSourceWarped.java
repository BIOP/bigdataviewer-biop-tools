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
package bdv.util.source.alpha;

import bdv.img.WarpedSource;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import net.imglib2.*;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * Alpha Source of a {@link WarpedSource}
 *
 * The transformation is synchronized because this source knows which source it is the alpha of.
 *
 */

public class AlphaSourceWarped extends AlphaSource {

    final WarpedSource<?> origin_warped;

    IAlphaSource origin_alpha;

    public AlphaSourceWarped(Source<?> origin) {
        super(origin);
        assert origin instanceof WarpedSource;
        origin_warped = (WarpedSource<?>) origin;
    }

    public AlphaSourceWarped(Source<?> origin, float alpha) {
        super(origin, alpha);
        origin_warped = (WarpedSource<?>) origin;
    }

    public IAlphaSource getOriginAlpha() {
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
                getOriginAlpha().getInterpolatedSource(t, level, method);
        if (origin_warped.isTransformed()) {final AffineTransform3D transform = new AffineTransform3D();
            getOriginAlpha().getSourceTransform(t, level, transform);
            final RealRandomAccessible<FloatType> srcRa = getOriginAlpha().getInterpolatedSource(t, level, method);
            if (origin_warped.getTransform() == null)
                return srcRa;
            else {
                final RealTransformSequence seq = new RealTransformSequence();
                seq.add(transform);
                seq.add(origin_warped.getTransform().copy()); // copy ? sure ?
                seq.add(transform.inverse());
                return new RealTransformRealRandomAccessible<>(srcRa, seq);
            }
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
