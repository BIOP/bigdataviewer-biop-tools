package ch.epfl.biop.bdv.command;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
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
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

// TODO : make it ok with the actual source transform, not only with RAI

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Find Line Of Symmetry (2D)")
public class FindLineSymmetry2DCommand implements Command {

    @Parameter
    OpService ops;

    @Parameter
    UIService ui;

    @Parameter
    SourceAndConverter sac;

    @Parameter
    int timepoint;

    @Parameter
    int numMipMap;

    @Parameter(type = ItemIO.OUTPUT)
    AffineTransform3D at3D = new AffineTransform3D();

    @Override
    public void run() {
        AffineTransform3D srcTrMM = new AffineTransform3D();
        sac.getSpimSource().getSourceTransform(timepoint,numMipMap,srcTrMM);

        RandomAccessibleInterval rai = sac.getSpimSource().getSource(timepoint,numMipMap);

        rai = Views.hyperSlice(rai,2,0); // Gets first slice in Z1
        RandomAccessibleInterval raiTr = (RandomAccessibleInterval) ops.threshold().otsu((IterableInterval) rai);
        RandomAccessibleInterval raiTrFilled = (RandomAccessibleInterval) ops.run("fillHoles", null, raiTr);
        RealLocalizable rl = ops.geom().centerOfGravity((IterableInterval) raiTrFilled);

        double angleMinScore = -0.5;
        double minScore = Double.MAX_VALUE;
        for (double angle = -0.5;angle<0.5;angle+=0.01) {
            double score = getScore(raiTrFilled,rl,angle);
            //System.out.println("angle \t"+angle+ "\t score \t"+score);
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