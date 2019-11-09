package ch.epfl.biop.bdv.commands;

import bdv.tools.transformation.TransformedSource;
import bdv.viewer.SourceAndConverter;
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

            /*if (src.getSpimSource() instanceof TransformedSource) {
                System.out.println("Rien a faire");
                TransformedSource ts = (TransformedSource) src.getSpimSource();
                AffineTransform3D at3d = new AffineTransform3D();
                ts.getFixedTransform(at3d);
                ts.setFixedTransform(at3d.concatenate(at));


                // Check whether the volatile one is tranformed...
                return src;
            } else*/
            {
                // TODO : wrap source with a TransformedSource
                System.out.println("Wrapping source within a Transformed Source");
                SourceAndConverter sac;
                TransformedSource ts = new TransformedSource(src.getSpimSource());
                ts.setFixedTransform(at);
                if (src.asVolatile()!=null) {
                    SourceAndConverter vsac;
                    TransformedSource vts = new TransformedSource(src.asVolatile().getSpimSource());
                    vts.setFixedTransform(at);
                    vsac = new SourceAndConverter(vts, src.asVolatile().getConverter());
                    sac = new SourceAndConverter<>(ts, src.getConverter(),vsac);
                } else {
                    sac = new SourceAndConverter<>(ts, src.getConverter());
                }
                return sac;
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
