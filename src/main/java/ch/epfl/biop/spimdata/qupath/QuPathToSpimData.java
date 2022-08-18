package ch.epfl.biop.spimdata.qupath;

import ch.epfl.biop.bdv.bioformats.BioFormatsMetaDataHelper;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.export.spimdata.BioFormatsConvertFilesToSpimData;
import ch.epfl.biop.bdv.bioformats.imageloader.*;
import ch.epfl.biop.omero.imageloader.OmeroToSpimData;
import ch.epfl.biop.omero.omerosource.OmeroSourceOpener;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import ij.IJ;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.*;
import net.imglib2.realtransform.AffineTransform3D;
import ome.units.UNITS;
import ome.units.quantity.Length;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * See documentation in {@link QuPathImageLoader}
 * @author Nicolas Chiaruttini, EPFL, BIOP, 2021
 * @author Rémy Dornier, EPFL, BIOP, 2022
 */

public class QuPathToSpimData {

    protected static Logger logger = LoggerFactory.getLogger(QuPathToSpimData.class);

   /* private int getChannelId(IMetadata omeMeta, int iSerie, int iChannel, boolean isRGB) {
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
    }*/

   /* int viewSetupCounter = 0;
    int nTileCounter = 0;
    int maxTimepoints = -1;
    int channelCounter = 0;*/
  //  int fileIndexCounter = 0;

  ///  Map<Integer,Channel> channelIdToChannel = new HashMap<>();
   // Map<BioFormatsMetaDataHelper.BioformatsChannel,Integer> channelToId = new HashMap<>();

    Map<URI, AbstractSpimData> spimDataMap = new HashMap<>();
    Map<URI, QuPathImageOpener> uriToOpener = new HashMap<>();

 //   Map<QuPathImageOpener, SequenceDescription> openerToSd = new HashMap<>();
    //Map<URI, QuPathImageLoader.QuPathSourceIdentifier> uriToQPidentifiers = new HashMap<>();
    //Map<URI, IMetadata> uriToOMEMetadata = new HashMap<>();
    //Map<URI, MinimalQuPathProject.PixelCalibrations> uriToPixelcalibration = new HashMap<>();
    //Map<Integer, QuPathImageLoader.QuPathEntryAndChannel> viewSetupToQuPathEntryAndChannel = new HashMap<>();

    //Map<Integer, MinimalQuPathProject.ImageEntry> viewSetupToImageEntry = new HashMap<>();


    public AbstractSpimData getSpimDataInstance(URI quPathProject, GuiParams guiparams, Gateway gateway, SecurityContext ctx) {
        /*viewSetupCounter = 0;
        nTileCounter = 0;
        maxTimepoints = -1;
        channelCounter = 0;*/

       /* // No Illumination
        Illumination dummy_ill = new Illumination(0);
        // No Angle
        Angle dummy_ang = new Angle(0);
        // Many View Setups
        List<ViewSetup> viewSetups = new ArrayList<>();*/

        //AtomicReference<BioFormatsBdvOpener> bioFormatBdvOpener = new AtomicReference<>(BioFormatsBdvOpener.getOpener());
        //AtomicReference<OmeroSourceOpener> omeroBdvOpener = new AtomicReference<>(OmeroSourceOpener.getOpener());

        try {

            // get the JsonObject of the Qupath project
            JsonObject projectJson = ProjectIO.loadRawProject(new File(quPathProject));
            Gson gson = new Gson();
            MinimalQuPathProject project = gson.fromJson(projectJson, MinimalQuPathProject.class);
            SpimData spimData = null;
           // Map<URI, BioFormatsBdvOpener> bioFormatsBdvOpeners = new HashMap<>();
            //Map<URI, OmeroSourceOpener> omeroSourceOpeners = new HashMap<>();

            logger.debug("Opening QuPath project " + project.uri);
            IJ.log("projectJson : "+projectJson);
         //   Set<QuPathImageLoader.QuPathSourceIdentifier> quPathSourceIdentifiers = new HashSet<>();

          //  Map<BioFormatsBdvOpener, IFormatReader> cachedReaders = new HashMap<>(); // Performance
           // List<SpimData> spimDataList = new ArrayList<>();

            // import all images in BDV

           /* project.images.forEach(image -> {
                int indexInQuPathProject = project.images.indexOf(image);
                QuPathImageOpener qpOpener = new QuPathImageOpener(image,guiparams,indexInQuPathProject, project.images.get(indexInQuPathProject).entryID).PixelCalibration();
                Object opener = qpOpener.getOpener();
                if(opener instanceof BioFormatsBdvOpener) {
                    try {
                        bioFormatsBdvOpeners.put(new URI(qpOpener.getURI().toString()+"_"+qpOpener.getIdentifier().bioformatsIndex), (BioFormatsBdvOpener) opener);
                        uriToOpener.put(new URI(qpOpener.getURI().toString()+"_"+qpOpener.getIdentifier().bioformatsIndex), qpOpener);
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                } else if (opener instanceof OmeroSourceOpener) {
                    omeroSourceOpeners.put(qpOpener.getURI(), (OmeroSourceOpener) opener);
                    uriToOpener.put(qpOpener.getURI(), qpOpener);
                }
            });
            if(!bioFormatsBdvOpeners.values().isEmpty())
                spimDataMap.put("BioFormats",(SpimData) (new BioFormatsConvertFilesToSpimData()).getSpimDataInstance(new ArrayList<>(bioFormatsBdvOpeners.values())));
            if(!omeroSourceOpeners.values().isEmpty())
                spimDataMap.put("Omero", (SpimData) (new OmeroToSpimData()).getSpimDataInstance(new ArrayList<>(omeroSourceOpeners.values())));*/
           System.out.println("Size of images in qupath Project : " +project.images.size());
           project.images.forEach(image -> {

                logger.debug("Opening qupath image "+image);
                System.out.println("Opening qupath image "+image);
                QuPathImageOpener qpOpener = new QuPathImageOpener(image, guiparams, project.images.indexOf(image), image.entryID, gateway, ctx).loadMetadata();
                Object opener = qpOpener.getOpener();
               System.out.println("opener  "+opener);
                System.out.println("opener name  "+opener.getClass().getName());
                System.out.println("Uri for image "+image+"  ; uri : " + qpOpener.getURI());

               // build spimdata
                if(opener instanceof BioFormatsBdvOpener) {
                    spimDataMap.put(qpOpener.getURI()/*new URI(qpOpener.getURI().toString()+"_"+qpOpener.getIdentifier().bioformatsIndex)*/, (SpimData) (new BioFormatsConvertFilesToSpimData()).getSpimDataInstance(Collections.singletonList((BioFormatsBdvOpener) qpOpener.getOpener())));
                    uriToOpener.put(qpOpener.getURI()/*new URI(qpOpener.getURI().toString()+"_"+qpOpener.getIdentifier().bioformatsIndex)*/, qpOpener);

                } else if (opener instanceof OmeroSourceOpener) {
                    //System.out.println("OmeroOpeners : "+(OmeroSourceOpener)qpOpener.getOpener());
                    spimDataMap.put(qpOpener.getURI(), (SpimData) (new OmeroToSpimData()).getSpimDataInstance(Collections.singletonList((OmeroSourceOpener)qpOpener.getOpener())));
                    uriToOpener.put(qpOpener.getURI(), qpOpener);
                }

                // If the image was imported in qupath with a rotation
             /*   double angleRotationZAxis = 0;
                if (image.serverBuilder.builderType.equals("rotated")) {
                    String angleDegreesStr = image.serverBuilder.rotation.substring(7); // "ROTATE_ANGLE" for instance "ROTATE_0", "ROTATE_270", etc
                    logger.debug("Rotated image server ("+angleDegreesStr+")");
                    if (angleDegreesStr.equals("NONE")) {
                        angleRotationZAxis = 0;
                    } else {
                        angleRotationZAxis = (Double.parseDouble(angleDegreesStr) / 180.0) * Math.PI;
                    }
                    MinimalQuPathProject.ServerBuilderMetadata metadata = image.serverBuilder.metadata; // To keep the metadata (pixel size for instance)
                    image.serverBuilder = image.serverBuilder.builder; // Skips the rotation
                    image.serverBuilder.metadata = metadata;
                }*/


               /* if (image.serverBuilder.builderType.equals("uri")) {
                    logger.debug("URI image server");
                    try {
                        QuPathImageLoader.QuPathSourceIdentifier identifier = new QuPathImageLoader.QuPathSourceIdentifier();
                        identifier.angleRotationZAxis = angleRotationZAxis;
                        URI serverBuilderUri = image.serverBuilder.uri;
                        URI uri = new URI(serverBuilderUri.getScheme(), serverBuilderUri.getHost(), serverBuilderUri.getPath(), null);


                        // This appears to work more reliably than converting to a File
                        String filePath = Paths.get(uri).toString();

                        if (!spimDataMap.containsKey(serverBuilderUri)) {
                            if (image.serverBuilder.providerClassName.equals("qupath.lib.images.servers.bioformats.BioFormatsServerBuilder")) {
                                BioFormatsBdvOpener opener = getBioFormatsBDVOpener(filePath, guiparams);
                                opener = opener.ignoreMetadata();
                                bioFormatBdvOpener.set(opener);

                                spimDataMap.put(serverBuilderUri, (SpimData) (new BioFormatsConvertFilesToSpimData()).getSpimDataInstance(Collections.singletonList(opener)));
                                uriToOMEMetadata.put(serverBuilderUri, (IMetadata) opener.getNewReader().getMetadataStore());
                            }else{
                                OmeroSourceOpener opener = getOmeroBDVOpener(filePath, guiparams);
                                opener = opener.ignoreMetadata();
                                omeroBdvOpener.set(opener);

                                spimDataMap.put(serverBuilderUri, (SpimData) (new OmeroToSpimData()).getSpimDataInstance(Collections.singletonList(opener)));
                                uriToOMEMetadata.put(serverBuilderUri, (IMetadata) opener.getNewReader().getMetadataStore());
                            }
                            // fileIndexCounter++;
                            // cachedReaders.put(opener, opener.getNewReader());
                        }

                        identifier.sourceFile = filePath;
                        identifier.indexInQuPathProject = project.images.indexOf(image);
                        identifier.entryID = project.images.get(identifier.indexInQuPathProject).entryID;
                        int iSerie = image.serverBuilder.args.indexOf("--series");

                        if (iSerie == -1) {
                            logger.error("Series not found in qupath project server builder!");
                            identifier.bioformatsIndex = -1;
                        } else {
                            identifier.bioformatsIndex = Integer.parseInt(image.serverBuilder.args.get(iSerie + 1));
                        }

                        uriToQPidentifiers.put(image.serverBuilder.uri, identifier);

                        MinimalQuPathProject.PixelCalibrations pixelCalibrations = null;

                        if (image.serverBuilder != null)
                            if (image.serverBuilder.metadata != null)
                                pixelCalibrations = image.serverBuilder.metadata.pixelCalibration;

                        uriToPixelcalibration.put(serverBuilderUri, pixelCalibrations);

                    } catch (URISyntaxException e) {
                        logger.error("URI Syntax error " + e.getMessage());
                        e.printStackTrace();
                    }
                }*/
            });

           /* System.out.println("Size of spimdataMap : "+spimDataMap.keySet().size());
            spimDataMap.keySet().forEach(spuri->{

                System.out.println("spuri : "+spuri);
                SpimData bioFormatSpimData = (SpimData) spimDataMap.get(spuri);
                System.out.println("SpimData : "+bioFormatSpimData);
                System.out.println("TimePoints : "+bioFormatSpimData.getSequenceDescription().getTimePoints().getTimePointsOrdered());
                System.out.println("ViewSetups ");
                bioFormatSpimData.getSequenceDescription().getViewSetupsOrdered().forEach(e->System.out.println(e.getName()));
                System.out.println("MissingValues ");
                bioFormatSpimData.getSequenceDescription().getMissingViews().getMissingViews().forEach(e->System.out.println("Missing time point : "+e.getTimePointId()+" ; ViewSetup Id  : "+e.getViewSetupId()));
                System.out.println("ViewRegistation ");
                bioFormatSpimData.getViewRegistrations().getViewRegistrationsOrdered().forEach(e->System.out.println("Registration time point : "+e.getTimePointId()+" ; ViewSetup Id  : "+e.getViewSetupId()));
            });*/


            // regroup all the spimdata in one big spimdata
            System.out.println("Spimdatamap : "+spimDataMap);
            System.out.println("Spimdatamap key : "+spimDataMap.keySet());
            System.out.println("Spimdatamap values : "+spimDataMap.values());
           /* ArrayList<URI> q = new ArrayList<>(spimDataMap.keySet());
            if(true)
                return spimDataMap.get(q.get(q.size()-1));*/
            spimDataMap.keySet().forEach(spimUri->{
                // get spimdata, opener and identifier
                SpimData localSpimData = (SpimData) spimDataMap.get(spimUri);
                QuPathImageOpener qpOpener = uriToOpener.get(spimUri);
                QuPathImageLoader.QuPathSourceIdentifier identifier = qpOpener.getIdentifier();
                MinimalQuPathProject.PixelCalibrations pixelCalibrations = qpOpener.getPixelCalibrations();

                // create a QuPath Entry
                QuPathEntryEntity qpentry = new QuPathEntryEntity(project.images.get(identifier.indexInQuPathProject).entryID);
                qpentry.setName(QuPathEntryEntity.getNameFromURIAndSerie(spimUri, identifier.bioformatsIndex));
                qpentry.setQuPathProjectionLocation(Paths.get(quPathProject).toString());
                SeriesNumber sn = new SeriesNumber(identifier.bioformatsIndex);

               // System.out.println("bioFormatSpimData : "+bioFormatSpimData);
               // System.out.println("bioFormatSpimData.getSequenceDescription() : "+bioFormatSpimData.getSequenceDescription());
               // System.out.println("bioFormatSpimData.getSequenceDescription().getViewSetups() : "+bioFormatSpimData.getSequenceDescription().getViewSetups());
               //// bioFormatSpimData.getSequenceDescription().getViewSetups().values().forEach(e->System.out.println(e.hashCode()));
               // System.out.println("bioFormatSpimData.getSequenceDescription().getViewSetups().values() : "+bioFormatSpimData.getSequenceDescription().getViewSetups().values());

                // update spimdata
                localSpimData.getSequenceDescription().getViewSetups().values().forEach(vss->{
                  //  vss.setAttribute(sn);
                    vss.setAttribute(qpentry);
                });

              //  boolean performQuPathRescaling = false;

                // create a new AffineTransform3D based on pixelCalibration
                AffineTransform3D quPathRescaling = new AffineTransform3D();
                System.out.println("pixelCalibrations : "+pixelCalibrations);
                if (pixelCalibrations!=null) {
                    double scaleX = 1.0;
                    double scaleY = 1.0;
                    double scaleZ = 1.0;
                  /*  System.out.println("qpOpener.getOmeMetaIdxOmeXml() : "+qpOpener.getOmeMetaIdxOmeXml());
                    System.out.println("identifier.bioformatsIndex : "+identifier.bioformatsIndex);
                    System.out.println("qpOpener.getOmeMetaIdxOmeXml().pixelPysicalSizeX : "+qpOpener.getOmeMetaIdxOmeXml().getPixelsPhysicalSizeX(identifier.bioformatsIndex));
                    System.out.println("qpOpener.getOmeMetaIdxOmeXml().pixelPysicalSizey : "+qpOpener.getOmeMetaIdxOmeXml().getPixelsPhysicalSizeY(identifier.bioformatsIndex));
                    System.out.println("qpOpener.getOmeMetaIdxOmeXml().pixelPysicalSizez : "+qpOpener.getOmeMetaIdxOmeXml().getPixelsPhysicalSizeZ(identifier.bioformatsIndex));
                    System.out.println("qpOpener.getOmeMetaIdxOmeXml().getChannelName : "+qpOpener.getOmeMetaIdxOmeXml().getChannelName(0,0));*/
                    Length[] voxSizes = BioFormatsMetaDataHelper.getSeriesVoxelSizeAsLengths(qpOpener.getOmeMetaIdxOmeXml(), identifier.bioformatsIndex);
                    if (pixelCalibrations.pixelWidth!=null) {
                        MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.pixelWidth;
                        //if (pc.unit.equals("um")) {
                        if ((voxSizes[0]!=null)&&(voxSizes[0].value(UNITS.MICROMETER)!=null)) {
                            logger.debug("xVox size = "+pc.value+" micrometer");
                            scaleX = pc.value/voxSizes[0].value(UNITS.MICROMETER).doubleValue();
                        } else {
                            Length defaultxPix = new Length(1, BioFormatsMetaDataHelper.getUnitFromString(guiparams.getUnit()));
                            scaleX = pc.value / defaultxPix.value(UNITS.MICROMETER).doubleValue();
                            logger.debug("rescaling x");
                        }
                        /*} else {
                            logger.warn("Unrecognized unit in QuPath project: "+pc.unit);
                        }*/
                    }
                    if (pixelCalibrations.pixelHeight!=null) {
                        MinimalQuPathProject.PixelCalibration pc = pixelCalibrations.pixelHeight;
                        //if (pc.unit.equals("um")) {
                        if ((voxSizes[1]!=null)&&(voxSizes[1].value(UNITS.MICROMETER)!=null)) {
                            scaleY = pc.value/voxSizes[1].value(UNITS.MICROMETER).doubleValue();
                        } else {
                            Length defaultxPix = new Length(1, BioFormatsMetaDataHelper.getUnitFromString(guiparams.getUnit()));
                            scaleY = pc.value / defaultxPix.value(UNITS.MICROMETER).doubleValue();
                            logger.debug("rescaling y");
                        }
                        /*} else {
                            logger.warn("Unrecognized unit in QuPath project: "+pc.unit);
                        }*/
                    }
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
                        }
                        /*} else {
                            logger.warn("Unrecognized unit in QuPath project: "+pc.unit);
                        }*/
                    }
                    logger.debug("ScaleX: "+scaleX+" scaleY:"+scaleY+" scaleZ:"+scaleZ);
                    final double finalScalex = scaleX;
                    final double finalScaley = scaleY;
                    final double finalScalez = scaleZ;
                    // update view Registrations
                    localSpimData.getViewRegistrations().getViewRegistrations().values().forEach(vr->{
                        if ((Math.abs(finalScalex-1.0)>0.0001)||(Math.abs(finalScaley-1.0)>0.0001)||(Math.abs(finalScalez-1.0)>0.0001))  {
                            logger.debug("Perform QuPath rescaling");
                            quPathRescaling.scale(finalScalex, finalScaley, finalScalez);
                            double oX = vr.getModel().get(0,3);
                            double oY = vr.getModel().get(1,3);
                            double oZ = vr.getModel().get(2,3);
                            vr.getModel().preConcatenate(quPathRescaling);
                            vr.getModel().set(oX, 0,3);
                            vr.getModel().set(oY, 1,3);
                            vr.getModel().set(oZ, 2,3);
                        }
                    });
                }
                // update spimdata
                spimDataMap.replace(spimUri,spimDataMap.get(spimUri), localSpimData);
            });

            // get the longest time serie
            List<TimePoint> newListOfTimePoint = new ArrayList<>();
            int lastSize = -1;

            System.out.println("Spimdatamap : "+spimDataMap);
            System.out.println("Spimdatamap key : "+spimDataMap.keySet());
            System.out.println("Spimdatamap values : "+spimDataMap.values());
            for(AbstractSpimData spData:spimDataMap.values()) {
                SpimData spd = (SpimData)spData;
                if(spd.getSequenceDescription().getTimePoints().getTimePointsOrdered().size() > lastSize) {
                    lastSize = spd.getSequenceDescription().getTimePoints().getTimePointsOrdered().size();
                    newListOfTimePoint = spd.getSequenceDescription().getTimePoints().getTimePointsOrdered();
                }
            }

            System.out.println("size of timepoints : "+lastSize);

            List<ViewSetup> newViewSetups = new ArrayList<>();
            List<ViewRegistration> newRegistrations = new ArrayList<>();
            List<ViewId> newMissingViews = new ArrayList<>();
            int i = 0;

            // create one spimdata by merging all previous spim data
            for(AbstractSpimData spData:spimDataMap.values()) {
                SpimData spd = (SpimData)spData;
                System.out.println("spd.getSequenceDescription().getViewSetups().values() : "+spd.getSequenceDescription().getViewSetups().values());
                for(ViewSetup viewSetup: spd.getSequenceDescription().getViewSetups().values()) {
                    ViewSetup newViewSetup = new ViewSetup(i, viewSetup.getName(), viewSetup.getSize(), viewSetup.getVoxelSize(), viewSetup.getTile(), viewSetup.getChannel(), viewSetup.getAngle(), viewSetup.getIllumination());
                    Map<String, Entity> attributes = viewSetup.getAttributes();
                    attributes.values().forEach(newViewSetup::setAttribute);
                    newViewSetups.add(newViewSetup);

                    for(TimePoint iTp : newListOfTimePoint) {
                        if (iTp.getId() < spd.getSequenceDescription().getTimePoints().getTimePointsOrdered().size()/*spd.getViewRegistrations().getViewRegistrationsOrdered().size()*/) {
                            newRegistrations.add(new ViewRegistration(iTp.getId(), i, spd.getViewRegistrations().getViewRegistration(0,0).getModel()));
                        } else {
                            newMissingViews.add(new ViewId(iTp.getId(), i));
                        }
                    }
                    i++;
                }
            }

            SequenceDescription sd = new SequenceDescription( new TimePoints( newListOfTimePoint ), newViewSetups , null, new MissingViews(newMissingViews));
            sd.setImgLoader(new QuPathImageLoader(quPathProject, new ArrayList<>(uriToOpener.values()), sd,2, 4));

            System.out.println("newRegistrations.size() : "+newRegistrations.size());

            final SpimData newSpimData = new SpimData( null, sd, new ViewRegistrations( newRegistrations ) );

           System.out.println("newSpimData spuri : "+newSpimData);
            System.out.println("newSpimData TimePoints : "+newSpimData.getSequenceDescription().getTimePoints().getTimePointsOrdered());
            System.out.println("newSpimData ViewSetups ");
            newSpimData.getSequenceDescription().getViewSetupsOrdered().forEach(e->System.out.println(e.getName()));
            System.out.println("newSpimData MissingValues ");
            newSpimData.getSequenceDescription().getMissingViews().getMissingViews().forEach(e->System.out.println("Missing time point : "+e.getTimePointId()+" ; ViewSetup Id  : "+e.getViewSetupId()));
            System.out.println("newSpimData ViewRegistation ");
            newSpimData.getViewRegistrations().getViewRegistrationsOrdered().forEach(e->System.out.println("Registration time point : "+e.getTimePointId()+" ; ViewSetup Id  : "+e.getViewSetupId()));


            //spimData = ss.get(0);

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
            return newSpimData;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error in try/catch spimdata");
        }

        return null;
    }

   /* private String getChannelName( IMetadata omeMeta, int iSerie, int iCh )
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
    }*/


}
