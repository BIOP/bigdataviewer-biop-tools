package ch.epfl.biop.scijava.command.bdv.userdefinedregion;

import bdv.util.BdvHandle;
import net.imagej.ImageJ;
import net.imglib2.RealPoint;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

@Plugin(type = Command.class, menuPath = "BigDataViewer>BDV>Get Bdv User Region")
public class GetUserRectangleCommand implements Command {

    @Parameter
    BdvHandle bdvh;

    @Parameter(required = false)
    int timeOut=-1;

    @Parameter(type = ItemIO.OUTPUT)
    List<RealPoint> pts = new ArrayList<>();

    @Override
    public void run() {
        RectangleSelectorBehaviour rsb = new RectangleSelectorBehaviour(bdvh);
        rsb.install();
        pts = rsb.waitForSelection(10000);
        rsb.uninstall();
    }


    public static void main(String... args) throws Exception {

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        BdvHandle bdvh = BdvRectangleSelectorDemo.initAndShowSources();

        ij.command().run(GetUserRectangleCommand.class, true, "bdvh", bdvh, "timeOut", -1);

    }
}
