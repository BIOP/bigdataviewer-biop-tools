package bdv.util;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.spimdata.qupath.QuPathEntryEntity;
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
     * Returns the QuPathEntity from a source directly linked to a dataset generated
     * from a qupath project. Returns null is there's not any
     * @param source
     * @return
     */
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
     * @param source
     * @return
     */
    public static QuPathEntryEntity getQuPathEntityFromDerivedSource(SourceAndConverter source) {
        return getQuPathEntityFromSource(SourceAndConverterInspector.getRootSourceAndConverter(source));
    }

    /**
     *
     * @param entryEntity
     * @return
     * @throws Exception
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
     * @param entryEntity
     * @return
     * @throws Exception
     */
    public static File getDataEntryFolder(SourceAndConverter source) throws Exception {
        return getDataEntryFolder(getQuPathEntityFromDerivedSource(source));
    }

    /**
     *
     * @return
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
     *
     * @return
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

        return null; // TODO
    }

}
