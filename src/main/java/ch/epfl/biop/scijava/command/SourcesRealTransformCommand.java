package ch.epfl.biop.scijava.command;

import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;

import java.util.Arrays;
import java.util.stream.Collectors;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Real Transform Sources")
public class SourcesRealTransformCommand implements Command {
    @Parameter
    SourceAndConverter[] sources_in;

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter[] sources_out;
    @Parameter
    RealTransform rt;

    @Override
    public void run() {
        SourceRealTransformer srt = new SourceRealTransformer(null, rt);
        sources_out =
                Arrays.asList(sources_in).stream()
                .map(srt::apply)
                .collect(Collectors.toList())
                .toArray(new SourceAndConverter[sources_in.length]);

    }
}
