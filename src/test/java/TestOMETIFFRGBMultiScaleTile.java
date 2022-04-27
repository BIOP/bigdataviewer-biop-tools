import loci.formats.ImageReader;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.out.OMETiffWriter;
import loci.formats.tiff.IFD;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;

public class TestOMETIFFRGBMultiScaleTile {

    static public void main(String... args) throws Exception {

        String path = "C:/Users/nicol/Desktop/ometiff/"; // to fill!!

        testExport(512,512,1,1,
                512,512,
                path+"rgb_source_tile_512.ome.tiff");

        copyRGBFile2(
                path+"rgb_source_tile_512.ome.tiff",
                path+"rgb_dest_tile_512.ome.tiff",
                512,512
        );

        testExport(512,512,1,1,
                256,256,
                path+"rgb_source_tile_256.ome.tiff");

        copyRGBFile2(
                path+"rgb_source_tile_256.ome.tiff",
                path+"rgb_dest_tile_256.ome.tiff",
                256,256);

        copyRGBFile2(
                path+"rgb_dest_tile_256.ome.tiff",
                path+"rgb_dest2_tile_256.ome.tiff",
                256,256);

        copyRGBFile2(
                path+"rgb_dest2_tile_256.ome.tiff",
                path+"rgb_dest3_tile_256.ome.tiff",
                512,512);

    }

    public static void testExport(int sizeX, int sizeY, int sizeZ, int sizeT,
                                  int tileX, int tileY,
                                  String path) throws Exception {

        // Copy metadata from ImagePlus:
        IMetadata omeMeta = MetadataTools.createOMEXMLMetadata();

        omeMeta.setImageID("Image:"+0, 0);
        omeMeta.setPixelsID("Pixels:"+0, 0);
        omeMeta.setImageName("Test Image", 0);
        omeMeta.setPixelsDimensionOrder(DimensionOrder.XYCZT, 0);
        omeMeta.setPixelsType(PixelType.UINT8, 0);
        omeMeta.setPixelsInterleaved(true, 0);
        omeMeta.setPixelsBigEndian(false,0);

        // Set resolutions
        omeMeta.setPixelsSizeX(new PositiveInteger(sizeX), 0);
        omeMeta.setPixelsSizeY(new PositiveInteger(sizeY), 0);
        omeMeta.setPixelsSizeZ(new PositiveInteger(sizeZ), 0);
        omeMeta.setPixelsSizeT(new PositiveInteger(sizeT), 0);
        omeMeta.setPixelsSizeC(new PositiveInteger(3), 0); // RGB

        omeMeta.setPixelsInterleaved(true, 0);
        omeMeta.setChannelID("Channel:0:" + 0, 0, 0);
        omeMeta.setChannelName("Channel_"+0, 0, 0);
        omeMeta.setChannelSamplesPerPixel(new PositiveInteger(3), 0, 0);

        // setup writer
        OMETiffWriter writer = new OMETiffWriter();
        writer.setMetadataRetrieve(omeMeta);
        writer.setId(path);
        writer.setSeries(0);
        writer.setTileSizeX(tileX);
        writer.setTileSizeY(tileY);
        writer.setInterleaved(true); // rgb, thus interleaved ?

        int nXTiles = (int) Math.ceil(sizeX/(double)tileX);
        int nYTiles = (int) Math.ceil(sizeY/(double)tileY);

        for (int t=0;t<sizeT;t++) {
            for (int z=0;z<sizeZ;z++) {
                for (int y=0; y<nYTiles; y++) {
                    for (int x=0; x<nXTiles; x++) {
                        long startX = x * tileX;
                        long startY = y * tileY;

                        long endX = (x + 1) * (tileX);
                        long endY = (y + 1) * (tileY);

                        if (endX > sizeX) endX = sizeX;
                        if (endY > sizeY) endY = sizeY;

                        int plane = t * sizeZ * 1 + z * 1 + 0; // c = 0

                        IFD ifd = new IFD();
                        ifd.putIFDValue(IFD.TILE_WIDTH, endX - startX);
                        ifd.putIFDValue(IFD.TILE_LENGTH, endY - startY);
                        ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, 2);

                        int nBytes = (int) ((endX - startX) * (endY - startY));
                        byte[] bytes = new byte[nBytes*3];
                        int sizeTileX = (int) (endX - startX);
                        int sizeTileY = (int) (endY - startY);

                        for (int yp = 0; yp < sizeTileY; yp++) {
                            for (int xp = 0; xp < sizeTileX; xp++) {
                                int addr = (yp * sizeTileX + xp) * 3;
                                bytes[addr + 0] = (byte) ((xp+startX)*255/sizeX);
                                bytes[addr + 1] = (byte) ((yp+startY)*255/sizeY);
                                bytes[addr + 2] = (byte) (125);
                            }
                        }
                        writer.saveBytes(plane, bytes, ifd, (int)startX, (int)startY, sizeTileX, sizeTileY);
                    }
                }
            }
        }
        writer.close();
    }

    public static void copyRGBFile2(
            String pathIn,
            String pathOut,
            int tileX, int tileY) throws Exception {

        IMetadata omeMetaReader = MetadataTools.createOMEXMLMetadata();
        ImageReader reader = new ImageReader();
        reader.setMetadataStore(omeMetaReader);
        reader.setId(pathIn);

        // setup writer
        OMETiffWriter writer = new OMETiffWriter();
        writer.setMetadataRetrieve(omeMetaReader);
        writer.setId(pathOut);
        writer.setSeries(0);
        writer.setTileSizeX(tileX);
        writer.setTileSizeY(tileY);
        writer.setInterleaved(false);

        int sizeX = omeMetaReader.getPixelsSizeX(0).getValue();
        int sizeY = omeMetaReader.getPixelsSizeY(0).getValue();
        int sizeZ = omeMetaReader.getPixelsSizeZ(0).getValue();

        int nXTiles = (int) Math.ceil(sizeX/(double)tileX);
        int nYTiles = (int) Math.ceil(sizeY/(double)tileY);

        for (int t=0;t<omeMetaReader.getPixelsSizeT(0).getValue();t++) {
            for (int z=0;z<omeMetaReader.getPixelsSizeZ(0).getValue();z++) {
                for (int y=0; y<nYTiles; y++) {
                    for (int x=0; x<nXTiles; x++) {

                        long startX = x * tileX;
                        long startY = y * tileY;

                        long endX = (x + 1) * (tileX);
                        long endY = (y + 1) * (tileY);

                        if (endX > sizeX) endX = sizeX;
                        if (endY > sizeY) endY = sizeY;

                        int plane = t * sizeZ * 1 + z * 1 + 0;

                        IFD ifd = new IFD();
                        ifd.putIFDValue(IFD.TILE_WIDTH, endX - startX);
                        ifd.putIFDValue(IFD.TILE_LENGTH, endY - startY);
                        ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, 2);
                        ifd.putIFDValue(IFD.PLANAR_CONFIGURATION, 1);

                        int nBytes = (int) ((endX - startX) * (endY - startY));
                        byte[] bytes = new byte[nBytes*3];
                        int sizeTileX = (int) (endX - startX);
                        int sizeTileY = (int) (endY - startY);

                        reader.openBytes(plane, bytes, (int) startX, (int) startY, sizeTileX, sizeTileY);
                        writer.saveBytes(plane, bytes, (int)startX, (int)startY, sizeTileX, sizeTileY);

                    }
                }
            }
        }
        writer.close();
        reader.close();
    }

}