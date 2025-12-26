package ch.epfl.biop.scijava.command.transform;

import bdv.util.Elliptical3DTransform;
import org.scijava.ItemIO;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>New Elliptic 3D Transform",
        description = "Creates a new elliptical 3D transform with specified radii, rotation, and center")
public class Elliptic3DTransformCreatorCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Radius X",
            style = "format:0.#####E0",
            description = "Radius along the first ellipse axis")
    double radius_x;

    @Parameter(label = "Radius Y",
            style = "format:0.#####E0",
            description = "Radius along the second ellipse axis")
    double radius_y;

    @Parameter(label = "Radius Z",
            style = "format:0.#####E0",
            description = "Radius along the third ellipse axis")
    double radius_z;

    @Parameter(label = "Rotation X",
            style = "format:0.#####E0",
            description = "Euler rotation angle around X axis (radians)")
    double rotation_x;

    @Parameter(label = "Rotation Y",
            style = "format:0.#####E0",
            description = "Euler rotation angle around Y axis (radians)")
    double rotation_y;

    @Parameter(label = "Rotation Z",
            style = "format:0.#####E0",
            description = "Euler rotation angle around Z axis (radians)")
    double rotation_z;

    @Parameter(label = "Center X",
            style = "format:0.#####E0",
            description = "X coordinate of ellipse center")
    double center_x;

    @Parameter(label = "Center Y",
            style = "format:0.#####E0",
            description = "Y coordinate of ellipse center")
    double center_y;

    @Parameter(label = "Center Z",
            style = "format:0.#####E0",
            description = "Z coordinate of ellipse center")
    double center_z;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The created elliptical 3D transform")
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
