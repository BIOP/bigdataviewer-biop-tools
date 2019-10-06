package ch.epfl.biop.bdv.source.ops;

import bdv.viewer.Source;
import ch.epfl.biop.bdv.process.BDVSourceAffineTransformed;
import net.imagej.ops.AbstractOp;
import net.imagej.ops.Op;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Op.class, name = "affinetransformsource")
public class AffineTransformSource extends AbstractOp {

    @Parameter
    AffineTransform3D at3D;

    @Parameter
    Source bdvSrc;

    @Parameter(type = ItemIO.OUTPUT)
    Source bdvSrcTransformed;

    public void run() {
        bdvSrcTransformed = new BDVSourceAffineTransformed(bdvSrc, at3D);
    }

}
