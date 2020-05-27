import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.edit.SourceSelectorBehaviour;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import sc.fiji.bdvpg.bdv.projector.Projection;
import sc.fiji.bdvpg.scijava.command.bdv.BdvWindowCreatorCommand;
import sc.fiji.bdvpg.scijava.command.source.SampleSourceCreatorCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;

/**
 * Goal : mimick Blender v>2.8
 * Blender
 * Select Left
 *
 * CardPanel have icons mimicking blender
 */

public class BdvSelectorDemo {

    static public void main(String... args) throws Exception {

        DebugTools.enableIJLogging(false);
        DebugTools.enableLogging("INFO");

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        BdvHandle bdvh = (BdvHandle) (ij.command().run(BdvWindowCreatorCommand.class, true,
                "is2D", false, //true,
                "interpolate", false,
                "projector", Projection.SUM_PROJECTOR,
                "nTimepoints", 1,
                "windowTitle", "2D editor").get().getOutput("bdvh"));

        SourceAndConverter voronoi = (SourceAndConverter) ij.command().run(SampleSourceCreatorCommand.class, true, "sampleName", "Voronoi").get().getOutput("sampleSource");

        SourceAndConverterServices.getSourceAndConverterDisplayService().show(bdvh,voronoi);

        SourceAndConverterServices.getSourceAndConverterDisplayService().getConverterSetup(voronoi).setDisplayRange(0,255);

        AffineTransform3D at3dViewer = new AffineTransform3D();

        double[] m = new double[]{0.2744604688804778, 0.0, 0.0, 249.40886987349538, 0.0, 0.2744604688804778, 0.0, 153.9768896882321, 0.0, 0.0, 0.2744604688804778, 0.0};

        at3dViewer.set(m);

        bdvh.getViewerPanel().setCurrentViewerTransform(at3dViewer);

        AffineTransform3D at3d = new AffineTransform3D();

        at3d.translate(600, 500,0);

        at3d.set(0.25, 0,1);

        at3d.rotate(2,Math.PI/36.0);

        SourceAndConverterServices.getSourceAndConverterDisplayService().show(bdvh,new SourceAffineTransformer(voronoi, at3d).getSourceOut());

        SourceSelectorBehaviour ssb = new SourceSelectorBehaviour(bdvh, "E");

        Behaviours editor = new Behaviours(new InputTriggerConfig());

        ClickBehaviour delete = (x,y) -> bdvh.getViewerPanel().state().removeSources(ssb.getSourceSelectorOverlay().getSelectedSources());

        editor.behaviour(delete, "remove-sources-from-bdv", new String[]{"DELETE"});

        ssb.setEditorOnCallback(() -> {
            editor.install(bdvh.getTriggerbindings(), "sources-editor");
        });

        ssb.setEditorOffCallback(() -> {
            bdvh.getTriggerbindings().removeInputTriggerMap("sources-editor");
            bdvh.getTriggerbindings().removeBehaviourMap("sources-editor");
        });



    }
}
