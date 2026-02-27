package ch.epfl.biop.command.register.warpy;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.source.affine.Elastix2DAffineRegistration;
import ch.epfl.biop.source.processor.SourcesProcessor;
import org.scijava.command.Command;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Register>Warpy>Register Pair - Elastix Affine 2D",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.RegisterMenu, weight = BdvPgMenus.RegisterW),
                @Menu(label = "Warpy", weight = -2),
                @Menu(label = "Register Pair - Affine Elastix 2D", weight = 7)
        },
        description = "Performs automatic 2D affine registration using Elastix")

public class PairRegistrationElastix2DAffineCommand extends AbstractPairRegistrationInROI2DCommand implements Command {

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
    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        return new Elastix2DAffineRegistration();
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
