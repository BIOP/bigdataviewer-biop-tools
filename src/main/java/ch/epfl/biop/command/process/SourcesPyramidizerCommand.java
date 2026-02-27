package ch.epfl.biop.command.process;

import bdv.cache.SharedQueue;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.SourceHelper;
import org.scijava.ItemIO;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Process>Source - Duplicate With Resolution Levels (Pyramidize)",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.ProcessMenu, weight = BdvPgMenus.ProcessW),
                @Menu(label = "Source - Pyramidize", weight = 2.1)
        },
        description = "Creates a new multi-resolution pyramid source by downsampling")
public class SourcesPyramidizerCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Source(s)",
            description = "The sources to add pyramid levels to")
    SourceAndConverter<?>[] sources;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The sources with newly generated pyramid levels")
    SourceAndConverter<?>[] sources_out;
    @Override
    public void run() {
        SharedQueue queue = new SharedQueue(Runtime.getRuntime().availableProcessors()-1, 5);
        sources_out = new SourceAndConverter[sources.length];
        for (int i = 0; i< sources.length; i++) {
            sources_out[i] = SourceHelper.lazyPyramidizeXY2((SourceAndConverter) sources[i], queue);
        }
    }
}
