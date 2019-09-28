import bdv.util.BdvHandle;
import ch.epfl.biop.bdv.scijava.command.open.BigDataViewerPlugInSciJava;
import loci.common.DebugTools;
import net.imagej.ImageJ;

import java.util.concurrent.ExecutionException;

public class WholeSlideAlignement {

    public static void main(String... args) {
        DebugTools.enableIJLogging(false);
        DebugTools.enableLogging("INFO");

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // Open Dataset

        try {
            // Opens dataset
            BdvHandle bdvh = (BdvHandle) ij.command().run(BigDataViewerPlugInSciJava.class, true,
                    "file", "C:\\Users\\nicol\\Dropbox\\BIOP\\QuPath Formation\\Test_60519\\Test_60519\\vsiFused3D_v3.xml",
                    "createNewWindow", true).get().getOutput("bdv_h");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }
}
