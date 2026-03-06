import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.slicer.SlicerViews;
import bdv.viewer.SourceAndConverter;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.viewers.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.services.SourceServices;
import sc.fiji.bdvpg.source.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.dataset.importer.XMLToDatasetImporter;

import java.util.List;


/**
 * Apparently this works now.
 */
public class DemoZSlicedSource {

    static {
        LegacyInjector.preinit();
    }

    static final ImageJ ij = new ImageJ();

    static public void main(String... args) throws Exception {

        ij.ui().showUI();

        // load and convert the famous blobs image// Gets active BdvHandle instance
        BdvHandle bdv = SourceServices.getBdvDisplayService().getActiveBdv();
        // Import SpimData
        new XMLToDatasetImporter("src/test/resources/mri-stack.xml").run();

        final List<SourceAndConverter<?>> sources = SourceServices.getSourceService().getSources();

        sources.forEach( source -> {
            SourceServices.getBdvDisplayService().show( bdv, source );
            new ViewerTransformAdjuster( bdv, source ).run();
            new BrightnessAutoAdjuster<>( source, 0 ).run();
        } );

        RandomAccessibleInterval<?> nonResliced = sources.get(0).getSpimSource().getSource(0,0);

        ExtendedRandomAccessibleInterval rai = SlicerViews.extendSlicer(nonResliced,2,0);

        BdvFunctions.show(
        Views.interval(rai,
                new FinalInterval(nonResliced.dimension(0)*nonResliced.dimension(2),
                        nonResliced.dimension(1),
                        1)
                       ),
                "Sliced"
                );

    }

}
