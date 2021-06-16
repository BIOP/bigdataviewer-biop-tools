package ch.epfl.biop.sourceandconverter.exporter;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.operetta.utils.HyperRange;
import ij.ImagePlus;
import ij.VirtualStack;
import ij.process.*;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Class which copies an ImagePlus, except that it applies an operation to modify
 * each ImageProcessor when it is requested.
 *
 */

public class SourceAndConverterVirtualStack extends VirtualStack {

    // Operation applied on an ImageProcessor

    final int bitDepth, size, height, width;

    Map<Integer, ImageProcessor> cachedImageProcessor = new ConcurrentHashMap<>();
    Map<Integer, Object> currentlyProcessedProcessor = new ConcurrentHashMap<>();

    final List<SourceAndConverter> sources;
    final int resolutionLevel;
    final CZTRange range;
    final int nBytesPerProcessor;
    final int nPixPerPlane;
    final AtomicLong bytesCounter;
    final boolean cache;

    public SourceAndConverterVirtualStack(List<SourceAndConverter> sources,
                                          int resolutionLevel,
                                          CZTRange range,
                                          boolean verbose,
                                          AtomicLong bytesCounter, boolean cache) {
        this.cache = cache;
        final int tModel = range.getRangeT().get(0);
        RandomAccessibleInterval raiModel = sources.get(0).getSpimSource().getSource(tModel,resolutionLevel);
        width = (int) raiModel.dimension(0);
        height = (int) raiModel.dimension(1);
        final int nSlices = (int) raiModel.dimension(2);
        size = (int) range.getTotalPlanes( );
        final Object type = Util.getTypeFromInterval(raiModel);
        if (type instanceof UnsignedShortType) {
            bitDepth = 16;
        } else if (type instanceof UnsignedByteType) {
            bitDepth = 8;
        } else if (type instanceof FloatType) {
            bitDepth = 32;
        } else {
            bitDepth = -1;
            throw new UnsupportedOperationException("Type "+type.getClass()+" unsupported.");
        }

        nBytesPerProcessor = width * height * (bitDepth / 8);

        this.sources = sources;
        this.resolutionLevel = resolutionLevel;
        this.range = range;
        this.nPixPerPlane = width*height;
        this.bytesCounter = bytesCounter;
    }

    ImagePlus imagePlusLocalizer = null;

    public void setImagePlusCZTSLocalizer(ImagePlus imp) {
        this.imagePlusLocalizer = imp;
    }

    /* Returns the pixel array for the specified slice, were 1<=n<=nslices. */
    public Object getPixels(int n) {
        ImageProcessor ip = getProcessor(n);
        if (ip!=null)
            return ip.getPixels();
        else
            return null;
    }

    public ByteProcessor getByteProcessor(int iC, int iZ, int iT) {
        SourceAndConverter sac = sources.get(iC);
        RandomAccessibleInterval<UnsignedByteType> rai = sac.getSpimSource().getSource(iT, resolutionLevel);
        RandomAccessibleInterval<UnsignedByteType> slice = Views.hyperSlice(rai, 2, iZ);
        byte[] bytes = new byte[nPixPerPlane];
        IterableInterval<UnsignedByteType> ii = Views.flatIterable(slice);
        int idx = 0;
        for (Cursor<UnsignedByteType> s = ii.cursor(); s.hasNext(); idx++) {
            bytes[idx] = (byte) s.next().get();
        }
        bytesCounter.addAndGet(nBytesPerProcessor);
        return new ByteProcessor(width, height, bytes, null);
    }

    public ShortProcessor getShortProcessor(int iC, int iZ, int iT) {
        SourceAndConverter sac = sources.get(iC);
        RandomAccessibleInterval<UnsignedShortType> rai = sac.getSpimSource().getSource(iT, resolutionLevel);
        RandomAccessibleInterval<UnsignedShortType> slice = Views.hyperSlice(rai, 2, iZ);
        short[] shorts = new short[nPixPerPlane];
        IterableInterval<UnsignedShortType> ii = Views.flatIterable(slice);
        int idx = 0;
        for (Cursor<UnsignedShortType> s = ii.cursor(); s.hasNext(); idx++) {
            shorts[idx] = (short) s.next().get();
        }
        bytesCounter.addAndGet(nBytesPerProcessor);
        return new ShortProcessor(width, height, shorts, null);
    }

    public FloatProcessor getFloatProcessor(int iC, int iZ, int iT) {
        SourceAndConverter sac = sources.get(iC);
        RandomAccessibleInterval<FloatType> rai = sac.getSpimSource().getSource(iT, resolutionLevel);
        RandomAccessibleInterval<FloatType> slice = Views.hyperSlice(rai, 2, iZ);
        float[] floats = new float[nPixPerPlane];
        IterableInterval<FloatType> ii = Views.flatIterable(slice);
        int idx = 0;
        for (Cursor<FloatType> s = ii.cursor(); s.hasNext(); idx++) {
            floats[idx] = s.next().get();
        }
        bytesCounter.addAndGet(nBytesPerProcessor);
        return new FloatProcessor(width, height, floats, null);
    }

    public ColorProcessor getColorProcessor(int iC, int iZ, int iT) {
        SourceAndConverter sac = sources.get(iC);
        RandomAccessibleInterval<ARGBType> rai = sac.getSpimSource().getSource(iT, resolutionLevel);
        RandomAccessibleInterval<ARGBType> slice = Views.hyperSlice(rai, 2, iZ);
        int[] ints = new int[nPixPerPlane];
        IterableInterval<ARGBType> ii = Views.flatIterable(slice);
        int idx = 0;
        for (Cursor<ARGBType> s = ii.cursor(); s.hasNext(); idx++) {
            ints[idx] = s.next().get();
        }
        bytesCounter.addAndGet(nBytesPerProcessor);
        return new ColorProcessor(width, height, ints);
    }

    Object lockAnalyzePreviousData = new Object();

    @Override
    public ImageProcessor getProcessor(int n) {
        //System.out.println("Ask for "+n);
        Object lockWaitedFor = null;
        if (imagePlusLocalizer==null) {
            switch (bitDepth) {
                case  8: return new ByteProcessor(width, height);
                case 16: return new ShortProcessor(width, height);
                case 24: return new ColorProcessor(width, height);
                case 32: return new FloatProcessor(width, height);
                default: throw new UnsupportedOperationException("Invalid bitdepth "+bitDepth);
            }
        }

        boolean computeInThread = false;
        boolean waitForResult = false;

        if (cache) {
            synchronized (lockAnalyzePreviousData) {
                if (cachedImageProcessor.containsKey(n)) {
                    //System.out.println("Stored  give back "+n);
                    return cachedImageProcessor.get(n);
                } else if (currentlyProcessedProcessor.keySet().contains(n)) {
                    lockWaitedFor = currentlyProcessedProcessor.get(n);
                    waitForResult = true;
                } else {
                    currentlyProcessedProcessor.put(n, new Object());
                    computeInThread = true;
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
            } else if (computeInThread) {
                Object lockProcessing = currentlyProcessedProcessor.get(n);
                synchronized (lockProcessing) {
                    int[] czt = imagePlusLocalizer.convertIndexToPosition(n);
                    int iC = range.getRangeC().get(czt[0]-1);
                    int iZ = range.getRangeZ().get(czt[1]-1);
                    int iT = range.getRangeT().get(czt[2]-1);
                    /*System.out.println("czt[0] = "+czt[0]);
                    System.out.println("czt[1] = "+czt[1]);
                    System.out.println("czt[2] = "+czt[2]);
                    System.out.println("iC = "+iC);
                    System.out.println("iZ = "+iZ);
                    System.out.println("iT = "+iT);*/
                    switch (bitDepth) {
                        case 8:
                            cachedImageProcessor.put(n, getByteProcessor(iC, iZ, iT));
                            break;
                        case 16:
                            cachedImageProcessor.put(n, getShortProcessor(iC, iZ, iT));
                            break;
                        case 24:
                            cachedImageProcessor.put(n, getColorProcessor(iC, iZ, iT));
                            break;
                        case 32:
                            cachedImageProcessor.put(n, getFloatProcessor(iC, iZ, iT));
                            break;
                    }
                    synchronized (lockAnalyzePreviousData) {
                        currentlyProcessedProcessor.remove(n);
                    }
                    lockProcessing.notifyAll();
                }
                return cachedImageProcessor.get(n);
            } else {
                throw new IllegalStateException("How did you reach this ?");
            }
        } else {
            int[] czt = imagePlusLocalizer.convertIndexToPosition(n);
            int iC = range.getRangeC().get(czt[0]-1);
            int iZ = range.getRangeZ().get(czt[1]-1);
            int iT = range.getRangeT().get(czt[2]-1);
            switch (bitDepth) {
                case 8:
                    return getByteProcessor(iC, iZ, iT);
                case 16:
                    return getShortProcessor(iC, iZ, iT);
                case 24:
                    return getColorProcessor(iC, iZ, iT);
                case 32:
                    return getFloatProcessor(iC, iZ, iT);
                default: throw new UnsupportedOperationException("Invalid bitdepth "+bitDepth);
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

}