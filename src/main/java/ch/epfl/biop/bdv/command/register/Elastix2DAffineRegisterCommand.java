package ch.epfl.biop.bdv.command.register;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.register.Elastix2DAffineRegister;
import ch.epfl.biop.wrappers.elastix.RegParamAffine_Fast;
import ch.epfl.biop.wrappers.elastix.RegParamRigid_Default;
import ch.epfl.biop.wrappers.elastix.RegisterHelper;
import ch.epfl.biop.wrappers.elastix.RegistrationParameters;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Sources with Elastix (Affine, 2D)")
public class Elastix2DAffineRegisterCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Fixed source for registration", description = "fixed source")
    SourceAndConverter sac_fixed;

    @Parameter
    int tpFixed;

    @Parameter
    int levelFixedSource;

    @Parameter(label = "Moving source for registration", description = "moving source")
    SourceAndConverter sac_moving;

    @Parameter
    int tpMoving;

    @Parameter
    int levelMovingSource;

    @Parameter
    double px,py,pz,sx,sy;

    @Parameter
    double pxSizeInCurrentUnit;

    @Parameter
    boolean interpolate;

    @Parameter
    boolean showImagePlusRegistrationResult = false;

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter registeredSource;

    @Parameter(type = ItemIO.OUTPUT)
    AffineTransform3D at3D;

    /*@Parameter(persist = false, required = false)
    String serverURL = null;

    @Parameter(persist = false, required = false)
    String taskInfo = null;*/

    @Override
    public void run() {

        RegisterHelper rh = new RegisterHelper();
        RegistrationParameters rp =  new RegParamAffine_Fast(); //

        rp.AutomaticScalesEstimation = false;
        rp.AutomaticTransformInitialization = true;
        rp.AutomaticTransformInitializationMethod = "CenterOfGravity";

        double maxSize = Math.max(sx/pxSizeInCurrentUnit,sy/pxSizeInCurrentUnit);
        int nScales = 0;

        while (Math.pow(2,nScales)<maxSize) {
            nScales++;
        }

        rp.NumberOfResolutions = Math.max(1,nScales-2);
        rp.BSplineInterpolationOrder = 1;
        rp.MaximumNumberOfIterations = 100;

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
                showImagePlusRegistrationResult);
        reg.setInterpolate(interpolate);

        //if ((serverURL!=null)&&(serverURL.trim()!="")) reg.setRegistrationServer(serverURL);
        //if ((taskInfo!=null)&&(taskInfo.trim()!="")) rh.setExtraRegisterInfo(taskInfo);

        reg.run();

        registeredSource = reg.getRegisteredSac();
        at3D = reg.getAffineTransform();
    }
}
