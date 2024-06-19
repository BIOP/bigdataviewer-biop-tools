package ch.epfl.biop.sourceandconverter.register;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.exporter.CZTRange;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusGetter;
import ij.IJ;
import ij.ImagePlus;
import mpicbg.ij.FeatureTransform;
import mpicbg.ij.SIFT;
import mpicbg.imagefeatures.Feature;
import mpicbg.imagefeatures.FloatArray2DSIFT;
import mpicbg.models.AffineModel2D;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SIFTRegister<FT extends NativeType<FT> & NumericType<FT>,
        MT extends NativeType<MT> & NumericType<MT>> {

    SourceAndConverter<FT>[] sacs_fixed;
    SourceAndConverter<MT>[] sacs_moving;
    int levelMipmapFixed, levelMipmapMoving;
    final boolean invertFixed, invertMoving;
    int tpMoving,tpFixed;

    AffineTransform3D affineTransformOut;

    double px,py,pz,sx,sy;

    double pxSizeInCurrentUnit;

    boolean interpolate = false;

    String errorMessage = "";

    final FloatArray2DSIFT.Param paramSift;
    final float rod;
    final double maxEpsilon;
    final double minInlierRatio;
    final int minNumInliers;

    public SIFTRegister(SourceAndConverter<FT>[] sacs_fixed,
                                   int levelMipmapFixed,
                                   int tpFixed,
                                   boolean invertFixed,
                                   SourceAndConverter<MT>[] sacs_moving,
                                   int levelMipmapMoving,
                                   int tpMoving,
                                   boolean invertMoving,
                                   double pxSizeInCurrentUnit,
                                   double px,
                                   double py,
                                   double pz,
                                   double sx,
                                   double sy,
                                   FloatArray2DSIFT.Param paramSift,
                                   final float rod, // rod – Ratio of distances (closest/next closest match)
                        final double maxEpsilon,
                        final double minInlierRatio,
                        final int minNumInliers
                        ) {
        this.sacs_fixed = sacs_fixed;
        this.sacs_moving = sacs_moving;
        this.pxSizeInCurrentUnit = pxSizeInCurrentUnit;
        this.px = px;
        this.py = py;
        this.pz = pz;
        this.sx = sx;
        this.sy = sy;
        this.levelMipmapFixed = levelMipmapFixed;
        this.levelMipmapMoving = levelMipmapMoving;
        this.tpFixed = tpFixed;
        this.tpMoving = tpMoving;
        this.paramSift = paramSift;
        this.rod = rod;
        this.maxEpsilon = maxEpsilon;
        this.minNumInliers = minNumInliers;
        this.minInlierRatio = minInlierRatio;
        this.invertFixed = invertFixed;
        this.invertMoving = invertMoving;
    }

    public void setInterpolate(boolean interpolate) {
        this.interpolate = interpolate;
    }

    public boolean run() {

        // Check mipmap level
        levelMipmapFixed = Math.min(levelMipmapFixed, sacs_fixed[0].getSpimSource().getNumMipmapLevels()-1);
        levelMipmapMoving = Math.min(levelMipmapMoving, sacs_moving[0].getSpimSource().getNumMipmapLevels()-1);

        ImagePlus croppedMoving = getCroppedImage("Moving", sacs_moving, tpMoving, levelMipmapMoving);
        ImagePlus croppedFixed = getCroppedImage("Fixed", sacs_fixed, tpFixed, levelMipmapFixed);

        Source<MT> sMoving = sacs_moving[0].getSpimSource();
        Source<FT> sFixed = sacs_fixed[0].getSpimSource();

        AffineTransform3D at3D = new AffineTransform3D();
        at3D.identity();
        at3D.translate(-px,-py,-pz);

        AffineTransform3D atMoving = new AffineTransform3D();
        sMoving.getSourceTransform(tpMoving,levelMipmapMoving,atMoving);

        AffineTransform3D atFixed = new AffineTransform3D();
        sFixed.getSourceTransform(tpMoving,levelMipmapFixed,atFixed);

        at3D.identity();
        at3D.translate(-px,-py,-pz);

        //----------------- SIFT

        // FloatArray2DSIFT.Param paramSift = new FloatArray2DSIFT.Param();
        final FloatArray2DSIFT sift = new FloatArray2DSIFT( paramSift );
        final SIFT ijSIFT = new SIFT( sift );

        final List<Feature> fs1 = new ArrayList<>();
        final List<Feature> fs2 = new ArrayList<>();
        if (invertFixed) croppedFixed.getProcessor().invert();
        if (invertMoving) croppedMoving.getProcessor().invert();
        ijSIFT.extractFeatures( croppedFixed.getProcessor(), fs1 );
        IJ.log( fs1.size() + " features extracted for fixed image" );
        ijSIFT.extractFeatures( croppedMoving.getProcessor(), fs2 );
        IJ.log( fs2.size() + " features extracted for moving image" );
        IJ.log( "Identifying correspondence candidates using brute force ..." );
        final List<PointMatch> candidates = new ArrayList<>();
        FeatureTransform.matchFeatures( fs1, fs2, candidates, rod );

        IJ.log( candidates.size() + " potentially corresponding features identified." );
        IJ.log( "Filtering correspondence candidates by geometric consensus ..." );
        ArrayList<PointMatch> inliers = new ArrayList<>();

        AffineModel2D model = new AffineModel2D();

        try
        {
            model.filterRansac(
                    candidates,
                    inliers,
                    1000,
                    maxEpsilon,
                    minInlierRatio,
                    minNumInliers );
        }
        catch ( final NotEnoughDataPointsException e )
        {
            IJ.log( "No correspondences found." );
            return false;
        }

        PointMatch.apply( inliers, model );

        if (inliers.size()<minNumInliers) {
            IJ.log( "Not enough points found." );
            return false;
        }

        IJ.log( inliers.size() + " corresponding features with an average displacement of " +  PointMatch.meanDistance( inliers ) + "px identified." );
        IJ.log( "Estimated transformation model: " + model );

        final ArrayList< Point > points1 = new ArrayList<>();
        final ArrayList< Point > points2 = new ArrayList<>();

        PointMatch.sourcePoints( inliers, points1 );
        PointMatch.targetPoints( inliers, points2 );

        //----------------- END OF SIFT

        AffineTransform3D affine3D = convertToAffineTransform3D(model.createAffine());

        AffineTransform3D mPatchPixToRegPatchPix = new AffineTransform3D();
        mPatchPixToRegPatchPix.set(affine3D);

        AffineTransform3D nonRegisteredPatchTransformPixToGlobal = new AffineTransform3D();
        nonRegisteredPatchTransformPixToGlobal.identity();
        nonRegisteredPatchTransformPixToGlobal.scale(pxSizeInCurrentUnit);
        double cx = px;
        double cy = py;
        double cz = pz;

        nonRegisteredPatchTransformPixToGlobal.translate(cx,cy,cz);

        AffineTransform3D nonRegPatchGlobalToPix = nonRegisteredPatchTransformPixToGlobal.inverse();

        RealPoint nonRegUNorm = getMatrixAxis(nonRegPatchGlobalToPix,0);
        RealPoint nonRegVNorm = getMatrixAxis(nonRegPatchGlobalToPix,1);
        RealPoint nonRegWNorm = getMatrixAxis(nonRegPatchGlobalToPix,2);

        double u0PatchCoord = prodScal(getMatrixAxis(atMoving,0), nonRegUNorm );
        double v0PatchCoord = prodScal(getMatrixAxis(atMoving,0), nonRegVNorm );
        double w0PatchCoord = prodScal(getMatrixAxis(atMoving,0), nonRegWNorm );

        double u1PatchCoord = prodScal(getMatrixAxis(atMoving,1), nonRegUNorm );
        double v1PatchCoord = prodScal(getMatrixAxis(atMoving,1), nonRegVNorm );
        double w1PatchCoord = prodScal(getMatrixAxis(atMoving,1), nonRegWNorm );

        double u2PatchCoord = prodScal(getMatrixAxis(atMoving,2), nonRegUNorm );
        double v2PatchCoord = prodScal(getMatrixAxis(atMoving,2), nonRegVNorm );
        double w2PatchCoord = prodScal(getMatrixAxis(atMoving,2), nonRegWNorm );

        // New origin
        RealPoint newOrigin = new RealPoint(3);
        newOrigin.setPosition(atMoving.get(0,3),0);
        newOrigin.setPosition(atMoving.get(1,3),1);
        newOrigin.setPosition(atMoving.get(2,3),2);
        nonRegPatchGlobalToPix.apply(newOrigin,newOrigin);

        double u3PatchCoord = newOrigin.getDoublePosition(0);
        double v3PatchCoord = newOrigin.getDoublePosition(1);
        double w3PatchCoord = newOrigin.getDoublePosition(2);

        // New Location :
        RealPoint p0 = new RealPoint(u0PatchCoord, v0PatchCoord, w0PatchCoord);
        RealPoint p1 = new RealPoint(u1PatchCoord, v1PatchCoord, w1PatchCoord);
        RealPoint p2 = new RealPoint(u2PatchCoord, v2PatchCoord, w2PatchCoord);
        RealPoint p3 = new RealPoint(u3PatchCoord, v3PatchCoord, w3PatchCoord);

        // mCopy.set(nonRegisteredPatchTransformPixToGLobal);
        // Computes new location in real coordinates
        // Removes translation for this computation
        AffineTransform3D mPatchPixToGlobal = new AffineTransform3D();
        mPatchPixToGlobal.set(nonRegisteredPatchTransformPixToGlobal);
        mPatchPixToGlobal = nonRegisteredPatchTransformPixToGlobal.concatenate(mPatchPixToRegPatchPix);

        double shiftX = mPatchPixToGlobal.get(0,3);
        double shiftY = mPatchPixToGlobal.get(1,3);
        double shiftZ = mPatchPixToGlobal.get(2,3);

        mPatchPixToGlobal.set(0,0,3);
        mPatchPixToGlobal.set(0,1,3);
        mPatchPixToGlobal.set(0,2,3);

        mPatchPixToGlobal.apply(p0,p0);
        mPatchPixToGlobal.apply(p1,p1);
        mPatchPixToGlobal.apply(p2,p2);

        mPatchPixToGlobal.set(shiftX,0,3);
        mPatchPixToGlobal.set(shiftY,1,3);
        mPatchPixToGlobal.set(shiftZ,2,3);
        mPatchPixToGlobal.apply(p3,p3);

        double[] newMatrix = new double[12];
        newMatrix[0] = p0.getDoublePosition(0);
        newMatrix[4] = p0.getDoublePosition(1);
        newMatrix[8] = p0.getDoublePosition(2);

        newMatrix[1] = p1.getDoublePosition(0);
        newMatrix[5] = p1.getDoublePosition(1);
        newMatrix[9] = p1.getDoublePosition(2);

        newMatrix[2] = p2.getDoublePosition(0);
        newMatrix[6] = p2.getDoublePosition(1);
        newMatrix[10] = p2.getDoublePosition(2);

        newMatrix[3] = p3.getDoublePosition(0);
        newMatrix[7] = p3.getDoublePosition(1);
        newMatrix[11] = p3.getDoublePosition(2);

        affineTransformOut = new AffineTransform3D();

        affineTransformOut.set(newMatrix);

        affineTransformOut = atMoving.concatenate(affineTransformOut.inverse());

        return true;
    }

    private <T extends NativeType<T> & NumericType<T>> ImagePlus getCroppedImage(String name, SourceAndConverter<T>[] sacs, int tp, int level) {

        // Fetch cropped images from source -> resample sources
        FinalRealInterval window = new FinalRealInterval(new double[]{px,py,pz}, new double[]{px+sx, py+sy, pz+pxSizeInCurrentUnit});

        SourceAndConverter<?> model = new EmptySourceAndConverterCreator("model",window,pxSizeInCurrentUnit,pxSizeInCurrentUnit,pxSizeInCurrentUnit).get();

        SourceResampler<T> resampler = new SourceResampler<>(null,
                model,model.getSpimSource().getName(), false, false, interpolate, level
        );

        List<SourceAndConverter<T>> resampled =
                Arrays.stream(sacs)
                        .map(resampler)
                        .collect(Collectors.toList());

        List<Integer> channels = new ArrayList<>(sacs.length);
        for (int i = 0; i< sacs.length;i++) {
            channels.add(i);
        }
        List<Integer> slices = new ArrayList<>();
        slices.add(0);
        List<Integer> timepoints = new ArrayList<>();
        timepoints.add(tp);

        CZTRange range = new CZTRange(channels, slices, timepoints);

        return ImagePlusGetter.getImagePlus(name,resampled,0,range,false,false,false,null);
    }

    public static double prodScal(RealPoint pt1, RealPoint pt2) {
        return pt1.getDoublePosition(0)*pt2.getDoublePosition(0)+
                pt1.getDoublePosition(1)*pt2.getDoublePosition(1)+
                pt1.getDoublePosition(2)*pt2.getDoublePosition(2);
    }

    public RealPoint getMatrixAxis(AffineTransform3D at3D, int axis) {
        RealPoint pt = new RealPoint(3);
        double[] m = at3D.getRowPackedCopy();
        pt.setPosition(m[0+axis],0);
        pt.setPosition(m[4+axis],1);
        pt.setPosition(m[8+axis],2);
        return pt;
    }

    public SourceAndConverter[] getRegisteredSacs() {
        SourceAndConverter[] out = new SourceAndConverter[sacs_moving.length];
        SourceAffineTransformer sat = new SourceAffineTransformer(null, affineTransformOut);
        for (int iCh=0;iCh< sacs_moving.length;iCh++) {
            out[iCh] = sat.apply(sacs_moving[iCh]);
        }
        return out;
    }

    public AffineTransform3D getAffineTransform() {
        return affineTransformOut;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    private static AffineTransform3D convertToAffineTransform3D(AffineTransform at) {
        // Create a new AffineTransform3D object
        AffineTransform3D at3d = new AffineTransform3D();

        // Get the matrix elements from the 2D AffineTransform
        double[] matrix2D = new double[6];
        at.getMatrix(matrix2D);

        // Map the 2D matrix to a 3D matrix
        double[] matrix3D = new double[12];

        /*
        this.a.m00 = values[0];
        this.a.m01 = values[1];
        this.a.m02 = values[2];
        this.a.m03 = values[3];
        this.a.m10 = values[4];
        this.a.m11 = values[5];
        this.a.m12 = values[6];
        this.a.m13 = values[7];
        this.a.m20 = values[8];
        this.a.m21 = values[9];
        this.a.m22 = values[10];
        this.a.m23 = values[11];
         */
        // Initialize to identity matrix
        matrix3D[0] = matrix2D[0]; // m00
        matrix3D[1] = matrix2D[2]; // m01
        matrix3D[2] = 0;           // m02
        matrix3D[3] = matrix2D[4]; // m03 - Translation X
        matrix3D[4] = matrix2D[1]; // m10
        matrix3D[5] = matrix2D[3]; // m11
        matrix3D[6] = 0;           // m12
        matrix3D[7] = matrix2D[5]; // m13 - Translation Y
        matrix3D[8] = 0;           // m20
        matrix3D[9] = 0;           // m21
        matrix3D[10] = 1;          // m22
        matrix3D[11] = 0;          // m23 - Translation Z

        // Set the 3D matrix to the AffineTransform3D object
        at3d.set(matrix3D);

        return at3d;
    }
}
