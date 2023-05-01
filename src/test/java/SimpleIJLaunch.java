import loci.common.DebugTools;
import net.imagej.ImageJ;

public class SimpleIJLaunch {

    static public void main(String... args) {
        final ImageJ ij = new ImageJ();

        DebugTools.enableLogging("INFO");
        ij.ui().showUI();
    }

}
