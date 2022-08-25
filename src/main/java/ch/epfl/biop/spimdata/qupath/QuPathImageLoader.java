package ch.epfl.biop.spimdata.qupath;

import bdv.ViewerImgLoader;
import bdv.cache.CacheControl;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.util.volatiles.SharedQueue;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvSource;
import ch.epfl.biop.omero.omerosource.OmeroSourceOpener;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import net.imglib2.Volatile;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.NumericType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * QuPath Image Loader. In combination with {@link QuPathToSpimData}, this class
 * is used to convert a QuPath project file into a BDV compatible dataset.
 *
 * There are some limitations: only bioformats image server, rotated image server
 * and omero-raw image server are supported ( among probably other limitations ).
 *
 * Also, editing files in the QuPath project after it has been converted to an xml bdv dataset
 * is not guaranteed to work.
 *
 * @author Nicolas Chiaruttini, EPFL, BIOP, 2021
 * @author Rémy Dornier, EPFL, BIOP, 2022
 */
public class QuPathImageLoader implements ViewerImgLoader, MultiResolutionImgLoader {

    private static final Logger logger = LoggerFactory.getLogger(QuPathImageLoader.class);
    final AbstractSequenceDescription<?, ?, ?> sequenceDescription;
    protected VolatileGlobalCellCache cache;
    protected SharedQueue sq;
    Map<Integer, QuPathSetupLoader> imgLoaders = new ConcurrentHashMap<>();
    Map<Integer, QuPathImageOpener> openerMap = new HashMap<>();
    public final int numFetcherThreads;
    public final int numPriorities;
    int viewSetupCounter = 0;
    Map<Integer, NumericType> tTypeGetter = new HashMap<>();
    Map<Integer, Volatile> vTypeGetter = new HashMap<>();
    Map<Integer, QuPathEntryAndChannel> viewSetupToQuPathEntryAndChannel = new HashMap<>();
    final URI quPathProject;
    final List<QuPathImageOpener> openerModel;

    public QuPathImageLoader(URI quPathProject, List<QuPathImageOpener> qpOpeners, final AbstractSequenceDescription<?, ?, ?> sequenceDescription, int numFetcherThreads, int numPriorities) {
        this.quPathProject = quPathProject;
        this.openerModel = qpOpeners;
        this.sequenceDescription = sequenceDescription;
        this.numFetcherThreads = numFetcherThreads;
        this.numPriorities = numPriorities;
        sq = new SharedQueue(numFetcherThreads, numPriorities);

        try {
            // deserialize qupath project
            JsonObject projectJson = ProjectIO.loadRawProject(new File(quPathProject));
            Gson gson = new Gson();
            MinimalQuPathProject project = gson.fromJson(projectJson, MinimalQuPathProject.class);
            logger.debug("Opening QuPath project " + project.uri);

            qpOpeners.forEach(qpOpener -> {
                // get the image corresponding to the opener
                MinimalQuPathProject.ImageEntry image = qpOpener.getImage();
                logger.debug("Opening qupath image "+image);

                if (image.serverBuilder.builderType.equals("uri")) {
                    logger.debug("URI image server");
                    if (image.serverBuilder.providerClassName.equals("qupath.lib.images.servers.bioformats.BioFormatsServerBuilder")) {
                        // get the BioFormats opener
                        QuPathSourceIdentifier identifier = qpOpener.getIdentifier();
                        BioFormatsBdvOpener opener = (BioFormatsBdvOpener) qpOpener.getOpener();
                        opener.setCache(sq);

                        // create a new reader
                        IFormatReader memo = opener.getNewReader();

                        // get metadata
                        IMetadata omeMeta = (IMetadata) memo.getMetadataStore();
                        memo.setMetadataStore(omeMeta);

                        // get series
                        int iSerie = identifier.bioformatsIndex;
                        memo.setSeries(iSerie);
                        IntStream channels = IntStream.range(0, qpOpener.getOmeMetaIdxOmeXml().getChannelCount(iSerie));

                        // Register Setups (one per channel and one per timepoint)
                        Type<?> t = BioFormatsBdvSource.getBioformatsBdvSourceType(memo, iSerie);
                        Volatile<?> v = BioFormatsBdvSource.getVolatileOf((NumericType<?>) t);
                        channels.forEach(iCh -> {
                                    QuPathEntryAndChannel usc = new QuPathEntryAndChannel(identifier, iCh);
                                    viewSetupToQuPathEntryAndChannel.put(viewSetupCounter, usc);
                                    tTypeGetter.put(viewSetupCounter, (NumericType<?>) t);
                                    vTypeGetter.put(viewSetupCounter, v);
                                    openerMap.put(viewSetupCounter, qpOpener);
                                    viewSetupCounter++;
                                });

                    } else {
                        if (image.serverBuilder.providerClassName.equals("qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder")) {
                            // get the Omero opener
                            QuPathSourceIdentifier identifier = qpOpener.getIdentifier();
                            OmeroSourceOpener opener = (OmeroSourceOpener) qpOpener.getOpener();
                            opener.setCache(sq);

                            // Register Setups (one per channel and one per timepoint)
                            for (int channelIdx = 0; channelIdx < opener.getSizeC(); channelIdx++) {
                                QuPathEntryAndChannel usc = new QuPathEntryAndChannel(identifier, channelIdx);
                                viewSetupToQuPathEntryAndChannel.put(viewSetupCounter, usc);
                                Type<?> t;
                                try {
                                    t = opener.getNumericType(0);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                                tTypeGetter.put(viewSetupCounter, (NumericType) t);
                                Volatile v = BioFormatsBdvSource.getVolatileOf((NumericType) t);
                                vTypeGetter.put(viewSetupCounter, v);
                                openerMap.put(viewSetupCounter, qpOpener);
                                viewSetupCounter++;
                            }
                        }else{
                            logger.error("Unsupported "+image.serverBuilder.providerClassName+" provider Class Name");
                            System.out.println("Unsupported "+image.serverBuilder.providerClassName+" provider Class Name");
                        }
                    }
                } else {
                    logger.error("Unsupported "+image.serverBuilder.builderType+" server builder");
                    System.out.println("Unsupported "+image.serverBuilder.builderType+" server builder");
                }
            });

            cache = new VolatileGlobalCellCache(sq);
        } catch (Exception e) {
            logger.error("Exception "+e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public QuPathSetupLoader<?,?> getSetupImgLoader(int setupId) {
        if (imgLoaders.containsKey(setupId)) {
            // Already created - return it
            return imgLoaders.get(setupId);
        } else {
            QuPathEntryAndChannel qec = viewSetupToQuPathEntryAndChannel.get(setupId);
            QuPathImageOpener opener = this.openerMap.get(setupId);
            int iS = qec.entry.bioformatsIndex;
            int iC = qec.iChannel;
            logger.debug("loading qupath entry number = "+qec.entry+"setupId = "+setupId+" series"+iS+" channel "+iC);
            QuPathSetupLoader<?,?> imgL = new QuPathSetupLoader(
                    opener,
                    iS,
                    iC,
                    tTypeGetter.get(setupId),
                    vTypeGetter.get(setupId)
            );
            imgLoaders.put(setupId,imgL);
            return imgL;
        }
    }

    @Override
    public CacheControl getCacheControl() {
        return cache;
    }

    public URI getProjectURI() {
        return quPathProject;
    }

    public List<QuPathImageOpener>  getModelOpener() {
        return openerModel;
    }

    public static class QuPathSourceIdentifier {
        int indexInQuPathProject;
        int entryID;
        String sourceFile;
        int bioformatsIndex;
        double angleRotationZAxis = 0;
        URI uri;

        public String toString() {
            String str = "";
            str+="sourceFile:"+sourceFile+"[bf:"+bioformatsIndex+" - qp:"+indexInQuPathProject+"]";
            return str;
        }
    }

    public static class QuPathEntryAndChannel {
        final public QuPathSourceIdentifier entry;
        final public int iChannel;

        public QuPathEntryAndChannel(QuPathSourceIdentifier entry, int iChannel) {
            this.entry = entry;
            this.iChannel = iChannel;
        }
    }
}
