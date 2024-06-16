package ch.epfl.biop.registration.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Pair - Center moving on fixed",
        description = "Defines a registration pair"  )
public class PairRegistrationCenterCommand extends AbstractPairRegistration2DCommand implements Command {


    @Parameter
    RegistrationPair registration_pair;

    @Override
    protected void addRegistrationParameters(Map<String, Object> parameters) {

    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        return null;
    }

    @Override
    protected boolean validate() {
        return true;
    }
}
