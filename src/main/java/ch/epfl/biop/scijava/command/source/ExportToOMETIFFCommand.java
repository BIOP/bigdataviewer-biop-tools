package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.exporter.OMETiffExporter;
import ij.IJ;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.TaskService;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Parameter( label = "Compress (LZW)")
    Boolean lzwCompression = false;

    @Parameter( label = "Monitor")
    Boolean monitor = true;

    OMETiffExporter exporter;

    AtomicBoolean error = new AtomicBoolean(false);

    AtomicBoolean done = new AtomicBoolean(false);

    @Parameter
    TaskService taskService;

    @Override
    public void run() {

        List<SourceAndConverter> sources = sorter.apply(Arrays.asList(sacs));

        sacs = sources.toArray(new SourceAndConverter[0]);

        OMETiffExporter.Builder builder = OMETiffExporter
                .builder()
                .monitor(taskService.createTask("OmeTiff export: "+file.getName()))
                .savePath(file.getAbsolutePath());

        if (lzwCompression) builder.lzw();
        if (unit.equals("MILLIMETER")) builder.millimeter();
        if (unit.equals("MICROMETER")) builder.micrometer();
        if ((tileSizeX>0)&&(tileSizeY>0)) builder.tileSize(tileSizeX, tileSizeY);
        builder.nThreads(nThreads);

        exporter = builder.create(sacs);

        new Thread(() -> {
            try {
                exporter.export();
                done.set(true);
            } catch (Exception e) {
                error.set(false);
                e.printStackTrace();
            }
        }).start();

        if (monitor) {
            while (exporter.getWrittenTiles() < exporter.getTotalTiles()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                IJ.log("Export to OME TIFF: "+exporter.getWrittenTiles()+"/"+exporter.getTotalTiles()+" tiles written");
            }
        }
    }

    public Function<Collection<SourceAndConverter>,List<SourceAndConverter>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultNoGeneric(sacslist);

}
