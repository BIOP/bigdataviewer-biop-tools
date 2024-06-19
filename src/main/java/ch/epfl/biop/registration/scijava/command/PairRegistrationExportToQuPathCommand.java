package ch.epfl.biop.registration.scijava.command;

import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Pair - Export registration to QuPath project",
        description = "If properly defined, exports the registration to the QuPath project"  )
public class PairRegistrationExportToQuPathCommand implements Command {

    @Parameter
    RegistrationPair registration_pair;

    @Parameter
    Context ctx;

    @Parameter
    boolean allow_overwrite = true;

    @Override
    public void run() {
        registration_pair.exportToQuPath(allow_overwrite, ctx);
    }
}
