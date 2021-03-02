package ch.epfl.biop.bdv.command.userdefinedregion;

import bdv.util.BdvHandle;
import net.imagej.ImageJ;
import net.imglib2.RealPoint;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

import java.util.ArrayList;
import java.util.List;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDV>Get Bdv User Rectangle")
public class GetUserRectangleCommand implements Command {

    @Parameter
    BdvHandle bdvh;

    @Parameter
    String messageForUser;

    @Parameter(required = false)
    int timeOutInMs=-1;

    @Parameter(type = ItemIO.OUTPUT)
    List<RealPoint> pts = new ArrayList<>();

    @Override
    public void run() {
        RectangleSelectorBehaviour rsb = new RectangleSelectorBehaviour(bdvh, messageForUser);
        rsb.install();
        pts = rsb.waitForSelection(timeOutInMs);
        rsb.uninstall();
    }


    public static void main(String... args) throws Exception {

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        BdvHandle bdvh = BdvSourcesDemo.initAndShowSources();

        ij.command().run(GetUserRectangleCommand.class, true, "bdvh", bdvh, "timeOutInMs", -1,"messageForUser", "Please select a rectangle");

    }
}
