package ch.epfl.biop.bdv.commands;

import ch.epfl.biop.bdv.scijava.util.BDVSourceFunctionalInterfaceCommand;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, initializer = "init", menuPath = ScijavaBdvRootMenu+"Transformation>Transform Source (affine)")
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
        String inputString = stringMatrix;
        // Test if the String is written using AffineTransform3D toString() method
        String[] testIfParenthesis = stringMatrix.split("[\\(\\)]+");// right of left parenthesis

        if (testIfParenthesis!=null) {
            for (String str : testIfParenthesis) {
                System.out.println(str);
            }

            if (testIfParenthesis.length > 1) {
                inputString = testIfParenthesis[1] + ",0,0,0,1";
            }
        }

        String[] strNumber = inputString.split(",");
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
