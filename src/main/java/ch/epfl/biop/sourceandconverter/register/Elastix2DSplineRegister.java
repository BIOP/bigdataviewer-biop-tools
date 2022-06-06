package ch.epfl.biop.sourceandconverter.register;

import bdv.util.RealCropper;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.fiji.imageplusutils.ImagePlusFunctions;
import ch.epfl.biop.java.utilities.roi.ConvertibleRois;
import ch.epfl.biop.java.utilities.roi.types.RealPointList;
import ch.epfl.biop.wrappers.elastix.DefaultElastixTask;
import ch.epfl.biop.wrappers.elastix.*;
import ch.epfl.biop.wrappers.transformix.DefaultTransformixTask;
import ch.epfl.biop.wrappers.transformix.RemoteTransformixTask;
import ch.epfl.biop.wrappers.transformix.TransformHelper;
import ch.epfl.biop.wrappers.transformix.TransformixTask;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;
import itc.transforms.elastix.ElastixTransform;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.*;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Elastix2DSplineRegister {

    SourceAndConverter[] sacs_fixed, sacs_moving;
    int levelMipmapFixed, levelMipmapMoving;
    int tpMoving,tpFixed;

    RegisterHelper rh = new RegisterHelper();

    RealTransform realTransformOut;

    RealTransform realTransformInverseOut;

    double px,py,pz,sx,sy;

    double pxSizeInCurrentUnit;

    boolean interpolate = false;

    boolean showResultIJ1;

    int nbControlPointsX;

    int numberOfIterationPerScale;

    TransformixTask tt = new DefaultTransformixTask();

    ElastixTask et = new DefaultElastixTask();

    double background_offset_value_moving = 0;

    double background_offset_value_fixed = 0;

    String errorMessage = "";

    public void setRegistrationServer(String serverURL) {
        tt = new RemoteTransformixTask(serverURL);
        et = new RemoteElastixTask(serverURL);
    }

    public Elastix2DSplineRegister(SourceAndConverter[] sacs_fixed,
                                   int levelMipmapFixed,
                                   int tpFixed,
                                   SourceAndConverter[] sacs_moving,
                                   int levelMipmapMoving,
                                   int tpMoving,
                                   //RegisterHelper rh,
                                   int nbControlPointsX,
                                   double pxSizeInCurrentUnit,
                                   double px,
                                   double py,
                                   double pz,
                                   double sx,
                                   double sy,
                                   int numberOfIterationPerScale,
                                   double background_offset_value_moving,
                                   double background_offset_value_fixed,
                                   boolean showResultIJ1) {
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
        this.showResultIJ1 = showResultIJ1;
        this.nbControlPointsX = nbControlPointsX;
        this.background_offset_value_fixed = background_offset_value_fixed;
        this.background_offset_value_moving = background_offset_value_moving;
        this.numberOfIterationPerScale = numberOfIterationPerScale;
    }

    public void setInterpolate(boolean interpolate) {
        this.interpolate = interpolate;
    }

    public boolean run() {

        SourceAndConverter sac_fixed = sacs_fixed[0];
        SourceAndConverter sac_moving = sacs_moving[0];

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
        //impM.show();
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
        //impF.show();
        impF = new Duplicator().run(impF); // Virtual messes up the process, don't know why

        if (background_offset_value_fixed!=0) {
            impF.getProcessor().subtract(background_offset_value_fixed);
        }

        rh.setMovingImage(impM);
        rh.setFixedImage(impF);

        RegistrationParameters rp = new RegParamBSpline_Default();
        rp.AutomaticScalesEstimation = true;
        double maxSize = Math.max(sx/pxSizeInCurrentUnit,sy/pxSizeInCurrentUnit);
        int nScales = 0;

        while (Math.pow(2,nScales)<maxSize) {
            nScales++;
        }
        //System.out.println("nScales = "+nScales);
        rp.NumberOfResolutions = nScales-2;
        rp.BSplineInterpolationOrder = 1;
        rp.MaximumNumberOfIterations = numberOfIterationPerScale;
        //rp.Metric = "AdvancedNormalizedCorrelation";
        /*rp.AutomaticScalesEstimation = true;
        rp.NumberOfResolutions = 8;
        rp.BSplineInterpolationOrder = 1;
        rp.MaximumNumberOfIterations = 100;*/
        //rp.FixedImagePyramid = "FixedRecursiveImagePyramid";
        //rp.MovingImagePyramid = "MovingRecursiveImagePyramid";
        //rp.NewSamplesEveryIteration = true;
        //rp.Optimizer = "AdaptiveStochasticGradientDescent";
        //rp.ASGDParameterEstimationMethod = "DisplacementDistribution";*/
        //rp.MaximumStepLength = 20f;

        int nbControlPointsY = (int)((double)nbControlPointsX/(double)(impF.getWidth())*(double) (impF.getHeight()));

        if (nbControlPointsY<2) nbControlPointsY = 2;

        double dX = ((double)(impF.getWidth())/(double)(nbControlPointsX));
        double dY = ((double)(impF.getHeight())/(double)(nbControlPointsY));

        // TODO : handle 0 and 1 pixels size image cases

        rp.FinalGridSpacingInVoxels = (int) dX;

        rh.addTransform(rp);
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
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        if (showResultIJ1) {
            synchronized (Elastix2DSplineRegister.class) {
                impF.show();
                ImagePlus transformedImage = ImagePlusFunctions.splitApplyRecompose(
                        imp -> {
                            TransformHelper th = new TransformHelper();
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
            }
        }

        List<RealPoint> fixedImageGridPointsInFixedImageCoordinates = new ArrayList<>();

        for (int xi = 0; xi<nbControlPointsX; xi ++) {
            for (int yi = 0; yi<nbControlPointsY; yi ++) {
                RealPoint pt = new RealPoint(2);
                pt.setPosition(new double[]{xi*dX+dX/2.0, yi*dY+dY/2.0});
                fixedImageGridPointsInFixedImageCoordinates.add(pt);
            }
        }

        TransformHelper th = new TransformHelper();
        th.setTransformFile(rh);

        ConvertibleRois rois = new ConvertibleRois();
        RealPointList rpl = new RealPointList(fixedImageGridPointsInFixedImageCoordinates);

        rois.set(rpl);

        th.setRois(rois);
        th.transform(tt);
        ConvertibleRois tr_rois = th.getTransformedRois();

        RealPointList rpl_tr = (RealPointList) tr_rois.to(RealPointList.class);

        // Now : transform the coordinates from the fixed image to real space:

        // Let's try something different for the transformation
        // This
        AffineTransform3D nonRegisteredPatchTransformPixToGLobal = new AffineTransform3D();
        nonRegisteredPatchTransformPixToGLobal.identity();
        nonRegisteredPatchTransformPixToGLobal.scale(pxSizeInCurrentUnit);
        double cx = px;
        double cy = py;
        double cz = pz;

        nonRegisteredPatchTransformPixToGLobal.translate(cx,cy,cz);

        List<RealPoint> fixedImageGridPointsInGlobalCoordinates =
                fixedImageGridPointsInFixedImageCoordinates.stream()
                .map(pt -> {
                    double[] newPos = new double[3];double[] oldPos = new double[3];
                    oldPos[0] = pt.positionAsDoubleArray()[0];
                    oldPos[1] = pt.positionAsDoubleArray()[1];
                    oldPos[2] = 0;
                    nonRegisteredPatchTransformPixToGLobal.apply(oldPos, newPos);
                    return new RealPoint(newPos);
                })
                .collect(Collectors.toList());

        List<RealPoint> movingImageGridPointsInGlobalCoordinates =
                rpl_tr.ptList.stream()
                        .map(pt -> {
                            double[] newPos = new double[3];
                            double[] oldPos = new double[3];
                            oldPos[0] = pt.positionAsDoubleArray()[0];
                            oldPos[1] = pt.positionAsDoubleArray()[1];
                            oldPos[2] = 0;
                            nonRegisteredPatchTransformPixToGLobal.apply(oldPos, newPos);

                            return new RealPoint(newPos);
                        })
                        .collect(Collectors.toList());

        double[][] coordsFixed = new double[2][nbControlPointsX*nbControlPointsY];
        double[][] coordsMoving = new double[2][nbControlPointsX*nbControlPointsY];

        for (int xi = 0; xi<nbControlPointsX; xi ++) {
            for (int yi = 0; yi<nbControlPointsY; yi ++) {
                int idx = yi*nbControlPointsX+xi;
                double[] fixed_coords = fixedImageGridPointsInGlobalCoordinates.get(idx).positionAsDoubleArray();
                double[] moving_coords = movingImageGridPointsInGlobalCoordinates.get(idx).positionAsDoubleArray();

                coordsFixed[0][idx] = fixed_coords[0];
                coordsFixed[1][idx] = fixed_coords[1];
                coordsMoving[0][idx] = moving_coords[0];
                coordsMoving[1][idx] = moving_coords[1];
            }
        }

        InvertibleRealTransform invTransform =
                new WrappedIterativeInvertibleRealTransform<>(new ThinplateSplineTransform( coordsFixed, coordsMoving ));

        realTransformOut =
                new Wrapped2DTransformAs3D(invTransform);

        InvertibleRealTransform invTransformPatch =
                new WrappedIterativeInvertibleRealTransform<>(new ThinplateSplineTransform( coordsMoving, coordsFixed ));

        realTransformInverseOut = new Wrapped2DTransformAs3D(invTransformPatch);

        return true; // success

    }

    public SourceAndConverter[] getRegisteredSacs() {
        SourceAndConverter[] out = new SourceAndConverter[sacs_moving.length];
        SourceRealTransformer srt = new SourceRealTransformer(null, realTransformOut);
        for (int iCh=0;iCh< sacs_moving.length;iCh++) {
            out[iCh] = srt.apply(sacs_moving[iCh]);
        }
        return out;
    }

    public RealTransform getRealTransform() {
        return realTransformOut;
    }

    public RealTransform getRealTransformInverse() {
        return realTransformInverseOut;
    }

    public void setRegistrationInfo(String taskInfo) {
        rh.setExtraRegisterInfo(taskInfo);
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
