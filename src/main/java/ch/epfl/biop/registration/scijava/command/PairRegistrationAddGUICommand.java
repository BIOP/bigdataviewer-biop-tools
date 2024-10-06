package ch.epfl.biop.registration.scijava.command;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.BdvScijavaHelper;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Registration pair - Add GUI",
        description = "Delete a registration pair"  )
public class PairRegistrationAddGUICommand implements Command {

    @Parameter
    RegistrationPair registration_pair;

    @Parameter
    ObjectService objectService;

    @Parameter
    Context ctx;

    RegistrationPair.RegistrationPairListener listener;

    @Override
    public void run() {

        BdvStackSource<?> bdvStack = BdvFunctions.show(registration_pair.getFixedSources()[0], 1, BdvOptions.options().is2D());

        final BdvHandle bdvh = bdvStack.getBdvHandle();

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
        }

        listener = new RegistrationPair.RegistrationPairListener() {
            @Override
            public void newEvent(RegistrationPair.RegistrationEvents event) {
                switch (event) {
                    case STEP_REMOVED:
                        int nSteps = registration_pair.getAllSourcesPerStep().size();
                        SourceGroup group = bdvh.getViewerPanel().state().getGroups().get(nSteps);
                        bdvh.getViewerPanel().state().removeSources(bdvh.getViewerPanel().state().getSourcesInGroup(group));
                        updateBdvSourceGroups(bdvh);
                        break;
                    case STEP_ADDED:
                        updateBdvSourceGroups(bdvh);
                        break;
                    case CLOSED:
                        bdvh.close();
                        break;
                }
            }
        };

        registration_pair.addListener(listener);

        addConfirmationCloseHook(bdvh);

        int hierarchyLevelsSkipped = 3;
        BdvScijavaHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationCenterCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvScijavaHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationSift2DAffineCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvScijavaHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationElastix2DAffineCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvScijavaHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationElastix2DSplineCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvScijavaHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationBigWarp2DSplineCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvScijavaHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationExportToQuPathCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvScijavaHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationExportToOMETIFFCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvScijavaHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationEditLastRegistrationCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
        BdvScijavaHelper.addCommandToBdvHandleMenu(bdvh, ctx, PairRegistrationRemoveLastRegistrationCommand.class,
                hierarchyLevelsSkipped,"registration_pair", registration_pair);
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
                String message = "Are you sure you want to exit the registration GUI?";

                int confirmed = JOptionPane.showConfirmDialog(frame,
                        message, "Close window ?",
                        JOptionPane.YES_NO_OPTION);
                if (confirmed == JOptionPane.YES_OPTION) {

                    registration_pair.removeListener(listener);

                    closeAlreadyActivated = true;

                    int clearRegistration = JOptionPane.showConfirmDialog(frame,
                            "Keep registration pair in memory?", "Keep registration in memory.",
                            JOptionPane.YES_NO_OPTION);

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
