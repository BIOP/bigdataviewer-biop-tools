package ch.epfl.biop.bdv.userdefinedregion;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlaySource;
import bdv.viewer.ViewerPanel;
import ch.epfl.biop.bdv.select.SelectedSourcesListener;
import net.imglib2.RealPoint;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import javax.swing.*;
import java.util.*;

/**
 * Appends and controls a {@link RectangleSelectorBehaviour} in a {@link BdvHandle}
 *
 * It is used in conjuncion with a BdvOverlay layer {@link RectangleSelectorOverlay} which can be retrieved with
 * {@link RectangleSelectorBehaviour#getRectangleSelectorOverlay()}
 *
 * The selections can be triggered by GUI actions in the linked {@link RectangleSelectorOverlay} or
 * directly programmatically
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL, 2020
 */

public class RectangleSelectorBehaviour {

    final public static String RECTANGLE_SELECTOR_MAP = "sources-selector";

    final public static String SET = "SET";

    final RectangleSelectorOverlay rectangleOverlay;

    BdvOverlaySource bos;

    final BdvHandle bdvh;

    final TriggerBehaviourBindings triggerbindings;

    final ViewerPanel viewer;

    final Behaviours behaviours;

    boolean isInstalled; // flag for the toggle action

    List<SelectedSourcesListener> selectedSourceListeners = new ArrayList<>();
    /**
     * Construct a SourceSelectorBehaviour
     * @param bdvh BdvHandle associated to this behaviour
     */
    public RectangleSelectorBehaviour(BdvHandle bdvh) {
        this.bdvh = bdvh;
        this.triggerbindings = bdvh.getTriggerbindings();
        this.viewer = bdvh.getViewerPanel();

        rectangleOverlay = new RectangleSelectorOverlay(viewer, this);

        behaviours = new Behaviours( new InputTriggerConfig(), "bdv" );
    }

    /**
     * @return the overlay layer associated with the source selector
     */
    public RectangleSelectorOverlay getRectangleSelectorOverlay() {
        return rectangleOverlay;
    }

    /**
     *
     * @return the BdhHandle associated to this Selector
     */
    public BdvHandle getBdvHandle() {
        return bdvh;
    }

    /**
     * Activate the selection mode
     */
    public synchronized void enable() {
        if (!isInstalled) {
            install();
        }
    }

    /**
     * Deactivate the selection mode
     */
    public synchronized void disable() {
        if (isInstalled) {
            uninstall();
        }
    }

    public synchronized boolean isEnabled() {
        return isInstalled;
    }

    /**
     * Completely disassociate the selector with this BdvHandle
     * TODO safe in terms of freeing memory ?
     */
    public void remove() {
        disable();
    }

    /**
     * Private : call enable instead
     */
    synchronized void install() {
        isInstalled = true;
        rectangleOverlay.addSelectionBehaviours(behaviours);
        behaviours.behaviour(new ClickBehaviour() {
            @Override
            public void click(int x, int y) {
                uninstall();
            }
        }, "cancel-set-rectangle", new String[]{"ESCAPE"});


        triggerbindings.addBehaviourMap(RECTANGLE_SELECTOR_MAP, behaviours.getBehaviourMap());
        triggerbindings.addInputTriggerMap(RECTANGLE_SELECTOR_MAP, behaviours.getInputTriggerMap(), "transform", "bdv");
        bos = BdvFunctions.showOverlay(rectangleOverlay, "Selector_Overlay", BdvOptions.options().addTo(bdvh));
        bdvh.getKeybindings().addInputMap("blocking-source-selector", new InputMap(), "bdv", "navigation");
    }

    public void addBehaviour(Behaviour behaviour, String behaviourName, String[] triggers) {
        behaviours.behaviour(behaviour, behaviourName, triggers);
    }

    /**
     * Private : call disable instead
     */
    synchronized void uninstall() {
        isInstalled = false;
        bos.removeFromBdv();
        triggerbindings.removeBehaviourMap( RECTANGLE_SELECTOR_MAP );
        triggerbindings.removeInputTriggerMap( RECTANGLE_SELECTOR_MAP );
        bdvh.getKeybindings().removeInputMap("blocking-source-selector");
    }

    volatile boolean rectangleSelected = false;
    volatile RealPoint startPt = null;
    volatile RealPoint endPt = null;

    void processSelectionEvent(RealPoint start, RealPoint end) {
        rectangleSelected = true;
        startPt = start;
        endPt = end;
    }

    public List<RealPoint> waitForSelection() {
        return waitForSelection(-1);
    }

    public List<RealPoint> waitForSelection(int timeOutInMs) {
        int totalTime = 0;
        if (timeOutInMs>0) {
            while ((endPt==null)&&(totalTime<timeOutInMs)) {
                try {
                    Thread.sleep(33);
                    totalTime+=33;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else {
            while ((endPt==null)) {
                try {
                    Thread.sleep(33);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        if (endPt==null) {
            return null;
        } else {
            List<RealPoint> pts = new ArrayList<>();
            pts.add(startPt);
            pts.add(endPt);
            startPt = null;
            endPt = null;
            return pts;
        }

    }

}