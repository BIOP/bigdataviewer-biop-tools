package ch.epfl.biop.command.register.warpy;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import ch.epfl.biop.registration.RegistrationPair;
import ch.epfl.biop.viewer.bdv.BusyOverlay;
import ch.epfl.biop.viewer.bdv.card.CardHelper;
import ch.epfl.biop.viewer.bdv.card.NavigationHelp;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvMenuHelper;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static bdv.ui.BdvDefaultCards.DEFAULT_SOURCEGROUPS_CARD;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Register>Warpy>Register Pair - Add GUI",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.RegisterMenu, weight = BdvPgMenus.RegisterW),
                @Menu(label = "Warpy", weight = -2),
                @Menu(label = "Register Pair - Add GUI", weight = 2)
        },
        description = "Opens a BigDataViewer window with  controls for performing registrations")
public class PairRegistrationAddGUICommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Registration Pair",
            description = "The registration pair to visualize and control")
    RegistrationPair registration_pair;

    @Parameter
    ObjectService objectService;

    @Parameter
    Context ctx;

    RegistrationPair.RegistrationPairListener listener;

    /** Animated banner shown while a registration runs in a background thread */
    BusyOverlay busyOverlay;

    /** Ticks on the EDT to animate {@link PairRegistrationAddGUICommand#busyOverlay} */
    Timer busyRepaintTimer;

    /** Window title without the busy prefix */
    String baseWindowTitle;

    @Override
    public void run() {

        BdvStackSource<?> bdvStack = BdvFunctions.show(registration_pair.getFixedSources()[0], 1, BdvOptions.options().is2D());

        final BdvHandle bdvh = bdvStack.getBdvHandle();

        baseWindowTitle = "Warpy Registration: "+registration_pair.getName();

        synchronized (registration_pair) {


            for (SourceAndConverter<?> source : registration_pair.getFixedSources()) {
                BdvFunctions.show(source, 1, BdvOptions.options().addTo(bdvh));
            }
            for (SourceAndConverter<?> source : registration_pair.getMovingSourcesOrigin()) {
                BdvFunctions.show(source, 1, BdvOptions.options().addTo(bdvh));
            }

            SourceGroup fixedGroup = bdvh.getViewerPanel().state().getGroups().get(0);
            bdvh.getViewerPanel().state().setGroupName(fixedGroup, "Fixed sources");
            bdvh.getViewerPanel().state().setGroupActive(fixedGroup,true);
            bdvh.getViewerPanel().state()
                    .addSourcesToGroup(Arrays.asList(registration_pair.getFixedSources()), fixedGroup);

            SourceGroup movingOriginGroup = bdvh.getViewerPanel().state().getGroups().get(1);

            bdvh.getViewerPanel().state().setGroupActive(movingOriginGroup,false);
            bdvh.getViewerPanel().state().setGroupName(movingOriginGroup, "Moving sources - origin");
            bdvh.getViewerPanel().state()
                    .addSourcesToGroup(Arrays.asList(registration_pair.getMovingSourcesOrigin()), movingOriginGroup);

            updateBdvSourceGroups(bdvh);

            bdvh.getViewerPanel().setDisplayMode(DisplayMode.FUSEDGROUP);
            new ViewerTransformAdjuster(bdvh, new SourceAndConverter[]{registration_pair.getFixedSources()[0], registration_pair.getMovingSourcesRegistered()[0]}).run();

            // Set Z to zero anyway
            AffineTransform3D transform3D = new AffineTransform3D();
            bdvh.getViewerPanel().state().getViewerTransform(transform3D);
            AffineTransform3D recenter = transform3D.copy();
            recenter.set(0,2,3);
            bdvh.getViewerPanel().state().setViewerTransform(recenter);
        }

        installBusyFeedback(bdvh);

        listener = new RegistrationPair.RegistrationPairListener() {
            @Override
            public void newEvent(RegistrationPair.RegistrationEvents event) {
                switch (event) {
                    case BUSY_CHANGED:
                        updateBusyFeedback(bdvh);
                        break;
                    case STEP_REMOVED:
                        int nSteps = registration_pair.getAllSourcesPerStep().size();
                        SourceGroup group = bdvh.getViewerPanel().state().getGroups().get(nSteps+2);
                        Set<SourceAndConverter<?>> sources = bdvh.getViewerPanel().state().getSourcesInGroup(group);
                        bdvh.getViewerPanel().state().removeSources(sources);
                        updateBdvSourceGroups(bdvh);
                        break;
                    case STEP_ADDED:
                        updateBdvSourceGroups(bdvh);
                        break;
                    case CLOSE:
                        uninstallBusyFeedback(bdvh);
                        bdvh.close();
                        break;
                }
            }
        };

        registration_pair.addListener(listener);

        addConfirmationCloseHook(bdvh);

        int hierarchyLevelsSkipped = 3;
        BdvMenuHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationCenterCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvMenuHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationRotateCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvMenuHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationFlipCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvMenuHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationManualAffineCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvMenuHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationSift2DAffineRegisterCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvMenuHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationElastix2DAffineRegisterCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvMenuHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationElastix2DSplineRegisterCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvMenuHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationBigWarp2DSplineRegisterCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvMenuHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationQuPathExportCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvMenuHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationOMETIFFExportCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvMenuHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationLastRegistrationEditCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvMenuHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationLastRegistrationRemoveCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);

        addHelpCard(bdvh);

        BdvHandleHelper.setWindowTitle(bdvh, baseWindowTitle);
    }

    /**
     * Adds a card telling how to navigate the window and what the source groups hold.
     * <p>
     * Users of the Warpy workflow are not necessarily users of BigDataViewer, so neither the
     * mouse gestures nor the meaning of the groups can be taken for granted. The navigation
     * gestures are read from the keymap of the window rather than hardcoded, see
     * {@link NavigationHelp}.
     * <p>
     * The card panel is expanded, otherwise the card - and the groups it describes - would stay
     * behind the collapsed side panel.
     *
     * @param bdvh the registration window to document
     */
    private void addHelpCard(BdvHandle bdvh) {

        JLabel help = new JLabel("<html><div style='width:" + (CARD_PANEL_WIDTH - 60) + "px'>" +
                "<b>Navigating</b><br>" +
                NavigationHelp.html(bdvh) + "<br><br>" +
                "<b>Groups</b><br>" +
                "The groups hold the successive stages of the registration: " +
                "<i>Fixed sources</i> is the target, <i>Moving sources - origin</i> the moving " +
                "image before any registration, and each <i>+ step</i> group the moving image " +
                "as it is once that step has been applied.<br><br>" +
                "Every group which is active is blended into the view, so activating and " +
                "deactivating them is how the outcome of a step is compared with the fixed " +
                "image, or with the steps which came before it.<br><br>" +
                "<b>Registering</b><br>" +
                "The registration steps themselves are in the <b>Warpy</b> menu of this window. " +
                "They apply to the last stage, so they accumulate: a spline registration " +
                "refines what the affine steps before it have already aligned." +
                "</div></html>");
        help.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(help);

        BdvHandleHelper.addCard(bdvh, "Help", panel, true);
        // The groups are half of what the card explains, so they are worth showing straight away
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCEGROUPS_CARD, true);
        CardHelper.expandCardPanel(bdvh, CARD_PANEL_WIDTH);
    }

    /** Width, in pixels, given to the card panel when the registration window opens */
    private static final int CARD_PANEL_WIDTH = 340;

    /**
     * Adds the busy banner to the viewer canvas, together with the swing timer which animates it.
     * The overlay is added to the canvas overlay list rather than shown with
     * {@code BdvFunctions.showOverlay}: the latter would add a source to the viewer state and
     * break the group / source index arithmetic of
     * {@link PairRegistrationAddGUICommand#updateBdvSourceGroups(BdvHandle)}.
     *
     * @param bdvh the viewer to decorate
     */
    private void installBusyFeedback(BdvHandle bdvh) {
        busyOverlay = new BusyOverlay();
        bdvh.getViewerPanel().getDisplay().overlays().add(busyOverlay);

        // Repaints the canvas only: the overlays are re-composited over the image which has
        // already been rendered, the sources are not rendered again.
        busyRepaintTimer = new Timer(200, e -> bdvh.getViewerPanel().getDisplay().repaint());
        busyRepaintTimer.setRepeats(true);
    }

    /**
     * Reflects the busy state of the registration pair in the viewer. Called from the thread
     * which runs the registration, hence the swing calls being deferred to the EDT.
     *
     * @param bdvh the viewer to update
     */
    private void updateBusyFeedback(BdvHandle bdvh) {
        final RegistrationPair pair = registration_pair;
        final BusyOverlay overlay = busyOverlay;
        if ((pair == null) || (overlay == null)) return;

        final int nRunning = pair.getRunningRegistrationCount();
        final boolean busy = nRunning > 0;

        overlay.setBusy(busy, nRunning > 1
                ? "Registration in progress (" + nRunning + " queued)"
                : "Registration in progress");

        final Timer timer = busyRepaintTimer;
        final String title = baseWindowTitle;

        SwingUtilities.invokeLater(() -> {
            if (timer != null) {
                if (busy) {
                    if (!timer.isRunning()) timer.start();
                } else {
                    timer.stop();
                }
            }
            bdvh.getViewerPanel().getDisplay().repaint();
            if (title != null) {
                BdvHandleHelper.setWindowTitle(bdvh, busy ? "[Registering] " + title : title);
            }
        });
    }

    /**
     * Stops the animation and removes the busy banner. Safe to call several times.
     *
     * @param bdvh the viewer to clean up
     */
    private void uninstallBusyFeedback(BdvHandle bdvh) {
        final Timer timer = busyRepaintTimer;
        if (timer != null) {
            busyRepaintTimer = null;
            SwingUtilities.invokeLater(timer::stop);
        }
        final BusyOverlay overlay = busyOverlay;
        if (overlay != null) {
            busyOverlay = null;
            overlay.setBusy(false, null);
            try {
                bdvh.getViewerPanel().getDisplay().overlays().remove(overlay);
            } catch (Exception e) {
                // The viewer may already be disposed - nothing worth reporting
            }
        }
    }

    private void updateBdvSourceGroups(BdvHandle bdvh) {
        for (int g = 2; g<bdvh.getViewerPanel().state().getGroups().size(); g++) {
            bdvh.getViewerPanel().state().setGroupName(bdvh.getViewerPanel().state().getGroups().get(g), "+ "+registration_pair.getRegistrationName(g-2));
        }
        List<SourceAndConverter<?>[]> sourcesPerStep = registration_pair.getAllSourcesPerStep();
        for (int step = 0; step < sourcesPerStep.size(); step++) {
            SourceGroup group = bdvh.getViewerPanel().state().getGroups().get(step+2);
            bdvh.getViewerPanel().state().removeSources(bdvh.getViewerPanel().state().getSourcesInGroup(group));

            bdvh.getViewerPanel().state().setGroupActive(group,false);
            List<SourceAndConverter<?>> sources = Arrays.asList(sourcesPerStep.get(step));

            for (SourceAndConverter<?> source : sources) {
                BdvFunctions.show(source, 1, BdvOptions.options().addTo(bdvh));
            }
            bdvh.getViewerPanel().state()
                    .addSourcesToGroup(sources, group);
        }

        for (SourceAndConverter<?> source : registration_pair.getFixedSources()) {
            BdvFunctions.show(source, 1, BdvOptions.options().addTo(bdvh));
        }
        for (SourceAndConverter<?> source : registration_pair.getMovingSourcesOrigin()) {
            BdvFunctions.show(source, 1, BdvOptions.options().addTo(bdvh));
        }

        SourceGroup fixedGroup = bdvh.getViewerPanel().state().getGroups().get(0);
        bdvh.getViewerPanel().state()
                .addSourcesToGroup(Arrays.asList(registration_pair.getFixedSources()), fixedGroup);

        SourceGroup movingOriginGroup = bdvh.getViewerPanel().state().getGroups().get(1);
        bdvh.getViewerPanel().state()
                .addSourcesToGroup(Arrays.asList(registration_pair.getMovingSourcesOrigin()), movingOriginGroup);

        if (sourcesPerStep.isEmpty()) {
            bdvh.getViewerPanel().state().setGroupActive(bdvh.getViewerPanel().state().getGroups().get(1), true);
        } else {
            bdvh.getViewerPanel().state().setGroupActive(bdvh.getViewerPanel().state().getGroups().get(1), false);
            bdvh.getViewerPanel().state().setGroupActive(
                    bdvh.getViewerPanel().state().getGroups().get(sourcesPerStep.size()+1),
                    true);
        }
    }

    boolean closeAlreadyActivated = false;

    private void addConfirmationCloseHook(final BdvHandle bdvh) {
        JFrame frame = BdvHandleHelper.getJFrame(bdvh);
        WindowListener[] listeners = frame.getWindowListeners();

        for (WindowListener listener:listeners) {
            frame.removeWindowListener(listener);
        }

        frame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
            if (!closeAlreadyActivated) {
                int confirmed = JOptionPane.YES_OPTION;

                if (!registration_pair.getForceClose()) {
                    String message = "Are you sure you want to exit the registration GUI?";

                    confirmed = JOptionPane.showConfirmDialog(frame,
                            message, "Close window ?",
                            JOptionPane.YES_NO_OPTION);
                }

                if (confirmed == JOptionPane.YES_OPTION) {

                    registration_pair.removeListener(listener);
                    uninstallBusyFeedback(bdvh);

                    closeAlreadyActivated = true;

                    int clearRegistration = JOptionPane.YES_OPTION;

                    if (!registration_pair.getForceClose()) {
                        clearRegistration = JOptionPane.showConfirmDialog(frame,
                                "Keep registration pair in memory?", "Keep registration in memory.",
                                JOptionPane.YES_NO_OPTION);
                    }

                    if (clearRegistration == JOptionPane.NO_OPTION) {
                        try {
                            registration_pair.close();
                            objectService.removeObject(registration_pair);
                            registration_pair = null;
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                    for (WindowListener listener : listeners) {
                        listener.windowClosing(e);
                    }
                    PairRegistrationAddGUICommand.this.registration_pair = null;

                } else {
                    frame.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
                }
            }
            }
        });
    }
}
