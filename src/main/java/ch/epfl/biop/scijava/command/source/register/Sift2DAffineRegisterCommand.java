package ch.epfl.biop.scijava.command.source.register;

import ch.epfl.biop.sourceandconverter.register.SIFTRegister;
import mpicbg.imagefeatures.FloatArray2DSIFT;
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
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Obsolete>Register Sources with SIFT (Affine, 2D)",
        description = "Performs an affine registration in 2D between 2 sources. Low level command which\n"+
                      "requires many parameters. For more user friendly command, use wizards instead.\n"+
                      "Outputs the transform to apply to the moving source.")
public class Sift2DAffineRegisterCommand extends Abstract2DRegistrationInRectangleCommand implements BdvPlaygroundActionCommand {

    private static Logger logger = LoggerFactory.getLogger(Sift2DAffineRegisterCommand.class);

    @Parameter
    boolean invert_moving;

    @Parameter
    boolean invert_fixed;

    @Parameter(type = ItemIO.OUTPUT)
    boolean success;

    @Override
    public void run() {
        FloatArray2DSIFT.Param param = new FloatArray2DSIFT.Param();
        param.maxOctaveSize = 2048;

        SIFTRegister reg = new SIFTRegister(
                sacs_fixed, level_fixed_source, tp_fixed,invert_fixed,
                sacs_moving, level_moving_source, tp_moving,invert_moving,
                px_size_in_current_unit,
                px,py,pz,sx,sy,
                param,
                0.92f,
                25.0f,
                0.05f,
                7
                );

        reg.setInterpolate(interpolate);

        success = reg.run();

        if (success) {
            at3d = reg.getAffineTransform();
        } else {
            logger.error("Error during registration: "+reg.getErrorMessage());
        }
    }

}
