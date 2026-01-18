/*-
 * #%L
 * Labkit Segmentation Demo for BigDataViewer-Playground - BIOP - EPFL
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

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ch.epfl.biop.scijava.command.source.labkit.SourcesLabkitClassifierCommand;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;

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
        DemoHelper.expandTreeView(ij);

        // Get services once for reuse
        SourceAndConverterService sacService = ij.get(SourceAndConverterService.class);
        SourceAndConverterBdvDisplayService displayService = ij.get(SourceAndConverterBdvDisplayService.class);

        // @doc-step: Download the Sample Dataset
        // Download a sample LLS7 (Lattice Light Sheet 7) dataset from Zenodo.
        // This dataset contains multi-channel Hela-Kyoto cells.
        // In your own workflow, you would use your local CZI files instead.
        File fileCZI = DatasetHelper
                .getDataset("https://zenodo.org/records/14505724/files/Hela-Kyoto-1-Timepoint-LLS7.czi");

        // @doc-step: Open the Dataset
        // Load the CZI file using the Bio-Formats opener command.
        // This registers the sources in BigDataViewer-Playground.
        // @doc-command: ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand
        String datasetName = fileCZI.getName();
        ij.command().run(CreateBdvDatasetBioFormatsCommand.class, true,
                "datasetname", datasetName,
                "unit", "MICROMETER",
                "files", new File[]{fileCZI},
                "split_rgb_channels", false,
                "auto_pyramidize", true,
                "plane_origin_convention", "CENTER",
                "disable_memo", false
        ).get();

        DemoHelper.shot("DemoLabkitSegmentation_02_dataset_loaded");

        // @doc-step: Apply the Labkit Classifier
        // Apply a pre-trained Labkit classifier to the sources.
        // This creates a new source with lazy-computed segmentation labels.
        // The classifier was trained using DemoLabkitIntegration.
        // @doc-command: ch.epfl.biop.scijava.command.source.labkit.SourcesLabkitClassifierCommand
        File classifierFile = new File(
                DemoLabkitSegmentation.class.getResource("/lls7-nuc-bg.classifier").getPath()
        );

        SourceAndConverter<?> classifiedSource = (SourceAndConverter<?>) ij.command().run(
                SourcesLabkitClassifierCommand.class, true,
                "sacs", datasetName,
                "classifier_file", classifierFile,
                "resolution_level", 0,
                "suffix", "_classified",
                "use_gpu", true
        ).get().getOutput("sac_out");

        DemoHelper.shot("DemoLabkitSegmentation_03_classifier_applied");

        // @doc-step: Display Sources in BigDataViewer
        // Show both the original sources and the classified result in BDV.
        // The segmentation is computed lazily as you navigate.
        SourceAndConverter<?>[] originalSources = sacService.getUI()
                .getSourceAndConvertersFromPath(datasetName)
                .toArray(new SourceAndConverter[0]);

        displayService.show(originalSources);
        displayService.show(classifiedSource);

        // Configure display range for the classification labels (typically 0-N classes)
        sacService.getConverterSetup(classifiedSource).setDisplayRange(0, 5);

        // Adjust view to fit the classified source
        BdvHandle bdvHandle = displayService.getActiveBdv();
        new ViewerTransformAdjuster(bdvHandle, classifiedSource).run();

        Thread.sleep(4000); // Wait for classification to occur before snapshoting

        DemoHelper.shot("DemoLabkitSegmentation_04_bdv_display");

        System.out.println("Demo completed! The segmentation is computed lazily as you navigate.");
    }
}
