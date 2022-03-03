package ch.epfl.biop.sourceandconverter.exporter;

import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.LUT;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.meta.IPyramidStore;
import loci.formats.out.PyramidOMETiffWriter;
import loci.formats.tiff.IFD;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RealPoint;
import net.imglib2.display.ColorConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.Color;
import ome.xml.model.primitives.PositiveInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

// Check this: https://github.com/BIOP/bigdataviewer-bioformats/commit/2da58e7994f80a0deb66a2c8722fd26ecf357651
// DRAFT...

public class OMETiffExporter {

    private static Logger logger = LoggerFactory.getLogger(OMETiffExporter.class);

    final long tileX, tileY;
    final int nResolutionLevels;
    final int downscalingFactor;
    final File file;
    final AlphaFusedResampledSource[] sources;
    final String name;
    final ColorConverter[] converters;
    final VoxelDimensions voxelDimensions;
    final String compression;

    public OMETiffExporter(AlphaFusedResampledSource[] sources,
                           ColorConverter[] converters,
                           VoxelDimensions voxelDimensions,
                           File file,
                           String compression,
                           String name) {
        AlphaFusedResampledSource model = sources[0];
        tileX = model.getCacheX();
        tileY = model.getCacheY();
        nResolutionLevels = model.getNumMipmapLevels();
        if (nResolutionLevels>1) {
            Source srcModel = model.getModelResamplerSource();
            // STRONG ASSERTION
            downscalingFactor = Math.round(srcModel.getSource(0,0).max(0)/srcModel.getSource(0,1).max(0));
            System.out.println("Downscaling factor = "+downscalingFactor);
        } else {
            downscalingFactor = -1;
        }
        this.file = file;
        this.sources = sources;
        this.name = name;
        this.converters = converters;
        this.voxelDimensions = voxelDimensions;
        this.compression = compression;
    }

    public void process() throws Exception {

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
        AlphaFusedResampledSource model = sources[0];

        NumericType pixelType = model.getType();

        if (pixelType instanceof UnsignedShortType) {
            omeMeta.setPixelsType(PixelType.UINT8, series);
        } else if (pixelType instanceof UnsignedByteType) {
            omeMeta.setPixelsType(PixelType.UINT16, series);
        } else if (pixelType instanceof FloatType) {
            omeMeta.setPixelsType(PixelType.FLOAT, series);
        } else if (pixelType instanceof ARGBType) {
            isRGB = true;
            throw new UnsupportedOperationException("Unhandled RGB bit depth pixel.");
        } else {
            throw new UnsupportedOperationException("Unhandled pixel type class: "+pixelType.getClass().getName());
        }

        omeMeta.setPixelsBigEndian(!isLittleEndian, 0);

        int width = (int) model.getSource(0,0).max(0);
        int height = (int) model.getSource(0,0).max(1);

        int sizeZ = (int) model.getSource(0,0).max(2);
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

        Unit<Length> unit = getUnitFromCalibration(voxelDimensions.unit());
        omeMeta.setPixelsPhysicalSizeX(new Length(voxelDimensions.dimension(0), unit), series);
        omeMeta.setPixelsPhysicalSizeY(new Length(voxelDimensions.dimension(1), unit), series);
        omeMeta.setPixelsPhysicalSizeZ(new Length(voxelDimensions.dimension(2), unit), series);
        // set Origin in XYZ
        RealPoint origin = new RealPoint(3);
        AffineTransform3D transform3D = new AffineTransform3D();
        model.getModelResamplerSource().getSourceTransform(0,0, transform3D);
        transform3D.apply(origin, origin);
        // TODO : check if enough or other planes need to be set ?
        omeMeta.setPlanePositionX(new Length(origin.getDoublePosition(0), unit),0,0);
        omeMeta.setPlanePositionY(new Length(origin.getDoublePosition(1), unit),0,0);
        omeMeta.setPlanePositionZ(new Length(origin.getDoublePosition(2), unit),0,0);

        for (int i= 0;i<nResolutionLevels-1;i++) {
            double divScale = Math.pow(downscalingFactor, i + 1);
            ((IPyramidStore)omeMeta).setResolutionSizeX(new PositiveInteger((int)(width / divScale)),series, i + 1);
            ((IPyramidStore)omeMeta).setResolutionSizeY(new PositiveInteger((int)(height / divScale)),series, i + 1);
        }

        // setup writer
        PyramidOMETiffWriter writer = new PyramidOMETiffWriter();

        writer.setWriteSequentially(true); // Setting this to false can be problematic!

        writer.setMetadataRetrieve(omeMeta);
        writer.setBigTiff(true);
        writer.setId(file.getAbsolutePath());
        writer.setSeries(0);
        writer.setCompression(compression);


        // generate downsampled resolutions and write to output
        for (int r = 0; r < nResolutionLevels; r++) {
            logger.debug("Saving resolution size " + r);
            writer.setResolution(r);

            for (int t=0;t<sizeT;t++) {
                for (int z=0;z<sizeZ;z++) {
                    for (int c=0;c<sizeC;c++) {

                        /*ImageProcessor processor;

                        if (image.getStack()==null) {
                            processor = image.getProcessor();
                        } else {
                            processor = image.getStack().getProcessor(image.getStackIndex(c+1, z+1, t+1));
                        }*/

                        if (r!=0) {
                            Integer x = ((IPyramidStore)omeMeta).getResolutionSizeX(0, r).getValue();
                            Integer y = ((IPyramidStore)omeMeta).getResolutionSizeY(0, r).getValue();
                            //processor.setInterpolationMethod(ImageProcessor.BILINEAR);
                            //processor = processor.resize(x,y);
                        }

                        int plane = t * sizeZ * sizeC + z * sizeC + c;

                        // You'd better keep these three lines to avoid an annoying Windows related issue
                        IFD ifd = new IFD();
                        /*ifd.putIFDValue(IFD.TILE_WIDTH, processor.getWidth());
                        ifd.putIFDValue(IFD.TILE_LENGTH, processor.getHeight());

                        AlphaFusedResampledSource

                        writer.saveBytes(plane, processorToBytes(processor, processor.getWidth()*processor.getHeight()), ifd);*/
                    }
                }
            }
        }
        writer.close();

        throw new UnsupportedOperationException("NOT IMPLEMENTED YET");

    }

    public static Unit<Length> getUnitFromCalibration(String unit) {
        switch (unit) {
            case "um":
            case "\u03BCm":
            case "\u03B5m":
            case "µm":
            case "micrometer":
                return UNITS.MICROMETER;
            case "mm":
            case "millimeter":
                return UNITS.MILLIMETER;
            case "cm":
            case "centimeter":
                return UNITS.CENTIMETER;
            case "m":
            case "meter":
                return UNITS.METRE;
            default:
                return UNITS.REFERENCEFRAME;
        }
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

}
