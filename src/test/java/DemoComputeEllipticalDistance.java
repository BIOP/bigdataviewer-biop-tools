import bdv.util.Elliptical3DTransform;
import ch.epfl.biop.bdv.command.transform.ComputeEllipse3DTransformedDistanceCommand;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;

public class DemoComputeEllipticalDistance
{

    static {
        LegacyInjector.preinit();
    }

    public static void main(String... args) {

        // Initializes static SourceService and Display Service
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        final ComputeEllipse3DTransformedDistanceCommand command = new ComputeEllipse3DTransformedDistanceCommand();
        Elliptical3DTransform e3Dt = new Elliptical3DTransform();
        e3Dt.setParameters(
                "r1", 338.0,
                "r2", 292.0,
                "r3", 320.0,
                "rx", 2.0,
                "ry", 0.81,
                "rz", 2.81,
                "tx", 209.0,
                "ty", 230.0,
                "tz", -230.0);
       command.e3Dt = e3Dt;
       command.pA0 = 1.1;
       command.pA1 = 1.0;
       command.pA2 = 2.4;
       command.pB0 = 1.1;
       command.pB1 = 2.2;
       command.pB2 = 3.8;

       command.numSteps = 1;
       command.run();
       System.out.println("Distance, numSteps 1: " + command.distance );

       command.numSteps = 100;
       command.run();
       System.out.println("Distance, numSteps 100: " + command.distance );
    }

}
