package ch.epfl.biop.scijava.command;

import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterUtils;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;

import java.util.Arrays;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Affine Transform Sources")
public class SourcesAffineTransformCommand implements Command {
    @Parameter
    SourceAndConverter[] sources_in;

    @Parameter
    AffineTransform3D at3D;

    @Parameter(choices = {"Mutate", "Append"})
    String mode = "Mutate";

    @Override
    public void run() {
        SourceAffineTransformer sat = new SourceAffineTransformer(null, at3D);
        Arrays.asList(sources_in).stream().forEach(sac -> {
            switch (mode) {
                case "Mutate":
                    SourceAndConverterUtils.mutate(at3D, sac);
                    break;
                case "Append":
                    SourceAndConverterUtils.append(at3D, sac);
                    break;
            }
        });

        SourceAndConverterServices
                .getSourceAndConverterDisplayService()
                .updateDisplays(sources_in);
    }
}
