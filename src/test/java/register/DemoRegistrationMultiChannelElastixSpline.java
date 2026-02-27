package register;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.OpenersToSpimData;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import ch.epfl.biop.command.register.Elastix2DSplineRegisterCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.viewers.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.services.SourceBdvDisplayService;
import sc.fiji.bdvpg.services.SourceServices;

import java.util.List;


public class DemoRegistrationMultiChannelElastixSpline {

    static SourceAndConverter<?> fixedSource;

    static SourceAndConverter<?> movingSource;

    static final ImageJ ij = new ImageJ();

    static public void main(String... args) throws Exception {
        ij.ui().showUI();

        OpenerSettings atlasSettings = OpenerSettings.BioFormats()
                .location("src/test/resources/multichanreg/Atlas.tif")
                .millimeter().setSerie(0);

        AbstractSpimData<?> atlasDataset = OpenersToSpimData.getSpimData(atlasSettings);

        SourceServices.getSourceService().register(atlasDataset);
        List<SourceAndConverter<?>> atlasSources = SourceServices.getSourceService().getSourcesFromDataset(atlasDataset);

        OpenerSettings sliceSettings = OpenerSettings.BioFormats()
                .location("src/test/resources/multichanreg/Slice.tif")
                .millimeter().setSerie(0);

        AbstractSpimData<?> sliceDataset = OpenersToSpimData.getSpimData(sliceSettings);

        SourceServices.getSourceService().register(sliceDataset);
        List<SourceAndConverter<?>> sliceSources = SourceServices.getSourceService().getSourcesFromDataset(sliceDataset);

        SourceBdvDisplayService displayService = SourceServices.getBdvDisplayService();
        BdvHandle bdvh = displayService.getNewBdv();
        displayService.show(bdvh, atlasSources.toArray(new SourceAndConverter[0]));
        displayService.show(bdvh, sliceSources.toArray(new SourceAndConverter[0]));

        new ViewerTransformAdjuster(bdvh, atlasSources.toArray(new SourceAndConverter[0])).run();

        fixedSource = atlasSources.get(0);
        movingSource = sliceSources.get(0);

        ij.context()
                .getService(CommandService.class)
                .run(Elastix2DSplineRegisterCommand.class, true,
                        "sacs_fixed", atlasSources.toArray(new SourceAndConverter[0]),//new SourceAndConverter[]{fixedSource},//atlasSources.toArray(new SourceAndConverter[0]),//new SourceAndConverter[]{fixedSource},
                        "tp_fixed", 0,
                        "level_fixed_source", 0,
                        "sacs_moving", sliceSources.toArray(new SourceAndConverter[0]),//new SourceAndConverter[]{movingSource},
                        "tp_moving", 0,
                        "level_moving_source", 0,
                        "px_size_in_current_unit", 0.02,
                        "interpolate", false,
                        //"showImagePlusRegistrationResult", true,
                        "px", 0,
                        "py", 0,
                        "pz", 0,
                        "sx", 12,
                        "sy", 9
                );
    }

}
