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

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.labkit.SourcesLabkitClassifyCommand;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.service.SourceBdvDisplayService;
import sc.fiji.bdvpg.scijava.service.SourceService;

import java.io.File;

/**
 * Demo showing how to apply a pre-trained Labkit classifier to SourceAndConverter sources
 * and visualize the lazy-computed segmentation results in BigDataViewer.
 * <p>
 * This demo assumes you have already trained a classifier using Labkit (see DemoLabkitIntegration).
 * The classifier is applied to create a new segmented source that computes on-demand.
 * </p>
 */
public class DemoLabkitSegmentation {

    static {
        LegacyInjector.preinit();
    }

    /**
     * Main entry point for the demo.
     *
     * @param args command line arguments (ignored)
     * @throws Exception if an error occurs during execution
     */
    public static void main(final String... args) throws Exception {
        final ImageJ ij = new ImageJ();
        DemoHelper.startFiji(ij);

        runSegmentationDemo(ij);
    }

    /**
     * Demonstrates applying a Labkit classifier to sources and displaying results.
     *
     * @param ij the ImageJ instance
     * @throws Exception if an error occurs during execution
     */
    public static void runSegmentationDemo(ImageJ ij) throws Exception {
        // Expand tree view for better screenshots
        DemoHelper.expandTreeView(ij,5);

        // @doc-step: Get services once for reuse
        // You can also get the services at the beginning of scripts by
        // using scjava parameters:
        // #@SourceService source_service
        // #@SourceBdvDisplayService display_service
        SourceService sourceService = ij.get(SourceService.class);
        SourceBdvDisplayService displayService = ij.get(SourceBdvDisplayService.class);

        // @doc-step: Download the Sample Dataset
        // Download a sample LLS7 (Lattice Light Sheet 7) dataset from Zenodo.
        // This dataset contains multi-channel Hela-Kyoto cells.
        // In your own workflow, you would use your local file instead.
        File fileCZI = DatasetHelper
                .getDataset("https://zenodo.org/records/14505724/files/Hela-Kyoto-1-Timepoint-LLS7.czi");

        // @doc-step: Open the Dataset
        // Load the CZI file using the Bio-Formats opener command.
        // This registers the sources in BigDataViewer-Playground.
        // @doc-command: ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand
        String datasetName = fileCZI.getName();
        ij.command().run(DatasetFromBioFormatsCreateCommand.class, true,
                "datasetname", datasetName,
                "unit", "MICROMETER",
                "files", new File[]{fileCZI},
                "split_rgb_channels", false,
                "auto_pyramidize", true,
                "plane_origin_convention", "CENTER",
                "disable_memo", false
        ).get();

        DemoHelper.shot("DemoLabkitSegmentation_03_dataset_loaded", "BDV Sources");

        // @doc-step: Apply the Labkit Classifier
        // Apply a pre-trained Labkit classifier to the sources.
        // This creates a new source with lazy-computed segmentation labels.
        // The classifier was trained using DemoLabkitIntegration.
        // @doc-command: ch.epfl.biop.scijava.command.source.labkit.SourcesLabkitClassifierCommand
        File classifierFile = new File(
                DemoLabkitSegmentation.class.getResource("/lls7-nuc-bg.classifier").getPath()
        );

        SourceAndConverter<?> classifiedSource = (SourceAndConverter<?>) ij.command().run(
                SourcesLabkitClassifyCommand.class, true,
                "sources", datasetName,
                "classifier_file", classifierFile,
                "resolution_level", 0,
                "suffix", "_classified",
                "use_gpu", true
        ).get().getOutput("source_out");

        //DemoHelper.shot("DemoLabkitSegmentation_04_classifier_applied", "BigDataViewer");

        // @doc-step: Display Sources in BigDataViewer
        // Show both the original sources and the classified result in BDV.
        // The segmentation is computed lazily as you navigate.
        SourceAndConverter<?>[] originalSources = sourceService.tree()
                .getSources(datasetName)
                .toArray(new SourceAndConverter[0]);

        displayService.show(originalSources);
        displayService.show(classifiedSource);

        // Configure display range for the classification labels (typically 0-N classes)
        sourceService.getConverterSetup(classifiedSource).setDisplayRange(0, 5);

        // Adjust view to fit the classified source
        BdvHandle bdvHandle = displayService.getActiveBdv();
        new ViewerTransformAdjuster(bdvHandle, classifiedSource).run();

        Thread.sleep(6000); // Wait for classification to occur before snapshoting

        DemoHelper.shot("DemoLabkitSegmentation_05_bdv_display", "BigDataViewer");

        System.out.println("Demo completed! The segmentation is computed lazily as you navigate.");
    }
}
