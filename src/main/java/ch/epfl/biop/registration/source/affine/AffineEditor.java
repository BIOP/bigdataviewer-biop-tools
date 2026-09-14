package ch.epfl.biop.registration.source.affine;

import bdv.TransformEventHandler2D;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.viewer.bdv.card.CardHelper;
import ch.epfl.biop.viewer.bdv.card.NavigationHelp;
import ch.epfl.biop.viewer.bdv.gizmo.AffineGizmo;
import ch.epfl.biop.viewer.bdv.gizmo.AffineGizmoOverlay;
import net.imglib2.RealPoint;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.ByteType;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.ClickBehaviour;
import sc.fiji.bdvpg.bdv.supplier.BdvSupplierHelper;
import sc.fiji.bdvpg.bdv.supplier.playground.PlaygroundSerializableBdvOptions;
import sc.fiji.bdvpg.scijava.service.SourceBdvDisplayService;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.SourceAndTimeRange;
import sc.fiji.bdvpg.source.SourceHelper;
import sc.fiji.bdvpg.source.transform.SourceTransformHelper;
import sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static bdv.ui.BdvDefaultCards.DEFAULT_SOURCEGROUPS_CARD;
import static bdv.ui.BdvDefaultCards.DEFAULT_SOURCES_CARD;
import static bdv.ui.BdvDefaultCards.DEFAULT_VIEWERMODES_CARD;

/**
 * Interactive edition of in-plane affine transforms: a 2D BigDataViewer window shows fixed sources and moving
 * sources transformed by the edited transform, which the user changes with an {@link AffineGizmo}.
 * <p>
 * Several {@link Pair}s can be edited in the same window, one at a time: Previous and Next, or the left and right
 * arrow keys, page through them and keep the view. A checkbox, on by default, applies each change made to the pair
 * shown to all pairs, see {@link AffineGizmo#applyChange(double[])}.
 * <p>
 * The window blocks the caller until the user applies or cancels the edition, closing the window cancels it.
 */
public class AffineEditor {

    /** Width, in pixels, given to the card panel when the window opens */
    private static final int CARD_PANEL_WIDTH = 360;

    /** Distance of the axis handles from the gizmo center, as a fraction of the smallest side of the region */
    private static final double HANDLE_LENGTH = 0.25;

    /** Id of the behaviours which page through the pairs */
    private static final String PAGING = "affine_editor_paging";

    /** Behaviours of a 2D window rotating the view with the left arrow key, shift or ctrl held or not */
    private static final String[] ROTATE_LEFT = {TransformEventHandler2D.ROTATE_LEFT,
            TransformEventHandler2D.ROTATE_LEFT_FAST, TransformEventHandler2D.ROTATE_LEFT_SLOW};

    /** Behaviours of a 2D window rotating the view with the right arrow key, shift or ctrl held or not */
    private static final String[] ROTATE_RIGHT = {TransformEventHandler2D.ROTATE_RIGHT,
            TransformEventHandler2D.ROTATE_RIGHT_FAST, TransformEventHandler2D.ROTATE_RIGHT_SLOW};

    /**
     * What is edited for one slice: moving sources displayed with the edited transform over fixed sources
     */
    public static class Pair {

        final SourceAndConverter<?>[] fixed;
        final SourceAndConverter<?>[] moving;
        final AffineTransform3D initial;
        final double[] roi;
        final String name;

        /**
         * @param fixed sources displayed as they are
         * @param moving sources displayed with the edited transform applied
         * @param initial transform to start from, not modified
         * @param roi region of interest {x, y, width, height} in world coordinates: the gizmo sits at its center.
         *            If null, the bounding box of the first moving source is used
         * @param name shown when several pairs are edited, can be null
         */
        public Pair(SourceAndConverter<?>[] fixed, SourceAndConverter<?>[] moving, AffineTransform3D initial,
                    double[] roi, String name) {
            this.fixed = fixed;
            this.moving = moving;
            this.initial = initial;
            this.roi = roi;
            this.name = name;
        }
    }

    /**
     * Opens the editor on a single pair and blocks until the user applies or cancels.
     * Do not call from the event dispatch thread.
     * @param fixed sources displayed as they are
     * @param moving sources displayed with the edited transform applied
     * @param initial transform to start from, not modified
     * @param roi region of interest {x, y, width, height} in world coordinates: the gizmo sits at its center.
     *            If null, the bounding box of the first moving source is used
     * @param timePoint time point displayed
     * @param title title of the window
     * @return the edited transform, or null if the edition was cancelled
     */
    public static AffineTransform3D edit(SourceAndConverter<?>[] fixed, SourceAndConverter<?>[] moving,
                                         AffineTransform3D initial, double[] roi, int timePoint, String title) {
        List<AffineTransform3D> result = edit(Collections.singletonList(new Pair(fixed, moving, initial, roi, null)),
                timePoint, title);
        return result == null ? null : result.get(0);
    }

    /**
     * Opens the editor on several pairs, showing the first one, and blocks until the user applies or cancels.
     * With a single pair, the paging controls are hidden. Do not call from the event dispatch thread.
     * @param pairs what to edit. The view is set on the first pair and kept while paging: all pairs should lie
     *              around the same region
     * @param timePoint time point displayed
     * @param title title of the window
     * @return the edited transforms, one per pair in the same order, or null if the edition was cancelled
     */
    public static List<AffineTransform3D> edit(List<Pair> pairs, int timePoint, String title) {
        final int n = pairs.size();
        final AffineGizmo[] gizmos = new AffineGizmo[n];
        for (int i = 0; i < n; i++) {
            Pair pair = pairs.get(i);
            double[] roi = (pair.roi == null) ? boundingBox(pair.moving[0], timePoint) : pair.roi;
            gizmos[i] = new AffineGizmo(pair.initial, roi[0] + roi[2] / 2.0, roi[1] + roi[3] / 2.0,
                    HANDLE_LENGTH * Math.min(roi[2], roi[3]));
        }

        final SourceAndConverter<?>[][] movingDisplayed = new SourceAndConverter[n][];
        final BdvHandle[] bdvhHolder = new BdvHandle[1];
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean applied = new AtomicBoolean(false);

        try {
            EventQueue.invokeAndWait(() -> {
                final BdvHandle bdvh = createBdvHandle(title);
                bdvhHolder[0] = bdvh;

                final SourceBdvDisplayService displayService = SourceServices.getBdvDisplayService();
                displayService.registerBdvHandle(bdvh);
                // Created for all pairs at once: they read the original sources and share their caches
                for (int i = 0; i < n; i++) {
                    SourceAndConverter<?>[] moving = pairs.get(i).moving;
                    movingDisplayed[i] = new SourceAndConverter[moving.length];
                    for (int c = 0; c < moving.length; c++) {
                        movingDisplayed[i][c] = SourceTransformHelper.createNewTransformedSourceAndConverter(
                                pairs.get(i).initial.copy(), new SourceAndTimeRange<>(moving[c], timePoint));
                    }
                }
                final SourceAndConverter<?>[] fixed = pairs.get(0).fixed;
                displayService.show(bdvh, fixed);
                displayService.show(bdvh, movingDisplayed[0]);
                new ViewerTransformAdjuster(bdvh, Stream.concat(Stream.of(fixed), Stream.of(movingDisplayed[0]))
                        .toArray(SourceAndConverter[]::new)).run();
                setZToZero(bdvh);

                new Session(bdvh, pairs, gizmos, movingDisplayed).build(apply -> {
                    // Everything runs on the event dispatch thread: the first way the user ends the edition wins
                    if (latch.getCount() == 0) return;
                    applied.set(apply);
                    latch.countDown();
                });
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            close(bdvhHolder[0], movingDisplayed);
        }

        if (!applied.get()) return null;
        List<AffineTransform3D> result = new ArrayList<>();
        for (AffineGizmo gizmo : gizmos) result.add(gizmo.getTransform());
        return result;
    }

    /**
     * An open editor: its gizmos, the pair shown and the card. Used on the event dispatch thread only.
     */
    private static class Session {

        final BdvHandle bdvh;
        final List<Pair> pairs;
        final AffineGizmo[] gizmos;
        final SourceAndConverter<?>[][] movingDisplayed;
        final int n;

        AffineGizmoOverlay overlay;
        final JLabel pairLabel = new JLabel();
        final JLabel linkedLabel = new JLabel("<html><b>Changes apply to all slices</b></html>");
        final JButton previousButton = new JButton("Previous");
        final JButton nextButton = new JButton("Next");
        final JCheckBox linkBox;

        /** Index of the pair shown */
        int current = 0;

        /** Index of the pair whose gizmo is dragged */
        int dragged = 0;

        Session(BdvHandle bdvh, List<Pair> pairs, AffineGizmo[] gizmos, SourceAndConverter<?>[][] movingDisplayed) {
            this.bdvh = bdvh;
            this.pairs = pairs;
            this.gizmos = gizmos;
            this.movingDisplayed = movingDisplayed;
            this.n = pairs.size();
            linkBox = new JCheckBox("Apply each change to all " + n + " slices", true);
        }

        /**
         * Adds the gizmo, the paging keys and the card to the window
         * @param finish called with true to apply the edition, with false to cancel it
         */
        void build(Consumer<Boolean> finish) {
            overlay = new AffineGizmoOverlay(bdvh, gizmos[0], this::dragChanged);
            overlay.setOnDragStart(this::dragStarted);
            if (n > 1) installPagingKeys();
            BdvFunctions.showOverlay(overlay, "Affine gizmo", BdvOptions.options().addTo(bdvh));
            overlay.install();

            final JButton resetButton = new JButton("Reset");
            resetButton.addActionListener(e -> reset());
            final JButton applyButton = new JButton("Apply transformation");
            applyButton.addActionListener(e -> finish.accept(true));
            final JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(e -> finish.accept(false));
            BdvHandleHelper.getJFrame(bdvh).addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    finish.accept(false);
                }
            });

            List<Component> components = new ArrayList<>();
            components.add(new JLabel(html(
                    "<b>Drag the handles to move the moving sources onto the fixed ones.</b><br><br>" +
                    "<b>White</b>: translate.<br>" +
                    "<b>Red</b> and <b>green</b>: set the x and y axes, which scales and shears.<br>" +
                    "<b>Yellow</b>: rotate and scale both axes.<br><br>" +
                    "Hold shift to constrain a drag: translate along x or y only, " +
                    "keep the direction of an axis, rotate without scaling.<br><br>" +
                    ((n > 1) ?
                        "<b>Previous</b> and <b>Next</b>, or the <b>left</b> and <b>right arrow keys</b>, " +
                        "show the other slices without moving the view.<br><br>" +
                        "While <b>Apply each change to all " + n + " slices</b> is checked, a drag changes all " +
                        "slices the same way, and <b>Reset</b> resets them all. Unchecked, both only change the " +
                        "slice shown.<br><br>" :
                        "") +
                    "<b>Reset</b> goes back to the transformation the edition started from.<br><br>" +
                    NavigationHelp.html(bdvh))));
            if (n > 1) {
                previousButton.addActionListener(e -> show(current - 1));
                nextButton.addActionListener(e -> show(current + 1));
                linkBox.addActionListener(e -> updateCard());
                // Keeps the focus in the viewer, where the arrow keys are handled
                previousButton.setFocusable(false);
                nextButton.setFocusable(false);
                linkBox.setFocusable(false);
                JPanel paging = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                paging.add(previousButton);
                paging.add(Box.createHorizontalStrut(3));
                paging.add(nextButton);
                paging.add(Box.createHorizontalStrut(10));
                paging.add(linkedLabel);
                components.add(pairLabel);
                components.add(paging);
                components.add(linkBox);
                updateCard();
            }
            components.add(resetButton);
            components.add(applyButton);
            components.add(cancelButton);

            BdvHandleHelper.addCard(bdvh, "Affine transformation", box(components.toArray(new Component[0])), true);
            CardHelper.expandCardPanel(bdvh, CARD_PANEL_WIDTH);
        }

        void dragStarted() {
            dragged = current;
            for (int i = 0; i < n; i++) {
                if (i != dragged) gizmos[i].startChange();
            }
        }

        void dragChanged() {
            if (linkBox.isSelected()) {
                double[] change = gizmos[dragged].getDragChange();
                if (change != null) {
                    for (int i = 0; i < n; i++) {
                        if (i == dragged) continue;
                        gizmos[i].applyChange(change);
                        updateMoving(i);
                    }
                }
            }
            updateMoving(dragged);
            if (n > 1) updateCard();
            bdvh.getViewerPanel().requestRepaint();
        }

        void reset() {
            for (int i = 0; i < n; i++) {
                if ((i == current) || linkBox.isSelected()) {
                    gizmos[i].reset();
                    updateMoving(i);
                }
            }
            if (n > 1) updateCard();
            bdvh.getViewerPanel().getDisplay().repaint();
            bdvh.getViewerPanel().requestRepaint();
        }

        /**
         * Shows another pair, keeping the view, and the color and display range of the sources channel by channel
         */
        void show(int index) {
            if ((index < 0) || (index >= n) || (index == current)) return;
            final SourceBdvDisplayService displayService = SourceServices.getBdvDisplayService();
            copyDisplaySettings(pairs.get(current).fixed, pairs.get(index).fixed);
            copyDisplaySettings(movingDisplayed[current], movingDisplayed[index]);
            displayService.remove(bdvh, movingDisplayed[current]);
            displayService.remove(bdvh, pairs.get(current).fixed);
            displayService.show(bdvh, pairs.get(index).fixed);
            displayService.show(bdvh, movingDisplayed[index]);
            current = index;
            overlay.setGizmo(gizmos[current]);
            updateCard();
            bdvh.getViewerPanel().requestRepaint();
        }

        void updateMoving(int index) {
            AffineTransform3D transform = gizmos[index].getTransform();
            for (SourceAndConverter<?> source : movingDisplayed[index]) {
                ((TransformedSource<?>) source.getSpimSource()).setFixedTransform(transform);
            }
        }

        void updateCard() {
            String name = pairs.get(current).name;
            pairLabel.setText(html("Slice " + (current + 1) + " / " + n + ((name == null) ? "" : ": " + name) +
                    (gizmos[current].isChanged() ? " <b>(changed)</b>" : "")));
            previousButton.setEnabled(current > 0);
            nextButton.setEnabled(current < n - 1);
            linkedLabel.setVisible(linkBox.isSelected());
        }

        /**
         * Takes over the left and right arrow keys, which rotate the view of a 2D window, to page through the pairs.
         * The rotation behaviours are overridden by name rather than their keys rebound: a new input trigger map would
         * have to copy the navigation bindings and block the original ones, and that copy would still pan the view
         * while {@link AffineGizmoOverlay} holds the left button.
         */
        void installPagingKeys() {
            final ClickBehaviour previous = (x, y) -> show(current - 1), next = (x, y) -> show(current + 1);
            final BehaviourMap behaviours = new BehaviourMap();
            for (String rotation : ROTATE_LEFT) behaviours.put(rotation, previous);
            for (String rotation : ROTATE_RIGHT) behaviours.put(rotation, next);
            // Added last, so it overrides the behaviours of BigDataViewer with the same names
            bdvh.getTriggerbindings().addBehaviourMap(PAGING, behaviours);
        }
    }

    private static String html(String text) {
        return "<html><div style='width:" + (CARD_PANEL_WIDTH - 60) + "px'>" + text + "</div></html>";
    }

    /**
     * Copies the color and the display range of each source to the source of the same index
     */
    private static void copyDisplaySettings(SourceAndConverter<?>[] from, SourceAndConverter<?>[] to) {
        for (int i = 0; i < Math.min(from.length, to.length); i++) {
            if (from[i] == to[i]) continue;
            ConverterSetup source = SourceServices.getSourceService().getConverterSetup(from[i]);
            ConverterSetup target = SourceServices.getSourceService().getConverterSetup(to[i]);
            if ((source == null) || (target == null)) continue;
            target.setDisplayRange(source.getDisplayRangeMin(), source.getDisplayRangeMax());
            if (source.supportsColor() && target.supportsColor()) target.setColor(source.getColor());
        }
    }

    /**
     * @return the xy bounding box {x, y, width, height} of a source, assuming it is not warped
     */
    private static double[] boundingBox(SourceAndConverter<?> source, int timePoint) {
        AffineTransform3D transform = new AffineTransform3D();
        source.getSpimSource().getSourceTransform(timePoint, 0, transform);
        long[] dims = source.getSpimSource().getSource(timePoint, 0).dimensionsAsLongArray();
        double width = Math.hypot(transform.get(0, 0), transform.get(1, 0)) * dims[0];
        double height = Math.hypot(transform.get(0, 1), transform.get(1, 1)) * dims[1];
        RealPoint center = SourceHelper.getSourceCenterPoint(source, timePoint);
        return new double[]{center.getDoublePosition(0) - width / 2.0, center.getDoublePosition(1) - height / 2.0, width, height};
    }

    /**
     * Closes the window and unregisters the sources which were created for it
     */
    private static void close(BdvHandle bdvh, SourceAndConverter<?>[][] movingDisplayed) {
        if (bdvh == null) return;
        SourceServices.getBdvDisplayService().closeBdv(bdvh);
        bdvh.close();
        for (SourceAndConverter<?>[] sources : movingDisplayed) {
            if (sources == null) continue;
            for (SourceAndConverter<?> source : sources) {
                if (source != null) SourceServices.getSourceService().remove(source);
            }
        }
    }

    /**
     * Sets the viewer transform so that the z = 0 plane is displayed, whatever the position of the sources along z
     */
    private static void setZToZero(BdvHandle bdvh) {
        AffineTransform3D transform = bdvh.getViewerPanel().state().getViewerTransform();
        transform.set(0, 2, 3);
        bdvh.getViewerPanel().state().setViewerTransform(transform);
    }

    /**
     * @return an empty 2D BigDataViewer window
     */
    private static BdvHandle createBdvHandle(String title) {
        PlaygroundSerializableBdvOptions sOptions = new PlaygroundSerializableBdvOptions();
        sOptions.is2D = true;
        sOptions.width = 1200;
        sOptions.height = 800;
        sOptions.interpolate = false;
        sOptions.frameTitle = title;
        sOptions.numTimePoints = 1;

        BdvOptions options = sOptions.getBdvOptions().sourceTransform(new AffineTransform3D());
        BdvStackSource<ByteType> bss = BdvFunctions.show(ArrayImgs.bytes(2L, 2L, 2L), "dummy", options);
        BdvHandle bdvh = bss.getBdvHandle();
        bdvh.getViewerPanel().state().removeSource(bdvh.getViewerPanel().state().getCurrentSource());
        bdvh.getViewerPanel().setNumTimepoints(sOptions.numTimePoints);
        BdvSupplierHelper.addSourcesDragAndDrop(bdvh);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCES_CARD, true);
        bdvh.getCardPanel().removeCard(DEFAULT_SOURCEGROUPS_CARD);
        bdvh.getCardPanel().removeCard(DEFAULT_VIEWERMODES_CARD);
        return bdvh;
    }

    private static JPanel box(Component... components) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        for (Component component : components) {
            if (component instanceof JComponent) {
                ((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            panel.add(component);
            panel.add(Box.createVerticalStrut(3));
        }
        return panel;
    }

}
