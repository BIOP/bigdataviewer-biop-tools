package ch.epfl.biop.command.view;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.opener.OpenerHelper;
import ch.epfl.biop.command.io.exporter.ExportToMultipleImagePlusCommand;
import ch.epfl.biop.bdv.select.SourceSelectorBehaviour;
import ch.epfl.biop.bdv.select.ToggleListener;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;
import org.scijava.Context;
import org.scijava.cache.CacheService;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import sc.fiji.bdvpg.command.view.display.SourceBrightnessAdjustInteractiveCommand;
import sc.fiji.bdvpg.viewers.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.viewers.behaviour.EditorBehaviourUnInstaller;
import sc.fiji.bdvpg.viewers.behaviour.SourceContextMenuClickBehaviour;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceService;
import sc.fiji.bdvpg.services.SourceServices;
import sc.fiji.bdvpg.source.SourceHelper;
import sc.fiji.bdvpg.source.transform.SourceAffineTransformer;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static sc.fiji.bdvpg.scijava.services.SourceService.getCommandName;
import static sc.fiji.bdvpg.services.ISourceService.SPIM_DATA_INFO;
import static sc.fiji.bdvpg.viewers.ViewerOrthoSyncStarter.MatrixApproxEquals;

/**
 * Command which display sources on a grid in BigDataViewer
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"View>BDV>BDV - Display Sources On Grid",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.DisplayMenu, weight = BdvPgMenus.DisplayW),
                @Menu(label = BdvPgMenus.BDVMenu, weight = BdvPgMenus.BDVW),
                @Menu(label = "BDV - Show Sources On Grid", weight = 3.1)
        },
        description = "Displays sources arranged on a grid in a new BigDataViewer window")
public class OverviewerCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Source(s)",
            description = "The sources to display on the grid")
    public SourceAndConverter<?>[] sources;

    @Parameter(label = "Start Timepoint",
            description = "The timepoint to use for determining source dimensions")
    int timepoint_begin;

    @Parameter(label = "Number of Columns",
            description = "Number of columns in the grid layout")
    int n_columns;

    @Parameter(label = "Split by Entities",
            description = "Comma-separated entity types to split by (e.g., 'channel, fileseries')")
    String entities_split = "";

    Map<String, Class<? extends Entity>> entityClasses = OpenerHelper.getEntities();

    // sourceList;

    int currentIndex = 0;
    AffineTransform3D currentAffineTransform = new AffineTransform3D();

    Map<SourceAndConverter<?>, SourceAndConverter<?>> transformedToOriginal = new HashMap<>();

    @Parameter
    Context ctx;

    @Override
    public void run() {


        List<Class<? extends Entity>> entSplit = new ArrayList<>();

        for (String entity : entities_split.split(",")) {
            String ent = entity.trim().toUpperCase();
            if (!entityClasses.containsKey(ent)){
                System.err.println("Unrecognized entity class "+ent);
            } else {
                System.out.println("Splitting by "+ent);
                entSplit.add(entityClasses.get(ent));
            }
        }

        // Sort according to location = affine transform 3d of sources

        List<SourceAndConverter<?>> sourceList = sorter.apply(Arrays.asList(sources));

        Map<SacProperties, List<SourceAndConverter<?>>> sourceClasses = sourceList
                .stream()
                .collect(Collectors.groupingBy(source -> {
                    SacProperties props = new SacProperties(source);
                    for (Class<? extends Entity> entityClass : entSplit) {
                        props.splitByEntity(entityClass);
                    }
                    return props; //new SacProperties(source)
                }));

        Map<SourceAndConverter<?>, List<SacProperties>> keySetSource = sourceClasses.keySet().stream().collect(Collectors.groupingBy(p -> p.source));

        List<SourceAndConverter<?>> sortedSources = sorter.apply(keySetSource.keySet());

        List<SourceAndConverter<?>> sourcesToDisplay = new ArrayList<>();

        sortedSources.forEach(sourceKey -> {
            SacProperties sourcePropsKey = keySetSource.get(sourceKey).get(0);
            for (Class<? extends Entity> entityClass : entSplit) {
                sourcePropsKey.splitByEntity(entityClass);
            }

            AffineTransform3D location = sourcePropsKey.location;

            int xPos = currentIndex % n_columns;
            int yPos = currentIndex / n_columns;

            currentAffineTransform.identity();
            currentAffineTransform.preConcatenate(location.inverse());
            AffineTransform3D translator = new AffineTransform3D();
            translator.translate(xPos, yPos,0);

            currentIndex++;

            List<SourceAndConverter<?>> sources = sorter.apply(sourceClasses.get(sourcePropsKey));

            int tp = timepoint_begin;

            long nPixX = 1, nPixY = 1, nPixZ = 1;

            if (!sources.get(0).getSpimSource().isPresent(tp)) {
                if (SourceHelper.hasAValidTimepoint(sources.get(0).getSpimSource())) {
                    tp = SourceHelper.getAValidTimepoint(sources.get(0).getSpimSource());
                }
            }

            if (sources.get(0).getSpimSource().isPresent(tp)) {
                nPixX = sources.get(0).getSpimSource().getSource(tp, 0).dimension(0);

                nPixY = sources.get(0).getSpimSource().getSource(tp, 0).dimension(1);

                nPixZ = sources.get(0).getSpimSource().getSource(tp, 0).dimension(2);
            }

            long sizeMax = Math.max(nPixX, nPixY);

            sizeMax = Math.max(sizeMax, nPixZ);

            currentAffineTransform.scale(1/(double)sizeMax, 1/(double) sizeMax, 1/(double)sizeMax);

            currentAffineTransform.translate(xPos,yPos,0);

            SourceAffineTransformer sat = new SourceAffineTransformer(null, currentAffineTransform);

            List<SourceAndConverter<?>> transformedSacs =
                    sources.stream().map(source -> {
                        SourceAndConverter<?> trSac = sat.apply(source);
                        transformedToOriginal.put(trSac, source);
                        SourceServices
                                .getSourceService()
                                .register(trSac);

                        ConverterSetup csOrigin = SourceServices
                                .getSourceService()
                                .getConverterSetup(source);

                        ConverterSetup csDestination = SourceServices
                                .getSourceService()
                                .getConverterSetup(trSac);

                        // TODO : fix potential mem leak with listeners
                        csOrigin.setupChangeListeners().add(setup -> {
                                if ((csDestination.getDisplayRangeMin() != setup.getDisplayRangeMin()) ||
                                        (csDestination.getDisplayRangeMax() != setup.getDisplayRangeMax()))
                                    csDestination.setDisplayRange(setup.getDisplayRangeMin(), setup.getDisplayRangeMax());
                                if (csDestination.supportsColor()) {
                                    if (csDestination.getColor().get() != setup.getColor().get())
                                        csDestination.setColor(new ARGBType(setup.getColor().get()));

                                }
                            }
                        );

                        csDestination.setupChangeListeners().add(setup -> {
                                    if ((csOrigin.getDisplayRangeMin() != setup.getDisplayRangeMin()) ||
                                            (csOrigin.getDisplayRangeMax() != setup.getDisplayRangeMax()))
                                        csOrigin.setDisplayRange(csOrigin.getDisplayRangeMin(), csOrigin.getDisplayRangeMax());
                                    if (csOrigin.supportsColor()) {
                                        if (csOrigin.getColor().get() != setup.getColor().get())
                                            csOrigin.setColor(new ARGBType(setup.getColor().get()));
                                    }
                                }
                        );

                        return trSac;
                    }).collect(Collectors.toList());

            sourcesToDisplay.addAll(transformedSacs);
        });

        //BdvHandle bdvh =

        BdvHandle bdvh = SourceServices.getBdvDisplayService().getNewBdv();
        SourceServices.getBdvDisplayService().show(bdvh, sourcesToDisplay.toArray(new SourceAndConverter[0]));

        AffineTransform3D currentViewLocation = new AffineTransform3D();

        bdvh.getViewerPanel().state().getViewerTransform(currentViewLocation);
        currentViewLocation.set(0,2,3);
        bdvh.getViewerPanel().state().setViewerTransform(currentViewLocation);

        SourceSelectorBehaviour ssb = (SourceSelectorBehaviour) SourceServices.getBdvDisplayService().getDisplayMetadata(
                bdvh, SourceSelectorBehaviour.class.getSimpleName());

        new EditorBehaviourUnInstaller(bdvh).run();

        addEditorBehaviours(bdvh, ssb);

        bdvh.getViewerPanel().setNumTimepoints(SourceHelper.getMaxTimepoint(sources)+1);

        // Close hook to try to release as many resources as possible -> proven avoiding mem leaks
        BdvHandleHelper.setBdvHandleCloseOperation(bdvh, ctx.getService(CacheService.class),
                SourceServices.getBdvDisplayService(), false,
                () -> {
                    sources = null; // free mem ?
                    transformedToOriginal = null;
                    ctx.getService(ObjectService.class).removeObject(bdvh);
                }
        );

    }


    void addEditorBehaviours(BdvHandle bdvh, SourceSelectorBehaviour ssb) {
        Behaviours editor = new Behaviours(new InputTriggerConfig());

        // Act on the original sources
        editor.behaviour(new SourceContextMenuClickBehaviour( bdvh,
                () -> ssb.getSelectedSources()
                            .stream()
                            .map((source) -> transformedToOriginal.get(source))
                            .collect(Collectors.toSet())), "Sources Context Menu", "button3");

        // One way to chain the behaviour : install and uninstall on source selector toggling:
        // The delete key will act only when the source selection mode is on
        ssb.addToggleListener(new ToggleListener() {
            @Override
            public void isEnabled() {
                bdvh.getViewerPanel().showMessage("Selection Mode Enable");
                bdvh.getViewerPanel().showMessage(ssb.getSelectedSources().size()+" sources selected");
                // Enable the editor behaviours when the selector is enabled
                editor.install(bdvh.getTriggerbindings(), "sources-editor");
            }

            @Override
            public void isDisabled() {
                bdvh.getViewerPanel().showMessage("Selection Mode Disable");
                // Disable the editor behaviours the selector is disabled
                bdvh.getTriggerbindings().removeInputTriggerMap("sources-editor");
                bdvh.getTriggerbindings().removeBehaviourMap("sources-editor");
            }
        });
    }

    /**
     * Sorts sources according to their dataset and view id
     */
    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = SourceHelper::sortDefaultGeneric;

    public static class SacProperties {

        public final AffineTransform3D location;
        public long[] dims = new long[3];
        public final SourceAndConverter<?> source;
        public final boolean isRGB; // Always split RGB images

        /**
         * crete the Sac Properties object
         * @param source source to give
         */
        public SacProperties(SourceAndConverter<?> source) {
            location = new AffineTransform3D();

            if (SourceHelper.hasAValidTimepoint(source.getSpimSource())) {
                int tpvalid = SourceHelper.getAValidTimepoint(source.getSpimSource());
                source.getSpimSource().getSourceTransform(tpvalid, 0, location);
                source.getSpimSource().getSource(tpvalid, 0).dimensions(dims);
            }
            this.source = source;
            this.isRGB = (source.getSpimSource().getType() instanceof ARGBType) || (source.getSpimSource().getType() instanceof VolatileARGBType);
        }

        public SourceAndConverter<?> getSource() {
            return source;
        }

        List<Class<? extends Entity>> entitiesSplit = new ArrayList<>();

        public void splitByEntity(Class<? extends Entity> entityClass) {
            entitiesSplit.add(entityClass);
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 89  * hash + (int) dims[0] + 17 * (int) dims[1] + 57 * (int) dims[2];
            hash = hash + (int) (10 * location.get(0,0));
            return hash;
        }

        @Override
        public boolean equals(Object obj) {

            if (obj instanceof SacProperties) {
                SacProperties other = (SacProperties) obj;
                if (isRGB) return other.source == this.source; // Always split different RGB images
                if  (
                      (MatrixApproxEquals(location.getRowPackedCopy(), other.location.getRowPackedCopy()))
                    &&(dims[0]==other.dims[0])&&(dims[1]==other.dims[1])&&(dims[2]==other.dims[2])) {
                    for (Class<? extends Entity> entityClass:entitiesSplit) {
                        if (!haveSameEntity(other.source, this.source, entityClass)) {
                            return false;
                        }
                    }
                    return true;
                } else {
                    return false;
                }

            } else {
                return false;
            }
        }

    }

    public static Entity getEntityFromSource(SourceAndConverter<?> source, Class<? extends Entity> entityClass) {
        if (SourceServices.getSourceService().getMetadata(source, SPIM_DATA_INFO) != null) {
            SourceService.SpimDataInfo sdi = (SourceService.SpimDataInfo) SourceServices.getSourceService().getMetadata(source, SPIM_DATA_INFO);
            AbstractSpimData<AbstractSequenceDescription<BasicViewSetup, ?, ?>> asd = (AbstractSpimData<AbstractSequenceDescription<BasicViewSetup, ?, ?>>) sdi.asd;
            BasicViewSetup bvs = asd.getSequenceDescription().getViewSetups().get(sdi.setupId);
            return bvs.getAttribute(entityClass);
        } else {
            return null;
        }
    }

    public static boolean haveSameEntity(SourceAndConverter<?> s1, SourceAndConverter<?> s2, Class<? extends Entity> entityClass) {
        Entity s1Entity = getEntityFromSource(s1, entityClass);
        Entity s2Entity = getEntityFromSource(s2, entityClass);
        if ((s1Entity==null)&&(s2Entity==null)) {
            return true;
        }
        if ((s1Entity==null)||(s2Entity==null)) {
            return false;
        }

        return s1Entity.equals(s2Entity);
    }

}
