package ch.epfl.biop.scijava.command.spimdata;

import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.n5.N5ImageLoader;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.bioformats.export.IntRangeParser;
import ch.epfl.biop.bdv.bioformats.export.ometiff.OMETiffPyramidizerExporter;
import ch.epfl.biop.sourceandconverter.SourceFuserAndResampler;
import ch.epfl.biop.sourceandconverter.SourceHelper;
import ij.IJ;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.sequence.Channel;
import net.imglib2.cache.LoaderCache;
import net.imglib2.cache.ref.BoundedSoftRefLoaderCache;
import org.apache.commons.io.FilenameUtils;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.scijava.services.ui.SourceAndConverterServiceUI;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
    boolean use_lzw_compression;

    @Parameter
    boolean split_slices = false;

    @Parameter
    boolean split_channels = false;

    @Parameter
    boolean split_frames = false;

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
        SourceAndConverter[] fusedSources = new SourceAndConverter[nChannels];

        for (int iChannel=0; iChannel<nChannels; iChannel++) {
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

            if ((split_channels==false)&&(split_slices==false)&&(split_frames==false)) {

                getDefaultBuilder()
                        .maxTilesInQueue(nThreads * 3)
                        .rangeC(range_channels)
                        .rangeZ(range_slices)
                        .rangeT(range_frames)
                        .nThreads(nThreads)
                        .savePath(output_path.getAbsolutePath())
                        .create(fusedSources).export();
            } else {
                int nC = fusedSources.length;
                int nZ = (int) fusedSources[0].getSpimSource().getSource(0,0).realMax(2);
                int nT = SourceAndConverterHelper.getMaxTimepoint(fusedSources);
                CZTSetIterator iterator = new CZTSetIterator(range_channels, range_slices, range_frames, split_channels, split_slices, split_frames, nC, nZ, nT);

                List<CZTSet> sets = new ArrayList<>();
                for (CZTSetIterator it = iterator; it.hasNext(); ) {
                    CZTSet set = it.next();
                    sets.add(set);
                }
                int nThreadPerTask;
                if (sets.size()>nThreads) {
                    nThreadPerTask = 1;
                } else {
                    nThreadPerTask = Math.min(nThreads/sets.size(), 1);
                }
                AtomicInteger counter = new AtomicInteger();
                int nExportedFiles = sets.size();
                Task export = taskService.createTask("Fuse and export " + this.xml_bigstitcher_file.getName());
                export.setProgressMaximum(nExportedFiles);
                try {
                    sets.parallelStream().forEach(set -> {
                        try {
                            IJ.log("Exporting " + set);
                            export(set.channels_set, set.slices_set, set.frames_set, nThreadPerTask, fusedSources);
                            synchronized (export) {
                                export.setProgressValue(counter.incrementAndGet());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                } finally {
                    export.run(() -> {});
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Cleanup
        sac_service.remove(node.sources());
    }

    static class CZTSet {
        String channels_set = "";
        String slices_set = "";
        String frames_set = "";

        public CZTSet() {

        }

        public CZTSet(CZTSet ini) {
            this.channels_set = ini.channels_set;
            this.slices_set = ini.slices_set;
            this.frames_set = ini.frames_set;
        }

        @Override
        public String toString() {
            return "C:\t"+channels_set+"\tZ:\t"+slices_set+"\tT\t:"+frames_set;
        }

    }

    static class CZTSetIterator implements Iterator<CZTSet> {

        CZTSet iniSet;

        List<Integer> lC = new ArrayList<>();
        List<Integer> lZ = new ArrayList<>();
        List<Integer> lT = new ArrayList<>();

        int iC = 0, iZ = -1, iT = 0, nC, nZ, nT;

        final boolean splitC,splitZ,splitT;

        CZTSetIterator(String range_channels, String range_slices, String range_frames,
                       boolean splitC, boolean splitZ, boolean splitT,
                       int nC, int nZ, int nT) throws Exception{
            iniSet = new CZTSet();

            this.splitC = splitC;
            this.splitZ = splitZ;
            this.splitT = splitT;

            iniSet.channels_set = range_channels;
            iniSet.slices_set = range_slices;
            iniSet.frames_set = range_frames;

            if (splitC) {
                lC = new IntRangeParser(range_channels).get(nC);
                this.nC = lC.size();
            } else this.nC = 1;
            if (splitZ) {
                lZ = new IntRangeParser(range_slices).get(nZ);
                this.nZ = lZ.size();
            } else this.nZ = 1;
            if (splitT) {
                lT = new IntRangeParser(range_frames).get(nT);
                this.nT = lT.size();
            } else this.nT = 1;
        }


        @Override
        public boolean hasNext() {
            return !((iC == nC-1)&&(iZ == nZ-1)&&(iT==nT-1));
        }

        @Override
        public CZTSet next() {
            CZTSet next = new CZTSet(iniSet);
            // T Z C
            iZ++;
            if (iZ==nZ) {
                iZ=0;
                iC++;
                if (iC==nC) {
                    iC=0;
                    iT++;
                    if (iT==nT) {
                        return null; // Done!
                    }
                }
            }
            if (splitC) next.channels_set = lC.get(iC).toString();
            if (splitZ) next.slices_set = lZ.get(iZ).toString();
            if (splitT) next.frames_set = lT.get(iT).toString();
            return next;
        }
    }

    private String getPath( String select_channels, String select_slices, String select_frames) {
        String path = output_path.getAbsolutePath();

        if (path.endsWith(".ome.tiff")) {
            path = path.substring(0, path.length()-9);
        } else if (path.endsWith(".ome.tif")) {
            path = path.substring(0, path.length()-8);
        } else path = FilenameUtils.removeExtension(path);

        if (split_channels) {path = path +"_C"+select_channels;}
        if (split_slices) {path = path +"_Z"+select_slices;}
        if (split_frames) {path = path +"_T"+select_frames;}

        path = path+".ome.tiff";

        return path;
    }

    private void export( String select_channels, String select_slices, String select_frames,
                        int nThreads, SourceAndConverter[] fusedSources) throws Exception {
        getDefaultBuilder()
                .nThreads(nThreads)
                .maxTilesInQueue(nThreads * 3)
                .rangeZ(select_slices)
                .rangeC(select_channels)
                .rangeT(select_frames)
                .savePath(getPath(select_channels, select_slices, select_frames))
                .create(fusedSources).export();
    }

    private OMETiffPyramidizerExporter.Builder getDefaultBuilder() {
        OMETiffPyramidizerExporter.Builder builder = OMETiffPyramidizerExporter.builder()
                .micrometer()
                .setPixelSize(vox_size_xy_micrometer, vox_size_xy_micrometer, vox_size_z_micrometer)
                .monitor(taskService)
                .downsample(2)
                .tileSize(1024, 1024)
                .nResolutionLevels(n_resolution_levels);

        if (use_lzw_compression) builder.lzw();

        return builder;
    }

}
