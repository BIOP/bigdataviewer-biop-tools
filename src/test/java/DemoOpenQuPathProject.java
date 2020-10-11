import ch.epfl.biop.bdv.bioformats.command.BioformatsBigdataviewerBridgeDatasetCommand;
import ch.epfl.biop.scijava.command.QuPathProjectToBDVDatasetCommand;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;

public class DemoOpenQuPathProject {

    static {
        LegacyInjector.preinit();
    }

    static public void main(String... args) throws Exception {
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        ij.command().run(QuPathProjectToBDVDatasetCommand.class, true,
                BioformatsBigdataviewerBridgeDatasetCommand.getDefaultParameters()
        ).get();

    }
}
