package process;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceVoxelProcessor;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.real.FloatType;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.importer.VoronoiSourceGetter;

public class DemoCachedBorders {
    public static void main(String... args) {
        final net.imagej.ImageJ ij = new ImageJ();

        DebugTools.enableLogging ("OFF");
        ij.ui().showUI();

        SourceAndConverter<FloatType> labels = new VoronoiSourceGetter(new long[]{1280, 1280, 128}, 2500, false).get();

        BdvHandle bdvh = SourceAndConverterServices.getBdvDisplayService().getNewBdv();

        //SourceAndConverterServices.getBdvDisplayService().show(bdvh, labels);

        SourceAndConverterServices.getBdvDisplayService().show(bdvh, SourceVoxelProcessor.getBorders(labels));

    }
}
