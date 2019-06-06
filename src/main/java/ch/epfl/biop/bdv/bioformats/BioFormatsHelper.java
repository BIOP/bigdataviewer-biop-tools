package ch.epfl.biop.bdv.bioformats;

import loci.formats.ImageReader;
import loci.formats.meta.IMetadata;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.xml.model.enums.PixelType;

import java.util.logging.Logger;

/**
 * Inspired from https://github.com/qupath/qupath-bioformats-extension/blob/master/src/main/java/qupath/lib/images/servers/BioFormatsImageServer.java
 */

public class BioFormatsHelper {


    private static final Logger LOGGER = Logger.getLogger( BioFormatsHelper.class.getName() );

    /**
     * Manager to help keep multithreading under control.
     */

    public int nDimensions;
    public boolean isRGB;
    public int nLevels;
    public int nChannels;

    //final VoxelDimensions voxelsDimensions;

    //final int numDimensions;

    final boolean is8bits;

    final boolean is16bits;

    final boolean is24bitsRGB;


    //public double pXmm, pYmm, pZmm, dXmm, dYmm, dZmm;

    public BioFormatsHelper(ImageReader reader, int image_index) throws Exception {

        final IMetadata omeMeta = (IMetadata) reader.getMetadataStore();
        reader.setSeries(image_index);
        is24bitsRGB = (reader.isRGB());//omeMeta.getPixelsType(image_index) == PixelType.UINT8)&&(omeMeta.getChannelSamplesPerPixel(image_index, 0) == PositiveInteger.valueOf("3"));
        is8bits = (omeMeta.getPixelsType(image_index) == PixelType.UINT8)&&(!is24bitsRGB);
        is16bits = (omeMeta.getPixelsType(image_index) == PixelType.UINT16)&&(!is24bitsRGB);
        /*System.out.println("getchannelsamples="+omeMeta.getChannelSamplesPerPixel(image_index, 0));
        System.out.println("getchannelcount="+omeMeta.getChannelCount(image_index));
        System.out.println("reader.getImageCount()="+reader.getImageCount());
        System.out.println("reader.isRGB()="+reader.isRGB());
        System.out.println("reader.getOptimalTileHeight()="+reader.getOptimalTileHeight());
        System.out.println("reader.getOptimalTileWidth()="+reader.getOptimalTileWidth());
        System.out.println("reader.isInterleaved()="+reader.isInterleaved());*/

        final Length physSizeZ = omeMeta.getPixelsPhysicalSizeZ(image_index);

        double pXmm, pYmm, pZmm, dXmm, dYmm, dZmm;

        pXmm = omeMeta.getPlanePositionX(image_index, 0).value(UNITS.MILLIMETER).doubleValue();
        pYmm = omeMeta.getPlanePositionY(image_index, 0).value(UNITS.MILLIMETER).doubleValue();

        dXmm = omeMeta.getPixelsPhysicalSizeX(image_index).value(UNITS.MILLIMETER).doubleValue();
        dYmm = omeMeta.getPixelsPhysicalSizeY(image_index).value(UNITS.MILLIMETER).doubleValue();

        if (physSizeZ==null) {
            pZmm=0;
            dZmm=0;
        } else {
            pZmm = omeMeta.getPlanePositionZ(image_index, 0).value(UNITS.MILLIMETER).doubleValue();
            dZmm = omeMeta.getPixelsPhysicalSizeZ(image_index).value(UNITS.MILLIMETER).doubleValue();
        }

        //System.out.println("["+pXmm+","+pYmm+","+pZmm+"]");

        //System.out.println("["+dXmm+","+dYmm+","+dZmm+"]");


    }

    public String toString() {
        String str = "Img \n";
        str+="nDimensions = "+nDimensions;

        return str;
    }
}
