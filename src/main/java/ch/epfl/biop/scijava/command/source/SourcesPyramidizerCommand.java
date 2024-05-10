package ch.epfl.biop.scijava.command.source;

import bdv.cache.SharedQueue;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceHelper;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Adds pyramids to sources")
public class SourcesPyramidizerCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Source(s)")
    SourceAndConverter<?>[] sacs;

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter<?>[] sacs_out;
    @Override
    public void run() {
        SharedQueue queue = new SharedQueue(Runtime.getRuntime().availableProcessors()-1, 5);
        sacs_out = new SourceAndConverter[sacs.length];
        for (int i = 0;i< sacs.length; i++) {
            sacs_out[i] = SourceHelper.lazyPyramidizeXY2((SourceAndConverter) sacs[i], queue);
        }
    }
}
