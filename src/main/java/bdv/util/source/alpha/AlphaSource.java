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

import bdv.util.DefaultInterpolators;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.*;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;

/**
 * Alpha channel of a {@link bdv.viewer.Source}
 * {@link FloatType} to fit in the 32 bits of an {@link net.imglib2.type.numeric.ARGBType} which
 * is normally used in bigdataviewer projectors.
 *
 * alpha = 1f : complete Opacity, 0 : fully transparent
 *
 * The interface {@link IAlphaSource} is used to identify which sources are alpha sources
 * in {@link sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier}
 *
 * This class is abstract because depending on the type of the original {@link Source} (Warped, or
 * Transformed), you may want to implement differently some methods. If you need to completely
 * override it, just implement {@link IAlphaSource}, as it is done in {@link AlphaSourceTransformed}
 *
 * NOTE : for more beautiful (= sharp) edges, do not interpolate the alpha source
 *
 * @author Nicolas Chiaruttini, 2021, EPFL
 *
 */

abstract public class AlphaSource implements IAlphaSource {

    protected final DefaultInterpolators< FloatType > interpolators = new DefaultInterpolators<>();

    final Source<?> origin;

    public AlphaSource(Source<?> origin) {
        this(origin, 1f);
    }

    public AlphaSource(Source<?> origin, float alpha) {
        this.origin = origin;
        this.alpha = alpha;
    }

    final float alpha;

    @Override
    public boolean isPresent(int t) {
        return origin.isPresent(t);
    }

    @Override
    abstract public RandomAccessibleInterval<FloatType> getSource(int t, int level);

    /**
     * To avoid weird edge effects, the alpha source is not interpolated : always using Interpolation.NEARESTNEIGHBOR
     * @param t timepoint
     * @param level resolution level
     * @param method of interpolation, ignored for alpha channel
     * @return a continuous field of float value representing the transparency of its linked source (presence: 1 or absence:0)
     */
    @Override
    abstract public RealRandomAccessible<FloatType> getInterpolatedSource(int t, int level, Interpolation method);

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        origin.getSourceTransform(t,level,transform);
    }

    @Override
    public FloatType getType() {
        return new FloatType();
    }

    @Override
    public String getName() {
        return origin.getName()+"_alpha";
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return origin.getVoxelDimensions();
    }

    @Override
    public int getNumMipmapLevels() {
        return origin.getNumMipmapLevels();
    }

    @Override
    public boolean doBoundingBoxCulling() {
        return origin.doBoundingBoxCulling();
    }
}
