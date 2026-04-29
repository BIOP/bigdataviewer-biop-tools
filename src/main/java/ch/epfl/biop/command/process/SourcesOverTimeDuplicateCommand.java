package ch.epfl.biop.command.process;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.transform.SourceTimeMapper;
import org.scijava.ItemIO;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

import java.util.Arrays;

import static bdv.util.source.time.MappedTimeSource.withName;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Sources>Create copy of sources over time",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.ProcessMenu, weight = BdvPgMenus.ProcessW),
                @Menu(label = "Source - Freeze Timepoint", weight = 2.2)
        },
        description = "Creates a new source showing a fixed timepoint across a time range")
public class SourcesOverTimeDuplicateCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Sources",
            description = "The sources to copy over time")
    SourceAndConverter<?>[] sources;

    @Parameter(label = "Timepoint to copy",
            description = "Timepoint to copy")
    int timepoint_to_copy = 0;

    @Parameter(label = "Timepoint start",
            description = "Timepoint to start")
    int t_start = 0;

    @Parameter(label = "Timepoint end (excluded)",
            description = "Timepoint to end")
    int t_end = 0;

    @Parameter(label = "Output Name",
            description = "Suffix for the time-shifted source")
    String suffix; // CSV separate for multiple sources

    @Parameter(type = ItemIO.OUTPUT,
            description = "The resulting time-shifted source")
    SourceAndConverter[] sources_out;

    @Override
    public void run() {

        sources_out = Arrays.stream(sources)
                .map(source -> new SourceTimeMapper(source, withName((t) -> (t >= t_start) && (t < t_end) ? timepoint_to_copy : -1, "t -> "+timepoint_to_copy+" ["+t_start+" to "+t_end+"]"), source.getSpimSource().getName() + suffix).get())
                .toArray(SourceAndConverter[]::new);
    }

}
