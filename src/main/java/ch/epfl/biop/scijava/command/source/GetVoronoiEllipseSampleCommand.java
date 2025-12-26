package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.importer.WeightedVoronoiSourceGetter;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAdjuster;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Create Elliptic Voronoi Source",
        description = "Creates a sample 3D source with weighted Voronoi ellipse patterns for testing")
public class GetVoronoiEllipseSampleCommand implements BdvPlaygroundActionCommand {

    @Parameter(type = ItemIO.OUTPUT,
            description = "The generated sample source with Voronoi ellipse patterns")
    SourceAndConverter sample_source;

    @Override
    public void run() {
        sample_source = (new WeightedVoronoiSourceGetter(new long[]{2048, 2048, 2048}, 65536, false).get());
        new BrightnessAdjuster(sample_source,0,255).run();
    }
}
