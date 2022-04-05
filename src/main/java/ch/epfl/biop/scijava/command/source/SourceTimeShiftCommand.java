package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.transform.SourceTimeMapper;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Create a time-shifted source")
public class SourceTimeShiftCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Source")
    SourceAndConverter sac;

    @Parameter(label = "Time shift")
    int timeshift = 0;

    @Parameter(label="Name of the time shifted source")
    String name; // CSV separate for multiple sources

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter sac_out;

    @Override
    public void run() {
        sac_out = new SourceTimeMapper(sac, (t) -> t+timeshift, name).get();
    }

}
