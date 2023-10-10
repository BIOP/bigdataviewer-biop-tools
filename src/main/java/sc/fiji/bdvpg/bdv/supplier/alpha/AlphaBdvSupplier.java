package sc.fiji.bdvpg.bdv.supplier.alpha;

import bdv.ui.CardPanel;
import bdv.util.*;
import bdv.util.projector.alpha.ILayerAlphaProjectorFactory;
import bdv.util.projector.alpha.Layer;
import bdv.util.projector.alpha.LayerAlphaIProjectorFactory;
import bdv.util.projector.alpha.LayerAlphaProjectorFactory;
import bdv.util.projector.alpha.LayerMetadata;
import bdv.util.projector.alpha.SourcesMetadata;
import bdv.util.source.alpha.AlphaSourceHelper;
import bdv.util.source.alpha.IAlphaSource;
import bdv.viewer.*;
import bdv.viewer.render.AccumulateProjectorARGB;
import bdv.viewer.render.AccumulateProjectorFactory;
import ch.epfl.biop.bdv.select.SourceSelectorBehaviour;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.real.FloatType;
import org.jetbrains.annotations.NotNull;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.bdv.navigate.RayCastPositionerSliderAdder;
import sc.fiji.bdvpg.bdv.navigate.SourceNavigatorSliderAdder;
import sc.fiji.bdvpg.bdv.navigate.TimepointAdapterAdder;
import sc.fiji.bdvpg.bdv.overlay.SourceNameOverlayAdder;
import sc.fiji.bdvpg.bdv.supplier.BdvSupplierHelper;
import sc.fiji.bdvpg.bdv.supplier.IBdvSupplier;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import javax.swing.*;
import java.awt.Font;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static bdv.util.source.alpha.AlphaSourceHelper.ALPHA_SOURCE_KEY;

/**
 * Supplies BigDataViewer windows with alpha composition support
 *
 * This class is serializable when using Gson's object from
 * {@link sc.fiji.persist.ScijavaGsonHelper}. And it can thus be used as the default bdv provider
 * of bigdataviewer-playground's commands.
 *
 * Alpha source are computed on demand and should not be an issue to deal with.
 *
 * Bdv Groups are used as layers. Limitation : you can remove groups, but this will cause issues.
 *
 * Other weird behaviour : a source can be in multiple groups, but it can be only in one layer (the
 * higher one).
 *
 * @author Nicolas Chiaruttini, EPFL, 2021
 */

public class AlphaBdvSupplier implements IBdvSupplier {

    public final AlphaSerializableBdvOptions sOptions;

    public AlphaBdvSupplier(AlphaSerializableBdvOptions sOptions) {
        this.sOptions = sOptions;
    }

    /**
     * Sources Metadata (how to retrieve and when to create alpha sources) backed by a
     * {@link sc.fiji.bdvpg.scijava.services.SourceAndConverterService}
     */
    final public static SourcesMetadata sourcesMetadata = new SourcesMetadata() {
        @Override
        public boolean isAlphaSource(SourceAndConverter<?> sac) {
            return sac.getSpimSource() instanceof IAlphaSource;
        }

        @Override
        public boolean hasAlphaSource(SourceAndConverter<?> sac) {
            return SourceAndConverterServices.getSourceAndConverterService().containsMetadata(sac, ALPHA_SOURCE_KEY);
        }

        @Override
        public SourceAndConverter<FloatType> getAlphaSource(SourceAndConverter<?> sac) {
            return (SourceAndConverter<FloatType>) SourceAndConverterServices.getSourceAndConverterService().getMetadata(sac, ALPHA_SOURCE_KEY);
        }
    };

    @Override
    public BdvHandle get() {
        AccumulateProjectorFactory<ARGBType> projectorFactory = AccumulateProjectorARGB.factory;

        if (sOptions.useAlphaCompositing) {
            if (sOptions.white_bg) {
                projectorFactory = new LayerAlphaIProjectorFactory();
                ((ILayerAlphaProjectorFactory)projectorFactory).setSourcesMeta(sourcesMetadata);
            } else {
                projectorFactory = new LayerAlphaProjectorFactory();
                ((ILayerAlphaProjectorFactory)projectorFactory).setSourcesMeta(sourcesMetadata);
            }
            /*projectorFactory = new LayerAlphaProjectorFactory();
            ((ILayerAlphaProjectorFactory)projectorFactory).setSourcesMeta(sourcesMetadata);*/
        }

        BdvOptions options = sOptions.getBdvOptions();

        // create dummy image to instantiate the BDV
        ArrayImg<ByteType, ByteArray> dummyImg = ArrayImgs.bytes(2, 2, 2);
        options = options.sourceTransform( new AffineTransform3D() );
        options.accumulateProjectorFactory(projectorFactory);
        BdvStackSource<ByteType> bss = BdvFunctions.show( dummyImg, "dummy", options );
        BdvHandle bdvh = bss.getBdvHandle();

        if ( sOptions.interpolate ) bdvh.getViewerPanel().setInterpolation( Interpolation.NLINEAR );

        // remove dummy image
        bdvh.getViewerPanel().state().removeSource(bdvh.getViewerPanel().state().getCurrentSource());
        bdvh.getViewerPanel().setNumTimepoints( sOptions.numTimePoints );

        if (sOptions.useAlphaCompositing) {
            bdvh.getViewerPanel().state().changeListeners().add(new AlphaSourcesSynchronizer(bdvh));
            ((ILayerAlphaProjectorFactory) projectorFactory).setLayerMeta(new GroupLayerMetadata(bdvh.getViewerPanel(), bdvh.getCardPanel()));
        }

        BdvSupplierHelper.addSourcesDragAndDrop(bdvh);
        SourceSelectorBehaviour ssb = BdvSupplierHelper.addEditorMode(bdvh, "");


        bdvh.getSplitPanel().setCollapsed(false);

        JPanel editorModeToggle = new JPanel();
        JButton editorToggle = new JButton("Editor Mode");
        editorToggle.addActionListener((e) -> {
            if (ssb.isEnabled()) {
                ssb.disable();
                editorToggle.setText("Editor Mode 'E'");
            }
            else {
                ssb.enable();
                editorToggle.setText("Navigation Mode 'E'");
            }
        });

        editorModeToggle.add(editorToggle);

        JButton nameToggle = new JButton("Display sources name");
        AtomicBoolean nameOverlayEnabled = new AtomicBoolean();
        nameOverlayEnabled.set(true);

        SourceNameOverlayAdder nameOverlayAdder = new SourceNameOverlayAdder(bdvh, new Font(sOptions.font, Font.PLAIN, sOptions.fontSize));

        nameToggle.addActionListener((e) -> {
            if (nameOverlayEnabled.get()) {
                nameOverlayEnabled.set(false);
                nameToggle.setText("Display sources names");
                nameOverlayAdder.removeFromBdv();

            }
            else {
                nameOverlayEnabled.set(true);
                nameToggle.setText("Hide sources name");
                nameOverlayAdder.addToBdv();
            }
        });

        editorModeToggle.add(nameToggle);

        SwingUtilities.invokeLater(() -> {
            nameOverlayAdder.run();
            BdvHandleHelper.addCenterCross(bdvh);
            new RayCastPositionerSliderAdder(bdvh).run();
            new SourceNavigatorSliderAdder(bdvh).run();
            new TimepointAdapterAdder(bdvh).run();
        });
        //SwingUtilities.invokeLater(() -> BdvHandleHelper.addCenterCross(bdvh));
        //SwingUtilities.invokeLater(() -> new RayCastPositionerSliderAdder(bdvh).run());
        BdvHandleHelper.addCard(bdvh, "Mode", editorModeToggle, true);

        return bdvh;
    }

    public static class GroupLayerMetadata implements LayerMetadata, ViewerStateChangeListener {

        final ViewerPanel viewer;

        final Map<SourceAndConverter<?>, SourceGroup> sourceToGroup = new ConcurrentHashMap<>();
        final Map<SourceGroup, SourceGroupLayer> groupToLayer = new ConcurrentHashMap<>();

        final JPanel sliders;

        final JLabel[] sliderLabels;

        public GroupLayerMetadata(ViewerPanel viewer, CardPanel panel) {
            this.viewer = viewer;
            defaultLayer = new SourceGroupLayer(viewer, new SourceGroup(), -1, "Default");
            sliders = new JPanel();
            sliders.setLayout(new BoxLayout(sliders, BoxLayout.Y_AXIS));

            sliderLabels = new JLabel[viewer.state().getGroups().size()+1];


            sliders.add(defaultLayer.getSlider());
            buildLayers();
            buildMap();
            viewer.state().changeListeners().add(this);
            SwingUtilities.invokeLater(() -> panel.addCard("Group Opacity", sliders, true));
        }

        public void buildLayers() {
            List<SourceGroup> groups = viewer.state().getGroups();
            for (int i=0;i<groups.size();i++) {
                SourceGroup group = groups.get(i);
                if (!groupToLayer.containsKey(group)) {
                    SourceGroupLayer sgl = new SourceGroupLayer(viewer, group, i, viewer.state().getGroupName(group));
                    groupToLayer.put(group, sgl);
                    sliders.add(sgl.getSlider());
                }
            }
        }

        public void buildMap() {
            // Issue : one source can belong to multiple groups, but only to one layer TODO
            sourceToGroup.clear();
            viewer.state().getGroups().forEach(group -> viewer.state().getSourcesInGroup(group).forEach(sac -> sourceToGroup.put(sac, group)));
        }

        final SourceGroupLayer defaultLayer;

        @Override
        public Layer getLayer(SourceAndConverter<?> sac) {
            if (sourceToGroup.containsKey(sac)) {
                return groupToLayer.get(sourceToGroup.get(sac));
            } else {
                return defaultLayer;
            }
        }

        @Override
        public void viewerStateChanged(ViewerStateChange change) {
            switch (change) {
                case CURRENT_GROUP_CHANGED:
                    break;
                case GROUP_ACTIVITY_CHANGED:
                    synchronized (viewer.state()) {
                        groupToLayer.values().forEach(sgl -> sgl.setActive(viewer.state().isGroupActive(sgl.getGroup())));
                    }
                    viewer.requestRepaint();
                    break;
                case GROUP_NAME_CHANGED:
                    synchronized (viewer.state()) {
                        groupToLayer.values().forEach(SourceGroupLayer::nameChanged);
                    }
                    break;
                case SOURCE_TO_GROUP_ASSIGNMENT_CHANGED:
                    buildMap();
                    viewer.requestRepaint();
                    break;
                case NUM_GROUPS_CHANGED:
                    System.err.println("Changing the number of groups / layers is not supported!");
                    break;
            }
        }
    }

    public static class SourceGroupLayer implements Layer {

        final ViewerPanel viewer;
        final SourceGroup group;
        float alpha = 1;
        final JLabel label;
        final JPanel sliderPanel;
        final int id;
        String name;

        public SourceGroupLayer(ViewerPanel viewer, SourceGroup group, int id, String name) {
            this.name = name;
            this.viewer = viewer;
            this.id = id;
            this.group = group;
            JSlider slider = new JSlider(JSlider.HORIZONTAL, 0, 255, 255);
            slider.addChangeListener(e -> setAlpha((float) (slider.getValue()/255.0)));
            label = new JLabel(name);
            sliderPanel = new JPanel();
            sliderPanel.add(label);
            sliderPanel.add(slider);
        }

        public SourceGroup getGroup() {
            return group;
        }

        public JComponent getSlider() {
            return sliderPanel;
        }

        @Override
        public float getAlpha() {
            return alpha;
        }

        public void setAlpha(float alpha) {
            if (alpha!=this.alpha) {
                this.alpha = alpha;
                viewer.requestRepaint();
            }
        }

        public void nameChanged() {
            label.setText(viewer.state().getGroupName(group));
        }

        @Override
        public int getBlendingMode() {
            return 0;
        }

        boolean active = true;

        public void setActive(boolean active) {
            this.active = active;
        }

        @Override
        public boolean skip() {
            return !active; // The sources are not part of the projector anyway //active; //!viewer.state().isGroupActive(group); // Thread deadlock!
        }

        @Override
        public int compareTo(@NotNull Layer o) {
            return Integer.compare(id, ((SourceGroupLayer)o).id);
            //viewer.state().groupOrder().compare(group, ((SourceGroupLayer)o).getGroup()); DO NOT USE! CAUSES THREADS DEADLOCK!
        }
    }

    public static class AlphaSourcesSynchronizer implements ViewerStateChangeListener {

        final BdvHandle bdvh;

        public AlphaSourcesSynchronizer(BdvHandle bdvh) {
            this.bdvh = bdvh;
        }

        @Override
        public void viewerStateChanged(ViewerStateChange change) {
            switch (change) {
                case NUM_SOURCES_CHANGED:
                    syncAlphaSourcesPresent();
                    break;
                case VISIBILITY_CHANGED:
                    syncAlphaSourcesVisibility();
                    break;
            }
        }

        /**
         * When sources are visible, alpha sources, if present, should be visible as well
         */
        private void syncAlphaSourcesVisibility() {
            ViewerState state = bdvh.getViewerPanel().state();

            Set<SourceAndConverter<?>> alphaSourcesToActivate =
                    state.getActiveSources().stream()
                    .filter(sac -> !sourcesMetadata.isAlphaSource(sac))
                    .filter(sourcesMetadata::hasAlphaSource)
                    .map(sourcesMetadata::getAlphaSource)
                    .collect(Collectors.toSet());

            state.setSourcesActive(alphaSourcesToActivate, true);

            Set<SourceAndConverter<?>> alphaSourcesToDeActivate =
                    state.getSources().stream()
                            .filter(sac -> !sourcesMetadata.isAlphaSource(sac))
                            .filter(sac -> !state.isSourceVisible(sac))
                            .filter(sourcesMetadata::hasAlphaSource)
                            .map(sourcesMetadata::getAlphaSource)
                            .collect(Collectors.toSet());

            state.setSourcesActive(alphaSourcesToDeActivate, false);

        }

        /**
         * This listener takes care of fetching or creating alpha sources
         * linked to the sources already present in the bdvh
         */
        private void syncAlphaSourcesPresent() {

            List<SourceAndConverter<?>> currentSources = bdvh.getViewerPanel().state().getSources();

            Set<SourceAndConverter<?>> alphaSourcesToAdd = new HashSet<>();
            Set<SourceAndConverter<?>> usefulAlphaSources = new HashSet<>();
            Set<SourceAndConverter<?>> currentAlphaSources =
                    currentSources.stream().filter(sourcesMetadata::isAlphaSource).collect(Collectors.toSet());

            for (SourceAndConverter<?> sac : currentSources) {
                // Let's investigate the source:
                // Ignore if it is an alpha source
                if (!sourcesMetadata.isAlphaSource(sac)) {
                    // Is there an alpha source linked ?
                    if (sourcesMetadata.hasAlphaSource(sac)) {
                        // Is it in the list of all sources ?
                        SourceAndConverter<?> alpha = sourcesMetadata.getAlphaSource(sac);
                        if (!currentSources.contains(alpha)) {
                            // Not in the list : it should be added
                            alphaSourcesToAdd.add(alpha);
                        } else {
                            usefulAlphaSources.add(alpha); // Flags that it shouldn't be removed
                        }
                    } else {
                        // No alpha source, let's try to build it
                        if (!(sac.getSpimSource() instanceof PlaceHolderSource)) {
                            System.out.println("Building alpha source for source " + sac.getSpimSource().getName() + " of class " + sac.getSpimSource().getClass().getSimpleName());
                            SourceAndConverter<FloatType> alpha = AlphaSourceHelper.getOrBuildAlphaSource(sac);
                            alphaSourcesToAdd.add(alpha);
                        }
                    }
                }
            }

            Set<SourceAndConverter<?>> alphaSourcesToRemove = currentAlphaSources.stream().filter(sac -> !usefulAlphaSources.contains(sac)).collect(Collectors.toSet());
            bdvh.getViewerPanel().state().removeSources(alphaSourcesToRemove);

            bdvh.getViewerPanel().state().addSources(alphaSourcesToAdd);

        }
    }


}