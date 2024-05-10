package ch.epfl.biop.scijava.command.spimdata;

import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.n5.N5ImageLoader;
import bdv.util.source.alpha.AlphaSource;
import bdv.util.source.alpha.AlphaSourceDistanceL1RAI;
import bdv.util.source.alpha.AlphaSourceHelper;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.kheops.ometiff.OMETiffExporter;
import ch.epfl.biop.sourceandconverter.SourceFuserAndResampler;
import ch.epfl.biop.sourceandconverter.SourceHelper;
import ch.epfl.biop.sourceandconverter.exporter.IntRangeParser;
import ij.IJ;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.cache.LoaderCache;
import net.imglib2.cache.ref.BoundedSoftRefLoaderCache;
import net.imglib2.realtransform.AffineTransform3D;
import ome.codecs.CompressionType;
import ome.units.UNITS;
import org.apache.commons.io.FilenameUtils;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static sc.fiji.bdvpg.services.ISourceAndConverterService.SPIM_DATA_INFO;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDVDataset>Fuse a BigStitcher dataset to OME-Tiff")
public class FuseBigStitcherDatasetIntoOMETiffCommand implements Command {

    @Parameter(label = "BigStitcher XML file", style = "open")
    File xml_bigstitcher_file;

    @Parameter(label = "Output folder", style ="directory")
    File output_path_directory;

    @Parameter( label = "Selected channels. Leave blank for all", required = false )
    String range_channels = "";

    @Parameter( label = "Selected slices. Leave blank for all", required = false )
    String range_slices = "";

    @Parameter( label = "Selected timepoints. Leave blank for all", required = false )
    String range_frames = "";

    @Parameter(label = "Number of resolution levels (scale factor = 2)", min = "1")
    int n_resolution_levels;

    @Parameter(label = "Fusion method", choices = {"SMOOTH "+AlphaFusedResampledSource.AVERAGE, AlphaFusedResampledSource.MAX, AlphaFusedResampledSource.AVERAGE})
    String fusion_method;

    @Parameter(label = "Use LZW compression")
    boolean use_lzw_compression;

    @Parameter(label = "Split slices")
    boolean split_slices = false;

    @Parameter(label = "Split channels")
    boolean split_channels = false;

    @Parameter(label = "Split frames")
    boolean split_frames = false;

    @Parameter(label = "Use custom XY/Z anisotropy ratio")
    boolean override_z_ratio = false;

    @Parameter(label = "XY/Z anisotropy ratio")
    double z_ratio = 1.0;

    @Parameter(label = "Interpolate when fusing (~4x slower)")
    boolean use_interpolation = false;

    double vox_size_x_micrometer = 1;

    double vox_size_y_micrometer = 1;

    double vox_size_z_micrometer = 1;

    @Parameter
    SourceAndConverterService sac_service;

    @Parameter
    TaskService taskService;

    File output_path;

    @Override
    public void run() {

        if (n_resolution_levels<1) {
            System.err.println("Invalid number of resolution, minimum = 1");
        }

        output_path_directory.mkdirs();

        output_path = new File(output_path_directory,FilenameUtils.removeExtension(xml_bigstitcher_file.getName())+".ome.tiff");

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

        List<SourceAndConverter<?>> allSources = sac_service.getSourceAndConverterFromSpimdata(asd);

        // Gets Z-ratio from the first source

        AffineTransform3D transform = new AffineTransform3D();
        allSources.get(0).getSpimSource().getSourceTransform(0,0,transform);
        //channelNodes.get(0).sources()[0].getSpimSource().getSourceTransform(0,0,transform);
        IJ.log("Transform of first source = "+transform);
        // The sample can be rotated. Here we try to get the max value of the line for each voxel
        double voxSX = Math.max(Math.max(Math.abs(transform.get(0,0)), Math.abs(transform.get(0,1))), Math.abs(transform.get(0,2)));
        double voxSY = Math.max(Math.max(Math.abs(transform.get(1,0)), Math.abs(transform.get(1,1))), Math.abs(transform.get(1,2)));
        double voxSZ = Math.max(Math.max(Math.abs(transform.get(2,0)), Math.abs(transform.get(2,1))), Math.abs(transform.get(2,2)));

        IJ.log("Pixel size X (in pixel, so it should be one) = "+voxSX);
        IJ.log("Pixel size Y (in pixel, so it should be one) = "+voxSY);
        if (voxSX!=1) IJ.log("Not ONE! ");
        IJ.log("Pixel size Z (in pixel, equals to the anisotropy ratio) = "+voxSZ);
        if (!override_z_ratio) z_ratio = voxSZ;
        if (voxSX!=voxSY) IJ.log("You probably performed a rotation, XY will be resampled with an equal pixel size");
        IJ.log("Anisotropy ratio used = "+z_ratio);

        VoxelDimensions voxelDimensions = ((BasicViewSetup)asd.getSequenceDescription().getViewSetups().get(0)).getVoxelSize();
        String unit = voxelDimensions.unit();
        IJ.log("Units is assumed to be micrometers, and it is "+unit);
        vox_size_x_micrometer = voxelDimensions.dimension(0);
        vox_size_y_micrometer = voxelDimensions.dimension(1);
        vox_size_z_micrometer = voxelDimensions.dimension(2);

        // Create a model source
        SourceAndConverter model = SourceHelper.getModelFusedMultiSources(allSources.toArray(new SourceAndConverter[0]),
                0, SourceAndConverterHelper.getMaxTimepoint(allSources.toArray(new SourceAndConverter[0])),
                1, 1, z_ratio,
                1,
                1,1,1, "Model");

        // For fusion with smooth edges:

        if (fusion_method.equals("SMOOTH "+AlphaFusedResampledSource.AVERAGE)) {
            for (SourceAndConverter<?> source: allSources) {
                AlphaSource alpha = new AlphaSourceDistanceL1RAI(source.getSpimSource(), (float) vox_size_x_micrometer,(float) vox_size_y_micrometer, (float) vox_size_z_micrometer);
                AlphaSourceHelper.setAlphaSource(source, alpha);
            }
        }

        Map<Integer, List<SourceAndConverter<?>>> channelToSources = new HashMap<>();

        allSources.forEach(source -> {
            SourceAndConverterService.SpimDataInfo sdi = (SourceAndConverterService.SpimDataInfo) sac_service.getMetadata(source, SPIM_DATA_INFO);
            // source
            int channelId = ((BasicViewSetup)asd.getSequenceDescription().getViewSetups().get(sdi.setupId)).getAttribute(Channel.class).getId();
            if (!channelToSources.containsKey(channelId)) {
                channelToSources.put(channelId, new ArrayList<>());
            }
            channelToSources.get(channelId).add(source);
        });

        //int nChannels =
        int[] channels = channelToSources.keySet().stream().sorted().mapToInt(Integer::intValue).toArray();
        SourceAndConverter[] fusedSources = new SourceAndConverter[channels.length];

        for (int iChannel=0; iChannel<channels.length; iChannel++) {
            // Fuse and resample
            fusedSources[iChannel] =
                    new SourceFuserAndResampler(channelToSources.get(channels[iChannel]),
                            fusion_method.contains("AVERAGE") ? AlphaFusedResampledSource.AVERAGE: AlphaFusedResampledSource.MAX,
                            model,
                            xml_bigstitcher_file.getName()+"_Channel"+iChannel,
                            false, true,
                            use_interpolation, 0, 128, 128, 1, nThreads*10, nThreads).get();
        }

        // OME Tiff exporter
        try {

            if ((!split_channels)&&(!split_slices)&&(!split_frames)) {

                OMETiffExporter.OMETiffExporterBuilder.WriterOptions.WriterOptionsBuilder exporter = OMETiffExporter.builder()
                        .put(fusedSources)
                        .defineMetaData(xml_bigstitcher_file.getName())
                        .putMetadataFromSources(fusedSources, UNITS.MICROMETER)
                        .voxelPhysicalSizeMicrometer(vox_size_x_micrometer, vox_size_y_micrometer, vox_size_z_micrometer)
                        .defineWriteOptions()
                        .nThreads(nThreads)
                        .maxTilesInQueue(nThreads * 3)
                        .rangeC(range_channels)
                        .rangeZ(range_slices)
                        .rangeT(range_frames)
                        .monitor(taskService)
                        .downsample(2)
                        .tileSize(1024, 1024)
                        .nResolutionLevels(n_resolution_levels)
                        .savePath(output_path.getAbsolutePath());

                if (use_lzw_compression) {
                    exporter.lzw();
                } else {
                    exporter.compression(CompressionType.UNCOMPRESSED.getCompression());
                }

                exporter.create().export();


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
        sac_service.remove(allSources.toArray(new SourceAndConverter[0]));
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

        OMETiffExporter.OMETiffExporterBuilder.WriterOptions.WriterOptionsBuilder exporter = OMETiffExporter.builder()
                .put(fusedSources)
                .defineMetaData(xml_bigstitcher_file.getName())
                .putMetadataFromSources(fusedSources, UNITS.MICROMETER)
                .voxelPhysicalSizeMicrometer(vox_size_x_micrometer, vox_size_y_micrometer, vox_size_z_micrometer)
                .defineWriteOptions()
                .nThreads(nThreads)
                .maxTilesInQueue(nThreads * 3)
                .rangeC(select_channels)
                .rangeZ(select_slices)
                .rangeT(select_frames)
                .monitor(taskService)
                .downsample(2)
                .tileSize(1024, 1024)
                .nResolutionLevels(n_resolution_levels)
                .savePath(getPath(select_channels, select_slices, select_frames));

        if (use_lzw_compression) exporter.lzw();

        exporter.create().export();


    }

    /*private OMETiffPyramidizerExporter.Builder getDefaultBuilder() {

        OMETiffExporter.builder().put()

        OMETiffPyramidizerExporter.Builder builder = OMETiffPyramidizerExporter.builder()
                .micrometer()
                .setPixelSize(vox_size_x_micrometer, vox_size_y_micrometer, vox_size_z_micrometer)
                .monitor(taskService)
                .downsample(2)
                .tileSize(1024, 1024)
                .nResolutionLevels(n_resolution_levels);

        if (use_lzw_compression) builder.lzw();

        return builder;
    }*/

}
