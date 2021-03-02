package ch.epfl.biop.bdv.command.register;

import bdv.viewer.SourceAndConverter;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.realtransform.Wrapped2DTransformAs3D;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import org.scijava.ItemIO;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>AutoWarp Sources with Elastix and BigWarp (2D)",
        description =
                "Register two 2D sources by automatically registering small fields of view \n" +
                "surrounding the specified points of interests using elastix. The returned result \n" +
                "is a thin plate spline transform object that can be applied to other sources then \n" +
                "edited in BigWarp.")

public class AutoWarp2DCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Fixed source")
    SourceAndConverter sac_fixed;

    @Parameter(label = "Timepoint of the fixed source")
    int tpFixed;

    @Parameter(label = "Resolution level of the fixed source")
    int levelFixedSource;

    @Parameter(label = "Moving source")
    SourceAndConverter sac_moving;

    @Parameter(label = "Timepoint of the moving source")
    int tpMoving;

    @Parameter(label = "Resolution level of the moving source")
    int levelMovingSource;

    @Parameter(label = "Size in physical units of each fov used for the registration")
    double sx,sy;

    @Parameter(label = "Location in z (default 0)")
    double zLocation = 0;

    @Parameter(label = "Location of points of interest (px,py) used for registration", style = "text area")
    String ptListCoordinates = "15,10,\n -30,-40,\n ";

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform tst;

    @Parameter(label = "Pixel size in physical unit used for sampling both the fixed and moving source")
    double pxSizeInCurrentUnit;

    @Parameter(label = "Interpolate sampled sources")
    boolean interpolate;

    @Parameter(label = "Parallel registration of points of interests")
    boolean parallel;

    @Parameter(label = "Show point of interests registration (parallel cannot be true)")
    boolean showPoints;

    @Parameter
    boolean verbose = false;

    @Parameter
    CommandService cs;

    public Consumer<String> log = s -> {};

    @Override
    public void run() {

        if (verbose) log = s -> System.out.println(s);

        if (showPoints) {
            parallel=false; // Cannot parallelize with IJ1 functions
            log.accept("Cannot run parallel Autowarp if showPoints is true");
        }

        ArrayList<RealPoint> pts_Fixed = new ArrayList<>();

        String[] coordsXY = ptListCoordinates.split(",");

        for (int i = 0;i<coordsXY.length;i+=2) {
            pts_Fixed.add(new RealPoint(Double.valueOf(coordsXY[i]),Double.valueOf(coordsXY[i+1]),zLocation));
        }

        Stream<RealPoint> streamOfPts;

        if (parallel) {
            streamOfPts = pts_Fixed.parallelStream();
        } else {
            streamOfPts = pts_Fixed.stream();
        }

        ConcurrentHashMap<RealPoint, RealPoint> correspondingPts = new ConcurrentHashMap<>();

        streamOfPts.forEach(pt -> {
            try {
                AffineTransform3D at = (AffineTransform3D) cs.run(Elastix2DAffineRegisterCommand.class,true,
                        "sac_fixed", sac_fixed,
                        "tpFixed", tpFixed,
                        "levelFixedSource", levelFixedSource,
                        "sac_moving", sac_moving,
                        "tpMoving", tpMoving,
                        "levelMovingSource", levelMovingSource,
                        "px", pt.getDoublePosition(0),
                        "py", pt.getDoublePosition(1),
                        "pz", pt.getDoublePosition(2),
                        "sx",sx,
                        "sy",sy,
                        "pxSizeInCurrentUnit", pxSizeInCurrentUnit,
                        "interpolate", interpolate,
                        "showImagePlusRegistrationResult", showPoints
                ).get().getOutput("at3D");

                RealPoint ptCorr = new RealPoint(3);
                at.apply(pt, ptCorr);
                correspondingPts.put(pt,ptCorr);

                String str = "xi ="+pt.getDoublePosition(0)+"\t xf ="+ptCorr.getDoublePosition(0)+"\n";
                str+="yi ="+pt.getDoublePosition(1)+"\t yf ="+ptCorr.getDoublePosition(1);
                log.accept("Registration point : "+str);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        });

        // Returns the Thin Plate Spline transform

        double[][] ptI = new double[2][pts_Fixed.size()];
        double[][] ptF = new double[2][pts_Fixed.size()];

        for (int i = 0;i<pts_Fixed.size();i++) {
            ptF[0][i] = pts_Fixed.get(i).getDoublePosition(0);
            ptF[1][i] = pts_Fixed.get(i).getDoublePosition(1);

            ptI[0][i] = correspondingPts.get(pts_Fixed.get(i)).getDoublePosition(0);
            ptI[1][i] = correspondingPts.get(pts_Fixed.get(i)).getDoublePosition(1);
        }

        tst = new Wrapped2DTransformAs3D(
                new WrappedIterativeInvertibleRealTransform<>(
                        new ThinplateSplineTransform(ptI,ptF)
                )
        );

    }

}
