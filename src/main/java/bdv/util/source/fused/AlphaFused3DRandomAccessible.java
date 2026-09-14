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
package bdv.util.source.fused;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

import java.util.function.Supplier;

import static bdv.util.source.fused.AlphaFusedResampledSource.AVERAGE;
import static bdv.util.source.fused.AlphaFusedResampledSource.MAX;
import static bdv.util.source.fused.AlphaFusedResampledSource.MEDIAN;
import static bdv.util.source.fused.AlphaFusedResampledSource.SUM;

public class AlphaFused3DRandomAccessible<T extends RealType<T>> implements RandomAccessible<T> {

    final RandomAccessible<T>[] origins;
    final RandomAccessible<FloatType>[] origins_alpha;
    final SubSetFusedRandomAccess<T> ra;
    final Supplier<T> pixelSupplier;
    final String blendingMode;

    public AlphaFused3DRandomAccessible(String blendingMode, RandomAccessible<T>[] origins, RandomAccessible<FloatType>[] origins_alpha, Supplier<T> pixelSupplier) {
        this.blendingMode = blendingMode;
        this.origins = origins;
        this.origins_alpha = origins_alpha;
        this.pixelSupplier = pixelSupplier;

        int l = origins.length;
        assert origins.length==origins_alpha.length;

        RandomAccess<T>[] origins_ra = new RandomAccess[origins.length];
        RandomAccess<FloatType>[] origins_alpha_ra = new RandomAccess[origins.length];

        for (int i=0;i<l;i++) {
            origins_ra[i] = origins[i].randomAccess();
            origins_alpha_ra[i] = origins_alpha[i].randomAccess();
        }

        switch (blendingMode) {
            case AVERAGE:
                this.ra = new AverageAlphaFused3DRandomAccess<T>(origins_ra, origins_alpha_ra, pixelSupplier);
                break;
            case MEDIAN:
                this.ra = new MedianAlphaFused3DRandomAccess<T>(origins_ra, origins_alpha_ra, pixelSupplier);
                break;
            case MAX:
                this.ra = new MaxAlphaFused3DRandomAccess<T>(origins_ra, origins_alpha_ra, pixelSupplier);
                break;
            case SUM:
                this.ra = new SumAlphaFused3DRandomAccess<T>(origins_ra, origins_alpha_ra, pixelSupplier);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported blending method: "+blendingMode);
        }
    }

    @Override
    public RandomAccess<T> randomAccess() {
        return (RandomAccess<T>) (ra.copy());
    }

    @Override
    public RandomAccess<T> randomAccess(Interval interval) {
        // Could be optimized to remove out of bounds sources - but this is not called
        // System.out.println("COULD BE OPTIMIZED!!");
        return randomAccess();
    }

    public RandomAccess<T> randomAccess(boolean[] sourcesPresentInCell) {
        // Could be optimized to remove out of bounds sources - but this is not called
        //System.out.println("COULD BE OPTIMIZED!!");

        return ra.copy(sourcesPresentInCell);
    }

    @Override
    public int numDimensions() {
        return 3;
    }

    @Override
    public T getType() {
        return pixelSupplier.get();
    }
}
