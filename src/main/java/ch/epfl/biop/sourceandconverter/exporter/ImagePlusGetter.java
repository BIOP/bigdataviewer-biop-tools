package ch.epfl.biop.sourceandconverter.exporter;

import bdv.viewer.SourceAndConverter;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.HyperStackConverter;
import ij.process.ImageProcessor;
import ij.process.LUT;
import net.imglib2.display.ColorConverter;
import net.imglib2.display.LinearRange;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import spimdata.imageplus.ImagePlusHelper;

import java.awt.*;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ImagePlusGetter {

    private static final Logger logger = LoggerFactory.getLogger(ImagePlusGetter.class);

    /*
     * Value used in {@link ImagePlusGetter#getImagePlus(String, List, int, CZTRange, boolean)}
     * in order to limit the amount of parallelization when acquiring an image
     */
    //public static int limitParallelJobs = 16;

    /* Helper method that determines the number of lines present in IJ log
     */
    static int countLines(String str) {
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

    // To avoid updating multiple times in parallel the IJ log window, still unsufficient to avoid little errors in the display
    static final Object IJLogLock = new Object();

    /**
     * Method which returns a virtual {@link ImagePlus} out of a list
     * of {@link SourceAndConverter}, taken at a certain resolution level, and which
     * czt range is specified via a {@link CZTRange} object.
     *
     * Each {@link SourceAndConverter} represents a channel. The dimension of each source
     * need to be compatible.
     *
     * TODO : handle dimension exception better ?
     *
     * @param name Name of the output ImagePlus
     * @param sources sources to export as ImagePlus
     * @param resolutionLevel resolution Level of the sources
     * @param range czt range which can be used to define a subset of the output image
     * @param cache if set to true, each time a plane is computed, it is stored in memory. Ultimately,
     *              this leads to filling the RAM as in a non-virtual image, if all planes are visited
     * @param verbose if set to true, a {@link BytesMonitor} is created to follow the progression of the creation of this ImagePlus
     * @return a virtual {@link ImagePlus} out of a list of {@link SourceAndConverter},
     * taken at a certain resolution level, and which czt range is specified via a {@link CZTRange} object
     */
    public static ImagePlus getVirtualImagePlus(String name,
                                         List<SourceAndConverter> sources,
                                         int resolutionLevel,
                                         CZTRange range,
                                         boolean cache,
                                         boolean verbose) {
        final AtomicLong bytesCounter = new AtomicLong();
        if (!cache) verbose = false;

        SourceAndConverterVirtualStack vStack = new SourceAndConverterVirtualStack(sources, resolutionLevel, range, bytesCounter, cache);
        vStack.getProcessor(1); // Avoid annoying race condition annoying with hyperstack converter
        ImagePlus out = new ImagePlus(name, vStack);
        int[] czt = range.getCZTDimensions( );
        if (out.getBitDepth()==24) {
            if (czt[0]>1) {
                IJ.log("Error: RGB Images cannot be multichannel");
                throw new UnsupportedOperationException("RGB Images cannot be multichannel");
            }
        }

        if ( ( czt[ 0 ] + czt[ 1 ] + czt[ 2 ] ) > 3 ) {
            if (out.getBitDepth()!=24) {
                out = HyperStackConverter.toHyperStack(out, czt[0], czt[1], czt[2], null, "color");
                out.setStack(vStack);
            }
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
                }, bytesCounter::get, () -> bytesCounter.get() == totalBytes, totalBytes, 1000, true);
            }
        }

        if ( ( czt[ 0 ] + czt[ 1 ] + czt[ 2 ] ) > 3 ) {
            // Needs conversion to hyperstack
            LUT[] luts = new LUT[range.getRangeC().size()];
            int iC = 0;
            for (Integer sourceIndex : range.getRangeC()) { //SourceAndConverter sac:sources) {
                SourceAndConverter<?> sac = sources.get(sourceIndex);
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
            for (LUT lut : luts) {
                if (lut == null) {
                    oneIsNull = true;
                    break;
                }
            }
            if (!oneIsNull&& out instanceof CompositeImage) ((CompositeImage)out).setLuts(luts);
        }

        AffineTransform3D at3D = new AffineTransform3D();

        int timepointbegin = 0;
        sources.get(0).getSpimSource().getSourceTransform(timepointbegin, resolutionLevel, at3D);
        String unit = "px";
        if (sources.get(0).getSpimSource().getVoxelDimensions() != null) {
            unit = sources.get(0).getSpimSource().getVoxelDimensions().unit();
            if (unit==null) {
                unit = "px";
            }
        }
        ImagePlusHelper.storeExtendedCalibrationToImagePlus(out,at3D,unit, timepointbegin);

        return out;
    }

    /**
     *
     * @param name Name of the output ImagePlus
     * @param sources sources to export as ImagePlus
     * @param resolutionLevel resolution Level of the sources
     * @param range czt range which can be used to define a subset of the output image
     * @param verbose if set to true, a {@link BytesMonitor} is created to follow the progression of the creation of this ImagePlus
     * @param parallelC loads all channels in parallet
     * @param parallelZ loads all slices in parallel
     * @param parallelT loads all timepoints in parallel
     * @return a non virtual {@link ImagePlus} out of a list of {@link SourceAndConverter},
     *       taken at a certain resolution level, and which czt range is specified via a {@link CZTRange} object
     */
    public static ImagePlus getImagePlus(String name,
                                         List<SourceAndConverter> sources,
                                         int resolutionLevel,
                                         CZTRange range,
                                         boolean verbose,
                                         boolean parallelC,
                                         boolean parallelZ,
                                         boolean parallelT) {
        ImagePlus vImage = getVirtualImagePlus(name, sources, resolutionLevel, range, false, false );

        final AtomicLong bytesCounter = new AtomicLong();

        int nBytesPerPlane = vImage.getHeight() * vImage.getWidth() * (vImage.getBitDepth()/8);
        long totalBytes = (long) range.getRangeC().size() * (long) range.getRangeZ().size() * (long) range.getRangeT().size() * (long) nBytesPerPlane;

        if (verbose) {
            synchronized (IJLogLock) {
                IJ.log("Starting Getting " + name + "...");
                String log = IJ.getLog();
                int nLines = countLines(log) - 1;
                new BytesMonitor(name, (m) -> {
                    synchronized (IJLogLock) {
                        IJ.log("\\Update" + nLines + ":" + m);
                    }
                }, bytesCounter::get, () -> bytesCounter.get() == totalBytes, totalBytes, 1000, false);
            }
        }

        int w = vImage.getWidth();
        int h = vImage.getHeight();
        ImageStack vStack = vImage.getStack();
        final ImageStack stack = ImageStack.create( w, h, (int) range.getTotalPlanes(), vImage.getBitDepth() );
        ImagePlus imp = new ImagePlus(name, stack);
        int[] czt = range.getCZTDimensions( );

        if ((imp.getBitDepth()!=24) && ( czt[ 0 ] + czt[ 1 ] + czt[ 2 ] ) > 3 ) {
            imp = HyperStackConverter.toHyperStack(imp, czt[0], czt[1], czt[2]);
        }

        final ImagePlus image = imp;

        Stream<Integer> cStream = range.rangeC.stream();
        if (parallelC) cStream = cStream.parallel();
        cStream.forEach(c -> {
            Stream<Integer> tStream = range.rangeT.stream();
            if (parallelT) tStream = tStream.parallel();
            tStream.forEach(t -> {
                Stream<Integer> zStream = range.rangeZ.stream();
                if (parallelZ) zStream = zStream.parallel();
                zStream.forEach(z -> {
                    int iC = range.rangeC.indexOf(c);
                    int iZ = range.rangeZ.indexOf(z);
                    int iT = range.rangeT.indexOf(t);

                    int n = image.getStackIndex(iC + 1, iZ + 1, iT + 1);
                    ImageProcessor ip = vStack.getProcessor(n);
                    if (n == 1) {
                        image.setProcessor(ip);
                    }
                    stack.setProcessor(ip, n);
                    bytesCounter.addAndGet(nBytesPerPlane);
                });
            });
        });

        if ( ( czt[ 0 ] + czt[ 1 ] + czt[ 2 ] ) > 3 ) {
            // Needs conversion to hyperstack
            LUT[] luts = new LUT[range.getRangeC().size()];
            int iC = 0;
            for (Integer sourceIndex : range.getRangeC()) { //SourceAndConverter sac:sources) {
                SourceAndConverter<?> sac = sources.get(sourceIndex);
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
                    imp.setC(iC+1);
                    imp.getProcessor().setLut(lut);

                    if (sac.getConverter() instanceof LinearRange) {
                        LinearRange converter = (LinearRange) sac.getConverter();
                        imp.setDisplayRange(converter.getMin(), converter.getMax());
                    }
                }
                iC++;
            }
            boolean oneIsNull = false;
            for (LUT lut : luts) {
                if (lut == null) {
                    oneIsNull = true;
                    break;
                }
            }
            if (!oneIsNull&& imp instanceof CompositeImage) ((CompositeImage)imp).setLuts(luts);

        }

        AffineTransform3D at3D = new AffineTransform3D();

        int timepointbegin = 0;
        sources.get(0).getSpimSource().getSourceTransform(timepointbegin, resolutionLevel, at3D);
        String unit = "px";
        if (sources.get(0).getSpimSource().getVoxelDimensions() != null) {
            unit = sources.get(0).getSpimSource().getVoxelDimensions().unit();
            if (unit==null) {
                unit = "px";
            }
        }
        ImagePlusHelper.storeExtendedCalibrationToImagePlus(imp,at3D,unit, timepointbegin);
        return imp;
    }

    /**
     * Returns the full CZT range of the sources given in input
     * @param source source
     * @param t initial timepoint used for probing the source (the source needs to be present at this timepoint
     * @param resolutionLevel resolution level of the source
     * @return a CZT range including all slices and time points
     * @throws Exception an exception is thrown if the source is null for instance
     */
    public static CZTRange fromSource(SourceAndConverter<?> source, int t, int resolutionLevel) throws Exception {
        int maxTimeFrames = SourceAndConverterHelper.getMaxTimepoint(source);
        int maxZSlices = (int) source.getSpimSource().getSource(t,resolutionLevel).dimension(2);
        return new CZTRange.Builder().get(1,maxZSlices, maxTimeFrames);
    }

    public static CZTRange fromSources(List<SourceAndConverter> sources, int t, int resolutionLevel) throws Exception {
        int maxTimeFrames = SourceAndConverterHelper.getMaxTimepoint(sources.get(0));
        int maxZSlices = (int) sources.get(0).getSpimSource().getSource(t,resolutionLevel).dimension(2);
        return new CZTRange.Builder().get(sources.size(),maxZSlices, maxTimeFrames);
    }

    /**
     * Simple class which monitors an amount of bytes processed (read / write / analyzed...)
     * and outputs a message every time it is updated. This monitor updates itself every
     * time step fixed in ms.
     *
     * A new thread is created for every monitor. The thread is stopped when the task is complete.
     * as fixed by the boolean supplier.
     *
     * TODO : fix busy waiting
     */
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
            this.timeMs = Math.max(timeMs, 10);
            new Thread(this::run).start();
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
                    double mbPerS = (double) currentBytesRead / (double) (1024*1024) / totalTime;
                    int numberOfEquals = (int) (20*currentRatio);
                    StringBuilder bar = new StringBuilder();
                    for (int i=0;i<numberOfEquals;i++) {
                        bar.append("=");
                    }
                    for(int i=numberOfEquals;i<20;i++) {
                        bar.append("  ");
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
