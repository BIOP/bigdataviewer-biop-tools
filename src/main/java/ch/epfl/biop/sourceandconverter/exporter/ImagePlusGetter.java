package ch.epfl.biop.sourceandconverter.exporter;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.imageplus.ImagePlusHelper;
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
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import org.scijava.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class ImagePlusGetter {

    private static final Logger logger = LoggerFactory.getLogger(ImagePlusGetter.class);

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
     * @param task can be used for monitoring the progression of the ImagePlus acquisition progression
     * @return a virtual {@link ImagePlus} out of a list of {@link SourceAndConverter},
     * taken at a certain resolution level, and which czt range is specified via a {@link CZTRange} object
     */
    public static <T extends NumericType<T> & NativeType<T>> ImagePlus getVirtualImagePlus(String name,
                                         List<SourceAndConverter<T>> sources,
                                         int resolutionLevel,
                                         CZTRange range,
                                         boolean cache,
                                         Task task) {
        final AtomicLong bytesCounter = new AtomicLong();
        if (!cache) task = null;

        SourceAndConverterVirtualStack vStack = new SourceAndConverterVirtualStack(sources, resolutionLevel, range, bytesCounter, cache, task);
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

        boolean monitor = task!=null;
        if (monitor) {
            task.setStatusMessage("Counting bytes...");
            task.setProgressMaximum(totalBytes);
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
     * @param parallelC loads all channels in parallel
     * @param parallelZ loads all slices in parallel
     * @param parallelT loads all timepoints in parallel
     * @param task can be used for monitoring the progression of the ImagePlus acquisition progression
     * @return a non virtual {@link ImagePlus} out of a list of {@link SourceAndConverter},
     *       taken at a certain resolution level, and which czt range is specified via a {@link CZTRange} object
     */
    public static <T extends NumericType<T> & NativeType<T>> ImagePlus getImagePlus(String name,
                                                                                  List<SourceAndConverter<T>> sources,
                                                                                  int resolutionLevel,
                                                                                  CZTRange range,
                                                                                  boolean parallelC,
                                                                                  boolean parallelZ,
                                                                                  boolean parallelT,
                                                                                  Task task) {
        ImagePlus vImage = getVirtualImagePlus(name, sources, resolutionLevel, range, false, null ); // task not used here

        final AtomicLong bytesCounter = new AtomicLong();

        int nBytesPerPlane = vImage.getHeight() * vImage.getWidth() * (vImage.getBitDepth()/8);
        long totalBytes = (long) range.getRangeC().size() * (long) range.getRangeZ().size() * (long) range.getRangeT().size() * (long) nBytesPerPlane;

        boolean monitor = task!=null;
        if (monitor) {
            task.setStatusMessage("Counting bytes...");
            task.setProgressMaximum(totalBytes);
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

                    if (monitor) {
                        long bytes = bytesCounter.addAndGet(nBytesPerPlane);
                        task.setProgressValue(bytes);
                    }
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
        if (task!=null) task.run(() ->{}); // signal task is ended
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

    public static CZTRange fromSources(List<SourceAndConverter<?>> sources, int t, int resolutionLevel) throws Exception {
        int maxTimeFrames = SourceAndConverterHelper.getMaxTimepoint(sources.get(0));
        int maxZSlices = (int) sources.get(0).getSpimSource().getSource(t,resolutionLevel).dimension(2);
        return new CZTRange.Builder().get(sources.size(),maxZSlices, maxTimeFrames);
    }

    public static <T extends NumericType<T> & NativeType<T>> List<SourceAndConverter<T>> sanitizeList(List<SourceAndConverter<?>> sources) {
        List<SourceAndConverter<T>> sanitizedList = new ArrayList<>();
        for (SourceAndConverter<?> source: sources) {
            sanitizedList.add((SourceAndConverter<T>) source);
        }
        return sanitizedList;
    }

}
