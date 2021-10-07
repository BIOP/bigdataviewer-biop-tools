import bdv.util.Elliptical3DTransform;
import ch.epfl.biop.bdv.command.transform.ComputeEllipse3DTransformedDistanceCommand;
import ij.IJ;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import org.scijava.Context;
import org.scijava.command.CommandService;

public class DemoComputeEllipticalDistanceCommand
{

    static {
        LegacyInjector.preinit();
    }

    public static void main(String... args) {

        ImageJ ij = new ImageJ();
        ij.ui().showUI();

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



        Context ctx = (Context ) IJ.runPlugIn("org.scijava.Context", "");
        CommandService commandService = ctx.service( CommandService.class );
        commandService.run( ComputeEllipse3DTransformedDistanceCommand.class, true );
    }

}
