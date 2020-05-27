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

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

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

    List<ToggleListener> toggleListeners = new ArrayList<>();

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

    public synchronized void enable() {
        if (!isInstalled) {
            install();
        }
    }

    public synchronized void disable() {
        if (isInstalled) {
            uninstall();
        }
    }

    public void remove() {
        triggerbindings.removeInputTriggerMap("source-selector-toggle");
        triggerbindings.removeBehaviourMap("source-selector-toggle");
    }

    synchronized void install() {
        isInstalled = true;
        editorOverlay.addSelectionBehaviours(behaviours);
        triggerbindings.addBehaviourMap(SOURCES_SELECTOR_MAP, behaviours.getBehaviourMap());
        triggerbindings.addInputTriggerMap(SOURCES_SELECTOR_MAP, behaviours.getInputTriggerMap(), "transform", "bdv");
        bos = BdvFunctions.showOverlay(editorOverlay, "Editor_Overlay", BdvOptions.options().addTo(bdvh));
        bdvh.getKeybindings().addInputMap("blocking-source-selector", new InputMap(), "bdv", "navigation");
        toggleListeners.forEach(tl -> tl.enable());
    }

    synchronized void uninstall() {
        isInstalled = false;
        bos.removeFromBdv();
        triggerbindings.removeBehaviourMap( SOURCES_SELECTOR_MAP );
        triggerbindings.removeInputTriggerMap( SOURCES_SELECTOR_MAP );
        bdvh.getKeybindings().removeInputMap("blocking-source-selector");
        toggleListeners.forEach(tl -> tl.disable());
    }

    // Listeners

    public void addToggleListener(ToggleListener toggleListener) {
        toggleListeners.add(toggleListener);
    }

    public void removeToggleListener(ToggleListener toggleListener) {
        toggleListeners.remove(toggleListener);
    }

    public void addSelectedSourcesListener(SelectedSourcesListener selectedSourcesListener) {
        editorOverlay.addSelectedSourcesListener(selectedSourcesListener);
    }

    public void removeSelectedSourcesListener(SelectedSourcesListener selectedSourcesListener) {
        editorOverlay.removeSelectedSourcesListener(selectedSourcesListener);
    }

}
