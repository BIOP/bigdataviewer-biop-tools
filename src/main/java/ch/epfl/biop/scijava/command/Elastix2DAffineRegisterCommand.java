package ch.epfl.biop.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.register.Elastix2DAffineRegister;
import ch.epfl.biop.wrappers.elastix.RegParamAffine_Fast;
import ch.epfl.biop.wrappers.elastix.RegisterHelper;
import ch.epfl.biop.wrappers.elastix.RegistrationParameters;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class,
        menuPath = "BigDataViewer>Sources>Register>Register Sources with Elastix (Affine, 2D)")
public class Elastix2DAffineRegisterCommand implements Command {

    @Parameter(label = "Fixed source for registration", description = "fixed source")
    SourceAndConverter sac_fixed;

    @Parameter
    int tpFixed;

    @Parameter
    int levelFixedSource;

    @Parameter
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

    @Override
    public void run() {

        RegisterHelper rh = new RegisterHelper();
        RegistrationParameters rp = new RegParamAffine_Fast();
        rp.AutomaticScalesEstimation = true;
        double maxSize = Math.max(sx/pxSizeInCurrentUnit,sy/pxSizeInCurrentUnit);
        int nScales = 0;

        while (Math.pow(2,nScales)<maxSize) {
            nScales++;
        }
        //System.out.println("nScales = "+nScales);
        rp.NumberOfResolutions = nScales-2;
        rp.BSplineInterpolationOrder = 1;
        rp.MaximumNumberOfIterations = 100;
        rh.addTransform(rp);

        Elastix2DAffineRegister reg = new Elastix2DAffineRegister(
                sac_fixed,levelFixedSource,tpFixed,
                sac_moving,levelMovingSource,tpMoving,
                rh,
                pxSizeInCurrentUnit,
                px,py,pz,sx,sy,
                showImagePlusRegistrationResult);
        reg.setInterpolate(interpolate);
        reg.run();

        registeredSource = reg.getRegisteredSac();
        at3D = reg.getAffineTransform();
    }
}
