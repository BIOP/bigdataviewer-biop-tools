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
import sc.fiji.bdvpg.scijava.services.ui.RenamableSourceAndConverter;
import sc.fiji.bdvpg.scijava.services.ui.inspect.ISourceInspector;
import sc.fiji.bdvpg.services.ISourceAndConverterService;
import sc.fiji.labkit.ui.segmentation.SegmentationUtils;
import sc.fiji.labkit.ui.segmentation.Segmenter;
import sc.fiji.labkit.ui.segmentation.weka.TrainableSegmentationSegmenter;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;

import static sc.fiji.bdvpg.scijava.services.ui.SourceAndConverterInspector.appendInspectorResult;

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
 *
 * @param <T> the pixel type of the input sources
 */
public class LabkitSource<T> implements Source<UnsignedByteType>, ISourceInspector {

    protected final DefaultInterpolators<UnsignedByteType> interpolators = new DefaultInterpolators<>();

    private final SourceAndConverter<T>[] sources;
    private final int resolutionLevel;
    private final String name;
    private final Segmenter segmenter;
    private final String classifierPath;
    private final boolean useGpu;

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
        this(name, sources, classifierPath, context, resolutionLevel, false);
    }

    /**
     * Creates a LabkitSource from an array of input sources and a classifier file with GPU option.
     *
     * @param name the name of this source
     * @param sources the input sources (each source represents a channel)
     * @param classifierPath path to the Labkit .classifier file
     * @param context the SciJava context
     * @param resolutionLevel the resolution level to use from the input sources
     * @param useGpu whether to use GPU acceleration for segmentation
     */
    public LabkitSource(String name, SourceAndConverter<T>[] sources, String classifierPath, Context context, int resolutionLevel, boolean useGpu) {
        this.name = name;
        this.sources = sources;
        this.resolutionLevel = resolutionLevel;
        this.classifierPath = classifierPath;
        this.useGpu = useGpu;

        // Load the classifier
        this.segmenter = new TrainableSegmentationSegmenter(context);
        this.segmenter.setUseGpu(useGpu);
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
        this.classifierPath = null; // Not available when using pre-loaded segmenter
        this.useGpu = false; // GPU setting is managed by the provided segmenter
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

    /**
     * Returns the input sources used for classification.
     *
     * @return the array of input SourceAndConverter
     */
    public SourceAndConverter<T>[] getInputSources() {
        return sources;
    }

    /**
     * Returns the path to the classifier file, or null if a pre-loaded segmenter was used.
     *
     * @return the classifier path, or null
     */
    public String getClassifierPath() {
        return classifierPath;
    }

    /**
     * Returns the resolution level used from the input sources.
     *
     * @return the resolution level
     */
    public int getResolutionLevel() {
        return resolutionLevel;
    }

    /**
     * Returns whether GPU acceleration is enabled.
     *
     * @return true if GPU is used, false otherwise
     */
    public boolean isUseGpu() {
        return useGpu;
    }

    @Override
    public Set<SourceAndConverter<?>> inspect(DefaultMutableTreeNode parent, SourceAndConverter<?> sac, ISourceAndConverterService sourceAndConverterService, boolean registerIntermediateSources) {
        DefaultMutableTreeNode nameNode = new DefaultMutableTreeNode(
                "Name: " + this.name);
        parent.add(nameNode);

        DefaultMutableTreeNode pathNode = new DefaultMutableTreeNode(
                "Classifier: " + this.classifierPath);
        parent.add(pathNode);

        DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(
                "Classes: ");

        int i = 0;
        for (String className: segmenter.classNames()) {
            classNode.add(new DefaultMutableTreeNode(
                    i+": " + className));
            i++;
        }
        parent.add(classNode);

        DefaultMutableTreeNode resolutionLevelNode = new DefaultMutableTreeNode(
                "Resolution Level: " + this.resolutionLevel);
        parent.add(resolutionLevelNode);

        DefaultMutableTreeNode useGpuNode = new DefaultMutableTreeNode(
                "GPU: " + this.useGpu);
        parent.add(useGpuNode);

        DefaultMutableTreeNode classifiedSources = new DefaultMutableTreeNode("Classified Sources");

        parent.add(classifiedSources);

        for (SourceAndConverter source : this.sources) {
            DefaultMutableTreeNode sourceNode = new DefaultMutableTreeNode(
                    new RenamableSourceAndConverter(source));
            classifiedSources.add(sourceNode);
            appendInspectorResult(sourceNode, source,
                    sourceAndConverterService, registerIntermediateSources);
        }


        return new HashSet<>(Arrays.asList(this.sources));
    }
}
