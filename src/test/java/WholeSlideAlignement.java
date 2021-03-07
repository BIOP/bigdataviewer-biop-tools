import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.bioformats.command.BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand;
import loci.common.DebugTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;

import java.io.File;

//TODO
public class WholeSlideAlignement {

    public static void main(String... args) throws Exception {

        DebugTools.enableIJLogging(false);
        DebugTools.enableLogging("INFO");

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // Open Dataset
        DatasetHelper.getSampleVSIDataset();
        File f = new File (DatasetHelper.getSampleVSIDataset());

        AbstractSpimData asd = (AbstractSpimData) ij.command().run(BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand.class,true,
                "files", new File[]{f},
                       "splitRGBChannels", false,
                       "unit",  "MILLIMETER"
                ).get().getOutput("spimData");

        SourceAndConverter[] sources = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(asd)
                .toArray(new SourceAndConverter[0]);

        SourceAndConverterServices
                .getSourceAndConverterDisplayService()
                .show(sources);

        BdvHandle bdvh = SourceAndConverterServices.getSourceAndConverterDisplayService().getActiveBdv();

        for (SourceAndConverter source : sources) {
            new BrightnessAutoAdjuster(source, 0).run();
        }

        new ViewerTransformAdjuster(bdvh, sources[2]).run();

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
