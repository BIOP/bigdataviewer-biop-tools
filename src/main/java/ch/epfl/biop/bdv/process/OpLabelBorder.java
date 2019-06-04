package ch.epfl.biop.bdv.process;

import net.imagej.ops.AbstractOp;
import net.imagej.ops.Op;
import net.imglib2.RandomAccessibleInterval;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static ch.epfl.biop.bdv.process.CachedComputing.get3DBorderLabelImage;

@Plugin(type = Op.class, name = "border")
public class OpLabelBorder extends AbstractOp {
    @Parameter(type = ItemIO.INPUT)
    RandomAccessibleInterval lblImg;

    @Parameter(type = ItemIO.OUTPUT)
    RandomAccessibleInterval lblImgBorder;

    @Override
    public void run() {
        lblImgBorder = get3DBorderLabelImage(lblImg);
    }

}
