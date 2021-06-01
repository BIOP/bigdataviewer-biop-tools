package ch.epfl.biop.spimdata.qupath;

import ch.epfl.biop.bdv.bioformats.BioFormatsMetaDataHelper;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.imageloader.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.*;
import net.imglib2.Dimensions;
import net.imglib2.type.numeric.ARGBType;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spimdata.util.Displaysettings;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.IntStream;

/**
 * See BioFormatsConvertFilesToSpimData
 */

public class QuPathToSpimData {

    protected static Logger logger = LoggerFactory.getLogger(QuPathToSpimData.class);

    private int getChannelId(IMetadata omeMeta, int iSerie, int iChannel, boolean isRGB) {
        BioFormatsMetaDataHelper.BioformatsChannel channel = new BioFormatsMetaDataHelper.BioformatsChannel(omeMeta, iSerie, iChannel, false);
        if (!channelToId.containsKey(channel)) {
            // No : add it in the channel hashmap
            channelToId.put(channel,channelCounter);
            logger.debug("New Channel for series "+iSerie+", channel "+iChannel+", set as number "+channelCounter);
            channelIdToChannel.put(channelCounter, new Channel(channelCounter));
            channelCounter++;
        } else {
            logger.debug("Channel for series "+iSerie+", channel "+iChannel+", already known.");
        }
        int idChannel = channelIdToChannel.get(channelToId.get(channel)).getId();
        return idChannel;
    }

    int viewSetupCounter = 0;
    int nTileCounter = 0;
    int maxTimepoints = -1;
    int channelCounter = 0;
    int fileIndexCounter = 0;

    Map<Integer,Channel> channelIdToChannel = new HashMap<>();
    Map<BioFormatsMetaDataHelper.BioformatsChannel,Integer> channelToId = new HashMap<>();

    Map<URI, BioFormatsBdvOpener> openerMap = new HashMap<>();

    Map<URI, Integer> uriToFileIndexMap = new HashMap<>();

    Map<Integer, QuPathImageLoader.QuPathEntryAndChannel> viewSetupToQuPathEntryAndChannel = new HashMap<>();

    public AbstractSpimData getSpimDataInstance(URI quPathProject, final BioFormatsBdvOpener openerModel) {

        viewSetupCounter = 0;
        nTileCounter = 0;
        maxTimepoints = -1;
        channelCounter = 0;

        // No Illumination
        Illumination dummy_ill = new Illumination(0);
        // No Angle
        Angle dummy_ang = new Angle(0);
        // Many View Setups
        List<ViewSetup> viewSetups = new ArrayList<>();

        try {

            JsonObject projectJson = ProjectIO.loadRawProject(new File(quPathProject));
            Gson gson = new Gson();
            MinimalQuPathProject project = gson.fromJson(projectJson, MinimalQuPathProject.class);

            logger.debug("Opening QuPath project " + project.uri);

            Set<QuPathImageLoader.QuPathBioFormatsSourceIdentifier> quPathSourceIdentifiers = new HashSet<>();

            Map<BioFormatsBdvOpener, IFormatReader> cachedReaders = new HashMap<>(); // Performance

            project.images.forEach(image -> {
                logger.debug("Opening qupath image "+image);
                QuPathImageLoader.QuPathBioFormatsSourceIdentifier identifier = new QuPathImageLoader.QuPathBioFormatsSourceIdentifier();
                if (image.serverBuilder.builderType.equals("rotated")) {
                    String angleDegreesStr = image.serverBuilder.rotation.substring(7);//"ROTATE_ANGLE" for instance "ROTATE_0", "ROTATE_270", etc
                    logger.debug("Rotated image server ("+angleDegreesStr+")");
                    if (angleDegreesStr.equals("NONE")) {
                        identifier.angleRotationZAxis = 0;
                    } else {
                        identifier.angleRotationZAxis = (Double.valueOf(angleDegreesStr) / 180.0) * Math.PI;
                    }
                    image.serverBuilder = image.serverBuilder.builder;
                }

                if (image.serverBuilder.builderType.equals("uri")) {
                    logger.debug("URI image server");
                    if (image.serverBuilder.providerClassName.equals("qupath.lib.images.servers.bioformats.BioFormatsServerBuilder")) {
                        try {
                            URI uri = new URI(image.serverBuilder.uri.getScheme(), image.serverBuilder.uri.getHost(), image.serverBuilder.uri.getPath(), null);

                            // This appears to work more reliably than converting to a File
                            String filePath = Paths.get(uri).toString();

                            if (!openerMap.keySet().contains(image.serverBuilder.uri)) {
                                BioFormatsBdvOpener opener = new BioFormatsBdvOpener(openerModel).location(Paths.get(uri).toString());
                                opener = opener.ignoreMetadata();
                                openerMap.put(image.serverBuilder.uri,opener);
                                cachedReaders.put(opener, opener.getNewReader());
                                uriToFileIndexMap.put(image.serverBuilder.uri, fileIndexCounter);
                                fileIndexCounter++;
                            }

                            identifier.uri = image.serverBuilder.uri;
                            identifier.sourceFile = filePath;
                            identifier.indexInQuPathProject = project.images.indexOf(image);
                            identifier.entryID = project.images.get(identifier.indexInQuPathProject).entryID;

                            int iSerie =  image.serverBuilder.args.indexOf("--series");

                            Tile tile = new Tile(nTileCounter);
                            nTileCounter++;

                            if (iSerie==-1) {
                                logger.error("Series not found in qupath project server builder!");
                                identifier.bioformatsIndex = -1;
                            } else {
                                identifier.bioformatsIndex = Integer.valueOf(image.serverBuilder.args.get(iSerie + 1));
                            }

                            logger.debug(identifier.toString());
                            quPathSourceIdentifiers.add(identifier);

                            BioFormatsBdvOpener opener = openerMap.get(image.serverBuilder.uri);
                            IFormatReader memo = cachedReaders.get(opener);
                            memo.setSeries(identifier.bioformatsIndex);

                            logger.debug("Number of Series : " + memo.getSeriesCount());
                            IMetadata omeMeta = (IMetadata) memo.getMetadataStore();
                            memo.setMetadataStore(omeMeta);

                            logger.debug("\t Serie " + identifier.bioformatsIndex + " Number of timesteps = " + omeMeta.getPixelsSizeT(identifier.bioformatsIndex).getNumberValue().intValue());
                            // ---------- Serie > Channels
                            logger.debug("\t Serie " + identifier.bioformatsIndex + " Number of channels = " + omeMeta.getChannelCount(identifier.bioformatsIndex));

                            IntStream channels = IntStream.range(0, omeMeta.getChannelCount(identifier.bioformatsIndex));
                            // Register Setups (one per channel and one per timepoint)

                            if (omeMeta.getPixelsSizeT(identifier.bioformatsIndex).getNumberValue().intValue() > maxTimepoints) {
                                maxTimepoints = omeMeta.getPixelsSizeT(identifier.bioformatsIndex).getNumberValue().intValue();
                            }

                            Dimensions dims = BioFormatsMetaDataHelper.getSeriesDimensions(omeMeta, identifier.bioformatsIndex); // number of pixels .. no calibration
                            logger.debug("X:"+dims.dimension(0)+" Y:"+dims.dimension(1)+" Z:"+dims.dimension(2));
                            VoxelDimensions voxDims = BioFormatsMetaDataHelper.getSeriesVoxelDimensions(omeMeta, identifier.bioformatsIndex,
                                    opener.u, opener.voxSizeReferenceFrameLength);

                            channels.forEach(
                                    iCh -> {
                                        QuPathImageLoader.QuPathEntryAndChannel usc = new QuPathImageLoader.QuPathEntryAndChannel(identifier, iCh);
                                        viewSetupToQuPathEntryAndChannel.put(viewSetupCounter,usc);
                                        int ch_id = getChannelId(omeMeta, identifier.bioformatsIndex, iCh, memo.isRGB());

                                        String setupName = image.imageName+"_"+getChannelName(omeMeta, identifier.bioformatsIndex, iCh);
                                        logger.debug(setupName);
                                        ViewSetup vs = new ViewSetup(
                                                viewSetupCounter,
                                                setupName,
                                                dims,
                                                voxDims,
                                                tile, // Tile is index of Serie
                                                channelIdToChannel.get(ch_id),
                                                dummy_ang,
                                                dummy_ill);
                                        FileIndex fi = new FileIndex(uriToFileIndexMap.get(identifier.uri),identifier.sourceFile);
                                        vs.setAttribute(fi);
                                        SeriesNumber sn = new SeriesNumber(identifier.bioformatsIndex);
                                        vs.setAttribute(sn);

                                        // Attempt to set color
                                        Displaysettings ds = new Displaysettings(viewSetupCounter);
                                        ds.min = 0;
                                        ds.max = 255;
                                        ds.isSet = false;

                                        // ----------- Color
                                        ARGBType color = BioFormatsMetaDataHelper.getColorFromMetadata(omeMeta, identifier.bioformatsIndex, iCh);

                                        if (color!=null) {
                                            ds.isSet = true;
                                            ds.color = new int[]{
                                                    ARGBType.red(color.get()),
                                                    ARGBType.green(color.get()),
                                                    ARGBType.blue(color.get()),
                                                    ARGBType.alpha(color.get())};
                                        }
                                        vs.setAttribute(ds);
                                        viewSetups.add(vs);
                                        logger.debug("View Setup "+viewSetupCounter+" series "+iSerie+" channel "+iCh);
                                        viewSetupCounter++;
                                    });

                        } catch (URISyntaxException e) {
                            logger.error("URI Syntax error "+e.getMessage());
                            e.printStackTrace();
                        }

                    } else {
                        logger.error("Unsupported "+image.serverBuilder.providerClassName+" class name provider");
                    }
                } else {
                    logger.error("Unsupported "+image.serverBuilder.builderType+" server builder");
                }
            });

            // ------------------- BUILDING SPIM DATA

            List<TimePoint> timePoints = new ArrayList<>();
            IntStream.range(0,maxTimepoints).forEach(tp -> timePoints.add(new TimePoint(tp)));

            final ArrayList<ViewRegistration> registrations = new ArrayList<>();

            List<ViewId> missingViews = new ArrayList<>();
            for (int iViewSetup=0;iViewSetup<viewSetupCounter;iViewSetup++) {
                QuPathImageLoader.QuPathEntryAndChannel usc = viewSetupToQuPathEntryAndChannel.get(iViewSetup);
                BioFormatsBdvOpener opener = openerMap.get(usc.entry.uri);
                IFormatReader memo = cachedReaders.get(openerMap.get(usc.entry.uri));//cachedReaders.get()openers.get(iF).getNewReader();

                final IMetadata omeMeta = (IMetadata) memo.getMetadataStore();

                final int bfIndex = usc.entry.bioformatsIndex;
                final int nTimepoints = omeMeta.getPixelsSizeT(bfIndex).getNumberValue().intValue();
                final int vs = iViewSetup;

                logger.debug("ViewSetup : " + vs + " append view registrations ");
                timePoints.forEach(iTp -> {
                    if (iTp.getId()<nTimepoints) {
                        registrations.add(new ViewRegistration(iTp.getId(), vs, BioFormatsMetaDataHelper.getSeriesRootTransform(
                                omeMeta,
                                bfIndex,
                                opener.u,
                                opener.positionPreTransformMatrixArray, //AffineTransform3D positionPreTransform,
                                opener.positionPostTransformMatrixArray, //AffineTransform3D positionPostTransform,
                                opener.positionReferenceFrameLength,
                                opener.positionIsImageCenter, //boolean positionIsImageCenter,
                                opener.voxSizePreTransformMatrixArray, //voxSizePreTransform,
                                opener.voxSizePostTransformMatrixArray, //AffineTransform3D voxSizePostTransform,
                                opener.voxSizeReferenceFrameLength, //null, //Length voxSizeReferenceFrameLength,
                                opener.axesOfImageFlip // axesOfImageFlip
                        )));
                    } else {
                        missingViews.add(new ViewId(iTp.getId(), vs));
                    }

                });

            }

            // Cleaning opened readers
            cachedReaders.values().forEach(reader -> {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            SequenceDescription sd = new SequenceDescription( new TimePoints( timePoints ), viewSetups , null, new MissingViews(missingViews));
            sd.setImgLoader(new QuPathImageLoader(quPathProject,openerModel,sd,openerModel.nFetcherThread, openerModel.numPriorities));

            final SpimData spimData = new SpimData( null, sd, new ViewRegistrations( registrations ) );
            return spimData;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private String getChannelName( IMetadata omeMeta, int iSerie, int iCh )
    {
        String channelName = omeMeta.getChannelName(iSerie, iCh);
        channelName = ( channelName == null || channelName.equals( "" ) )  ? "ch" + iCh : channelName;
        return channelName;
    }

    private String getImageName( String dataLocation, int seriesCount, IMetadata omeMeta, int iSerie )
    {
        String imageName = omeMeta.getImageName(iSerie);
        String fileNameWithoutExtension = FilenameUtils.removeExtension( new File( dataLocation ).getName() );
        fileNameWithoutExtension = fileNameWithoutExtension.replace( ".ome", "" ); // above only removes .tif
        imageName = ( imageName == null || imageName.equals( "" ) ) ? fileNameWithoutExtension : imageName;
        imageName = seriesCount > 1 ?  imageName + "-s" + iSerie : imageName;
        return imageName;
    }


}
