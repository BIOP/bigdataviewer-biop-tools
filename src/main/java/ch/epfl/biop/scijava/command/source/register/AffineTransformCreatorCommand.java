package ch.epfl.biop.scijava.command.source.register;

import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

@Plugin(type = Command.class, initializer = "init",
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>New Affine Transform",
        description = "Creates an affine transform and makes it accessible for other commands\n"
                     +"An affine transform is a 4x3 matrix; elements are separated by comma.",
        headless = true)
public class AffineTransformCreatorCommand implements Command {

    private static Logger logger = LoggerFactory.getLogger(AffineTransformCreatorCommand.class);

    @Parameter(label = "Affine Transform Matrix", style = "text area")
    String stringMatrix = "1,0,0,0,\n 0,1,0,0,\n 0,0,1,0, \n 0,0,0,1";

    @Parameter(type = ItemIO.OUTPUT)
    AffineTransform3D at3D;

    @Override
    public void run() {
        at3D = new AffineTransform3D();
        at3D.set(toDouble());
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
            logger.error("Matrix has not enough elements, 16 expected, "+strNumber.length+" provided.");
            return null;
        }
        for (int i=0;i<16;i++) {
            mat[i] = Double.valueOf(strNumber[i].trim());
        }
        return mat;
    }

}
