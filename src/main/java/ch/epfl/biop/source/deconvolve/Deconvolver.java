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
package ch.epfl.biop.source.deconvolve;

import bdv.cache.SharedQueue;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.SourceVoxelProcessor;
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
     * @param psfSource the point spread function as a SourceAndConverter
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
            SourceAndConverter<? extends RealType<?>> psfSource,
            SharedQueue queue) {

        DeconvolutionProcessor<T> processor = new DeconvolutionProcessor<>(
                cellDimensions, overlap, numIterations, nonCirculant, regularizationFactor, psfSource);
        processor.initialize(source);

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
     * @param psfSource the point spread function as a SourceAndConverter
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
            SourceAndConverter<? extends RealType<?>> psfSource,
            SharedQueue queue) {

        DeconvolutionProcessorCast<T> processor = new DeconvolutionProcessorCast<>(
                cellDimensions, overlap, numIterations, nonCirculant, regularizationFactor, psfSource);
        processor.initialize(source);

        return new SourceVoxelProcessor<>(name, source, processor, source.getSpimSource().getType(), queue).get();
    }

    /**
     * Distills a point spread function (PSF) from an image of sub-resolution beads
     * and a mask locating their centres ("PSF distillation" / reverse deconvolution).
     * <p>
     * PSF distillation exploits the fact that convolution is commutative. The bead
     * image is modelled as {@code beads = points ⊛ PSF}, so Richardson-Lucy - the very
     * same engine used for deconvolution - can recover the missing factor (the PSF) by
     * swapping the roles of "image" and "kernel": the bead image is fed as the observed
     * image, and the point mask is fed as the "PSF"/kernel. The recovered latent image
     * is the distilled PSF.
     * </p>
     * <p>
     * <b>Why this cannot be tiled.</b> In ordinary deconvolution the kernel is a small,
     * spatially local PSF, which is what makes tiling valid. Here the "kernel" is the
     * point mask, which spans the whole volume and is non-local; splitting it across
     * tiles would be meaningless. The whole volume is therefore processed as a single
     * tile with no overlap. Consequently the entire image (and its FFT) must fit in GPU
     * memory - fine for typical bead stacks, but not for large volumes.
     * </p>
     * <p>
     * The output is a full-size estimate; the distilled PSF sits at its centre and is
     * normally cropped and re-centred afterwards, exactly as in the classic ops-based
     * distillation script.
     * </p>
     *
     * @param <T> the input pixel type of the bead image
     * @param beadsSource the image containing sub-resolution beads
     * @param pointMaskSource a same-sized image with a single non-zero pixel at the
     *                        centre of each detected bead
     * @param name the name for the output PSF source
     * @param numIterations number of Richardson-Lucy iterations
     * @param nonCirculant whether to use non-circulant boundary conditions
     * @param regularizationFactor regularization factor (0 = no regularization)
     * @param queue shared queue for volatile source
     * @return the distilled PSF as a FloatType source
     */
    public static <T extends RealType<T>> SourceAndConverter<FloatType> distillPSF(
            final SourceAndConverter<T> beadsSource,
            final SourceAndConverter<? extends RealType<?>> pointMaskSource,
            String name,
            int numIterations,
            boolean nonCirculant,
            float regularizationFactor,
            SharedQueue queue) {

        // Process the whole volume as a single tile: the point mask (the "PSF" here) is
        // non-local and spans the full image, so tiling would be incorrect.
        final long[] dims = beadsSource.getSpimSource().getSource(0, 0).dimensionsAsLongArray();
        if (dims.length < 3) {
            throw new IllegalArgumentException("PSF distillation requires a 3D bead source, but got "
                    + dims.length + " dimensions.");
        }

        final int[] cellDimensions = new int[]{
                Math.toIntExact(dims[0]),
                Math.toIntExact(dims[1]),
                Math.toIntExact(dims[2])
        };
        final int[] overlap = new int[]{0, 0, 0};

        // Reuse the deconvolution engine with image = beads and kernel = point mask.
        return getDeconvolved(
                beadsSource,
                name,
                cellDimensions,
                overlap,
                numIterations,
                nonCirculant,
                regularizationFactor,
                pointMaskSource,
                queue);
    }
}
