package ch.epfl.biop.sourceandconverter.register;

import bdv.util.RealCropper;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.fiji.imageplusutils.ImagePlusFunctions;
import ch.epfl.biop.java.utilities.roi.ConvertibleRois;
import ch.epfl.biop.java.utilities.roi.types.RealPointList;
import ch.epfl.biop.sourceandconverter.exporter.CZTRange;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusGetter;
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
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Elastix2DSplineRegister<FT extends NativeType<FT> & NumericType<FT>,
        MT extends NativeType<MT> & NumericType<MT>> {

    SourceAndConverter<FT>[] sacs_fixed;
    SourceAndConverter<MT>[] sacs_moving;
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

    Supplier<TransformixTask> tt = () -> new DefaultTransformixTask();

    ElastixTask et = new DefaultElastixTask();

    double background_offset_value_moving = 0;

    double background_offset_value_fixed = 0;

    String errorMessage = "";

    public void setRegistrationServer(String serverURL) {
        tt = () -> new RemoteTransformixTask(serverURL);
        et = new RemoteElastixTask(serverURL);
    }

    public Elastix2DSplineRegister(SourceAndConverter<FT>[] sacs_fixed,
                                   int levelMipmapFixed,
                                   int tpFixed,
                                   SourceAndConverter<MT>[] sacs_moving,
                                   int levelMipmapMoving,
                                   int tpMoving,
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

        // Check mipmap level
        levelMipmapFixed = Math.min(levelMipmapFixed, sacs_fixed[0].getSpimSource().getNumMipmapLevels()-1);
        levelMipmapMoving = Math.min(levelMipmapMoving, sacs_moving[0].getSpimSource().getNumMipmapLevels()-1);

        ImagePlus croppedMoving = getCroppedImage("Moving", sacs_moving, tpMoving, levelMipmapMoving);
        ImagePlus croppedFixed = getCroppedImage("Fixed", sacs_fixed, tpFixed, levelMipmapFixed);

        // Fetch cropped images from source

        Source<MT> sMoving = sacs_moving[0].getSpimSource();
        Source<FT> sFixed = sacs_fixed[0].getSpimSource();

        AffineTransform3D at3D = new AffineTransform3D();
        at3D.identity();
        at3D.translate(-px,-py,-pz);
        FinalRealInterval fi = new FinalRealInterval(new double[]{0,0,0}, new double[]{sx, sy, 0});

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

        int nbControlPointsY = (int)((double)nbControlPointsX/(double)(croppedFixed.getWidth())*(double) (croppedFixed.getHeight()));

        if (nbControlPointsY<2) nbControlPointsY = 2;

        double dX = ((double)(croppedFixed.getWidth())/(double)(nbControlPointsX));
        double dY = ((double)(croppedFixed.getHeight())/(double)(nbControlPointsY));


        RegistrationParameters rp;

        if (sacs_fixed.length>1) {
            if (sacs_fixed.length==sacs_moving.length) {
                RegistrationParameters[] rps = new RegistrationParameters[sacs_fixed.length];
                for (int iCh = 0; iCh<sacs_fixed.length;iCh++) {
                    rps[iCh] = getRegistrationParameters((int) dX);
                }
                rp = RegistrationParameters.combineRegistrationParameters(rps);
            } else {
                System.err.println("Cannot perform multichannel registration : non identical number of channels between moving and fixed sources.");
                rp = getRegistrationParameters((int) dX);
            }
        } else {
            rp = getRegistrationParameters((int) dX);
        }

        rh.addTransform(rp);
        try {
            rh.align(et);
        } catch (Exception e) {
            errorMessage = e.getMessage();
            e.printStackTrace();
            return false;
        }

        if (showResultIJ1) {
            synchronized (Elastix2DSplineRegister.class) {
                croppedFixed.show();
                ImagePlus transformedImage = ImagePlusFunctions.splitApplyRecompose(
                        imp -> {
                            TransformHelper th = new TransformHelper();
                            th.setTransformFile(rh);
                            th.setImage(imp);
                            th.transform(tt.get());
                            return ((ImagePlus) (th.getTransformedImage().to(ImagePlus.class)));
                        }
                        , croppedMoving);

                transformedImage.show();

                IJ.run(croppedFixed, "Enhance Contrast", "saturated=0.35");
                IJ.run(transformedImage, "Enhance Contrast", "saturated=0.35");
                IJ.run(croppedFixed, "32-bit", "");
                if ((transformedImage.getNChannels()==1)&&(croppedFixed.getNChannels()==1)) {
                    IJ.run((ImagePlus) null, "Merge Channels...", "c1=Transformed_DUP_Moving c2=DUP_Fixed create");
                }
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
        th.transform(tt.get());
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

    private RegistrationParameters getRegistrationParameters(int gridSpacing) {
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


        // TODO : handle 0 and 1 pixels size image cases

        rp.FinalGridSpacingInVoxels = gridSpacing;
        return rp;
    }


    private<T extends NativeType<T> & NumericType<T>> ImagePlus getCroppedImage(String name, SourceAndConverter<T>[] sacs, int tp, int level) {

        // Fetch cropped images from source -> resample sources
        FinalRealInterval window = new FinalRealInterval(new double[]{px,py,pz}, new double[]{px+sx, py+sy, pz+pxSizeInCurrentUnit});

        SourceAndConverter model = new EmptySourceAndConverterCreator("model",window,pxSizeInCurrentUnit,pxSizeInCurrentUnit,pxSizeInCurrentUnit).get();

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

        return ImagePlusGetter.getImagePlus(name, resampled,0,range,false,false,false,null);
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
