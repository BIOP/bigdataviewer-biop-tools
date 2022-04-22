package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.exporter.OMETiffExporter;
import ch.epfl.biop.sourceandconverter.exporter.OMETiffPyramidizerExporter;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Export Sources To OME TIFF (build pyramid)")
public class ExportToOMETIFFBuildPyramidCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Sources to export")
    public SourceAndConverter[] sacs;

    @Parameter(label = "File path")
    public File file;

    @Parameter( label = "Unit", choices = {"MILLIMETER", "MICROMETER"})
    String unit;

    @Parameter( label = "Number of resolution levels")
    int n_resolution_levels = 4;

    @Parameter( label = "Downscaling between resolution levels")
    int downscaling = 2;

    @Parameter( label = "Tile Size X (negative for no tiling)")
    int tile_size_x = 512;

    @Parameter( label = "Tile Size Y (negative for no tiling)")
    int tile_size_y = 512;

    @Parameter( label = "Number of threads (0 = serial)")
    int n_threads = 8;

    @Parameter( label = "Number of tiles computed in advance")
    int max_tiles_queue = 256;

    @Parameter( label = "Compress (LZW)")
    Boolean lzw_compression = false;

    @Parameter
    TaskService taskService;

    @Override
    public void run() {

        List<SourceAndConverter> sources = sorter.apply(Arrays.asList(sacs));

        sacs = sources.toArray(new SourceAndConverter[0]);

        Task task = taskService.createTask("Export: "+file.getName());

        OMETiffPyramidizerExporter.Builder builder = OMETiffPyramidizerExporter
                .builder()
                .monitor(task)
                .downsample(downscaling)
                .nResolutionLevels(n_resolution_levels)
                .savePath(file.getAbsolutePath());

        if (lzw_compression) builder.lzw();
        if (unit.equals("MILLIMETER")) builder.millimeter();
        if (unit.equals("MICROMETER")) builder.micrometer();
        if ((tile_size_x>0)&&(tile_size_y>0)) builder.tileSize(tile_size_x, tile_size_y);
        builder.maxTilesInQueue(max_tiles_queue);
        builder.nThreads(n_threads);

        try {
            builder.create(sacs).export();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Function<Collection<SourceAndConverter>,List<SourceAndConverter>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultNoGeneric(sacslist);

}
