import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ch.epfl.biop.bdv.img.samples.DatasetHelper;
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

        AbstractSpimData<?> asd = (AbstractSpimData<?>) ij.command().run(CreateBdvDatasetBioFormatsCommand.class,true,
                "files", new File[]{f},
                       "splitrgbchannels", false,
                       "unit",  "MILLIMETER"
                ).get().getOutput("spimdata");

        SourceAndConverter<?>[] sources = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(asd)
                .toArray(new SourceAndConverter[0]);

        SourceAndConverterServices
                .getBdvDisplayService()
                .show(sources);

        BdvHandle bdvh = SourceAndConverterServices.getBdvDisplayService().getActiveBdv();

        for (SourceAndConverter<?> source : sources) {
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
