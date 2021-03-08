package ch.epfl.biop.bdv.command.userdefinedregion;

import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlaySource;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealPoint;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static bdv.util.BdvFunctions.showOverlay;
/**
 * Appends and controls a {@link PointsSelectorBehaviour} in a {@link BdvHandle}
 *
 * It is used in conjuncion with a BdvOverlay layer {@link PointsSelectorOverlay} which can be retrieved with
 * {@link PointsSelectorBehaviour#getPointsSelectorOverlay()}
 *
 * The selections can be triggered by GUI actions in the linked {@link PointsSelectorOverlay} or
 * directly programmatically
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL, 2020
 */

public class PointsSelectorBehaviour {

    final public static String POINTS_SELECTOR_MAP = "points-selector";

    final PointsSelectorOverlay pointsOverlay;

    BdvOverlaySource bos;

    final BdvHandle bdvh;

    final TriggerBehaviourBindings triggerbindings;

    final ViewerPanel viewer;

    final Behaviours behaviours;

    boolean isInstalled; // flag for the toggle action

    /**
     * Construct a SourceSelectorBehaviour
     * @param bdvh BdvHandle associated to this behaviour
     * @param message to display to the user as overlay on bdv
     */
    public PointsSelectorBehaviour(BdvHandle bdvh, String message) {
        this.bdvh = bdvh;
        this.triggerbindings = bdvh.getTriggerbindings();
        this.viewer = bdvh.getViewerPanel();

        pointsOverlay = new PointsSelectorOverlay(viewer, this, message);

        behaviours = new Behaviours( new InputTriggerConfig(), "bdv" );
    }

    /**
     * @return the overlay layer associated with the source selector
     */
    public PointsSelectorOverlay getPointsSelectorOverlay() {
        return pointsOverlay;
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
        pointsOverlay.addSelectionBehaviours(behaviours);
        behaviours.behaviour(new ClickBehaviour() {
            @Override
            public void click(int x, int y) {
                bos.removeFromBdv();
                uninstall(); userDone = true;
            }
        }, "cancel-set-points", new String[]{"ESCAPE"});

        triggerbindings.addBehaviourMap(POINTS_SELECTOR_MAP, behaviours.getBehaviourMap());
        triggerbindings.addInputTriggerMap(POINTS_SELECTOR_MAP, behaviours.getInputTriggerMap(), "transform", "bdv");
        bos = showOverlay(pointsOverlay, "Point_Selector_Overlay", BdvOptions.options().addTo(bdvh));
        bdvh.getKeybindings().addInputMap("blocking-source-selector_points", new InputMap(), "bdv", "navigation");
    }

    public void addBehaviour(Behaviour behaviour, String behaviourName, String[] triggers) {
        behaviours.behaviour(behaviour, behaviourName, triggers);
    }

    /**
     * Private : call disable instead
     */
    synchronized void uninstall() {
        isInstalled = false;
        //bos.removeFromBdv(); // NPE ??
        triggerbindings.removeBehaviourMap( POINTS_SELECTOR_MAP );
        triggerbindings.removeInputTriggerMap( POINTS_SELECTOR_MAP );
        bdvh.getKeybindings().removeInputMap("blocking-source-selector");
    }

    volatile List<RealPoint> points = new ArrayList<>();

    List<RealPoint> getPoints() {
        return new ArrayList<>(points);
    }

    void addPoint(RealPoint newPt) {
        points.add(newPt);
    }

    public List<RealPoint> waitForSelection() {
        return waitForSelection(-1);
    }

    public List<RealPoint> waitForSelection(int timeOutInMs) {
        int totalTime = 0;
        if (timeOutInMs>0) {
            while (!(isUserDone())&&(totalTime<timeOutInMs)) {
                try {
                    Thread.sleep(33);
                    totalTime+=33;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else {
            while (!(isUserDone())) {
                try {
                    Thread.sleep(33);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        userDone = false;
        return points;
    }

    volatile boolean userDone = false;

    private boolean isUserDone() {
        return userDone;
    }

}
