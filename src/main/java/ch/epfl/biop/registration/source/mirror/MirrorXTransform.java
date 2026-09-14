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
package ch.epfl.biop.registration.source.mirror;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;

public class MirrorXTransform implements InvertibleRealTransform {

    final double xFactor;

    public MirrorXTransform(double xFactor) {
        this.xFactor = xFactor;
    }

    @Override
    public int numSourceDimensions() {
        return 3;
    }

    @Override
    public int numTargetDimensions() {
        return 3;
    }

    @Override
    public void apply(double[] source, double[] target) {
        target[0] = source[0]*xFactor>0 ? source[0]:-source[0];
        target[1] = source[1];
        target[2] = source[2];
    }

    @Override
    public void apply(RealLocalizable realLocalizable, RealPositionable realPositionable) {
        double xPos = realLocalizable.getDoublePosition(0);
        realPositionable.setPosition(xPos*xFactor>0 ? xPos:-xPos,0);
        realPositionable.setPosition(realLocalizable.getDoublePosition(1),1);
        realPositionable.setPosition(realLocalizable.getDoublePosition(2),2);
    }

    @Override
    public void applyInverse(double[] source, double[] target) {
        target[0] = source[0];
        target[1] = source[1];
        target[2] = source[2];
    }

    @Override
    public void applyInverse(RealPositionable realPositionable, RealLocalizable realLocalizable) {
        realPositionable.setPosition(realLocalizable.getDoublePosition(0),0);
        realPositionable.setPosition(realLocalizable.getDoublePosition(1),1);
        realPositionable.setPosition(realLocalizable.getDoublePosition(2),2);
    }

    @Override
    public InvertibleRealTransform inverse() {
        return new AffineTransform3D();
    }

    @Override
    public InvertibleRealTransform copy() {
        return new MirrorXTransform(xFactor);
    }
}
