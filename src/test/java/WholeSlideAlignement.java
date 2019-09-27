import bdv.util.BdvHandle;
import bdv.viewer.Source;
import ch.epfl.biop.bdv.commands.BDVSourceAffineTransform;
import ch.epfl.biop.bdv.scijava.command.open.BigDataViewerPlugInSciJava;
import ch.epfl.biop.bdv.wholeslidealign.AutoWarp2D;
import ch.epfl.biop.bdv.wholeslidealign.RegisterBdvSources2D;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ThinplateSplineTransform;
import org.scijava.command.CommandModule;

import java.util.concurrent.ExecutionException;

public class WholeSlideAlignement {

    public static void main(String... args) {
        DebugTools.enableIJLogging(false);
        DebugTools.enableLogging("INFO");

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // Open Dataset

        try {
            // Opens dataset
            BdvHandle bdvh = (BdvHandle) ij.command().run(BigDataViewerPlugInSciJava.class, true,
                    "file", "C:\\Users\\nicol\\Dropbox\\BIOP\\QuPath Formation\\Test_60519\\Test_60519\\vsiFused3D_v3.xml",
                    "createNewWindow", true).get().getOutput("bdv_h");

            // Approximate rigid registration
           /* CommandModule cm = ij.command().run(RegisterBdvSources2D.class, true,
                    "bdv_h", bdvh,
                    "idxFixedSource", 2,
                    "tpFixed", 0,
                    "levelFixedSource", 5,
                    "idxMovingSource", 9,
                    "tpMoving", 0,
                    "levelMovingSource", 5,
                    "px", -77,
                    "py", -64,
                    "pz", 0,
                    "sx", 8,
                    "sy", 6,
                    "pxSizeInCurrentUnit", 0.01,
                    "interpolate", false,
                    "outputRegisteredSource", true,
                    "showImagePlusRegistrationResult", true
            ).get();

            AffineTransform3D at1 = (AffineTransform3D) cm.getOutput("affineTransformOut");

            // More precise rigid registration
            ij.command().run(RegisterBdvSources2D.class, true,
                    "bdv_h", bdvh,
                    "idxFixedSource", 2,
                    "tpFixed", 0,
                    "levelFixedSource", 4,
                    "idxMovingSource", 21,
                    "tpMoving", 0,
                    "levelMovingSource", 4,
                    "px", -77,
                    "py", -64,
                    "pz", 0,
                    "sx", 8,
                    "sy", 6,
                    "pxSizeInCurrentUnit", 0.0025,
                    "interpolate", false,
                    "outputRegisteredSource", true,
                    "showImagePlusRegistrationResult", true
            ).get();

            // Precise Warping based on particular locations
            ThinplateSplineTransform tst =
                    (ThinplateSplineTransform) ij.command().run(AutoWarp2D.class, true,
                    "bdv_h", bdvh,
                    "idxFixedSource", 2,
                    "tpFixed", 0,
                    "levelFixedSource", 2,
                    "idxMovingSource", 22,// because that's where it was appended
                    "tpMoving", 0,
                    "levelMovingSource", 2,
                    "ptListCoordinates",
                            "-76.5, -62.8,\n" +
                            "-76.9, -60.5,\n" +
                            "-69, -63,\n" +
                            "-69.1, -60.7,\n" +
                            "-74.8, -59.5",
                    "zLocation", 0,
                    "sx", 0.5, // 200 microns
                    "sy", 0.5, // 200 microns
                    "pxSizeInCurrentUnit", 0.001, //1 micron per pixel
                    "interpolate", false,
                    "showPoints", true,
                    "parallel", false,
                    "appendWarpedSource", false
            ).get().getOutput("tst");

            System.out.println("Job Done!");
*/

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }
}
