/*-
 * #%L
 * Tiled GPU Deconvolution for BigDataViewer-Playground - BIOP - EPFL
 * %%
 * Copyright (C) 2024 - 2025 EPFL
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package ch.epfl.biop.sourceandconverter.deconvolve;

import bdv.cache.SharedQueue;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceVoxelProcessor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * Utility class for creating deconvolved sources using Richardson-Lucy GPU deconvolution.
 * <p>
 * This class provides methods to create lazy, tiled deconvolution sources that
 * use CLIJ2 for GPU-accelerated processing.
 * </p>
 */
public class Deconvolver {

    /**
     * Creates a deconvolved source with FloatType output.
     *
     * @param <T> the input pixel type
     * @param source the source to deconvolve
     * @param name the name for the output source
     * @param cellDimensions tile dimensions for GPU processing [x, y, z]
     * @param overlap overlap between tiles [x, y, z]
     * @param numIterations number of Richardson-Lucy iterations
     * @param nonCirculant whether to use non-circulant boundary conditions
     * @param regularizationFactor regularization factor (0 = no regularization)
     * @param psf the point spread function
     * @param queue shared queue for volatile source
     * @return the deconvolved source with FloatType pixels
     */
    public static <T extends RealType<T>> SourceAndConverter<FloatType> getDeconvolved(
            final SourceAndConverter<T> source,
            String name,
            int[] cellDimensions,
            int[] overlap,
            int numIterations,
            boolean nonCirculant,
            float regularizationFactor,
            RandomAccessibleInterval<? extends RealType<?>> psf,
            SharedQueue queue) {

        DeconvolutionProcessor<T> processor = new DeconvolutionProcessor<>(
                cellDimensions, overlap, numIterations, nonCirculant, regularizationFactor, psf);
        processor.initialize(source.getSpimSource());

        return new SourceVoxelProcessor<>(name, source, processor, new FloatType(), queue).get();
    }

    /**
     * Creates a deconvolved source that preserves the original pixel type.
     *
     * @param <T> the input and output pixel type
     * @param source the source to deconvolve
     * @param name the name for the output source
     * @param cellDimensions tile dimensions for GPU processing [x, y, z]
     * @param overlap overlap between tiles [x, y, z]
     * @param numIterations number of Richardson-Lucy iterations
     * @param nonCirculant whether to use non-circulant boundary conditions
     * @param regularizationFactor regularization factor (0 = no regularization)
     * @param psf the point spread function
     * @param queue shared queue for volatile source
     * @return the deconvolved source with the same pixel type as input
     */
    public static <T extends RealType<T> & NativeType<T>> SourceAndConverter<T> getDeconvolvedCast(
            final SourceAndConverter<T> source,
            String name,
            int[] cellDimensions,
            int[] overlap,
            int numIterations,
            boolean nonCirculant,
            float regularizationFactor,
            RandomAccessibleInterval<? extends RealType<?>> psf,
            SharedQueue queue) {

        DeconvolutionProcessorCast<T> processor = new DeconvolutionProcessorCast<>(
                cellDimensions, overlap, numIterations, nonCirculant, regularizationFactor, psf);
        processor.initialize(source.getSpimSource());

        return new SourceVoxelProcessor<>(name, source, processor, source.getSpimSource().getType(), queue).get();
    }
}
