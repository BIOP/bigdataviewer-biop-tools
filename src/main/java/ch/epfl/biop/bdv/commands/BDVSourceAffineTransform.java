package ch.epfl.biop.bdv.commands;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.process.BDVSourceAffineTransformed;
import ch.epfl.biop.bdv.scijava.command.BDVSourceAndConverterFunctionalInterfaceCommand;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, initializer = "init", menuPath = ScijavaBdvRootMenu+"Transformation>Transform Source (affine)")
public class BDVSourceAffineTransform extends BDVSourceAndConverterFunctionalInterfaceCommand {

    @Parameter(label = "Affine Transform Matrix", style = "text area")
    String stringMatrix = "1,0,0,0,\n 0,1,0,0,\n 0,0,1,0, \n 0,0,0,1";

    public BDVSourceAffineTransform() {
        this.f = src -> {
            AffineTransform3D at = new AffineTransform3D();
            at.set(this.toDouble());

            if (src.getSpimSource() instanceof BDVSourceAffineTransformed) {
                ((BDVSourceAffineTransformed) src.getSpimSource()).transform =((BDVSourceAffineTransformed) src.getSpimSource()).transform.concatenate(at);
                return src;
            } else {
                // TODO : wrapping as volatile
                return new SourceAndConverter<>(new BDVSourceAffineTransformed(src.getSpimSource(), at), src.getConverter());
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
