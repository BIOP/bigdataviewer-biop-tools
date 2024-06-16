package ch.epfl.biop.registration.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.sourceandconverter.affine.Elastix2DAffineRegistration;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Pair with Elastix - 2D - Affine",
        description = "Performs a manual registration with BigWarp between two sources."  )

public class PairRegistrationElastix2DAffineCommand extends AbstractPairRegistrationInROI2DCommand implements Command {

    @Parameter(label = "Registration re-sampling (micrometers)")
    double pixel_size_micrometer = 20;

    @Parameter(label = "Show registration results as ImagePlus")
    boolean show_imageplus_registration_result;

    @Override
    protected void addRegistrationSpecificParametersExceptRoi(Map<String, Object> parameters) {
        parameters.put(Registration.RESAMPLING_PX_SIZE, pixel_size_micrometer/1000.0);
        parameters.put("background_offset_value_moving", 0);
        parameters.put("background_offset_value_fixed", 0);
        parameters.put("showImagePlusRegistrationResult", show_imageplus_registration_result);
    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        return new Elastix2DAffineRegistration();
    }

    @Override
    protected boolean validate() {
        return true;
    }
}
