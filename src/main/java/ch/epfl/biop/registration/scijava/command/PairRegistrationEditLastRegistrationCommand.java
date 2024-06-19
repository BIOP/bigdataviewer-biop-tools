package ch.epfl.biop.registration.scijava.command;

import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Pair - Edit last registration",
        description = "Edit the last registration of a registration pair"  )
public class PairRegistrationEditLastRegistrationCommand implements Command {

    @Parameter
    RegistrationPair registration_pair;

    @Override
    public void run() {
        registration_pair.editLastRegistration();
    }
}
