package ch.epfl.biop.scijava.command.bdv.userdefinedregion;

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

@Plugin(type = Command.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"BDV>Get Bdv User Box",
        description = "Allows the user to interactively select a 3D bounding box in a BigDataViewer window")
public class BoxSelectorCommand implements Command {

    @Parameter(label = "Select BDV Window",
            description = "The BigDataViewer window where the box will be selected")
    BdvHandle bdvh;

    @Parameter(label = "Message",
            description = "The instruction message displayed to the user")
    String message;

    @Parameter(label = "Range Min X",
            description = "Minimum allowed X coordinate for the box",
            style = "format:0.#####E0")
    double xmin;

    @Parameter(label = "Range Max X",
            description = "Maximum allowed X coordinate for the box",
            style = "format:0.#####E0")
    double xmax;

    @Parameter(label = "Range Min Y",
            description = "Minimum allowed Y coordinate for the box",
            style = "format:0.#####E0")
    double ymin;

    @Parameter(label = "Range Max Y",
            description = "Maximum allowed Y coordinate for the box",
            style = "format:0.#####E0")
    double ymax;

    @Parameter(label = "Range Min Z",
            description = "Minimum allowed Z coordinate for the box",
            style = "format:0.#####E0")
    double zmin;

    @Parameter(label = "Range Max Z",
            description = "Maximum allowed Z coordinate for the box",
            style = "format:0.#####E0")
    double zmax;

    @Parameter(label = "Initial Min X",
            description = "Initial X minimum for the box",
            style = "format:0.#####E0")
    double xmin_ini = 1.2;

    @Parameter(label = "Initial Max X",
            description = "Initial X maximum for the box",
            style = "format:0.#####E0")
    double xmax_ini = 0.8;

    @Parameter(label = "Initial Min Y",
            description = "Initial Y minimum for the box",
            style = "format:0.#####E0")
    double ymin_ini = -2.0*Math.PI;

    @Parameter(label = "Initial Max Y",
            description = "Initial Y maximum for the box",
            style = "format:0.#####E0")
    double ymax_ini = 2.0*Math.PI;

    @Parameter(label = "Initial Min Z",
            description = "Initial Z minimum for the box",
            style = "format:0.#####E0")
    double zmin_ini = -4.0*Math.PI;

    @Parameter(label = "Initial Max Z",
            description = "Initial Z maximum for the box",
            style = "format:0.#####E0")
    double zmax_ini = 4.0*Math.PI;

    @Parameter(type = ItemIO.OUTPUT,
            label = "Selected Interval",
            description = "The 3D bounding box interval selected by the user")
    RealInterval interval;

    @Parameter(type = ItemIO.OUTPUT,
            label = "Selection Valid",
            description = "True if the user confirmed the selection, false if cancelled")
    Boolean result;

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

        this.result = result.isValid();

        if (this.result)
        {
            interval = result.getInterval();
        }
    }
}
