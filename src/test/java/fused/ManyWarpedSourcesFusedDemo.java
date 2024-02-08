package fused;

import bdv.util.BdvHandle;
import bdv.util.source.alpha.AlphaSourceHelper;
import bdv.util.source.alpha.IAlphaSource;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import ch.epfl.biop.sourceandconverter.SourceFuserAndResampler;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleWrapped2DTransformAs3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import org.junit.After;
import org.junit.Test;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import java.util.ArrayList;
import java.util.List;

public class ManyWarpedSourcesFusedDemo {

    static ImageJ ij;

    public static void main(String... args) {
        // Initializes static SourceService and Display Service

        ij = new ImageJ();
        ij.ui().showUI();

        int nSourcesInX = 40;

        List<SourceAndConverter> all_sources = demo(nSourcesInX);

        AffineTransform3D location = new AffineTransform3D();
        location.scale(0.5);
        location.translate(0,0,0);

        SourceAndConverter model = new EmptySourceAndConverterCreator("Model", location, 400*nSourcesInX,300*nSourcesInX,1).get();

        SourceAndConverterServices.getSourceAndConverterService().register(model);

        SourceAndConverter fused = new SourceFuserAndResampler(all_sources,
                AlphaFusedResampledSource.AVERAGE,
                model, "Fused source",
                true, true, false, 0,
                256, 256, 1, -1,8).get();

        BdvHandle bdvh = SourceAndConverterServices.getBdvDisplayService().getNewBdv();

        SourceAndConverterServices
                .getBdvDisplayService().show(bdvh, fused);

        new ViewerTransformAdjuster(bdvh, fused).run();

    }

    @Test
    public void demoRunOk() {
        main(new String[]{""});
    }

    @After
    public void closeFiji() {
        TestHelper.closeFijiAndBdvs(ij);
    }

    public static List<SourceAndConverter> demo(int numberOfSourcesInOneAxis) {

        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceAndConverterServices
                .getBdvDisplayService().getActiveBdv();

        //final String filePath = "src/test/resources/mri-stack.xml";
        final String filePath = "src/test/resources/Icons.xml";
        // Import SpimData
        SpimDataFromXmlImporter importer = new SpimDataFromXmlImporter(filePath);
        //importer.run();

        final AbstractSpimData spimData = importer.get();

        SourceAndConverter sac0 = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(spimData)
                .get(0);

        SourceAndConverter sac1 = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(spimData)
                .get(1);

        SourceAndConverter sac2 = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(spimData)
                .get(2);

        new ViewerTransformAdjuster(bdvHandle, sac0).run();
        new BrightnessAutoAdjuster(sac0, 0).run();

        new ViewerTransformAdjuster(bdvHandle, sac1).run();
        new BrightnessAutoAdjuster(sac1, 0).run();

        new ViewerTransformAdjuster(bdvHandle, sac2).run();
        new BrightnessAutoAdjuster(sac2, 0).run();

        double[][] origin = new double[2][4];
        double[][] target = new double[2][4];

        for (int iPt = 0; iPt < 4; iPt++) {

            origin[0] = new double[]{0,1024,1024,0};
            origin[1] = new double[]{0,0,1024,1024};

            target[0] = new double[]{0,512+Math.random()*512,512+Math.random()*512,0};
            target[1] = new double[]{0,0,512+Math.random()*512,512+Math.random()*512};
        }

        RealTransform rt = new ThinplateSplineTransform(target, origin);
        WrappedIterativeInvertibleRealTransform invertibleRealTransform = new WrappedIterativeInvertibleRealTransform(rt);
        InvertibleWrapped2DTransformAs3D rt3d = new InvertibleWrapped2DTransformAs3D(invertibleRealTransform);

        ArrayList<SourceAndConverter> sacs = new ArrayList<>();
        for (int x = 0; x < numberOfSourcesInOneAxis;x++) {
            for (int y = 0; y < numberOfSourcesInOneAxis; y++) {

                if (Math.random()>0.0) {
                    AffineTransform3D at3d = new AffineTransform3D();

                    at3d.rotate(2, Math.random());
                    //at3d.scale(0.5 + Math.random() / 3, 0.5 + Math.random() / 2, 1);
                    at3d.scale((0.5 + Math.random() / 3.0)/2.5, (0.5 + Math.random() / 2.0)/2.5, 1);
                    at3d.translate(200 * x, 200 * y, 0);

                    SourceAndConverter sac;

                    double test = Math.random();
                    if (test<0.33) {
                        sac = sac0;
                    } else if (test<0.66) {
                        sac = sac1;
                    } else {
                        sac = sac2;
                    }

                    SourceAndConverter alphaSac = AlphaSourceHelper.getOrBuildAlphaSource(sac);

                    SourceAndConverter warped_sac = new SourceRealTransformer(sac, rt3d).get();
                    SourceAndConverterServices
                            .getSourceAndConverterService()
                                    .register(warped_sac);

                    AlphaSourceHelper.setAlphaSource(warped_sac, alphaSac); // Keeps bounds

                    SourceAndConverter transformedSac = new SourceAffineTransformer(warped_sac, at3d).get();

                    sacs.add(transformedSac);
                }
            }
        }

        /*SourceAndConverterServices
                .getBdvDisplayService()
                .show(bdvHandle, sacs.toArray(new SourceAndConverter[0]));*/

        //SourceGroup sg = bdvHandle.getViewerPanel().state().getGroups().get(1);

        //bdvHandle.getViewerPanel().state().addSourcesToGroup(sacs, sg);
        return sacs;
    }
}
