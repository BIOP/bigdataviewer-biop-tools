package ch.epfl.biop.scijava.command;

import bdv.util.Elliptical3DTransform;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = "BigDataViewer>Sources>Transform>New Elliptic 3D Transform")
public class Elliptic3DTransformCreatorCommand implements Command {

    @Parameter
    double r1, r2, r3, //radius of axes 1 2 3 of ellipse
            rx, ry, rz, // 3D rotation euler angles  - maybe not the best parametrization
            tx, ty, tz; // ellipse center

    @Parameter(type = ItemIO.OUTPUT)
    Elliptical3DTransform e3Dt;

    @Override
    public void run() {
        e3Dt = new Elliptical3DTransform();
        e3Dt.setParameters(
                "r1", r1,
                "r2", r2,
                "r3", r3,
                "rx", rx,
                "ry", ry,
                "rz", rz,
                "tx", tx,
                "ty", ty,
                "tz", tz);
    }
}
