package ch.epfl.biop.registration.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.sourceandconverter.affine.Sift2DAffineRegistration;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Sources with Sift - 2D - Affine",
        description = "Performs a manual registration with BigWarp between two sources."  )

public class Sift2DRegistrationCommand extends AbstractSourcesRegistrationCommand implements Command {

    @Parameter(label = "Registration re-sampling (micrometers)")
    double pixel_size_micrometer = 20;

    @Parameter(label = "Invert moving image")
    boolean invert_moving;

    @Parameter(label = "Invert fixed image")
    boolean invert_fixed;

    @Override
    protected void addRegistrationSpecificParameters(Map<String, Object> parameters) {
        parameters.put(Registration.RESAMPLING_PX_SIZE, pixel_size_micrometer/1000.0);
        parameters.put("invert_moving", invert_moving);
        parameters.put("invert_fixed", invert_fixed);
    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        return new Sift2DAffineRegistration();
    }

    @Override
    protected boolean validate() {
        return true;
    }
}
