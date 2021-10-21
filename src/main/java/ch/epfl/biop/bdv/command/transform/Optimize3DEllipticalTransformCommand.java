package ch.epfl.biop.bdv.command.transform;

import bdv.img.WarpedSource;
import bdv.util.Elliptical3DTransform;
import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import net.imglib2.RealRandomAccess;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.scijava.plugin.Parameter;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

import static bdv.util.Elliptical3DTransform.CENTER_X;
import static bdv.util.Elliptical3DTransform.CENTER_Y;
import static bdv.util.Elliptical3DTransform.CENTER_Z;
import static bdv.util.Elliptical3DTransform.RADIUS_X;
import static bdv.util.Elliptical3DTransform.RADIUS_Y;
import static bdv.util.Elliptical3DTransform.RADIUS_Z;
import static bdv.util.Elliptical3DTransform.ROTATION_X;
import static bdv.util.Elliptical3DTransform.ROTATION_Y;
import static bdv.util.Elliptical3DTransform.ROTATION_Z;


@Plugin(type = BdvPlaygroundActionCommand.class, initializer = "init", menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Elliptic 3D Transform Optimization")
public class Optimize3DEllipticalTransformCommand implements BdvPlaygroundActionCommand{

    @Parameter(type = ItemIO.BOTH)
    Elliptical3DTransform e3dT;

    @Parameter
    double thresholdIntensity = 0;

    @Parameter
    boolean r1=true;
    @Parameter(style="format:0.#####E0")
    double sr1 = 1;
    @Parameter
    boolean r2=true;
    @Parameter(style="format:0.#####E0")
    double sr2 = 1;
    @Parameter
    boolean r3=true;
    @Parameter(style="format:0.#####E0")
    double sr3 = 1;
    @Parameter
    boolean rx=true;
    @Parameter(style="format:0.#####E0")
    double srx = 0.1;
    @Parameter
    boolean ry=true;
    @Parameter(style="format:0.#####E0")
    double sry = 0.1;
    @Parameter
    boolean rz =true;
    @Parameter(style="format:0.#####E0")
    double srz = 0.1;
    @Parameter
    boolean tx=true;
    @Parameter(style="format:0.#####E0")
    double stx = 1;
    @Parameter
    boolean ty=true;
    @Parameter(style="format:0.#####E0")
    double sty = 1;
    @Parameter
    boolean tz=true;
    @Parameter(style="format:0.#####E0")
    double stz = 1;

    @Parameter
    SourceAndConverter sac;

    int nOptimizedParams;

    @Parameter
    int sourceMipMapLevel=0;

    @Parameter
    int sourceTimePoint = 0;

    @Parameter
    double phiMin=-Math.PI, phiMax=Math.PI, thetaMin=0, thetaMax=Math.PI, dPhi=0.05, dTheta=0.05;

    @Parameter
    int maxOptimisationStep;

    @Parameter
    int timeout_seconds;

    public void run() {
        // Is this a warped source ?
        WarpedSource<?> ws = (WarpedSource<?>) sac.getSpimSource();
        /*
        try {
            if ( bdv_h.getViewerPanel().getState().getSources().get(sourceIndex).getSpimSource() instanceof TransformedSource) {
                TransformedSource<?> ts = (TransformedSource<?>) bdv_h.getViewerPanel().getState().getSources().get(sourceIndex).getSpimSource();
                System.out.println(ts.getWrappedSource().getClass());
                ws = (WarpedSource<?>) (ts.getWrappedSource());
            } else if ( bdv_h.getViewerPanel().getState().getSources().get(sourceIndex).getSpimSource() instanceof WarpedSource) {
                ws = (WarpedSource<?>) (bdv_h.getViewerPanel().getState().getSources().get(sourceIndex).getSpimSource());
            } else {
                System.err.println("Source is not a WarpedSource. Cannot optimize");
                System.out.println(bdv_h.getViewerPanel().getState().getSources().get(sourceIndex).getSpimSource().getClass());
                return;
            }
        } catch (ClassCastException e) {
            System.err.println("Source is not a WarpedSource. Cannot optimize");
            return;
        }*/

        nOptimizedParams=0;
        if (r1) nOptimizedParams++;
        if (r2) nOptimizedParams++;
        if (r3) nOptimizedParams++;
        if (rx) nOptimizedParams++;
        if (ry) nOptimizedParams++;
        if (rz) nOptimizedParams++;
        if (tx) nOptimizedParams++;
        if (ty) nOptimizedParams++;
        if (tz) nOptimizedParams++;

        if (ws.getType() instanceof RealType) {
            //RealRandomAccess rra = ws.getInterpolatedSource(sourceTimePoint,sourceMipMapLevel, Interpolation.NEARESTNEIGHBOR).realRandomAccess();

            MultivariateFunction mf = doubles -> {
                this.setParams(doubles);
                double ans = computeIntegratedIntensity((RealRandomAccess) ws.getInterpolatedSource(sourceTimePoint,sourceMipMapLevel, Interpolation.NEARESTNEIGHBOR).realRandomAccess());
                Map<String, Double> params = e3dT.getParameters();
                return  ans*params.get(RADIUS_X)*params.get(RADIUS_Y)*params.get(RADIUS_Z); // Avoid strongly curved on a single bright pixel -> correct jacobian ?
            };

            SimplexOptimizer optimizer = new SimplexOptimizer(1e-10, 1e-30);
            try {



                final Duration timeout = Duration.ofSeconds(timeout_seconds);
                ExecutorService executor = Executors.newSingleThreadExecutor();

                final Future<double[]> handler = executor.submit(new Callable() {

                    @Override
                    public double[] call() throws Exception {
                        final PointValuePair optimum =
                                optimizer.optimize(
                                        new MaxEval(maxOptimisationStep),
                                        new ObjectiveFunction(mf),
                                        GoalType.MAXIMIZE,
                                        new InitialGuess(getCurrentOptimizedParamsAsDoubles()),
                                        new NelderMeadSimplex(getStepOptimizedParamsAsDoubles())
                                );// Steps for optimization
                        return optimum.getPoint();
                    }
                });

                try {
                    this.setParams(handler.get(timeout.toMillis(), TimeUnit.MILLISECONDS));//optimum.getPoint());
                } catch (TimeoutException e) {
                    handler.cancel(true);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }

                executor.shutdownNow();


            } catch (TooManyEvaluationsException e) {

                System.err.println("Optimization did not converge in "+maxOptimisationStep+" iterations");
            }
            /*System.out.println(Arrays.toString(e3dT.getParameters().values()) + " : "
                    + optimum.getSecond());*/

        } else {
            System.err.println("Cannot optimize because the pixel type is not numeric");
        }
    }

    public double[] getCurrentOptimizedParamsAsDoubles() {
        double[] ans = new double[nOptimizedParams];
        int cIndex=0;
        Map<String, Double> p = e3dT.getParameters();
        if (r1) {ans[cIndex]=p.get(RADIUS_X);cIndex++;}
        if (r2) {ans[cIndex]=p.get(RADIUS_Y);cIndex++;}
        if (r3) {ans[cIndex]=p.get(RADIUS_Z);cIndex++;}
        if (rx) {ans[cIndex]=p.get(ROTATION_X);cIndex++;}
        if (ry) {ans[cIndex]=p.get(ROTATION_Y);cIndex++;}
        if (rz) {ans[cIndex]=p.get(ROTATION_Z);cIndex++;}
        if (tx) {ans[cIndex]=p.get(CENTER_X);cIndex++;}
        if (ty) {ans[cIndex]=p.get(CENTER_Y);cIndex++;}
        if (tz) {ans[cIndex]=p.get(CENTER_Z);cIndex++;}
        return ans;
    }

    public double[] getStepOptimizedParamsAsDoubles() {
        double[] ans = new double[nOptimizedParams];
        int cIndex=0;
        Map<String, Double> p = e3dT.getParameters();
        if (r1) {ans[cIndex]=sr1;cIndex++;}
        if (r2) {ans[cIndex]=sr2;cIndex++;}
        if (r3) {ans[cIndex]=sr3;cIndex++;}
        if (rx) {ans[cIndex]=srx;cIndex++;}
        if (ry) {ans[cIndex]=sry;cIndex++;}
        if (rz) {ans[cIndex]=srz;cIndex++;}
        if (tx) {ans[cIndex]=stx;cIndex++;}
        if (ty) {ans[cIndex]=sty;cIndex++;}
        if (tz) {ans[cIndex]=stz;cIndex++;}
        return ans;
    }

    public void setParams(double[] params) {
        Object[] args = new Object[params.length*2];
        //System.out.println("r1="+params[0]);
        int cIndex=0;
        if (r1) {args[2*cIndex]=RADIUS_X;args[2*cIndex+1]=params[cIndex];cIndex++;}
        if (r2) {args[2*cIndex]=RADIUS_Y;args[2*cIndex+1]=params[cIndex];cIndex++;}
        if (r3) {args[2*cIndex]=RADIUS_Z;args[2*cIndex+1]=params[cIndex];cIndex++;}
        if (rx) {args[2*cIndex]=ROTATION_X;args[2*cIndex+1]=params[cIndex];cIndex++;}
        if (ry) {args[2*cIndex]=ROTATION_Y;args[2*cIndex+1]=params[cIndex];cIndex++;}
        if (rz) {args[2*cIndex]=ROTATION_Z;args[2*cIndex+1]=params[cIndex];cIndex++;}
        if (tx) {args[2*cIndex]=CENTER_X;args[2*cIndex+1]=params[cIndex];cIndex++;}
        if (ty) {args[2*cIndex]=CENTER_Y;args[2*cIndex+1]=params[cIndex];cIndex++;}
        if (tz) {args[2*cIndex]=CENTER_Z;args[2*cIndex+1]=params[cIndex];cIndex++;}
        e3dT.setParameters(args);
    }

    int counter = 0;

    public < T extends RealType< T > & NativeType< T >> double computeIntegratedIntensity(RealRandomAccess<T> rra) {
        // TODO : ponderate with Jacobian
        double somme =0;
        for (double pTheta=thetaMin;pTheta<=thetaMax;pTheta+=dTheta) {
            for (double pPhi=phiMin;pPhi<=phiMax;pPhi+=dPhi) {
                rra.setPosition(new double[]{1,pTheta,pPhi});
                double v = rra.get().getRealDouble();
                if (v>thresholdIntensity) {
                    somme+=v*Math.abs(Math.sin(pTheta));
                }
            }
        }
        counter++;
        System.out.println(counter+"\t v="+somme);
        return somme;
    }

}
