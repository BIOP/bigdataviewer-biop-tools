import ch.epfl.biop.bdv.img.legacy.bioformats.command.BioformatsBigdataviewerBridgeDatasetCommand;
import ch.epfl.biop.scijava.command.spimdata.QuPathProjectToBDVDatasetCommand;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;

public class DemoOpenQuPathProject {

    static {
        LegacyInjector.preinit();
    }

    static public void main(String... args) throws Exception {
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        DebugTools.enableLogging("INFO");
        ij.command().run(QuPathProjectToBDVDatasetCommand.class, true,
                BioformatsBigdataviewerBridgeDatasetCommand.getDefaultParameters()
        ).get();

    }
}
