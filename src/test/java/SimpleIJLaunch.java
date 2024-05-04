import java.io.IOException;

import static ch.epfl.biop.DatasetHelper.dowloadBrainVSIDataset;

public class SimpleIJLaunch {

    static public void main(String... args) throws IOException {
        /*final ImageJ ij = new ImageJ();

        DebugTools.enableLogging("INFO");
        ij.ui().showUI();*/

        dowloadBrainVSIDataset();



    }

}
