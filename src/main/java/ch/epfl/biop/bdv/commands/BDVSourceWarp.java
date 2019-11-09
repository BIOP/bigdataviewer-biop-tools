package ch.epfl.biop.bdv.commands;

import bdv.img.WarpedSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.scijava.command.BDVSourceAndConverterFunctionalInterfaceCommand;
import net.imglib2.Volatile;
import net.imglib2.realtransform.RealTransform;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, initializer = "init", menuPath = ScijavaBdvRootMenu+"Transformation>Transform Source (warp)")
public class BDVSourceWarp extends BDVSourceAndConverterFunctionalInterfaceCommand {
    @Parameter(label = "RealTransform object")
    RealTransform rt;

    public BDVSourceWarp() {
        this.f = src -> {
                WarpedSource ws = new WarpedSource(src.getSpimSource(),"Warped_"+src.getSpimSource().getName());
                ws.updateTransform(rt);
                ws.setIsTransformed(true);
                if (ws.getType() instanceof Volatile) {
                    return new SourceAndConverter<>(ws, src.getConverter());
                } else {
                    if (src.asVolatile()== null) {
                        // TODO : wrap source as volatile and create volatile        sourceandconverter
                        return new SourceAndConverter<>(ws, src.getConverter());
                    } else {
                        WarpedSource vws = new WarpedSource(src.asVolatile().getSpimSource(),"Warped_"+src.asVolatile().getSpimSource().getName());
                        vws.updateTransform(rt);
                        vws.setIsTransformed(true);
                        SourceAndConverter vsac = new SourceAndConverter<>(vws, src.asVolatile().getConverter());
                        return new SourceAndConverter<>(ws, src.getConverter(), vsac);
                    }
                }
        };
    }

}
