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
import java.util.stream.Collectors;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Process>Source - Duplicate With Time-Shift",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.ProcessMenu, weight = BdvPgMenus.ProcessW),
                @Menu(label = "Source - Duplicate With Time-Shift", weight = 2.2)
        },
        description = "Creates a source with shifted timepoints relative to the original")
public class SourceTimeShiftCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Sources",
            description = "The sources to time-shift")
    SourceAndConverter[] sources;

    @Parameter(label = "Time Shift",
            description = "Number of timepoints to shift (positive = forward, negative = backward)")
    int timeshift = 0;

    @Parameter(label = "Output Name",
            description = "Suffix for the time-shifted source")
    String suffix; // CSV separate for multiple sources

    @Parameter(type = ItemIO.OUTPUT,
            description = "The resulting time-shifted source")
    SourceAndConverter[] sacs_out;

    @Override
    public void run() {
        sacs_out = Arrays.stream(sources)
                         .map(sac -> new SourceTimeMapper(sac, (t) -> t+timeshift, sac.getSpimSource().getName()+suffix).get())
                         .collect(Collectors.toList())
                         .toArray(new SourceAndConverter[0]);
    }

}
