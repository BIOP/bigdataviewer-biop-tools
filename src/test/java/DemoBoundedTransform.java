import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import bdv.util.BoundedRealTransform;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.FinalRealInterval;
import net.imglib2.type.numeric.ARGBType;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.sourceandconverter.register.BigWarpLauncher;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DemoBoundedTransform {

    static {
        LegacyInjector.preinit();
    }

    static public void main(String... args) {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        demo3d();
    }


    public static void demo3d() {
        final String filePath = "src/test/resources/mri-stack.xml";
        // Import SpimData
        SpimDataFromXmlImporter importer = new SpimDataFromXmlImporter(filePath);

        AbstractSpimData spimData = importer.get();

        SourceAndConverter sacFixed = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(spimData)
                .get(0);

        importer = new SpimDataFromXmlImporter(filePath);

        spimData = importer.get();

        SourceAndConverter sacMoving = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(spimData)
                .get(0);


        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceAndConverterServices.getSourceAndConverterDisplayService().getActiveBdv();

        // Show the sourceandconverter
        SourceAndConverterServices.getSourceAndConverterDisplayService().show(bdvHandle, sacFixed);

        SourceAndConverterServices.getSourceAndConverterDisplayService().getConverterSetup(sacMoving)
                .setColor(new ARGBType(ARGBType.rgba(0, 255, 255,0)));

        new BrightnessAutoAdjuster(sacFixed, 0).run();

        new BrightnessAutoAdjuster(sacMoving, 0).run();

        new ViewerTransformAdjuster(bdvHandle, sacFixed).run();

        List<SourceAndConverter> movingSources = new ArrayList<>();
        movingSources.add(sacMoving);

        List<SourceAndConverter> fixedSources = new ArrayList<>();
        fixedSources.add(sacFixed);

        List<ConverterSetup> converterSetups = movingSources.stream().map(src -> SourceAndConverterServices.getSourceAndConverterDisplayService().getConverterSetup(src)).collect(Collectors.toList());
        converterSetups.addAll(fixedSources.stream().map(src -> SourceAndConverterServices.getSourceAndConverterDisplayService().getConverterSetup(src)).collect(Collectors.toList()));

        BigWarpLauncher bwl = new BigWarpLauncher(movingSources, fixedSources, "BigWarp Demo", converterSetups);
        bwl.run();

        bwl.getBigWarp().loadLandmarks( "src/test/resources/landmarks3d-demo.csv" );

        bwl.getBigWarp().toggleMovingImageDisplay();
        bwl.getBigWarp().matchActiveViewerPanelToOther();

        for (SourceAndConverter sac : bwl.getWarpedSources()) {
            SourceAndConverterServices.getSourceAndConverterService()
                    .register(sac);
        }

        BoundedRealTransform brt = new BoundedRealTransform(bwl.getBigWarp().getBwTransform().getTransformation(0), new FinalRealInterval(new double[]{20,20,20}, new double[]{150,150,150}));

        SourceAndConverter tr = new SourceRealTransformer(null,brt).apply(sacFixed);
        SourceAndConverterServices.getSourceAndConverterDisplayService()
                .show(bdvHandle, tr);

    }
}
