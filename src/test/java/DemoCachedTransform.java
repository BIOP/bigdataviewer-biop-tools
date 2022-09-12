import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.util.DefaultInterpolators;
import bdv.util.source.field.ResampledTransformFieldSource;
import bdv.util.BoundedRealTransform;
import bdv.util.EmptySource;
import bdv.util.ResampledSource;
import bdv.util.SourcedRealTransform;
import bdv.util.source.field.ITransformFieldSource;
import bdv.util.source.field.TransformFieldSource;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.FinalRealInterval;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.numeric.ARGBType;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.sourceandconverter.register.BigWarpLauncher;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

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

        SourceAndConverter<?> sacFixed = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(spimData)
                .get(0);

        importer = new SpimDataFromXmlImporter(filePath);

        spimData = importer.get();

        SourceAndConverter<?> sacMoving = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(spimData)
                .get(0);


        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceAndConverterServices.getBdvDisplayService().getActiveBdv();

        // Show the sourceandconverter
        SourceAndConverterServices.getBdvDisplayService().show(bdvHandle, sacFixed);

        SourceAndConverterServices.getSourceAndConverterService().getConverterSetup(sacMoving)
                .setColor(new ARGBType(ARGBType.rgba(0, 255, 255,0)));

        new BrightnessAutoAdjuster(sacFixed, 0).run();

        new BrightnessAutoAdjuster(sacMoving, 0).run();

        new ViewerTransformAdjuster(bdvHandle, sacFixed).run();

        List<SourceAndConverter<?>> movingSources = new ArrayList<>();
        movingSources.add(sacMoving);

        List<SourceAndConverter<?>> fixedSources = new ArrayList<>();
        fixedSources.add(sacFixed);

        List<ConverterSetup> converterSetups = movingSources.stream().map(src -> SourceAndConverterServices.getSourceAndConverterService().getConverterSetup(src)).collect(Collectors.toList());
        converterSetups.addAll(fixedSources.stream().map(src -> SourceAndConverterServices.getSourceAndConverterService().getConverterSetup(src)).collect(Collectors.toList()));

        BigWarpLauncher bwl = new BigWarpLauncher(movingSources, fixedSources, "BigWarp Demo", converterSetups);
        bwl.run();

        bwl.getBigWarp().loadLandmarks( "src/test/resources/landmarks3d-demo.csv" );

        bwl.getBigWarp().toggleMovingImageDisplay();
        bwl.getBigWarp().matchActiveViewerPanelToOther();

        for (SourceAndConverter sac : bwl.getWarpedSources()) {
            SourceAndConverterServices.getSourceAndConverterService()
                    .register(sac);
        }

        // Makes a source from a transform:

        ITransformFieldSource source = new TransformFieldSource(bwl.getBigWarp().getBwTransform().getTransformation(0), "BigWarp Transformation");

        EmptySource.EmptySourceParams params = new EmptySource.EmptySourceParams();
        params.name = "Model Source";
        params.nx = 25;
        params.ny = 25;
        params.nz = 25;
        params.at3D.scale(20,20,20);
        params.at3D.translate(-100,-100, -100);

        EmptySource model = new EmptySource(params);

        ITransformFieldSource cached_transform = new ResampledTransformFieldSource(source, model, "Cached transform");


        RealTransform transform = new SourcedRealTransform(cached_transform);

        //BoundedRealTransform brt = new BoundedRealTransform(bwl.getBigWarp().getBwTransform().getTransformation(0), new FinalRealInterval(new double[]{20,20,20}, new double[]{150,150,150}));

        SourceAndConverter tr = new SourceRealTransformer(null,transform).apply(sacFixed);
        SourceAndConverterServices.getBdvDisplayService()
                .show(bdvHandle, tr);

        SourceAndConverter resampled = new SourceResampler(fixedSources.get(0), SourceAndConverterHelper.createSourceAndConverter(model), "Model Size", false, false, false, 0).get();

        SourceAndConverterServices.getBdvDisplayService()
                .show(bdvHandle, resampled);

    }
}
