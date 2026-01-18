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

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.labkit.SourcesInputImage;
import ch.epfl.biop.scijava.command.spimdata.LLS7OpenDatasetCommand;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import org.apache.commons.io.FilenameUtils;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.labkit.ui.LabkitFrame;

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
        ij.ui().showUI();

        demoLabkitWithSourceAndConverter(ij);
    }

    /**
     * Demonstrates opening a SourceAndConverter in Labkit for segmentation.
     *
     * @param ij the ImageJ instance
     * @throws Exception if dataset loading fails
     */
    public static void demoLabkitWithSourceAndConverter(ImageJ ij) throws Exception {

        // @doc-step: Download the Sample Dataset
        // Download a sample LLS7 (Lattice Light Sheet 7) dataset from Zenodo.
        // This dataset contains multi-channel Hela-Kyoto cells.
        // In your own workflow, you would use your local CZI files instead.
        File fileCZI = DatasetHelper
                .getDataset("https://zenodo.org/records/14505724/files/Hela-Kyoto-1-Timepoint-LLS7.czi"); // Multi Channel
                //.getDataset("https://zenodo.org/records/14903188/files/RBC_full_time_series.czi"); // Multi Timepoints

        // @doc-step: Open the LLS7 Dataset
        // Load the CZI file using the LLS7 opener command.
        // This command performs live deskewing of the lattice light sheet data
        // and registers the sources in BigDataViewer-Playground.
        // @doc-command: ch.epfl.biop.scijava.command.spimdata.LLS7OpenDatasetCommand
        ij.command().run(LLS7OpenDatasetCommand.class, true,
                "czi_file", fileCZI,
                "legacy_xy_mode", false).get();

        DemoHelper.shot("DemoLabkitIntegration_01_dataset_loaded");

        // @doc-step: Retrieve the Sources
        // Get all SourceAndConverter objects from the service.
        // These represent the image channels that were loaded.
        String datasetName = FilenameUtils.removeExtension(fileCZI.getName());
        SourceAndConverterService sacService = ij.context().getService(SourceAndConverterService.class);

        // Get all sources under the dataset path (includes all channels)
        SourceAndConverter[] sources = sacService.getSourceAndConverters().toArray(new SourceAndConverter[0]);

        System.out.println("Loaded " + sources.length + " source(s) from dataset: " + datasetName);
        for (int i = 0; i < sources.length; i++) {
            System.out.println("  Channel " + i + ": " + sources[i].getSpimSource().getName());
        }

        // @doc-step: Create Labkit Input Image
        // Wrap the sources in a SourcesInputImage, which adapts them
        // for use with Labkit's segmentation interface.
        // Using resolution level 0 (full resolution) and timepoint 0.
        SourcesInputImage inputImage = new SourcesInputImage(sources, 0);

        System.out.println("Created SourceAndConverterInputImage:");
        System.out.println("  - Image dimensions: " + inputImage.imageForSegmentation().numDimensions() + "D");
        System.out.println("  - Default labeling file: " + inputImage.getDefaultLabelingFilename());

        // @doc-step: Open in Labkit
        // Open Labkit with the input image for interactive segmentation.
        // You can now use Labkit's tools to create labels and train classifiers.
        LabkitFrame labkitFrame = LabkitFrame.showForImage(ij.context(), inputImage);

        // Optional: Add a listener to handle when Labkit is closed
        labkitFrame.onCloseListeners().addListener(() -> {
            System.out.println("Labkit window closed");
        });

        System.out.println("Labkit opened successfully!");

        DemoHelper.shot("DemoLabkitIntegration_02_labkit_open");
    }
}
