package ch.epfl.biop.bdv.register.dim2;

import bdv.util.BdvHandle;
import net.imagej.ops.OpService;
import net.imglib2.*;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, menuPath = ScijavaBdvRootMenu+"Bdv>Edit Sources>Register>2D>Find Line Of Symmetry")
public class FindLineSymmetry2D implements Command {

    @Parameter
    OpService ops;

    @Parameter
    UIService ui;

    @Parameter
    BdvHandle bdv_h;

    @Parameter
    int sourceIndex;

    @Parameter
    int numMipMap;

    @Parameter
    int timepoint;

    @Parameter
    double thresholdValue;

    @Parameter(type = ItemIO.OUTPUT)
    AffineTransform3D at3D = new AffineTransform3D();

    @Override
    public void run() {
        AffineTransform3D srcTrMM = new AffineTransform3D();
        bdv_h.getViewerPanel().getState().getSources().get(sourceIndex).getSpimSource().getSourceTransform(timepoint,numMipMap,srcTrMM);

        RandomAccessibleInterval rai = bdv_h.getViewerPanel().getState().getSources().get(sourceIndex).getSpimSource().getSource(timepoint,numMipMap);

        rai = Views.hyperSlice(rai,2,0); // Gets first slice in Z1
        RandomAccessibleInterval raiTr = (RandomAccessibleInterval) ops.threshold().otsu((IterableInterval) rai);
        RandomAccessibleInterval raiTrFilled = (RandomAccessibleInterval) ops.run("fillHoles", null, raiTr);
        RealLocalizable rl = ops.geom().centerOfGravity((IterableInterval) raiTrFilled);

        double angleMinScore = -0.5;
        double minScore = Double.MAX_VALUE;
        for (double angle = -0.5;angle<0.5;angle+=0.01) {
            double score = getScore(raiTrFilled,rl,angle);
            if (score<minScore) {
                minScore = score;
                angleMinScore = angle;
            }
        }

        AffineTransform3D transformInRAICoord = new AffineTransform3D();
        transformInRAICoord.translate(-rl.getDoublePosition(0),-rl.getDoublePosition(1),0);
        transformInRAICoord.rotate(2,angleMinScore);

        at3D.concatenate(srcTrMM);
        at3D.concatenate(transformInRAICoord);
        double[] translateToCenter = new double[3];
        at3D.apply(new double[]{rl.getDoublePosition(0),rl.getDoublePosition(1),0},translateToCenter);
        translateToCenter[0]*=-1;
        translateToCenter[1]*=-1;
        translateToCenter[2]*=-1;
        at3D.concatenate(srcTrMM.inverse());
        at3D.translate(translateToCenter); // Recenter to zero
    }

    double getScore(RandomAccessibleInterval image, RealLocalizable c, double a) {

        int maxdim = (int) Math.max(image.dimension(0), image.dimension(1));

        AffineTransform2D transform = new AffineTransform2D();
        transform.translate(-c.getDoublePosition(0),-c.getDoublePosition(1));
        transform.rotate(a);

        IntervalView iv = Views.expandZero( image,  new long[] {maxdim,maxdim});
        RealRandomAccessible rimage = Views.interpolate(iv, new NearestNeighborInterpolatorFactory<>() );

        RealRandomAccessible rotrimage = RealViews.affine(rimage,transform);

        RandomAccessibleInterval rasterrotrimageLeft = Views.interval((RandomAccessible) rotrimage, new long[] {-maxdim,-maxdim},
        new long[] {0,maxdim}); //Sets the interval
        //ui.show(rasterrotrimageLeft);

        RandomAccessibleInterval rasterrotrimageRight = Views.invertAxis(Views.interval((RandomAccessible) rotrimage, new long[] {0,-maxdim},
                new long[] {maxdim,maxdim}),0); //Sets the interval
        //ui.show(rasterrotrimageRight);

        Cursor<RealType> cR = ((IterableInterval)rasterrotrimageLeft).localizingCursor();
        RandomAccess<RealType> ra = rasterrotrimageRight.randomAccess();

        int nPxDiff = 0;
        //int[] pos = new int[2];
        while (cR.hasNext()) {
            // move both forward
            cR.fwd();
            //cR.localize(pos);
            ra.setPosition(cR);
            if (cR.get().getRealFloat()!=ra.get().getRealFloat()) {
                nPxDiff ++;
            }
        }
        return nPxDiff;
    }
}
