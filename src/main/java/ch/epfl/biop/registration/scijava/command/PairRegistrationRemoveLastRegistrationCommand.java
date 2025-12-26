package ch.epfl.biop.registration.scijava.command;

import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Pair - Remove last registration",
        description = "Removes the last registration step from the registration pair")
public class PairRegistrationRemoveLastRegistrationCommand implements Command {

    @Parameter(label = "Registration Pair",
            description = "The registration pair whose last step will be removed")
    RegistrationPair registration_pair;

    @Override
    public void run() {
        registration_pair.removeLastRegistration();
    }
}
