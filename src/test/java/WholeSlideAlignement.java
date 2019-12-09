import bdv.util.BdvHandle;
import ch.epfl.biop.bdv.scijava.command.spimdata.SpimdatasetOpenXML;
import loci.common.DebugTools;
import net.imagej.ImageJ;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class WholeSlideAlignement {

    public static void main(String... args) {
        DebugTools.enableIJLogging(false);
        DebugTools.enableLogging("INFO");

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // Open Dataset

        DatasetHelper.getSampleVSIDataset();
        File f = DatasetHelper.getDataset(DatasetHelper.LIF);

        try {
            // Opens dataset
            BdvHandle bdvh = (BdvHandle) ij.command().run(SpimdatasetOpenXML.class, true,
                    "file", f, "createNewWindow", true).get().getOutput("bdv_h");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }
}
