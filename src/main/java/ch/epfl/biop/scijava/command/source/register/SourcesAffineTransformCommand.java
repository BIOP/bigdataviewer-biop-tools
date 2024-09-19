package ch.epfl.biop.scijava.command.source.register;

import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

import java.util.Arrays;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Obsolete>Affine Transform Sources")
public class SourcesAffineTransformCommand implements BdvPlaygroundActionCommand {
    @Parameter
    SourceAndConverter[] sources_in;

    @Parameter
    AffineTransform3D at3d;

    @Parameter(choices = {"Mutate", "Append"})
    String mode = "Mutate";

    @Parameter
    int timepoint_begin;

    @Parameter
    int timepoint_end;

    @Override
    public void run() {

        Arrays.stream(sources_in).forEach(sac -> {
            switch (mode) {
                case "Mutate":
                    SourceTransformHelper.mutate(at3d, new SourceAndConverterAndTimeRange(sac, timepoint_begin, timepoint_end));
                    break;
                case "Append":
                    SourceTransformHelper.append(at3d, new SourceAndConverterAndTimeRange(sac, timepoint_begin, timepoint_end));
                    break;
            }
        });

        SourceAndConverterServices
                .getBdvDisplayService()
                .updateDisplays(sources_in);
    }
}
