package ch.epfl.biop.bdv.commands;

import bdv.img.WarpedSource;
import ch.epfl.biop.bdv.scijava.util.BDVSourceFunctionalInterfaceCommand;
import ch.epfl.biop.bdv.transform.Elliptical3DTransform;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.module.DefaultMutableModuleItem;
import org.scijava.module.ModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, menuPath = ScijavaBdvRootMenu+"Transformation>Elliptical Transform")
public class EllipticalTransformCommand extends BDVSourceFunctionalInterfaceCommand {

    @Parameter
    double r1, r2, r3, //radius of axes 1 2 3 of ellipse
            theta, phi, angle_en, // 3D rotation euler angles maybe not the best parametrization
            tx, ty, tz; // ellipse center

    @Parameter(type = ItemIO.OUTPUT)
    Elliptical3DTransform e3Dt;

    // -- Initializable methods --

    public EllipticalTransformCommand() {

        this.f = src -> {
            System.out.println("0");
            WarpedSource ws = new WarpedSource(src,src.getName()+"_EllipticalTransform");

            System.out.println("1");
            e3Dt.updateNotifiers.add(() -> {
                ws.updateTransform(e3Dt);
                this.bdv_h_out.getViewerPanel().requestRepaint();
            }); // TODO avoid memory leak somehow...

            System.out.println("2");
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

            System.out.println("3");
            ws.setIsTransformed(true);
            System.out.println("4");
            return ws;
        };
    }


    private List<ModuleItem<Double>> transformParamsItems = new ArrayList<>();

    /*public void init() {
        Elliptical3DTransform.getParamsName().forEach(p -> {
            final ModuleItem<Double> item = new DefaultMutableModuleItem<>(getInfo(),
                    p, double.class);
            item.setLabel(p);
            transformParamsItems.add(item);
            getInfo().addInput(item);
        });
    }*/

    public void initCommand() {
        e3Dt = new Elliptical3DTransform();

    }
}
