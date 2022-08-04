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
    private Object opener;
    private MinimalQuPathProject.ImageEntry image;
    private URI serverBuilderUri;
    private String providerClassName;
    private IMetadata omeMetaIdxOmeXml;
    private QuPathImageLoader.QuPathSourceIdentifier identifier;
    private Boolean canCreateOpener;
    private MinimalQuPathProject.PixelCalibrations pixelCalibrations = null;

    // getter functions
    public URI getURI(){return this.serverBuilderUri;}
    public Object getOpener(){return this.opener;}
    public QuPathImageLoader.QuPathSourceIdentifier getIdentifier(){return this.identifier;}
    public MinimalQuPathProject.PixelCalibrations getPixelCalibrations(){return this.pixelCalibrations;}
    public IMetadata getOmeMetaIdxOmeXml(){return this.omeMetaIdxOmeXml;}
    public MinimalQuPathProject.ImageEntry getImage(){return this.image;}


    /**
     * Constructor building the qupath opener
     * //TODO see what to do with guiparams
     * @param image
     * @param guiparams
     * @param indexInQuPathProject
     * @param entryID
     * @param gateway
     * @param ctx
     */
    public QuPathImageOpener(MinimalQuPathProject.ImageEntry image, GuiParams guiparams, int indexInQuPathProject, int entryID, Gateway gateway, SecurityContext ctx) {
        this.image = image;
        this.serverBuilderUri = image.serverBuilder.uri;
        this.providerClassName = image.serverBuilder.providerClassName;
        this.canCreateOpener = createOpener(guiparams, indexInQuPathProject, entryID, gateway, ctx);
    }


    /**
     * This constructor is used by XmlIoQuPathImgLoader class
     * //TODO Have a look to this class and try to fit with current QuPathImageOpener or modify QuPathImageOpener so that all the fields are filled
     * @param opener : Bioformats-BDV opener
     */
    public QuPathImageOpener(BioFormatsBdvOpener opener) {
        this.image = null;
        this.serverBuilderUri = null;
        this.providerClassName = "qupath.lib.images.servers.bioformats.BioFormatsServerBuilder";
        this.canCreateOpener = true;
        this.opener = opener;
    }


    /**
     * Build the minimal QuPath opener
     * @param guiparams : defaults parameters for all openers
     * @param indexInQuPathProject : image index in the QuPath project
     * @param entryID
     * @param gateway : logged-in gateway already connected to OMERO session
     * @param ctx : the corresponding security context
     * @return
     */
    private boolean createOpener(GuiParams guiparams, int indexInQuPathProject, int entryID, Gateway gateway, SecurityContext ctx) {
        // get the rotation angle if the image has been loaded in qupath with the rotation command
        double angleRotationZAxis = getAngleRotationZAxis(this.image);

        if (this.image.serverBuilder.builderType.equals("uri")) {
            logger.debug("URI image server");
            try {
                this.identifier = new QuPathImageLoader.QuPathSourceIdentifier();
                identifier.angleRotationZAxis = angleRotationZAxis;
                URI uri = new URI(this.serverBuilderUri.getScheme(), this.serverBuilderUri.getHost(), this.serverBuilderUri.getPath(), null);
                String filePath;

                // get the bioFormats opener
                if (this.providerClassName.equals("qupath.lib.images.servers.bioformats.BioFormatsServerBuilder")) {
                    // This appears to work more reliably than converting to a File
                    filePath = Paths.get(uri).toString();
                    BioFormatsBdvOpener bfOpener = getInitializedBioFormatsBDVOpener(filePath, guiparams).ignoreMetadata();
                    this.opener = bfOpener;
                    this.omeMetaIdxOmeXml = (IMetadata) bfOpener.getNewReader().getMetadataStore();
                }
                else {
                    // get the OMERO opener
                    if (this.providerClassName.equals("qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder")) {
                        filePath = this.serverBuilderUri.toString();
                        this.opener = getInitializedOmeroBDVOpener(filePath, guiparams, gateway, ctx).ignoreMetadata();
                        this.omeMetaIdxOmeXml = MetadataTools.createOMEXMLMetadata();
                    }
                    else {
                        logger.error("Unsupported "+this.providerClassName+" provider Class Name");
                        return false;
                    }
                }

                // fill the identifier
                this.identifier.uri = this.serverBuilderUri;
                this.identifier.sourceFile = filePath;
                this.identifier.indexInQuPathProject = indexInQuPathProject;
                this.identifier.entryID = entryID;

                int iSerie = this.image.serverBuilder.args.indexOf("--series");

                if (iSerie == -1) {
                    logger.error("Series not found in qupath project server builder!");
                    this.identifier.bioformatsIndex = 0;// was initially -1 but put to 0 because of index -1 does not exists (in QuPathToSpimData / BioFormatsMetaDataHelper.getSeriesVoxelSizeAsLengths()
                } else {
                    this.identifier.bioformatsIndex = Integer.parseInt(this.image.serverBuilder.args.get(iSerie + 1));
                }

            } catch (Exception e) {
                logger.error("URI Syntax error " + e.getMessage());
                e.printStackTrace();
            }
        }
        return true;
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
     * @param guiParams : default parameters for all openers
     * @param gateway : connected gateway
     * @param ctx
     * @return
     * @throws Exception
     */
    public OmeroSourceOpener getInitializedOmeroBDVOpener(String datalocation, GuiParams guiParams, Gateway gateway, SecurityContext ctx) throws Exception {
        Unit bfUnit = BioFormatsMetaDataHelper.getUnitFromString(guiParams.getUnit());
        Length positionReferenceFrameLength = new Length(guiParams.getRefframesizeinunitlocation(), bfUnit);
        Length voxSizeReferenceFrameLength = new Length(guiParams.getVoxSizeReferenceFrameLength(), bfUnit);

        // create the Omero opener
        OmeroSourceOpener opener = OmeroSourceOpener.getOpener().location(datalocation).ignoreMetadata();

        // flip x, y and z axis
        if (!guiParams.getFlippositionx().equals("AUTO") && guiParams.getFlippositionx().equals("TRUE")) {
            opener = opener.flipPositionX();
        }

        if (!guiParams.getFlippositiony().equals("AUTO") && guiParams.getFlippositiony().equals("TRUE")) {
            opener = opener.flipPositionY();
        }

        if (!guiParams.getFlippositionz().equals("AUTO") && guiParams.getFlippositionz().equals("TRUE")) {
            opener = opener.flipPositionZ();
        }

        // set unit length and references
        UnitsLength unit = guiParams.getUnit().equals("MILLIMETER")?UnitsLength.MILLIMETER:guiParams.getUnit().equals("MICROMETER")?UnitsLength.MICROMETER:guiParams.getUnit().equals("NANOMETER")?UnitsLength.NANOMETER:null;
        opener = opener.unit(unit);
        opener = opener.positionReferenceFrameLength(positionReferenceFrameLength);
        opener = opener.voxSizeReferenceFrameLength(voxSizeReferenceFrameLength);

        // split RGB channels
        if (guiParams.getSplitChannels()) {
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
     * @param guiParams : default parameters for all openers
     * @return
     * @throws Exception
     */
    public BioFormatsBdvOpener getInitializedBioFormatsBDVOpener(String datalocation, GuiParams guiParams) {
        Unit bfUnit = BioFormatsMetaDataHelper.getUnitFromString(guiParams.getUnit());
        Length positionReferenceFrameLength = new Length(guiParams.getRefframesizeinunitlocation(), bfUnit);
        Length voxSizeReferenceFrameLength = new Length(guiParams.getVoxSizeReferenceFrameLength(), bfUnit);

        // create the bioformats opener
        BioFormatsBdvOpener opener = BioFormatsBdvOpener.getOpener()
                .location(datalocation)
                .ignoreMetadata();

        // Switch channels and Z axis
        if (!guiParams.getSwitchzandc().equals("AUTO")) {
            opener = opener.switchZandC(guiParams.getSwitchzandc().equals("TRUE"));
        }

        // configure cache block size
        if (!guiParams.getUsebioformatscacheblocksize()) {
            opener = opener.cacheBlockSize(guiParams.getCachesizex(), guiParams.getCachesizey(), guiParams.getCachesizez());
        }

        // configure the coordinates origin convention
        if (!guiParams.getPositoniscenter().equals("AUTO")) {
            if (guiParams.getPositoniscenter().equals("TRUE")) {
                opener = opener.centerPositionConvention();
            } else {
                opener = opener.cornerPositionConvention();
            }
        }

        // flip x,y and z axis
        if (!guiParams.getFlippositionx().equals("AUTO") && guiParams.getFlippositionx().equals("TRUE")) {
            opener = opener.flipPositionX();
        }

        if (!guiParams.getFlippositiony().equals("AUTO") && guiParams.getFlippositiony().equals("TRUE")) {
            opener = opener.flipPositionY();
        }

        if (!guiParams.getFlippositionz().equals("AUTO") && guiParams.getFlippositionz().equals("TRUE")) {
            opener = opener.flipPositionZ();
        }

        // set unit length
        opener = opener.unit(bfUnit);
        opener = opener.positionReferenceFrameLength(positionReferenceFrameLength);
        opener = opener.voxSizeReferenceFrameLength(voxSizeReferenceFrameLength);

        // split channels
        if (guiParams.getSplitChannels()) {
            opener = opener.splitRGBChannels();
        }
        return opener;
    }
}

