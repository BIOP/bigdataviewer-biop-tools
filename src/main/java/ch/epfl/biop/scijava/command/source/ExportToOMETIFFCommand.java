package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.exporter.OMETiffExporter;
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

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Export Sources To OME TIFF")
public class ExportToOMETIFFCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Sources to export")
    public SourceAndConverter[] sacs;

    @Parameter(label = "File path")
    public File file;

    @Parameter( label = "Unit", choices = {"MILLIMETER", "MICROMETER"})
    String unit;

    @Parameter( label = "Tile Size X (negative for no tiling)")
    int tileSizeX = 512;

    @Parameter( label = "Tile Size Y (negative for no tiling)")
    int tileSizeY = 512;

    @Parameter( label = "Number of threads (0 = serial)")
    int nThreads = 8;

    @Parameter( label = "Number of tiles computed in advance")
    int maxTilesInQueue = 256;

    @Parameter( label = "Compress (LZW)")
    Boolean lzwCompression = false;

    OMETiffExporter exporter;

    @Parameter
    TaskService taskService;

    @Parameter(type = ItemIO.OUTPUT)
    Task task;

    @Override
    public void run() {

        List<SourceAndConverter> sources = sorter.apply(Arrays.asList(sacs));

        sacs = sources.toArray(new SourceAndConverter[0]);

        task = taskService.createTask("Export: "+file.getName());

        OMETiffExporter.Builder builder = OMETiffExporter
                .builder()
                .monitor(task)
                .savePath(file.getAbsolutePath());

        if (lzwCompression) builder.lzw();
        if (unit.equals("MILLIMETER")) builder.millimeter();
        if (unit.equals("MICROMETER")) builder.micrometer();
        if ((tileSizeX>0)&&(tileSizeY>0)) builder.tileSize(tileSizeX, tileSizeY);
        builder.maxTilesInQueue(maxTilesInQueue);
        builder.nThreads(nThreads);

        exporter = builder.create(sacs);

        new Thread(() -> {
            try {
                exporter.export();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public Function<Collection<SourceAndConverter>,List<SourceAndConverter>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultNoGeneric(sacslist);

}
