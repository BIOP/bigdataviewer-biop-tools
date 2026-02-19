package ch.epfl.biop.command.source.register.warpy;

import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Pair - Edit last registration",
        description = "Re-opens the last registration step for editing (e.g., adjust landmarks)")
public class PairRegistrationEditLastRegistrationCommand implements Command {

    @Parameter(label = "Registration Pair",
            description = "The registration pair whose last step will be edited")
    RegistrationPair registration_pair;

    @Override
    public void run() {
        registration_pair.editLastRegistration();
    }
}
