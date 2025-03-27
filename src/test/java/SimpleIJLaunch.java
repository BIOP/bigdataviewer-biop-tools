import ch.epfl.biop.sourceandconverter.transform.SourceTimeMapper;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

import java.io.IOException;

public class SimpleIJLaunch {

    static public void main(String... args) throws IOException {
        final ImageJ ij = new ImageJ();

        DebugTools.enableLogging("INFO");
        ij.ui().showUI();

        //SourceTransformHelper.createNewTransformedSourceAndConverter();

        //SourceTimeMapper

    }

}
