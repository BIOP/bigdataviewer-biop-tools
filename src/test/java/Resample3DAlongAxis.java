import bdv.util.*;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceHelper;
import ij.ImagePlus;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;


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

        RandomAccessibleIntervalSource<UnsignedByteType> rais;

        // DO NOT WORK
         rais = new RandomAccessibleIntervalSource<UnsignedByteType>(
                Views.expandZero(sac.getSpimSource().getSource(0,0),0,0,0), // even though we don't care about the size of the border, this helps set the dimension
                new UnsignedByteType(),
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
