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
