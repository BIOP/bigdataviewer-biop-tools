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

import bdv.cache.SharedQueue;
import bdv.util.BdvFunctions;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ch.epfl.biop.scijava.command.spimdata.LLS7OpenDatasetCommand;
import ch.epfl.biop.sourceandconverter.deconvolve.Deconvolver;
import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clijx.imglib2cache.Clij2RichardsonLucyImglib2Cache;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import org.apache.commons.io.FilenameUtils;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;

import java.io.File;

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

        //System.out.println(System.getProperty("java.library.path"));
        //clij2fftWrapper.diagnostic();

        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        CLIJ.getAvailableDeviceNames().forEach(System.out::println);

        demoDeconvolution(ij);
    }

    public static void demoDeconvolution(ImageJ ij) throws Exception {

        File helaKyotoLLS7 = DatasetHelper.getDataset("https://zenodo.org/records/14505724/files/Hela-Kyoto-1-Timepoint-LLS7.czi");

        ij.command().run(LLS7OpenDatasetCommand.class, true,
                "czi_file", helaKyotoLLS7,
                "legacy_xy_mode", false).get();

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

        String datasetName = FilenameUtils.removeExtension(helaKyotoLLS7.getName());
        SourceAndConverter source = ij.context().getService(SourceAndConverterService.class).getUI().getSourceAndConvertersFromPath(datasetName)
                .toArray(new SourceAndConverter[0])[0];

        SourceAndConverter psf = ij.context().getService(SourceAndConverterService.class).getUI().getSourceAndConvertersFromPath("psf_lls7_200nm")
                .toArray(new SourceAndConverter[0])[0];

        Clij2RichardsonLucyImglib2Cache.Builder builder =
                Clij2RichardsonLucyImglib2Cache.builder()
                        .nonCirculant(false)
                        .numberOfIterations(40)
                        .psf(psf.getSpimSource().getSource(0,0))
                        .overlap(32, 32, 16)
                        .regularizationFactor(0.001f);

        SourceAndConverter<?> deconvolved = Deconvolver.getDeconvolvedCast(
                source,
                "Deconvolved",
                new int[]{128-32,512-32,32},
                builder,
                new SharedQueue(4,1));

        BdvFunctions.show(deconvolved);

        ij.get(SourceAndConverterService.class).register(deconvolved);

    }

}