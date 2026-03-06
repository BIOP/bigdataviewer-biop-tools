import bdv.util.*;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.SourceHelper;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.services.SourceServices;
import sc.fiji.bdvpg.dataset.importer.XMLToDatasetImporter;


public class Resample3DAlongAxis {

    static {
        LegacyInjector.preinit();
    }

    public static void main(String... args) {
        // Initializes static SourceService and Display Service

        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        final String filePath = "src/test/resources/mri-stack.xml";
        // Import SpimData
        XMLToDatasetImporter importer = new XMLToDatasetImporter(filePath);
        //importer.run();

        final AbstractSpimData spimData = importer.get();

        SourceAndConverter source = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData)
                .get(0);

        // Creates a BdvHandle
        //BdvHandle bdvHandle = SourceServices
        //        .getSourceAndConverterDisplayService().getActiveBdv();
        /*SourceServices
                .getSourceAndConverterDisplayService()
                .show(source);*/

        AffineTransform3D m = new AffineTransform3D();

        source.getSpimSource().getSourceTransform(0,0,m);

        RandomAccessibleIntervalSource<UnsignedShortType> rais;

        // DO NOT WORK
         rais = new RandomAccessibleIntervalSource<UnsignedShortType>(
                Views.expandZero(source.getSpimSource().getSource(0,0),0,0,0), // even though we don't care about the size of the border, this helps set the dimension
                new UnsignedShortType(),
                m,
                "RAIS"
        );

        RealPoint pt1 = new RealPoint(3);
        pt1.setPosition(new double[]{100,100,100});

        RealPoint pt2 = new RealPoint(3);
        pt2.setPosition(new double[]{130,150,150});

        Source alignedAlongZ = SourceHelper.AlignAxisResample(rais, pt1, pt2, 0.5, 300, 300, 20, true, true);

        RandomAccessibleInterval rai = alignedAlongZ.getSource(0,0);

        rai = Views.rotate(rai,0,1);

        ImageJFunctions.show(rai);
    }


}
