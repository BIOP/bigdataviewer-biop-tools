package ch.epfl.biop.spimdata.qupath;

import ch.epfl.biop.bdv.bioformats.BioFormatsMetaDataHelper;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.omero.omerosource.OmeroSourceOpener;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import omero.model.enums.UnitsLength;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;

public class QuPathImageOpener {

    protected static Logger logger = LoggerFactory.getLogger(QuPathImageOpener.class);
    private Object opener;
    private MinimalQuPathProject.ImageEntry image;
    private URI serverBuilderUri;
    private String providerClassName;

    private IMetadata omeMetaIdxOmeXml;

    private QuPathImageLoader.QuPathBioFormatsSourceIdentifier identifier;

    private Boolean canCreateOpener;
    private MinimalQuPathProject.PixelCalibrations pixelCalibrations = null;

    public URI getURI(){return this.serverBuilderUri;}
    public Object getOpener(){return this.opener;}
    public QuPathImageLoader.QuPathBioFormatsSourceIdentifier getIdentifier(){return this.identifier;}
    public MinimalQuPathProject.PixelCalibrations getPixelCalibrations(){return this.pixelCalibrations;}
    public IMetadata getOmeMetaIdxOmeXml(){return this.omeMetaIdxOmeXml;}

    public QuPathImageOpener(MinimalQuPathProject.ImageEntry image, GuiParams guiparams, int indexInQuPathProject, int entryID) {
        this.image = image;
        this.serverBuilderUri = image.serverBuilder.uri;
        this.providerClassName = image.serverBuilder.providerClassName;
        this.canCreateOpener = createOpener(guiparams, indexInQuPathProject, entryID);
    }


    private boolean createOpener(GuiParams guiparams, int indexInQuPathProject, int entryID) {
        double angleRotationZAxis = getAngleRotationZAxis(this.image);

        if (this.image.serverBuilder.builderType.equals("uri")) {
            logger.debug("URI image server");
            try {
                this.identifier = new QuPathImageLoader.QuPathBioFormatsSourceIdentifier();
                identifier.angleRotationZAxis = angleRotationZAxis;
                URI uri = new URI(this.serverBuilderUri.getScheme(), this.serverBuilderUri.getHost(), this.serverBuilderUri.getPath(), null);

                // This appears to work more reliably than converting to a File
                String filePath = Paths.get(uri).toString();

                if (this.providerClassName.equals("qupath.lib.images.servers.bioformats.BioFormatsServerBuilder")) {
                    BioFormatsBdvOpener bfOpener = getInitializedBioFormatsBDVOpener(filePath, guiparams).ignoreMetadata();
                    this.opener = bfOpener;
                    this.omeMetaIdxOmeXml = (IMetadata) bfOpener.getNewReader().getMetadataStore();
                } else {
                    if (this.providerClassName.equals("qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder")) {
                        this.opener = getInitializedOmeroBDVOpener(filePath, guiparams).ignoreMetadata();
                        this.omeMetaIdxOmeXml = MetadataTools.createOMEXMLMetadata();
                    } else {
                        return false;
                    }
                }

                this.identifier.sourceFile = filePath;
                this.identifier.indexInQuPathProject = indexInQuPathProject;
                this.identifier.entryID = entryID;

                int iSerie = this.image.serverBuilder.args.indexOf("--series");

                if (iSerie == -1) {
                    logger.error("Series not found in qupath project server builder!");
                    this.identifier.bioformatsIndex = -1;
                } else {
                    this.identifier.bioformatsIndex = Integer.parseInt(this.image.serverBuilder.args.get(iSerie + 1));
                }

            } catch (URISyntaxException e) {
                logger.error("URI Syntax error " + e.getMessage());
                e.printStackTrace();
            }
        }

        return true;
    }

    public QuPathImageOpener PixelCalibration(){
        if (this.image.serverBuilder != null)
            if (this.image.serverBuilder.metadata != null)
                this.pixelCalibrations = this.image.serverBuilder.metadata.pixelCalibration;
        return this;
    }

    private double getAngleRotationZAxis(MinimalQuPathProject.ImageEntry image) {
        // If the image was imported in qupath with a rotation
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


    public OmeroSourceOpener getInitializedOmeroBDVOpener(String datalocation, GuiParams guiParams) {
        Unit bfUnit = BioFormatsMetaDataHelper.getUnitFromString(guiParams.getUnit());
        Length positionReferenceFrameLength = new Length(guiParams.getRefframesizeinunitlocation(), bfUnit);
        Length voxSizeReferenceFrameLength = new Length(guiParams.getVoxSizeReferenceFrameLength(), bfUnit);

        OmeroSourceOpener opener = OmeroSourceOpener.getOpener()
                .location(datalocation)
                //.auto()
                .ignoreMetadata();

        if (!guiParams.getFlippositionx().equals("AUTO") && guiParams.getFlippositionx().equals("TRUE")) {
            opener = opener.flipPositionX();
        }

        if (!guiParams.getFlippositiony().equals("AUTO") && guiParams.getFlippositiony().equals("TRUE")) {
            opener = opener.flipPositionY();
        }

        if (!guiParams.getFlippositionz().equals("AUTO") && guiParams.getFlippositionz().equals("TRUE")) {
            opener = opener.flipPositionZ();
        }
        UnitsLength unit = guiParams.getUnit().equals("MILLIMETER")?UnitsLength.MILLIMETER:guiParams.getUnit().equals("MICROMETER")?UnitsLength.MICROMETER:guiParams.getUnit().equals("NANOMETER")?UnitsLength.NANOMETER:null;
        opener = opener.unit(unit);
        opener = opener.positionReferenceFrameLength(positionReferenceFrameLength);
        opener = opener.voxSizeReferenceFrameLength(voxSizeReferenceFrameLength);
        if (guiParams.getSplitChannels()) {
            opener = opener.splitRGBChannels();
        }

        return opener;
    }

    public BioFormatsBdvOpener getInitializedBioFormatsBDVOpener(String datalocation, GuiParams guiParams) {
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

}

