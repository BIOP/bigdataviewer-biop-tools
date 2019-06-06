import loci.common.DebugTools;
import net.imagej.ImageJ;

public class SimpleIJLaunch {

    static public void main(String... args) {
        // create the ImageJ application context with all available services

        DebugTools.enableIJLogging(false);
        DebugTools.enableLogging("INFO");

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
    }
}
