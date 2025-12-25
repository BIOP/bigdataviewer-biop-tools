package ch.epfl.biop.scijava.command.bdv.userdefinedregion;

import bdv.util.BdvHandle;
import ch.epfl.biop.bdv.gui.graphicalhandle.GraphicalHandle;
import ch.epfl.biop.bdv.gui.graphicalhandle.XYRectangleGraphicalHandle;
import net.imagej.ImageJ;
import net.imglib2.RealPoint;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Plugin(type = Command.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"BDV>Get Bdv User Points",
        description = "Allows the user to interactively select multiple points in a BigDataViewer window")
public class GetUserPointsCommand implements Command {

    @Parameter(label = "Graphical Handle Supplier",
            description = "Optional function to create custom graphical handles for the points",
            required = false)
    Function<RealPoint, GraphicalHandle> graphical_handle_supplier;

    @Parameter(label = "Select BDV Window",
            description = "The BigDataViewer window where points will be selected")
    BdvHandle bdvh;

    @Parameter(label = "Message",
            description = "The instruction message displayed to the user")
    String message_for_user;

    @Parameter(label = "Timeout (ms)",
            description = "Maximum time to wait for selection in milliseconds (-1 for no timeout)",
            required = false)
    int time_out_in_ms =-1;

    @Parameter(type = ItemIO.OUTPUT,
            label = "Selected Points",
            description = "The list of 3D points selected by the user")
    List<RealPoint> pts = new ArrayList<>();

    @Override
    public void run() {
        PointsSelectorBehaviour psb = new PointsSelectorBehaviour(bdvh, message_for_user, graphical_handle_supplier);
        psb.install();
        pts = psb.waitForSelection(time_out_in_ms);
        psb.uninstall();
    }

    public static void main(String... args) throws Exception {

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        BdvHandle bdvh = BdvSourcesDemo.initAndShowSources();

        ij.command().run(GetUserPointsCommand.class, true,
                "bdvh", bdvh,
                "time_out_in_ms", -1,
                "message_for_user", "Select your point of interest",
                "graphical_handle_supplier",
                (Function<RealPoint, GraphicalHandle>) realPoint ->
                        new XYRectangleGraphicalHandle(
                                new Behaviours(new InputTriggerConfig()),
                                bdvh.getTriggerbindings(),
                                UUID.randomUUID().toString(),
                                bdvh.getViewerPanel().state(),
                                () -> realPoint,
                                () -> 60d,
                                () -> 35d,
                                () -> PointsSelectorBehaviour.defaultLandmarkColor ));

    }
}
