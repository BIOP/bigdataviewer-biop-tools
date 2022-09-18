package bdv.util;

import bdv.util.source.field.ITransformFieldSource;
import bdv.util.source.field.ResampledTransformFieldSource;
import bdv.viewer.Source;
import jitk.spline.ThinPlateR2LogRSplineKernelTransform;
import net.imglib2.realtransform.*;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class RealTransformHelper {

    private static Logger logger = LoggerFactory.getLogger(RealTransformHelper.class);

    public static String BigWarpFileFromRealTransform(RealTransform rt) {
        try {
            File file = File.createTempFile("temp", null);
            //System.out.println(file.getAbsolutePath());
            file.deleteOnExit();

            if (rt instanceof Wrapped2DTransformAs3D) {
                rt = ((Wrapped2DTransformAs3D)rt).transform;
            }

            if (rt instanceof WrappedIterativeInvertibleRealTransform) {
                rt = ((WrappedIterativeInvertibleRealTransform<?>)rt).getTransform();
            }

            if (rt instanceof BoundedRealTransform) {
                rt = ((BoundedRealTransform)rt).getTransform();

                if (rt instanceof Wrapped2DTransformAs3D) {
                    rt = ((Wrapped2DTransformAs3D)rt).transform;
                }

                if (rt instanceof WrappedIterativeInvertibleRealTransform) {
                    rt = ((WrappedIterativeInvertibleRealTransform<?>)rt).getTransform();
                }
            }

            if (!(rt instanceof ThinplateSplineTransform)) {
                logger.error("Cannot edit the transform : it's not of class thinplatesplinetransform");
                return null;
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

    public static RealTransform resampleTransform(RealTransform origin, Source<?> model) {
        ITransformFieldSource transformFieldSource = new ResampledTransformFieldSource(origin, model, "undef");
        return new SourcedRealTransform(transformFieldSource);
    }

}
