package ch.epfl.biop.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.importer.WeightedVoronoiSourceGetter;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Create Elliptic Voronoi Source")
public class GetVoronoiEllipseSampleCommand implements Command {

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter sampleSource;

    @Override
    public void run() {
        sampleSource = (new WeightedVoronoiSourceGetter(new long[]{2048, 2048, 2048}, 65536, false).get());
    }
}
