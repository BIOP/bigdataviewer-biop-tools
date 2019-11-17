package ch.epfl.biop.bdv.register;

import bdv.util.BdvHandle;
import ch.epfl.biop.bdv.scijava.command.export.BDVSlicesToImgPlus;
import ij.ImagePlus;
import net.imagej.ops.OpService;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;

import java.util.concurrent.ExecutionException;

public class ShapeAlignAlongZAxis implements Command {

    @Parameter
    double threshold;

    @Parameter
    BdvHandle bdv_h;

    @Parameter
    int sourceIndex;

    @Parameter
    CommandService cs;

    @Parameter
    OpService ops;

    @Parameter(label = "Timepoint", persist = false)
    public int timepoint = 0;

    @Override
    public void run() {
        // Get a 400x400 pixel image
        try {
            ImagePlus imp = (ImagePlus) cs.run(BDVSlicesToImgPlus.class, true,
                    "bdv_h", bdv_h,
                    "sourceIndexString", sourceIndex,
                    "matchWindowSize", false,
                    "wrapMultichannelParallel", false,
                    "ignoreSourceLut", true,
                    "interpolate", false,
                    "timepoint", timepoint,
                    "zSize", 0).get().getOutput("imp");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }
}
