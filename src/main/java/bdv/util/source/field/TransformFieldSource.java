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
package bdv.util.source.field;

import bdv.viewer.Interpolation;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;

public class TransformFieldSource implements ITransformFieldSource {

    final RealTransform transform;
    final int targetDimensions;
    final int sourceDimensions;
    final String name;

    public TransformFieldSource(RealTransform transform, String name) throws UnsupportedOperationException {
        this.transform = transform;
        this.sourceDimensions = transform.numSourceDimensions();
        this.targetDimensions = transform.numTargetDimensions();
        this.name = name;
    }

    @Override
    public boolean isPresent(int t) {
        return true; // always present
    }

    @Override
    public RandomAccessibleInterval<RealPoint> getSource(int t, int level) {
        throw new UnsupportedOperationException("The raster source of a transform field source is not defined");
    }

    @Override
    public RealRandomAccessible<RealPoint> getInterpolatedSource(int t, int level, Interpolation method) {
        RealTransform transformCopy = transform.copy();
        return new FunctionRealRandomAccessible<>(sourceDimensions, (position, value) -> {
            transformCopy.apply(position, value);
        }, this::getType); // new or keep ?
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        transform.identity();
    }

    @Override
    public RealPoint getType() {
        return new RealPoint(targetDimensions);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return new FinalVoxelDimensions("pixel",1,1,1);
    }

    @Override
    public int getNumMipmapLevels() {
        return 1; // I never remember if it's zero or 1
    }

    @Override
    public int numSourceDimensions() {
        return transform.numSourceDimensions();
    }

    @Override
    public int numTargetDimensions() {
        return transform.numTargetDimensions();
    }

    @Override
    public RealTransform getTransform() {
        return transform;
    }
}
