package ch.epfl.biop.scijava.command;

import bdv.viewer.SourceAndConverter;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.ThinplateSplineTransform;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Stream;


@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>AutoWarp Sources with Elastix and BigWarp (2D)")
public class AutoWarp2DCommand implements Command {

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
    double sx,sy;

    @Parameter
    double zLocation;

    @Parameter(label = "Location of interest (px,py)", style = "text area")
    String ptListCoordinates = "15,10,\n -30,-40,\n ";

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform tst;

    @Parameter
    double pxSizeInCurrentUnit;

    @Parameter
    boolean interpolate;

    @Parameter
    boolean parallel;

    @Parameter
    boolean showPoints;

    @Parameter
    CommandService cs;

    public Consumer<String> log = s -> System.out.println(s);

    @Override
    public void run() {

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
                //System.out.println(str);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        });

        double[][] ptI = new double[3][pts_Fixed.size()];
        double[][] ptF = new double[3][pts_Fixed.size()];

        for (int i = 0;i<pts_Fixed.size();i++) {
            ptF[0][i] = pts_Fixed.get(i).getDoublePosition(0);
            ptF[1][i] = pts_Fixed.get(i).getDoublePosition(1);
            ptF[2][i] = pts_Fixed.get(i).getDoublePosition(2);

            ptI[0][i] = correspondingPts.get(pts_Fixed.get(i)).getDoublePosition(0);
            ptI[1][i] = correspondingPts.get(pts_Fixed.get(i)).getDoublePosition(1);
            ptI[2][i] = correspondingPts.get(pts_Fixed.get(i)).getDoublePosition(2);
        }

        tst = new ThinplateSplineTransform(ptI,ptF);

    }

}
