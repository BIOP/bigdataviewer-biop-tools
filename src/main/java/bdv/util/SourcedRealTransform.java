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

import bdv.util.source.field.ITransformFieldSource;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealViews;

/**
 * This RealTransform class takes a Source of RealLocalizable and turns it into a RealTransform object
 * This (potentially) allows to use caching, resampling and interpolation to speed up computation of complex
 * transformations.
 */
public class SourcedRealTransform implements RealTransform {

    final ITransformFieldSource source;
    final RealRandomAccessible<RealLocalizable> realRandomAccess;
    final AffineTransform3D transform3D = new AffineTransform3D();

    public SourcedRealTransform(ITransformFieldSource source) {
        this.source = source;
        source.getSourceTransform(0,0, transform3D);
        this.realRandomAccess = RealViews.affine(source.getInterpolatedSource(0,0,null), transform3D);
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
        realPositionable.setPosition(realRandomAccess.getAt(realLocalizable).positionAsDoubleArray());
    }

    @Override
    public RealTransform copy() {
        return new SourcedRealTransform(source);
    }

    public RealTransform getTransform() {
        return source.getTransform();
    }
}
