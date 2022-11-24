package bdv.util;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.legacy.qupath.entity.QuPathEntryEntity;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.scijava.services.ui.SourceAndConverterInspector;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.io.File;

public class QuPathBdvHelper {

    /**
     * @param source source probed
     * @return true is this input source is part of a dataset generated from a
     *      qupath project
     */
    public static boolean isSourceDirectlyLinkedToQuPath(SourceAndConverter<?> source) {
        return getQuPathEntityFromSource(source)!=null;
    }

    /**
     * A derived source can be a {@link bdv.tools.transformation.TransformedSource} or
     * a {@link bdv.img.WarpedSource}. See implementation details of
     * {@link SourceAndConverterInspector#getRootSourceAndConverter(Source)} to check the exact
     * definition of a derived Source
     *
     * @param source source probed
     * @return true is this input source is derived from a dataset generated from a
     *      qupath project.
     */
    public static boolean isSourceLinkedToQuPath(SourceAndConverter<?> source) {
        return isSourceDirectlyLinkedToQuPath(SourceAndConverterInspector.getRootSourceAndConverter(source));
    }

    /**
     * Deprecated : Use getQuPathEntry instead
     *
     * Returns the QuPathEntity from a source directly linked to a dataset generated
     * from a qupath project. Returns null is there's not any
     * @param source source which should be linked to q QuPath dataset
     * @return its corresponding {@link QuPathEntryEntity}
     */
    @Deprecated
    public static QuPathEntryEntity getQuPathEntityFromSource(SourceAndConverter source) {

        if (SourceAndConverterServices.getSourceAndConverterService()
                .getMetadata(source, SourceAndConverterService.SPIM_DATA_INFO)==null) {
            return null;
        } else {
            AbstractSpimData asd =
                    ((SourceAndConverterService.SpimDataInfo) SourceAndConverterServices.getSourceAndConverterService()
                            .getMetadata(source, SourceAndConverterService.SPIM_DATA_INFO)).asd;

            int viewSetupId = ((SourceAndConverterService.SpimDataInfo) SourceAndConverterServices.getSourceAndConverterService()
                    .getMetadata(source, SourceAndConverterService.SPIM_DATA_INFO)).setupId;

            BasicViewSetup bvs = (BasicViewSetup) asd.getSequenceDescription().getViewSetups().get(viewSetupId);

            return bvs.getAttribute(QuPathEntryEntity.class);
        }
    }

    /**
     * Returns the QuPathEntity from a source derived from a dataset generated
     * from a qupath project. Returns null is there's not any
     * See implementation details of
     *      {@link SourceAndConverterInspector#getRootSourceAndConverter(Source)} to check the exact
     *      definition of a derived Source
     * @param source bdv source
     * @return corresponding {@link QuPathEntryEntity}
     */
    public static QuPathEntryEntity getQuPathEntityFromDerivedSource(SourceAndConverter source) {
        return getQuPathEntityFromSource(SourceAndConverterInspector.getRootSourceAndConverter(source));
    }

    /**
     *
     * @param entryEntity qupathEntry Entity, contained in a bdv dataset
     * @return the data folder of the image within QuPath project
     * @throws Exception if the folder is not present
     */
    public static File getDataEntryFolder(QuPathEntryEntity entryEntity) throws Exception {
        String filePath = new File(entryEntity.getQuPathProjectionLocation()).getParent();

        // under filePath, there should be a folder data/#entryID

        File f = new File(filePath, "data"+File.separator+entryEntity.getId());

        if (!f.exists()) {
            throw new Exception("QuPath entry folder "+f.getAbsolutePath()+" does not exist.");
        }

        return f;
    }

    /**
     *
     * @param source bdv source
     * @return the file of the data of this source
     * @throws Exception if the file is not found
     */
    public static File getDataEntryFolder(SourceAndConverter source) throws Exception {
        return getDataEntryFolder(getQuPathEntityFromDerivedSource(source));
    }

    /**
     * @param source bdv source
     * @return the file of the QupathProject from this source
     * @throws Exception if the file is not found
     */
    public static File getQuPathProjectFile(SourceAndConverter source) throws Exception {
        if (isSourceLinkedToQuPath(source)) {
            QuPathEntryEntity entity = QuPathBdvHelper.getQuPathEntityFromDerivedSource(source);
            return getQuPathProjectFile(entity);
        } else {
            return null;
        }
    }

    /**
     * @param entity qupathEntry Entity, contained in a bdv dataset
     * @return qupath project file
     * @throws Exception if the file is not found
     */
    public static File getQuPathProjectFile(QuPathEntryEntity entity) throws Exception {
        File quPathProject = new File(entity.getQuPathProjectionLocation());
        if (!quPathProject.exists()) {
            throw new Exception("QuPath project file "+quPathProject.getAbsolutePath()+" does not exist.");
        }
        return quPathProject;
    }

    public static SourceAndConverter[] getAllChannels(SourceAndConverter source) {
        /*QuPathEntryEntity entity = getQuPathEntityFromSource(source);
        if (entity==null) return null;

        AbstractSpimData asd =
                ((SourceAndConverterService.SpimDataInfo) SourceAndConverterServices.getSourceAndConverterService()
                        .getMetadata(source, SourceAndConverterService.SPIM_DATA_INFO)).asd;

        SourceAndConverterServices.getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(asd);*/

        throw new UnsupportedOperationException("getAllChannels currently unimplemented");

        //return null; // TODO
    }

}
