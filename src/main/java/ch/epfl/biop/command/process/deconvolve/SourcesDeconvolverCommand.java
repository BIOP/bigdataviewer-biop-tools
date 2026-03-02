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
package ch.epfl.biop.command.process.deconvolve;

import bdv.cache.SharedQueue;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.SourceHelper;
import ch.epfl.biop.source.deconvolve.Deconvolver;
import net.imglib2.type.numeric.RealType;
import org.scijava.ItemIO;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Process>Deconvolve>Source - Deconvolve (Richardson Lucy GPU - Tiled)",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.ProcessMenu, weight = BdvPgMenus.ProcessW),
                @Menu(label = "Deconvolve", weight = -1.1),
                @Menu(label = "Source - Deconvolve (Richardson Lucy GPU - Tiled)", weight = 6)
        },
        description = "Lazy tiled Richardson Lucy deconvolution of BDV sources that uses the GPU via CLIJ2")
public class SourcesDeconvolverCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Source(s)",
            style = "sorted",
            description = "The sources to deconvolve")
    SourceAndConverter<?>[] sources;

    @Parameter(label = "PSF Source",
            description = "Point spread function used for deconvolution (applied to all sources)")
    SourceAndConverter<?> psf;

    final public static String ORIGINAL = "Keep Pixel Type Of Original Image";
    final public static String FLOAT = "Float";

    @Parameter(label = "Output Pixel Type",
            choices = {ORIGINAL, FLOAT},
            description = "Pixel type for the deconvolved output")
    String output_pixel_type;

    @Parameter(label = "Name Suffix",
            description = "Suffix appended to source names for the deconvolved outputs")
    String suffix = "_deconvolved";

    @Parameter(label = "Block Size X",
            description = "Tile size in X dimension for GPU processing (pixels)")
    int block_size_x;

    @Parameter(label = "Block Size Y",
            description = "Tile size in Y dimension for GPU processing (pixels)")
    int block_size_y;

    @Parameter(label = "Block Size Z",
            description = "Tile size in Z dimension for GPU processing (pixels)")
    int block_size_z;

    @Parameter(label = "Overlap Size",
            description = "Overlap between adjacent tiles to avoid edge artifacts (pixels)")
    int overlap_size;

    @Parameter(label = "Iterations",
            description = "Number of Richardson-Lucy deconvolution iterations")
    int num_iterations;

    @Parameter(label = "Non-Circulant",
            description = "When checked, uses non-circulant boundary conditions (reduces edge artifacts)")
    boolean non_circulant;

    @Parameter(label = "Regularization Factor",
            description = "Regularization strength to prevent noise amplification (0 = no regularization)")
    float regularization_factor;

    @Parameter(label = "Number of Threads",
            description = "Number of parallel threads for tile processing")
    int n_threads = 10;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The deconvolved sources")
    SourceAndConverter<?>[] sources_out;

    @Override
    public void run() {
        // Validation
        if (psf.getSpimSource().isPresent(1)) {
            System.out.println("PSF has several timepoints. All except the first timepoint will be ignored.");
        }

        if (psf.getSpimSource().getNumMipmapLevels()>1) {
            System.out.println("PSF has several resolution levels. Only the highest resolution level will be used.");
        }

        if (!psf.getSpimSource().isPresent(0)) {
            System.err.println("PSF should be defined at timepoint 0.");
            return;
        }

        if (!(psf.getSpimSource().getType() instanceof RealType)) {
            System.err.println("The PSF has pixel type of "+psf.getSpimSource().getType().getClass()+", which cannot be used for deconvolution.");
            return;
        }

        sources_out = new SourceAndConverter[sources.length];

        int nMipmapLevels = sources[0].getSpimSource().getNumMipmapLevels();

        int[] cellDimensions = new int[]{block_size_x, block_size_y, block_size_z};
        int[] overlap = new int[]{overlap_size, overlap_size, overlap_size};

        switch (output_pixel_type) {
            case FLOAT:
                for (int i = 0; i < sources.length; i++) {
                    sources_out[i] = Deconvolver.getDeconvolved(
                            (SourceAndConverter) sources[i],
                            sources[i].getSpimSource().getName() + suffix,
                            cellDimensions,
                            overlap,
                            num_iterations,
                            non_circulant,
                            regularization_factor,
                            (SourceAndConverter) psf,
                            new SharedQueue(n_threads, 1)
                    );
                }
                break;
            case ORIGINAL:
                for (int i = 0; i < sources.length; i++) {
                    sources_out[i] = Deconvolver.getDeconvolvedCast(
                            (SourceAndConverter) sources[i],
                            sources[i].getSpimSource().getName() + suffix,
                            cellDimensions,
                            overlap,
                            num_iterations,
                            non_circulant,
                            regularization_factor,
                            (SourceAndConverter) psf,
                            new SharedQueue(n_threads, 1)
                    );
                }
                break;
            default:
                throw new RuntimeException("Unrecognized output pixel type " + output_pixel_type);
        }


        if (nMipmapLevels>1) {
            System.out.println("The original image has multiresolution levels, all resolution levels will be discarded and recomputed");
            for (int i = 0; i< sources.length; i++) {
                sources_out[i] = SourceHelper.lazyPyramidizeXY2((SourceAndConverter) sources_out[i]);
            }
        }

    }

}
