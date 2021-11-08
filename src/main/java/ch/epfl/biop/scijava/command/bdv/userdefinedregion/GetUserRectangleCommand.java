package ch.epfl.biop.scijava.command.bdv.userdefinedregion;

import bdv.util.BdvHandle;
import net.imagej.ImageJ;
import net.imglib2.RealPoint;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

import java.util.List;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDV>Get Bdv User Rectangle")
public class GetUserRectangleCommand implements Command {

    @Parameter
    BdvHandle bdvh;

    @Parameter
    String messageForUser;

    @Parameter(required = false)
    int timeOutInMs=-1;

    @Parameter(type = ItemIO.BOTH, required = false) // BOTH allow to pass an initial rectangle
    RealPoint p1;

    @Parameter(type = ItemIO.BOTH, required = false) // BOTH allow to pass an initial rectangle
    RealPoint p2;

    @Override
    public void run() {
        RectangleSelectorBehaviour rsb =
                new RectangleSelectorBehaviour(bdvh, messageForUser, p1, p2);
        rsb.install();
        List<RealPoint> pts = rsb.waitForSelection(timeOutInMs);
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
        ij.command().run(GetUserRectangleCommand.class, true,
                "bdvh", bdvh,
                "timeOutInMs", -1,
                "messageForUser", "Please select a rectangle and confirm your input",
                "p1", new RealPoint(3),
                "p2", pB
        );
    }
}
