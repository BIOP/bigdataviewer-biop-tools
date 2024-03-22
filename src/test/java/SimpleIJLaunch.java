import loci.common.DebugTools;
import net.imagej.ImageJ;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static fused.DatasetHelper.dowloadBrainVSIDataset;

public class SimpleIJLaunch {

    static public void main(String... args) throws IOException {
        /*final ImageJ ij = new ImageJ();

        DebugTools.enableLogging("INFO");
        ij.ui().showUI();*/

        dowloadBrainVSIDataset();



    }

}
