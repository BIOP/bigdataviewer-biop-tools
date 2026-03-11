package ch.epfl.biop.command.display.bdv.region;

import bdv.util.BdvHandle;
import ch.epfl.biop.viewer.bdv.BdvSourcesDemo;
import ch.epfl.biop.viewer.bdv.RectangleSelectorBehaviour;
import net.imagej.ImageJ;
import net.imglib2.RealPoint;
import org.scijava.ItemIO;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

import java.util.List;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"View>BDV>ROI>BDV - Define Rectangle",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.DisplayMenu, weight = BdvPgMenus.DisplayW),
                @Menu(label = BdvPgMenus.BDVMenu, weight = BdvPgMenus.BDVW),
                @Menu(label = "ROI", weight = 0.1),
                @Menu(label = "BDV - Define Rectangle", weight = 1)
        },
        description = "Allows the user to interactively select a rectangle in a BigDataViewer window")
public class UserRectangleGetCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select BDV Window",
            description = "The BigDataViewer window where the rectangle will be selected")
    BdvHandle bdvh;

    @Parameter(label = "Message",
            description = "The instruction message displayed to the user")
    String message_for_user;

    @Parameter(label = "Timeout (ms)",
            description = "Maximum time to wait for selection in milliseconds (-1 for no timeout)",
            required = false)
    int time_out_in_ms =-1;

    @Parameter(type = ItemIO.BOTH,
            label = "Corner Point 1",
            description = "First corner point of the rectangle (can be preset or will be set by user)",
            required = false) // BOTH allow to pass an initial rectangle
    RealPoint p1;

    @Parameter(type = ItemIO.BOTH,
            label = "Corner Point 2",
            description = "Opposite corner point of the rectangle (can be preset or will be set by user)",
            required = false) // BOTH allow to pass an initial rectangle
    RealPoint p2;

    @Override
    public void run() {
        RectangleSelectorBehaviour rsb =
                new RectangleSelectorBehaviour(bdvh, message_for_user, p1, p2);
        rsb.install();
        List<RealPoint> pts = rsb.waitForSelection(time_out_in_ms);
        rsb.uninstall();
        p1 = pts.get(0);
        p2 = pts.get(1);
    }

    public static void main(String... args) throws Exception {
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        BdvHandle bdvh = BdvSourcesDemo.initAndShowSources();
        RealPoint pB = new RealPoint(3);
        pB.setPosition(new double[]{800, 500, 80});
        ij.command().run(UserRectangleGetCommand.class, true,
                "bdvh", bdvh,
                "time_out_in_ms", -1,
                "message_for_user", "Please select a rectangle and confirm your input",
                "p1", new RealPoint(3),
                "p2", pB
        );
    }
}
