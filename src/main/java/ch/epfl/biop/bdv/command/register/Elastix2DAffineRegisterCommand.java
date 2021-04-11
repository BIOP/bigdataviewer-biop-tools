package ch.epfl.biop.bdv.command.register;

import ch.epfl.biop.sourceandconverter.register.Elastix2DAffineRegister;
import ch.epfl.biop.wrappers.elastix.RegParamAffine_Fast;
import ch.epfl.biop.wrappers.elastix.RegisterHelper;
import ch.epfl.biop.wrappers.elastix.RegistrationParameters;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

/**
 * This command automatically computes the number of scales needed for registration
 * It resamples the original sources in order to allow for a registration based on a specific
 * region and not on the whole image. This also allows to register landmark regions
 * on big images. See {@link RegisterWholeSlideScans2DCommand}
 */

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Sources with Elastix (Affine, 2D)",
        description = "Performs an affine registration in 2D between 2 sources. Low level command which\n"+
                      "requires many parameters. For more user friendly command, use wizards instead.\n"+
                      "Outputs the transform to apply to the moving source."  )
public class Elastix2DAffineRegisterCommand extends AbstractElastix2DRegistrationInRectangleCommand implements BdvPlaygroundActionCommand {

    @Override
    public void run() {

        RegisterHelper rh = new RegisterHelper();
        if (verbose) {
            rh.verbose();
        }

        RegistrationParameters rp =  new RegParamAffine_Fast(); //

        rp.AutomaticScalesEstimation = false;
        if (automaticTransformInitialization) {
            rp.AutomaticTransformInitialization = true;
            rp.AutomaticTransformInitializationMethod = "CenterOfGravity";
        } else {
            rp.AutomaticTransformInitialization = false;
        }

        double maxSize = Math.min(sx/pxSizeInCurrentUnit,sy/pxSizeInCurrentUnit);

        int nScales = 0;

        while (Math.pow(2,nScales)<maxSize) {
            nScales++;
        }

        int nScalesSkipped = 0;

        while (Math.pow(2,nScalesSkipped)<minPixSize) {
            nScalesSkipped++;
        }

        rp.NumberOfResolutions = Math.max(1,nScales-nScalesSkipped); // Starts with 2^nScalesSkipped pixels

        rp.BSplineInterpolationOrder = 1;
        rp.MaximumNumberOfIterations = maxIterationNumberPerScale;

        rp.ImagePyramidSchedule = new Integer[2*rp.NumberOfResolutions];
        for (int scale = 0; scale < rp.NumberOfResolutions ; scale++) {
            rp.ImagePyramidSchedule[2*scale] = (int) Math.pow(2, rp.NumberOfResolutions-scale-1);
            rp.ImagePyramidSchedule[2*scale+1] = (int) Math.pow(2, rp.NumberOfResolutions-scale-1);
        }

        rh.addTransform(rp);

        Elastix2DAffineRegister reg = new Elastix2DAffineRegister(
                sac_fixed,levelFixedSource,tpFixed,
                sac_moving,levelMovingSource,tpMoving,
                rh,
                pxSizeInCurrentUnit,
                px,py,pz,sx,sy,
                background_offset_value_moving,
                background_offset_value_fixed,
                showImagePlusRegistrationResult);
        reg.setInterpolate(interpolate);

        boolean success = reg.run();

        if (success) {
            //registeredSource = reg.getRegisteredSac();
            at3D = reg.getAffineTransform();
        } else {
            System.err.println("Error during registration");
        }
    }
}
