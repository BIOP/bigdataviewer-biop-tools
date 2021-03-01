package bdv.util;

import jitk.spline.ThinPlateR2LogRSplineKernelTransform;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.realtransform.Wrapped2DTransformAs3D;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import sc.fiji.bdvpg.services.serializers.plugins.ThinPlateSplineTransformAdapter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class RealTransformHelper {

    public static String BigWarpFileFromRealTransform(RealTransform rt) {
        try {
            File file = File.createTempFile("temp", null);
            System.out.println(file.getAbsolutePath());
            file.deleteOnExit();

            if (rt instanceof Wrapped2DTransformAs3D) {
                rt = ((Wrapped2DTransformAs3D)rt).transform;
            }

            if (rt instanceof WrappedIterativeInvertibleRealTransform) {
                rt = ((WrappedIterativeInvertibleRealTransform)rt).getTransform();
            }

            if (rt instanceof BoundedRealTransform) {
                rt = ((BoundedRealTransform)rt).getTransform();

                if (rt instanceof Wrapped2DTransformAs3D) {
                    rt = ((Wrapped2DTransformAs3D)rt).transform;
                }

                if (rt instanceof WrappedIterativeInvertibleRealTransform) {
                    rt = ((WrappedIterativeInvertibleRealTransform)rt).getTransform();
                }
            }

            if (!(rt instanceof ThinplateSplineTransform)) {
                System.err.println("Cannot edit the transform : it's not of class thinplatesplinetransform");
            }

            ThinplateSplineTransform tst = (ThinplateSplineTransform) rt;

            ThinPlateR2LogRSplineKernelTransform kernel = ThinPlateSplineTransformAdapter.getKernel(tst);

            double[][] srcPts = ThinPlateSplineTransformAdapter.getSrcPts(kernel);
            double[][] tgtPts = ThinPlateSplineTransformAdapter.getTgtPts(kernel);

            int nbLandmarks = kernel.getNumLandmarks();
            int nbDimensions = kernel.getNumDims();

            String toFile = "";

            for (int i = 0;i<nbLandmarks;i++) {
                toFile+="\"Pt-"+i+"\",\"true\"";
                for (int d = 0; d<nbDimensions; d++) {
                    toFile+=",\""+tgtPts[d][i]+"\"";
                }
                for (int d = 0; d<nbDimensions; d++) {
                    toFile+=",\""+srcPts[d][i]+"\"";
                }
                toFile+="\n";
            }

            FileWriter writer = new FileWriter(file);
            writer.write(toFile);
            writer.flush();
            writer.close();

            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ThinplateSplineTransform getTransform(List<RealPoint> moving_pts, List<RealPoint> fixed_pts) {
        int nbDimensions = moving_pts.get(0).numDimensions();
        int nbLandmarks = moving_pts.size();

        double[][] tgtPts = new double[nbDimensions][nbLandmarks];
        double[][] srcPts = new double[nbDimensions][nbLandmarks];

        for (int i = 0;i<nbLandmarks;i++) {
            for (int d = 0; d<nbDimensions; d++) {
                srcPts[d][i] = fixed_pts.get(i).getDoublePosition(d);
                //System.out.println("srcPts["+d+"]["+i+"]=" +srcPts[d][i]);
            }
            for (int d = 0; d<nbDimensions; d++) {
                tgtPts[d][i] = moving_pts.get(i).getDoublePosition(d);
                //System.out.println("tgtPts["+d+"]["+i+"]=" +tgtPts[d][i]);
            }
        }

        return new ThinplateSplineTransform(srcPts, tgtPts);
    }

}
