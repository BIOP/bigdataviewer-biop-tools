package ch.epfl.biop.scijava.command.transform;

import bdv.util.Elliptical3DTransform;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.transform.Elliptic3DTransformer;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.Arrays;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Elliptic 3D Transform Sources",
        description = "Applies an elliptical 3D transform to sources for spherical/ellipsoidal projection")
public class Elliptic3DTransformerCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Elliptical Transform",
            description = "The elliptical 3D transform to apply")
    Elliptical3DTransform e3dt;

    @Parameter(label = "Select Source(s)",
            description = "The sources to transform")
    SourceAndConverter[] sacs_in;

    @Override
    public void run() {

        Elliptic3DTransformer et = new Elliptic3DTransformer(null, e3dt);
        Arrays.stream(sacs_in).map(et::apply);//.collect(Collectors.toList());

    }
}
