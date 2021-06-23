package ch.epfl.biop.sourceandconverter.register;

import bdv.util.RealCropper;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.fiji.imageplusutils.ImagePlusFunctions;
import ch.epfl.biop.wrappers.elastix.DefaultElastixTask;
import ch.epfl.biop.wrappers.elastix.ElastixTask;
import ch.epfl.biop.wrappers.elastix.RegisterHelper;
import ch.epfl.biop.wrappers.elastix.RemoteElastixTask;
import ch.epfl.biop.wrappers.transformix.DefaultTransformixTask;
import ch.epfl.biop.wrappers.transformix.RemoteTransformixTask;
import ch.epfl.biop.wrappers.transformix.TransformHelper;
import ch.epfl.biop.wrappers.transformix.TransformixTask;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;
import itc.converters.ElastixAffine2DToAffineTransform3D;
import itc.converters.ElastixEuler2DToAffineTransform3D;
import itc.transforms.elastix.ElastixAffineTransform2D;
import itc.transforms.elastix.ElastixEulerTransform2D;
import itc.transforms.elastix.ElastixTransform;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;

import java.io.File;
import java.io.IOException;

public class Elastix2DAffineRegister {

    SourceAndConverter sac_fixed, sac_moving;
    int levelMipmapFixed, levelMipmapMoving;
    int tpMoving,tpFixed;

    RegisterHelper rh;

    AffineTransform3D affineTransformOut;

    double px,py,pz,sx,sy;

    double pxSizeInCurrentUnit;

    boolean interpolate = false;

    boolean showResultIJ1;

    double background_offset_value_moving = 0;

    double background_offset_value_fixed = 0;

    TransformixTask tt = new DefaultTransformixTask();

    ElastixTask et = new DefaultElastixTask();

    String errorMessage = "";

    public void setRegistrationServer(String serverURL) {
        tt = new RemoteTransformixTask(serverURL);
        et = new RemoteElastixTask(serverURL);
    }

    public Elastix2DAffineRegister(SourceAndConverter sac_fixed,
                                   int levelMipmapFixed,
                                   int tpFixed,
                                   SourceAndConverter sac_moving,
                                   int levelMipmapMoving,
                                   int tpMoving,
                                   RegisterHelper rh,
                                   double pxSizeInCurrentUnit,
                                   double px,
                                   double py,
                                   double pz,
                                   double sx,
                                   double sy,
                                   double background_offset_value_moving,
                                   double background_offset_value_fixed,
                                   boolean showResultIJ1) {
        this.rh = rh;
        this.sac_fixed = sac_fixed;
        this.sac_moving = sac_moving;
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
        this.background_offset_value_moving = background_offset_value_moving;
        this.background_offset_value_fixed = background_offset_value_fixed;
        this.showResultIJ1 = showResultIJ1;
    }

    public void setInterpolate(boolean interpolate) {
        this.interpolate = interpolate;
    }

    public boolean run() {

        // Check mipmap level
        levelMipmapFixed = Math.min(levelMipmapFixed, sac_fixed.getSpimSource().getNumMipmapLevels()-1);
        levelMipmapMoving = Math.min(levelMipmapMoving, sac_moving.getSpimSource().getNumMipmapLevels()-1);

        // Interpolation switch
        Interpolation interpolation;
        if (interpolate) {
            interpolation = Interpolation.NLINEAR;
        } else {
            interpolation = Interpolation.NEARESTNEIGHBOR;
        }

        // Fetch cropped images from source

        Source sMoving = sac_moving.getSpimSource();
        Source sFixed = sac_fixed.getSpimSource();

        // Get real random accessible from the source
        final RealRandomAccessible ipMovingimg = sMoving.getInterpolatedSource(tpMoving, levelMipmapMoving, interpolation);
        final RealRandomAccessible ipFixedimg = sFixed.getInterpolatedSource(tpFixed, levelMipmapFixed, interpolation);

        AffineTransform3D at3D = new AffineTransform3D();
        at3D.identity();
        at3D.translate(-px,-py,-pz);
        FinalRealInterval fi = new FinalRealInterval(new double[]{0,0,0}, new double[]{sx, sy, 0});

        AffineTransform3D atMoving = new AffineTransform3D();
        sMoving.getSourceTransform(tpMoving,levelMipmapMoving,atMoving);

        AffineTransform3D atFixed = new AffineTransform3D();
        sFixed.getSourceTransform(tpMoving,levelMipmapFixed,atFixed);

        AffineTransform3D movat = at3D.concatenate(atMoving);

        RandomAccessibleInterval viewMoving = RealCropper.getCroppedSampledRRAI(ipMovingimg,
                movat,fi,pxSizeInCurrentUnit,pxSizeInCurrentUnit,pxSizeInCurrentUnit);

        ImagePlus impM = ImageJFunctions.wrap(viewMoving, "Moving");

        impM = new Duplicator().run(impM); // Virtual messes up the process, don't know why

        if (background_offset_value_moving!=0) {
            impM.getProcessor().subtract(background_offset_value_moving);
        }

        at3D.identity();
        at3D.translate(-px,-py,-pz);
        AffineTransform3D fixat = at3D.concatenate(atFixed);
        RandomAccessibleInterval viewFixed = RealCropper.getCroppedSampledRRAI(ipFixedimg,
                fixat,fi,pxSizeInCurrentUnit,pxSizeInCurrentUnit,pxSizeInCurrentUnit);
        ImagePlus impF = ImageJFunctions.wrap(viewFixed, "Fixed");
        impF = new Duplicator().run(impF); // Virtual messes up the process, don't know why

        if (background_offset_value_fixed!=0) {
            impF.getProcessor().subtract(background_offset_value_fixed);
        }

        rh.setMovingImage(impM);
        rh.setFixedImage(impF);

        try {
            rh.align(et);
        } catch (Exception e) {
            errorMessage = e.getMessage();
            e.printStackTrace();
            return false;
        }

        File fTransform = new File(rh.getFinalTransformFile());

        ElastixTransform et;
        try {
            et = ElastixTransform.load(fTransform);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        AffineTransform3D affine3D;

        if (et.getClass() == ElastixAffineTransform2D.class) {
            affine3D = ElastixAffine2DToAffineTransform3D.convert((ElastixAffineTransform2D)et);
        } else if (et.getClass() == ElastixEulerTransform2D.class) {
            affine3D = ElastixEuler2DToAffineTransform3D.convert((ElastixEulerTransform2D)et);
        } else {
            System.err.println("Error : elastix transform class expected : "+
                ElastixAffineTransform2D.class.getSimpleName()+" or "+
                ElastixEulerTransform2D.class.getSimpleName()+
                " vs obtained : "+et.getClass().getSimpleName());
            return false;
        }

        AffineTransform3D mPatchPixToRegPatchPix = new AffineTransform3D();
        mPatchPixToRegPatchPix.set(affine3D);

        if (showResultIJ1) {
            synchronized (Elastix2DAffineRegister.class) {
                if (impM.getBitDepth() != 24) {
                    impF.show();
                    ImagePlus transformedImage = ImagePlusFunctions.splitApplyRecompose(
                            imp -> {
                                TransformHelper th = new TransformHelper();
                                //th.verbose();
                                th.setTransformFile(rh);
                                th.setImage(imp);
                                th.transform(tt);
                                return ((ImagePlus) (th.getTransformedImage().to(ImagePlus.class)));
                            }
                            , impM);

                    transformedImage.show();

                    IJ.run(impF, "Enhance Contrast", "saturated=0.35");
                    IJ.run(transformedImage, "Enhance Contrast", "saturated=0.35");
                    IJ.run(impF, "32-bit", "");
                    IJ.run((ImagePlus) null, "Merge Channels...", "c1=Transformed_DUP_Moving c2=DUP_Fixed create");

                } else {
                    System.err.println("Cannot transform RGB imagej1 images.");
                }
            }
        }

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

        return true; // success

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

    public static void normalize(RealPoint pt) {
        double norm = Math.sqrt(prodScal(pt,pt));
        pt.setPosition(pt.getDoublePosition(0)/norm,0);
        pt.setPosition(pt.getDoublePosition(1)/norm,1);
        pt.setPosition(pt.getDoublePosition(2)/norm,2);
    }

    public SourceAndConverter getRegisteredSac() {
        return new SourceAffineTransformer(null, affineTransformOut).apply(sac_moving);
    }

    public AffineTransform3D getAffineTransform() {
        return affineTransformOut;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
