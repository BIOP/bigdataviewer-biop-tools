import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.command.workflow.elliptic.Elliptic3DTransformCreatorCommand;
import ch.epfl.biop.command.register.SourcesRealTransformCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.realtransform.RealTransform;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.display.BrightnessAdjuster;
import sc.fiji.bdvpg.dataset.importer.XMLToDatasetImporter;

import java.util.concurrent.ExecutionException;

public class DemoEllipticalSource {

    static {
        LegacyInjector.preinit();
    }

    public static void main(String... args) {
        // Initializes static SourceService and Display Service

        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        final String filePath = "src/test/resources/mri-stack.xml";
        // Import SpimData
        XMLToDatasetImporter importer = new XMLToDatasetImporter(filePath);
        final AbstractSpimData spimData = importer.get();

        SourceAndConverter source = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData)
                .get(0);

        new BrightnessAdjuster(source,0,250).run();

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
                    "center_z",120).get().getOutput("e3dt");
            SourceAndConverter transformed_source = ((SourceAndConverter[]) ij.command().run(SourcesRealTransformCommand.class, true,
                    "sources_in", new SourceAndConverter[]{source},
                    "rt", rt).get().getOutput("sources_out"))[0];
            BdvHandle bdvh = SourceServices
                    .getBdvDisplayService()
                    .getNewBdv();
            SourceServices
                    .getBdvDisplayService()
                    .show(bdvh, transformed_source);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

    }

}
