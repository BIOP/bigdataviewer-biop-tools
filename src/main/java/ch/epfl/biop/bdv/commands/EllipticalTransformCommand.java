package ch.epfl.biop.bdv.commands;

import bdv.img.WarpedSource;
import ch.epfl.biop.bdv.transform.Elliptical3DTransform;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, initializer = "init", menuPath = "Plugins>BIOP>BDV>Elliptical Transform")
public class EllipticalTransformCommand extends BDVSourceFunctionalInterfaceCommand {

    @Parameter
    double r1, r2, r3, //radius of axes 1 2 3 of ellipse
            theta, phi, angle_en, // 3D rotation euler angles maybe not the best parametrization
            tx, ty, tz; // ellipse center

    @Parameter(type = ItemIO.OUTPUT)
    Elliptical3DTransform e3Dt;

    public EllipticalTransformCommand() {
        this.f = src -> {
            WarpedSource ws = new WarpedSource(src,src.getName()+"_EllipticalTransform");
            e3Dt.updateNotifiers.add(() -> {
                ws.updateTransform(e3Dt);
                this.bdv_h.getViewerPanel().requestRepaint();
            }); // TODO avoid memory leak somehow...
            e3Dt.setParameters(
                    "r1", r1,
                    "r2", r2,
                    "r3", r3,
                    "theta", theta,
                    "phi", phi,
                    "angle_en", angle_en,
                    "tx", tx,
                    "ty", ty,
                    "tz", tz);

            ws.setIsTransformed(true);
            return ws;
        };
    }

    public void initCommand() {
        e3Dt = new Elliptical3DTransform();

    }
}
