package ch.epfl.biop.sourceandconverter.exporter;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.meta.IPyramidStore;
import loci.formats.out.PyramidOMETiffWriter;
import loci.formats.tiff.IFD;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.display.ColorConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.Color;
import ome.xml.model.primitives.PositiveInteger;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

public class OMETiffExporter {

    private static Logger logger = LoggerFactory.getLogger(OMETiffExporter.class);

    final long tileX, tileY;
    final int nResolutionLevels;
    final File file;
    final Source[] sources;
    final String name;
    final ColorConverter[] converters;
    final Unit<Length> unit;
    final String compression;
    final AtomicLong writtenTiles = new AtomicLong();
    long totalTiles;

    public OMETiffExporter(Source[] sources,
                           ColorConverter[] converters,
                           Unit<Length> unit,
                           File file,
                           int tileX,
                           int tileY,
                           String compression,
                           String name) {
        Source model = sources[0];
        this.tileX = tileX;
        this.tileY = tileY;
        nResolutionLevels = model.getNumMipmapLevels();
        this.unit = unit;
        this.file = file;
        this.sources = sources;
        this.name = name;
        this.converters = converters;
        this.compression = compression;
        writtenTiles.set(0);
        totalTiles = 1; // To avoid divisions by zero
    }

    public void export() throws Exception {

        // Copy metadata from ImagePlus:
        IMetadata omeMeta = MetadataTools.createOMEXMLMetadata();

        boolean isLittleEndian = false;
        boolean isRGB = false;
        boolean isInterleaved = false;
        int nChannels = sources.length;

        int series = 0;
        omeMeta.setImageID("Image:"+series, series);
        omeMeta.setPixelsID("Pixels:"+series, series);
        omeMeta.setImageName(name, series);

        omeMeta.setPixelsDimensionOrder(DimensionOrder.XYCZT, series);
        Source model = sources[0];

        if (!(model.getType() instanceof NumericType)) throw new UnsupportedOperationException("Can'r export pixel type "+model.getType().getClass());

        NumericType pixelType = (NumericType) model.getType();

        if (pixelType instanceof UnsignedShortType) {
            omeMeta.setPixelsType(PixelType.UINT16, series);
        } else if (pixelType instanceof UnsignedByteType) {
            omeMeta.setPixelsType(PixelType.UINT8, series);
        } else if (pixelType instanceof FloatType) {
            omeMeta.setPixelsType(PixelType.FLOAT, series);
        } else if (pixelType instanceof ARGBType) {
            isRGB = true;
            throw new UnsupportedOperationException("Unhandled RGB bit depth pixel.");
        } else {
            throw new UnsupportedOperationException("Unhandled pixel type class: "+pixelType.getClass().getName());
        }

        omeMeta.setPixelsBigEndian(!isLittleEndian, 0);

        int width = (int) model.getSource(0,0).max(0)+1;
        int height = (int) model.getSource(0,0).max(1)+1;

        int sizeZ = (int) model.getSource(0,0).max(2)+1;
        int sizeT = getMaxTimepoint(model);
        int sizeC = sources.length;

        // Set resolutions
        omeMeta.setPixelsSizeX(new PositiveInteger(width), series);
        omeMeta.setPixelsSizeY(new PositiveInteger(height), series);
        omeMeta.setPixelsSizeZ(new PositiveInteger(sizeZ), series);
        omeMeta.setPixelsSizeT(new PositiveInteger(sizeT), series);
        omeMeta.setPixelsSizeC(new PositiveInteger(nChannels), series);

        if (isRGB) {
            omeMeta.setChannelID("Channel:0", series, 0);
            omeMeta.setPixelsInterleaved(isInterleaved, series);
            omeMeta.setChannelSamplesPerPixel(new PositiveInteger(3), series, 0); //nSamples = 3; // TODO : check!
        } else {
            omeMeta.setChannelSamplesPerPixel(new PositiveInteger(1), series, 0);
            omeMeta.setPixelsInterleaved(isInterleaved, series);
            for (int c = 0; c < nChannels; c++) {
                omeMeta.setChannelID("Channel:0:" + c, series, c);
                // omeMeta.setChannelSamplesPerPixel(new PositiveInteger(1), series, c);
                int colorCode = converters[c].getColor().get();
                int colorRed = ARGBType.red(colorCode); //channelLUT.getRed(255);
                int colorGreen = ARGBType.green(colorCode);
                int colorBlue = ARGBType.blue(colorCode);
                int colorAlpha = ARGBType.alpha(colorCode);
                omeMeta.setChannelColor(new Color(colorRed, colorGreen, colorBlue, colorAlpha), series, c);
                omeMeta.setChannelName("Channel_"+c, series, c);
            }
        }

        AffineTransform3D mat = new AffineTransform3D();

        model.getSourceTransform(0,0, mat);

        double[] m = mat.getRowPackedCopy();
        double[] voxelSizes = new double[3];

        for(int d = 0; d < 3; ++d) {
            voxelSizes[d] = Math.sqrt(m[d] * m[d] + m[d + 4] * m[d + 4] + m[d + 8] * m[d + 8]);
        }

        omeMeta.setPixelsPhysicalSizeX(new Length(voxelSizes[0], unit), series);
        omeMeta.setPixelsPhysicalSizeY(new Length(voxelSizes[1], unit), series);
        omeMeta.setPixelsPhysicalSizeZ(new Length(voxelSizes[2], unit), series);
        // set Origin in XYZ
        RealPoint origin = new RealPoint(3);
        AffineTransform3D transform3D = new AffineTransform3D();
        model.getSourceTransform(0,0, transform3D);
        transform3D.apply(origin, origin);
        // TODO : check if enough or other planes need to be set ?
        omeMeta.setPlanePositionX(new Length(origin.getDoublePosition(0), unit),0,0);
        omeMeta.setPlanePositionY(new Length(origin.getDoublePosition(1), unit),0,0);
        omeMeta.setPlanePositionZ(new Length(origin.getDoublePosition(2), unit),0,0);

        for (int i= 0;i<nResolutionLevels-1;i++) {
            ((IPyramidStore)omeMeta).setResolutionSizeX(new PositiveInteger((int) model.getSource(0,i+1).max(0)+1),series, i + 1);
            ((IPyramidStore)omeMeta).setResolutionSizeY(new PositiveInteger((int) model.getSource(0,i+1).max(1)+1),series, i + 1);
        }

        // setup writer
        PyramidOMETiffWriter writer = new PyramidOMETiffWriter();

        writer.setWriteSequentially(true); // Setting this to false can be problematic!

        writer.setMetadataRetrieve(omeMeta);
        writer.setBigTiff(true);
        writer.setId(file.getAbsolutePath());
        writer.setSeries(0);
        writer.setCompression(compression);//TODO : understand why LZW compression does not work!!!
        writer.setTileSizeX((int)tileX);
        writer.setTileSizeY((int)tileY);

        totalTiles = 0;

        for (int r = 0; r < nResolutionLevels; r++) {
            logger.debug("Saving resolution size " + r);
            writer.setResolution(r);
            int nXTiles;
            int nYTiles;
            int maxX, maxY;
            if (r!=0) {
                maxX = ((IPyramidStore)omeMeta).getResolutionSizeX(0,r).getValue();
                maxY = ((IPyramidStore)omeMeta).getResolutionSizeY(0,r).getValue();
            } else {
                maxX = width;
                maxY = height;
            }
            nXTiles = (int) Math.ceil(maxX/(double)tileX);
            nYTiles = (int) Math.ceil(maxY/(double)tileY);
            totalTiles+=nXTiles*nYTiles;
        }

        totalTiles *= sizeT*sizeC*sizeZ;

        // generate downsampled resolutions and write to output
        for (int r = 0; r < nResolutionLevels; r++) {
            logger.debug("Saving resolution size " + r);
            writer.setResolution(r);
            int nXTiles;
            int nYTiles;
            int maxX, maxY;
            if (r!=0) {
                maxX = ((IPyramidStore)omeMeta).getResolutionSizeX(0,r).getValue();
                maxY = ((IPyramidStore)omeMeta).getResolutionSizeY(0,r).getValue();
            } else {
                maxX = width;
                maxY = height;
            }
            nXTiles = (int) Math.ceil(maxX/(double)tileX);
            nYTiles = (int) Math.ceil(maxY/(double)tileY);
            for (int t=0;t<sizeT;t++) {
                for (int c=0;c<sizeC;c++) {
                    RandomAccessibleInterval<NumericType<?>> rai = sources[c].getSource(t,r);
                    for (int z=0;z<sizeZ;z++) {
                        RandomAccessibleInterval<NumericType<?>> slice = Views.hyperSlice(rai, 2, z);
                        for (int y=0; y<nYTiles; y++) {
                            for (int x=0; x<nXTiles; x++) {
                                long startX = x*tileX;
                                long startY = y*tileY;

                                long endX = (x+1)*(tileX);
                                long endY = (y+1)*(tileY);

                                if (endX>maxX) endX = maxX;
                                if (endY>maxY) endY = maxY;

                                byte[] tileByte = SourceToByteArray.raiToByteArray(
                                        Views.interval(slice, new FinalInterval(new long[]{startX,startY}, new long[]{endX-1, endY-1})),
                                        pixelType);

                                int plane = t * sizeZ * sizeC + z * sizeC + c;

                                IFD ifd = new IFD();
                                ifd.putIFDValue(IFD.TILE_WIDTH, endX-startX);
                                ifd.putIFDValue(IFD.TILE_LENGTH, endY-startY);

                                System.out.println("xmin:"+startX);
                                System.out.println("xmax:"+endX);
                                System.out.println("ymin:"+startY);
                                System.out.println("ymax:"+endY);

                                writer.saveBytes(plane, tileByte, ifd, (int)startX, (int)startY, (int)(endX-startX), (int)(endY-startY));
                                writtenTiles.incrementAndGet();
                            }
                        }
                    }
                }
            }
        }
        writer.close();
    }

    public long getTotalTiles() {
        return totalTiles;
    }

    public long getWrittenTiles() {
        return writtenTiles.get();
    }

    public static int getMaxTimepoint(Source source) {
        if (!source.isPresent(0)) {
            return 0;
        } else {
            int nFrames = 1;
            int iFrame = 1;

            int previous;
            for(previous = iFrame; iFrame < 1073741823 && source.isPresent(iFrame); iFrame *= 2) {
                previous = iFrame;
            }

            if (iFrame > 1) {
                for(int tp = previous; tp < iFrame + 1; ++tp) {
                    if (!source.isPresent(tp)) {
                        nFrames = tp;
                        break;
                    }
                }
            }
            return nFrames;
        }
    }

    public static int getMaxTimepoint(SourceAndConverter<?> sac) {
        if (!sac.getSpimSource().isPresent(0)) {
            return 0;
        } else {
            int nFrames = 1;
            int iFrame = 1;

            int previous;
            for(previous = iFrame; iFrame < 1073741823 && sac.getSpimSource().isPresent(iFrame); iFrame *= 2) {
                previous = iFrame;
            }

            if (iFrame > 1) {
                for(int tp = previous; tp < iFrame + 1; ++tp) {
                    if (!sac.getSpimSource().isPresent(tp)) {
                        nFrames = tp;
                        break;
                    }
                }
            }

            return nFrames;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        Unit unit = UNITS.MILLIMETER;
        String path;
        int tileX = Integer.MAX_VALUE; // = no tiling
        int tileY = Integer.MAX_VALUE; // = no tiling
        String compression = "Uncompressed";

        public Builder tileSize(int tileX, int tileY) {
            this.tileX = tileX;
            this.tileY = tileY;
            return this;
        }

        public Builder lzw() {
            this.compression = "LZW";
            return this;
        }

        public Builder compression(String compression) {
            this.compression = compression;
            return this;
        }

        public Builder savePath(String path) {
            this.path = path;
            return this;
        }

        public Builder millimeter() {
            this.unit = UNITS.MILLIMETER;
            return this;
        }

        public Builder micrometer() {
            this.unit = UNITS.MICROMETER;
            return this;
        }

        public Builder unit(Unit unit) {
            this.unit = unit;
            return this;
        }

        public OMETiffExporter create(SourceAndConverter... sacs) {
            if (path == null) throw new UnsupportedOperationException("Path not specified");
            Source[] sources = new Source[sacs.length];
            ColorConverter[] converters = new ColorConverter[sacs.length];

            for (int i = 0;i<sacs.length;i++) {
                sources[i] = sacs[i].getSpimSource();
                converters[i] = (ColorConverter) sacs[i].getConverter();
            }
            File f = new File(path);
            String imageName = FilenameUtils.removeExtension(f.getName());
            return new OMETiffExporter(sources, converters, unit, f, tileX, tileY, compression, imageName);
        }
    }

}
