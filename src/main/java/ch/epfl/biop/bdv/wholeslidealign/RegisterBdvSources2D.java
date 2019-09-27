package ch.epfl.biop.bdv.wholeslidealign;

import bdv.util.BdvHandle;
import bdv.util.RealCropper;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import ch.epfl.biop.bdv.commands.BDVSourceAffineTransform;
import ch.epfl.biop.wrappers.elastix.RegisterHelper;
import ch.epfl.biop.wrappers.elastix.ij2commands.Elastix_Register;
import ch.epfl.biop.wrappers.transformix.ij2commands.Transformix_TransformImgPlus;
import ij.ImagePlus;
import ij.plugin.Duplicator;
import itc.transforms.elastix.ElastixAffineTransform2D;
import itc.transforms.elastix.ElastixTransform;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.module.ModuleService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.IJ;

import java.io.File;
import java.util.List;
import java.util.concurrent.Future;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;
import static ch.epfl.biop.bdv.scijava.util.BDVSourceFunctionalInterfaceCommand.ADD;
import static ch.epfl.biop.bdv.scijava.util.BDVSourceFunctionalInterfaceCommand.LIST;

@Plugin(type = Command.class, menuPath = ScijavaBdvRootMenu+"Registration>Align Sources with Elastix")
public class RegisterBdvSources2D implements Command {

    @Parameter
    BdvHandle bdv_h_fixed;

    @Parameter
    int idxFixedSource;

    @Parameter
    int tpFixed;

    @Parameter
    int levelFixedSource;

    @Parameter
    BdvHandle bdv_h_moving;

    @Parameter
    int idxMovingSource;

    @Parameter
    int tpMoving;

    @Parameter
    int levelMovingSource;

    @Parameter
    CommandService cs;

    @Parameter
    double px,py,pz,sx,sy;

    @Parameter
    double pxSizeInCurrentUnit;

    @Parameter
    boolean interpolate;

    @Parameter
    boolean displayRegisteredSource = false;

    @Parameter
    boolean showImagePlusRegistrationResult = false;

    @Parameter(type = ItemIO.OUTPUT)
    AffineTransform3D affineTransformOut;

    @Parameter(type = ItemIO.OUTPUT)
    Source registeredSource;

    @Parameter(required = false)
    BdvHandle bdv_h_out;

    @Parameter
    ModuleService ms;

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

        Source sMoving = bdv_h_moving.getViewerPanel().getState().getSources().get(idxMovingSource).getSpimSource();
        Source sFixed = bdv_h_fixed.getViewerPanel().getState().getSources().get(idxFixedSource).getSpimSource();

        // Get real random accessible from the source
        final RealRandomAccessible ipMovingimg = sMoving.getInterpolatedSource(tpMoving, levelMovingSource, interpolation);
        final RealRandomAccessible ipFixedimg = sFixed.getInterpolatedSource(tpFixed, levelFixedSource, interpolation);

        AffineTransform3D at3D = new AffineTransform3D();
        at3D.identity();
        at3D.translate(-px,-py,pz);
        FinalRealInterval fi = new FinalRealInterval(new double[]{0,0,0}, new double[]{sx, sy, 0});

        AffineTransform3D atMoving = new AffineTransform3D();
        sMoving.getSourceTransform(tpMoving,levelMovingSource,atMoving);

        AffineTransform3D atFixed = new AffineTransform3D();
        sFixed.getSourceTransform(tpMoving,levelMovingSource,atFixed);

        AffineTransform3D movat = at3D.concatenate(atMoving);

        RandomAccessibleInterval viewMoving = RealCropper.getCroppedSampledRRAI(ipMovingimg,
                movat,fi,pxSizeInCurrentUnit,pxSizeInCurrentUnit,pxSizeInCurrentUnit);
        ImagePlus impM = ImageJFunctions.wrap(viewMoving, "Moving");
        impM = new Duplicator().run(impM); // Virtual messes up the process, don't know why
        //impM.show();

        at3D.identity();
        at3D.translate(-px,-py,pz);
        AffineTransform3D fixat = at3D.concatenate(atFixed);
        RandomAccessibleInterval viewFixed = RealCropper.getCroppedSampledRRAI(ipFixedimg,
                fixat,fi,pxSizeInCurrentUnit,pxSizeInCurrentUnit,pxSizeInCurrentUnit);
        ImagePlus impF = ImageJFunctions.wrap(viewFixed, "Fixed");
        impF = new Duplicator().run(impF); // Virtual messes up the process, don't know why
        //impF.show();

        try {
            Future<CommandModule> cm = cs.run(Elastix_Register.class, true, "movingImage",impM,
                    "fixedImage", impF,
                    "rigid",false,
                    "fast_affine",true,
                    "affine",false,
                    "spline",false);
            CommandModule cmg = cm.get();

            RegisterHelper rh = (RegisterHelper) cmg.getOutput("rh");
            File fTransform = new File(rh.getFinalTransformFile());

            ElastixTransform et = ElastixTransform.load(fTransform);

            assert et.getClass()== ElastixAffineTransform2D.class;

            Double[] m2D = et.TransformParameters;

            for (Double d : m2D) {
                System.out.println(d);
            }

            final AffineTransform3D affine3D = new AffineTransform3D();
            affine3D.set(new double[][] {
                    {m2D[0], m2D[1], 0,   m2D[4]}, //0 ok
                    {m2D[2], m2D[3], 0,   m2D[5]},
                    {0     ,      0, 1,        0},
                    {0.    ,      0, 0,        1}});

            at3D.identity();
            at3D.translate(-px,-py,pz);
            at3D.scale(1./pxSizeInCurrentUnit, 1./pxSizeInCurrentUnit, 1./pxSizeInCurrentUnit);


            AffineTransform3D transformInRealCoordinates = new AffineTransform3D();
            transformInRealCoordinates.identity();
            transformInRealCoordinates.concatenate(at3D.inverse());
            transformInRealCoordinates.concatenate(affine3D);
            transformInRealCoordinates.concatenate(at3D);

            String mode = ADD;
            if (!displayRegisteredSource) {
                mode = LIST;

            }

                // Using module service because:
                /*
                log.debug("The command '" + info.getIdentifier() +
                    "' extends Module directly. Due to a design flaw in the " +
                    "CommandService API, the result cannot be coerced to a " +
                    "Future<CommandModule>, so null will be returned instead. " +
                    "If you need the resulting module, please instead call " +
                    "moduleService.run(commandService.getCommand(commandClass), ...).");*/

            Object o = ms.run(cs.getCommand(BDVSourceAffineTransform.class),true,
                        "bdv_h_in", bdv_h_moving,
                        "sourceIndexString", Integer.toString(idxMovingSource),
                        "bdv_h_out", bdv_h_out,
                        "output_mode", mode,
                        "keepConverters", false,
                        "outputInNewBdv", false,
                        "stringMatrix", transformInRealCoordinates.inverse().toString(),
                    "makeInputVolatile",false)
                    .get().getOutput("srcs_out");

            registeredSource = ((List<Source<?>>) o).get(0);

            if (showImagePlusRegistrationResult) {
                impF.show();

                ImagePlus transformedImage = ((ImagePlus)cs.run(Transformix_TransformImgPlus.class, true, "img_in", impM,
                        "rh", rh).get().getOutput("img_out"));
                transformedImage.show();

                IJ.run(impF, "Enhance Contrast", "saturated=0.35");
                IJ.run(transformedImage, "Enhance Contrast", "saturated=0.35");
                IJ.run(impF, "32-bit", "");
                IJ.run((ImagePlus) null, "Merge Channels...", "c1=Transformed_DUP_Moving c2=DUP_Fixed create");
            }

            //--------------------------------------------------
            affineTransformOut = transformInRealCoordinates.inverse();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
