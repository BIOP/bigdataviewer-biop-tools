package lazypyramidize;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.OpenersToSpimData;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import ch.epfl.biop.sourceandconverter.SourceHelper;
import loci.common.DebugTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.util.List;

public class DemoLazyPyramid {
    public static void main(String... args) {
        final net.imagej.ImageJ ij = new ImageJ();

        DebugTools.enableLogging ("OFF");
        ij.ui().showUI();

        BdvHandle bdvh = SourceAndConverterServices.getBdvDisplayService().getNewBdv();

        OpenerSettings atlasSettings = OpenerSettings.BioFormats()
                .location("N:\\temp-Nico\\Slide_Demo_Downscale.tif")
                //.location("C:\\Users\\chiarutt\\Dropbox\\BIOP\\bigdataviewer-biop-tools\\src\\test\\resources\\multichanreg\\Atlas.tif")
                .millimeter().setSerie(0);

        AbstractSpimData<?> dataset = OpenersToSpimData.getSpimData(atlasSettings);

        SourceAndConverterServices.getSourceAndConverterService().register(dataset);

        List<SourceAndConverter<?>> sources = SourceAndConverterServices.getSourceAndConverterService().getSourceAndConverterFromSpimdata(dataset);

        SourceAndConverterServices.getSourceAndConverterService().register(SourceHelper.lazyPyramidizeXY2((SourceAndConverter)sources.get(0)));

    }
}
