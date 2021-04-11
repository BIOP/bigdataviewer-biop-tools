package ch.epfl.biop.bdv.command.register;

import ch.epfl.biop.sourceandconverter.register.Elastix2DSplineRegister;
import net.imglib2.realtransform.RealTransform;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Sources with Elastix on Server (Spline, 2D)")
public class Elastix2DSplineRegisterServerCommand extends AbstractElastix2DRegistrationInRectangleCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Number of control points along the X axis")
    int nbControlPointsX;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform rt;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform rt_inverse;

    @Parameter(type = ItemIO.OUTPUT)
    boolean success; // No issue during remote registration ?

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
                maxIterationNumberPerScale,
                background_offset_value_moving,
                background_offset_value_fixed,
                showImagePlusRegistrationResult);

        reg.setInterpolate(interpolate);

        if ((serverURL!=null)&&(serverURL.trim()!="")) reg.setRegistrationServer(serverURL);
        if ((taskInfo!=null)&&(taskInfo.trim()!="")) reg.setRegistrationInfo(taskInfo);

        success = reg.run();

        if (success) {
            //registeredSource = reg.getRegisteredSac();
            rt = reg.getRealTransform();
            rt_inverse = reg.getRealTransformInverse();
        }
    }
}
