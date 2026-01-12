import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.scijava.command.spimdata.LLS7OpenDatasetCommand;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.apache.commons.io.FilenameUtils;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceLabkitClassifier;

import java.io.File;

/**
 * Demo showing how to apply a Labkit classifier to SourceAndConverter sources
 * and get lazy-computed segmentation results using SourceLabkitClassifier.
 */
public class DemoLabkitSegmentation {

    static {
        LegacyInjector.preinit();
    }

    public static void main(final String... args) throws Exception {
        // Create ImageJ context
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // Load LLS7 dataset
        File fileCZI = DatasetHelper.getDataset(
                "https://zenodo.org/records/14505724/files/Hela-Kyoto-1-Timepoint-LLS7.czi");

        ij.command().run(LLS7OpenDatasetCommand.class, true,
                "czi_file", fileCZI,
                "legacy_xy_mode", false).get();

        // Get sources
        String datasetName = FilenameUtils.removeExtension(fileCZI.getName());
        SourceAndConverterService sacService = ij.context().getService(SourceAndConverterService.class);
        SourceAndConverter[] sources = sacService.getSourceAndConverters().toArray(new SourceAndConverter[0]);

        System.out.println("Loaded " + sources.length + " source(s)");

        // Path to the classifier
        String classifierPath = DemoLabkitSegmentation.class.getResource("/lls7-nuc-bg.classifier").getPath();
        System.out.println("Classifier path: " + classifierPath);

        // Create SourceLabkitClassifier action - applies the classifier lazily
        SourceLabkitClassifier classifier = new SourceLabkitClassifier(
                sources,
                classifierPath,
                ij.context(),
                "Segmentation",
                0, true  // resolution level
        );

        // Get the result (this creates the lazy source)
        SourceAndConverter<UnsignedByteType> segmentationSac = classifier.get();

        SourceAndConverterServices.getSourceAndConverterService().register(segmentationSac);

        System.out.println("Segmentation created with classes: " + classifier.getClassNames());

        // Display in BDV
        BdvStackSource<?> bdv = BdvFunctions.show(sources[0]);
        BdvFunctions.show(segmentationSac, BdvOptions.options().addTo(bdv));

        System.out.println("Segmentation displayed!");
    }
}
