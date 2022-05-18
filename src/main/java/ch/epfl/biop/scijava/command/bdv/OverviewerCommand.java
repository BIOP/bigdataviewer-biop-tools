package ch.epfl.biop.scijava.command.bdv;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.bioformats.imageloader.FileIndex;
import ch.epfl.biop.bdv.bioformats.imageloader.SeriesNumber;
import ch.epfl.biop.scijava.command.source.ExportToMultipleImagePlusCommand;
import ch.epfl.biop.bdv.select.SourceSelectorBehaviour;
import ch.epfl.biop.bdv.select.ToggleListener;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;
import org.scijava.Context;
import org.scijava.cache.CacheService;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.behaviour.EditorBehaviourUnInstaller;
import sc.fiji.bdvpg.behaviour.SourceAndConverterContextMenuClickBehaviour;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.command.source.BrightnessAdjusterCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static sc.fiji.bdvpg.bdv.navigate.ViewerTransformSyncStopper.MatrixApproxEquals;
import static sc.fiji.bdvpg.scijava.services.SourceAndConverterService.getCommandName;
import static sc.fiji.bdvpg.services.ISourceAndConverterService.SPIM_DATA_INFO;

/**
 * Command which display sources on a grid in BigDataViewer
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Display Sources On Grid")
public class OverviewerCommand implements BdvPlaygroundActionCommand {

    /**
     * Sources to display on a grid
     */
    @Parameter
    public SourceAndConverter[] sacs;

    @Parameter
    int timepointBegin;

    @Parameter
    int nColumns;

    @Parameter(label = "Split by dataset entites, comma separated (channel, fileseries)")
    String entitiesSplit = "";

    Map<String, Class<? extends Entity>> entityClasses = new HashMap<>();

    // sourceList;

    int currentIndex = 0;
    AffineTransform3D currentAffineTransform = new AffineTransform3D();

    Map<SourceAndConverter<?>, SourceAndConverter<?>> transformedToOriginal = new HashMap<>();

    @Parameter
    Context ctx;

    @Override
    public void run() {

        entityClasses.put("CHANNEL", Channel.class);
        entityClasses.put("TILE", Tile.class);
        entityClasses.put("ILLUMINATION", Illumination.class);
        entityClasses.put("ANGLE", Angle.class);
        entityClasses.put("FILE", FileIndex.class);
        entityClasses.put("SERIES", SeriesNumber.class);

        List<Class<? extends Entity>> entSplit = new ArrayList<>();

        for (String entity : entitiesSplit.split(",")) {
            String ent = entity.trim().toUpperCase();
            if (!entityClasses.containsKey(ent)){
                System.err.println("Unrecognized entity class "+ent);
            } else {
                System.out.println("Splitting by "+ent);
                entSplit.add(entityClasses.get(ent));
            }
        }

        // Sort according to location = affine transform 3d of sources

        List<SourceAndConverter<?>> sourceList = sorter.apply(Arrays.asList(sacs));

        Map<SacProperties, List<SourceAndConverter>> sacClasses = sourceList
                .stream()
                .collect(Collectors.groupingBy(sac -> {
                    SacProperties props = new SacProperties(sac);
                    for (Class<? extends Entity> entityClass : entSplit) {
                        props.splitByEntity(entityClass);
                    }
                    return props; //new SacProperties(sac)
                }));

        Map<SourceAndConverter<?>, List<SacProperties>> keySetSac = sacClasses.keySet().stream().collect(Collectors.groupingBy(p -> p.sac));

        List<SourceAndConverter<?>> sortedSacs = sorter.apply(keySetSac.keySet());

        List<SourceAndConverter<?>> sacsToDisplay = new ArrayList<>();

        sortedSacs.forEach(sacKey -> {
            SacProperties sacPropsKey = keySetSac.get(sacKey).get(0);
            for (Class<? extends Entity> entityClass : entSplit) {
                sacPropsKey.splitByEntity(entityClass);
            }

            AffineTransform3D location = sacPropsKey.location;

            int xPos = currentIndex % nColumns;
            int yPos = currentIndex / nColumns;

            currentAffineTransform.identity();
            currentAffineTransform.preConcatenate(location.inverse());
            AffineTransform3D translator = new AffineTransform3D();
            translator.translate(xPos, yPos,0);

            currentIndex++;

            List<SourceAndConverter> sacs = sacClasses.get(sacPropsKey);// sacSortedPerLocation.get(location);

            long nPixX = sacs.get(0).getSpimSource().getSource(timepointBegin, 0).dimension(0);

            long nPixY = sacs.get(0).getSpimSource().getSource(timepointBegin, 0).dimension(1);

            long nPixZ = sacs.get(0).getSpimSource().getSource(timepointBegin, 0).dimension(2);

            long sizeMax = Math.max(nPixX, nPixY);

            sizeMax = Math.max(sizeMax, nPixZ);

            currentAffineTransform.scale(1/(double)sizeMax, 1/(double) sizeMax, 1/(double)sizeMax);

            currentAffineTransform.translate(xPos,yPos,0);

            SourceAffineTransformer sat = new SourceAffineTransformer(null, currentAffineTransform);

            List<SourceAndConverter<?>> transformedSacs =
                    sacs.stream().map(sac -> {
                        SourceAndConverter<?> trSac = sat.apply(sac);
                        transformedToOriginal.put(trSac, sac);
                        SourceAndConverterServices
                                .getSourceAndConverterService()
                                .register(trSac);

                        ConverterSetup csOrigin = SourceAndConverterServices
                                .getSourceAndConverterService()
                                .getConverterSetup(sac);

                        ConverterSetup csDestination = SourceAndConverterServices
                                .getSourceAndConverterService()
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

            sacsToDisplay.addAll(transformedSacs);
        });

        //BdvHandle bdvh =

        BdvHandle bdvh = SourceAndConverterServices.getBdvDisplayService().getNewBdv();
        SourceAndConverterServices.getBdvDisplayService().show(bdvh, sacsToDisplay.toArray(new SourceAndConverter[0]));

        AffineTransform3D currentViewLocation = new AffineTransform3D();

        bdvh.getViewerPanel().state().getViewerTransform(currentViewLocation);
        currentViewLocation.set(0,2,3);
        bdvh.getViewerPanel().state().setViewerTransform(currentViewLocation);

        SourceSelectorBehaviour ssb = (SourceSelectorBehaviour) SourceAndConverterServices.getBdvDisplayService().getDisplayMetadata(
                bdvh, SourceSelectorBehaviour.class.getSimpleName());

        new EditorBehaviourUnInstaller(bdvh).run();

        addEditorBehaviours(bdvh, ssb);

        bdvh.getViewerPanel().setNumTimepoints(SourceAndConverterHelper.getMaxTimepoint(sacs[0]));

        // Close hook to try to release as many resources as possible -> proven avoiding mem leaks
        BdvHandleHelper.setBdvHandleCloseOperation(bdvh, ctx.getService(CacheService.class),
                SourceAndConverterServices.getBdvDisplayService(), false,
                () -> {
                    sacs = null; // free mem ?
                    transformedToOriginal = null;
                    ctx.getService(ObjectService.class).removeObject(bdvh);
                }
        );

    }


    void addEditorBehaviours(BdvHandle bdvh, SourceSelectorBehaviour ssb) {
        Behaviours editor = new Behaviours(new InputTriggerConfig());

        // Act on the original sources
        editor.behaviour(new SourceAndConverterContextMenuClickBehaviour( bdvh,
                () -> ssb.getSelectedSources()
                            .stream()
                            .map((sac) -> transformedToOriginal.get(sac))
                            .collect(Collectors.toSet()),
                getPopupActionsOnWrappedSource() ), "Sources Context Menu", "button3");

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

    public static String[] getPopupActionsOnWrappedSource() {
        String[] editorPopupActions = {
                "Inspect Sources",
                getCommandName(BrightnessAdjusterCommand.class),
                getCommandName(ExportToMultipleImagePlusCommand.class)};
        return editorPopupActions;
    }

    /**
     * Sorts sources according to their dataset and view id
     */
    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultGeneric(sacslist);

    public static class SacProperties {

        public final AffineTransform3D location;
        public long[] dims = new long[3];
        public final SourceAndConverter sac;
        public final boolean isRGB; // Always split RGB images

        /**
         * crete the Sac Properties object
         * @param sac source to give
         */
        public SacProperties(SourceAndConverter sac) {
            location = new AffineTransform3D();
            sac.getSpimSource().getSourceTransform(0, 0, location);
            sac.getSpimSource().getSource(0,0).dimensions(dims);
            this.sac = sac;
            this.isRGB = (sac.getSpimSource().getType() instanceof ARGBType) || (sac.getSpimSource().getType() instanceof VolatileARGBType);
        }

        public SourceAndConverter getSource() {
            return sac;
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
                if (isRGB) return other.sac == this.sac; // Always split different RGB images
                if  (
                      (MatrixApproxEquals(location.getRowPackedCopy(), other.location.getRowPackedCopy()))
                    &&(dims[0]==other.dims[0])&&(dims[1]==other.dims[1])&&(dims[2]==other.dims[2])) {
                    for (Class<? extends Entity> entityClass:entitiesSplit) {
                        if (!haveSameEntity(other.sac, this.sac, entityClass)) {
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
        if (SourceAndConverterServices.getSourceAndConverterService().getMetadata(source, SPIM_DATA_INFO) != null) {
            SourceAndConverterService.SpimDataInfo sdi = (SourceAndConverterService.SpimDataInfo) SourceAndConverterServices.getSourceAndConverterService().getMetadata(source, SPIM_DATA_INFO);
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
