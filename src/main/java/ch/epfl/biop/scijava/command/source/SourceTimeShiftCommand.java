package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.transform.SourceTimeMapper;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Create a time-shifted source",
        description = "Creates a source with shifted timepoints relative to the original")
public class SourceTimeShiftCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Source",
            description = "The source to time-shift")
    SourceAndConverter sac;

    @Parameter(label = "Time Shift",
            description = "Number of timepoints to shift (positive = forward, negative = backward)")
    int timeshift = 0;

    @Parameter(label = "Output Name",
            description = "Name for the time-shifted source")
    String name; // CSV separate for multiple sources

    @Parameter(type = ItemIO.OUTPUT,
            description = "The resulting time-shifted source")
    SourceAndConverter sac_out;

    @Override
    public void run() {
        sac_out = new SourceTimeMapper(sac, (t) -> t+timeshift, name).get();
    }

}
