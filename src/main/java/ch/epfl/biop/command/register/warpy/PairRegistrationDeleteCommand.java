package ch.epfl.biop.command.register.warpy;

import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.command.Command;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

import java.io.IOException;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.RegisterMenu, weight = BdvPgMenus.RegisterW),
                @Menu(label = "Warpy", weight = -2),
                @Menu(label = "Delete Registration Pair", weight = 14)
        },
        description = "Removes a registration pair from memory and closes associated resources")
public class PairRegistrationDeleteCommand implements BdvPlaygroundActionCommand {

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
