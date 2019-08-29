package ch.epfl.biop.bdv.commands;

import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, initializer = "init", menuPath = "Plugins>BIOP>BDV>Transform Source (affine)")
public class BDVSourceAffineTransform extends BDVSourceFunctionalInterfaceCommand {

    @Parameter(label = "Affine Transform Matrix", style = "text area")
    String stringMatrix = "1,0,0,0,\n 0,1,0,0,\n 0,0,1,0, \n 0,0,0,1";

    public BDVSourceAffineTransform() {
        this.f = src -> {
            AffineTransform3D at = new AffineTransform3D();
            at.set(this.toDouble());

            if (src instanceof  BDVSourceAffineTransformed) {
                ((BDVSourceAffineTransformed) src).transform =((BDVSourceAffineTransformed) src).transform.concatenate(at);
                return src;
            } else {
                return new BDVSourceAffineTransformed(src, at);
            }
        };
    }

    public double[] toDouble() {
        String[] strNumber = stringMatrix.split(",");
        double[] mat = new double[16];
        if (strNumber.length!=16) {
            System.err.println("matrix has not enough elements");
            return null;
        }
        for (int i=0;i<16;i++) {
            mat[i] = Double.valueOf(strNumber[i].trim());
        }
        return mat;
    }
}
