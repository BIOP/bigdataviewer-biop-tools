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
    double radius_x, radius_y, radius_z, // radii of axes 1 2 3 of ellipse
            rotation_x, rotation_y, rotation_z, // 3D rotation euler angles  - maybe not the best parametrization
            center_x, center_y, center_z; // ellipse center

    @Parameter(type = ItemIO.OUTPUT)
    Elliptical3DTransform e3dt;

    @Parameter
    CommandService cs;

    @Override
    public void run() {
        e3dt = new Elliptical3DTransform();
        e3dt.setParameters(
                Elliptical3DTransform.RADIUS_X, radius_x,
                Elliptical3DTransform.RADIUS_Y, radius_y,
                Elliptical3DTransform.RADIUS_Z, radius_z,
                Elliptical3DTransform.ROTATION_X, rotation_x,
                Elliptical3DTransform.ROTATION_Y, rotation_y,
                Elliptical3DTransform.ROTATION_Z, rotation_z,
                Elliptical3DTransform.CENTER_X, center_x,
                Elliptical3DTransform.CENTER_Y, center_y,
                Elliptical3DTransform.CENTER_Z, center_z);

        cs.run(DisplayEllipseFromTransformCommand.class, false, "r_min", 0.9, "r_max", 1.1, "e3dt", e3dt);
    }
}
