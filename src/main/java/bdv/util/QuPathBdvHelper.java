package bdv.util;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.OpenersImageLoader;
import ch.epfl.biop.bdv.img.legacy.qupath.entity.QuPathEntryEntity;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.scijava.services.ui.SourceAndConverterInspector;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.io.File;

// Can't be moved to bigdataviewer-image-loader because this class depends on
// bigdataviewer-playground
// Needs to support legacy qupath image loader
public class QuPathBdvHelper {

    /**
     * @param source bdv source
     * @return the file of the data of this source
     * @throws Exception if the file is not found
     */
    public static File getDataEntryFolder(SourceAndConverter source) throws IllegalArgumentException {
        File quPathProject = QuPathBdvHelper.getProjectFile(source);
        int entryId = QuPathBdvHelper.getEntryId(source);
        File f = new File(quPathProject.getParent(), "data"+File.separator+entryId);
        if (!f.exists()) {
            throw new IllegalArgumentException("QuPath entry folder "+f.getAbsolutePath()+" does not exist.");
        }
        return f;
    }

    /**
     * @param source_in bdv source
     * @return the file of the QuPath Project from this source
     * @throws Exception if the file is not found
     */
    public static File getProjectFile(SourceAndConverter source_in) throws IllegalArgumentException {
        SourceAndConverter<?> rootSource = SourceAndConverterInspector.getRootSourceAndConverter(source_in);
        if (!isBoundToLegacyQuPathBDVDataset(rootSource)) {
            AbstractSpimData asd =
                    ((SourceAndConverterService.SpimDataInfo) SourceAndConverterServices.getSourceAndConverterService()
                            .getMetadata(rootSource, SourceAndConverterService.SPIM_DATA_INFO)).asd;

            int viewSetupId = ((SourceAndConverterService.SpimDataInfo) SourceAndConverterServices.getSourceAndConverterService()
                    .getMetadata(rootSource, SourceAndConverterService.SPIM_DATA_INFO)).setupId;

            if (!asd.getSequenceDescription().getImgLoader().getClass().equals(OpenersImageLoader.class)) {
                throw new IllegalArgumentException("The source "+source_in.getSpimSource().getName()+" is not associated to a QuPath Dataset");
            }

            OpenersImageLoader imgLoader = (OpenersImageLoader) asd.getSequenceDescription().getImgLoader();
            int openerId = imgLoader.getViewSetupToOpenerAndChannelIndex().get(viewSetupId).getOpenerIndex();
            OpenerSettings settings = imgLoader.getOpenerSettings().get(openerId);

            if (settings.getType().equals(OpenerSettings.OpenerType.QUPATH)) {
                //String qupathProjectLocation = settings.getLocation();
                return new File(settings.getLocation());
            } else {
                throw new IllegalArgumentException("The source "+source_in.getSpimSource().getName()+" is not associated to a QuPath project");
            }
        } else {
            //noinspection deprecation
            QuPathEntryEntity entity = getQuPathEntityFromSource(rootSource);
            return getProjectFile(entity);
        }
    }

    public static boolean isSourceLinkedToQuPath(SourceAndConverter<?> source) {
        File f;
        try {
            f = getProjectFile(source);
            return f.exists();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static int getEntryId(SourceAndConverter source) throws IllegalArgumentException {
        SourceAndConverter<?> rootSource = SourceAndConverterInspector.getRootSourceAndConverter(source);
        if (!isBoundToLegacyQuPathBDVDataset(rootSource)) {

            AbstractSpimData asd =
                    ((SourceAndConverterService.SpimDataInfo) SourceAndConverterServices.getSourceAndConverterService()
                            .getMetadata(rootSource, SourceAndConverterService.SPIM_DATA_INFO)).asd;

            int viewSetupId = ((SourceAndConverterService.SpimDataInfo) SourceAndConverterServices.getSourceAndConverterService()
                    .getMetadata(rootSource, SourceAndConverterService.SPIM_DATA_INFO)).setupId;

            // BasicViewSetup bvs = (BasicViewSetup) asd.getSequenceDescription().getViewSetups().get(viewSetupId);

            if (!asd.getSequenceDescription().getImgLoader().getClass().equals(OpenersImageLoader.class)) {
                throw new IllegalArgumentException("The source "+source.getSpimSource().getName()+" is not associated to a QuPath Dataset");
            }

            OpenersImageLoader imgLoader = (OpenersImageLoader) asd.getSequenceDescription().getImgLoader();
            int openerId = imgLoader.getViewSetupToOpenerAndChannelIndex().get(viewSetupId).getOpenerIndex();
            OpenerSettings settings = imgLoader.getOpenerSettings().get(openerId);

            if (settings.getType().equals(OpenerSettings.OpenerType.QUPATH)) {
                //String qupathProjectLocation = settings.getLocation();
                return settings.getEntryId();
            } else {
                throw new IllegalArgumentException("The source "+source.getSpimSource().getName()+" is not associated to a QuPath project");
            }
        } else {
            return getQuPathEntityFromSource(rootSource).getId();
        }
    }

    private static boolean isBoundToLegacyQuPathBDVDataset(SourceAndConverter<?> testSource) throws IllegalArgumentException{
        if (SourceAndConverterServices.getSourceAndConverterService()
                .getMetadata(testSource, SourceAndConverterService.SPIM_DATA_INFO)==null) {
                throw new IllegalArgumentException("No BDV dataset is associated with the source "+testSource.getSpimSource().getName());
        } else {
            AbstractSpimData asd =
                    ((SourceAndConverterService.SpimDataInfo) SourceAndConverterServices.getSourceAndConverterService()
                            .getMetadata(testSource, SourceAndConverterService.SPIM_DATA_INFO)).asd;

            int viewSetupId = ((SourceAndConverterService.SpimDataInfo) SourceAndConverterServices.getSourceAndConverterService()
                    .getMetadata(testSource, SourceAndConverterService.SPIM_DATA_INFO)).setupId;

            BasicViewSetup bvs = (BasicViewSetup) asd.getSequenceDescription().getViewSetups().get(viewSetupId);

            //noinspection deprecation
            return  bvs.getAttribute(QuPathEntryEntity.class)!=null;
        }
    }

    /**
     * Deprecated : Use getQuPathEntry instead
     *
     * Returns the QuPathEntity from a source directly linked to a dataset generated
     * from a qupath project. Returns null is there's not any
     * @param source_in source which should be linked to q QuPath dataset
     * @return its corresponding {@link QuPathEntryEntity}
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    private static QuPathEntryEntity getQuPathEntityFromSource(SourceAndConverter source_in) {
        SourceAndConverter<?> rootSource = SourceAndConverterInspector.getRootSourceAndConverter(source_in);
        if (SourceAndConverterServices.getSourceAndConverterService()
                .getMetadata(rootSource, SourceAndConverterService.SPIM_DATA_INFO)==null) {
            return null;
        } else {
            AbstractSpimData asd =
                    ((SourceAndConverterService.SpimDataInfo) SourceAndConverterServices.getSourceAndConverterService()
                            .getMetadata(rootSource, SourceAndConverterService.SPIM_DATA_INFO)).asd;

            int viewSetupId = ((SourceAndConverterService.SpimDataInfo) SourceAndConverterServices.getSourceAndConverterService()
                    .getMetadata(rootSource, SourceAndConverterService.SPIM_DATA_INFO)).setupId;

            BasicViewSetup bvs = (BasicViewSetup) asd.getSequenceDescription().getViewSetups().get(viewSetupId);

            return bvs.getAttribute(QuPathEntryEntity.class);
        }
    }

    /**
     * @param entity qupathEntry Entity, contained in a bdv dataset
     * @return qupath project file
     * @throws Exception if the file is not found
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    private static File getProjectFile(QuPathEntryEntity entity) throws IllegalArgumentException {
        File quPathProject = new File(entity.getQuPathProjectionLocation());
        if (!quPathProject.exists()) {
            throw new IllegalArgumentException("QuPath project file "+quPathProject.getAbsolutePath()+" does not exist.");
        }
        return quPathProject;
    }
}
