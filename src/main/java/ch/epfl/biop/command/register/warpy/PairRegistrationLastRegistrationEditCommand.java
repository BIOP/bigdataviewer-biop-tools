package ch.epfl.biop.command.register.warpy;

import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.command.Command;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Register>Warpy>Register Pair - Edit last registration",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.RegisterMenu, weight = BdvPgMenus.RegisterW),
                @Menu(label = "Warpy", weight = -2),
                @Menu(label = "Register Pair - Edit Last Registration", weight = 10)
        },
        description = "Re-opens the last registration step for editing (e.g., adjust landmarks)")
public class PairRegistrationLastRegistrationEditCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Registration Pair",
            description = "The registration pair whose last step will be edited")
    RegistrationPair registration_pair;

    @Override
    public void run() {
        registration_pair.editLastRegistration();
    }
}
