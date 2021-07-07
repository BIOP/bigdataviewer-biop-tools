package ch.epfl.biop.bdv.command.transform;

import bdv.util.coordinates.CoordinateSource;
import bdv.util.coordinates.VectorFieldSource;
import bdv.viewer.SourceAndConverter;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Create Coordinates Sources")
public class CreateCoordinatesSourceCommand implements BdvPlaygroundActionCommand {

    @Parameter
    SourceAndConverter source;

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter[] sourceCoordinates;

    @Override
    public void run() {
        VectorFieldSource cs = new VectorFieldSource(source.getSpimSource(), source.getSpimSource().getName()+"_Coordinates");

        CoordinateSource sourceX = new CoordinateSource(cs, 0);
        CoordinateSource sourceY = new CoordinateSource(cs, 1);
        CoordinateSource sourceZ = new CoordinateSource(cs, 2);

        sourceCoordinates = new SourceAndConverter[3];

        sourceCoordinates[0] = new SourceAndConverter(sourceX, SourceAndConverterHelper.createConverter(sourceX));
        sourceCoordinates[1] = new SourceAndConverter(sourceY, SourceAndConverterHelper.createConverter(sourceY));
        sourceCoordinates[2] = new SourceAndConverter(sourceZ, SourceAndConverterHelper.createConverter(sourceZ));
    }
}
