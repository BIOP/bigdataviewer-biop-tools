package ch.epfl.biop.sourceandconverter.exporter;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.gui.card.CardHelper;
import ij.ImagePlus;
import ij.VirtualStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.scijava.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.ColorModel;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Class which copies an ImagePlus, except that it applies an operation to modify
 * each ImageProcessor when it is requested.
 * <p>
 * TODO : cache CZT key to make faster the duplication of identical frames
 *
 * @param <T> the pixel type of the source
 */

public class SourceAndConverterVirtualStack<T extends NumericType<T> & NativeType<T>> extends VirtualStack {

    private static final Logger logger = LoggerFactory.getLogger(SourceAndConverterVirtualStack.class);

    // Operation applied on an ImageProcessor

    final int bitDepth, size, height, width;

    Map<Integer, ImageProcessor> cachedImageProcessor = new ConcurrentHashMap<>();
    Map<Integer, Object> currentlyProcessedProcessor = new HashMap<>();
    Map<CZTId, Integer> cztIdToComputedProcessor = new HashMap<>();

    final List<SourceAndConverter<T>> sources;
    final int resolutionLevel;
    final CZTRange range;
    final int nBytesPerProcessor;
    final int nPixPerPlane;
    final AtomicLong bytesCounter;
    final boolean cache;
    final int totalPlanes;
    //private final int nChannels, nZSlices, nFrames;
    final Task task;
    private final long totalBytes;

    /**
     * Takes a list of SourceAndConverter and makes a VirtualStack out of it
     * The sources should have the same dimensions in terms of XYZCT, but it's actively checked before an error shows up
     *
     * @param sources         sources list in, each source is a channel
     * @param resolutionLevel the resolution level used from the sources
     * @param range           a CZT range objet which can be used to select and or reorder the data from the sources
     * @param bytesCounter    and atomic counter which can be used to monitor the amount of bytes read in this virtual stack
     * @param cache           whether the read planes should be kept in cache or not (it's a basic cache, it's never freed unless you discard the image)
     * @param task            a task object, not sure how it's used anymore
     */
    public SourceAndConverterVirtualStack(List<SourceAndConverter<T>> sources,
                                          int resolutionLevel,
                                          CZTRange range,
                                          AtomicLong bytesCounter, boolean cache, Task task) {
        this.cache = cache;
        this.task = task;
        final int tModel = range.getRangeT().get(0);
        RandomAccessibleInterval raiModel;
        if (sources.get(0).asVolatile() != null) {
            raiModel = sources.get(0).asVolatile().getSpimSource().getSource(tModel, resolutionLevel);
        } else {
            raiModel = sources.get(0).getSpimSource().getSource(tModel, resolutionLevel);
        }
        width = (int) raiModel.dimension(0);
        height = (int) raiModel.dimension(1);
        //nSlices = (int) raiModel.dimension(2);
        size = (int) range.getTotalPlanes();
        final Object type = Util.getTypeFromInterval(raiModel);
        if ((type instanceof UnsignedShortType) || (type instanceof VolatileUnsignedShortType)) {
            bitDepth = 16;
        } else if ((type instanceof UnsignedByteType) || (type instanceof VolatileUnsignedByteType)) {
            bitDepth = 8;
        } else if ((type instanceof FloatType) || (type instanceof VolatileFloatType)) {
            bitDepth = 32;
        } else if ((type instanceof ARGBType) || (type instanceof VolatileARGBType)) {
            bitDepth = 24;
        } else {
            bitDepth = -1;
            throw new UnsupportedOperationException("Type " + type.getClass() + " unsupported.");
        }

        nBytesPerProcessor = width * height * (bitDepth / 8);

        this.sources = sources;
        this.resolutionLevel = resolutionLevel;
        this.range = range;
        this.nPixPerPlane = width * height;
        this.bytesCounter = bytesCounter;

        totalPlanes = (int) range.getTotalPlanes();
        totalBytes = (long) totalPlanes * nBytesPerProcessor;

        /*nChannels = range.getRangeC().size();
        nFrames = range.getRangeT().size();
        nZSlices = range.getRangeZ().size();*/

        /*if ((int) raiModel.dimension(2)!=nZSlices) {
            logger.error("Mismatch! nSlices = "+nZSlices+" rai Z dimension = "+raiModel.dimension(2));
        }*/

    }

    ImagePlus imagePlusLocalizer = null;

    /**
     * Uses an extra ImagePlus that serves as a reference for proper CZT indexing
     *
     * @param imp the imageplus reference for indexing
     */
    public void setImagePlusCZTSLocalizer(ImagePlus imp) {
        this.imagePlusLocalizer = imp;
    }

    /**
     * Returns the pixel array for the specified slice, were n is between 1 and nslices.
     **/
    public Object getPixels(int n) {
        ImageProcessor ip = getProcessor(n);
        if (ip != null)
            return ip.getPixels();
        else
            return null;
    }

    public boolean noSourcePresent(SourceAndConverter sac, int iZ, int iT) {
        return !sac.getSpimSource().isPresent(iT);
    }

    /**
     * Returns the byte processor for the input c z t parameter, basically reads
     * a {@link RandomAccessibleInterval} of type {@link UnsignedByteType} and feeds data to a ByteProcessor
     *
     * @param iC channel index
     * @param iZ slice index
     * @param iT frame index
     * @return byteprocessor
     */
    public ByteProcessor getByteProcessor(int iC, int iZ, int iT) {
        SourceAndConverter sac = sources.get(iC);
        if (noSourcePresent(sac, iZ, iT)) return new ByteProcessor(width, height);

        RandomAccessibleInterval<UnsignedByteType> rai = sac.getSpimSource().getSource(iT, resolutionLevel);
        RandomAccessibleInterval<UnsignedByteType> slice = Views.hyperSlice(rai, 2, iZ);
        byte[] bytes = new byte[nPixPerPlane];
        IterableInterval<UnsignedByteType> ii = Views.flatIterable(slice);
        int idx = 0;
        for (Cursor<UnsignedByteType> s = ii.cursor(); s.hasNext(); idx++) {
            bytes[idx] = (byte) s.next().get();
        }
        if (task != null) {
            long bytesLoaded = bytesCounter.addAndGet(nBytesPerProcessor);
            task.setProgressValue(bytesLoaded);
            if (bytesLoaded == totalBytes) task.run(() -> {
            });
        }
        return new ByteProcessor(width, height, bytes, getCM(iC));
    }

    /**
     * Returns the short processor for the input c z t parameter, basically reads
     * a {@link RandomAccessibleInterval} of type {@link UnsignedShortType} and feeds data to a ShortProcessor
     *
     * @param iC channel index
     * @param iZ slice index
     * @param iT frame index
     * @return shortprocessor
     */
    public ShortProcessor getShortProcessor(int iC, int iZ, int iT) {
        SourceAndConverter sac = sources.get(iC);
        if (noSourcePresent(sac, iZ, iT)) return new ShortProcessor(width, height);
        RandomAccessibleInterval<UnsignedShortType> rai = sac.getSpimSource().getSource(iT, resolutionLevel);
        RandomAccessibleInterval<UnsignedShortType> slice = Views.hyperSlice(rai, 2, iZ);
        short[] shorts = new short[nPixPerPlane];
        IterableInterval<UnsignedShortType> ii = Views.flatIterable(slice);
        int idx = 0;
        for (Cursor<UnsignedShortType> s = ii.cursor(); s.hasNext(); idx++) {
            shorts[idx] = (short) s.next().get();
        }
        if (task != null) {
            long bytesLoaded = bytesCounter.addAndGet(nBytesPerProcessor);
            task.setProgressValue(bytesLoaded);
            if (bytesLoaded == totalBytes) task.run(() -> {
            });
        }
        return new ShortProcessor(width, height, shorts, getCM(iC));
    }

    ColorModel getCM(int iC) {
        if (imagePlusLocalizer == null) {
            return null;
        } else {
            LUT[] luts = imagePlusLocalizer.getLuts();
            if ((luts.length > iC) && (luts[iC] != null) && (luts[iC].getColorModel() != null)) {
                return luts[iC].getColorModel();
            } else {
                LUT lut = LUT.createLutFromColor(new Color(ARGBType.red(255), ARGBType.green(255), ARGBType.blue(255)));
                logger.debug("Null Color Model");
                return lut.getColorModel();
            }
        }
    }

    /**
     * Returns the short processor for the input c z t parameter, basically reads
     * a {@link RandomAccessibleInterval} of type {@link FloatType} and feeds data to a FloatProcessor
     *
     * @param iC channel index
     * @param iZ slice index
     * @param iT frame index
     * @return floatprocessor
     */
    public FloatProcessor getFloatProcessor(int iC, int iZ, int iT) {
        SourceAndConverter sac = sources.get(iC);
        if (noSourcePresent(sac, iZ, iT)) return new FloatProcessor(width, height);
        RandomAccessibleInterval<FloatType> rai = sac.getSpimSource().getSource(iT, resolutionLevel);
        RandomAccessibleInterval<FloatType> slice = Views.hyperSlice(rai, 2, iZ);
        float[] floats = new float[nPixPerPlane];
        IterableInterval<FloatType> ii = Views.flatIterable(slice);
        int idx = 0;
        for (Cursor<FloatType> s = ii.cursor(); s.hasNext(); idx++) {
            floats[idx] = s.next().get();
        }
        if (task != null) {
            long bytesLoaded = bytesCounter.addAndGet(nBytesPerProcessor);
            task.setProgressValue(bytesLoaded);
            if (bytesLoaded == totalBytes) task.run(() -> {
            });
        }
        return new FloatProcessor(width, height, floats, getCM(iC));
    }

    /**
     * Returns the short processor for the input c z t parameter, basically reads
     * a {@link RandomAccessibleInterval} of type {@link ARGBType} and feeds data to a ColorProcessor
     *
     * @param iC channel index
     * @param iZ slice index
     * @param iT frame index
     * @return colorprocessor
     */
    public ColorProcessor getColorProcessor(int iC, int iZ, int iT) {
        SourceAndConverter sac = sources.get(iC);
        if (noSourcePresent(sac, iZ, iT)) return new ColorProcessor(width, height);
        RandomAccessibleInterval<ARGBType> rai = sac.getSpimSource().getSource(iT, resolutionLevel);
        RandomAccessibleInterval<ARGBType> slice = Views.hyperSlice(rai, 2, iZ);
        int[] ints = new int[nPixPerPlane];
        IterableInterval<ARGBType> ii = Views.flatIterable(slice);
        int idx = 0;
        for (Cursor<ARGBType> s = ii.cursor(); s.hasNext(); idx++) {
            ints[idx] = s.next().get();
        }
        if (task != null) {
            long bytesLoaded = bytesCounter.addAndGet(nBytesPerProcessor);
            task.setProgressValue(bytesLoaded);
            if (bytesLoaded == totalBytes) task.run(() -> {
            });
        }
        return new ColorProcessor(width, height, ints);
    }

    final Object lockAnalyzePreviousData = new Object();

    @Override
    public void setProcessor(ImageProcessor ip, int n) {
        cachedImageProcessor.put(n, ip);
    }


    @Override
    public ImageProcessor getProcessor(int n) {
        Object lockWaitedFor = null;
        int[] czt;
        if (n == 1) {
            if (cachedImageProcessor.containsKey(n)) {
                return (ImageProcessor) cachedImageProcessor.get(n).clone(); // Really weird bug.... thread lock ?
            }
        }
        if (imagePlusLocalizer == null) {
            czt = new int[]{1, 1, 1};
        } else {
            czt = imagePlusLocalizer.convertIndexToPosition(n);
        }

        boolean waitForResult = false;

        if (cache) {
            int iC = range.getRangeC().get(czt[0] - 1);
            int iZ = range.getRangeZ().get(czt[1] - 1);
            int iT = range.getRangeT().get(czt[2] - 1);
            final CZTId cztId = new CZTId(iC, iZ, iT);
            synchronized (lockAnalyzePreviousData) {
                if (cachedImageProcessor.containsKey(n)) {
                    return cachedImageProcessor.get(n);
                } else if (currentlyProcessedProcessor.containsKey(n)) {
                    lockWaitedFor = currentlyProcessedProcessor.get(n);
                    waitForResult = true;
                } else if (cztIdToComputedProcessor.containsKey(cztId)) {
                    // Shortcut -> skipping the loading!
                    int nReferenced = cztIdToComputedProcessor.get(cztId);
                    cachedImageProcessor.put(n, cachedImageProcessor.get(nReferenced));
                    if (task != null) {
                        long bytesLoaded = bytesCounter.addAndGet(nBytesPerProcessor);
                        task.setProgressValue(bytesLoaded);
                        if (bytesLoaded == totalBytes) task.run(() -> {
                        });
                    }
                    return cachedImageProcessor.get(n);
                } else {
                    currentlyProcessedProcessor.put(n, new Object());
                }
            }

            // We need to wait
            if (waitForResult) {
                synchronized (lockWaitedFor) {
                    while (!cachedImageProcessor.containsKey(n)) {
                        try {
                            lockWaitedFor.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }
                return cachedImageProcessor.get(n);
            } else {
                Object lockProcessing = currentlyProcessedProcessor.get(n);
                synchronized (lockProcessing) {
                    ImageProcessor ip = null;
                    switch (bitDepth) {
                        case 8:
                            ip = getByteProcessor(iC, iZ, iT);
                            break;
                        case 16:
                            ip = getShortProcessor(iC, iZ, iT);
                            break;
                        case 24:
                            ip = getColorProcessor(iC, iZ, iT);
                            break;
                        case 32:
                            ip = getFloatProcessor(iC, iZ, iT);
                            break;
                    }

                    synchronized (lockAnalyzePreviousData) {
                        cztIdToComputedProcessor.put(cztId, n);
                        currentlyProcessedProcessor.remove(n);
                    }
                    cachedImageProcessor.put(n, ip);
                    lockProcessing.notifyAll();
                }
                return cachedImageProcessor.get(n);
            }

        } else {
            int iC = range.getRangeC().get(czt[0] - 1);
            int iZ = range.getRangeZ().get(czt[1] - 1);
            int iT = range.getRangeT().get(czt[2] - 1);
            switch (bitDepth) {
                case 8:
                    return getByteProcessor(iC, iZ, iT);
                case 16:
                    return getShortProcessor(iC, iZ, iT);
                case 24:
                    return getColorProcessor(iC, iZ, iT);
                case 32:
                    return getFloatProcessor(iC, iZ, iT);
                default:
                    throw new UnsupportedOperationException("Invalid bitdepth " + bitDepth);
            }
        }
    }

    @Override
    public int getBitDepth() {
        return bitDepth;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    /**
     * Just a czt key with a proper hashcode and equals function
     */
    public static class CZTId {
        final int c, z, t;

        public CZTId(int c, int z, int t) {
            this.c = c;
            this.z = z;
            this.t = t;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CZTId cztId = (CZTId) o;
            return c == cztId.c && z == cztId.z && t == cztId.t;
        }

        @Override
        public int hashCode() {
            return Objects.hash(c, z, t);
        }
    }

}