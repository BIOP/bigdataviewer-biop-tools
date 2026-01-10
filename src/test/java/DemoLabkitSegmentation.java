import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.labkit.SourcesToImgPlus;
import ch.epfl.biop.scijava.command.spimdata.LLS7OpenDatasetCommand;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.apache.commons.io.FilenameUtils;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.labkit.ui.segmentation.SegmentationUtils;
import sc.fiji.labkit.ui.segmentation.Segmenter;
import sc.fiji.labkit.ui.segmentation.weka.TrainableSegmentationSegmenter;

import java.io.File;

/**
 * Demo showing how to apply a Labkit classifier to SourceAndConverter sources
 * and get lazy-computed segmentation results.
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

        // Create lazy segmentation
        RandomAccessibleInterval<UnsignedByteType> lazySegmentation =
                createLazySegmentation(ij, sources, classifierPath, 0);

        // Display in BDV
        BdvStackSource<?> bdv = BdvFunctions.show(sources[0]);
        BdvFunctions.show(VolatileViews.wrapAsVolatile(lazySegmentation), "Segmentation", BdvOptions.options().addTo(bdv));

        System.out.println("Segmentation displayed!");
    }

    /**
     * Creates a lazy segmentation from sources using a Labkit classifier.
     *
     * @param ij the ImageJ instance (for context)
     * @param sources the input sources
     * @param classifierPath path to the .classifier file
     * @param resolutionLevel the resolution level to use
     * @return a lazy RandomAccessibleInterval with segmentation results
     */
    public static RandomAccessibleInterval<UnsignedByteType> createLazySegmentation(
            ImageJ ij,
            SourceAndConverter[] sources,
            String classifierPath,
            int resolutionLevel) {

        // Load the classifier
        Segmenter segmenter = new TrainableSegmentationSegmenter(ij.context());
        segmenter.openModel(classifierPath);

        System.out.println("Classifier loaded. Classes: " + segmenter.classNames());

        // Convert sources to ImgPlus (what Labkit expects)
        ImgPlus<?> imgPlus = SourcesToImgPlus.wrap(sources, "input", resolutionLevel);

        System.out.println("ImgPlus created: " + imgPlus.numDimensions() + "D");

        // Create lazy cached segmentation
        RandomAccessibleInterval<UnsignedByteType> lazySegmentation =
                SegmentationUtils.createCachedSegmentation(segmenter, imgPlus, null);

        System.out.println("Lazy segmentation created");

        return lazySegmentation;
    }
}
