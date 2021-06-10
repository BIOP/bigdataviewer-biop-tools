import bdv.util.BdvFunctions;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusGetter;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

public class ImagePlusGetterTest {


    static {
        LegacyInjector.preinit();
    }

    public static void main(String... args) {

        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        final String filePath = "src/test/resources/mri-stack.xml";
        // Import SpimData
        SpimDataFromXmlImporter importer = new SpimDataFromXmlImporter(filePath);
        //importer.run();

        final AbstractSpimData spimData = importer.get();

        SourceAndConverter sac = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(spimData)
                .get(0);

        // Creates a BdvHandle
        //BdvHandle bdvHandle = SourceAndConverterServices
        //        .getSourceAndConverterDisplayService().getActiveBdv();
        /*SourceAndConverterServices
                .getSourceAndConverterDisplayService()
                .show(sac);*/

        AffineTransform3D m = new AffineTransform3D();

        sac.getSpimSource().getSourceTransform(0,0,m);

        RandomAccessibleInterval rai = sac.getSpimSource().getSource(0,0);

        ImagePlusGetter.getImagePlus("TestMri", rai).show();

    }
}
