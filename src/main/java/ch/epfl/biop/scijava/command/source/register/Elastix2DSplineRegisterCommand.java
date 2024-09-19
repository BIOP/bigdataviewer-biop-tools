package ch.epfl.biop.scijava.command.source.register;

import ch.epfl.biop.sourceandconverter.register.Elastix2DSplineRegister;
import net.imglib2.realtransform.RealTransform;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class/*,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Sources with Elastix (Spline, 2D)"*/)
public class Elastix2DSplineRegisterCommand extends AbstractElastix2DRegistrationInRectangleCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Number of control points along the X axis")
    int num_ctrl_points_x;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform rt;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform rt_inverse;

    @Parameter
    Context ctx;

    @Parameter(type = ItemIO.OUTPUT)
    boolean success;

    @Override
    public void run() {
        ElastixHelper.checkOrSetLocal(ctx);
        Elastix2DSplineRegister reg = new Elastix2DSplineRegister(
                sacs_fixed, level_fixed_source, tp_fixed,
                sacs_moving, level_moving_source, tp_moving,
                num_ctrl_points_x,
                px_size_in_current_unit,
                px,py,pz,sx,sy,
                max_iteration_per_scale,
                background_offset_value_moving,
                background_offset_value_fixed,
                show_image_registration);

        reg.setInterpolate(interpolate);

        success = reg.run();

        //registeredSource = reg.getRegisteredSac();
        rt = reg.getRealTransform();
        rt_inverse = reg.getRealTransformInverse();
    }

}
