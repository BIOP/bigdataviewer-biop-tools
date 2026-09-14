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
