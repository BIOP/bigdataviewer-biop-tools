
import ch.epfl.biop.bdv.img.qupath.command.CreateBdvDatasetQuPathCommand;
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
        ij.command().run(CreateBdvDatasetQuPathCommand.class, true).get();

    }
}
