package ch.epfl.biop.registration.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.kheops.ometiff.OMETiffExporter;
import ch.epfl.biop.registration.RegistrationPair;
import ch.epfl.biop.sourceandconverter.processor.SourcesProcessor;
import ij.IJ;
import ome.units.UNITS;
import org.apache.commons.io.FilenameUtils;
import org.scijava.Context;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.TaskService;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Register Pair - Export registration to OME-TIFF",
        description = "If properly defined, exports the current registration as an OME-TIFF file."  )
public class PairRegistrationExportToOMETIFFCommand implements Command {

    @Parameter
    Context ctx;

    @Parameter
    RegistrationPair registration_pair;

    @Parameter(label = "Interpolate pixels values")
    boolean interpolate;

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String message = "If you include channels of the fixed image, the pixel type should match those of the moving one";

    @Parameter(label = "Fixed image channels (comma separated, empty for none, '*' for all)")
    String channels_fixed_csv;

    @Parameter(label = "Moving image channels (comma separated, empty for none, '*' for all)")
    String channels_moving_csv;

    @Parameter(style = "save")
    File file_path;

    @Parameter
    LogService ls;

    @Parameter(label = "Number of resolution levels")
    int n_resolution_levels = 4;

    @Parameter(label = "Scaling factor between resolution levels")
    int downscaling = 2;

    @Parameter(label = "Tile Size X (negative: no tiling)")
    int tile_size_x = 512;

    @Parameter(label = "Tile Size Y (negative: no tiling)")
    int tile_size_y = 512;

    @Parameter(label = "Number of threads (0 = serial)")
    int n_threads = 8;

    @Parameter(label = "Compression type", choices = {"LZW", "Uncompressed", "JPEG-2000", "JPEG-2000 Lossy", "JPEG"})
    String compression = "LZW";

    @Parameter(label = "Compress temporary files (save space on drive during pyramid building)")
    boolean compress_temp_files = false;

    @Parameter
    TaskService taskService;

    @Override
    public void run() {

        if (file_path.exists()) {
            ls.warn("Export file path already exists, the export will not be performed.");
            return;
        }
        if (channels_fixed_csv.trim().equals("*")) {
            channels_fixed_csv = "0";
            for (int i = 1; i<registration_pair.getFixedSources().length; i++) {
                channels_fixed_csv+=","+i;
            }
        }
        if (channels_moving_csv.trim().equals("*")) {
            channels_moving_csv = "0";
            for (int i = 1; i<registration_pair.getMovingSourcesOrigin().length; i++) {
                channels_moving_csv+=","+i;
            }
        }

        SourceAndConverter<?>[] fixed_sources = null, moving_sources = null;

        if ((channels_fixed_csv != null) && (!channels_fixed_csv.trim().isEmpty())) {
            fixed_sources = getSourcesProcessorFixed().apply(registration_pair.getFixedSources());
        }
        if ((channels_moving_csv != null) && (!channels_moving_csv.trim().isEmpty())) {
            moving_sources = getSourcesProcessorMoving().apply(registration_pair.getMovingSourcesRegistered());
        }

        int nSources = ((fixed_sources == null)?0: fixed_sources.length) + ((moving_sources == null)?0: moving_sources.length);

        if (nSources == 0) {
            ls.warn("No source is defined in the export command - skipping export.");
            return;
        }

        List<SourceAndConverter<?>> exportedSources;

        if (fixed_sources!=null) {
            exportedSources = new ArrayList<>(Arrays.asList(fixed_sources)); // Already adds all fixed sources - no need to change anything
        } else {
            exportedSources = new ArrayList<>();
        }

        // TODO do a pixel type matching check

        // Now let's add the moving sources resamples like the fixed sources
        // We take the first source of the fixed sources as the model
        SourceAndConverter<?> modelSource = registration_pair.getFixedSources()[0];

        if (moving_sources!=null) {
            for (SourceAndConverter<?> source : moving_sources) {
                exportedSources.add(
                        new SourceResampler(source,
                                modelSource,
                                source.getSpimSource().getName() + "_Registered",
                                false, false,
                                interpolate, 0).get());
            }
        }

        try {
            String imageName = FilenameUtils.removeExtension(file_path.getName());
            if (imageName.endsWith(".ome")) {
                imageName = FilenameUtils.removeExtension(imageName);
            }

            OMETiffExporter.builder()
                    .put(exportedSources.toArray(new SourceAndConverter[0]))
                    .defineMetaData(FilenameUtils.removeExtension(imageName))
                    .putMetadataFromSources(exportedSources.toArray(new SourceAndConverter[0]), UNITS.MILLIMETER)
                    .defineWriteOptions().maxTilesInQueue(200).compression(this.compression)
                    .compressTemporaryFiles(this.compress_temp_files)
                    .nThreads(this.n_threads)
                    .downsample(this.downscaling)
                    .nResolutionLevels(this.n_resolution_levels)
                    .rangeT("").rangeC("").rangeZ("")
                    .monitor(this.taskService).savePath(file_path.getAbsolutePath())
                    .tileSize(this.tile_size_x, this.tile_size_y).create().export();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        IJ.log("Export warpy registered image done.");

    }

    protected SourcesProcessor getSourcesProcessorFixed() {
        return AbstractPairRegistration2DCommand.getChannelProcessorFromCsv(channels_fixed_csv, registration_pair.getFixedSources().length);
    }

    protected SourcesProcessor getSourcesProcessorMoving() {
        return AbstractPairRegistration2DCommand.getChannelProcessorFromCsv(channels_moving_csv, registration_pair.getMovingSourcesOrigin().length);
    }
}
