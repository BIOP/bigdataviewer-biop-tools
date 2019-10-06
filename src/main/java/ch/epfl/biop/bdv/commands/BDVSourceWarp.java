package ch.epfl.biop.bdv.commands;

import bdv.img.WarpedSource;
import ch.epfl.biop.bdv.scijava.command.BDVSourceFunctionalInterfaceCommand;
import net.imglib2.realtransform.RealTransform;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, initializer = "init", menuPath = ScijavaBdvRootMenu+"Transformation>Transform Source (warp)")
public class BDVSourceWarp extends BDVSourceFunctionalInterfaceCommand {
    @Parameter(label = "RealTransform object")
    RealTransform rt;

    public BDVSourceWarp() {
        this.f = src -> {
                WarpedSource ws = new WarpedSource(src,"Warped_"+src.getName());
                ws.updateTransform(rt);
                ws.setIsTransformed(true);
                return ws;
        };
    }

}
