import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.meta.IPyramidStore;
import loci.formats.out.PyramidOMETiffWriter;
import loci.formats.tiff.IFD;

import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;

import java.io.File;
import java.util.function.BiFunction;

public class TestOMETIFFExport {

    static public void main(String... args) throws Exception {

        String path = "C:/Users/chiarutt/Desktop/ometiff/"; // to fill!!
        testExport(1024,1024,1,1,1,4,
                128,128,
                2,new File(path+"test.ome.tiff"),
                "LZW", (x,y) -> true);

        testExport(1024,1024,1,1,1,4,
                128,128,
                2,new File(path+"test.eventiles.ome.tiff"),
                "LZW", (x,y) -> (x+y)%2==0);

        testExport(1024,1024,1,1,1,4,
                128,128,
                2,new File(path+"test.removefirsttile.ome.tiff"),
                "LZW", (x,y) -> !((x==0)&&(y==0)));

        testExport(1024,1024,1,1,1,4,
                128,128,
                2,new File(path+"test.removesecondtile.ome.tiff"),
                "LZW", (x,y) -> !((x==1)&&(y==0)));

        // this one has a problem!!
        testExport(1024,1024,1,1,1,4,
                128,128,
                2,new File(path+"test.oddtiles.ome.tiff"),
                "LZW", (x,y) -> (x+y)%2==1);

    }

    public static void testExport(int sizeX, int sizeY, int sizeZ, int sizeC, int sizeT, int sizeR,
                                  int tileX, int tileY,
                                  int downsample, File file,
                                  String compression, BiFunction<Integer,Integer,Boolean> condition) throws Exception {

        // Copy metadata from ImagePlus:
        IMetadata omeMeta = MetadataTools.createOMEXMLMetadata();

        boolean isLittleEndian = false;
        boolean isInterleaved = false;

        int series = 0;
        omeMeta.setImageID("Image:"+series, series);
        omeMeta.setPixelsID("Pixels:"+series, series);
        omeMeta.setImageName("Test Image", series);
        omeMeta.setPixelsDimensionOrder(DimensionOrder.XYCZT, series);
        omeMeta.setPixelsType(PixelType.UINT16, series);
        omeMeta.setPixelsBigEndian(!isLittleEndian, 0);

        // Set resolutions
        omeMeta.setPixelsSizeX(new PositiveInteger(sizeX), series);
        omeMeta.setPixelsSizeY(new PositiveInteger(sizeY), series);
        omeMeta.setPixelsSizeZ(new PositiveInteger(sizeZ), series);
        omeMeta.setPixelsSizeT(new PositiveInteger(sizeT), series);
        omeMeta.setPixelsSizeC(new PositiveInteger(sizeC), series);

        omeMeta.setChannelSamplesPerPixel(new PositiveInteger(1), series, 0);
        omeMeta.setPixelsInterleaved(isInterleaved, series);
        for (int c = 0; c < sizeC; c++) {
            omeMeta.setChannelID("Channel:0:" + c, series, c);
            omeMeta.setChannelName("Channel_"+c, series, c);
        }

        for (int i= 0;i<sizeR-1;i++) {
            ((IPyramidStore)omeMeta).setResolutionSizeX(new PositiveInteger((int) (sizeX/Math.pow(downsample,i+1))),series, i + 1);
            ((IPyramidStore)omeMeta).setResolutionSizeY(new PositiveInteger((int) (sizeX/Math.pow(downsample,i+1))),series, i + 1);
        }

        // setup writer
        PyramidOMETiffWriter writer = new PyramidOMETiffWriter();
        writer.setWriteSequentially(true); // Setting this to false can be problematic!
        writer.setMetadataRetrieve(omeMeta);
        writer.setBigTiff(true);
        writer.setId(file.getAbsolutePath());
        writer.setSeries(0);
        writer.setCompression(compression);
        writer.setTileSizeX(tileX);
        writer.setTileSizeY(tileY);

        // generate downsampled resolutions and write to output
        for (int r = 0; r < sizeR; r++) {
            writer.setResolution(r);
            int nXTiles;
            int nYTiles;
            int maxX, maxY;
            if (r!=0) {
                maxX = ((IPyramidStore)omeMeta).getResolutionSizeX(0,r).getValue();
                maxY = ((IPyramidStore)omeMeta).getResolutionSizeY(0,r).getValue();
            } else {
                maxX = sizeX;
                maxY = sizeY;
            }
            nXTiles = (int) Math.ceil(maxX/(double)tileX);
            nYTiles = (int) Math.ceil(maxY/(double)tileY);
            for (int t=0;t<sizeT;t++) {
                for (int c=0;c<sizeC;c++) {
                    for (int z=0;z<sizeZ;z++) {
                        for (int y=0; y<nYTiles; y++) {
                            for (int x=0; x<nXTiles; x++) {
                                long startX = x * tileX;
                                long startY = y * tileY;

                                long endX = (x + 1) * (tileX);
                                long endY = (y + 1) * (tileY);

                                if (endX > maxX) endX = maxX;
                                if (endY > maxY) endY = maxY;

                                int plane = t * sizeZ * sizeC + z * sizeC + c;

                                IFD ifd = new IFD();
                                ifd.putIFDValue(IFD.TILE_WIDTH, endX - startX);
                                ifd.putIFDValue(IFD.TILE_LENGTH, endY - startY);

                                double height = 4.0;
                                int nBytes = (int) ((endX - startX) * (endY - startY));
                                byte[] bytes = new byte[2*nBytes];
                                int sizeTileX = (int) (endX - startX);
                                int sizeTileY = (int) (endY - startY);
                                if (condition.apply(x,y)) { // Keeps tiles values to zero sometimes
                                    for (int yp = 0; yp < sizeTileY; yp++) {
                                        for (int xp = 0; xp < sizeTileX; xp++) {
                                            int addr = (yp * sizeTileX + xp) * 2;
                                            bytes[addr + 1] = (byte) ((height + height * Math.cos(3.0 * x * xp / sizeTileX)) + (height + height * Math.sin(3.0 * y * yp / sizeTileY)));
                                        }
                                    }
                                }
                                writer.saveBytes(plane, bytes, ifd, (int)startX, (int)startY, sizeTileX, sizeTileY);

                            }
                        }
                    }
                }
            }
        }
        writer.close();
    }
}