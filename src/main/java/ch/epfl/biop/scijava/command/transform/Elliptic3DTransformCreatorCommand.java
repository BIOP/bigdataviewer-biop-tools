package ch.epfl.biop.scijava.command.transform;

import bdv.util.Elliptical3DTransform;
import org.scijava.ItemIO;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>New Elliptic 3D Transform")
public class Elliptic3DTransformCreatorCommand implements BdvPlaygroundActionCommand {

    @Parameter(style = "format:0.#####E0")
    double radiusX, radiusY, radiusZ, // radii of axes 1 2 3 of ellipse
            rotationX, rotationY, rotationZ, // 3D rotation euler angles  - maybe not the best parametrization
            centerX, centerY, centerZ; // ellipse center

    @Parameter(type = ItemIO.OUTPUT)
    Elliptical3DTransform e3Dt;

    @Parameter
    CommandService cs;

    @Override
    public void run() {
        e3Dt = new Elliptical3DTransform();
        e3Dt.setParameters(
                "radiusX", radiusX,
                "radiusY", radiusY,
                "radiusZ", radiusZ,
                "rotationX", rotationX,
                "rotationY", rotationY,
                "rotationZ", rotationZ,
                "centerX", centerX,
                "centerY", centerY,
                "centerZ", centerZ );

        cs.run(DisplayEllipseFromTransformCommand.class, false, "rMin", 0.9, "rMax", 1.1, "e3Dt", e3Dt);
    }
}
