package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.transform.SourceTimeMapper;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.Arrays;
import java.util.stream.Collectors;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Create copy of sources over time",
        description = "Span sources over time")
public class SourceCopyOverTimeCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Sources",
            description = "The sources to copy over time")
    SourceAndConverter[] sacs;

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
    SourceAndConverter[] sacs_out;

    @Override
    public void run() {
        sacs_out = Arrays.stream(sacs)
                         .map(sac -> new SourceTimeMapper(sac, (t) -> (t>=t_start)&&(t<t_end)?timepoint_to_copy:-1, sac.getSpimSource().getName()+suffix).get())
                         .collect(Collectors.toList())
                         .toArray(new SourceAndConverter[0]);
    }

}
