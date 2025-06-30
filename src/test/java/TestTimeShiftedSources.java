import ij.IJ;
import ij.ImagePlus;
import loci.common.DebugTools;
import net.imagej.ImageJ;

import java.io.IOException;


public class TestTimeShiftedSources {





    static public void main(String... args) throws IOException {
        final ImageJ ij = new ImageJ();

        DebugTools.enableLogging("INFO");
        ij.ui().showUI();

        //dowloadBrainVSIDataset();
        ImagePlus imp = IJ.openImage("http://imagej.net/images/blobs.gif");
        imp.show();
        IJ.run(imp, "Invert LUT", "");
        IJ.run(imp, "Make BDVDataset from current IJ1 image", "");
        IJ.run("Create a time-shifted source", "sac=blobs.gif timeshift=-3 name=shift-3");
        IJ.run("Create a time-shifted source", "sac=blobs.gif timeshift=+3 name=shift+3");
        IJ.run(imp, "Display Sources On Grid", "timepoint_begin=0 n_columns=6 entities_split=imagename");

        imp.show();

    }




}
