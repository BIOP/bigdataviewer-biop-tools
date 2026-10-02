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

import bdv.util.BdvFunctions;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.deconvolve.SourcesDeconvolveCommand;
import ch.epfl.biop.command.workflow.lls7.LLS7DatasetOpenCommand;
import net.haesleinhuepf.clij.CLIJ;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import org.apache.commons.io.FilenameUtils;
import sc.fiji.bdvpg.scijava.service.SourceService;

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

        ij.command().run(LLS7DatasetOpenCommand.class, true,
                "czi_file", helaKyotoLLS7,
                "legacy_xy_mode", false).get();

        // Load the PSF
        File psfLLS7 = DatasetHelper.getDataset("https://zenodo.org/records/14505724/files/psf-200nm.tif");
        ij.command().run(DatasetFromBioFormatsCreateCommand.class, true,
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
        SourceService sourceService = ij.context().getService(SourceService.class);

        SourceAndConverter<?>[] sources = sourceService.tree().getSources(datasetName)
                .toArray(new SourceAndConverter[0]);

        SourceAndConverter<?> psf = sourceService.tree().getSources("psf_lls7_200nm")
                .toArray(new SourceAndConverter[0])[0];

        // Run the deconvolution command
        Future<?> result = ij.command().run(SourcesDeconvolveCommand.class, true,
                "sources", new SourceAndConverter[]{sources[0]},
                "psf", psf,
                "output_pixel_type", SourcesDeconvolveCommand.ORIGINAL,
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
        SourceAndConverter<?>[] deconvolvedSources = sourceService.getSources().stream()
                .filter(source -> source.getSpimSource().getName().contains("_deconvolved"))
                .toArray(SourceAndConverter[]::new);

        if (deconvolvedSources.length > 0) {
            BdvFunctions.show(deconvolvedSources[0]);
        }
    }
}
