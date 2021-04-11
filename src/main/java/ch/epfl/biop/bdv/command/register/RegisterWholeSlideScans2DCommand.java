package ch.epfl.biop.bdv.command.register;

import bdv.util.BigWarpHelper;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.bioformats.command.BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand;
import net.imagej.ImageJ;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.*;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import org.scijava.ItemIO;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.command.bdv.BdvSourcesShowCommand;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Align Slides (2D)")
public class RegisterWholeSlideScans2DCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Global reference image (fixed, usually, first dapi channel)")
    SourceAndConverter globalRefSource;

    @Parameter(label = "Index of current reference image (moving, dapi channel of scan i)")
    SourceAndConverter currentRefSource;

    @Parameter(label = "Background offset value for moving image")
    double background_offset_value_moving = 0;

    @Parameter(label = "Background offset value for fixed image")
    double background_offset_value_fixed = 0;

    @Parameter(label = "Locations of interest for warping registration", style = "text area")
    String ptListCoordinates = "15,10,\n -30,-40,\n ";

    @Parameter(label = "Make a first affine registration on defined regions")
    boolean performFirstCoarseAffineRegistration;

    @Parameter(label = "Make a second spline registration with landmarks")
    boolean performSecondSplineRegistration;

    @Parameter
    double topLeftX;

    @Parameter
    double topLeftY;

    @Parameter
    double bottomRightX;

    @Parameter
    double bottomRightY;

    @Parameter(label = "Number of iterations for each scale (default 100)")
    int maxIterationNumberPerScale = 100;

    @Parameter
    CommandService cs;

    Consumer<String> log = s -> System.out.println(s);

    @Parameter
    boolean showDetails;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform tst;

    @Parameter
    boolean verbose;

    @Override
    public void run() {

        // Approximate rigid registration
        try {
            AffineTransform3D at1 = new AffineTransform3D();
            SourceAndConverter firstRegSrc = currentRefSource;

            if (performFirstCoarseAffineRegistration) {
                log.accept("----------- First registration - Coarse Affine");

                CommandModule cm = cs.run(Elastix2DAffineRegisterCommand.class, true,
                        "sac_fixed", globalRefSource,
                        "tpFixed", 0,
                        "levelFixedSource", SourceAndConverterHelper.bestLevel(globalRefSource, 0, 0.01),
                        "sac_moving", currentRefSource,
                        "tpMoving", 0,
                        "levelMovingSource", SourceAndConverterHelper.bestLevel(currentRefSource, 0, 0.01),
                        "px", topLeftX,
                        "py", topLeftY,
                        "pz", 0,
                        "sx", bottomRightX - topLeftX,
                        "sy", bottomRightY - topLeftY,
                        "pxSizeInCurrentUnit", 0.01, // in mm
                        "interpolate", true,
                        "showImagePlusRegistrationResult", showDetails,
                        "automaticTransformInitialization", false,
                        "maxIterationNumberPerScale", maxIterationNumberPerScale,
                        "minPixSize", 32,
                        "background_offset_value_moving", background_offset_value_moving,
                        "background_offset_value_fixed", background_offset_value_fixed,
                        "verbose", verbose
                ).get();
                at1 = (AffineTransform3D) cm.getOutput("at3D");
                //firstRegSrc = (SourceAndConverter) cm.getOutput("registeredSource");
                firstRegSrc = new SourceAffineTransformer(at1).apply(currentRefSource);
            }

            log.accept("----------- Precise Warping based on particular locations");
            RealTransform tst_temp = new AffineTransform3D(); // Identity transform applied if no warping
            if (performSecondSplineRegistration) {
                tst_temp =
                        (RealTransform) cs.run(Elastix2DSparsePointsRegisterCommand.class, true,
                                "sac_fixed", globalRefSource,
                                "sac_moving", firstRegSrc,
                                "tpFixed", 0,
                                "levelFixedSource", SourceAndConverterHelper.bestLevel(globalRefSource, 0, 0.001),
                                "tpMoving", 0,
                                "levelMovingSource", SourceAndConverterHelper.bestLevel(firstRegSrc, 0, 0.001),
                                "ptListCoordinates", ptListCoordinates,
                                "zLocation", 0,
                                "sx", 0.5, // 500 microns
                                "sy", 0.5, // 500 microns
                                "pxSizeInCurrentUnit", 0.001, //1 micron per pixel
                                "interpolate", true,
                                "showPoints", showDetails,//true,
                                "parallel", !showDetails,//false,
                                "verbose", verbose,
                                "maxIterationNumberPerScale", maxIterationNumberPerScale,
                                "minPixSize", 32,
                                "background_offset_value_moving", background_offset_value_moving,
                                "background_offset_value_fixed", background_offset_value_fixed,
                                "verbose", verbose
                        ).get().getOutput("tst");
            } else {
                // Let's put landmarks on each corner in case the user wants to edit the registration later
                ptListCoordinates  = topLeftX+","+topLeftY+",";
                ptListCoordinates += bottomRightX+","+topLeftY+",";
                ptListCoordinates += bottomRightX+","+bottomRightY+",";
                ptListCoordinates += topLeftX+","+bottomRightY;
            }

            log.accept("----------- Computing global transformation");

            RealTransformSequence rts = new RealTransformSequence();
            AffineTransform3D at2 = new AffineTransform3D();
            rts.add(at1.concatenate(at2).inverse());
            rts.add(tst_temp);

            ArrayList<RealPoint> pts_Fixed = new ArrayList<>();
            ArrayList<RealPoint> pts_Moving = new ArrayList<>();

            String[] coordsXY = ptListCoordinates.split(",");

            for (int i = 0;i<coordsXY.length;i+=2) {
                RealPoint pt_fixed = new RealPoint(Double.valueOf(coordsXY[i]),Double.valueOf(coordsXY[i+1]),0);
                pts_Fixed.add(pt_fixed);
                RealPoint pt_moving = new RealPoint(3);
                rts.apply(pt_fixed, pt_moving);
                pts_Moving.add(pt_moving);
            }

            tst = new Wrapped2DTransformAs3D(
                    new WrappedIterativeInvertibleRealTransform<>(
                            BigWarpHelper.getTransform(pts_Moving, pts_Fixed,true)
                    )
            );

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

    }

    public static void main(String... args) throws Exception {
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        CommandService cs = ij.command();

        cs.run(BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand.class,true,
                "unit","MILLIMETER",
                "splitRGBChannels",false).get();

        cs.run(BdvSourcesShowCommand.class,true,
                "autoContrast",true,
                "adjustViewOnSource",true,
                "is2D",true,
                "windowTitle","Test Registration",
                "interpolate",false,
                "nTimepoints",1,
                "projector","Sum Projector",
                "sacs","SpimData 0>Channel>1").get();
    }
}
