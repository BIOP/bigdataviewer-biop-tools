package ch.epfl.biop.registration.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.sourceandconverter.bigwarp.SacBigWarp2DRegistration;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Sources with BigWarp - 2D - Spline",
        description = "Performs a manual registration with BigWarp between two sources."  )

public class SacBigWarp2DRegistrationCommand extends AbstractSourcesRegistrationCommand implements Command {


    @Override
    protected void addRegistrationSpecificParameters(Map<String, Object> parameters) {
        // Nothing required
    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        return new SacBigWarp2DRegistration();
    }

    @Override
    protected boolean validate() {
        return true;
    }
}
