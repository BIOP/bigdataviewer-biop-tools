package fused;

import bdv.util.BdvHandle;
import bdv.util.source.alpha.AlphaSourceHelper;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ch.epfl.biop.source.SourceFuserAndResampler;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleWrapped2DTransformAs3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import net.imglib2.type.numeric.real.FloatType;
import org.junit.After;
import org.junit.Test;
import sc.fiji.bdvpg.viewers.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.services.SourceServices;
import sc.fiji.bdvpg.source.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.source.importer.EmptySourceCreator;
import sc.fiji.bdvpg.source.transform.SourceAffineTransformer;
import sc.fiji.bdvpg.source.transform.SourceRealTransformer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ManyWarpedSourcesFusedDemo {

    static ImageJ ij;

    public static void main(String... args) {
        // Initializes static SourceService and Display Service

        ij = new ImageJ();
        ij.ui().showUI();

        int nSourcesInX = 10;

        List<SourceAndConverter<?>> all_sources = demo(nSourcesInX);

        all_sources.forEach(SourceServices.getSourceService()::register);

        AffineTransform3D location = new AffineTransform3D();
        location.scale(0.5);
        location.translate(0,0,0);

        SourceAndConverter<?> model = new EmptySourceCreator("Model", location, 400*nSourcesInX,300*nSourcesInX,1).get();

        SourceServices.getSourceService().register(model);

        SourceAndConverter<?> fused = new SourceFuserAndResampler(all_sources,
                AlphaFusedResampledSource.AVERAGE,
                model, "Fused source",
                true, true, false, 0,
                256, 256, 1, -1,8).get();

        BdvHandle bdvh = SourceServices.getBdvDisplayService().getNewBdv();

        SourceServices
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

    public static List<SourceAndConverter<?>> demo(int numberOfSourcesInOneAxis) {

        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceServices
                .getBdvDisplayService().getActiveBdv();

        String datasetName = "Icons";
        AbstractSpimData<?> spimdata = null;
        try {
            spimdata = (AbstractSpimData<?>) ij.command().run(CreateBdvDatasetBioFormatsCommand.class, true,
                    "files", new File[] {
                            new File("src/test/resources/ij.jpg"),
                            //new File("src/test/resources/fiji.jpg"),
                            //new File("src/test/resources/ImageJ.jpg")
                    },
                    "datasetname", datasetName,
                    "unit", "MICROMETER",
                    "split_rgb_channels", true,
                    "plane_origin_convention", "CENTER").get().getOutput("spimdata");
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        SourceAndConverter source0 = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimdata)
                .get(0);

        SourceAndConverter source1 = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimdata)
                .get(1);

        SourceAndConverter source2 = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimdata)
                .get(2);

        new ViewerTransformAdjuster(bdvHandle, source0).run();
        new BrightnessAutoAdjuster(source0, 0).run();

        new ViewerTransformAdjuster(bdvHandle, source1).run();
        new BrightnessAutoAdjuster(source1, 0).run();

        new ViewerTransformAdjuster(bdvHandle, source2).run();
        new BrightnessAutoAdjuster(source2, 0).run();

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

        ArrayList<SourceAndConverter<?>> sources = new ArrayList<>();
        for (int x = 0; x < numberOfSourcesInOneAxis;x++) {
            for (int y = 0; y < numberOfSourcesInOneAxis; y++) {

                if (Math.random()>0.0) {
                    AffineTransform3D at3d = new AffineTransform3D();

                    at3d.rotate(2, Math.random());
                    //at3d.scale(0.5 + Math.random() / 3, 0.5 + Math.random() / 2, 1);
                    at3d.scale((0.5 + Math.random() / 3.0)/2.5, (0.5 + Math.random() / 2.0)/2.5, 1);
                    at3d.translate(200 * x, 200 * y, 0.5);

                    SourceAndConverter<?> source;

                    double test = Math.random();
                    if (test<0.33) {
                        source = source0;
                    } else if (test<0.66) {
                        source = source1;
                    } else {
                        source = source2;
                    }

                    SourceAndConverter<FloatType> alphaSac = AlphaSourceHelper.getOrBuildAlphaSource(source);

                    SourceAndConverter<?> warped_source = new SourceRealTransformer(source, rt3d).get();
                    SourceServices
                            .getSourceService()
                                    .register(warped_source);

                    AlphaSourceHelper.setAlphaSource(warped_source, alphaSac); // Keeps bounds

                    SourceAndConverter<?> transformedSac = new SourceAffineTransformer(warped_source, at3d).get();

                    sources.add(transformedSac);
                }
            }
        }

        /*SourceServices
                .getBdvDisplayService()
                .show(bdvHandle, sources.toArray(new SourceAndConverter[0]));*/

        //SourceGroup sg = bdvHandle.getViewerPanel().state().getGroups().get(1);

        //bdvHandle.getViewerPanel().state().addSourcesToGroup(sources, sg);
        return sources;
    }
}
