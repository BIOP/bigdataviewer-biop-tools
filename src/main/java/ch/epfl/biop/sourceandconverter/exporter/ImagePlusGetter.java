package ch.epfl.biop.sourceandconverter.exporter;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.operetta.utils.HyperRange;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.HyperStackConverter;
import ij.process.LUT;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
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
            String log = IJ.getLog();
            int nLines = countLines(log);
            new Monitor(name, (m) -> IJ.log("\\Update"+nLines+":" + m), () -> bytesCounter.get(), () -> bytesCounter.get() == totalBytes, totalBytes, 1000);
        }

        range.getRangeC().stream().parallel().forEach(
                c -> {
                    range.getRangeT().stream().parallel().forEach( t -> {
                        int iC = range.getRangeC().indexOf( c );
                        int iT = range.getRangeT().indexOf( t );
                        ImageStack stackCT = getImageStack(sources.get(iC).getSpimSource().getSource(iT,resolutionLevel), bytesCounter);
                        for (int z=0;z< nSlices;z++) {
                            int idx = imp.getStackIndex(iC+1, z+1, iT+1);
                            stack.setProcessor(stackCT.getProcessor(z+1), idx);
                        }
                    });
                }
        );

        //System.out.println("Number of planes read = "+planeCounter.get()+" / "+nPlanes);

        int[] czt = range.getCZTDimensions( );
        if ( ( czt[ 0 ] + czt[ 1 ] + czt[ 2 ] ) > 3 ) {

            ImagePlus out = HyperStackConverter.toHyperStack(imp, czt[0], czt[1], czt[2]);
            LUT[] luts = new LUT[sources.size()];
            for (SourceAndConverter sac:sources) {
                /*int iOri
                iSource = range.getRangeC().indexOf()
                if (!(sac.getSpimSource().getType() instanceof ARGBType)) {
                    LUT lut;
                    if (sac.getConverter() instanceof ColorConverter) {
                        ColorConverter converter = (ColorConverter) sac.getConverter();
                        ARGBType c = converter.getColor();
                        lut = LUT.createLutFromColor(new Color(ARGBType.red(c.get()), ARGBType.green(c.get()), ARGBType.blue(c.get())));
                    } else {
                        lut = LUT.createLutFromColor(new Color(ARGBType.red(255), ARGBType.green(255), ARGBType.blue(255)));
                    }

                    luts[.indexOf(sac)] = lut;
                    imp.setC(sacs.indexOf(sac)+1);
                    imp.getProcessor().setLut(lut);

                    if (sac.getConverter() instanceof LinearRange) {
                        LinearRange converter = (LinearRange) sac.getConverter();
                        imp.setDisplayRange(converter.getMin(), converter.getMax());
                    }
                }*/
            }

            /*boolean oneIsNull = false;
            for (int c = 0;c<luts.length;c++) {
                if (luts[c] == null) {
                    oneIsNull = true;
                }
            }
            if (!oneIsNull) ((CompositeImage)imp).setLuts(luts);*/

            return out;
        } else {
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
                    nFrames = tp-1;
                    break;
                }
            }
        }
        System.out.println("frames found = "+nFrames);
        HyperRange.Builder b = new HyperRange
                .Builder()
                .setRangeT(1,nFrames)
                .setRangeZ(1,nSlices);
        return b;
    }

    public static HyperRange.Builder fromSources(List<SourceAndConverter> sources, int t, int resolutionLevel) {
        return fromSource(sources.get(0),t,resolutionLevel).setRangeC(1,sources.size());
    }

    public static class Monitor {
        final Supplier<Long> bytesRead;
        final Supplier<Boolean> complete;
        final int timeMs;
        final long totalBytes;
        final String taskName;
        final Consumer<String> monitorLogger;

        public Monitor(String taskName, Consumer<String> logger, Supplier<Long> bytesRead, Supplier<Boolean> complete, long totalBytes, int timeMs) {
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
            while (!complete.get()) {
                try {
                    Thread.sleep(timeMs);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                double totalTime = Duration.between(jobStart, Instant.now()).getSeconds();//counterStep*timeMs/1000.0;
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
                    if (estimatedJobTimeInS>10) {
                        monitorLogger.accept(taskName+": ["+bar+"] "+(int) (currentRatio*100)+"% Complete ["+(int)(totalTime)+" s - Remaining = "+(int)(estimatedJobTimeInS - totalTime)+" s] ("+df.format(mbPerS)+" Mb / s)");
                    } else {
                        monitorLogger.accept(taskName+": ["+bar+"] "+(int) (currentRatio*100)+"% Complete ["+(int)(totalTime)+" s] ("+df.format(mbPerS)+" Mb / s)");
                    }
                }
                if ((currentBytesRead == 0)&&(totalTime>10)) {
                    monitorLogger.accept(taskName+": No progress in 10 seconds");
                }
            }
            logger.debug("Exit monitor thread of task "+taskName);
        }
    }

}
