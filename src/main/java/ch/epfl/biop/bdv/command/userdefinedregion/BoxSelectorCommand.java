package ch.epfl.biop.bdv.command.userdefinedregion;

import bdv.tools.boundingbox.BoxSelectionOptions;
import bdv.tools.boundingbox.TransformedRealBoxSelectionDialog;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDV>Get Bdv User Box")
public class BoxSelectorCommand implements Command {

    @Parameter
    BdvHandle bdvh;

    @Parameter
    String message;

    @Parameter(style = "format:0.#####E0")
    double xmin, xmax, ymin, ymax, zmin, zmax;

    @Parameter(style = "format:0.#####E0")
    double xmin_ini = 1.2, xmax_ini = 0.8,
           ymin_ini = -2.0*Math.PI, ymax_ini = +2.0*Math.PI,
           zmin_ini = -4.0*Math.PI, zmax_ini = +4.0*Math.PI;

    @Parameter(type = ItemIO.OUTPUT)
    RealInterval interval;

    @Parameter(type = ItemIO.OUTPUT)
    Boolean validResult;

    public void run() {

        final RealInterval initialInterval = Intervals.createMinMaxReal( xmin_ini, ymin_ini, zmin_ini, xmax_ini, ymax_ini, zmax_ini );
        final RealInterval rangeInterval = Intervals.createMinMaxReal( xmin, ymin, zmin, xmax, ymax, zmax );

        final TransformedRealBoxSelectionDialog.Result result = BdvFunctions.selectRealBox(
                bdvh,
                new AffineTransform3D(),
                initialInterval,
                rangeInterval,
                BoxSelectionOptions.options()
                        .title( message ) );

        validResult = result.isValid();

        if ( validResult )
        {
            interval = result.getInterval();
        }
    }
}
