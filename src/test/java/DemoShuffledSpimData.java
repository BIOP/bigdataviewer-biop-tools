import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.spimdata.reordered.LifReOrdered;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.util.List;


/**
 * NOT WORKING!! A Clone for affine transform or for the outofbounds stuff should be pu somewhere
 *
 * TO FIX (if the source is resampled, as in ABBA, that works however)
 *
 */
public class DemoShuffledSpimData {

    static {
        LegacyInjector.preinit();
    }

    static final ImageJ ij = new ImageJ();

    static public void main(String... args) throws Exception {

        ij.ui().showUI();

        System.out.println(LifReOrdered.class.getSimpleName());
        // load and convert the famous blobs image// Gets active BdvHandle instance
        BdvHandle bdv = SourceAndConverterServices.getBdvDisplayService().getActiveBdv();
        // Import SpimData
        //SpimDataFromXmlImporter importer = new SpimDataFromXmlImporter("N:\\Temp Oli\\Kunal\\lifkunal-nico_v3.xml");
        //importer.run();
        //AbstractSpimData asd = importer.get();

        //final List<SourceAndConverter> sacs = SourceAndConverterServices.getSourceAndConverterService().getSourceAndConverters();

        /*sacs.forEach( sac -> {
            SourceAndConverterServices.getBdvDisplayService().show( bdv, sac );
            //new ViewerTransformAdjuster( bdv, sac ).run();
            //new BrightnessAutoAdjuster( sac, 0 ).run();
        } );

        new ViewerTransformAdjuster( bdv, sacs.get(0) ).run();*/

        System.out.println("Reordering dataset");
        LifReOrdered kd = new LifReOrdered("N:\\Temp Oli\\Kunal\\lifkunal-nico_v3.xml",16,4);
        kd.initialize();
        AbstractSpimData reshuffled = kd.constructSpimData();

        System.out.println("Registering reordered dataset");
        SourceAndConverterServices.getSourceAndConverterService().register(reshuffled);

        System.out.println("Showing reordered dataset");

        final List<SourceAndConverter<?>> sacsReordered = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(reshuffled);

        sacsReordered.forEach( sac -> {
            System.out.println(sac.getSpimSource().getName());
            SourceAndConverterServices.getBdvDisplayService().show( bdv, sac );
            //new ViewerTransformAdjuster( bdv, sac ).run();
            //new BrightnessAutoAdjuster( sac, 0 ).run();
        } );

        /*SourceAndConverterServices.getBdvDisplayService().show( bdv, sacsReordered.get(0) );
        SourceAndConverterServices.getBdvDisplayService().show( bdv, sacsReordered.get(1) );
        SourceAndConverterServices.getBdvDisplayService().show( bdv, sacsReordered.get(2) );
        SourceAndConverterServices.getBdvDisplayService().show( bdv, sacsReordered.get(3) );*/
        new ViewerTransformAdjuster( bdv, sacsReordered.get(0) ).run();

        System.out.println("Showing reordered dataset - DONE");

        /*RandomAccessibleInterval nonResliced = sacs.get(0).getSpimSource().getSource(0,0);

        ExtendedRandomAccessibleInterval rai = SlicerViews.extendSlicer(nonResliced,2,0);

        // TODO : Fix! This does not work!
        BdvFunctions.show(
        Views.interval(rai,
                new FinalInterval(nonResliced.dimension(0)*nonResliced.dimension(2),
                        nonResliced.dimension(1),
                        nonResliced.dimension(2))
                       ),
                "Sliced"
                );*/

    }

}
