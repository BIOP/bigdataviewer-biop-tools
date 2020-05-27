package ch.epfl.biop.bdv.edit;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlaySource;
import bdv.viewer.ViewerPanel;
import org.scijava.ui.behaviour.*;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import java.util.concurrent.Callable;

public class SourceSelectorBehaviour {

    SourceSelectorOverlay editorOverlay;

    final BdvHandle bdvh;

    private final TriggerBehaviourBindings triggerbindings;

    ViewerPanel viewer;

    private static final String SOURCES_SELECTOR_MAP = "sources-selector";

    private static final String SOURCES_SELECTOR_TOGGLE_MAP = "source-selector-toggle";

    private final Behaviours behaviours;

    BdvOverlaySource bos;

    boolean isInstalled; // flag for the toggle action

    Runnable notifyInstalled;

    Runnable notifyUnInstalled;

    public SourceSelectorBehaviour(BdvHandle bdvh, String triggerToggleSelector) {
        this.bdvh = bdvh;
        this.triggerbindings = bdvh.getTriggerbindings();
        this.viewer = bdvh.getViewerPanel();

        editorOverlay = new SourceSelectorOverlay(viewer);

        ClickBehaviour toggleEditor = (x,y) -> {
            if (isInstalled) {
                uninstall();
            } else {
                install();
            }
        };

        Behaviours behavioursToggle = new Behaviours(new InputTriggerConfig(), "bdv");
        behavioursToggle.behaviour(toggleEditor, SOURCES_SELECTOR_TOGGLE_MAP, triggerToggleSelector);
        behavioursToggle.install(bdvh.getTriggerbindings(), "source-selector-toggle");
        behaviours = new Behaviours( new InputTriggerConfig(), "bdv" );

    }

    public SourceSelectorOverlay getSourceSelectorOverlay() {
        return editorOverlay;
    }

    public BdvHandle getBdvHandle() {
        return bdvh;
    }

    public synchronized void setEditorOnCallback(Runnable runnable) {
        this.notifyInstalled = runnable;
    }

    public synchronized void setEditorOffCallback(Runnable runnable) {
        this.notifyUnInstalled = runnable;
    }

    synchronized void install() {
        isInstalled = true;
        editorOverlay.addSelectionBehaviours(behaviours);
        triggerbindings.addBehaviourMap(SOURCES_SELECTOR_MAP, behaviours.getBehaviourMap());
        triggerbindings.addInputTriggerMap(SOURCES_SELECTOR_MAP, behaviours.getInputTriggerMap(), "transform");
        bos = BdvFunctions.showOverlay(editorOverlay, "Editor_Overlay", BdvOptions.options().addTo(bdvh));
        notifyInstalled.run();
    }

    synchronized void uninstall() {
        isInstalled = false;
        bos.removeFromBdv();
        triggerbindings.removeBehaviourMap( SOURCES_SELECTOR_MAP );
        triggerbindings.removeInputTriggerMap( SOURCES_SELECTOR_MAP );
        notifyUnInstalled.run();
    }

}
