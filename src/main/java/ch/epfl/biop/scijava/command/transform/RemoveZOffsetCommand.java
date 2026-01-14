package ch.epfl.biop.scijava.command.transform;

import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Remove Z Offset",
        description = "Removes the Z position offset from sources, centering them at Z=0")
public class RemoveZOffsetCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Timepoint",
            description = "Timepoint used to compute the Z offset (ignored if 'Apply to all timepoints' is checked)")
    int timepoint = 0;

    @Parameter(label = "Apply to all timepoints",
            description = "If checked, removes Z offset for all timepoints independently")
    boolean apply_to_all_timepoints = false;

    @Parameter(label = "Select Source(s)",
            type = ItemIO.BOTH,
            description = "The sources to remove Z offset from")
    public SourceAndConverter[] sacs;

    @Parameter(label = "Mode",
            choices = {"Mutate", "Append"},
            description = "Mutate modifies existing transform; Append adds a new transform layer")
    String mode = "Mutate";

    @Override
    public void run() {
        for (SourceAndConverter sac : sacs) {
            if (apply_to_all_timepoints) {
                // Get the number of timepoints from the source
                int maxTimepoint = SourceAndConverterHelper.getMaxTimepoint(sac);
                int numTimepoints = maxTimepoint + 1;

                // Apply transformation to each timepoint
                for (int tp = 0; tp < numTimepoints; tp++) {
                    removeZOffsetForTimepoint(sac, tp);
                }
            } else {
                // Apply only to the specified timepoint
                removeZOffsetForTimepoint(sac, timepoint);
            }
        }

        SourceAndConverterServices
                .getBdvDisplayService()
                .updateDisplays(sacs);
    }

    private void removeZOffsetForTimepoint(SourceAndConverter sac, int tp) {
        long sz = sac.getSpimSource().getSource(tp, 0).dimension(2);

        AffineTransform3D at3D = new AffineTransform3D();
        sac.getSpimSource().getSourceTransform(tp, 0, at3D);

        AffineTransform3D at3DCenter = new AffineTransform3D();
        at3DCenter.concatenate(at3D.inverse());
        at3DCenter.translate(0, 0, -sz / 2.0);
        at3D.set(0, 2, 3);
        at3DCenter.preConcatenate(at3D);

        switch (mode) {
            case "Mutate":
                SourceTransformHelper.mutate(at3DCenter, new SourceAndConverterAndTimeRange(sac, tp));
                break;
            case "Append":
                SourceTransformHelper.append(at3DCenter, new SourceAndConverterAndTimeRange(sac, tp));
                break;
        }
    }
}
