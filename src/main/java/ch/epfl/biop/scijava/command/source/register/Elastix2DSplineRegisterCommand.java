package ch.epfl.biop.scijava.command.source.register;

import ch.epfl.biop.sourceandconverter.register.Elastix2DSplineRegister;
import net.imglib2.realtransform.RealTransform;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Sources with Elastix (Spline, 2D)")
public class Elastix2DSplineRegisterCommand extends AbstractElastix2DRegistrationInRectangleCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Number of control points along the X axis")
    int nbControlPointsX;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform rt;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform rt_inverse;

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

        reg.run();

        //registeredSource = reg.getRegisteredSac();
        rt = reg.getRealTransform();
        rt_inverse = reg.getRealTransformInverse();
    }

}
