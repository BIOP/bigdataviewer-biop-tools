package fused;

import bdv.util.BdvHandle;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceFuserAndResampler;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.ARGBType;
import org.junit.After;
import org.junit.Test;
import fused.TestHelper;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.bdv.supplier.IBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaSerializableBdvOptions;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.sourceandconverter.display.ColorChanger;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class FusedDemo {
    static ImageJ ij;

    public static void main( String[] args ) throws ExecutionException, InterruptedException
    {
        // Create the ImageJ application context with all available services; necessary for SourceAndConverterServices creation
        ij = new ImageJ();
        ij.ui().showUI();
        demo();
    }

    @Test
    public void demoRunOk() throws ExecutionException, InterruptedException
    {
        main(new String[]{""});
    }

    @After
    public void closeFiji() {
        TestHelper.closeFijiAndBdvs(ij);
    }

    public static void demo() {
        IBdvSupplier bdvSupplier = new AlphaBdvSupplier(new AlphaSerializableBdvOptions());

        SourceAndConverterServices.getBdvDisplayService().setDefaultBdvSupplier(bdvSupplier);

        BdvHandle bdv = SourceAndConverterServices.getBdvDisplayService().getNewBdv();

        // Import SpimData
        new SpimDataFromXmlImporter( "src/test/resources/mri-stack.xml" ).run();
        new SpimDataFromXmlImporter("src/test/resources/mri-stack-shiftedX.xml").run();
        new SpimDataFromXmlImporter( "src/test/resources/mri-stack-shiftedY.xml" ).run();

        // Get a handle on the sacs
        final List<SourceAndConverter> sacs = SourceAndConverterServices.getSourceAndConverterService().getSourceAndConverters();

        // Show all three sacs
        sacs.forEach( sac -> {
            SourceAndConverterServices.getBdvDisplayService().show(bdv, sac);
            new ViewerTransformAdjuster(bdv, sac).run();
            new BrightnessAutoAdjuster(sac, 0).run();
        });

        // Change color of third one
        new ColorChanger( sacs.get( 2 ), new ARGBType( ARGBType.rgba( 0, 255, 0, 255 ) ) ).run();

        SourceAndConverter fused = new SourceFuserAndResampler(sacs,
                AlphaFusedResampledSource.AVERAGE,
                sacs.get(0), "Fused source",
                true, true, false, 0,
                64, 64, 64, 4).get();

        BdvHandle bdvh = SourceAndConverterServices.getBdvDisplayService().getNewBdv();

        SourceAndConverterServices
                .getBdvDisplayService().show(bdvh, fused);

        new ViewerTransformAdjuster(bdvh, fused).run();

    }
}
