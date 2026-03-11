package ch.epfl.biop.command.process.transform;

import bdv.viewer.SourceAndConverter;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.SourceAndTimeRange;
import sc.fiji.bdvpg.source.SourceHelper;
import sc.fiji.bdvpg.source.transform.SourceTransformHelper;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = BdvPgMenus.RootMenu+"Process>Transform>Source - Remove Z Offset",
        description = "Removes the Z position offset from sources, centering them at Z=0")
public class SourcesZOffsetRemoveCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Timepoint",
            description = "Timepoint used to compute the Z offset (ignored if 'Apply to all timepoints' is checked)")
    int timepoint = 0;

    @Parameter(label = "Apply to all timepoints",
            description = "If checked, removes Z offset for all timepoints independently")
    boolean apply_to_all_timepoints = false;

    @Parameter(label = "Select Source(s)",
            type = ItemIO.BOTH,
            description = "The sources to remove Z offset from")
    public SourceAndConverter[] sources;

    @Parameter(label = "Mode",
            choices = {"Mutate", "Append"},
            description = "Mutate modifies existing transform; Append adds a new transform layer")
    String mode = "Mutate";

    @Override
    public void run() {
        for (SourceAndConverter source : sources) {
            if (apply_to_all_timepoints) {
                // Get the number of timepoints from the source
                int maxTimepoint = SourceHelper.getMaxTimepoint(source);
                int numTimepoints = maxTimepoint + 1;

                // Apply transformation to each timepoint
                for (int tp = 0; tp < numTimepoints; tp++) {
                    removeZOffsetForTimepoint(source, tp);
                }
            } else {
                // Apply only to the specified timepoint
                removeZOffsetForTimepoint(source, timepoint);
            }
        }

        SourceServices
                .getBdvDisplayService()
                .updateDisplays(sources);
    }

    private void removeZOffsetForTimepoint(SourceAndConverter<?> source, int tp) {

        switch (mode) {
            case "Mutate":
                SourceTransformHelper.mutate(getZ0Transform(source, tp), new SourceAndTimeRange(source, tp));
                break;
            case "Append":
                SourceTransformHelper.append(getZ0Transform(source, tp), new SourceAndTimeRange(source, tp));
                break;
        }
    }

    public static AffineTransform3D getZ0Transform(SourceAndConverter<?> source, int tp) {
        RealPoint center = SourceHelper.getSourceCenterPoint(source, tp);
        AffineTransform3D z0 = new AffineTransform3D();
        z0.set(-center.getDoublePosition(2),2,3);
        return z0;
    }

}
