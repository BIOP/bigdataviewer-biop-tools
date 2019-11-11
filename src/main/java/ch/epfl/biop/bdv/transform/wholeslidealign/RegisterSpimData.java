package ch.epfl.biop.bdv.transform.wholeslidealign;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.util.BdvHandle;
import ch.epfl.biop.wrappers.elastix.RegisterHelper;
import ch.epfl.biop.wrappers.elastix.ij2commands.Elastix_Register;
import ij.ImagePlus;
import itc.transforms.elastix.ElastixAffineTransform2D;
import itc.transforms.elastix.ElastixTransform;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import net.imglib2.*;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.view.Views;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;

import java.io.File;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, menuPath = ScijavaBdvRootMenu+"SpimDataset>Register>Align SpimData with Elastix (obsolete)")
public class RegisterSpimData implements Command {

    @Parameter
    BdvHandle bdv_h;

    @Parameter
    AbstractSpimData as;

    @Parameter
    String datasetName;

    @Parameter
    int viewSetupFixed;

    @Parameter
    int tpFixed;

    @Parameter
    int levelSetupFixed;

    @Parameter
    int viewSetupMoving;

    @Parameter
    int tpMoving;

    @Parameter
    int levelSetupMoving;

    @Parameter
    CommandService cs;

    @Override
    public void run() {
        BasicSetupImgLoader silF = as.getSequenceDescription().getImgLoader().getSetupImgLoader(viewSetupFixed);
        AffineTransform3D atfMM;

        RandomAccessibleInterval raiM;
        if (silF instanceof MultiResolutionSetupImgLoader) {
            MultiResolutionSetupImgLoader msil1 = (MultiResolutionSetupImgLoader) silF;
            raiM = msil1.getImage(tpFixed,levelSetupFixed);
            atfMM = msil1.getMipmapTransforms()[levelSetupFixed];
        } else {
            raiM = silF.getImage(tpFixed);
            atfMM = new AffineTransform3D();
            atfMM.identity();
        }

        BasicSetupImgLoader silM = as.getSequenceDescription().getImgLoader().getSetupImgLoader(viewSetupMoving);
        AffineTransform3D atmMM;
        RandomAccessibleInterval raiF;
        if (silM instanceof MultiResolutionSetupImgLoader) {
            MultiResolutionSetupImgLoader msil2 = (MultiResolutionSetupImgLoader) silM;
            raiF = msil2.getImage(tpMoving,levelSetupMoving);
            atmMM = msil2.getMipmapTransforms()[levelSetupMoving];
        } else {
            raiF = silF.getImage(tpMoving);
            atmMM = new AffineTransform3D();
            atmMM.identity();
        }

        AffineTransform3D pxFToRealSpace = as.getViewRegistrations().getViewRegistration(tpFixed,viewSetupFixed).getModel();

        AffineTransform3D pxMToRealSpace = as.getViewRegistrations().getViewRegistration(tpMoving,viewSetupMoving).getModel();

        AffineTransform3D movToFixed = new AffineTransform3D();

        // Computes transform of moving RAI into fixed one
        movToFixed.identity();
        movToFixed.concatenate(atmMM.inverse());
        movToFixed.concatenate(pxMToRealSpace.inverse());
        movToFixed.concatenate(pxFToRealSpace);
        movToFixed.concatenate(atfMM);

        movToFixed = movToFixed.inverse(); // Apparently better

        RealRandomAccessible rraiM = Views.interpolate(Views.extendZero(raiM), new NearestNeighborInterpolatorFactory());
        RandomAccessible trRraiM = RealViews.affine(rraiM, movToFixed);

        ImagePlus impM = ImageJFunctions.wrap(Views.interval(trRraiM,raiF), "Moving");

        //ImagePlus impM = ImageJFunctions.wrap(raiM, "Moving");
        ImagePlus impF = ImageJFunctions.wrap(raiF, "Fixed");

        impM.show();
        impF.show();

        try {
            CommandModule cm = cs.run(Elastix_Register.class, true, "movingImage",impM,
                    "fixedImage", impF,
                    "rigid",false,
                    "fast_affine",true,
                    "affine",false,
                    "spline",false).get();

            RegisterHelper rh = (RegisterHelper) cm.getOutput("rh");
            File fTransform = new File(rh.getFinalTransformFile());

            ElastixTransform et = ElastixTransform.load(fTransform);

            System.out.println("Transfo Class :"+ et.getClass());

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

            AffineTransform3D atRes = new AffineTransform3D();
            atRes.identity();
            //atRes.concatenate(movToFixed.inverse());
            //atRes.concatenate(affine3D.inverse());
            atRes.concatenate(pxMToRealSpace);
            atRes.concatenate(pxFToRealSpace.inverse());
            atRes.concatenate(atfMM.inverse());
            atRes.concatenate(affine3D);
            atRes.concatenate(atmMM);





            //atRes.concatenate(movToFixed);

            ViewTransform vt = new ViewTransformAffine( null, atRes.inverse() ); // affine3D

            as.getViewRegistrations().getViewRegistration(tpMoving,viewSetupMoving).getTransformList().add(vt);
            as.getViewRegistrations().getViewRegistration(tpMoving,viewSetupMoving).updateModel();

            System.out.println("Done!");

            new XmlIoSpimDataMinimal().save( (SpimDataMinimal) as, new File(as.getBasePath(),datasetName).getAbsolutePath() );
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
