import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ch.epfl.biop.scijava.command.source.labkit.SourcesLabkitClassifierCommand;
import ch.epfl.biop.scijava.command.source.labkit.SourcesLabkitCommand;
import ch.epfl.biop.scijava.command.spimdata.LLS7OpenDatasetCommand;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.apache.commons.io.FilenameUtils;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceLabkitClassifier;

import java.io.File;
import java.util.List;

/**
 * Demo showing how to apply a Labkit classifier to SourceAndConverter sources
 * and get lazy-computed segmentation results using SourceLabkitClassifier.
 */
public class DemoLabkitSegmentation {

    static {
        LegacyInjector.preinit();
    }

    public static void main(final String... args) throws Exception {
        final ImageJ ij = new ImageJ();
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

        // Path to the classifier
        String classifierPath = DemoLabkitSegmentation.class.getResource("/lls7-nuc-bg.classifier").getPath();
        System.out.println("Classifier path: " + classifierPath);

        SourceAndConverter<UnsignedByteType> classified = (SourceAndConverter<UnsignedByteType>) ij.command().run(SourcesLabkitClassifierCommand.class, true,
                "sacs", datasetName,
                "classifier_file", new File(classifierPath),
                "resolution_level", 0,
                "suffix", "_classified",
                "use_gpu", true
        ).get().getOutput("sac_out");

        SourceAndConverter[] sources = ij.get(SourceAndConverterService.class)
                .getUI().getSourceAndConvertersFromPath(datasetName).toArray(new SourceAndConverter[0]);

        ij.get(SourceAndConverterBdvDisplayService.class)
                .show(sources);

        ij.get(SourceAndConverterBdvDisplayService.class)
                .show(classified);

        ij.get(SourceAndConverterService.class).getConverterSetup(classified).setDisplayRange(0,5);

        BdvHandle bdvh = ij.get(SourceAndConverterBdvDisplayService.class).getActiveBdv();

        new ViewerTransformAdjuster(bdvh, classified).run();

    }
}
