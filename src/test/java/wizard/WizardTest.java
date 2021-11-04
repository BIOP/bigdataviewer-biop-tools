package wizard;

import ch.epfl.biop.wizard.Wizard;
import net.imagej.ImageJ;

public class WizardTest {

    public static void main(String... args) {

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        Wizard.builder(ij.context())
                .nextCommand(DefineNameCommand.class)
                .nextCommand(GreetingInIJCommand.class)
                .connect(0,"name_output", 1, "name")
                .build().run();

    }
}
