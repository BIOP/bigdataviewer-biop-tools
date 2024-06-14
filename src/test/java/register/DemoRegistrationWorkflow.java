package register;

import ch.epfl.biop.bdv.img.qupath.command.CreateBdvDatasetQuPathCommand;
import ch.epfl.biop.registration.scijava.command.PairRegistrationCreateCommand;
import ch.epfl.biop.registration.scijava.command.PairRegistrationSift2DAffineCommand;
import net.imagej.ImageJ;

public class DemoRegistrationWorkflow {

    static final ImageJ ij = new ImageJ();

    static public void main(String... args) throws Exception {
        ij.ui().showUI();
        ij.command().run(CreateBdvDatasetQuPathCommand.class,true).get();
        ij.command().run(PairRegistrationCreateCommand.class, true).get();
        ij.command().run(PairRegistrationSift2DAffineCommand.class, true).get();
    }
}
