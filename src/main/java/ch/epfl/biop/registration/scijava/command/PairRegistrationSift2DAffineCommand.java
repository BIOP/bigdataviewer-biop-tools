package ch.epfl.biop.registration.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.sourceandconverter.affine.Sift2DAffineRegistration;
import ch.epfl.biop.sourceandconverter.processor.SourcesProcessor;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Pair 2D - Sift Affine",
        description = "Performs a manual registration with BigWarp between two sources."  )

public class PairRegistrationSift2DAffineCommand extends AbstractPairRegistrationInROI2DCommand implements Command {

    @Parameter(label = "Fixed image channels used for registration (comma separated)")
    String channels_fixed_csv;

    @Parameter(label = "Moving image channels used for registration (comma separated)")
    String channels_moving_csv;

    @Parameter(label = "Registration re-sampling (micrometers)")
    double pixel_size_micrometer = 20;

    @Parameter(label = "Invert moving image")
    boolean invert_moving;

    @Parameter(label = "Invert fixed image")
    boolean invert_fixed;

    @Override
    protected void addRegistrationSpecificParametersExceptRoi(Map<String, Object> parameters) {
        parameters.put(Registration.RESAMPLING_PX_SIZE, pixel_size_micrometer/1000.0);
        parameters.put(Sift2DAffineRegistration.INVERT_MOVING_KEY, invert_moving);
        parameters.put(Sift2DAffineRegistration.INVERT_FIXED_KEY, invert_fixed);
    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        return new Sift2DAffineRegistration();
    }

    @Override
    protected boolean validate() {
        return true;
    }

    @Override
    protected SourcesProcessor getSourcesProcessorFixed() {
        return AbstractPairRegistration2DCommand.getChannelProcessorFromCsv(channels_fixed_csv, registration_pair.getFixedSources().length);
    }

    @Override
    protected SourcesProcessor getSourcesProcessorMoving() {
        return AbstractPairRegistration2DCommand.getChannelProcessorFromCsv(channels_moving_csv, registration_pair.getMovingSourcesOrigin().length);
    }
}
