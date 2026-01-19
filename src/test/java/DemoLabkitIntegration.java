/*-
 * #%L
 * Labkit Integration Demo for BigDataViewer-Playground - BIOP - EPFL
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

import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ch.epfl.biop.scijava.command.source.labkit.SourcesLabkitCommand;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;

import java.io.File;

/**
 * Demo showing how to use Labkit with SourceAndConverter from BigDataViewer-Playground.
 * <p>
 * This demo loads an LLS7 dataset and opens it in Labkit for segmentation.
 * </p>
 */
public class DemoLabkitIntegration {

    static {
        LegacyInjector.preinit();
    }

    /**
     * Main function to run the demo.
     *
     * @param args command line arguments (ignored)
     * @throws Exception if dataset loading fails
     */
    public static void main(final String... args) throws Exception {
        // Create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        //ij.ui().showUI();
        DemoHelper.startFiji(ij);

        demoLabkitWithSourceAndConverter(ij);
    }

    /**
     * Demonstrates opening a SourceAndConverter in Labkit for segmentation.
     *
     * @param ij the ImageJ instance
     * @throws Exception if dataset loading fails
     */
    public static void demoLabkitWithSourceAndConverter(ImageJ ij) throws Exception {

        // Expand tree view for better screenshots
        DemoHelper.expandTreeView(ij);

        // @doc-step: Download the Sample Dataset
        // Download a sample LLS7 (Lattice Light Sheet 7) dataset from Zenodo.
        // This dataset contains multi-channel Hela-Kyoto cells.
        // In your own workflow, you would use your local CZI files instead.
        File fileCZI = DatasetHelper
                .getDataset("https://zenodo.org/records/14505724/files/Hela-Kyoto-1-Timepoint-LLS7.czi"); // Multi Channel, 1 timepoint
                //.getDataset("https://zenodo.org/records/14903188/files/RBC_full_time_series.czi"); // Multi Timepoints, 1 channel

        // @doc-step: Open the LLS7 Dataset
        // Load the CZI file using the Bio-Formats opener command.
        // This command performs live deskewing of the lattice light sheet data, if you are use the Zeiss Quick Start Reader
        // and registers the sources in BigDataViewer-Playground.
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

        DemoHelper.shot("DemoLabkitIntegration_02_dataset_loaded");

        // @doc-step: Open in Labkit
        // Open Labkit with the input image for interactive segmentation.
        // You can now use Labkit's tools to create labels and train classifiers.
        // Sources can be selected thanks to their path in bigdataviewer-playground's treeview
        // @doc-command: ch.epfl.biop.scijava.command.source.labkit.SourcesLabkitCommand
        ij.command().run(SourcesLabkitCommand.class, true,
                "sacs", datasetName,
                "resolution_level", 0
        ).get();

        DemoHelper.shot("DemoLabkitIntegration_03_labkit_open");

        // @doc-step: Train a Classifier
        // Use Labkit's interactive tools to train a pixel classifier.
        // @doc-manual: Perform the following steps in Labkit:
        // 1. Select the "background" label in the Labels panel
        // 2. Use the brush tool to draw scribbles on background regions
        // 3. Select the "foreground" label
        // 4. Draw scribbles on the cells/structures you want to segment
        // 5. Click "Train Classifier" to see the segmentation result
        // 6. Adjust scribbles as needed to improve the segmentation
        DemoHelper.pauseForUserAction("DemoLabkitIntegration_04_classifier_trained",
                "Please train a classifier in Labkit:\n\n" +
                "1. Select the 'background' label in the Labels panel\n" +
                "2. Use the brush tool (B) to draw scribbles on background regions\n" +
                "3. Select the 'foreground' label\n" +
                "4. Draw scribbles on the cells/structures you want to segment\n" +
                "5. Click 'Train Classifier' to see the segmentation result\n" +
                "6. Adjust scribbles as needed to improve the segmentation");

        // @doc-step: Save the Classifier
        // Export the trained classifier for later use.
        // @doc-manual: Save your classifier:
        // 1. Go to Labkit menu: Segmentation > Save Classifier...
        // 2. Choose a location and filename (e.g., my_classifier.classifier)
        // 3. The classifier can be reloaded later or used in batch processing
        DemoHelper.pauseForUserAction("DemoLabkitIntegration_05_classifier_saved",
                "Please save your trained classifier:\n\n" +
                "1. Go to Labkit menu: Segmentation > Save Classifier...\n" +
                "2. Choose a location and filename (e.g., my_classifier.classifier)\n" +
                "3. The classifier can be reloaded later or used in batch processing");

        System.out.println("Demo completed!");
    }
}
