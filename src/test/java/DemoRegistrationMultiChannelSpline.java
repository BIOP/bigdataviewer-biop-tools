import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.export.spimdata.BioFormatsConvertFilesToSpimData;
import ch.epfl.biop.scijava.command.source.register.Elastix2DSplineRegisterCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import ome.units.UNITS;
import ome.units.quantity.Length;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.util.List;
import java.util.concurrent.Future;


public class DemoRegistrationMultiChannelSpline {

    static {
        LegacyInjector.preinit();
    }

    static SourceAndConverter fixedSource;

    static SourceAndConverter movingSource;

    static final ImageJ ij = new ImageJ();

    static public void main(String... args) throws Exception {

        ij.ui().showUI();

        BioFormatsBdvOpener opener =
                BioFormatsConvertFilesToSpimData
                        .getDefaultOpener("src/test/resources/multichanreg/Atlas.tif");

        AbstractSpimData atlasDataset = BioFormatsConvertFilesToSpimData.getSpimData(
                opener
                        .voxSizeReferenceFrameLength(new Length(1, UNITS.MILLIMETER))
                        .positionReferenceFrameLength(new Length(1,UNITS.METER)));

        SourceAndConverterServices.getSourceAndConverterService().register(atlasDataset);
        List<SourceAndConverter<?>> atlasSources = SourceAndConverterServices.getSourceAndConverterService().getSourceAndConverterFromSpimdata(atlasDataset);

        opener =
                BioFormatsConvertFilesToSpimData
                        .getDefaultOpener("src/test/resources/multichanreg/Slice.tif");

        AbstractSpimData sliceDataset = BioFormatsConvertFilesToSpimData.getSpimData(
                opener
                        .voxSizeReferenceFrameLength(new Length(1, UNITS.MILLIMETER))
                        .positionReferenceFrameLength(new Length(1,UNITS.METER)));

        SourceAndConverterServices.getSourceAndConverterService().register(sliceDataset);
        List<SourceAndConverter<?>> sliceSources = SourceAndConverterServices.getSourceAndConverterService().getSourceAndConverterFromSpimdata(sliceDataset);

        SourceAndConverterBdvDisplayService displayService = SourceAndConverterServices.getBdvDisplayService();
        BdvHandle bdvh = displayService.getNewBdv();
        displayService.show(bdvh, atlasSources.toArray(new SourceAndConverter[0]));
        displayService.show(bdvh, sliceSources.toArray(new SourceAndConverter[0]));

        fixedSource = atlasSources.get(0);
        movingSource = sliceSources.get(0);

        Future<CommandModule> task = ij.context()
                .getService(CommandService.class)
                .run(Elastix2DSplineRegisterCommand.class, true,
                        "sacs_fixed", atlasSources.toArray(new SourceAndConverter[0]),//new SourceAndConverter[]{fixedSource},//atlasSources.toArray(new SourceAndConverter[0]),//new SourceAndConverter[]{fixedSource},
                        "tpFixed", 0,
                        "levelFixedSource", 0,
                        "sacs_moving", sliceSources.toArray(new SourceAndConverter[0]),//new SourceAndConverter[]{movingSource},
                        "tpMoving", 0,
                        "levelMovingSource", 0,
                        "pxSizeInCurrentUnit", 0.02,
                        "interpolate", false,
                        "showImagePlusRegistrationResult", true,
                        "px", 0,
                        "py", 0,
                        "pz", 0,
                        "sx", 12,
                        "sy", 9
                );
    }

}
