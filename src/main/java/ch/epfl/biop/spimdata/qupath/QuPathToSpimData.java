package ch.epfl.biop.spimdata.qupath;

import ch.epfl.biop.bdv.bioformats.BioFormatsMetaDataHelper;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.export.spimdata.BioFormatsConvertFilesToSpimData;
import ch.epfl.biop.bdv.bioformats.imageloader.*;
import ch.epfl.biop.ij2command.OmeroTools;
import ch.epfl.biop.omero.imageloader.OmeroToSpimData;
import ch.epfl.biop.omero.omerosource.OmeroSourceOpener;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import omero.gateway.ServerInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.*;

/**
 * See documentation in {@link QuPathImageLoader}
 * @author Nicolas Chiaruttini, EPFL, BIOP, 2021
 * @author Rémy Dornier, EPFL, BIOP, 2022
 */

public class QuPathToSpimData {

    protected static Logger logger = LoggerFactory.getLogger(QuPathToSpimData.class);

    Map<URI, AbstractSpimData> spimDataMap = new HashMap<>();
    Map<URI, QuPathImageOpener> uriToOpener = new HashMap<>();
    Map<URI, String> uriToImageName = new HashMap<>();
    List<URI> rawURI = new ArrayList<>();
    Map<String, OmeroTools.GatewaySecurityContext> hostToGatewayCtx = new HashMap<>();


    public AbstractSpimData getSpimDataInstance(URI quPathProject, GuiParams guiparams) {

        try {
            // Deserialize the QuPath project
            JsonObject projectJson = ProjectIO.loadRawProject(new File(quPathProject));
            Gson gson = new Gson();
            MinimalQuPathProject project = gson.fromJson(projectJson, MinimalQuPathProject.class);
            logger.debug("Opening QuPath project " + project.uri);

            //IJ.log("projectJson : "+projectJson);

            project.images.forEach(image -> {
                logger.debug("Opening qupath image "+image);

                // create a QuPathOpener
                QuPathImageOpener qpOpener = new QuPathImageOpener(image, guiparams, project.images.indexOf(image));

               try {
                   // check for omero opener and ask credentials if necessary
                   if(image.serverBuilder.providerClassName.equals("qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder")) {
                       if (!hostToGatewayCtx.containsKey(image.serverBuilder.providerClassName)) {

                           // ask credentials
                           Boolean onlyCredentials = false;
                           String[] credentials = OmeroTools.getOmeroConnectionInputParameters(onlyCredentials);
                           String host = credentials[0];
                           int port = Integer.parseInt(credentials[1]);
                           String username = credentials[2];
                           String password = credentials[3];
                           credentials = new String[]{};

                           // connect to omero
                           Gateway gateway = OmeroTools.omeroConnect(host, port, username, password);
                           SecurityContext ctx = OmeroTools.getSecurityContext(gateway);
                           ctx.setServerInformation(new ServerInformation(host));

                           // add the connection to the hash map
                           OmeroTools.GatewaySecurityContext gtCtx = new OmeroTools.GatewaySecurityContext(host, port, gateway, ctx);
                           hostToGatewayCtx.put(image.serverBuilder.providerClassName, gtCtx);
                       }

                       // initialize omero opener
                       OmeroTools.GatewaySecurityContext gtCtx = hostToGatewayCtx.get(image.serverBuilder.providerClassName);
                       qpOpener.create(gtCtx.host, gtCtx.port, gtCtx.gateway,gtCtx.ctx).loadMetadata();

                   } else {
                       // initialize bioformats opener
                       qpOpener.create("",-1,null,null).loadMetadata();
                   }
               } catch (Exception e) {
                   throw new RuntimeException(e);
               }

               // get opener
               Object opener = qpOpener.getOpener();

               // create a unique URI for each image based on the name of the image file AND on the name of the serie
               URI enhancedURI;
               try {
                   // get the name of the serie (real image name)
                   String imageName = qpOpener.getOmeMetaIdxOmeXml().getImageName(qpOpener.getIdentifier().bioformatsIndex);

                   // add the name of the serie at the end of the current URI
                   if(imageName != null && !imageName.isEmpty()) {
                       enhancedURI = new URI(qpOpener.getURI().toString() + "/" +imageName);
                       uriToImageName.put(enhancedURI,imageName);
                   }else {
                       // if the name of the serie is not valid, just remove the extension
                       enhancedURI = qpOpener.getURI();
                       String[] nameWithoutExtension = image.imageName.split("\\.");
                       uriToImageName.put(enhancedURI,nameWithoutExtension[0]);
                   }
               } catch (URISyntaxException e) {
                   throw new RuntimeException(e);
               }

               // build spimdata depending on the opener class
               if(!rawURI.contains(qpOpener.getURI())) {
                   if (opener instanceof BioFormatsBdvOpener) {
                       spimDataMap.put(enhancedURI, (SpimData) (new BioFormatsConvertFilesToSpimData()).getSpimDataInstance(Collections.singletonList((BioFormatsBdvOpener) qpOpener.getOpener())));
                   } else if (opener instanceof OmeroSourceOpener) {
                       spimDataMap.put(enhancedURI, (SpimData) (new OmeroToSpimData()).getSpimDataInstance(Collections.singletonList((OmeroSourceOpener) qpOpener.getOpener())));
                   }

                   rawURI.add(qpOpener.getURI());
               }else{
                   // If the image file has more than one serie, the same spimdata is linked to all series (because one spimdata contains all series at once)
                   spimDataMap.put(enhancedURI,spimDataMap.get(spimDataMap.keySet().stream().filter(e -> e.toString().contains(qpOpener.getURI().toString())).findFirst().get()));
               }
               uriToOpener.put(enhancedURI, qpOpener);
            });

            // regroup all the spimdata in one big spimdata
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

                // update spimdata by adding attributed to viewsetups
                localSpimData.getSequenceDescription().getViewSetups().values().forEach(vss->{
                    vss.setAttribute(sn);
                    vss.setAttribute(qpentry);
                });

                // create a new AffineTransform3D based on pixelCalibration
                AffineTransform3D quPathRescaling = new AffineTransform3D();
                if (pixelCalibrations!=null) {
                    double scaleX = 1.0;
                    double scaleY = 1.0;
                    double scaleZ = 1.0;

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
            for(AbstractSpimData spData:spimDataMap.values()) {
                SpimData spd = (SpimData)spData;
                if(spd.getSequenceDescription().getTimePoints().getTimePointsOrdered().size() > lastSize) {
                    lastSize = spd.getSequenceDescription().getTimePoints().getTimePointsOrdered().size();
                    newListOfTimePoint = spd.getSequenceDescription().getTimePoints().getTimePointsOrdered();
                }
            }

            List<ViewSetup> newViewSetups = new ArrayList<>();
            List<ViewRegistration> newRegistrations = new ArrayList<>();
            List<ViewId> newMissingViews = new ArrayList<>();
            int i = 0;

            // create new viewSetups
            for(URI spURI:spimDataMap.keySet()) {
                SpimData spd = (SpimData)spimDataMap.get(spURI);
                for(ViewSetup viewSetup: spd.getSequenceDescription().getViewSetups().values()) {
                    if(viewSetup.getName().contains(uriToImageName.get(spURI))) {
                        // duplicate each new viewsetup with a new ID corresponding to the new spimdata
                        ViewSetup newViewSetup = new ViewSetup(i, viewSetup.getName(), viewSetup.getSize(), viewSetup.getVoxelSize(), viewSetup.getTile(), viewSetup.getChannel(), viewSetup.getAngle(), viewSetup.getIllumination());

                        // set attributes to the new viewsetup
                        Map<String, Entity> attributes = viewSetup.getAttributes();
                        attributes.values().forEach(newViewSetup::setAttribute);
                        newViewSetups.add(newViewSetup);

                        // create new viewRegistrations based on the new list of timepoints
                        for (TimePoint iTp : newListOfTimePoint) {
                            if (iTp.getId() < spd.getSequenceDescription().getTimePoints().getTimePointsOrdered().size()) {
                                newRegistrations.add(new ViewRegistration(iTp.getId(), i, spd.getViewRegistrations().getViewRegistration(0, 0).getModel()));
                            } else {
                                newMissingViews.add(new ViewId(iTp.getId(), i));
                            }
                        }
                        i++;
                    }
                }
            }

            // create the new sequence description and set the image loader
            SequenceDescription sd = new SequenceDescription( new TimePoints( newListOfTimePoint ), newViewSetups , null, new MissingViews(newMissingViews));
            sd.setImgLoader(new QuPathImageLoader(quPathProject, new ArrayList<>(uriToOpener.values()), sd,2, 4));

            // create the new spimdata
            final SpimData newSpimData = new SpimData( null, sd, new ViewRegistrations( newRegistrations ) );

            // disconnect the gateways
            hostToGatewayCtx.values().forEach(e-> e.gateway.disconnect());

            return newSpimData;

        } catch (Exception e) {
            e.printStackTrace();
        }

        // disconnect the gateways
        hostToGatewayCtx.values().forEach(e-> e.gateway.disconnect());

        return null;
    }
}
