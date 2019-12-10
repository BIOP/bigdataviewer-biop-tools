package ch.epfl.biop.bdv.register.dim2;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import ch.epfl.biop.bdv.scijava.command.edit.transform.BdvSourcesAffineTransform;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.scijava.command.edit.transform.BdvSourcesWarp;
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

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;
import static ch.epfl.biop.bdv.scijava.command.BdvSourceAndConverterFunctionalInterfaceCommand.ADD;
import static ch.epfl.biop.bdv.scijava.command.BdvSourceAndConverterFunctionalInterfaceCommand.REPLACE;

@Plugin(type = Command.class, menuPath = ScijavaBdvRootMenu+"Bdv>Edit Sources>Register>2D>Auto Align Sources with Elastix")
public class RegisterWholeSlideScans2D implements Command {

    @Parameter
    BdvHandle bdv_h;

    @Parameter
    BdvHandle bdv_h_accumulating_scans_volatile;

    @Parameter(label = "Index of global reference image (usually, first dapi channel)")
    int globalRefSourceIndex;

    @Parameter(label = "Index of current reference image (dapi channel of scan i)")
    int currentRefSourceIndex;

    @Parameter(label = "Indexes of channels of scan i, except the reference one")
    String otherChannelIndexes;

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

    @Parameter
    ModuleService ms;

    Consumer<String> log = s -> System.out.println(s);

    @Parameter
    boolean showDetails;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransformSequence rts;

    @Override
    public void run() {

        // Approximate rigid registration
        try {

            BdvHandle bdv_regSteps = BdvFunctions.show(
                    bdv_h.getViewerPanel().getState().getSources().get(globalRefSourceIndex).getSpimSource()
            ).getBdvHandle();

            BdvFunctions.show(
                    bdv_h.getViewerPanel().getState().getSources().get(currentRefSourceIndex).getSpimSource(),
                    BdvOptions.options().addTo(bdv_regSteps)
            );

            log.accept("----------- First registration");

            CommandModule cm = cs.run(RegisterBdvSources2D.class, true,
                    "bdv_h_out", bdv_regSteps,
                    "bdv_h_fixed", bdv_regSteps,
                    "idxFixedSource", 0,
                    "tpFixed", 0,
                    "levelFixedSource", 5,
                    "bdv_h_moving", bdv_regSteps,
                    "idxMovingSource", 1,
                    "tpMoving", 0,
                    "levelMovingSource", 5,
                    "px", topLeftX,
                    "py", topLeftY,
                    "pz", 0,
                    "sx", bottomRightX-topLeftX,
                    "sy", bottomRightY-topLeftY,
                    "pxSizeInCurrentUnit", 0.01, // in mm
                    "interpolate", false,
                    "displayRegisteredSource", false,
                    "showImagePlusRegistrationResult", showDetails
            ).get();//.getOutput("");
            AffineTransform3D at1 = (AffineTransform3D) cm.getOutput("affineTransformOut");
            SourceAndConverter firstRegSrc = (SourceAndConverter) cm.getOutput("registeredSource");

            BdvFunctions.show(firstRegSrc.getSpimSource(), BdvOptions.options().addTo(bdv_regSteps));

            log.accept("----------- Second registration");

            // More precise rigid registration
            cm = cs.run(RegisterBdvSources2D.class, true,
                    "bdv_h_fixed", bdv_regSteps,
                    "idxFixedSource", 0,
                    "tpFixed", 0,
                    "levelFixedSource", 4,
                    "bdv_h_moving", bdv_regSteps,
                    "bdv_h_out", bdv_regSteps,
                    "idxMovingSource", 2,
                    "tpMoving", 0,
                    "levelMovingSource", 4,
                    "px", topLeftX,
                    "py", topLeftY,
                    "pz", 0,
                    "sx", bottomRightX-topLeftX,
                    "sy", bottomRightY-topLeftY,
                    "pxSizeInCurrentUnit", 0.0025, // 2.5 micron per pixel
                    "interpolate", false,
                    "displayRegisteredSource", false,
                    "showImagePlusRegistrationResult", showDetails
            ).get();

            AffineTransform3D at2 = (AffineTransform3D) cm.getOutput("affineTransformOut");
            SourceAndConverter secondRegSrc = (SourceAndConverter) cm.getOutput("registeredSource");

            BdvFunctions.show(secondRegSrc.getSpimSource(), BdvOptions.options().addTo(bdv_regSteps)).getBdvHandle();

            log.accept("----------- Precise Warping based on particular locations");
            ThinplateSplineTransform tst =
                    (ThinplateSplineTransform) cs.run(AutoWarp2D.class, true,
                            "bdv_h_fixed", bdv_regSteps,
                            "bdv_h_moving", bdv_regSteps,
                            "idxFixedSource", 0,
                            "tpFixed", 0,
                            "levelFixedSource", 2,
                            "idxMovingSource", 3,// because that's where it was appended
                            "tpMoving", 0,
                            "levelMovingSource", 2,
                            "ptListCoordinates", ptListCoordinates,
                            "zLocation", 0,
                            "sx", 0.5, // 500 microns
                            "sy", 0.5, // 500 microns
                            "pxSizeInCurrentUnit", 0.001, //1 micron per pixel
                            "interpolate", false,
                            "showPoints", showDetails,//true,
                            "parallel", !showDetails,//false,
                            "bdv_h_out", bdv_regSteps,
                            "appendWarpedSource", true
                    ).get().getOutput("tst");

            int nSourcesBefore = bdv_regSteps.getViewerPanel().getState().getSources().size();


            ms.run(cs.getCommand(BdvSourcesAffineTransform.class),true,
                    "bdv_h_in",bdv_h,
                    "bdv_h_out", bdv_regSteps,
                    "output_mode", ADD,
                    "sourceIndexString",(currentRefSourceIndex+","+otherChannelIndexes),
                    "stringMatrix", at1.concatenate(at2).toString()
                    ).get();

            int nSourcesAfter = bdv_regSteps.getViewerPanel().getState().getSources().size();

            String sourcesToTransform = "";
            for (int i = nSourcesBefore;i<nSourcesAfter;i++) {
                sourcesToTransform+=(i)+",";
            }
            sourcesToTransform = sourcesToTransform.substring(0, sourcesToTransform.length()-1);

            System.out.println("sourcesToTransform="+sourcesToTransform);

            ms.run(cs.getCommand(BdvSourcesWarp.class),true,
                    "bdv_h_in",bdv_regSteps,
                    "bdv_h_out", this.bdv_h_accumulating_scans_volatile,
                    "output_mode", REPLACE,
                    "sourceIndexString",sourcesToTransform,
                    "rt", tst
            ).get();

            rts = new RealTransformSequence();
            rts.add(at1.concatenate(at2));
            rts.add(tst);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

    }
}