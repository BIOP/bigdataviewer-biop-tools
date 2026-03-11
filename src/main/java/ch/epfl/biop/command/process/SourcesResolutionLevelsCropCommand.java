package ch.epfl.biop.command.process;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.transform.SourceLevelMapper;
import org.scijava.ItemIO;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

import java.util.Arrays;

/**
 * Command to create sources with a cropped range of resolution levels.
 * <p>
 * This allows users to restrict the resolution pyramid to a subset of levels,
 * for example to exclude the highest resolution levels (for performance) or
 * the lowest resolution levels.
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu + "Sources>Create level-cropped sources",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.ProcessMenu, weight = BdvPgMenus.ProcessW),
                @Menu(label = "Source - Crop Resolution Levels", weight = 2.2)
        },
        description = "Creates a new source with only a subset of resolution levels")
public class SourcesResolutionLevelsCropCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Sources",
            description = "The sources to crop resolution levels from")
    SourceAndConverter<?>[] sources;

    @Parameter(label = "Min Level",
            description = "Minimum resolution level to keep (0 = highest resolution)")
    int minLevel = 0;

    @Parameter(label = "Max Level",
            description = "Maximum resolution level to keep (inclusive)")
    int maxLevel = 0;

    @Parameter(label = "Name Suffix",
            description = "Suffix to append to the source name")
    String suffix = "_lvlcropped";

    @Parameter(type = ItemIO.OUTPUT,
            description = "The resulting sources with cropped resolution levels")
    SourceAndConverter<?>[] sources_out;

    @Override
    public void run() {
        sources_out = Arrays.stream(sources)
                .map(source -> new SourceLevelMapper(
                        source,
                        minLevel,
                        maxLevel,
                        source.getSpimSource().getName() + suffix).get())
                .toArray(SourceAndConverter[]::new);
    }
}
