package ch.epfl.biop.sourceandconverter.register;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.fiji.imageplusutils.ImagePlusFunctions;
import ch.epfl.biop.sourceandconverter.exporter.CZTRange;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusGetter;
import ch.epfl.biop.wrappers.elastix.DefaultElastixTask;
import ch.epfl.biop.wrappers.elastix.ElastixTask;
import ch.epfl.biop.wrappers.elastix.RegisterHelper;
import ch.epfl.biop.wrappers.transformix.DefaultTransformixTask;
import ch.epfl.biop.wrappers.transformix.TransformHelper;
import ch.epfl.biop.wrappers.transformix.TransformixTask;
import ij.IJ;
import ij.ImagePlus;
import itc.converters.ElastixAffine2DToAffineTransform3D;
import itc.converters.ElastixEuler2DToAffineTransform3D;
import itc.transforms.elastix.ElastixAffineTransform2D;
import itc.transforms.elastix.ElastixEulerTransform2D;
import itc.transforms.elastix.ElastixTransform;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Elastix2DAffineRegister<FT extends NativeType<FT> & NumericType<FT>,
        MT extends NativeType<MT> & NumericType<MT>> {

    SourceAndConverter<FT>[] sacs_fixed;
    SourceAndConverter<MT>[] sacs_moving;
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

    Supplier<TransformixTask> tt = () -> new DefaultTransformixTask();

    ElastixTask et = new DefaultElastixTask();

    String errorMessage = "";

    public Elastix2DAffineRegister(SourceAndConverter<FT>[] sacs_fixed,
                                   int levelMipmapFixed,
                                   int tpFixed,
                                   SourceAndConverter<MT>[] sacs_moving,
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
        this.background_offset_value_moving = background_offset_value_moving;
        this.background_offset_value_fixed = background_offset_value_fixed;
        this.showResultIJ1 = showResultIJ1;
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

        if (background_offset_value_moving!=0) {
            System.err.println("Ignored background_offset_value_moving");
        }

        at3D.identity();
        at3D.translate(-px,-py,-pz);

        if (background_offset_value_fixed!=0) {
            System.err.println("Ignored background_offset_value_fixed");
        }

        rh.setMovingImage(croppedMoving);
        rh.setFixedImage(croppedFixed);

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
                if (croppedMoving.getBitDepth() != 24) {
                    croppedFixed.show();
                    ImagePlus transformedImage = ImagePlusFunctions.splitApplyRecompose(
                            imp -> {
                                TransformHelper th = new TransformHelper();
                                th.verbose();
                                th.setTransformFile(rh);
                                th.setImage(imp);
                                th.transform(tt.get());
                                return ((ImagePlus) (th.getTransformedImage().to(ImagePlus.class)));
                            }, croppedMoving);

                    transformedImage.show();

                    IJ.run(croppedFixed, "Enhance Contrast", "saturated=0.35");
                    IJ.run(transformedImage, "Enhance Contrast", "saturated=0.35");
                    IJ.run(croppedFixed, "32-bit", "");
                    if ((transformedImage.getNChannels()==1)&&(croppedFixed.getNChannels()==1)) {
                        IJ.run((ImagePlus) null, "Merge Channels...", "c1=Transformed_Moving c2=Fixed create");
                    }

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

    public static void normalize(RealPoint pt) {
        double norm = Math.sqrt(prodScal(pt,pt));
        pt.setPosition(pt.getDoublePosition(0)/norm,0);
        pt.setPosition(pt.getDoublePosition(1)/norm,1);
        pt.setPosition(pt.getDoublePosition(2)/norm,2);
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
}
