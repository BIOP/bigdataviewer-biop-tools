package ch.epfl.biop.scijava.command;

import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterUtils;


@Plugin(type = Command.class, menuPath = "BigDataViewer>Sources>Transform>Center Sources")
public class SourcesRecenterCommand implements Command {

    @Parameter
    int timePoint = 0;

    @Parameter(label = "Sources", type = ItemIO.BOTH)
    public SourceAndConverter[] sacs;

    @Parameter(choices = {"Mutate", "Append"})
    String mode = "Mutate";

    @Override
    public void run() {
        for (SourceAndConverter sac:sacs) {

            long sx = sac.getSpimSource().getSource(timePoint, 0).dimension(0);

            long sy = sac.getSpimSource().getSource(timePoint, 0).dimension(1);

            AffineTransform3D at3D = new AffineTransform3D();

            sac.getSpimSource().getSourceTransform(timePoint, 0, at3D);

            //AffineTransform3D at3DToOrigin = new AffineTransform3D();
            //at3DToOrigin.translate(-at3D.get(0, 3), -at3D.get(1, 3), -at3D.get(2, 3));

            AffineTransform3D at3DCenter = new AffineTransform3D();
            at3DCenter.concatenate(at3D.inverse());
            at3DCenter.translate(-sx/2, -sy/2,0);
            at3D.set(0,0,3);
            at3D.set(0,1,3);
            at3D.set(0,2,3);
            at3DCenter.preConcatenate(at3D);

            switch (mode) {
                case "Mutate":
                    SourceAndConverterUtils.mutate(at3DCenter, sac);
                    break;
                case "Append":
                    SourceAndConverterUtils.append(at3DCenter, sac);
                    break;
            }
        }

        SourceAndConverterServices
                .getSourceAndConverterDisplayService()
                .updateDisplays(sacs);

    }
}