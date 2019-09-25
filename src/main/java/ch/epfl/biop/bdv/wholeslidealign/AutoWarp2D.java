package ch.epfl.biop.bdv.wholeslidealign;

import bdv.util.BdvHandle;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, menuPath = ScijavaBdvRootMenu+"Registration>AutoWarp Sources with Elastix and BigWarp")
public class AutoWarp2D implements Command {

    @Parameter
    BdvHandle bdv_h;

    @Parameter
    int idxFixedSource;

    @Parameter
    int tpFixed;

    @Parameter
    int levelFixedSource;

    @Parameter
    int idxMovingSource;

    @Parameter
    int tpMoving;

    @Parameter
    int levelMovingSource;

    @Parameter
    CommandService cs;

    @Parameter
    double sx,sy;

    @Parameter
    double zLocation;

    @Parameter(label = "Areas of interest (px,py)", style = "text area")
    String ptListCoordinates = "15,10,\n -30,-40,\n ";

    @Parameter
    double pxSizeInCurrentUnit;

    @Parameter
    boolean interpolate;

    @Parameter
    boolean parallel;

    @Override
    public void run() {

        ArrayList<RealPoint> pts_Fixed = new ArrayList<>();

        ArrayList<RealPoint> pts_Moving = new ArrayList<>();

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


        streamOfPts.forEach(pt -> {
            try {
                AffineTransform3D at = (AffineTransform3D) cs.run(RegisterBdvSources2D.class,true,
                        "bdv_h", bdv_h,
                        "idxFixedSource", idxFixedSource,
                        "tpFixed", tpFixed,
                        "levelFixedSource", levelFixedSource,
                        "idxMovingSource", idxMovingSource,
                        "tpMoving", tpMoving,
                        "levelMovingSource", levelMovingSource,
                        "px", pt.getDoublePosition(0),
                        "py", pt.getDoublePosition(1),
                        "pz", pt.getDoublePosition(2),
                        "sx",sx,
                        "sy",sy,
                        "pxSizeInCurrentUnit", pxSizeInCurrentUnit,
                        "interpolate", interpolate,
                        "outputRegisteredSource", false,
                        "showImagePlusRegistrationResult", true
                ).get().getOutput("affineTransformOut");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        });



    }

}
