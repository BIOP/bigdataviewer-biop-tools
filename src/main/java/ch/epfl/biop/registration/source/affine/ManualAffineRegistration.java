package ch.epfl.biop.registration.source.affine;

import bdv.KeyConfigContexts;
import bdv.TransformEventHandler2D;
import bdv.tools.transformation.TransformedSource;
import bdv.ui.splitpanel.SplitPanel;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.TransformListener;
import ch.epfl.biop.registration.plugin.IRegistrationPlugin;
import ch.epfl.biop.registration.plugin.RegistrationTypeProperties;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.ByteType;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.InputTrigger;
import sc.fiji.bdvpg.bdv.supplier.BdvSupplierHelper;
import sc.fiji.bdvpg.bdv.supplier.playground.PlaygroundSerializableBdvOptions;
import sc.fiji.bdvpg.scijava.service.SourceBdvDisplayService;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.SourceAndTimeRange;
import sc.fiji.bdvpg.source.transform.SourceTransformHelper;
import sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.viewer.bdv.ManualRegistrationStarter;
import sc.fiji.bdvpg.viewer.bdv.config.BdvKeymapHelper;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static bdv.ui.BdvDefaultCards.DEFAULT_SOURCEGROUPS_CARD;
import static bdv.ui.BdvDefaultCards.DEFAULT_SOURCES_CARD;
import static bdv.ui.BdvDefaultCards.DEFAULT_VIEWERMODES_CARD;

/**
 * Interactive affine registration: the user drags, rotates and zooms the moving sources
 * onto the fixed ones in a dedicated BigDataViewer window, and the resulting transformation
 * is stored as the affine transform of this registration step.
 * <p>
 * The interaction relies on {@link ManualRegistrationStarter}: while the 'move' mode is
 * active, any change of the viewer transform is compensated on the moving sources, which
 * therefore stay at a fixed position relative to the screen and move relative to the fixed
 * sources. The mode can be toggled on and off as many times as needed, so that the user can
 * navigate (zoom in to check the alignment, for instance) without altering the transform.
 * <p>
 * Because the transformation is built from the viewer transform, it is a 2D similarity
 * (rotation, isotropic scaling and translation in the XY plane) rather than a general affine.
 * This is what a user can meaningfully specify with a mouse, and it composes with the other
 * (automated) affine steps of the Warpy workflow.
 * <p>
 * This registration is editable: editing it reopens the same window with the previously
 * defined transformation already applied to the moving sources.
 */
@Plugin(type = IRegistrationPlugin.class)
@RegistrationTypeProperties(
        isManual = true,
        isEditable = true)
public class ManualAffineRegistration extends AffineTransformSourceRegistration {

    @Override
    public boolean register() {
        // Nothing has been defined yet: the moving sources are displayed as they are
        return interactiveRegistration(new AffineTransform3D(), "Manual Affine Registration");
    }

    @Override
    public boolean edit() {
        // at3d holds the transformation of this step: the moving sources are displayed with it
        // already applied, and the user refines it
        return interactiveRegistration(at3d.copy(), "Edit Manual Affine Registration");
    }

    /**
     * Opens a BigDataViewer window where the user aligns the moving sources onto the fixed
     * ones, and blocks until the transformation is either applied or cancelled.
     *
     * @param initialTransform transformation already applied to the moving sources when the
     *                         window opens - identity for a new registration, the current
     *                         transformation when editing
     * @param title            title of the registration window
     * @return true if the user applied a transformation, false if the registration was
     * cancelled - in which case {@link ManualAffineRegistration#at3d} is left untouched
     */
    private boolean interactiveRegistration(AffineTransform3D initialTransform, String title) {

        // Transiently wrapped moving sources: they are the ones the user actually moves, and
        // they accumulate the successive manual transformations. They are thrown away at the
        // end of the registration, only their transform is kept.
        final SourceAndConverter<?>[] movingDisplayed = new SourceAndConverter[mimg.length];
        final BdvHandle[] bdvhHolder = new BdvHandle[1];

        // Countdown released when the user applies or cancels the registration
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean applied = new AtomicBoolean(false);
        // Makes sure the registration is terminated once only, whichever way the user ends it
        final AtomicBoolean finished = new AtomicBoolean(false);

        // State of the 'move' mode - accessed from the EDT only
        final ManualRegistrationStarter[] starterHolder = new ManualRegistrationStarter[1];
        final AtomicBoolean viewerMoved = new AtomicBoolean(false);

        try {
            EventQueue.invokeAndWait(() -> {
                final BdvHandle bdvh = createBdvHandle(title);
                bdvhHolder[0] = bdvh;

                final SourceBdvDisplayService displayService = SourceServices.getBdvDisplayService();
                displayService.registerBdvHandle(bdvh);

                for (int i = 0; i < mimg.length; i++) {
                    movingDisplayed[i] = SourceTransformHelper.createNewTransformedSourceAndConverter(
                            initialTransform.copy(), new SourceAndTimeRange(mimg[i], timePoint));
                }

                displayService.show(bdvh, fimg);
                displayService.show(bdvh, movingDisplayed);

                final List<SourceAndConverter<?>> allSources = new ArrayList<>();
                allSources.addAll(Arrays.asList(fimg));
                allSources.addAll(Arrays.asList(movingDisplayed));
                new ViewerTransformAdjuster(bdvh, allSources.toArray(new SourceAndConverter[0])).run();
                setZToZero(bdvh);

                final TransformListener<AffineTransform3D> movedListener = t -> viewerMoved.set(true);

                final JButton moveButton = new JButton(MOVE_TEXT);
                final JButton applyButton = new JButton("Apply transformation");
                final JButton cancelButton = new JButton("Cancel");

                // Leaves the move mode if it is on, keeping the transformation defined so far
                // only if the registration is not being cancelled
                final Consumer<Boolean> terminate = (keepTransform) -> {
                    if (starterHolder[0] != null) {
                        stopMoveMode(bdvh, starterHolder[0], keepTransform && viewerMoved.get(), movingDisplayed);
                        bdvh.getViewerPanel().transformListeners().remove(movedListener);
                        starterHolder[0] = null;
                    }
                };

                moveButton.addActionListener(e -> {
                    if (starterHolder[0] == null) {
                        ManualRegistrationStarter starter = new ManualRegistrationStarter(bdvh, movingDisplayed);
                        starter.run();
                        viewerMoved.set(false);
                        bdvh.getViewerPanel().transformListeners().add(movedListener);
                        starterHolder[0] = starter;
                        moveButton.setText(FIX_TEXT);
                    } else {
                        stopMoveMode(bdvh, starterHolder[0], viewerMoved.get(), movingDisplayed);
                        bdvh.getViewerPanel().transformListeners().remove(movedListener);
                        starterHolder[0] = null;
                        moveButton.setText(MOVE_TEXT);
                    }
                });

                applyButton.addActionListener(e -> {
                    if (!finished.compareAndSet(false, true)) return;
                    terminate.accept(true);
                    applied.set(true);
                    latch.countDown();
                });

                cancelButton.addActionListener(e -> {
                    if (!finished.compareAndSet(false, true)) return;
                    terminate.accept(false);
                    latch.countDown();
                });

                BdvHandleHelper.addCard(bdvh, "Manual affine registration",
                        box(new JLabel("<html><div style='width:" + (CARD_PANEL_WIDTH - 60) + "px'>" +
                                        "<b>Move the moving sources onto the fixed ones.</b><br><br>" +
                                        "While the moving mode is on, the moving sources stay at the same " +
                                        "position relative to the screen: navigating in the window moves " +
                                        "them relative to the fixed sources.<br><br>" +
                                        "<b>" + triggerText(bdvh, TransformEventHandler2D.DRAG_TRANSLATE,
                                        TransformEventHandler2D.DRAG_TRANSLATE_KEYS, " drag") +
                                        "</b> &ndash; translate<br>" +
                                        "<b>" + triggerText(bdvh, TransformEventHandler2D.DRAG_ROTATE,
                                        TransformEventHandler2D.DRAG_ROTATE_KEYS, " drag") +
                                        "</b> &ndash; rotate<br>" +
                                        "<b>" + triggerText(bdvh, TransformEventHandler2D.ZOOM_NORMAL,
                                        TransformEventHandler2D.ZOOM_NORMAL_KEYS, "") +
                                        "</b> &ndash; scale<br><br>" +
                                        "Switch the moving mode off to navigate - zoom in to check the " +
                                        "alignment, for instance - without moving the sources." +
                                        "</div></html>"),
                                moveButton,
                                applyButton,
                                cancelButton),
                        true);
                expandCardPanel(bdvh);

                // Closing the window is a cancellation
                BdvHandleHelper.getJFrame(bdvh).addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        if (!finished.compareAndSet(false, true)) return;
                        terminate.accept(false);
                        latch.countDown();
                    }
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
        }

        // The transform accumulated by the wrapped moving sources is the result of this step
        final AffineTransform3D result = new AffineTransform3D();
        ((TransformedSource<?>) movingDisplayed[0].getSpimSource()).getFixedTransform(result);

        closeBdvHandle(bdvhHolder[0], movingDisplayed);

        if (!applied.get()) {
            errorMessage = "Manual affine registration cancelled by the user.";
            return false;
        }

        at3d = result;
        isDone = true;
        return true;
    }

    /**
     * Leaves the 'move' mode: removes the transient sources created by the starter and, if the
     * user actually moved the view, appends the corresponding transformation to the wrapped
     * moving sources.
     * <p>
     * This does the job of {@code ManualRegistrationStopper}, except that it also handles the
     * case where the view has not been moved at all - the stopper unconditionally reads the
     * current transform of the starter, which is null until the first view change.
     *
     * @param bdvh            the registration window
     * @param starter         the started manual registration
     * @param keepTransform   whether the transformation has to be kept - false when the view
     *                        was not moved, or when the registration is being cancelled
     * @param movingDisplayed the wrapped moving sources which accumulate the transformations
     */
    private void stopMoveMode(BdvHandle bdvh, ManualRegistrationStarter starter,
                              boolean keepTransform, SourceAndConverter<?>[] movingDisplayed) {

        bdvh.getViewerPanel().transformListeners().remove(starter.getListener());

        final SourceBdvDisplayService displayService = SourceServices.getBdvDisplayService();
        final List<SourceAndConverter<?>> transientSources = starter.getTransformedSourceAndConverterDisplayed();
        displayService.remove(bdvh, transientSources.toArray(new SourceAndConverter[0]));
        for (SourceAndConverter<?> source : transientSources) {
            SourceServices.getSourceService().remove(source);
        }

        if (keepTransform) {
            AffineTransform3D delta = starter.getCurrentTransform().copy();
            for (SourceAndConverter<?> source : movingDisplayed) {
                SourceTransformHelper.mutateTransformedSourceAndConverter(delta,
                        new SourceAndTimeRange(source, timePoint));
            }
        }

        displayService.show(bdvh, movingDisplayed);
    }

    /**
     * Closes the registration window and unregisters the sources which were created for it.
     *
     * @param bdvh            the registration window, may be null if it could not be created
     * @param movingDisplayed the wrapped moving sources to unregister
     */
    private void closeBdvHandle(BdvHandle bdvh, SourceAndConverter<?>[] movingDisplayed) {
        if (bdvh == null) return;
        SourceServices.getBdvDisplayService().closeBdv(bdvh);
        bdvh.close();
        for (SourceAndConverter<?> source : movingDisplayed) {
            if (source != null) SourceServices.getSourceService().remove(source);
        }
    }

    /**
     * Tells which mouse or key triggers a navigation command is bound to, as a short readable
     * string - 'Left click drag', 'Mouse wheel'.
     * <p>
     * The bindings are user configurable - the keymap page of the BDV preferences, bound to
     * ctrl COMMA, edits them - so they are read from the keymap of the window rather than
     * hardcoded. Every trigger a command is bound to is listed: the alternatives are gestures
     * a user may well be used to rather than variants of one another, the BIOP keymap for
     * instance rotating on a middle drag and on a shift left drag alike.
     *
     * @param bdvh        the window whose keymap is read
     * @param commandName name of the command, see {@link TransformEventHandler2D}
     * @param defaults    triggers to fall back on when the keymap does not mention the command
     * @param suffix      appended to each trigger, ' drag' for the dragging commands
     * @return the triggers, separated by ' or ', or an empty string if the command is unbound
     */
    private static String triggerText(BdvHandle bdvh, String commandName, String[] defaults,
                                      String suffix) {

        Set<InputTrigger> bound = BdvKeymapHelper.getConfig(bdvh)
                .getInputs(commandName, KeyConfigContexts.BIGDATAVIEWER);

        Stream<String> triggers = bound.isEmpty()
                ? Arrays.stream(defaults)
                : bound.stream().map(InputTrigger::toString);

        String text = triggers
                .filter(trigger -> !NOT_MAPPED.equals(trigger))
                .map(trigger -> MODIFIER.matcher(trigger).replaceAll("$1 + ")
                        .replace("button1", "left click")
                        .replace("button2", "middle click")
                        .replace("button3", "right click")
                        .replace("scroll", "mouse wheel") + suffix)
                .collect(Collectors.joining(" or "));

        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Modifier keys, as {@link InputTrigger#toString()} writes them - 'shift button1'. The
     * trailing space is part of the match so that the replacement spells them out as
     * 'shift + button1'.
     */
    private static final Pattern MODIFIER = Pattern.compile("\\b(shift|ctrl|meta|alt)\\b ");

    /** How {@link InputTrigger#toString()} spells a binding which is deliberately blocked */
    private static final String NOT_MAPPED = "not mapped";

    /**
     * Sets the viewer transform so that the z = 0 plane is displayed, whatever the position
     * of the sources along z.
     *
     * @param bdvh the viewer to adjust
     */
    private static void setZToZero(BdvHandle bdvh) {
        AffineTransform3D transform3D = new AffineTransform3D();
        bdvh.getViewerPanel().state().getViewerTransform(transform3D);
        AffineTransform3D recenter = transform3D.copy();
        recenter.set(0, 2, 3);
        bdvh.getViewerPanel().state().setViewerTransform(recenter);
    }

    /**
     * Creates the 2D BigDataViewer window used for the manual registration.
     *
     * @param title title of the window
     * @return an empty bdv window
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
        ArrayImg<ByteType, ByteArray> dummyImg = ArrayImgs.bytes(2L, 2L, 2L);
        BdvStackSource<ByteType> bss = BdvFunctions.show(dummyImg, "dummy", options);
        BdvHandle bdvh = bss.getBdvHandle();
        if (sOptions.interpolate) {
            bdvh.getViewerPanel().setInterpolation(Interpolation.NLINEAR);
        }

        bdvh.getViewerPanel().state().removeSource(bdvh.getViewerPanel().state().getCurrentSource());
        bdvh.getViewerPanel().setNumTimepoints(sOptions.numTimePoints);
        BdvSupplierHelper.addSourcesDragAndDrop(bdvh);
        // The split panel is deliberately NOT expanded here: see expandCardPanel
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCES_CARD, true);
        bdvh.getCardPanel().removeCard(DEFAULT_SOURCEGROUPS_CARD);
        bdvh.getCardPanel().removeCard(DEFAULT_VIEWERMODES_CARD);
        return bdvh;
    }

    /**
     * Makes the card panel visible, with a usable width.
     * <p>
     * This cannot be done while the window is being built: {@code SplitPanel.setCollapsed(false)}
     * places the divider from the current width of the panel, which is still zero as long as the
     * frame has not been laid out. The panel then ends up expanded but with a zero width - it
     * simply looks absent. The expansion is therefore deferred to the first layout pass, and the
     * divider is placed explicitly rather than left to the default.
     *
     * @param bdvh the viewer whose card panel must be shown
     */
    private static void expandCardPanel(BdvHandle bdvh) {
        final SplitPanel splitPanel = bdvh.getSplitPanel();

        final Runnable expand = () -> {
            int width = splitPanel.getWidth();
            if (width <= 0) return;
            // setCollapsed is a no-op if the panel is already expanded: collapse it first so that
            // the divider is recomputed
            splitPanel.setCollapsed(true);
            splitPanel.setCollapsed(false);
            splitPanel.setDividerLocation(Math.max(width / 2, width - CARD_PANEL_WIDTH));
        };

        SwingUtilities.invokeLater(() -> {
            if (splitPanel.getWidth() > 0) {
                expand.run();
            } else {
                // The frame has not been laid out yet: wait for it
                splitPanel.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent e) {
                        if (splitPanel.getWidth() <= 0) return;
                        splitPanel.removeComponentListener(this);
                        expand.run();
                    }
                });
            }
        });
    }

    private static JPanel box(Component... components) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        for (Component component : components) {
            if (component instanceof javax.swing.JComponent) {
                ((javax.swing.JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            panel.add(component);
            panel.add(Box.createVerticalStrut(3));
        }
        return panel;
    }

    private static final String MOVE_TEXT = "Start moving the moving sources";

    private static final String FIX_TEXT = "Stop moving the moving sources";

    /** Width, in pixels, given to the card panel when the registration window opens */
    private static final int CARD_PANEL_WIDTH = 360;

    @Override
    public void abort() {

    }

    String errorMessage = "Unspecified error";

    @Override
    public String getExceptionMessage() {
        return errorMessage;
    }

    String name = "Manual Affine";

    @Override
    public void setRegistrationName(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }

}
