package ch.epfl.biop.bdv.edit;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlaySource;
import bdv.viewer.ViewerPanel;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.InputTrigger;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import java.awt.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SourceEditorBehaviour {

    public SourceEditorOverlay editorOverlay;

    BdvHandle bdvh;

    private final TriggerBehaviourBindings triggerbindings;

    ViewerPanel viewer;

    private static final String AFFINE_SOURCES_TOGGLE_EDITOR = "edit affine-sources";

    private static final String[] AFFINE_SOURCES_TOGGLE_EDITOR_KEYS = new String[] { "button1","J"};

    private static final String AFFINE_SOURCES_MAP = "affine-sources";

    private static final String BLOCKING_MAP = "affine-sources-blocking";

    private final Behaviours behaviours;

    private final BehaviourMap blockMap;

    private static final String[] SOURCES_EDITOR_TOGGLE_KEYS = new String[] { "button1" };

    public SourceEditorBehaviour(BdvHandle bdvh) {
        this.bdvh = bdvh;
        this.triggerbindings = bdvh.getTriggerbindings();
        this.viewer = bdvh.getViewerPanel();
        //

        behaviours = new Behaviours( new InputTriggerConfig(), "bdv" );

        /*
         * Create BehaviourMap to block behaviours interfering with
         * DragBoxCornerBehaviour. The block map is only active while a corner
         * is highlighted.
         */
        blockMap = new BehaviourMap();
    }

    BdvOverlaySource bos;

    public void install()
    {
        editorOverlay = new SourceEditorOverlay(viewer);
        editorOverlay.addBehaviours(behaviours);

        bos = BdvFunctions.showOverlay(editorOverlay, "Editor_Overlay", BdvOptions.options().addTo(bdvh));


        /*bdvh.getViewerPanel().getDisplay().addHandler( boxOverlay.getCornerHighlighter() ); TODO */
        refreshBlockMap();
        block();

        behaviours.install(triggerbindings, AFFINE_SOURCES_MAP);

        /*updateEditability();

        if ( boxSource != null )
            boxSource.addToViewer();*/
    }

    public void uninstall()
    {
        bos.removeFromBdv();
        /*viewer.removeTransformListener( boxOverlay );
        viewer.getDisplay().removeHandler( boxOverlay.getCornerHighlighter() );*/

        triggerbindings.removeInputTriggerMap( AFFINE_SOURCES_MAP );
        triggerbindings.removeBehaviourMap( AFFINE_SOURCES_MAP );

        unblock();

        /*if ( boxSource != null )
            boxSource.removeFromViewer();*/
    }

    // TODO : understand
    private void block()
    {
        triggerbindings.addBehaviourMap( BLOCKING_MAP, blockMap );
    }

    // TODO : understand
    private void unblock()
    {
        triggerbindings.removeBehaviourMap( BLOCKING_MAP );
    }

    // TODO : understand
    private void refreshBlockMap()
    {

        triggerbindings.removeBehaviourMap( BLOCKING_MAP  );

        final Set<InputTrigger> moveCornerTriggers = new HashSet<>();
        for ( final String s : SOURCES_EDITOR_TOGGLE_KEYS )
            moveCornerTriggers.add( InputTrigger.getFromString( s ) );

        final Map< InputTrigger, Set< String > > bindings = triggerbindings.getConcatenatedInputTriggerMap().getAllBindings();
        final Set< String > behavioursToBlock = new HashSet<>();
        for ( final InputTrigger t : moveCornerTriggers )
            behavioursToBlock.addAll( bindings.get( t ) );

        blockMap.clear();
        final Behaviour block = new Behaviour() {};
        for ( final String key : behavioursToBlock )
            blockMap.put( key, block );
    }

}
