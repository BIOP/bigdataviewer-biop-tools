import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import bdv.util.BoundedRealTransform;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.FinalRealInterval;
import net.imglib2.type.numeric.ARGBType;
import sc.fiji.bdvpg.viewers.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.services.SourceServices;
import sc.fiji.bdvpg.source.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.source.register.BigWarpLauncher;
import sc.fiji.bdvpg.source.transform.SourceRealTransformer;
import sc.fiji.bdvpg.dataset.importer.SpimDataFromXmlImporter;

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

        AbstractSpimData<?> spimData = importer.get();

        SourceAndConverter<?> sourceFixed = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData)
                .get(0);

        importer = new SpimDataFromXmlImporter(filePath);

        spimData = importer.get();

        SourceAndConverter<?> sourceMoving = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData)
                .get(0);


        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceServices.getBdvDisplayService().getActiveBdv();

        // Show the sourceandconverter
        // SourceServices.getBdvDisplayService().show(bdvHandle, sourceFixed);

        SourceServices.getSourceService().getConverterSetup(sourceMoving)
                .setColor(new ARGBType(ARGBType.rgba(0, 255, 255,0)));

        new BrightnessAutoAdjuster(sourceFixed, 0).run();

        new BrightnessAutoAdjuster(sourceMoving, 0).run();

        new ViewerTransformAdjuster(bdvHandle, sourceFixed).run();

        List<SourceAndConverter<?>> movingSources = new ArrayList<>();
        movingSources.add(sourceMoving);

        List<SourceAndConverter<?>> fixedSources = new ArrayList<>();
        fixedSources.add(sourceFixed);

        List<ConverterSetup> converterSetups = movingSources.stream().map(src -> SourceServices.getSourceService().getConverterSetup(src)).collect(Collectors.toList());
        converterSetups.addAll(fixedSources.stream().map(src -> SourceServices.getSourceService().getConverterSetup(src)).collect(Collectors.toList()));

        BigWarpLauncher bwl = new BigWarpLauncher(movingSources, fixedSources, "BigWarp Demo", converterSetups);
        bwl.run();

        bwl.getBigWarp().loadLandmarks( "src/test/resources/landmarks3d-demo.csv" );

        bwl.getBigWarp().toggleMovingImageDisplay();
        bwl.getBigWarp().matchActiveViewerPanelToOther();

        for (SourceAndConverter<?> source : bwl.getWarpedSources()) {
            SourceServices.getSourceService()
                    .register(source);
        }

        BoundedRealTransform brt = new BoundedRealTransform(bwl.getBigWarp().getBwTransform().getTransformation(0), new FinalRealInterval(new double[]{20,20,20}, new double[]{150,150,550}));

        SourceAndConverter<?> tr = new SourceRealTransformer(null,brt).apply(sourceFixed);
        SourceServices.getBdvDisplayService()
                .show(bdvHandle, tr);

    }
}
