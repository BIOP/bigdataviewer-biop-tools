import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.util.BoundedRealTransform;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.command.exporter.BasicBdvViewToImagePlusExportCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.FinalRealInterval;
import net.imglib2.type.numeric.ARGBType;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.sourceandconverter.register.BigWarpLauncher;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DemoImagePlusExport
{

    static {
        LegacyInjector.preinit();
    }

    static public void main(String... args) {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        demo();
    }


    public static void demo() {
        final String filePath = "src/test/resources/mri-stack.xml";
        // Import SpimData
        SpimDataFromXmlImporter importer = new SpimDataFromXmlImporter(filePath);

        AbstractSpimData spimData = importer.get();

        SourceAndConverter sac = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(spimData)
                .get(0);

        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceAndConverterServices.getBdvDisplayService().getActiveBdv();

        SourceAndConverterServices.getBdvDisplayService().show(bdvHandle, sac);

        new BrightnessAutoAdjuster(sac, 0).run();

        new ViewerTransformAdjuster(bdvHandle, sac).run();

        final BasicBdvViewToImagePlusExportCommand exportCommand = new BasicBdvViewToImagePlusExportCommand();
        exportCommand.bdv_h = bdvHandle;
        exportCommand.samplingxyinphysicalunit = 1.0;
        exportCommand.samplingzinphysicalunit = 1.0;
        exportCommand.run();
        exportCommand.imageplus.show();


    }
}
