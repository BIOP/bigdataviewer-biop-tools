package process;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.SourceHelper;
import ch.epfl.biop.source.SourceVoxelProcessor;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import sc.fiji.bdvpg.command.process.SourceDuplicateWithLUTCommand;
import sc.fiji.bdvpg.services.SourceServices;
import sc.fiji.bdvpg.source.importer.VoronoiSourceGetter;

public class DemoCachedBorders {
    public static void main(String... args) throws Exception {
        final net.imagej.ImageJ ij = new ImageJ();
         // This has been transfered to DEMO, except for lazy pyramidize
        DebugTools.enableLogging ("OFF");
        ij.ui().showUI();

        SourceAndConverter<FloatType> voronoi = new VoronoiSourceGetter(new long[]{4096*2, 4096*2, 4096*2}, 100000, false).get();

        BdvHandle bdvh = SourceServices.getBdvDisplayService().getNewBdv();

        SourceAndConverter<UnsignedByteType> borders = SourceVoxelProcessor.getBorders(voronoi);

        SourceAndConverter<?>[] reColoredVoronoi = (SourceAndConverter<?>[])
                ij.module().run(ij.command().getCommand(SourceDuplicateWithLUTCommand.class), true,
                        "sources", new SourceAndConverter[]{voronoi}
                ).get().getOutput("sources_out");

        SourceServices.getBdvDisplayService().show(bdvh, reColoredVoronoi[0]);
        SourceServices
                .getSourceService()
                        .getConverterSetup(reColoredVoronoi[0]).setDisplayRange(0,1280);

        SourceServices
                .getSourceService()
                .getConverterSetup(borders).setDisplayRange(0,256);

        SourceServices.getBdvDisplayService().show(bdvh, borders);

        SourceAndConverter<UnsignedByteType> smoothenedBorders = SourceHelper.lazyPyramidizeXY2(borders);

        SourceServices
                .getSourceService()
                .getConverterSetup(smoothenedBorders).setDisplayRange(0,256);


        SourceServices
                .getSourceService()
                .getConverterSetup(smoothenedBorders).setColor(new ARGBType(ARGBType.rgba(128, 255, 120, 60)));

        SourceServices.getBdvDisplayService().show(bdvh, smoothenedBorders);

    }
}
