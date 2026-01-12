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

import bdv.util.BdvFunctions;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ch.epfl.biop.scijava.command.source.deconvolve.SourcesDeconvolverCommand;
import ch.epfl.biop.scijava.command.spimdata.LLS7OpenDatasetCommand;
import net.haesleinhuepf.clij.CLIJ;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import org.apache.commons.io.FilenameUtils;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;

import java.io.File;
import java.util.concurrent.Future;

public class DemoDeconvolution {

    static {
        LegacyInjector.preinit();
    }

    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception ExecutionException – if the computation threw an exception, InterruptedException – if the current thread was interrupted while waiting
     */
    public static void main(final String... args) throws Exception {

        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        CLIJ.getAvailableDeviceNames().forEach(System.out::println);

        demoDeconvolution(ij);
    }

    public static void demoDeconvolution(ImageJ ij) throws Exception {

        // Load the LLS7 dataset
        File helaKyotoLLS7 = DatasetHelper.getDataset("https://zenodo.org/records/14505724/files/Hela-Kyoto-1-Timepoint-LLS7.czi");

        ij.command().run(LLS7OpenDatasetCommand.class, true,
                "czi_file", helaKyotoLLS7,
                "legacy_xy_mode", false).get();

        // Load the PSF
        File psfLLS7 = DatasetHelper.getDataset("https://zenodo.org/records/14505724/files/psf-200nm.tif");
        ij.command().run(CreateBdvDatasetBioFormatsCommand.class, true,
                "files", new File[]{psfLLS7},
                "datasetname", "psf_lls7_200nm",
                "unit", "MICROMETER",
                "split_rgb_channels", false,
                "auto_pyramidize", false,
                "plane_origin_convention", "CENTER",
                "disable_memo", false
        ).get();

        // Get the source and PSF from the service
        String datasetName = FilenameUtils.removeExtension(helaKyotoLLS7.getName());
        SourceAndConverterService sacService = ij.context().getService(SourceAndConverterService.class);

        SourceAndConverter[] sources = sacService.getUI().getSourceAndConvertersFromPath(datasetName)
                .toArray(new SourceAndConverter[0]);

        SourceAndConverter psf = sacService.getUI().getSourceAndConvertersFromPath("psf_lls7_200nm")
                .toArray(new SourceAndConverter[0])[0];

        // Run the deconvolution command
        Future<?> result = ij.command().run(SourcesDeconvolverCommand.class, true,
                "sacs", new SourceAndConverter[]{sources[0]},
                "psf", psf,
                "output_pixel_type", SourcesDeconvolverCommand.ORIGINAL,
                "suffix", "_deconvolved",
                "block_size_x", 128 - 32,
                "block_size_y", 512 - 32,
                "block_size_z", 32,
                "overlap_size", 32,
                "num_iterations", 40,
                "non_circulant", false,
                "regularization_factor", 0.001f,
                "n_threads", 4
        );

        // Wait for completion and get output
        result.get();

        // Get the deconvolved source from the service (it was registered by the command)
        SourceAndConverter[] deconvolvedSources = sacService.getSourceAndConverters().stream()
                .filter(sac -> sac.getSpimSource().getName().contains("_deconvolved"))
                .toArray(SourceAndConverter[]::new);

        if (deconvolvedSources.length > 0) {
            BdvFunctions.show(deconvolvedSources[0]);
        }
    }
}
