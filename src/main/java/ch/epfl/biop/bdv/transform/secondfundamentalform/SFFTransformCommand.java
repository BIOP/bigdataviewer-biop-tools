package ch.epfl.biop.bdv.transform.secondfundamentalform;

import bdv.util.BWBdvHandle;
import bdv.util.BdvHandle;
import bdv.viewer.Source;
import bigwarp.BigWarp;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Vector3d;

import java.util.Arrays;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, menuPath = ScijavaBdvRootMenu+"Bdv>Edit Sources>Transform>Transform Source (second fundamental form)")
public class SFFTransformCommand implements Command {
    @Parameter(label = "Bdv Frame from BigWarp")
    BdvHandle bdv_warp;

    // Center of the plate
    @Parameter
    double ox;
    @Parameter
    double oy;
    @Parameter
    double oz;

    // First vector of the referential
    @Parameter
    double phi;
    @Parameter
    double theta;
    @Parameter
    double normal_angle;


    // Radius of curvature
    @Parameter
    double cu;
    @Parameter
    double cv;

    @Parameter
    double du;

    @Parameter
    double dv;

    @Parameter
    int nPtu;

    @Parameter
    int nPtv;

    // Thickness of the thin plate
    @Parameter
    double thickness;

    @Parameter
    boolean optimize;

    public SecondFundamentalForm sff;

    public BigWarp bdvH;

    public int counter = 0;

    @Override
    public void run() {
        phi = phi/180*Math.PI;
        theta = theta/180*Math.PI;
        normal_angle = normal_angle/180*Math.PI;

        bdvH = ((BWBdvHandle) bdv_warp).getBW();

        // Let's remove all points

        // how many landmark pairs are there?
        int nrows = bdvH.getLandmarkPanel().getTableModel().getRowCount();

        // delete the ith row (pair) of landmark points
        for (int i = 0;i<nrows;i++) {
            bdvH.getLandmarkPanel().getTableModel().deleteRow( 0 );
        }

        sff = new SecondFundamentalForm();

        sff.setParameters(ox, oy, oz,
                          theta, phi, normal_angle,
                          cu,cv);

        if (optimize) {
            optimizeShapeOnIntensity();
            setPointsBW();
        } else {
            setPointsBW();
        }

    }

    public void setPointsBW() {
        for (int u=-nPtu;u<=nPtu;u++) {
            for (int v=-nPtv;v<=nPtv;v++) {
                double pu = u*du;
                double pv = v*dv;

                Vector3d pB = sff.getPosAt(pu,pv,-thickness/2); // Below

                //System.out.println(Arrays.toString(new double[]{pB.x,pB.y,pB.z}));

                Vector3d pA = sff.getPosAt(pu,pv,+thickness/2); // Above

                bdvH.addPoint(new double[]{pu,pv,thickness/20d},false);
                bdvH.addPoint(new double[]{pA.x,pA.y,pA.z},true);

                bdvH.addPoint(new double[]{pu,pv,-thickness/20d},false);
                bdvH.addPoint(new double[]{pB.x,pB.y,pB.z},true);

            }
        }
    }

    public < T extends RealType< T > & NativeType< T >> void optimizeShapeOnIntensity() {
        Source<?> src =  bdv_warp.getViewerPanel().getState().getSources().get(0).getSpimSource();
        if (src.getType() instanceof RealType) {
            RandomAccessibleInterval<?> rai = src.getSource(0,0);
            RealRandomAccessible<?> image3DInfinite = Views.interpolate(Views.extendMirrorSingle( rai ), new NearestNeighborInterpolatorFactory() );
            RealRandomAccess<T> rra = (RealRandomAccess<T>) image3DInfinite.realRandomAccess();

            MultivariateFunction mf = doubles -> {
                sff.setParametersArray(doubles);
                return computeIntegratedIntensity(rra, 4);
            };

            SimplexOptimizer optimizer = new SimplexOptimizer(1e-10, 1e-30);

            final PointValuePair optimum =
                    optimizer.optimize(
                            new MaxEval(1000),
                            new ObjectiveFunction(mf),
                            GoalType.MAXIMIZE,
                            new InitialGuess(sff.getParametersArray()),
                            new NelderMeadSimplex(new double[]{ 1  ,1  , 1,        // pixels
                                                                0.1  ,0.1, 0.1,          // angle in degree
                                                                0.1,0.1     // curvature
                                                    }));// Steps for optimization


            sff.setParametersArray(optimum.getPoint());

            System.out.println(Arrays.toString(sff.getParametersArray()) + " : "
                    + optimum.getSecond());

        } else {
            System.err.println("Cannot optimize because the pixel type is not numeric");
        }
    }

    public < T extends RealType< T > & NativeType< T >> double computeIntegratedIntensity(RealRandomAccess<T> rra, int subsampling) {
        double somme =0;
        //T val_sum;
        //val_sum = rra.get().createVariable();
        //val_sum.setZero();
        for (int u=-nPtu*subsampling;u<=nPtu*subsampling;u++) {
            for (int v=-nPtv*subsampling;v<=nPtv*subsampling;v++) {
                double pu = u*du/subsampling;
                double pv = v*dv/subsampling;
                Vector3d pt = sff.getPosAt(pu,pv,0); // On curved plane
                rra.setPosition(new double[]{pt.x,pt.y,pt.z});
                somme+=rra.get().getRealDouble();
            }
        }
        counter++;
        System.out.println(counter+"\t v="+somme);//val_sum.getRealDouble());
        return somme;//val_sum.getRealDouble();
    }

}
