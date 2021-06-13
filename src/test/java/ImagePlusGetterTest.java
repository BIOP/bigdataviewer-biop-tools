import bdv.util.BdvFunctions;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.operetta.utils.HyperRange;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusGetter;
import ij.IJ;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import java.util.ArrayList;
import java.util.List;

public class ImagePlusGetterTest {


    static {
        LegacyInjector.preinit();
    }

    public static void main(String... args) {

        ImageJ ij = new ImageJ();
        ij.ui().showUI();
        //final String filePath = "src/test/resources/mri-stack.xml";

        //final String filePath = "D:/Operetta Dataset/Opertta Tiling Magda/MagdaData.xml";
        final String filePath = "N:/temp-romain/TL2_bdv.xml";
        // Import SpimData
        SpimDataFromXmlImporter importer = new SpimDataFromXmlImporter(filePath);
        //importer.run();

        final AbstractSpimData spimData = importer.get();

        List<SourceAndConverter> allSources = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(spimData);

        // Creates a BdvHandle
        //BdvHandle bdvHandle = SourceAndConverterServices
        //        .getSourceAndConverterDisplayService().getActiveBdv();
        /*SourceAndConverterServices
                .getSourceAndConverterDisplayService()
                .show(sac);*/
        ArrayList<SourceAndConverter> sources = new ArrayList<>();
        sources.add(allSources.get(0));
        sources.add(allSources.get(1));


        //ImagePlusGetter.getImagePlus("TestMri", rai).show();
        HyperRange range = ImagePlusGetter
                .fromSources(sources,0,0)
                //.setRangeC("1")
                //.setRangeT("0")
                .build();

        //ImagePlusGetter.getImagePlus("TestMri_Sac", sources, 0, range, true).show();
        ImagePlusGetter.getVirtualImagePlus("TestMri_Sac", sources, 0, range, true, true).show();
    }
}
