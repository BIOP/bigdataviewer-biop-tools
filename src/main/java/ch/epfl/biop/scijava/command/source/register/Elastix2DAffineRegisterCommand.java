package ch.epfl.biop.scijava.command.source.register;

import ch.epfl.biop.sourceandconverter.register.Elastix2DAffineRegister;
import ch.epfl.biop.wrappers.elastix.RegParamAffine_Fast;
import ch.epfl.biop.wrappers.elastix.RegisterHelper;
import ch.epfl.biop.wrappers.elastix.RegistrationParameters;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

/**
 * This command automatically computes the number of scales needed for registration
 * It resamples the original sources in order to allow for a registration based on a specific
 * region and not on the whole image. This also allows to register landmark regions
 * on big images. See {@link RegisterWholeSlideScans2DCommand}
 */

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Obsolete>Register Sources with Elastix (Affine, 2D)",
        description = "Performs an affine registration in 2D between 2 sources. Low level command which\n"+
                      "requires many parameters. For more user friendly command, use wizards instead.\n"+
                      "Outputs the transform to apply to the moving source.")
public class Elastix2DAffineRegisterCommand extends AbstractElastix2DRegistrationInRectangleCommand implements BdvPlaygroundActionCommand {

    private static Logger logger = LoggerFactory.getLogger(Elastix2DAffineRegisterCommand.class);

    @Parameter
    Context ctx;

    @Parameter(type = ItemIO.OUTPUT)
    boolean success;

    @Override
    public void run() {
        ElastixHelper.checkOrSetLocal(ctx);
        RegisterHelper rh = new RegisterHelper();
        if (verbose) {
            rh.verbose();
        }

        RegistrationParameters rp;

        if (sacs_fixed.length>1) {
            if (sacs_fixed.length==sacs_moving.length) {
                RegistrationParameters[] rps = new RegistrationParameters[sacs_fixed.length];
                for (int iCh = 0; iCh<sacs_fixed.length;iCh++) {
                    rps[iCh] = getRegistrationParameters();
                }
                rp = RegistrationParameters.combineRegistrationParameters(rps);
            } else {
                System.err.println("Cannot perform multichannel registration : non identical number of channels between moving and fixed sources.");
                rp = getRegistrationParameters();
            }
        } else {
            rp = getRegistrationParameters();
        }

        rh.addTransform(rp);

        Elastix2DAffineRegister reg = new Elastix2DAffineRegister(
                sacs_fixed, level_fixed_source, tp_fixed,
                sacs_moving, level_moving_source, tp_moving,
                rh,
                px_size_in_current_unit,
                px,py,pz,sx,sy,
                background_offset_value_moving,
                background_offset_value_fixed,
                show_image_registration);
        reg.setInterpolate(interpolate);

        success = reg.run();

        if (success) {
            at3d = reg.getAffineTransform();
        } else {
            logger.error("Error during registration");
        }
    }

    private RegistrationParameters getRegistrationParameters() {
        RegistrationParameters rp = new RegParamAffine_Fast(); //

        rp.AutomaticScalesEstimation = false;
        if (automatic_transform_initialization) {
            rp.AutomaticTransformInitialization = true;
            rp.AutomaticTransformInitializationMethod = "CenterOfGravity";
        } else {
            rp.AutomaticTransformInitialization = false;
        }

        double maxSize = Math.min(sx/ px_size_in_current_unit,sy/ px_size_in_current_unit);

        int nScales = 0;

        while (Math.pow(2,nScales)<maxSize) {
            nScales++;
        }

        int nScalesSkipped = 0;

        while (Math.pow(2,nScalesSkipped)< min_image_size_pix) {
            nScalesSkipped++;
        }

        rp.NumberOfResolutions = Math.max(1,nScales-nScalesSkipped); // Starts with 2^nScalesSkipped pixels

        rp.BSplineInterpolationOrder = 1;
        rp.MaximumNumberOfIterations = max_iteration_per_scale;

        rp.ImagePyramidSchedule = new Integer[2*rp.NumberOfResolutions];
        for (int scale = 0; scale < rp.NumberOfResolutions ; scale++) {
            rp.ImagePyramidSchedule[2*scale] = (int) Math.pow(2, rp.NumberOfResolutions-scale-1);
            rp.ImagePyramidSchedule[2*scale+1] = (int) Math.pow(2, rp.NumberOfResolutions-scale-1);
        }
        return rp;
    }
}
