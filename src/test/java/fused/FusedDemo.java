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
package fused;

import bdv.util.BdvHandle;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.kheops.ometiff.OMETiffExporter;
import ch.epfl.biop.source.SourceFuserAndResampler;
import ij.IJ;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.ARGBType;
import ome.units.UNITS;
import org.junit.After;
import org.junit.Test;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.viewer.bdv.supplier.IBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaSerializableBdvOptions;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.source.display.ColorChanger;
import sc.fiji.bdvpg.dataset.importer.XMLToDatasetImporter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class FusedDemo {
    static ImageJ ij;

    public static void main( String[] args ) throws ExecutionException, InterruptedException
    {
        // Create the ImageJ application context with all available services; necessary for SourceServices creation
        ij = new ImageJ();
        ij.ui().showUI();
        demo();
    }

    @Test
    public void demoRunOk() throws ExecutionException, InterruptedException
    {
        main(new String[]{""});
    }

    @After
    public void closeFiji() {
        TestHelper.closeFijiAndBdvs(ij);
    }

    public static void demo() {

        IBdvSupplier bdvSupplier = new AlphaBdvSupplier(new AlphaSerializableBdvOptions());

        //SourceServices.getBdvDisplayService().setDefaultBdvSupplier(bdvSupplier);

        BdvHandle bdv = bdvSupplier.get(); //SourceServices.getBdvDisplayService().getNewBdv();

        // Import SpimData
        new XMLToDatasetImporter( "src/test/resources/mri-stack.xml" ).run();
        new XMLToDatasetImporter("src/test/resources/mri-stack-shiftedX.xml").run();
        new XMLToDatasetImporter( "src/test/resources/mri-stack-shiftedY.xml" ).run();

        // Get a handle on the sources
        final List<SourceAndConverter<?>> sources = SourceServices.getSourceService().getSources();

        // Show all three sources
        sources.forEach( source -> {
            SourceServices.getBdvDisplayService().show(bdv, source);
            new ViewerTransformAdjuster(bdv, source).run();
            new BrightnessAutoAdjuster<>(source, 0).run();
        });

        // Change color of third one
        new ColorChanger( sources.get( 2 ), new ARGBType( ARGBType.rgba( 0, 255, 0, 255 ) ) ).run();

        SourceAndConverter<?> fused = new SourceFuserAndResampler(sources,
                AlphaFusedResampledSource.AVERAGE,
                sources.get(0), "Fused source",
                true, true, false, 0,
                64, 64, 1, -1,4).get();

        BdvHandle bdvh = SourceServices.getBdvDisplayService().getNewBdv();

        SourceServices
                .getBdvDisplayService().show(bdvh, fused);

        new ViewerTransformAdjuster(bdvh, fused).run();

        System.out.println("Write file");

        try {
            DebugTools.setRootLevel("OFF");
            Instant start = Instant.now();

            OMETiffExporter.builder()
                    .put(fused) //
                    .defineMetaData("test")
                    .putMetadataFromSources(fused, UNITS.MICROMETER)
                    .defineWriteOptions()
                    //.savePath("C:\\Users\\chiarutt\\test.ome.tiff")
                    .savePath("C:\\Users\\nicolas\\Desktop\\test.ome.tiff")
                    .tileSize(128,128)
                    .nThreads(4)
                    .create().export();

            Instant finish = Instant.now();
            IJ.log("Duration: "+ Duration.between(start, finish));
            IJ.log("File saved");
        } catch (Exception e) {
            System.err.println("Error during saving");
            e.printStackTrace();
        }

    }
}
