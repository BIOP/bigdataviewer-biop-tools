package bdv.util;

import com.opencsv.CSVReader;
import jitk.spline.ThinPlateR2LogRSplineKernelTransform;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.realtransform.Wrapped2DTransformAs3D;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import sc.fiji.bdvpg.services.serializers.plugins.ThinPlateSplineTransformAdapter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
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

    public static RealTransform RealTransformFromBigWarpFile(File f, boolean force3d) throws Exception{

        CSVReader reader = new CSVReader( new FileReader( f.getAbsolutePath() ));
        List< String[] > rows;
        rows = reader.readAll();
        reader.close();
        if( rows == null || rows.size() < 1 )
        {
            throw new IOException("Wrong number of rows in file "+f.getAbsolutePath());
        }

        int ndims = 3;
        int expectedRowLength = 8;
        int numRowsTmp = 0;

        ArrayList<double[]> movingPts = new ArrayList<>();
        ArrayList<double[]>	targetPts = new ArrayList<>();

        for( String[] row : rows )
        {
            // detect a file with 2d landmarks
            if( numRowsTmp == 0 && // only check for the first row
                    row.length == 6 )
            {
                ndims = 2;
                expectedRowLength = 6;
            }

            if( row.length != expectedRowLength  )
                throw new IOException( "Invalid file - not enough columns" );

            double[] movingPt = new double[ ndims ];
            double[] targetPt = new double[ ndims ];

            int k = 2;
            for( int d = 0; d < ndims; d++ )
                movingPt[ d ] = Double.parseDouble( row[ k++ ]);

            for( int d = 0; d < ndims; d++ )
                targetPt[ d ] = Double.parseDouble( row[ k++ ]);

            {
                movingPts.add( movingPt );
                targetPts.add( targetPt );
            }
            numRowsTmp++;
        }

        List<RealPoint> moving_pts = new ArrayList<>();
        List<RealPoint> fixed_pts = new ArrayList<>();

        for (int indexLandmark = 0; indexLandmark<numRowsTmp; indexLandmark++) {

            RealPoint moving = new RealPoint(ndims);
            RealPoint fixed = new RealPoint(ndims);

            moving.setPosition(movingPts.get(indexLandmark));
            fixed.setPosition(targetPts.get(indexLandmark));

            moving_pts.add(moving);
            fixed_pts.add(fixed);
        }

        ThinplateSplineTransform tst = getTransform(moving_pts, fixed_pts, false);

        InvertibleRealTransform irt = new WrappedIterativeInvertibleRealTransform<>(tst);

        if (force3d&&(irt.numSourceDimensions()==2)) {
            return new Wrapped2DTransformAs3D(irt);
        } else {
            return irt;
        }
    }

    public static ThinplateSplineTransform getTransform(List<RealPoint> moving_pts, List<RealPoint> fixed_pts, boolean force2d) {
        int nbDimensions = moving_pts.get(0).numDimensions();
        int nbLandmarks = moving_pts.size();

        if (force2d) nbDimensions = 2;

        double[][] mPts = new double[nbDimensions][nbLandmarks];
        double[][] fPts = new double[nbDimensions][nbLandmarks];

        for (int i = 0;i<nbLandmarks;i++) {
            for (int d = 0; d<nbDimensions; d++) {
                fPts[d][i] = fixed_pts.get(i).getDoublePosition(d);
                //System.out.println("fPts["+d+"]["+i+"]=" +fPts[d][i]);
            }
            for (int d = 0; d<nbDimensions; d++) {
                mPts[d][i] = moving_pts.get(i).getDoublePosition(d);
                //System.out.println("mPts["+d+"]["+i+"]=" +mPts[d][i]);
            }
        }

        return new ThinplateSplineTransform(fPts, mPts);
    }

}
