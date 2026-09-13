package ch.epfl.biop.registration.source.affine;

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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static bdv.ui.BdvDefaultCards.DEFAULT_SOURCEGROUPS_CARD;
import static bdv.ui.BdvDefaultCards.DEFAULT_SOURCES_CARD;
import static bdv.ui.BdvDefaultCards.DEFAULT_VIEWERMODES_CARD;

/**
 * Interactive edition of an in-plane affine transform: a 2D BigDataViewer window shows the fixed sources and the
 * moving sources transformed by the edited transform, which the user changes with an {@link AffineGizmo}.
 * The window blocks the caller until the user applies or cancels the edition, closing the window cancels it.
 */
public class AffineEditor {

    /** Width, in pixels, given to the card panel when the window opens */
    private static final int CARD_PANEL_WIDTH = 360;

    /** Distance of the axis handles from the gizmo center, as a fraction of the smallest side of the region */
    private static final double HANDLE_LENGTH = 0.25;

    /**
     * Opens the editor and blocks until the user applies or cancels. Do not call from the event dispatch thread.
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
        if (roi == null) roi = boundingBox(moving[0], timePoint);
        final AffineGizmo gizmo = new AffineGizmo(initial, roi[0] + roi[2] / 2.0, roi[1] + roi[3] / 2.0,
                HANDLE_LENGTH * Math.min(roi[2], roi[3]));

        final SourceAndConverter<?>[] movingDisplayed = new SourceAndConverter[moving.length];
        final BdvHandle[] bdvhHolder = new BdvHandle[1];
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean applied = new AtomicBoolean(false);

        try {
            EventQueue.invokeAndWait(() -> {
                final BdvHandle bdvh = createBdvHandle(title);
                bdvhHolder[0] = bdvh;

                final SourceBdvDisplayService displayService = SourceServices.getBdvDisplayService();
                displayService.registerBdvHandle(bdvh);
                for (int i = 0; i < moving.length; i++) {
                    movingDisplayed[i] = SourceTransformHelper.createNewTransformedSourceAndConverter(
                            initial.copy(), new SourceAndTimeRange<>(moving[i], timePoint));
                }
                displayService.show(bdvh, fixed);
                displayService.show(bdvh, movingDisplayed);
                new ViewerTransformAdjuster(bdvh, Stream.concat(Stream.of(fixed), Stream.of(movingDisplayed))
                        .toArray(SourceAndConverter[]::new)).run();
                setZToZero(bdvh);

                final Runnable update = () -> {
                    AffineTransform3D transform = gizmo.getTransform();
                    for (SourceAndConverter<?> source : movingDisplayed) {
                        ((TransformedSource<?>) source.getSpimSource()).setFixedTransform(transform);
                    }
                    bdvh.getViewerPanel().requestRepaint();
                };
                final AffineGizmoOverlay overlay = new AffineGizmoOverlay(bdvh, gizmo, update);
                BdvFunctions.showOverlay(overlay, "Affine gizmo", BdvOptions.options().addTo(bdvh));
                overlay.install();

                final JButton resetButton = new JButton("Reset");
                resetButton.addActionListener(e -> {
                    gizmo.reset();
                    update.run();
                });

                // Everything runs on the event dispatch thread: the first way the user ends the edition wins
                final Consumer<Boolean> finish = apply -> {
                    if (latch.getCount() == 0) return;
                    applied.set(apply);
                    latch.countDown();
                };
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

                BdvHandleHelper.addCard(bdvh, "Affine transformation",
                        box(new JLabel("<html><div style='width:" + (CARD_PANEL_WIDTH - 60) + "px'>" +
                                        "<b>Drag the handles to move the moving sources onto the fixed ones.</b><br><br>" +
                                        "<b>White</b>: translate.<br>" +
                                        "<b>Red</b> and <b>green</b>: set the x and y axes, which scales and shears.<br>" +
                                        "<b>Yellow</b>: rotate and scale both axes.<br><br>" +
                                        "Hold shift to constrain a drag: translate along x or y only, " +
                                        "keep the direction of an axis, rotate without scaling.<br><br>" +
                                        "<b>Reset</b> goes back to the transformation the edition started from.<br><br>" +
                                        NavigationHelp.html(bdvh) +
                                        "</div></html>"),
                                resetButton,
                                applyButton,
                                cancelButton),
                        true);
                CardHelper.expandCardPanel(bdvh, CARD_PANEL_WIDTH);
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

        return applied.get() ? gizmo.getTransform() : null;
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
    private static void close(BdvHandle bdvh, SourceAndConverter<?>[] movingDisplayed) {
        if (bdvh == null) return;
        SourceServices.getBdvDisplayService().closeBdv(bdvh);
        bdvh.close();
        for (SourceAndConverter<?> source : movingDisplayed) {
            if (source != null) SourceServices.getSourceService().remove(source);
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
