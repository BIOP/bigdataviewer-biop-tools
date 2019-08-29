package ch.epfl.biop.bdv.commands;

import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, initializer = "init", menuPath = "Plugins>BIOP>BDV>Transform Source (affine)")
public class BDVSourceAffineTransform extends BDVSourceFunctionalInterfaceCommand {

    @Parameter(label = "Affine Transform Matrix", style = "text area")
    String matrix;

    public BDVSourceAffineTransform() {
        this.f = src -> {
            AffineTransform3D at = new AffineTransform3D();
            at.translate(20,0,0);

            if (src instanceof  BDVSourceAffineTransformed) {
                ((BDVSourceAffineTransformed) src).transform.concatenate(at);
                return src;
            } else {
                return new BDVSourceAffineTransformed(src, at);
            }
        };
    }
}
