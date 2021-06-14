package ch.epfl.biop.sourceandconverter.exporter;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.operetta.utils.HyperRange;
import ij.*;
import ij.plugin.HyperStackConverter;
import ij.process.LUT;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.display.ColorConverter;
import net.imglib2.display.LinearRange;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spimdata.imageplus.ImagePlusHelper;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ImagePlusGetter {

    private static Logger logger = LoggerFactory.getLogger(ImagePlusGetter.class);

    public static ImagePlus getImagePlus(String name,
                                         SourceAndConverter source,
                                         int resolutionLevel,
                                         HyperRange range) {
        List<SourceAndConverter> sources = new ArrayList<>();
        sources.add(source);
        return getImagePlus(name, sources, resolutionLevel, range, false);
    }

    public static ImagePlus getImagePlus(String name,
                                         List<SourceAndConverter> sources,
                                         int resolutionLevel,
                                         HyperRange range) {
        return getImagePlus(name,
                sources,
                resolutionLevel,
                range, false);
    }

    public static int countLines(String str) {
        if(str == null || str.isEmpty())
        {
            return 0;
        }
        int lines = 1;
        int pos = 0;
        while ((pos = str.indexOf("\n", pos) + 1) != 0) {
            lines++;
        }
        return lines-1;
    }

    static Object IJLogLock = new Object();

    public static ImagePlus getVirtualImagePlus(String name,
                                         List<SourceAndConverter> sources,
                                         int resolutionLevel,
                                         HyperRange range,
                                         boolean cache,
                                         boolean verbose) {
        final AtomicLong bytesCounter = new AtomicLong();
        if (!cache) verbose = false;
        SourceAndConverterVirtualStack vStack = new SourceAndConverterVirtualStack(sources, resolutionLevel, range, verbose, bytesCounter, cache);
        ImagePlus out = new ImagePlus(name, vStack);
        int[] czt = range.getCZTDimensions( );
        if ( ( czt[ 0 ] + czt[ 1 ] + czt[ 2 ] ) > 3 ) {
            out = HyperStackConverter.toHyperStack(out, czt[0], czt[1], czt[2]);
        }
        vStack.setImagePlusCZTSLocalizer(out);
        long totalBytes = (long) range.getRangeC().size() * (long) range.getRangeZ().size() * (long) range.getRangeT().size()*(long) (vStack.getBitDepth()/8)*(long) vStack.getHeight()*(long) vStack.getWidth();

        if (verbose) {
            synchronized (IJLogLock) {
                IJ.log("Starting Getting " + name + "...");
                String log = IJ.getLog();
                int nLines = countLines(log) - 1;
                new BytesMonitor(name, (m) -> {
                    synchronized (IJLogLock) {
                        IJ.log("\\Update" + nLines + ":" + m);
                    }
                }, () -> bytesCounter.get(), () -> bytesCounter.get() == totalBytes, totalBytes, 1000, true);
            }
        }

        if ( ( czt[ 0 ] + czt[ 1 ] + czt[ 2 ] ) > 3 ) {
            // Needs conversion to hyperstack
            LUT[] luts = new LUT[range.getRangeC().size()];
            int iC = 0;
            for (Integer sourceIndex : range.getRangeC()) { //SourceAndConverter sac:sources) {
                SourceAndConverter sac = sources.get(sourceIndex-1);
                if (!(sac.getSpimSource().getType() instanceof ARGBType)) {
                    LUT lut;
                    if (sac.getConverter() instanceof ColorConverter) {
                        ColorConverter converter = (ColorConverter) sac.getConverter();
                        ARGBType c = converter.getColor();
                        lut = LUT.createLutFromColor(new Color(ARGBType.red(c.get()), ARGBType.green(c.get()), ARGBType.blue(c.get())));
                    } else {
                        lut = LUT.createLutFromColor(new Color(ARGBType.red(255), ARGBType.green(255), ARGBType.blue(255)));
                    }

                    luts[iC] = lut;
                    out.setC(iC+1);
                    out.getProcessor().setLut(lut);

                    if (sac.getConverter() instanceof LinearRange) {
                        LinearRange converter = (LinearRange) sac.getConverter();
                        out.setDisplayRange(converter.getMin(), converter.getMax());
                    }
                }
                iC++;
            }
            boolean oneIsNull = false;
            for (int c = 0;c<luts.length;c++) {
                if (luts[c] == null) {
                    oneIsNull = true;
                }
            }
            if (!oneIsNull&& out instanceof CompositeImage) ((CompositeImage)out).setLuts(luts);

        }
        //ImagePlus imp_out = ImagePlusHelper.wrap(sacSortedPerLocation.get(location).stream().map(sac -> (SourceAndConverter) sac).collect(Collectors.toList()), mapSacToMml, timepointbegin, numtimepoints, timestep);
        AffineTransform3D at3D = new AffineTransform3D();

        int timepointbegin = range.getRangeT().get(0)-1;
        sources.get(0).getSpimSource().getSourceTransform(timepointbegin, resolutionLevel, at3D);
        String unit = "px";
        if (sources.get(0).getSpimSource().getVoxelDimensions() != null) {
            unit = sources.get(0).getSpimSource().getVoxelDimensions().unit();
            if (unit==null) {
                unit = "px";
            }
        }
        //imp_out.setTitle();
        ImagePlusHelper.storeExtendedCalibrationToImagePlus(out,at3D,unit, timepointbegin);
        return out;
    }

    public static ImagePlus getImagePlus(String name,
                                         List<SourceAndConverter> sources,
                                         int resolutionLevel,
                                         HyperRange range,
                                         boolean verbose) {
        // Todo : confirm that all is all the same type (8 bits and compatible)
        // And less than 2e9 pix per plane
        // And the same size (x / y / z)
        final int tModel = range.getRangeT().get(0)-1;
        RandomAccessibleInterval raiModel = sources.get(0).getSpimSource().getSource(tModel,resolutionLevel);
        final int stack_width = (int) raiModel.dimension(0);
        final int stack_height = (int) raiModel.dimension(1);
        final int nSlices = (int) raiModel.dimension(2);
        final int nPlanes = range.getTotalPlanes( );
        final int bitDepth;
        final Object type = Util.getTypeFromInterval(raiModel);
        if (type instanceof UnsignedShortType) {
            bitDepth = 16;
        } else if (type instanceof UnsignedByteType) {
            bitDepth = 8;
        } else if (type instanceof FloatType) {
            bitDepth = 32;
        } else {
            throw new UnsupportedOperationException("Type "+type.getClass()+" unsupported.");
        }
        // Create the new stack. We need to create it before because some images might be missing
        final ImageStack stack = ImageStack.create( stack_width, stack_height, nPlanes, bitDepth );
        ImagePlus imp = new ImagePlus(name, stack);
        imp.setDimensions(range.getRangeC().size(), nSlices, range.getRangeT().size());
        // Need to launch reading for
        // All Z
        long totalBytes = (long) range.getRangeC().size() * (long) nSlices * (long) range.getRangeT().size()*(long) (bitDepth/8)*(long) stack_width*(long) stack_height;
        AtomicLong bytesCounter = new AtomicLong();
        bytesCounter.set(0);
        if (verbose) {
            synchronized (IJLogLock) {
                IJ.log("Starting Getting " + name + "...");
                String log = IJ.getLog();
                int nLines = countLines(log) - 1;
                new BytesMonitor(name, (m) -> {
                    synchronized (IJLogLock) {
                        IJ.log("\\Update" + nLines + ":" + m);
                    }
                }, () -> bytesCounter.get(), () -> bytesCounter.get() == totalBytes, totalBytes, 1000, false);
            }
        }

        range.getRangeC().stream().parallel().forEach(
                c -> {
                    range.getRangeT().stream().parallel().forEach( t -> {
                        int iC = range.getRangeC().indexOf( c );
                        int iT = range.getRangeT().indexOf( t );
                        ImageStack stackCT = getImageStack(sources.get(c-1).getSpimSource().getSource(t-1,resolutionLevel), bytesCounter);
                        for (int z=0;z< nSlices;z++) {
                            int idx = imp.getStackIndex(iC+1, z+1, iT+1);
                            stack.setProcessor(stackCT.getProcessor(z+1), idx);
                        }
                    });
                }
        );

        int[] czt = range.getCZTDimensions( );
        if ( ( czt[ 0 ] + czt[ 1 ] + czt[ 2 ] ) > 3 ) {
            // Needs conversion to hyperstack
            ImagePlus out = HyperStackConverter.toHyperStack(imp, czt[0], czt[1], czt[2]);
            LUT[] luts = new LUT[range.getRangeC().size()];
            int iC = 0;
            for (Integer sourceIndex : range.getRangeC()) { //SourceAndConverter sac:sources) {
                SourceAndConverter sac = sources.get(sourceIndex-1);
                if (!(sac.getSpimSource().getType() instanceof ARGBType)) {
                    LUT lut;
                    if (sac.getConverter() instanceof ColorConverter) {
                        ColorConverter converter = (ColorConverter) sac.getConverter();
                        ARGBType c = converter.getColor();
                        lut = LUT.createLutFromColor(new Color(ARGBType.red(c.get()), ARGBType.green(c.get()), ARGBType.blue(c.get())));
                    } else {
                        lut = LUT.createLutFromColor(new Color(ARGBType.red(255), ARGBType.green(255), ARGBType.blue(255)));
                    }

                    luts[iC] = lut;
                    out.setC(iC+1);
                    out.getProcessor().setLut(lut);

                    if (sac.getConverter() instanceof LinearRange) {
                        LinearRange converter = (LinearRange) sac.getConverter();
                        out.setDisplayRange(converter.getMin(), converter.getMax());
                    }
                }
                iC++;
            }
            boolean oneIsNull = false;
            for (int c = 0;c<luts.length;c++) {
                if (luts[c] == null) {
                    oneIsNull = true;
                }
            }
            if (!oneIsNull&& out instanceof CompositeImage) ((CompositeImage)out).setLuts(luts);

            //ImagePlus imp_out = ImagePlusHelper.wrap(sacSortedPerLocation.get(location).stream().map(sac -> (SourceAndConverter) sac).collect(Collectors.toList()), mapSacToMml, timepointbegin, numtimepoints, timestep);
            AffineTransform3D at3D = new AffineTransform3D();

            int timepointbegin = range.getRangeT().get(0)-1;
            sources.get(0).getSpimSource().getSourceTransform(timepointbegin, resolutionLevel, at3D);
            String unit = "px";
            if (sources.get(0).getSpimSource().getVoxelDimensions() != null) {
                unit = sources.get(0).getSpimSource().getVoxelDimensions().unit();
                if (unit==null) {
                    unit = "px";
                }
            }
            //imp_out.setTitle();
            ImagePlusHelper.storeExtendedCalibrationToImagePlus(out,at3D,unit, timepointbegin);

            return out;
        } else {
            // Single czt :
            //ImagePlus imp_out = ImagePlusHelper.wrap(sacSortedPerLocation.get(location).stream().map(sac -> (SourceAndConverter) sac).collect(Collectors.toList()), mapSacToMml, timepointbegin, numtimepoints, timestep);
            AffineTransform3D at3D = new AffineTransform3D();

            int timepointbegin = range.getRangeT().get(0)-1;
            sources.get(0).getSpimSource().getSourceTransform(timepointbegin, resolutionLevel, at3D);
            String unit = "px";
            if (sources.get(0).getSpimSource().getVoxelDimensions() != null) {
                unit = sources.get(0).getSpimSource().getVoxelDimensions().unit();
                if (unit==null) {
                    unit = "px";
                }
            }
            //imp_out.setTitle();
            ImagePlusHelper.storeExtendedCalibrationToImagePlus(imp,at3D,unit, timepointbegin);
            return imp;
        }
    }

    public static ImageStack getImageStack(RandomAccessibleInterval rai, AtomicLong counter) {
        assert rai.numDimensions() == 3;
        final Object type = Util.getTypeFromInterval(rai);

        if ((rai.dimension(0)* rai.dimension(1))>Integer.MAX_VALUE) {
            throw new UnsupportedOperationException("Too many pixels in a single plane, unsupported ImagePlus creation");
        }

        final int sx = (int)rai.dimension(0);
        final int sy = (int)rai.dimension(1);

        final int nPixPerPlane = sx*sy;
        ImageStack stack = new ImageStack(sx,sy);
        if (type instanceof UnsignedShortType) {
            stack.setBitDepth(16);
            final int nBytesPerPlane = 2 * sx * sy;
            short[] shorts = new short[nPixPerPlane];
            if (rai instanceof IterableInterval) {
                IterableInterval<UnsignedShortType> ii = Views.flatIterable(rai);
                int idx = 0;
                for (Cursor<UnsignedShortType> s = ii.cursor(); s.hasNext();idx++) {
                    shorts[idx] = (short) s.next().get();
                    if (idx == nPixPerPlane-1) {
                        idx = -1;
                        stack.addSlice("", shorts);
                        shorts = new short[nPixPerPlane];
                        counter.addAndGet(nBytesPerPlane);
                    }
                }
            }
        } else if (type instanceof UnsignedByteType) {
            final int nBytesPerPlane = sx * sy;
            stack.setBitDepth(8);
            byte[] bytes = new byte[nPixPerPlane];
            if (rai instanceof IterableInterval) {
                IterableInterval<UnsignedByteType> ii = Views.flatIterable(rai);
                int idx = 0;
                for (Cursor<UnsignedByteType> s = ii.cursor(); s.hasNext();idx++) {
                    bytes[idx] = (byte) s.next().get();
                    if (idx == nPixPerPlane-1) {
                        idx = -1;
                        stack.addSlice("", bytes);
                        bytes = new byte[nPixPerPlane];
                        counter.addAndGet(nBytesPerPlane);
                    }
                }
            }
        } else if (type instanceof FloatType) {
            final int nBytesPerPlane = sx * sy * 4;
            stack.setBitDepth(32);
            float[] floats = new float[nPixPerPlane];
            if (rai instanceof IterableInterval) {
                IterableInterval<FloatType> ii = Views.flatIterable(rai);
                int idx = 0;
                for (Cursor<FloatType> s = ii.cursor(); s.hasNext();idx++) {
                    floats[idx] = s.next().get();
                    if (idx == nPixPerPlane-1) {
                        idx = -1;
                        stack.addSlice("", floats);
                        floats = new float[nPixPerPlane];
                        counter.addAndGet(nBytesPerPlane);
                    }
                }
            }
        } else if (type instanceof ARGBType) {
            final int nBytesPerPlane = sx * sy * 4;
            stack.setBitDepth(24);
            int[] ints = new int[nPixPerPlane];
            if (rai instanceof IterableInterval) {
                IterableInterval<ARGBType> ii = Views.flatIterable(rai);
                int idx = 0;
                for (Cursor<ARGBType> s = ii.cursor(); s.hasNext();idx++) {
                    ints[idx] = s.next().get();
                    if (idx == nPixPerPlane-1) {
                        idx = -1;
                        stack.addSlice("", ints);
                        ints = new int[nPixPerPlane];
                        counter.addAndGet(nBytesPerPlane);
                    }
                }
            }
        } else {
            throw new UnsupportedOperationException("Type "+type.getClass()+" unsupported.");
        }
        return stack;
    }

    public static HyperRange.Builder fromSource(SourceAndConverter source, int t, int resolutionLevel) {
        int nSlices = (int) source.getSpimSource().getSource(t,resolutionLevel).dimension(2);
        int nFrames = 1;
        int iFrame = 1;
        int previous = iFrame;
        while (source.getSpimSource().isPresent(iFrame)) {
            previous = iFrame;
            iFrame *= 2;
        }
        if (iFrame>1) {
            for (int tp = previous;tp<iFrame;tp++) {
                if (!source.getSpimSource().isPresent(tp)) {
                    nFrames = tp;
                    break;
                }
            }
        }
        HyperRange.Builder b = new HyperRange
                .Builder()
                .setRangeT(1,nFrames)
                .setRangeZ(1,nSlices);
        return b;
    }

    public static HyperRange.Builder fromSources(List<SourceAndConverter> sources, int t, int resolutionLevel) {
        return fromSource(sources.get(0),t,resolutionLevel).setRangeC(1,sources.size());
    }

    public static class BytesMonitor {
        final Supplier<Long> bytesRead;
        final Supplier<Boolean> complete;
        final int timeMs;
        final long totalBytes;
        final String taskName;
        final Consumer<String> monitorLogger;
        final boolean isVirtual;

        public BytesMonitor(String taskName,
                            Consumer<String> logger,
                            Supplier<Long> bytesRead,
                            Supplier<Boolean> complete,
                            long totalBytes,
                            int timeMs,
                            boolean isVirtual) {
            this.isVirtual = isVirtual;
            this.taskName = taskName;
            this.bytesRead = bytesRead;
            this.complete = complete;
            this.monitorLogger = logger;
            this.totalBytes = totalBytes;
            if (timeMs > 10) {
                this.timeMs = timeMs;
            } else {
                this.timeMs = 10;
            }
            SwingUtilities.invokeLater(() -> new Thread(this::run).start());
        }

        public void run() {
            DecimalFormat df = new DecimalFormat("#0.0");
            double previousBytesRead = 0;
            Instant jobStart = Instant.now();
            double totalTime = 0;
            double totalMb = totalBytes / (1024.0*1024);
            while (!complete.get()) {
                try {
                    Thread.sleep(timeMs);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                totalTime = Duration.between(jobStart, Instant.now()).getSeconds();//counterStep*timeMs/1000.0;
                long currentBytesRead = bytesRead.get();
                if (currentBytesRead>previousBytesRead) {
                    previousBytesRead = currentBytesRead;
                    double currentRatio = (double) currentBytesRead / (double) totalBytes;
                    double estimatedJobTimeInS = totalTime/currentRatio;
                    double mbPerS = (double) currentBytesRead / (double) (1024*1024) / (double) totalTime;
                    int numberOfEquals = (int) (20*currentRatio);
                    String bar = "";
                    for (int i=0;i<numberOfEquals;i++) {
                        bar+="=";
                    }
                    for(int i=numberOfEquals;i<20;i++) {
                        bar+="  ";
                    }
                    if (isVirtual) {
                        monitorLogger.accept(taskName+": ["+bar+"] "+(int) (currentRatio*100)+"% Loaded ( "+df.format((double)currentBytesRead/(double)(1024*1024))+"/ "+df.format(totalMb)+" Mb)");
                    } else if (estimatedJobTimeInS>10) {
                        monitorLogger.accept(taskName+": ["+bar+"] "+(int) (currentRatio*100)+"% Loaded ["+(int)(totalTime)+" s - Remaining = "+(int)(estimatedJobTimeInS - totalTime)+" s] ("+df.format(mbPerS)+" Mb / s)");
                    } else {
                        monitorLogger.accept(taskName+": ["+bar+"] "+(int) (currentRatio*100)+"% Loaded ["+(int)(totalTime)+" s] ("+df.format(mbPerS)+" Mb / s)");
                    }
                }
                if ((currentBytesRead == 0)&&(totalTime>10)&&(!isVirtual)) {
                    monitorLogger.accept(taskName+": No progress in 10 seconds.");
                }
            }
            if (isVirtual) {
                monitorLogger.accept(taskName + ": [====================] (" + df.format(totalMb) + " Mb)");
            } else {
                monitorLogger.accept(taskName + ": [====================] Completed in ~ " + (int) (totalTime) + " s] (" + df.format(totalMb) + " Mb)");
            }
            logger.debug("Exit monitor thread of task "+taskName);
        }
    }

}
