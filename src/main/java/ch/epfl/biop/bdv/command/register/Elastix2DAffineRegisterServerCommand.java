package ch.epfl.biop.bdv.command.register;

import ch.epfl.biop.sourceandconverter.register.Elastix2DAffineRegister;
import ch.epfl.biop.wrappers.elastix.RegParamAffine_Fast;
import ch.epfl.biop.wrappers.elastix.RegisterHelper;
import ch.epfl.biop.wrappers.elastix.RegistrationParameters;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Sources with Elastix on Server (Affine, 2D)")
public class Elastix2DAffineRegisterServerCommand extends AbstractElastix2DRegistrationInRectangleCommand implements BdvPlaygroundActionCommand {

    private static Logger logger = LoggerFactory.getLogger(Elastix2DAffineRegisterServerCommand.class);

    @Parameter(type = ItemIO.OUTPUT)
    boolean success; // No issue during remote registration ?

    @Parameter(type = ItemIO.OUTPUT)
    String error = ""; // No issue during remote registration ?

    @Parameter(persist = false, required = false)
    String serverURL = null;

    @Parameter(persist = false, required = false)
    String taskInfo = null;

    @Override
    public void run() {

        RegisterHelper rh = new RegisterHelper();
        if (verbose) {
            rh.verbose();
        }

        RegistrationParameters rp = new RegParamAffine_Fast();
        rp.AutomaticScalesEstimation = false;
        if (automaticTransformInitialization) {
            rp.AutomaticTransformInitialization = true;
            rp.AutomaticTransformInitializationMethod = "CenterOfGravity";
        } else {
            rp.AutomaticTransformInitialization = false;
        }

        double maxSize = Math.max(sx/pxSizeInCurrentUnit,sy/pxSizeInCurrentUnit);
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

        if ((serverURL!=null)&&(serverURL.trim()!="")) reg.setRegistrationServer(serverURL);
        if ((taskInfo!=null)&&(taskInfo.trim()!="")) rh.setExtraRegisterInfo(taskInfo);

        success = reg.run();

        if (success) {
            //registeredSource = reg.getRegisteredSac();
            at3D = reg.getAffineTransform();
        } else {
            logger.error("Error during registration");
            error = reg.getErrorMessage();
        }
    }
}
