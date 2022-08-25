import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.bioformats.command.BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand;
import ch.epfl.biop.scijava.command.spimdata.QuPathProjectToBDVDatasetCommand;
import loci.common.DebugTools;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.command.spimdata.SpimDataExporterCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;

import java.io.File;

//TODO
public class DemoQuPathOpenSaveLoad {

    public static void main(String... args) throws Exception {

        DebugTools.enableIJLogging(false);
        DebugTools.enableLogging("INFO");

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        AbstractSpimData asd = (AbstractSpimData) ij.command().run(QuPathProjectToBDVDatasetCommand.class,true).get().getOutput("spimData");

        SourceAndConverter[] sources = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(asd)
                .toArray(new SourceAndConverter[0]);

        SourceAndConverterServices
                .getBdvDisplayService()
                .show(sources);

        BdvHandle bdvh = SourceAndConverterServices.getBdvDisplayService().getActiveBdv();

        for (SourceAndConverter source : sources) {
            new BrightnessAutoAdjuster(source, 0).run();
        }

        new ViewerTransformAdjuster(bdvh, sources[2]).run();

        //ij.command().run(SpimDataExporterCommand.class,true).get();

        asd.setBasePath(new File("D:\\Remy\\QuPath\\export3.xml"));
        (new XmlIoSpimData()).save((SpimData)asd, "D:\\Remy\\QuPath\\export3.xml");


        /*
        try {
            // Opens dataset
            BdvHandle bdvh = (BdvHandle) ij.command().run(SpimdatasetOpenXML.class, true,
                    "file", f, "createNewWindow", true).get().getOutput("bdv_h");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }*/
    }
}
