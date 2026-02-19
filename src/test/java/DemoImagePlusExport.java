import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.command.bdv.BasicBdvViewToImagePlusExportCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.viewers.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.services.SourceServices;
import sc.fiji.bdvpg.source.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.dataset.importer.SpimDataFromXmlImporter;

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
        SpimDataFromXmlImporter importer = new SpimDataFromXmlImporter(filePath);

        AbstractSpimData spimData = importer.get();

        SourceAndConverter sac = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData)
                .get(0);

        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceServices.getBdvDisplayService().getActiveBdv();

        // Show the sourceandconverter
        SourceServices.getBdvDisplayService().show(bdvHandle, sac);
        new BrightnessAutoAdjuster(sac, 0).run();
        new ViewerTransformAdjuster(bdvHandle, sac).run();

        // Export
        ij.context()
                .getService( CommandService.class)
                .run( BasicBdvViewToImagePlusExportCommand.class, true,
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
