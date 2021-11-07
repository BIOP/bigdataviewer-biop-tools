package ch.epfl.biop.bdv.command.userdefinedregion;

import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlaySource;
import bdv.viewer.ViewerPanel;
import ch.epfl.biop.bdv.gui.card.CardHelper;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static bdv.ui.BdvDefaultCards.*;
import static bdv.util.BdvFunctions.showOverlay;

/**
 * Appends and controls a {@link RectangleSelectorBehaviour} in a {@link BdvHandle}
 *
 * It is used in conjuncion with a BdvOverlay layer RectangleSelectorOverlay which can be retrieved with
 * {@link RectangleSelectorBehaviour#getRectangleSelectorOverlay()}
 *
 * The selections can be triggered by GUI actions in the linked RectangleSelectorOverlay or
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

    final String userCardKey = "Rectangle Selection";

    boolean iniSplitPanelState;

    CardHelper.CardState iniCardState;

    JPanel pane;

    private boolean navigationEnabled = false;

    AffineTransform3D initialView;


    public RectangleSelectorBehaviour(BdvHandle bdvh, String message) {
        this(bdvh, message, null, null);
    }

    /**
     * Construct a SourceSelectorBehaviour
     * @param bdvh BdvHandle associated to this behaviour$
     * @param message to display to the user as overlay on bdv
     */
    public RectangleSelectorBehaviour(BdvHandle bdvh, String message, RealPoint p1, RealPoint p2) {
        this.bdvh = bdvh;
        this.triggerbindings = bdvh.getTriggerbindings();
        this.viewer = bdvh.getViewerPanel();
        rectangleOverlay = new RectangleSelectorOverlay(viewer, this, message);
        behaviours = new Behaviours( new InputTriggerConfig(), "bdv" );
        initialView = bdvh.getViewerPanel().state().getViewerTransform();

        JButton restoreView = new JButton("Restore initial view");
        restoreView.addActionListener((e)-> {
            bdvh.getViewerPanel().state().setViewerTransform(initialView);
        });

        JButton navigationButton = new JButton("Enable navigation");
        navigationButton.addActionListener((e) -> {
            if (navigationEnabled) {
                triggerbindings.addBehaviourMap(RECTANGLE_SELECTOR_MAP, behaviours.getBehaviourMap());
                triggerbindings.addInputTriggerMap(RECTANGLE_SELECTOR_MAP, behaviours.getInputTriggerMap(), "transform", "bdv");
                bdvh.getKeybindings().addInputMap("blocking-source-selector_rectangle", new InputMap(), "bdv", "navigation");
                navigationEnabled = false;
                navigationButton.setText("Re-enable navigation");
            } else {
                triggerbindings.removeBehaviourMap( RECTANGLE_SELECTOR_MAP );
                triggerbindings.removeInputTriggerMap( RECTANGLE_SELECTOR_MAP );
                bdvh.getKeybindings().removeInputMap("blocking-source-selector");
                navigationEnabled = true;
                navigationButton.setText("Enable rectangle selection");
            }
        });

        JButton restoreInitialRectangle = null;
        if (p1!=null && p2!=null) {
            rectangleOverlay.setRectangle(p1,p2);
            restoreInitialRectangle = new JButton("Restore initial rectangle");
            restoreInitialRectangle.addActionListener((e) -> rectangleOverlay.setRectangle(p1,p2));
        }

        JButton confirmationButton = new JButton("Confirm rectangle");
        confirmationButton.addActionListener((e) -> {
            if (endPt!=null) {
                userValidated = true;
            }
        });

        if (restoreInitialRectangle== null) {
            pane = box(false, new JLabel(message), box(false, navigationButton, restoreView), confirmationButton);
        } else {
            pane = box(false, new JLabel(message), box(false, navigationButton, restoreView), restoreInitialRectangle, confirmationButton);
        }
    }

    public static JPanel box(boolean alongX,JComponent... components) {
        JPanel box = new JPanel();
        if (alongX) {
            box.setLayout(new GridLayout(1, components.length));
        } else {
            box.setLayout(new GridLayout(components.length, 1));//new BoxLayout(box, BoxLayout.Y_AXIS));
        }
        for(JComponent component : components) {
            box.add(component);
        }
        return box;
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
        //bos = BdvFunctions.showOverlay(rectangleOverlay, "Selector_Overlay", BdvOptions.options().addTo(bdvh));
        bdvh.getKeybindings().addInputMap("blocking-source-selector_rectangle", new InputMap(), "bdv", "navigation");
        bos = showOverlay(rectangleOverlay, "Rectangle_Selector_Overlay", BdvOptions.options().addTo(bdvh));

        iniCardState = CardHelper.getCardState(bdvh);

        bdvh.getSplitPanel().setCollapsed(false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCEGROUPS_CARD, false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_VIEWERMODES_CARD, false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCES_CARD, false);
        bdvh.getCardPanel().addCard(userCardKey, pane, true);
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
        bdvh.getCardPanel().removeCard(userCardKey);
        CardHelper.restoreCardState(bdvh, iniCardState);
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

    boolean userValidated = false;

    public List<RealPoint> waitForSelection(int timeOutInMs) {
        int totalTime = 0;
        while (!userValidated) {
            if (timeOutInMs > 0) {
                while ((endPt == null) && (totalTime < timeOutInMs)) {
                    try {
                        Thread.sleep(33);
                        totalTime += 33;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                while ((endPt == null)) {
                    try {
                        Thread.sleep(33);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            try {
                Thread.sleep(33);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (endPt!=null) {
                rectangleOverlay.drawLastRectangle();
            }
        }

        rectangleOverlay.removeLastRectangle();

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
