package fused;

import bdv.util.BdvHandle;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import ch.epfl.biop.sourceandconverter.SourceFuserAndResampler;
import loci.common.DebugTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import org.junit.After;
import org.junit.Test;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import java.util.ArrayList;
import java.util.List;

public class ManySourcesFusedDemo {

    static ImageJ ij;

    public static void main(String... args) {
        // Initializes static SourceService and Display Service

        ij = new ImageJ();
        ij.ui().showUI();

        DebugTools.setRootLevel("OFF");
        demo(50);

        AffineTransform3D location = new AffineTransform3D();
        location.scale(0.5);
        location.translate(0,0,120);

        SourceAndConverter model = new EmptySourceAndConverterCreator("Model", location, 8000,6000,1).get();

        List<SourceAndConverter> all_sources = SourceAndConverterServices.getSourceAndConverterService().getSourceAndConverters();

        SourceAndConverterServices.getSourceAndConverterService().register(model);

        SourceAndConverter fused = new SourceFuserAndResampler(all_sources,
                AlphaFusedResampledSource.AVERAGE,
                model, "Fused source",
                true, true, false, 0,
                64, 64, 64, 1000,8).get();

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

    public static void demo(int numberOfSourcesInOneAxis) {

        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceAndConverterServices
                .getBdvDisplayService().getActiveBdv();

        final String filePath = "src/test/resources/mri-stack.xml";
        // Import SpimData
        SpimDataFromXmlImporter importer = new SpimDataFromXmlImporter(filePath);
        //importer.run();

        final AbstractSpimData spimData = importer.get();

        SourceAndConverter sac = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(spimData)
                .get(0);

        new ViewerTransformAdjuster(bdvHandle, sac).run();
        new BrightnessAutoAdjuster(sac, 0).run();

        ArrayList<SourceAndConverter<?>> sacs = new ArrayList<>();
        for (int x = 0; x < numberOfSourcesInOneAxis;x++) {
            for (int y = 0; y < numberOfSourcesInOneAxis; y++) {

                if (Math.random()>0.0) {
                    AffineTransform3D at3d = new AffineTransform3D();

                    at3d.rotate(2, Math.random());
                    at3d.scale(0.5 + Math.random() / 4, 0.5 + Math.random() / 4, 1);
                    at3d.translate(200 * x, 200 * y, 0);

                    SourceAffineTransformer sat = new SourceAffineTransformer(sac, at3d);
                    sat.run();

                    SourceAndConverter transformedSac = sat.getSourceOut();

                    sacs.add(transformedSac);
                }
            }
        }

        SourceAndConverterServices
                .getBdvDisplayService()
                .show(bdvHandle, sacs.toArray(new SourceAndConverter[0]));

        SourceGroup sg = bdvHandle.getViewerPanel().state().getGroups().get(1);

        bdvHandle.getViewerPanel().state().addSourcesToGroup(sacs, sg);
    }
}
