import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.OpenersToSpimData;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import ch.epfl.biop.scijava.command.source.register.Elastix2DAffineRegisterCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.util.List;

public class DemoRegistrationMultiChannelAffine {

    static SourceAndConverter<?> fixedSource;

    static SourceAndConverter<?> movingSource;

    static final ImageJ ij = new ImageJ();

    static public void main(String... args) throws Exception {

        ij.ui().showUI();

        OpenerSettings atlasSettings = OpenerSettings.BioFormats()
                .location("./src/test/resources/multichanreg/Atlas.tif")
                //.location("C:\\Users\\chiarutt\\Dropbox\\BIOP\\bigdataviewer-biop-tools\\src\\test\\resources\\multichanreg\\Atlas.tif")
                .millimeter().setSerie(0);

        AbstractSpimData<?> atlasDataset = OpenersToSpimData.getSpimData(atlasSettings);

        SourceAndConverterServices.getSourceAndConverterService().register(atlasDataset);
        List<SourceAndConverter<?>> atlasSources = SourceAndConverterServices.getSourceAndConverterService().getSourceAndConverterFromSpimdata(atlasDataset);

        OpenerSettings sliceSettings = OpenerSettings.BioFormats()
                .location("./src/test/resources/multichanreg/Slice.tif")
                //.location("C:\\Users\\chiarutt\\Dropbox\\BIOP\\bigdataviewer-biop-tools\\src\\test\\resources\\multichanreg\\Slice.tif")
                .millimeter().setSerie(0);

        AbstractSpimData<?> sliceDataset = OpenersToSpimData.getSpimData(sliceSettings);

        SourceAndConverterServices.getSourceAndConverterService().register(sliceDataset);
        List<SourceAndConverter<?>> sliceSources = SourceAndConverterServices.getSourceAndConverterService().getSourceAndConverterFromSpimdata(sliceDataset);

        SourceAndConverterBdvDisplayService displayService = SourceAndConverterServices.getBdvDisplayService();
        BdvHandle bdvh = displayService.getNewBdv();
        displayService.show(bdvh, atlasSources.toArray(new SourceAndConverter[0]));
        displayService.show(bdvh, sliceSources.toArray(new SourceAndConverter[0]));

        new ViewerTransformAdjuster(bdvh, atlasSources.toArray(new SourceAndConverter[0])).run();

        fixedSource = atlasSources.get(0);
        movingSource = sliceSources.get(0);

        ij.context()
            .getService(CommandService.class)
            .run(Elastix2DAffineRegisterCommand.class, true,
                    "sacs_fixed", atlasSources.toArray(new SourceAndConverter[0]),
                    "tpFixed", 0,
                    "levelFixedSource", 0,
                    "sacs_moving", sliceSources.toArray(new SourceAndConverter[0]),
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
