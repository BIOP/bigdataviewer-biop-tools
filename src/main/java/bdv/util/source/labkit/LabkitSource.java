package bdv.util.source.labkit;

import bdv.util.DefaultInterpolators;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.labkit.SourcesToImgPlus;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imagej.ImgPlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import org.scijava.Context;
import sc.fiji.labkit.ui.segmentation.SegmentationUtils;
import sc.fiji.labkit.ui.segmentation.Segmenter;
import sc.fiji.labkit.ui.segmentation.weka.TrainableSegmentationSegmenter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A lazy Source that applies a Labkit classifier to input sources.
 * <p>
 * The segmentation is computed on-demand as tiles are requested.
 * Input sources (channels) are combined and classified using the provided
 * Labkit classifier file.
 * </p>
 * <p>
 * Note: While input sources may have multiple resolution levels, this source
 * only provides a single resolution level (the one specified at construction).
 * </p>
 */
public class LabkitSource<T> implements Source<UnsignedByteType> {

    protected final DefaultInterpolators<UnsignedByteType> interpolators = new DefaultInterpolators<>();

    private final SourceAndConverter<T>[] sources;
    private final int resolutionLevel;
    private final String name;
    private final Segmenter segmenter;

    // Cache for computed segmentations per timepoint
    private final Map<Integer, RandomAccessibleInterval<UnsignedByteType>> cachedSegmentations = new HashMap<>();

    /**
     * Creates a LabkitSource from an array of input sources and a classifier file.
     *
     * @param name the name of this source
     * @param sources the input sources (each source represents a channel)
     * @param classifierPath path to the Labkit .classifier file
     * @param context the SciJava context
     * @param resolutionLevel the resolution level to use from the input sources
     */
    public LabkitSource(String name, SourceAndConverter<T>[] sources, String classifierPath, Context context, int resolutionLevel) {
        this.name = name;
        this.sources = sources;
        this.resolutionLevel = resolutionLevel;

        // Load the classifier
        this.segmenter = new TrainableSegmentationSegmenter(context);
        this.segmenter.openModel(classifierPath);
    }

    /**
     * Creates a LabkitSource from an array of input sources and a pre-loaded segmenter.
     *
     * @param name the name of this source
     * @param sources the input sources (each source represents a channel)
     * @param segmenter the pre-loaded Labkit segmenter
     * @param resolutionLevel the resolution level to use from the input sources
     */
    public LabkitSource(String name, SourceAndConverter<T>[] sources, Segmenter segmenter, int resolutionLevel) {
        this.name = name;
        this.sources = sources;
        this.resolutionLevel = resolutionLevel;
        this.segmenter = segmenter;
    }

    /**
     * Returns the class names from the loaded classifier.
     *
     * @return list of class names
     */
    public List<String> getClassNames() {
        return segmenter.classNames();
    }

    @Override
    public boolean isPresent(int t) {
        return sources[0].getSpimSource().isPresent(t);
    }

    @Override
    public synchronized RandomAccessibleInterval<UnsignedByteType> getSource(int t, int level) {
        // We only have one resolution level
        if (level != 0) {
            throw new IllegalArgumentException("LabkitSource only supports resolution level 0, requested: " + level);
        }

        return cachedSegmentations.computeIfAbsent(t, this::createLazySegmentation);
    }

    private RandomAccessibleInterval<UnsignedByteType> createLazySegmentation(int timepoint) {
        // Create ImgPlus from sources at this timepoint
        ImgPlus<?> imgPlus = SourcesToImgPlus.wrap(sources, name, resolutionLevel, timepoint);

        // Create lazy cached segmentation
        return SegmentationUtils.createCachedSegmentation(segmenter, imgPlus, null);
    }

    @Override
    public RealRandomAccessible<UnsignedByteType> getInterpolatedSource(int t, int level, Interpolation method) {
        return Views.interpolate(Views.extendZero(getSource(t, level)), interpolators.get(method));
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        // Use the transform from the first input source at the specified resolution level
        sources[0].getSpimSource().getSourceTransform(t, resolutionLevel, transform);
    }

    @Override
    public UnsignedByteType getType() {
        return new UnsignedByteType();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return sources[0].getSpimSource().getVoxelDimensions();
    }

    @Override
    public int getNumMipmapLevels() {
        // Only one resolution level for the segmentation output
        return 1;
    }
}
