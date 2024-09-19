import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.scijava.command.transform.Elliptic3DTransformCreatorCommand;
import ch.epfl.biop.scijava.command.source.register.SourcesRealTransformCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.realtransform.RealTransform;
import sc.fiji.bdvpg.scijava.command.source.BrightnessAdjusterCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAdjuster;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import java.util.concurrent.ExecutionException;

public class DemoEllipticalSource {

    static {
        LegacyInjector.preinit();
    }

    public static void main(String... args) {
        // Initializes static SourceService and Display Service

        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceAndConverterServices
                .getBdvDisplayService().getActiveBdv();

        final String filePath = "src/test/resources/mri-stack.xml";
        // Import SpimData
        SpimDataFromXmlImporter importer = new SpimDataFromXmlImporter(filePath);
        //importer.run();

        final AbstractSpimData spimData = importer.get();

        SourceAndConverter sac = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(spimData)
                .get(0);

        new BrightnessAdjuster(sac,0,250).run();

        try {
            RealTransform rt = (RealTransform) ij.command().run(Elliptic3DTransformCreatorCommand.class, true,
                    "radius_x",100,
                    "radius_y",100,
                    "radius_z",100, // radii of axes 1 2 3 of ellipse
                    "rotation_x",0,
                    "rotation_y",0,
                    "rotation_z",0, // 3D rotation euler angles  - maybe not the best parametrization
                    "center_x",120,
                    "center_y",120,
                    "center_z",120).get().getOutput("e3Dt");
            SourceAndConverter transformed_source = ((SourceAndConverter[]) ij.command().run(SourcesRealTransformCommand.class, true,
                    "sources_in", new SourceAndConverter[]{sac},
                    "rt", rt).get().getOutput("sources_out"))[0];
            BdvHandle bdvh = SourceAndConverterServices
                    .getBdvDisplayService()
                    .getNewBdv();
            SourceAndConverterServices
                    .getBdvDisplayService()
                    .show(bdvh, transformed_source);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

    }

}
