package ch.epfl.biop.scijava.command;

import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.ThinplateSplineTransform;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.module.ModuleService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;


@Plugin(type = Command.class, menuPath = "BigDataViewer>Sources>Register>Align Slides (2D)")
public class RegisterWholeSlideScans2DCommand implements Command {

    @Parameter(label = "Global reference image (fixed, usually, first dapi channel)")
    SourceAndConverter globalRefSource;

    @Parameter(label = "Index of current reference image (moving, dapi channel of scan i)")
    SourceAndConverter currentRefSource;

    @Parameter(label = "Locations of interest for warping registration", style = "text area")
    String ptListCoordinates = "15,10,\n -30,-40,\n ";

    @Parameter
    double topLeftX;

    @Parameter
    double topLeftY;

    @Parameter
    double bottomRightX;

    @Parameter
    double bottomRightY;

    @Parameter
    CommandService cs;

    Consumer<String> log = s -> System.out.println(s);

    @Parameter
    boolean showDetails;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform rts;

    @Override
    public void run() {

        // Approximate rigid registration
        try {

            log.accept("----------- First registration - Coarse Affine");

            CommandModule cm = cs.run(Elastix2DAffineRegisterCommand.class, true,
                    "sac_fixed", globalRefSource,
                    "tpFixed", 0,
                    "levelFixedSource", 5,
                    "sac_moving", currentRefSource,
                    "tpMoving", 0,
                    "levelMovingSource", 5,
                    "px", topLeftX,
                    "py", topLeftY,
                    "pz", 0,
                    "sx", bottomRightX-topLeftX,
                    "sy", bottomRightY-topLeftY,
                    "pxSizeInCurrentUnit", 0.01, // in mm
                    "interpolate", false,
                    "showImagePlusRegistrationResult", showDetails
            ).get();
            AffineTransform3D at1 = (AffineTransform3D) cm.getOutput("at3D");
            SourceAndConverter firstRegSrc = (SourceAndConverter) cm.getOutput("registeredSource");

            log.accept("----------- Second registration - Precise Affine");

            // More precise rigid registration
            cm = cs.run(Elastix2DAffineRegisterCommand.class, true,
                    "sac_fixed", globalRefSource,
                    "tpFixed", 0,
                    "levelFixedSource", 4,
                    "sac_moving", firstRegSrc,
                    "tpMoving", 0,
                    "levelMovingSource", 4,
                    "px", topLeftX,
                    "py", topLeftY,
                    "pz", 0,
                    "sx", bottomRightX-topLeftX,
                    "sy", bottomRightY-topLeftY,
                    "pxSizeInCurrentUnit", 0.0025, // 2.5 micron per pixel
                    "interpolate", false,
                    "showImagePlusRegistrationResult", showDetails
            ).get();

            AffineTransform3D at2 = (AffineTransform3D) cm.getOutput("at3D");
            SourceAndConverter secondRegSrc = (SourceAndConverter) cm.getOutput("registeredSource");

            log.accept("----------- Precise Warping based on particular locations");
            ThinplateSplineTransform tst =
                    (ThinplateSplineTransform) cs.run(AutoWarp2DCommand.class, true,
                            "sac_fixed", globalRefSource,
                            "sac_moving", secondRegSrc,
                            "tpFixed", 0,
                            "levelFixedSource", 2,
                            "tpMoving", 0,
                            "levelMovingSource", 2,
                            "ptListCoordinates", ptListCoordinates,
                            "zLocation", 0,
                            "sx", 0.5, // 500 microns
                            "sy", 0.5, // 500 microns
                            "pxSizeInCurrentUnit", 0.001, //1 micron per pixel
                            "interpolate", false,
                            "showPoints", showDetails,//true,
                            "parallel", !showDetails//false,
                    ).get().getOutput("tst");

            log.accept("----------- Computing global transformation");

            rts = new RealTransformSequence();
            ((RealTransformSequence)rts).add(at1.concatenate(at2).inverse());
            ((RealTransformSequence)rts).add(tst);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

    }
}
