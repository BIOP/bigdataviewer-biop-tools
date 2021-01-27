package ch.epfl.biop.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.register.Elastix2DSplineRegister;
import net.imglib2.realtransform.RealTransform;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Sources with Elastix (Spline, 2D)")
public class Elastix2DSplineRegisterCommand implements Command {

    @Parameter
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
    int nbControlPointsX;

    @Parameter
    boolean interpolate;

    @Parameter
    boolean showImagePlusRegistrationResult = false;

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter registeredSource;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform rt;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform rt_inverse;

    @Parameter(persist = false, required = false)
    String serverURL = null;

    @Parameter(persist = false, required = false)
    String taskInfo = null;

    @Override
    public void run() {

        Elastix2DSplineRegister reg = new Elastix2DSplineRegister(
                sac_fixed,levelFixedSource,tpFixed,
                sac_moving,levelMovingSource,tpMoving,
                nbControlPointsX,
                pxSizeInCurrentUnit,
                px,py,pz,sx,sy,
                showImagePlusRegistrationResult);
        reg.setInterpolate(interpolate);

        if ((serverURL!=null)&&(serverURL.trim()!="")) reg.setRegistrationServer(serverURL);
        if ((taskInfo!=null)&&(taskInfo.trim()!="")) reg.setRegistrationInfo(taskInfo);

        reg.run();

        registeredSource = reg.getRegisteredSac();
        rt = reg.getRealTransform();
        rt_inverse = reg.getRealTransformInverse();
    }
}
