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
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Remove Z Offset",
        description = "Removes the Z position offset from sources, centering them at Z=0")
public class RemoveZOffsetCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Timepoint",
            description = "Timepoint used to compute the Z offset")
    int timepoint = 0;

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
        for (SourceAndConverter sac:sacs) {


            //RealPoint center = SourceAndConverterHelper.getSourceAndConverterCenterPoint(sac, timePoint);

            //long sx = sac.getSpimSource().getSource(timePoint, 0).dimension(0);

            //long sy = sac.getSpimSource().getSource(timePoint, 0).dimension(1);

            long sz = sac.getSpimSource().getSource(timepoint, 0).dimension(2);

            AffineTransform3D at3D = new AffineTransform3D();

            sac.getSpimSource().getSourceTransform(timepoint, 0, at3D);

            //AffineTransform3D at3DToOrigin = new AffineTransform3D();
            //at3DToOrigin.translate(-at3D.get(0, 3), -at3D.get(1, 3), -at3D.get(2, 3));

            AffineTransform3D at3DCenter = new AffineTransform3D();
            at3DCenter.concatenate(at3D.inverse());
            at3DCenter.translate(0, 0,-sz/2.0);
            //at3D.set(cx,0,3);
            //at3D.set(cy,1,3);
            at3D.set(0,2,3);
            at3DCenter.preConcatenate(at3D);

            switch (mode) {
                case "Mutate":
                    SourceTransformHelper.mutate(at3DCenter, new SourceAndConverterAndTimeRange(sac, timepoint));
                    break;
                case "Append":
                    SourceTransformHelper.append(at3DCenter, new SourceAndConverterAndTimeRange(sac, timepoint));
                    break;
            }
        }

        SourceAndConverterServices
                .getBdvDisplayService()
                .updateDisplays(sacs);

    }
}
