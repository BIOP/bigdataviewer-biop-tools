package ch.epfl.biop.command.register;

import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.SourceAndTimeRange;
import sc.fiji.bdvpg.source.transform.SourceTransformHelper;

import java.util.Arrays;

@Plugin(type = BdvPlaygroundActionCommand.class,
   //     menuPath = BdvPgMenus.RootMenu+"Source>Transform>Obsolete>Affine Transform Sources",
        description = "Applies an affine transformation to sources over a range of timepoints")
public class SourcesAffineTransformCommand implements BdvPlaygroundActionCommand {
    @Parameter(label = "Select Source(s)",
            description = "The sources to transform")
    SourceAndConverter[] sources_in;

    @Parameter(label = "Transform",
            description = "The affine transformation to apply")
    AffineTransform3D at3d;

    @Parameter(label = "Mode",
            choices = {"Mutate", "Append"},
            description = "Mutate modifies existing transform; Append adds a new transform layer")
    String mode = "Mutate";

    @Parameter(label = "Start Timepoint",
            description = "First timepoint to apply the transform to")
    int timepoint_begin;

    @Parameter(label = "End Timepoint",
            description = "Last timepoint to apply the transform to")
    int timepoint_end;

    @Override
    public void run() {

        Arrays.stream(sources_in).forEach(source -> {
            switch (mode) {
                case "Mutate":
                    SourceTransformHelper.mutate(at3d, new SourceAndTimeRange(source, timepoint_begin, timepoint_end));
                    break;
                case "Append":
                    SourceTransformHelper.append(at3d, new SourceAndTimeRange(source, timepoint_begin, timepoint_end));
                    break;
            }
        });

        SourceServices
                .getBdvDisplayService()
                .updateDisplays(sources_in);
    }
}
