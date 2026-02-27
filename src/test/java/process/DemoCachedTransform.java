package process;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.util.RealTransformHelper;
import bdv.util.EmptySource;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.realtransform.RealTransform;
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

public class DemoCachedTransform {

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

        SourceAndConverter<?> sacFixed = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData)
                .get(0);

        importer = new SpimDataFromXmlImporter(filePath);

        spimData = importer.get();

        SourceAndConverter<?> sacMoving = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData)
                .get(0);


        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceServices.getBdvDisplayService().getActiveBdv();

        // Show the sourceandconverter
        SourceServices.getBdvDisplayService().show(bdvHandle, sacFixed);

        SourceServices.getSourceService().getConverterSetup(sacMoving)
                .setColor(new ARGBType(ARGBType.rgba(0, 255, 255,0)));

        new BrightnessAutoAdjuster(sacFixed, 0).run();

        new BrightnessAutoAdjuster(sacMoving, 0).run();

        new ViewerTransformAdjuster(bdvHandle, sacFixed).run();

        List<SourceAndConverter<?>> movingSources = new ArrayList<>();
        movingSources.add(sacMoving);

        List<SourceAndConverter<?>> fixedSources = new ArrayList<>();
        fixedSources.add(sacFixed);

        List<ConverterSetup> converterSetups = movingSources.stream().map(src -> SourceServices.getSourceService().getConverterSetup(src)).collect(Collectors.toList());
        converterSetups.addAll(fixedSources.stream().map(src -> SourceServices.getSourceService().getConverterSetup(src)).collect(Collectors.toList()));

        BigWarpLauncher bwl = new BigWarpLauncher(movingSources, fixedSources, "BigWarp Demo", converterSetups);
        bwl.run();

        bwl.getBigWarp().loadLandmarks( "src/test/resources/landmarks3d-demo.csv" );

        bwl.getBigWarp().toggleMovingImageDisplay();
        bwl.getBigWarp().matchActiveViewerPanelToOther();

        for (SourceAndConverter sac : bwl.getWarpedSources()) {
            SourceServices.getSourceService()
                    .register(sac);
        }

        // Makes a source from a transform:

        //ITransformFieldSource source = new TransformFieldSource(bwl.getBigWarp().getBwTransform().getTransformation(0), "BigWarp Transformation");

        EmptySource.EmptySourceParams params = new EmptySource.EmptySourceParams();
        params.name = "Model Source";
        params.nx = 25; // <25 breaks!! singular matrix exception
        params.ny = 25;
        params.nz = 20; // could be anything ?
        params.at3D.scale(10,10,10);
        params.at3D.translate(-50,-50, -50);
        EmptySource model = new EmptySource(params);

        //Source<?> model = sacFixed.getSpimSource();

        //ITransformFieldSource cached_transform = new ResampledTransformFromSourceFieldSource(source, model, "Cached transform");
        //RealTransform transform = new SourcedRealTransform(cached_transform);

        //BoundedRealTransform brt = new BoundedRealTransform(bwl.getBigWarp().getBwTransform().getTransformation(0), new FinalRealInterval(new double[]{20,20,20}, new double[]{150,150,150}));

        //ResampledTransformFieldSource tra

        RealTransform transform = RealTransformHelper.resampleTransform(bwl.getBigWarp().getBwTransform().getTransformation(0), model);

        SourceAndConverter tr = new SourceRealTransformer(null,transform).apply(sacFixed);
        SourceServices.getBdvDisplayService()
                .show(bdvHandle, tr);

        SourceServices.getBdvDisplayService()
                .show(bdvHandle, bwl.getWarpedSources()[0]);

        /*SourceAndConverter resampled = new SourceResampler(fixedSources.get(0), SourceHelper.createSourceAndConverter(model), "Model Size", false, false, false, 0).get();

        SourceServices.getBdvDisplayService()
                .show(bdvHandle, resampled);*/

    }
}
