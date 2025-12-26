package ch.epfl.biop.scijava.command.transform;

import bdv.AbstractSpimSource;
import bdv.tools.transformation.TransformedSource;
import bdv.viewer.SourceAndConverter;
import ij.IJ;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;
import org.scijava.command.Command;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Rotation 3D Transform",
        description = "Applies interactive 3D rotation to sources around a specified center point")
public class Rotation3DTransformCommand extends InteractiveCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Source(s)",
            description = "The sources to rotate (must be wrapped as TransformedSource)")
    SourceAndConverter[] sacs;

    @Parameter(label = "Rotation X",
            style = "slider,format:0.#####E0",
            min = "0",
            max = "360",
            description = "Rotation angle around X axis in degrees")
    int rx;

    @Parameter(label = "Rotation Y",
            style = "slider,format:0.#####E0",
            min = "0",
            max = "360",
            description = "Rotation angle around Y axis in degrees")
    int ry;

    @Parameter(label = "Rotation Z",
            style = "slider,format:0.#####E0",
            min = "0",
            max = "360",
            description = "Rotation angle around Z axis in degrees")
    double rz;

    @Parameter(label = "Center X",
            style = "format:0.#####E0",
            description = "X coordinate of rotation center")
    double cx;

    @Parameter(label = "Center Y",
            style = "format:0.#####E0",
            description = "Y coordinate of rotation center")
    double cy;

    @Parameter(label = "Center Z",
            style = "format:0.#####E0",
            description = "Z coordinate of rotation center")
    double cz;

    public void run() {

        double rxRad = Math.PI * rx/360.0; // factor 2 because quaternions
        double ryRad = Math.PI * ry/360.0; // factor 2 because quaternions
        double rzRad = Math.PI * rz/360.0; // factor 2 because quaternions

        double[] qx = new double[4];

        qx[0] = Math.cos(rxRad);
        qx[1] = Math.sin(rxRad);
        qx[2] = 0;
        qx[3] = 0;

        double[] qy = new double[4];

        qy[0] = Math.cos(ryRad);
        qy[1] = 0;
        qy[2] = Math.sin(ryRad);
        qy[3] = 0;

        double[] qz = new double[4];

        qz[0] = Math.cos(rzRad);
        qz[1] = 0;
        qz[2] = 0;
        qz[3] = Math.sin(rzRad);

        double[] qXY = new double[4];

        LinAlgHelpers.quaternionMultiply(qy,qx,qXY);

        double[] qRes = new double[4];

        LinAlgHelpers.quaternionMultiply(qz,qXY,qRes);

        double [][] m = new double[3][3];

        AffineTransform3D rotMatrix = new AffineTransform3D();

        LinAlgHelpers.quaternionToR(qRes, m);

        rotMatrix.set( m[0][0], m[0][1], m[0][2], 0,
                       m[1][0], m[1][1], m[1][2], 0,
                       m[2][0], m[2][1], m[2][2], 0);

        AffineTransform3D at3D = new AffineTransform3D();

        at3D.translate(-cx, -cy, -cz);

        at3D.preConcatenate(rotMatrix);

        if (sacs!=null) {
            for (SourceAndConverter sac:sacs) {
                if (sac!=null) {
                    if (sac.getSpimSource() instanceof TransformedSource) {
                        ((TransformedSource) sac.getSpimSource()).setFixedTransform(at3D);
                        SourceAndConverterServices.getBdvDisplayService().updateDisplays(sac);
                    } else {
                        IJ.log("Can't rotate a non transformed source, please use  BigDataViewer-Playground › Sources › Transform › Wrap as Transformed Source");
                    }
                }
            }
        }
    }
}
