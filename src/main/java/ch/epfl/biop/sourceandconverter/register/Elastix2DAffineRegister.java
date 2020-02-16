package ch.epfl.biop.sourceandconverter.register;

import bdv.util.RealCropper;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.fiji.imageplusutils.ImagePlusFunctions;
import ch.epfl.biop.wrappers.elastix.RegisterHelper;
import ch.epfl.biop.wrappers.transformix.TransformHelper;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;
import itc.transforms.elastix.ElastixAffineTransform2D;
import itc.transforms.elastix.ElastixTransform;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;

import java.io.File;
import java.io.IOException;

public class Elastix2DAffineRegister implements Runnable {

    SourceAndConverter sac_fixed, sac_moving, sac_registered;
    int levelMipmapFixed, levelMipmapMoving;
    int tpMoving,tpFixed;

    RegisterHelper rh;

    AffineTransform3D affineTransformOut;

    double px,py,pz,sx,sy;

    double pxSizeInCurrentUnit;

    boolean interpolate = false;

    boolean showResultIJ1 = false;

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
        this.showResultIJ1 = showResultIJ1;
    }

    public void setInterpolate(boolean interpolate) {
        this.interpolate = interpolate;
    }

    @Override
    public void run() {

        // Interpolation switch
        Interpolation interpolation;
        if (interpolate) {
            interpolation = Interpolation.NLINEAR;
        } else {
            interpolation = Interpolation.NEARESTNEIGHBOR;
        }

        // Fetch cropped images from source

        Source sMoving = sac_moving.getSpimSource();//bdv_h_moving.getViewerPanel().getState().getSources().get(idxMovingSource).getSpimSource();
        Source sFixed = sac_fixed.getSpimSource();//bdv_h_fixed.getViewerPanel().getState().getSources().get(idxFixedSource).getSpimSource();

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


        at3D.identity();
        at3D.translate(-px,-py,-pz);
        AffineTransform3D fixat = at3D.concatenate(atFixed);
        RandomAccessibleInterval viewFixed = RealCropper.getCroppedSampledRRAI(ipFixedimg,
                fixat,fi,pxSizeInCurrentUnit,pxSizeInCurrentUnit,pxSizeInCurrentUnit);
        ImagePlus impF = ImageJFunctions.wrap(viewFixed, "Fixed");
        //impF.show();
        impF = new Duplicator().run(impF); // Virtual messes up the process, don't know why


        rh.setMovingImage(impM);
        rh.setFixedImage(impF);

        rh.align();
        //rh.to(RHZipFile.class);

        File fTransform = new File(rh.getFinalTransformFile());

        ElastixTransform et = null;
        try {
            et = ElastixTransform.load(fTransform);
        } catch (IOException e) {
            e.printStackTrace();
        }

        assert et.getClass()== ElastixAffineTransform2D.class;

        Double[] m2D = et.TransformParameters;

        for (Double d : m2D) {
            System.out.println(d);
        }

        final AffineTransform3D affine3D = new AffineTransform3D();
        affine3D.set(new double[][] {
                {m2D[0], m2D[1], 0,   m2D[4]},
                {m2D[2], m2D[3], 0,   m2D[5]},
                {0     ,      0, 1,        0},
                {0.    ,      0, 0,        1}});

        at3D.identity();
        at3D.translate(-px,-py,-pz);
        at3D.scale(1./pxSizeInCurrentUnit, 1./pxSizeInCurrentUnit, 1./pxSizeInCurrentUnit);


        AffineTransform3D transformInRealCoordinates = new AffineTransform3D();
        transformInRealCoordinates.identity();
        transformInRealCoordinates.concatenate(at3D.inverse());
        transformInRealCoordinates.concatenate(affine3D);
        transformInRealCoordinates.concatenate(at3D);

        affineTransformOut = transformInRealCoordinates.inverse();

        if (showResultIJ1) {
            impF.show();
            ImagePlus transformedImage = ImagePlusFunctions.splitApplyRecompose(
                    imp -> {
                        TransformHelper th = new TransformHelper();
                        th.setTransformFile(rh);
                        th.setImage(imp);
                        th.transform();
                        return ((ImagePlus) (th.getTransformedImage().to(ImagePlus.class)));
                    }
                    ,impM);

            transformedImage.show();

            IJ.run(impF, "Enhance Contrast", "saturated=0.35");
            IJ.run(transformedImage, "Enhance Contrast", "saturated=0.35");
            IJ.run(impF, "32-bit", "");
            IJ.run((ImagePlus) null, "Merge Channels...", "c1=Transformed_DUP_Moving c2=DUP_Fixed create");
        }
    }

    public SourceAndConverter getRegisteredSac() {
        return new SourceAffineTransformer(null, affineTransformOut).apply(sac_moving);
    }

    public AffineTransform3D getAffineTransform() {
        return affineTransformOut;
    }

}
