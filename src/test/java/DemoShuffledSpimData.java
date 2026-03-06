import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.dataset.reordered.LifReOrdered;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.service.SourceServices;

import java.util.List;


/**
 * NOT WORKING!! A Clone for affine transform or for the outofbounds stuff should be put somewhere
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
        BdvHandle bdv = SourceServices.getBdvDisplayService().getActiveBdv();

        System.out.println("Reordering dataset");
        LifReOrdered kd = new LifReOrdered("N:\\Temp Oli\\Kunal\\lifkunal-nico_v3.xml",16,4);
        kd.initialize();
        AbstractSpimData<?> reshuffled = kd.constructSpimData();

        System.out.println("Registering reordered dataset");
        SourceServices.getSourceService().register(reshuffled);

        System.out.println("Showing reordered dataset");

        final List<SourceAndConverter<?>> sourcesReordered = SourceServices
                .getSourceService()
                .getSourcesFromDataset(reshuffled);

        sourcesReordered.forEach( source -> {
            System.out.println(source.getSpimSource().getName());
            SourceServices.getBdvDisplayService().show( bdv, source );
        } );

        new ViewerTransformAdjuster( bdv, sourcesReordered.get(0) ).run();

        System.out.println("Showing reordered dataset - DONE");

    }

}
