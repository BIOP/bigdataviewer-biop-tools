import ch.epfl.biop.scijava.command.transform.Elliptic3DTransformExporterCommand;
import ch.epfl.biop.scijava.command.transform.Elliptic3DTransformImporterCommand;
import ch.epfl.biop.scijava.command.transform.Elliptic3DTransformCreatorCommand;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;

import java.util.concurrent.ExecutionException;

public class DemoEllipticalTransformIO
{

    static {
        LegacyInjector.preinit();
    }

    public static void main(String... args) {
        // Initializes static SourceService and Display Service

        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        try {
            ij.command().run(Elliptic3DTransformCreatorCommand.class, true).get();
            ij.command().run(Elliptic3DTransformExporterCommand.class, true).get();
            ij.command().run(Elliptic3DTransformImporterCommand.class, true).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

    }

}
