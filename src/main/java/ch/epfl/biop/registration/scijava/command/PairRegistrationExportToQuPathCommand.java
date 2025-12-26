package ch.epfl.biop.registration.scijava.command;

import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Register Pair - Export registration to QuPath project",
        description = "Exports the registration transforms to a QuPath project for use in Warpy")
public class PairRegistrationExportToQuPathCommand implements Command {

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
