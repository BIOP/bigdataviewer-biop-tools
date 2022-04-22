package ch.epfl.biop.sourceandconverter.exporter;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import loci.formats.MetadataTools;
import loci.formats.in.DynamicMetadataOptions;
import loci.formats.meta.IMetadata;
import loci.formats.meta.IPyramidStore;
import loci.formats.out.OMETiffWriter;
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
import org.scijava.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Export an array of {@link SourceAndConverter} into a OME-TIFF file, potentially multiresolution,
 * if the original sources are multiresolution themselves.
 *
 * The array represents different channels. All Sources should have the same size in XYZCT, and
 * number of resolution levels.
 *
 * If the sources are multiresolution, a pyramidal OME-TIFF will be saved. If you want to build the
 * sub resolution levels (because you want to recompute them or because there are none,
 * please use {@link OMETiffPyramidizerExporter} instead).
 *
 * The builder {@link Builder} should be used to export the sources. (private constructor)
 *
 * Parallelization can occur at the reading level, with a number of threads set with
 * {@link Builder#nThreads(int)}. Writing to HDD is serial.
 *
 * For big 2d planes, tiled images can be saved with {@link Builder#tileSize(int, int)}
 *
 * Lzw compression possible {@link Builder#lzw()}
 *
 * This class should not be memory hungry, the number of data kept into ram should never exceed
 * (max queue size + nThreads) * tile size in bytes, even with really large dataset.
 *
 * A {@link Task} object can be given in {@link Builder#monitor(Task)}to monitor the saving and
 * also to cancel the export (TODO for cancellation).
 *
 * The RAM occupation depends on the caching mechanism (if any) in the input {@link SourceAndConverter} array.
 *
 * @author Nicolas Chiaruttini, EPFL, 2022
 */

public class OMETiffExporter {

    private static final Logger logger = LoggerFactory.getLogger(OMETiffExporter.class);

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

    final int nChannels;
    final NumericType pixelType;
    final int width, height, sizeT, sizeC, sizeZ;
    final double[] voxelSizes = new double[3];
    final RealPoint origin = new RealPoint(3);
    final Map<Integer, Integer> mapResToWidth = new HashMap<>();
    final Map<Integer, Integer> mapResToHeight = new HashMap<>();

    final Map<TileIterator.IntsKey, byte[]> computedBlocks;

    final Map<Integer, Integer> resToNY = new HashMap<>();
    final Map<Integer, Integer> resToNX = new HashMap<>();
    final TileIterator tileIterator;
    final int nThreads;
    final Task task;

    volatile Object tileLock = new Object();

    private OMETiffExporter(Source[] sources,
                           ColorConverter[] converters,
                           Unit<Length> unit,
                           File file,
                           int tileX,
                           int tileY,
                           String compression,
                           String name,
                           int nThreads,
                           int maxTilesInQueue,
                           Task task) {
        this.task = task;
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

        // Prepare = gets all dimensions
        nChannels = sources.length;

        if (!(model.getType() instanceof NumericType)) throw new UnsupportedOperationException("Can't export pixel type "+model.getType().getClass());

        pixelType = (NumericType) model.getType();

        width = (int) model.getSource(0,0).max(0)+1;
        height = (int) model.getSource(0,0).max(1)+1;

        sizeZ = (int) model.getSource(0,0).max(2)+1;
        sizeT = getMaxTimepoint(model);
        sizeC = sources.length;

        AffineTransform3D mat = new AffineTransform3D();
        model.getSourceTransform(0,0, mat);

        double[] m = mat.getRowPackedCopy();

        for(int d = 0; d < 3; ++d) {
            voxelSizes[d] = Math.sqrt(m[d] * m[d] + m[d + 4] * m[d + 4] + m[d + 8] * m[d + 8]);
        }

        AffineTransform3D transform3D = new AffineTransform3D();
        model.getSourceTransform(0,0, transform3D);
        transform3D.apply(origin, origin);

        for (int i= 0;i<nResolutionLevels-1;i++) {
            mapResToWidth.put(i+1,(int) model.getSource(0,i+1).max(0)+1);
            mapResToHeight.put(i+1,(int) model.getSource(0,i+1).max(1)+1);
        }

        // One iteration to count the number of tiles

        // some assertion : same dimensions for all  nr and c and t
        for (int r = 0; r < nResolutionLevels; r++) {
            int nXTiles;
            int nYTiles;
            int maxX, maxY;
            if (r!=0) {
                maxX = mapResToWidth.get(r);
                maxY = mapResToHeight.get(r);
            } else {
                maxX = width;
                maxY = height;
            }
            nXTiles = (int) Math.ceil(maxX/(double)tileX);
            nYTiles = (int) Math.ceil(maxY/(double)tileY);
            resToNX.put(r,nXTiles);
            resToNY.put(r,nYTiles);
        }

        tileIterator = new TileIterator(nResolutionLevels, sizeT, sizeC, sizeZ, resToNY, resToNX, maxTilesInQueue);
        this.nThreads = nThreads;
        computedBlocks = new ConcurrentHashMap<>(nThreads*3+1); // should be enough to avoiding overlap of hash
    }

    private void computeTile(TileIterator.IntsKey key) {
        int r = key.array[0];
        int t = key.array[1];
        int c = key.array[2];
        int z = key.array[3];
        int y = key.array[4];
        int x = key.array[5];

        long startX = x*tileX;
        long startY = y*tileY;

        long endX = (x+1)*(tileX);
        long endY = (y+1)*(tileY);

        int maxX, maxY;

        if (r!=0) {
            maxX = mapResToWidth.get(r);
            maxY = mapResToHeight.get(r);
        } else {
            maxX = width;
            maxY = height;
        }

        if (endX>maxX) endX = maxX;
        if (endY>maxY) endY = maxY;

        RandomAccessibleInterval<NumericType<?>> rai = sources[c].getSource(t,r);
        RandomAccessibleInterval<NumericType<?>> slice = Views.hyperSlice(rai, 2, z);
        byte[] tileByte = SourceToByteArray.raiToByteArray(
                Views.interval(slice, new FinalInterval(new long[]{startX,startY}, new long[]{endX-1, endY-1})),
                pixelType);

        computedBlocks.put(key,tileByte);
    }

    private boolean computeNextTile() {
        TileIterator.IntsKey key = null;
        synchronized (tileIterator) {
            if (tileIterator.hasNext()) {
                key = tileIterator.next();
            }
        }
        if (key == null) {
            synchronized (tileLock) {
                tileLock.notifyAll();
            }
            return false;
        } else {
            computeTile(key);
            synchronized (tileLock) {
                tileLock.notifyAll();
            }
            return true;
        }
    }

    public void export() throws Exception {
        if (task!=null) task.setStatusMessage("Exporting "+file.getName()+" with "+nThreads+" threads.");
        // Copy metadata from ImagePlus:
        IMetadata omeMeta = MetadataTools.createOMEXMLMetadata();

        boolean isLittleEndian = false;
        boolean isRGB = false;
        boolean isInterleaved = false;

        int series = 0;
        omeMeta.setImageID("Image:"+series, series);
        omeMeta.setPixelsID("Pixels:"+series, series);
        omeMeta.setImageName(name, series);
        omeMeta.setPixelsDimensionOrder(DimensionOrder.XYZCT, series);

        if (pixelType instanceof UnsignedShortType) {
            omeMeta.setPixelsType(PixelType.UINT16, series);
        } else if (pixelType instanceof UnsignedByteType) {
            omeMeta.setPixelsType(PixelType.UINT8, series);
        } else if (pixelType instanceof FloatType) {
            omeMeta.setPixelsType(PixelType.FLOAT, series);
        } else if (pixelType instanceof ARGBType) {
            isInterleaved = true;
            isRGB = true;
            omeMeta.setPixelsType(PixelType.UINT8, series);
        } else {
            throw new UnsupportedOperationException("Unhandled pixel type class: "+pixelType.getClass().getName());
        }

        omeMeta.setPixelsBigEndian(!isLittleEndian, 0);

        // Set resolutions
        omeMeta.setPixelsSizeX(new PositiveInteger(width), series);
        omeMeta.setPixelsSizeY(new PositiveInteger(height), series);
        omeMeta.setPixelsSizeZ(new PositiveInteger(sizeZ), series);
        omeMeta.setPixelsSizeT(new PositiveInteger(sizeT), series);
        omeMeta.setPixelsSizeC(new PositiveInteger(isRGB?nChannels*3:nChannels), series);

        if (isRGB) {
            omeMeta.setChannelID("Channel:0", series, 0);
            omeMeta.setChannelName("Channel_0", series, 0);
            omeMeta.setPixelsInterleaved(isInterleaved, series);
            omeMeta.setChannelSamplesPerPixel(new PositiveInteger(3), series, 0); //nSamples = 3; // TODO : check!
        } else {
            //omeMeta.setChannelSamplesPerPixel(new PositiveInteger(1), series, 0);
            omeMeta.setPixelsInterleaved(isInterleaved, series);
            for (int c = 0; c < nChannels; c++) {
                omeMeta.setChannelID("Channel:0:" + c, series, c);
                omeMeta.setChannelSamplesPerPixel(new PositiveInteger(1), series, c);
                int colorCode = converters[c].getColor().get();
                int colorRed = ARGBType.red(colorCode); //channelLUT.getRed(255);
                int colorGreen = ARGBType.green(colorCode);
                int colorBlue = ARGBType.blue(colorCode);
                int colorAlpha = ARGBType.alpha(colorCode);
                omeMeta.setChannelColor(new Color(colorRed, colorGreen, colorBlue, colorAlpha), series, c);
                omeMeta.setChannelName("Channel_"+c, series, c);
            }
        }

        omeMeta.setPixelsPhysicalSizeX(new Length(voxelSizes[0], unit), series);
        omeMeta.setPixelsPhysicalSizeY(new Length(voxelSizes[1], unit), series);
        omeMeta.setPixelsPhysicalSizeZ(new Length(voxelSizes[2], unit), series);
        // set Origin in XYZ
        // TODO : check if enough or other planes need to be set ?
        omeMeta.setPlanePositionX(new Length(origin.getDoublePosition(0), unit),0,0);
        omeMeta.setPlanePositionY(new Length(origin.getDoublePosition(1), unit),0,0);
        omeMeta.setPlanePositionZ(new Length(origin.getDoublePosition(2), unit),0,0);

        for (int i= 0;i<nResolutionLevels-1;i++) {
            ((IPyramidStore)omeMeta).setResolutionSizeX(new PositiveInteger(mapResToWidth.get(i+1)),series, i + 1);
            ((IPyramidStore)omeMeta).setResolutionSizeY(new PositiveInteger(mapResToHeight.get(i+1)),series, i + 1);
        }

        // setup writer
        PyramidOMETiffWriter writer = new PyramidOMETiffWriter();
        ((DynamicMetadataOptions)writer.getMetadataOptions()).set(OMETiffWriter.COMPANION_KEY,FilenameUtils.removeExtension(file.getAbsolutePath())+".companion.ome"); // TODO : check ome xml does not already exists ?

        writer.setWriteSequentially(true); // Setting this to false can be problematic!

        writer.setMetadataRetrieve(omeMeta);
        writer.setBigTiff(true);
        writer.setId(file.getAbsolutePath());
        writer.setSeries(0);
        writer.setCompression(compression);//TODO : understand why LZW compression does not work!!!
        writer.setTileSizeX((int)tileX);
        writer.setTileSizeY((int)tileY);
        writer.setInterleaved(omeMeta.getPixelsInterleaved(series));

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

        if (task!=null) task.setProgressMaximum(totalTiles);

        for (int i=0;i<nThreads;i++) {
            new Thread(() -> {
                while(computeNextTile()) { } // loops until no tile needs computation  anymore
            }).start();
        }

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
                    for (int z=0;z<sizeZ;z++) {
                        for (int y=0; y<nYTiles; y++) {
                            for (int x=0; x<nXTiles; x++) {
                                long startX = x * tileX;
                                long startY = y * tileY;

                                long endX = (x + 1) * (tileX);
                                long endY = (y + 1) * (tileY);

                                if (endX > maxX) endX = maxX;
                                if (endY > maxY) endY = maxY;

                                TileIterator.IntsKey key = new TileIterator.IntsKey(new int[]{r, t, c, z, y, x});

                                if (nThreads == 0) {
                                    computeTile(key);
                                } else {
                                    while (!computedBlocks.containsKey(key)) {
                                        synchronized (tileLock) {
                                            tileLock.wait();
                                        }
                                    }
                                }

                                //int plane = t * sizeZ * sizeC + z * sizeC + c;

                                int plane = t * sizeZ * sizeC + c * sizeZ + z;

                                IFD ifd = new IFD();
                                ifd.putIFDValue(IFD.TILE_WIDTH, endX-startX);
                                ifd.putIFDValue(IFD.TILE_LENGTH, endY-startY);

                                writer.saveBytes(plane, computedBlocks.get(key), ifd, (int)startX, (int)startY, (int)(endX-startX), (int)(endY-startY));

                                computedBlocks.remove(key);
                                tileIterator.decrementQueue();
                                if(task!=null) task.setProgressValue(writtenTiles.incrementAndGet());
                            }
                        }
                    }
                }
            }
        }
        writer.close();
        computedBlocks.clear();
        if (task!=null) task.run(()->{});
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        Unit unit = UNITS.MILLIMETER;
        String path;
        int tileX = Integer.MAX_VALUE; // = no tiling
        int tileY = Integer.MAX_VALUE; // = no tiling
        String compression = "Uncompressed";
        int nThreads = 0;
        int maxTilesInQueue = 10;
        transient Task task = null;

        public Builder tileSize(int tileX, int tileY) {
            this.tileX = tileX;
            this.tileY = tileY;
            return this;
        }

        public Builder lzw() {
            this.compression = "LZW";
            return this;
        }

        public Builder monitor(Task task) {
            this.task = task;
            return this;
        }

        public Builder maxTilesInQueue(int max) {
            this.maxTilesInQueue = max;
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

        public Builder nThreads(int nThreads) {
            this.nThreads = nThreads;
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
            return new OMETiffExporter(sources, converters, unit, f, tileX, tileY, compression, imageName, nThreads, maxTilesInQueue, task);
        }
    }

}
