package ch.epfl.biop.scijava.command;

import bdv.util.Elliptical3DTransform;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.transform.Elliptic3DTransformer;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.Arrays;
import java.util.stream.Collectors;

@Plugin(type = Command.class, menuPath = "BigDataViewer>Sources>Transform>Elliptic 3D Transform Sources")
public class Elliptic3DTransformerCommand implements Command {

    @Parameter
    Elliptical3DTransform e3Dt;

    @Parameter
    SourceAndConverter[] sacs_in;

    @Override
    public void run() {

        Elliptic3DTransformer et = new Elliptic3DTransformer(null, e3Dt);
        Arrays.asList(sacs_in).stream().map(et::apply).collect(Collectors.toList());

    }
}
