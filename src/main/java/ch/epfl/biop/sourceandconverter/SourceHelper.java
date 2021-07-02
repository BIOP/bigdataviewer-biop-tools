package ch.epfl.biop.sourceandconverter;

import bdv.util.EmptySource;
import bdv.util.ResampledSource;
import bdv.viewer.Source;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

public class SourceHelper {

    public static Source AlignAxisResample(Source src, RealPoint pA, RealPoint pB, double voxSize, int nPixX, int nPixY, int nPixZ, boolean alignX, boolean sourceInterpolate) {
        AffineTransform3D rot = getRotationMatrixAlignZ(pA,pB, alignX);

        double cx = ((pA.getDoublePosition(0)+pB.getDoublePosition(0))/2.0);
        double cy = ((pA.getDoublePosition(1)+pB.getDoublePosition(1))/2.0);
        double cz = ((pA.getDoublePosition(2)+pB.getDoublePosition(2))/2.0);

        RealPoint center = new RealPoint(3);
        center.setPosition(new double[] {cx,cy,cz});

        AffineTransform3D translateCenterBwd = new AffineTransform3D();
        translateCenterBwd.translate(-nPixX/2,-nPixY/2,-nPixZ/2);

        AffineTransform3D translateCenterFwd = new AffineTransform3D();
        translateCenterFwd.translate(-cx,-cy,-cz);

        AffineTransform3D scaler = new AffineTransform3D();
        scaler.scale(voxSize, voxSize, voxSize);

        AffineTransform3D m = new AffineTransform3D();

        m.concatenate(translateCenterFwd.inverse());
        m.concatenate(rot);
        m.concatenate(scaler);
        m.concatenate(translateCenterBwd);

        Source model = new EmptySource(nPixX,nPixY,nPixZ,m,src.getName()+"_ZAlignedModel");

        ResampledSource resampled_src = new ResampledSource(src, model, false, true, sourceInterpolate,0);

        return resampled_src;
    }

    public static AffineTransform3D getRotationMatrixAlignZ(RealPoint pt1, RealPoint pt2, boolean alignX) {
        double v1x = (pt2.getDoublePosition(0)-pt1.getDoublePosition(0));
        double v1y = (pt2.getDoublePosition(1)-pt1.getDoublePosition(1));
        double v1z = (pt2.getDoublePosition(2)-pt1.getDoublePosition(2));

        double normv1 = Math.sqrt(v1x*v1x+v1y*v1y+v1z*v1z);

        v1x = v1x/normv1;
        v1y = v1y/normv1;
        v1z = v1z/normv1;

        double v2x = 0;
        double v2y = 0;
        double v2z = 1;

        double normv2 = Math.sqrt(v2x*v2x+v2y*v2y+v2z*v2z);

        v2x = v2x/normv2;
        v2y = v2y/normv2;
        v2z = v2z/normv2;

        double[] q1 = new double[4];

        q1[0] = 1 + v2x*v1x+v2y*v1y+v2z*v1z;
        q1[1] = v2y*v1z-v2z*v1y;
        q1[2] = v2z*v1x-v2x*v1z;
        q1[3] = v2x*v1y-v2y*v1x;

        double normq1 = Math.sqrt( q1[0]*q1[0]+q1[1]*q1[1]+q1[2]*q1[2]+q1[3]*q1[3] );

        q1[0]/=normq1;
        q1[1]/=normq1;
        q1[2]/=normq1;
        q1[3]/=normq1;

        double [][] m = new double[3][3];

        AffineTransform3D rotMatrix = new AffineTransform3D();

        if (alignX) {
            double[] q2 = new double[4];

            double alpha = (Math.atan2(v1y,v1x)+Math.PI/2.0)/2.0;

            q2[0] = Math.cos(alpha);
            q2[3] = Math.sin(alpha);

            double[] q1q2 = new double[4];

            LinAlgHelpers.quaternionMultiply(q1,q2,q1q2);
            LinAlgHelpers.quaternionToR(q1q2, m);
        } else {
            LinAlgHelpers.quaternionToR(q1, m);
        }

        rotMatrix.set( m[0][0], m[0][1], m[0][2], 0,
                m[1][0], m[1][1], m[1][2], 0,
                m[2][0], m[2][1], m[2][2], 0);

        return rotMatrix;
    }
}
