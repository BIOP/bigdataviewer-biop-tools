package ch.epfl.biop.registration.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.sourceandconverter.spline.Elastix2DSplineRegistration;
import ch.epfl.biop.sourceandconverter.processor.SourcesProcessor;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Pair 2D - Elastix Spline",
        description = "Performs automatic 2D B-spline deformable registration using Elastix")

public class PairRegistrationElastix2DSplineCommand extends AbstractPairRegistrationInROI2DCommand implements Command {

    @Parameter(label = "Control Points X",
            description = "Number of B-spline control points along X axis (minimum 2, more = finer deformation)")
    int nb_control_points_x = 10;

    @Parameter(label = "Fixed Channels",
            description = "Channel indices of the fixed image to use for registration (comma separated, e.g., '0' or '0,1')")
    String channels_fixed_csv;

    @Parameter(label = "Moving Channels",
            description = "Channel indices of the moving image to use for registration (comma separated, e.g., '0' or '0,1')")
    String channels_moving_csv;

    @Parameter(label = "Pixel Size (um)",
            description = "Pixel size in micrometers for resampling during registration (larger = faster but less precise)")
    double pixel_size_micrometer = 20;

    @Parameter(label = "Show Result",
            description = "When checked, displays the registration result as an ImagePlus for verification")
    boolean show_imageplus_registration_result;

    @Override
    protected void addRegistrationSpecificParametersExceptRoi(Map<String, Object> parameters) {
        parameters.put(Registration.RESAMPLING_PX_SIZE, pixel_size_micrometer/1000.0);
        parameters.put("background_offset_value_moving", 0);
        parameters.put("background_offset_value_fixed", 0);
        parameters.put("show_image_registration", show_imageplus_registration_result);
        parameters.put("num_ctrl_points_x", nb_control_points_x);
    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        return new Elastix2DSplineRegistration();
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
