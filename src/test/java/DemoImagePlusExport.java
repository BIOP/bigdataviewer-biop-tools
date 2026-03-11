import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.command.display.bdv.export.BdvViewToImagePlusBasicExportCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.dataset.importer.XMLToDatasetImporter;

public class DemoImagePlusExport
{
    static final ImageJ ij = new ImageJ();

    static {
        LegacyInjector.preinit();
    }

    static public void main(String... args) {
        ij.ui().showUI();

        demo();
    }


    public static void demo() {
        final String filePath = "src/test/resources/mri-stack.xml";
        // Import SpimData
        XMLToDatasetImporter importer = new XMLToDatasetImporter(filePath);

        AbstractSpimData<?> spimData = importer.get();

        SourceAndConverter<?> source = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData)
                .get(0);

        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceServices.getBdvDisplayService().getActiveBdv();

        // Show the sourceandconverter
        SourceServices.getBdvDisplayService().show(bdvHandle, source);
        new BrightnessAutoAdjuster(source, 0).run();
        new ViewerTransformAdjuster(bdvHandle, source).run();

        // Export
        ij.context()
                .getService( CommandService.class)
                .run( BdvViewToImagePlusBasicExportCommand.class, true,
                        "bdv_h", bdvHandle,
                        "capturename", "image",
                        "zsize", 20,
                        "samplingxyinphysicalunit", 1,
                        "samplingzinphysicalunit", 1,
                        "interpolate", true,
                        "unit", "px",
                        "selected_timepoints_str", "",
                        "export_mode", "Normal",
                        "range", ""
                );
    }
}
