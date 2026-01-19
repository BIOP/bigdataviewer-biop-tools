package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.transform.SourceTimeMapper;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.Arrays;
import java.util.stream.Collectors;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Create a time-shifted source",
        description = "Creates a source with shifted timepoints relative to the original")
public class SourceTimeShiftCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Sources",
            description = "The sources to time-shift")
    SourceAndConverter[] sacs;

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
        sacs_out = Arrays.stream(sacs)
                         .map(sac -> new SourceTimeMapper(sac, (t) -> t+timeshift, sac.getSpimSource().getName()+suffix).get())
                         .collect(Collectors.toList())
                         .toArray(new SourceAndConverter[0]);
    }

}
