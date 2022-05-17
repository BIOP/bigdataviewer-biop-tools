package ch.epfl.biop.scijava.command.spimdata;

import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.n5.N5ImageLoader;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.bioformats.export.ometiff.OMETiffPyramidizerExporter;
import ch.epfl.biop.sourceandconverter.SourceFuserAndResampler;
import ch.epfl.biop.sourceandconverter.SourceHelper;
import ij.IJ;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.sequence.Channel;
import net.imglib2.cache.LoaderCache;
import net.imglib2.cache.ref.BoundedSoftRefLoaderCache;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.TaskService;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.scijava.services.ui.SourceAndConverterServiceUI;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDVDataset>Fuse a BigStitcher dataset to OME-Tiff")
public class FuseBigStitcherDatasetIntoOMETiffCommand implements Command {

    @Parameter
    File xml_bigstitcher_file;

    @Parameter(style ="save")
    File output_path;

    @Parameter( label = "Selected Channels. Leave blank for all", required = false )
    String range_channels = "";

    @Parameter( label = "Selected Slices. Leave blank for all", required = false )
    String range_slices = "";

    @Parameter( label = "Selected Timepoints. Leave blank for all", required = false )
    String range_frames = "";

    @Parameter(label = "Number of resolution levels (scale factor = 2)")
    int n_resolution_levels;

    @Parameter
    double vox_size_xy_micrometer = 1;

    @Parameter
    double vox_size_z_micrometer = 1;

    @Parameter
    double z_ratio = 10;

    @Parameter
    SourceAndConverterService sac_service;

    @Parameter
    TaskService taskService;

    @Override
    public void run() {
        AbstractSpimData asd = new SpimDataFromXmlImporter(xml_bigstitcher_file).get();

        BasicImgLoader imageLoader = asd.getSequenceDescription().getImgLoader();
        /*
        if the PR https://github.com/bigdataviewer/bigdataviewer-core/pull/137 is accepted:
        if (imageLoader instanceof CacheOverrider) {
            CacheOverrider co = (CacheOverrider) imageLoader;
            SharedQueue queue = new SharedQueue(numberOfFetcherThreads, numberOfPriorities);
            LoaderCache loaderCache;
            if (maxNumberOfCells>0) {
                loaderCache = new BoundedSoftRefLoaderCache<>(maxNumberOfCells);
            } else {
                loaderCache = new SoftRefLoaderCache();
            }
            VolatileGlobalCellCache cache = new VolatileGlobalCellCache(queue, loaderCache);
            co.setCache(cache);
        } else {
            IJ.log("Can't override cache with image loader type: "+imageLoader.getClass().getName());
        }*/
        // ---------------------- Hacking through reflection while waiting for the PR

        int nThreads = Runtime.getRuntime().availableProcessors()-1;
        int maxNumberOfCells  = nThreads * 200;

        LoaderCache  loaderCache = new BoundedSoftRefLoaderCache<>(maxNumberOfCells);
        VolatileGlobalCellCache cache = new VolatileGlobalCellCache(nThreads, 5);
        // Now override the backingCache field of the VolatileGlobalCellCache
        try {
            Field backingCacheField = VolatileGlobalCellCache.class.getDeclaredField("backingCache");
            backingCacheField.setAccessible(true);
            backingCacheField.set(cache,loaderCache);
            // Now overrides the cache in the ImageLoader
            if (imageLoader instanceof Hdf5ImageLoader) {
                Field cacheField = Hdf5ImageLoader.class.getDeclaredField("cache");
                cacheField.setAccessible(true);
                cacheField.set(imageLoader,cache);
            } else if (imageLoader instanceof N5ImageLoader) {
                Field cacheField = N5ImageLoader.class.getDeclaredField("cache");
                cacheField.setAccessible(true);
                cacheField.set(imageLoader,cache);
            } else {
                IJ.log("Can't override cache with image loader type: "+imageLoader.getClass().getName());
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        SourceAndConverterServiceUI.Node node = sac_service.getUI().getRoot().child(xml_bigstitcher_file.getName());

        List<SourceAndConverterServiceUI.Node> channelNodes = node.child(Channel.class.getSimpleName()).children();

        // Create a model source
        SourceAndConverter model = SourceHelper.getModelFusedMultiSources(channelNodes.get(0).sources(),
                0, SourceAndConverterHelper.getMaxTimepoint(channelNodes.get(0).sources()),
                1, z_ratio,
                1,
                1,1,"Model");

        int nChannels = channelNodes.size();
        System.out.println("There are "+nChannels+" channels");
        SourceAndConverter[] fusedSources = new SourceAndConverter[nChannels];

        for (int iChannel=0; iChannel<nChannels; iChannel++) {
            System.out.println("Channel "+iChannel+" there are "+channelNodes.get(iChannel).sources().length+" sources");
            // Fuse and resample
            fusedSources[iChannel] =
                    new SourceFuserAndResampler(Arrays.asList(channelNodes.get(iChannel).sources()),
                            AlphaFusedResampledSource.AVERAGE,
                            model,
                            xml_bigstitcher_file.getName()+"_Channel"+iChannel,
                            false, true,
                            false, 0, 1024, 1024, 1, nThreads*10, nThreads).get();
        }

        // OME Tiff exporter
        try {
            OMETiffPyramidizerExporter.builder()
                    .micrometer()
                    .setPixelSize(vox_size_xy_micrometer, vox_size_xy_micrometer, vox_size_z_micrometer)
                    .monitor(taskService)
                    .lzw()
                    .maxTilesInQueue(nThreads*3)
                    .rangeZ(range_slices)
                    .rangeC(range_channels)
                    .rangeT(range_frames)
                    .downsample(2)
                    .tileSize(1024, 1024)
                    .nThreads(nThreads)
                    .savePath(output_path.getAbsolutePath())
                    .nResolutionLevels(n_resolution_levels)
                    .create(fusedSources).export();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Cleanup
        sac_service.remove(node.sources());


    }
}
