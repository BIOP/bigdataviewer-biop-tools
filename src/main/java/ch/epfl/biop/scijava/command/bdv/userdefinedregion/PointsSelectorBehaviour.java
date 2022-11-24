package ch.epfl.biop.scijava.command.bdv.userdefinedregion;

import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlaySource;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerState;
import ch.epfl.biop.bdv.gui.card.CardHelper;
import ch.epfl.biop.bdv.gui.graphicalhandle.CircleGraphicalHandle;
import ch.epfl.biop.bdv.gui.graphicalhandle.GraphicalHandle;
import ch.epfl.biop.bdv.gui.graphicalhandle.GraphicalHandleListener;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static bdv.ui.BdvDefaultCards.*;
import static bdv.util.BdvFunctions.showOverlay;
import static ch.epfl.biop.scijava.command.bdv.userdefinedregion.RectangleSelectorBehaviour.box;

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

public class PointsSelectorBehaviour implements GraphicalHandleListener {

    final public static String POINTS_SELECTOR_MAP = "points-selector";

    final PointsSelectorOverlay pointsOverlay;

    BdvOverlaySource bos;

    final BdvHandle bdvh;

    final TriggerBehaviourBindings triggerbindings;

    final ViewerPanel viewer;

    final Behaviours behaviours;

    boolean isInstalled; // flag for the toggle action

    JPanel pane;

    private boolean navigationEnabled = false;

    AffineTransform3D initialView;

    final String userCardKey = "Points Selection";

    final Function<RealPoint, GraphicalHandle> graphicalHandleSupplier;

    Map<RealPoint, GraphicalHandle> ptToGraphicalHandle = new ConcurrentHashMap<>();

    volatile boolean userDone = false;

    public PointsSelectorBehaviour(BdvHandle bdvh, String message,
                                   Function<RealPoint, GraphicalHandle> graphicalHandleSupplier) {
        this.bdvh = bdvh;
        this.triggerbindings = bdvh.getTriggerbindings();
        this.viewer = bdvh.getViewerPanel();
        if (graphicalHandleSupplier == null) {
            this.graphicalHandleSupplier = (coords) -> new DefaultCircularHandle( () -> coords, bdvh.getViewerPanel().state(), 20);
        } else {
            this.graphicalHandleSupplier = graphicalHandleSupplier;
        }

        pointsOverlay = new PointsSelectorOverlay(viewer, this);

        behaviours = new Behaviours( new InputTriggerConfig(), "bdv" );

        initialView = bdvh.getViewerPanel().state().getViewerTransform();

        //JButton restoreView = new JButton("Restore initial view");

        /*restoreView.addActionListener((e)-> {
            bdvh.getViewerPanel().state().setViewerTransform(initialView);
        });*/

        JButton navigationButton = new JButton("Enable navigation");
        navigationButton.addActionListener((e) -> {
            if (navigationEnabled) {
                ptToGraphicalHandle.values().forEach(graphicalHandle -> graphicalHandle.enable());
                triggerbindings.addBehaviourMap(POINTS_SELECTOR_MAP, behaviours.getBehaviourMap());
                triggerbindings.addInputTriggerMap(POINTS_SELECTOR_MAP, behaviours.getInputTriggerMap(), "transform", "bdv");
                bdvh.getKeybindings().addInputMap("blocking-source-selector_rectangle", new InputMap(), "bdv", "navigation");
                navigationEnabled = false;
                navigationButton.setText("Re-enable navigation");
            } else {
                ptToGraphicalHandle.values().forEach(graphicalHandle -> graphicalHandle.disable());
                triggerbindings.removeBehaviourMap( POINTS_SELECTOR_MAP );
                triggerbindings.removeInputTriggerMap( POINTS_SELECTOR_MAP );
                bdvh.getKeybindings().removeInputMap("blocking-source-selector");
                navigationEnabled = true;
                navigationButton.setText("Enable point selection");
            }
        });

        JButton confirmationButton = new JButton("Confirm points");
        confirmationButton.addActionListener((e) -> {
            userDone = true;
        });

        JButton clearAllPointsButton = new JButton("Clear points");
        clearAllPointsButton.addActionListener((e) -> {
            clearPoints();
        });

        pane = box(false,new JLabel(message), box(false,navigationButton), clearAllPointsButton, confirmationButton);
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

    CardHelper.CardState iniCardState;

    /**
     * Private : call enable instead
     */
    synchronized void install() {
        isInstalled = true;
        pointsOverlay.addSelectionBehaviours(behaviours);
        behaviours.behaviour((ClickBehaviour) (x, y) -> {
            bos.removeFromBdv();
            uninstall(); userDone = true;
        }, "cancel-set-points", new String[]{"ESCAPE"});

        triggerbindings.addBehaviourMap(POINTS_SELECTOR_MAP, behaviours.getBehaviourMap());
        triggerbindings.addInputTriggerMap(POINTS_SELECTOR_MAP, behaviours.getInputTriggerMap(), "transform", "bdv");
        bos = showOverlay(pointsOverlay, "Point_Selector_Overlay", BdvOptions.options().addTo(bdvh));
        bdvh.getKeybindings().addInputMap("blocking-source-selector_points", new InputMap(), "bdv", "navigation");

        iniCardState = CardHelper.getCardState(bdvh);

        bdvh.getSplitPanel().setCollapsed(false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCEGROUPS_CARD, false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_VIEWERMODES_CARD, false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCES_CARD, false);
        //bdvh.getCardPanel().addCard(userCardKey, pane, true);
        BdvHandleHelper.addCard(bdvh, userCardKey, pane, true);

        bdvh.getViewerPanel().getDisplay().addHandler(pointsOverlay);
    }

    public void addBehaviour(Behaviour behaviour, String behaviourName, String[] triggers) {
        behaviours.behaviour(behaviour, behaviourName, triggers);
    }

    /**
     * Private : call disable instead
     */
    synchronized void uninstall() {
        isInstalled = false;
        bos.removeFromBdv(); // NPE ??
        triggerbindings.removeBehaviourMap( POINTS_SELECTOR_MAP );
        triggerbindings.removeInputTriggerMap( POINTS_SELECTOR_MAP );
        bdvh.getKeybindings().removeInputMap("blocking-source-selector");

        bdvh.getCardPanel().removeCard(userCardKey);
        CardHelper.restoreCardState(bdvh, iniCardState);

        bdvh.getViewerPanel().getDisplay().removeHandler(pointsOverlay);
    }

    private volatile List<RealPoint> points = new ArrayList<>();

    void addPoint(RealPoint newPt) {
        GraphicalHandle gh = graphicalHandleSupplier.apply(newPt);

        if (gh.getBehaviours()!=null) {
            gh.getBehaviours().behaviour(new DragBehaviour() {
                @Override
                public void init(int x, int y) {
                    bdvh.getViewerPanel().displayToGlobalCoordinates(x, y, newPt);
                    bdvh.getViewerPanel().requestRepaint();
                }

                @Override
                public void drag(int x, int y) {
                    bdvh.getViewerPanel().displayToGlobalCoordinates(x, y, newPt);
                    bdvh.getViewerPanel().requestRepaint();
                }

                @Override
                public void end(int x, int y) {
                    bdvh.getViewerPanel().displayToGlobalCoordinates(x, y, newPt);
                    bdvh.getViewerPanel().requestRepaint();
                }
            }, "drag_point", "button1");
        }

        gh.addGraphicalHandleListener(this);
        ptToGraphicalHandle.put(newPt, gh);
        points.add(newPt);
    }

    public Collection<GraphicalHandle> getGraphicalHandles() {
        return ptToGraphicalHandle.values();
    }

    void clearPoints() {
        ptToGraphicalHandle.values().forEach(gh -> gh.removeGraphicalHandleListener(this));
        ptToGraphicalHandle.clear();
        points.clear();
        bdvh.getBdvHandle().getViewerPanel().requestRepaint();
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

    private boolean isUserDone() {
        return userDone;
    }

    public static Integer[] defaultLandmarkColor = new Integer[]{210, 220, 24, 128+64+32};

    @Override
    public void disabled(GraphicalHandle gh) {

    }

    @Override
    public void enabled(GraphicalHandle gh) {

    }

    @Override
    public void hover_in(GraphicalHandle gh) {

    }

    @Override
    public void hover_out(GraphicalHandle gh) {

    }

    @Override
    public void created(GraphicalHandle gh) {

    }

    @Override
    public void removed(GraphicalHandle gh) {

    }

    public void mouseDragged(MouseEvent e) {
        ptToGraphicalHandle.values().forEach(gh -> gh.mouseDragged(e));
    }

    public void mouseMoved(MouseEvent e) {
        ptToGraphicalHandle.values().forEach(gh -> gh.mouseMoved(e));
    }

    public static class DefaultCircularHandle extends CircleGraphicalHandle {

        public DefaultCircularHandle(Supplier<RealPoint> globalCoord,
                                     final ViewerState vState,
                                     int radius) {
            super(null, null, null, null,
                    () -> {
                        RealPoint pt = new RealPoint(3);
                        vState.getViewerTransform().apply(globalCoord.get(), pt);
                        return new Integer[]{(int) pt.getDoublePosition(0), (int) pt.getDoublePosition(1), 0};
                    },
                    () -> {
                        RealPoint pt = new RealPoint(3);
                        vState.getViewerTransform().apply(globalCoord.get(), pt);
                        double distZ = pt.getDoublePosition(2);
                        if (Math.abs(distZ)<radius) {
                            return (int) (4 + Math.sqrt((radius) * (radius) - distZ * distZ));
                        } else {
                            return 4;
                        }
                    },
                    () -> defaultLandmarkColor);
        }

    }
}
