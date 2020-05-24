import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.edit.SourceEditorOverlay;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import sc.fiji.bdvpg.bdv.projector.Projection;
import sc.fiji.bdvpg.scijava.command.bdv.BdvWindowCreatorCommand;
import sc.fiji.bdvpg.scijava.command.source.SampleSourceCreatorCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

public class BdvEditor2dDemo {

    static public void main(String... args) throws Exception {

        DebugTools.enableIJLogging(false);
        DebugTools.enableLogging("INFO");

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        BdvHandle bdvh = (BdvHandle) (ij.command().run(BdvWindowCreatorCommand.class, true,
                "is2D", false,//true,
                "interpolate", false,
                "projector", Projection.SUM_PROJECTOR,
                "nTimepoints", 1,
                "windowTitle", "2D editor").get().getOutput("bdvh"));

        SourceEditorOverlay editorOverlay = new SourceEditorOverlay(bdvh);

        SourceAndConverter voronoi = (SourceAndConverter) ij.command().run(SampleSourceCreatorCommand.class, true, "sampleName", "Voronoi").get().getOutput("sampleSource");

        SourceAndConverterServices.getSourceAndConverterDisplayService().show(bdvh,voronoi);
        SourceAndConverterServices.getSourceAndConverterDisplayService().getConverterSetup(voronoi).setDisplayRange(0,255);


        BdvFunctions.showOverlay(editorOverlay, "Editor_Overlay", BdvOptions.options().addTo(bdvh));

    }
}
