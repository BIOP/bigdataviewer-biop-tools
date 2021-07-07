import bdv.util.coordinates.VectorFieldSource;
import bdv.util.coordinates.CoordinateSource;
import bdv.viewer.SourceAndConverter;
import loci.common.DebugTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import java.util.ArrayList;
import java.util.List;

public class TestCoordinatesSource {

    public static void main(String... args) {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();
        final String filePath = "src/test/resources/mri-stack.xml";

        DebugTools.enableIJLogging(true);

        //final String filePath = "src/test/resources/mitosis.xml";
        DebugTools.enableIJLogging(true);
        DebugTools.enableLogging("DEBUG");

        //final String filePath = "D:/Operetta Dataset/Opertta Tiling Magda/MagdaData.xml";
        //final String filePath = "N:/temp-romain/TL2_bdv.xml";
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

        if (allSources.size()>1) {
            sources.add(allSources.get(1));
        }

        VectorFieldSource cs = new VectorFieldSource(sources.get(0).getSpimSource(), "Coordinates");

        CoordinateSource sourceX = new CoordinateSource(cs, 0);
        CoordinateSource sourceY = new CoordinateSource(cs, 1);
        CoordinateSource sourceZ = new CoordinateSource(cs, 2);

        SourceAndConverterServices
                .getSourceAndConverterService()
                .register(new SourceAndConverter(sourceX, SourceAndConverterHelper.createConverter(sourceX)));

        /*SourceAndConverterServices
                .getSourceAndConverterService()
                .register(sacY);

        SourceAndConverterServices
                .getSourceAndConverterService()
                .register(sacZ);*/
    }
}
