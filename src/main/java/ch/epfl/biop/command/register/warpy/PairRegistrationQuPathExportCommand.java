package ch.epfl.biop.command.register.warpy;

import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Register>Warpy>Register Pair - Export To QuPath Project",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.RegisterMenu, weight = BdvPgMenus.RegisterW),
                @Menu(label = "Warpy", weight = -2),
                @Menu(label = "Register Pair - Export To QuPath", weight = 12)
        },
        description = "Exports the registration transforms to a QuPath project for use in Warpy")
public class PairRegistrationQuPathExportCommand implements Command {

    @Parameter(label = "Registration Pair",
            description = "The registration pair to export")
    RegistrationPair registration_pair;

    @Parameter
    Context ctx;

    @Parameter(label = "Allow Overwrite",
            description = "When checked, overwrites existing registration files in the QuPath project")
    boolean allow_overwrite = true;

    @Override
    public void run() {
        registration_pair.exportToQuPath(allow_overwrite, ctx);
    }
}
