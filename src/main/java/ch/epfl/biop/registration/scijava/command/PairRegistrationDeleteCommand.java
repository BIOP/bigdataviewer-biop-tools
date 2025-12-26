package ch.epfl.biop.registration.scijava.command;

import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.command.Command;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.io.IOException;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Delete registration pair",
        description = "Removes a registration pair from memory and closes associated resources")
public class PairRegistrationDeleteCommand implements Command {

    @Parameter(label = "Registration Pair",
            description = "The registration pair to delete")
    RegistrationPair registration_pair;

    @Parameter
    ObjectService objectService;

    @Override
    public void run() {
        objectService.removeObject(registration_pair);
        try {
            registration_pair.close(false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
