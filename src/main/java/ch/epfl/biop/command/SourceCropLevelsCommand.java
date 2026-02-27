package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.transform.SourceLevelMapper;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Command to create sources with a cropped range of resolution levels.
 * <p>
 * This allows users to restrict the resolution pyramid to a subset of levels,
 * for example to exclude the highest resolution levels (for performance) or
 * the lowest resolution levels.
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu + "Sources>Create level-cropped sources",
        description = "Creates sources with only a subset of resolution levels from the original")
public class SourceCropLevelsCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Sources",
            description = "The sources to crop resolution levels from")
    SourceAndConverter[] sacs;

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
    SourceAndConverter[] sacs_out;

    @Override
    public void run() {
        sacs_out = Arrays.stream(sacs)
                .map(sac -> new SourceLevelMapper(
                        sac,
                        minLevel,
                        maxLevel,
                        sac.getSpimSource().getName() + suffix).get())
                .collect(Collectors.toList())
                .toArray(new SourceAndConverter[0]);
    }
}
