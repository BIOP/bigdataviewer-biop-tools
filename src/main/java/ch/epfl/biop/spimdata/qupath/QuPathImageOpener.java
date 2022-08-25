package ch.epfl.biop.spimdata.qupath;

import ch.epfl.biop.bdv.bioformats.BioFormatsMetaDataHelper;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.omero.omerosource.OmeroSourceOpener;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import ome.xml.model.primitives.Color;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import omero.model.enums.UnitsLength;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Paths;
import java.util.List;


/**
 * QuPath Image Opener. This class builds a specific opener depending on the image provider class
 * that is used to convert QuPath data into BDV compatible data
 *
 * There are some limitations: only bioformats image server, rotated image server
 * and omero-raw image server are supported ( among probably other limitations ).
 *
 * Also, editing files in the QuPath project after it has been converted to an xml bdv dataset
 * is not guaranteed to work.
 *
 * @author Rémy Dornier, EPFL, BIOP, 2022
 * @author Nicolas Chiaruttini, EPFL, BIOP, 2021
 */
public class QuPathImageOpener {

    protected static Logger logger = LoggerFactory.getLogger(QuPathImageOpener.class);
    transient private Object opener;
    transient private IMetadata omeMetaIdxOmeXml;
    transient private QuPathImageLoader.QuPathSourceIdentifier identifier;
    transient private MinimalQuPathProject.PixelCalibrations pixelCalibrations = null;
    transient private int seriesCount;
    private MinimalQuPathProject.ImageEntry image;
    private GuiParams defaultParams;
    private int indexInQuPathProject;
    private String host;
    private int port;



    // getter functions
    public URI getURI(){return this.image.serverBuilder.uri;}
    public Object getOpener(){return this.opener;}
    public QuPathImageLoader.QuPathSourceIdentifier getIdentifier(){return this.identifier;}
    public MinimalQuPathProject.PixelCalibrations getPixelCalibrations(){return this.pixelCalibrations;}
    public IMetadata getOmeMetaIdxOmeXml(){return this.omeMetaIdxOmeXml;}
    public MinimalQuPathProject.ImageEntry getImage(){return this.image;}
    public GuiParams getDefaultParams(){return this.defaultParams;}
    public int getSeriesCount(){return this.seriesCount;}
    public String getHost(){return this.host;}
    public int getPort(){return this.port;}

    /**
     * Constructor building the qupath opener
     * //TODO see what to do with guiparams
     * @param image
     * @param guiparams
     * @param indexInQuPathProject
     */
    public QuPathImageOpener(MinimalQuPathProject.ImageEntry image, GuiParams guiparams, int indexInQuPathProject) {
        this.image = image;
        this.indexInQuPathProject = indexInQuPathProject;
        this.defaultParams = guiparams;
    }

    public QuPathImageOpener create(String host, int port, Gateway gateway, SecurityContext ctx){
        this.host = host;
        this.port = port;

        // get the rotation angle if the image has been loaded in qupath with the rotation command
        double angleRotationZAxis = getAngleRotationZAxis(this.image);

        if (this.image.serverBuilder.builderType.equals("uri")) {
            logger.debug("URI image server");

            try {
                this.identifier = new QuPathImageLoader.QuPathSourceIdentifier();
                this.identifier.angleRotationZAxis = angleRotationZAxis;
                URI uri = new URI(this.image.serverBuilder.uri.getScheme(), this.image.serverBuilder.uri.getHost(), this.image.serverBuilder.uri.getPath(), null);
                String filePath;

                // create openers
                if (this.image.serverBuilder.providerClassName.equals("qupath.lib.images.servers.bioformats.BioFormatsServerBuilder")) {
                    // This appears to work more reliably than converting to a File
                    filePath = Paths.get(uri).toString();

                    BioFormatsBdvOpener bfOpener = getInitializedBioFormatsBDVOpener(filePath).ignoreMetadata();
                    this.opener = bfOpener;
                    this.seriesCount = bfOpener.getNewReader().getSeriesCount();
                    this.omeMetaIdxOmeXml = (IMetadata) bfOpener.getNewReader().getMetadataStore();
                }
                else {
                    if (this.image.serverBuilder.providerClassName.equals("qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder")) {
                        filePath = this.image.serverBuilder.uri.toString();
                        this.opener = getInitializedOmeroBDVOpener(filePath, gateway, ctx).ignoreMetadata();
                        this.seriesCount = 1;
                        this.omeMetaIdxOmeXml = MetadataTools.createOMEXMLMetadata();
                    }
                    else {
                        logger.error("Unsupported "+this.image.serverBuilder.providerClassName+" provider Class Name");
                        System.out.println("Unsupported " + this.image.serverBuilder.providerClassName + " provider Class Name");
                        return this;
                    }
                 }

                // fill the identifier
                this.identifier.uri = this.image.serverBuilder.uri;
                this.identifier.sourceFile = filePath;
                this.identifier.indexInQuPathProject = this.indexInQuPathProject;
                this.identifier.entryID = this.image.entryID;

                // get bioformats serie number
                int iSerie = this.image.serverBuilder.args.indexOf("--series");

                if (iSerie == -1) {
                    logger.error("Series not found in qupath project server builder!");
                    this.identifier.bioformatsIndex = 0;// was initially -1 but put to 0 because of index -1 does not exists (in QuPathToSpimData / BioFormatsMetaDataHelper.getSeriesVoxelSizeAsLengths()
                } else {
                    this.identifier.bioformatsIndex = Integer.parseInt(this.image.serverBuilder.args.get(iSerie + 1));
                }

            } catch (Exception e) {
                logger.error("URI Syntax error " + e.getMessage());
                System.out.println("URI Syntax error " + e.getMessage());
                e.printStackTrace();
            }
        }
        return this;
    }

    /**
     * Fill the opener metadata with QuPath metadata
     * @return this object
     */
    public QuPathImageOpener loadMetadata(){
        if (this.image.serverBuilder != null) {
            // if metadata is null, it means that the image has been imported using BioFormats
            if (this.image.serverBuilder.metadata != null) {
                MinimalQuPathProject.PixelCalibrations pixelCalibration = this.image.serverBuilder.metadata.pixelCalibration;
                this.pixelCalibrations = pixelCalibration;

                if (pixelCalibration != null) {
                    // fill pixels size and unit
                    this.omeMetaIdxOmeXml.setPixelsPhysicalSizeX(new Length(pixelCalibration.pixelWidth.value, convertStringToUnit(pixelCalibration.pixelWidth.unit)), 0);
                    this.omeMetaIdxOmeXml.setPixelsPhysicalSizeY(new Length(pixelCalibration.pixelHeight.value, convertStringToUnit(pixelCalibration.pixelHeight.unit)), 0);
                    this.omeMetaIdxOmeXml.setPixelsPhysicalSizeZ(new Length(pixelCalibration.zSpacing.value, convertStringToUnit(pixelCalibration.zSpacing.unit)), 0);

                    // fill channels' name and color
                    List<MinimalQuPathProject.ChannelInfo> channels = this.image.serverBuilder.metadata.channels;
                    for (int i = 0; i < channels.size(); i++) {
                        this.omeMetaIdxOmeXml.setChannelName(channels.get(i).name, 0, i);
                        this.omeMetaIdxOmeXml.setChannelColor(new Color(channels.get(i).color), 0, i);
                    }
                }
            }
        }
        return this;
    }


    /**
     * Convert the string unit from QuPath metadata into Unit class readable by the opener
     * @param unitString
     * @return
     */
    private Unit<Length> convertStringToUnit(String unitString){
        switch(unitString){
            case "µm" : return UNITS.MICROMETER;
            case "mm" : return UNITS.MILLIMETER;
            case "cm" : return UNITS.CENTIMETER;
            case "px" : return UNITS.PIXEL;
            default: return UNITS.REFERENCEFRAME;
        }
    }


    /**
     * get the rotation angle of the image if the image was imported in qupath with a rotation
     * @param image
     * @return
     */
    private double getAngleRotationZAxis(MinimalQuPathProject.ImageEntry image) {
        double angleRotationZAxis = 0;
        if (image.serverBuilder.builderType.equals("rotated")) {
            String angleDegreesStr = image.serverBuilder.rotation.substring(7); // "ROTATE_ANGLE" for instance "ROTATE_0", "ROTATE_270", etc
            logger.debug("Rotated image server (" + angleDegreesStr + ")");
            if (angleDegreesStr.equals("NONE")) {
                angleRotationZAxis = 0;
            } else {
                angleRotationZAxis = (Double.parseDouble(angleDegreesStr) / 180.0) * Math.PI;
            }
            MinimalQuPathProject.ServerBuilderMetadata metadata = image.serverBuilder.metadata; // To keep the metadata (pixel size for instance)
            image.serverBuilder = image.serverBuilder.builder; // Skips the rotation
            image.serverBuilder.metadata = metadata;
        }

        return angleRotationZAxis;
    }


    /**
     * create and initialize an OmeroSourceOpener object to read images from OMERO in BDV
     * @param datalocation : url of the image
     * @param gateway : connected gateway
     * @param ctx
     * @return
     * @throws Exception
     */
    public OmeroSourceOpener getInitializedOmeroBDVOpener(String datalocation, Gateway gateway, SecurityContext ctx) throws Exception {
        Unit bfUnit = BioFormatsMetaDataHelper.getUnitFromString(this.defaultParams.getUnit());
        Length positionReferenceFrameLength = new Length(this.defaultParams.getRefframesizeinunitlocation(), bfUnit);
        Length voxSizeReferenceFrameLength = new Length(this.defaultParams.getVoxSizeReferenceFrameLength(), bfUnit);

        // create the Omero opener
        OmeroSourceOpener opener = new OmeroSourceOpener().location(datalocation).ignoreMetadata();

        // flip x, y and z axis
        if (!this.defaultParams.getFlippositionx().equals("AUTO") && this.defaultParams.getFlippositionx().equals("TRUE")) {
            opener = opener.flipPositionX();
        }

        if (!this.defaultParams.getFlippositiony().equals("AUTO") && this.defaultParams.getFlippositiony().equals("TRUE")) {
            opener = opener.flipPositionY();
        }

        if (!this.defaultParams.getFlippositionz().equals("AUTO") && this.defaultParams.getFlippositionz().equals("TRUE")) {
            opener = opener.flipPositionZ();
        }

        // set unit length and references
        UnitsLength unit = this.defaultParams.getUnit().equals("MILLIMETER")?UnitsLength.MILLIMETER:this.defaultParams.getUnit().equals("MICROMETER")?UnitsLength.MICROMETER:this.defaultParams.getUnit().equals("NANOMETER")?UnitsLength.NANOMETER:null;
        opener = opener.unit(unit);
        opener = opener.positionReferenceFrameLength(positionReferenceFrameLength);
        opener = opener.voxSizeReferenceFrameLength(voxSizeReferenceFrameLength);

        // split RGB channels
        if (this.defaultParams.getSplitChannels()) {
            opener = opener.splitRGBChannels();
        }

        // set omero connection
        String[] imageString = datalocation.split("%3D");
        String[] omeroId = imageString[1].split("-");

        opener.gateway(gateway).securityContext(ctx).imageID(Long.parseLong(omeroId[1])).host(ctx.getServerInformation().getHost()).create();

        return opener;
    }


    /**
     * create and initialize an BioFormatsBdvOpener object to read images from Bioformats in BDV
     * @param datalocation : uri of the image
     * @return
     * @throws Exception
     */
    public BioFormatsBdvOpener getInitializedBioFormatsBDVOpener(String datalocation) {
        Unit bfUnit = BioFormatsMetaDataHelper.getUnitFromString(this.defaultParams.getUnit());
        Length positionReferenceFrameLength = new Length(this.defaultParams.getRefframesizeinunitlocation(), bfUnit);
        Length voxSizeReferenceFrameLength = new Length(this.defaultParams.getVoxSizeReferenceFrameLength(), bfUnit);

        // create the bioformats opener
        BioFormatsBdvOpener opener = BioFormatsBdvOpener.getOpener()
                .location(datalocation)
                .ignoreMetadata();

        // Switch channels and Z axis
        if (!this.defaultParams.getSwitchzandc().equals("AUTO")) {
            opener = opener.switchZandC(this.defaultParams.getSwitchzandc().equals("TRUE"));
        }

        // configure cache block size
        if (!this.defaultParams.getUsebioformatscacheblocksize()) {
            opener = opener.cacheBlockSize(this.defaultParams.getCachesizex(), this.defaultParams.getCachesizey(), this.defaultParams.getCachesizez());
        }

        // configure the coordinates origin convention
        if (!this.defaultParams.getPositoniscenter().equals("AUTO")) {
            if (this.defaultParams.getPositoniscenter().equals("TRUE")) {
                opener = opener.centerPositionConvention();
            } else {
                opener = opener.cornerPositionConvention();
            }
        }

        // flip x,y and z axis
        if (!this.defaultParams.getFlippositionx().equals("AUTO") && this.defaultParams.getFlippositionx().equals("TRUE")) {
            opener = opener.flipPositionX();
        }

        if (!this.defaultParams.getFlippositiony().equals("AUTO") && this.defaultParams.getFlippositiony().equals("TRUE")) {
            opener = opener.flipPositionY();
        }

        if (!this.defaultParams.getFlippositionz().equals("AUTO") && this.defaultParams.getFlippositionz().equals("TRUE")) {
            opener = opener.flipPositionZ();
        }

        // set unit length
        opener = opener.unit(bfUnit);
        opener = opener.positionReferenceFrameLength(positionReferenceFrameLength);
        opener = opener.voxSizeReferenceFrameLength(voxSizeReferenceFrameLength);

        // split channels
        if (this.defaultParams.getSplitChannels()) {
            opener = opener.splitRGBChannels();
        }
        return opener;
    }
}

