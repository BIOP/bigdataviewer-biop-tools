import ch.epfl.biop.bdv.img.bioformats.BioFormatsOpener;
import loci.common.DebugTools;
import loci.formats.in.ZeissCZIReader;
import net.imagej.ImageJ;

public class SimpleIJLaunch {

    static public void main(String... args) {
        final ImageJ ij = new ImageJ();

        DebugTools.enableLogging("INFO");
        ij.ui().showUI();
    }

}
