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

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Recenter sources")
public class SourcesRecenterCommand implements BdvPlaygroundActionCommand {

    @Parameter
    int timepoint = 0;

    @Parameter(style = "format:0.#####E0")
    double cx, cy, cz;

    @Parameter(label = "Sources", type = ItemIO.BOTH)
    public SourceAndConverter[] sacs;

    @Parameter(choices = {"Mutate", "Append"})
    String mode = "Mutate";

    @Override
    public void run() {
        for (SourceAndConverter sac:sacs) {

            long sx = sac.getSpimSource().getSource(timepoint, 0).dimension(0);

            long sy = sac.getSpimSource().getSource(timepoint, 0).dimension(1);

            AffineTransform3D at3D = new AffineTransform3D();

            sac.getSpimSource().getSourceTransform(timepoint, 0, at3D);

            //AffineTransform3D at3DToOrigin = new AffineTransform3D();
            //at3DToOrigin.translate(-at3D.get(0, 3), -at3D.get(1, 3), -at3D.get(2, 3));

            AffineTransform3D at3DCenter = new AffineTransform3D();
            at3DCenter.concatenate(at3D.inverse());
            at3DCenter.translate(-sx/2, -sy/2,0);
            at3D.set(cx,0,3);
            at3D.set(cy,1,3);
            at3D.set(cz,2,3);
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
