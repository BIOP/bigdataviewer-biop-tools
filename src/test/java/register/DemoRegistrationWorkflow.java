package register;

import loci.common.DebugTools;
import net.imagej.ImageJ;

public class DemoRegistrationWorkflow {

    static final ImageJ ij = new ImageJ();

    static public void main(String... args) throws Exception {
        DebugTools.setRootLevel("INFO");
        ij.ui().showUI();
        /*Thread.sleep(5000);
        ij.command().run(CreateBdvDatasetQuPathCommand.class,true).get();
        ij.command().run(PairRegistrationCreateCommand.class, true).get();
        ij.command().run(PairRegistrationSift2DAffineCommand.class, true).get();*/
    }
}
