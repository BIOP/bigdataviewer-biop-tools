package ch.epfl.biop.scijava.command.source.register;

import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.RealTransform;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;

import java.util.Arrays;
import java.util.stream.Collectors;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Obsolete>Real Transform Sources",
        description = "Applies a non-linear real transform (e.g., spline) to sources")
public class SourcesRealTransformCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Source(s)",
            description = "The sources to transform")
    SourceAndConverter[] sources_in;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The transformed sources")
    SourceAndConverter[] sources_out;

    @Parameter(label = "Transform",
            description = "The real transform to apply (e.g., thin plate spline)")
    RealTransform rt;

    @Override
    public void run() {
        SourceRealTransformer srt = new SourceRealTransformer(null, rt);
        sources_out =
                Arrays.stream(sources_in)
                .map(srt::apply)
                .collect(Collectors.toList())
                .toArray(new SourceAndConverter[sources_in.length]);

    }
}
