package process;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceHelper;
import ch.epfl.biop.sourceandconverter.SourceVoxelProcessor;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import sc.fiji.bdvpg.scijava.command.source.LUTSourceCreatorCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.importer.VoronoiSourceGetter;

public class DemoCachedBorders {
    public static void main(String... args) throws Exception {
        final net.imagej.ImageJ ij = new ImageJ();

        DebugTools.enableLogging ("OFF");
        ij.ui().showUI();

        SourceAndConverter<FloatType> voronoi = new VoronoiSourceGetter(new long[]{4096*2, 4096*2, 4096*2}, 100000, false).get();

        BdvHandle bdvh = SourceAndConverterServices.getBdvDisplayService().getNewBdv();

        SourceAndConverter<UnsignedByteType> borders = SourceVoxelProcessor.getBorders(voronoi);

        SourceAndConverter<?>[] reColoredVoronoi = (SourceAndConverter<?>[])
                ij.module().run(ij.command().getCommand(LUTSourceCreatorCommand.class), true,
                        "sacs", new SourceAndConverter[]{voronoi}
                ).get().getOutput("sacs_out");

        SourceAndConverterServices.getBdvDisplayService().show(bdvh, reColoredVoronoi[0]);
        SourceAndConverterServices
                .getSourceAndConverterService()
                        .getConverterSetup(reColoredVoronoi[0]).setDisplayRange(0,1280);

        SourceAndConverterServices
                .getSourceAndConverterService()
                .getConverterSetup(borders).setDisplayRange(0,256);

        SourceAndConverterServices.getBdvDisplayService().show(bdvh, borders);

        SourceAndConverter<UnsignedByteType> smoothenedBorders = SourceHelper.lazyPyramidizeXY2(borders);

        SourceAndConverterServices
                .getSourceAndConverterService()
                .getConverterSetup(smoothenedBorders).setDisplayRange(0,256);


        SourceAndConverterServices
                .getSourceAndConverterService()
                .getConverterSetup(smoothenedBorders).setColor(new ARGBType(ARGBType.rgba(128, 255, 120, 60)));

        SourceAndConverterServices.getBdvDisplayService().show(bdvh, smoothenedBorders);

    }
}
