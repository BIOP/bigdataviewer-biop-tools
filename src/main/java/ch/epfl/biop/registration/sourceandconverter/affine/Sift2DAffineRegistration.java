package ch.epfl.biop.registration.sourceandconverter.affine;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.plugin.IRegistrationPlugin;
import ch.epfl.biop.registration.plugin.RegistrationTypeProperties;
import ch.epfl.biop.scijava.command.source.register.Sift2DAffineRegisterCommand;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Uses Elastix backend to perform an affine transform registration
 *
 */
@Plugin(type = IRegistrationPlugin.class)
@RegistrationTypeProperties(
        isManual = false,
        isEditable = false)

public class Sift2DAffineRegistration extends AffineTransformSourceAndConverterRegistration{


    protected static final Logger logger = LoggerFactory.getLogger(Sift2DAffineRegistration.class);

    public final static String INVERT_MOVING_KEY = "invert_moving";
    public final static String INVERT_FIXED_KEY = "invert_fixed";

    @Override
    public void setFixedImage(SourceAndConverter[] fimg) {
        if (fimg.length==0) {
            logger.error("Error, no fixed image set in class "+this.getClass().getSimpleName());
        }
        if (fimg.length>1) {
            logger.error("Error, sift registration only works with a single channel "+this.getClass().getSimpleName());
        }
        super.setFixedImage(fimg);
    }

    @Override
    public void setMovingImage(SourceAndConverter[] mimg) {
        if (mimg.length==0) {
            logger.error("Error, no fixed image set in class "+this.getClass().getSimpleName());
        }
        if (mimg.length>1) {
            logger.error("Error, sift registration only works with a single channel "+this.getClass().getSimpleName());
        }
        super.setMovingImage(mimg);
    }

    Future<CommandModule> task;

    @Override
    public boolean register() {
        try {
            Class<? extends Command> registrationCommandClass;
            // Is it supposed to be done on a server ?
            registrationCommandClass = Sift2DAffineRegisterCommand.class;

            // Registration success flag
            boolean success = true;

            // Transforms map into flat String : key1, value1, key2, value2, etc.
            // Necessary for CommandService
            List<Object> flatParameters = new ArrayList<>(parameters.size()*2+4);

            double voxSizeInMm = Double.parseDouble(parameters.get("pxSizeInCurrentUnit"));
            boolean invert_moving = Boolean.parseBoolean(parameters.get(INVERT_MOVING_KEY));
            boolean invert_fixed = Boolean.parseBoolean(parameters.get(INVERT_FIXED_KEY));

            parameters.keySet().forEach(k -> {
                flatParameters.add(k);
                flatParameters.add(parameters.get(k));
            });

            addToFlatParameters(flatParameters,
                // Fixed image
          "sacs_fixed", fimg,
                // Moving image
                "sacs_moving", mimg,
                // No interpolation in resampling
                "interpolate", false,
                 // Atlas image : a single timepoint
                 "tpFixed", 0,
                 // Level 2 for the atlas
                 "levelFixedSource", SourceAndConverterHelper.bestLevel(fimg[0], timePoint, voxSizeInMm),
                 // Timepoint moving source (normally 0)
                 "tpMoving", timePoint,
                 // Tries to be clever for the moving source sampling
                 "levelMovingSource", SourceAndConverterHelper.bestLevel(mimg[0], timePoint, voxSizeInMm),
                 "invert_moving", invert_moving,
                 "invert_fixed", invert_fixed
            );

            task = context
                    .getService(CommandService.class)
                    .run(registrationCommandClass, false,
                            flatParameters.toArray(new Object[0])
                    );

             CommandModule module = task.get();

             if (module.getOutputs().containsKey("success")) {
                 success = (boolean) module.getOutput("success");
             }
             if (success) {
                at3d = (AffineTransform3D) module.getOutput("at3D");
             } else {
                 if (module.getOutputs().containsKey("error")) {
                     errorMessage = (String) module.getOutput("error");
                 }
             }

             isDone = true;
             return success;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            e.printStackTrace();
            return false;
        }
    }

    String errorMessage = "";

    @Override
    public String getExceptionMessage() {
        return errorMessage;
    }

    @Override
    public void abort() {
        if (task!=null) {
            logger.info(this.getClass().getSimpleName()+": Attempt to interrupt registration...");
            task.cancel(true);
        }
    }

    public String toString() {
        return "Elastix 2D Affine";
    }

}
