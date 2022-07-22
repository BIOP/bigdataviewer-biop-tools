package ch.epfl.biop.spimdata.qupath;

import ch.epfl.biop.bdv.bioformats.BioFormatsMetaDataHelper;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.export.spimdata.BioFormatsConvertFilesToSpimData;
import ch.epfl.biop.bdv.bioformats.imageloader.*;
import ch.epfl.biop.omero.omerosource.OmeroSourceOpener;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import ij.IJ;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.*;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
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
 * See documentation in {@link QuPathImageLoader}
 * @author Nicolas Chiaruttini, EPFL, BIOP, 2021
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

    Map<Integer, MinimalQuPathProject.ImageEntry> viewSetupToImageEntry = new HashMap<>();


    public BioFormatsBdvOpener getBioFormatsBDVOpener(String datalocation, GuiParams guiParams) {
        Unit bfUnit = BioFormatsMetaDataHelper.getUnitFromString(guiParams.getUnit());
        Length positionReferenceFrameLength = new Length(guiParams.getRefframesizeinunitlocation(), bfUnit);
        Length voxSizeReferenceFrameLength = new Length(guiParams.getVoxSizeReferenceFrameLength(), bfUnit);

        BioFormatsBdvOpener opener = BioFormatsBdvOpener.getOpener()
                .location(datalocation)
                //.auto()
                .ignoreMetadata();
        if (!guiParams.getSwitchzandc().equals("AUTO")) {
            opener = opener.switchZandC(guiParams.getSwitchzandc().equals("TRUE"));
        }

        if (!guiParams.getUsebioformatscacheblocksize()) {
            opener = opener.cacheBlockSize(guiParams.getCachesizex(), guiParams.getCachesizey(), guiParams.getCachesizez());
        }

        if (!guiParams.getPositoniscenter().equals("AUTO")) {
            if (guiParams.getPositoniscenter().equals("TRUE")) {
                opener = opener.centerPositionConvention();
            } else {
                opener = opener.cornerPositionConvention();
            }
        }

        if (!guiParams.getFlippositionx().equals("AUTO") && guiParams.getFlippositionx().equals("TRUE")) {
            opener = opener.flipPositionX();
        }

        if (!guiParams.getFlippositiony().equals("AUTO") && guiParams.getFlippositiony().equals("TRUE")) {
            opener = opener.flipPositionY();
        }

        if (!guiParams.getFlippositionz().equals("AUTO") && guiParams.getFlippositionz().equals("TRUE")) {
            opener = opener.flipPositionZ();
        }

        opener = opener.unit(bfUnit);
        opener = opener.positionReferenceFrameLength(positionReferenceFrameLength);
        opener = opener.voxSizeReferenceFrameLength(voxSizeReferenceFrameLength);
        if (guiParams.getSplitChannels()) {
            opener = opener.splitRGBChannels();
        }

        return opener;
    }



    public AbstractSpimData getSpimDataInstance(URI quPathProject, GuiParams guiparams) {
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
            IJ.log("projectJson : "+projectJson);
            MinimalQuPathProject project = gson.fromJson(projectJson, MinimalQuPathProject.class);
            SpimData spimData = null;
            logger.debug("Opening QuPath project " + project.uri);

         //   Set<QuPathImageLoader.QuPathBioFormatsSourceIdentifier> quPathSourceIdentifiers = new HashSet<>();

            Map<BioFormatsBdvOpener, IFormatReader> cachedReaders = new HashMap<>(); // Performance
            List<SpimData>ss = new ArrayList<>();
            project.images.forEach(image -> {

                logger.debug("Opening qupath image "+image);

                double angleRotationZAxis = 0;
                if (image.serverBuilder.builderType.equals("rotated")) {
                    String angleDegreesStr = image.serverBuilder.rotation.substring(7);//"ROTATE_ANGLE" for instance "ROTATE_0", "ROTATE_270", etc
                    logger.debug("Rotated image server ("+angleDegreesStr+")");
                    if (angleDegreesStr.equals("NONE")) {
                        angleRotationZAxis = 0;
                    } else {
                        angleRotationZAxis = (Double.valueOf(angleDegreesStr) / 180.0) * Math.PI;
                    }
                    MinimalQuPathProject.ServerBuilderMetadata metadata = image.serverBuilder.metadata; // To keep the metadata (pixel size for instance)
                    image.serverBuilder = image.serverBuilder.builder; // Skips the rotation
                    image.serverBuilder.metadata = metadata;
                }


                if (image.serverBuilder.builderType.equals("uri")) {
                    logger.debug("URI image server");
                    if (image.serverBuilder.providerClassName.equals("qupath.lib.images.servers.bioformats.BioFormatsServerBuilder")) {
                        try {
                            QuPathImageLoader.QuPathBioFormatsSourceIdentifier identifier = new QuPathImageLoader.QuPathBioFormatsSourceIdentifier();
                            identifier.angleRotationZAxis = angleRotationZAxis;


                            URI uri = new URI(image.serverBuilder.uri.getScheme(), image.serverBuilder.uri.getHost(), image.serverBuilder.uri.getPath(), null);
                            IJ.log("angleRotationZAxis : " + identifier.angleRotationZAxis);
                            IJ.log("uri : " + uri);
                            // This appears to work more reliably than converting to a File
                            String filePath = Paths.get(uri).toString();


                            if (!openerMap.containsKey(image.serverBuilder.uri)) {
                                BioFormatsBdvOpener opener = getBioFormatsBDVOpener(filePath, guiparams);
                                opener = opener.ignoreMetadata();

                                ss.add((SpimData) (new BioFormatsConvertFilesToSpimData()).getSpimDataInstance(Collections.singletonList(opener)));

                              /*  openerMap.put(image.serverBuilder.uri,opener);
                                cachedReaders.put(opener, opener.getNewReader());
                                uriToFileIndexMap.put(image.serverBuilder.uri, fileIndexCounter);
                                fileIndexCounter++;*/
                            }
                        } catch (URISyntaxException e) {
                            logger.error("URI Syntax error " + e.getMessage());
                            e.printStackTrace();

                        }

                    }
                }
            });
            spimData = ss.get(0);

                            /*identifier.uri = image.serverBuilder.uri;
                            identifier.sourceFile = filePath;
                            identifier.indexInQuPathProject = project.images.indexOf(image);
                            identifier.entryID = project.images.get(identifier.indexInQuPathProject).entryID;

                            IJ.log("identifier.uri : " + identifier.uri);
                            IJ.log("identifier.sourceFile : " + identifier.sourceFile);
                            IJ.log("identifier.indexInQuPathProject : " + identifier.indexInQuPathProject);
                            IJ.log("identifier.entryID : " + identifier.entryID);

                            int iSerie =  image.serverBuilder.args.indexOf("--series");

                            IJ.log("iSerie : " + iSerie);

                            Tile tile = new Tile(nTileCounter);
                            IJ.log("nTileCounter : " + nTileCounter);
                            nTileCounter++;

                            if (iSerie==-1) {
                                logger.error("Series not found in qupath project server builder!");
                                identifier.bioformatsIndex = -1;
                            } else {
                                identifier.bioformatsIndex = Integer.parseInt(image.serverBuilder.args.get(iSerie + 1));
                            }
                            IJ.log("identifier.bioformatsIndex : " + identifier.bioformatsIndex);

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

                            QuPathEntryEntity qpentry = new QuPathEntryEntity(identifier.entryID);
                            qpentry.setName(QuPathEntryEntity.getNameFromURIAndSerie(identifier.uri, identifier.bioformatsIndex));
                            qpentry.setQuPathProjectionLocation(Paths.get(quPathProject).toString());
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

                                        viewSetupToImageEntry.put(viewSetupCounter, image);

                                        FileIndex fi = new FileIndex(uriToFileIndexMap.get(identifier.uri),identifier.sourceFile);
                                        vs.setAttribute(fi);
                                        SeriesNumber sn = new SeriesNumber(identifier.bioformatsIndex);
                                        vs.setAttribute(sn);
                                        vs.setAttribute(qpentry);

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

                    }
                    else {
                        if (image.serverBuilder.providerClassName.equals("qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder")) {
                            QuPathImageLoader.QuPathOmeroSourceIdentifier identifier = new QuPathImageLoader.QuPathOmeroSourceIdentifier();
                            identifier.angleRotationZAxis = angleRotationZAxis;
                            URI uri = image.serverBuilder.uri;

                            // This appears to work more reliably than converting to a File
                            String filePath = uri.toString();//Paths.get(uri).toString();


                            if (!openerMap.containsKey(image.serverBuilder.uri)) {
                                IJ.log("build an OmeroSourceOpener");
                                OmeroSourceOpener opener = OmeroSourceOpener.getOpener().location(filePath).ignoreMetadata();
                                IJ.log("opener : "+opener);
                               // opener = opener.ignoreMetadata();
                               // openerMap.put(image.serverBuilder.uri,opener);
                                //cachedReaders.put(opener, opener.getNewReader());
                                uriToFileIndexMap.put(image.serverBuilder.uri, fileIndexCounter);
                                fileIndexCounter++;
                            }
                            IJ.log("Before sting identifiers");
                            identifier.uri = image.serverBuilder.uri;
                            identifier.sourceFile = filePath;
                            identifier.indexInQuPathProject = project.images.indexOf(image);
                            identifier.entryID = project.images.get(identifier.indexInQuPathProject).entryID;

                            IJ.log("OMEROidentifier.uri : " + identifier.uri);
                            IJ.log("OMEROidentifier.sourceFile : " + identifier.sourceFile);
                            IJ.log("OMEROidentifier.indexInQuPathProject : " + identifier.indexInQuPathProject);
                            IJ.log("OMEROidentifier.entryID : " + identifier.entryID);

                            int iSerie =  image.serverBuilder.args.indexOf("--series");

                            IJ.log("OMEROiSerie : " + iSerie);

                            Tile tile = new Tile(nTileCounter);
                            IJ.log("nTileCounter : " + nTileCounter);
                            nTileCounter++;

                            if (iSerie==-1) {
                                logger.error("Series not found in qupath project server builder!");
                                identifier.omeroIndex = -1;
                            } else {
                                identifier.omeroIndex = Integer.parseInt(image.serverBuilder.args.get(iSerie + 1));
                            }
                            IJ.log("OMEROidentifier.omeroIndex : " + identifier.omeroIndex);
                            logger.debug(identifier.toString());
                            //quPathSourceIdentifiers.add(identifier);

                            // This appears
                        } else {
                            logger.error("Unsupported " + image.serverBuilder.providerClassName + " class name provider");
                            IJ.log("Unsupported " + image.serverBuilder.providerClassName + " class name provider");
                        }
                    }
                } else {
                    logger.error("Unsupported "+image.serverBuilder.builderType+" server builder");
                    IJ.log("Unsupported "+image.serverBuilder.builderType+" server builder");
                }
            });
*/
            // ------------------- BUILDING SPIM DATA

/*            List<TimePoint> timePoints = new ArrayList<>();
            IntStream.range(0,maxTimepoints).forEach(tp -> timePoints.add(new TimePoint(tp)));

            final ArrayList<ViewRegistration> registrations = new ArrayList<>();

            List<ViewId> missingViews = new ArrayList<>();
            for (int iViewSetup=0;iViewSetup<viewSetupCounter;iViewSetup++) {
                QuPathImageLoader.QuPathEntryAndChannel usc = viewSetupToQuPathEntryAndChannel.get(iViewSetup);
                BioFormatsBdvOpener opener = openerMap.get(usc.entry.uri);
                IFormatReader memo = cachedReaders.get(opener);//openerMap.get(usc.entry.uri));

                final IMetadata omeMeta = (IMetadata) memo.getMetadataStore();

                final int bfIndex = usc.entry.bioformatsIndex;
                final int nTimepoints = omeMeta.getPixelsSizeT(bfIndex).getNumberValue().intValue();
                final int vs = iViewSetup;

                AffineTransform3D affine = BioFormatsMetaDataHelper.getSeriesRootTransform(
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
                );

                MinimalQuPathProject.PixelCalibrations pixelCalibrations = null;

                if (viewSetupToImageEntry.get(vs)!=null) {
                    if (viewSetupToImageEntry.get(vs).serverBuilder!=null)
                        if (viewSetupToImageEntry.get(vs).serverBuilder.metadata!=null)
                    pixelCalibrations = viewSetupToImageEntry.get(vs).serverBuilder.metadata.pixelCalibration;
                }

                boolean performQuPathRescaling = false;

                AffineTransform3D quPathRescaling = new AffineTransform3D();
                if (pixelCalibrations!=null) {
                    double scaleX = 1.0;
                    double scaleY = 1.0;
                    double scaleZ = 1.0;
                    Length[] voxSizes = BioFormatsMetaDataHelper.getSeriesVoxelSizeAsLengths(omeMeta, bfIndex);
                    if (pixelCalibrations.pixelWidth!=null) {
                        MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.pixelWidth;
                        //if (pc.unit.equals("um")) {
                            if ((voxSizes[0]!=null)&&(voxSizes[0].value(UNITS.MICROMETER)!=null)) {
                                logger.debug("xVox size = "+pc.value+" micrometer");
                                scaleX = pc.value/voxSizes[0].value(UNITS.MICROMETER).doubleValue();
                            } else {
                                Length defaultxPix = new Length(1, opener.u);
                                scaleX = pc.value / defaultxPix.value(UNITS.MICROMETER).doubleValue();
                                logger.debug("rescaling x");
                            }*/
                        /*} else {
                            logger.warn("Unrecognized unit in QuPath project: "+pc.unit);
                        }*/
 /*                   }
                    if (pixelCalibrations.pixelHeight!=null) {
                        MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.pixelHeight;
                        //if (pc.unit.equals("um")) {
                            if ((voxSizes[1]!=null)&&(voxSizes[1].value(UNITS.MICROMETER)!=null)) {
                                scaleY = pc.value/voxSizes[1].value(UNITS.MICROMETER).doubleValue();
                            } else {
                                Length defaultxPix = new Length(1, opener.u);
                                scaleY = pc.value / defaultxPix.value(UNITS.MICROMETER).doubleValue();
                                logger.debug("rescaling y");
                            }*/
                        /*} else {
                            logger.warn("Unrecognized unit in QuPath project: "+pc.unit);
                        }*/
 /*                   }
                    if (pixelCalibrations.zSpacing!=null) {
                        MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.zSpacing;
                        //if (pc.unit.equals("um")) { problem with micrometer character
                            if ((voxSizes[2]!=null)&&(voxSizes[2].value(UNITS.MICROMETER)!=null)) {
                                scaleZ = pc.value/voxSizes[2].value(UNITS.MICROMETER).doubleValue();
                            } else {
                                if ((voxSizes[2]!=null)) {

                                } else {
                                    logger.warn("Null Z voxel size");
                                }
                                //logger.warn("Null Z voxel size");
                            }*/
                        /*} else {
                            logger.warn("Unrecognized unit in QuPath project: "+pc.unit);
                        }*/
 /*                   }
                    logger.debug("ScaleX: "+scaleX+" scaleY:"+scaleY+" scaleZ:"+scaleZ);
                    if ((Math.abs(scaleX-1.0)>0.0001)||(Math.abs(scaleY-1.0)>0.0001)||(Math.abs(scaleZ-1.0)>0.0001))  {
                        logger.debug("Perform QuPath rescaling");
                        quPathRescaling.scale(scaleX, scaleY, scaleZ);
                        double oX = affine.get(0,3);
                        double oY = affine.get(1,3);
                        double oZ = affine.get(2,3);
                        affine.preConcatenate(quPathRescaling);
                        affine.set(oX, 0,3);
                        affine.set(oY, 1,3);
                        affine.set(oZ, 2,3);
                    }
                }

                logger.debug("ViewSetup : " + vs + " append view registrations ");
                timePoints.forEach(iTp -> {
                    if (iTp.getId()<nTimepoints) {
                        registrations.add(new ViewRegistration(iTp.getId(), vs, affine));
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

            final SpimData spimData = new SpimData( null, sd, new ViewRegistrations( registrations ) );*/
            /*spimData.getSequenceDescription().getViewSetups();
            spimData.getViewRegistrations().getViewRegistrations();
            spimData.getSequenceDescription().getTimePoints();*/
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
