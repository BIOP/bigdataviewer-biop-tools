import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.command.exporter.Elliptic3DTransformExporterCommand;
import ch.epfl.biop.bdv.command.importer.Elliptic3DTransformImporterCommand;
import ch.epfl.biop.bdv.command.register.SourcesRealTransformCommand;
import ch.epfl.biop.bdv.command.transform.Elliptic3DTransformCreatorCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

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
