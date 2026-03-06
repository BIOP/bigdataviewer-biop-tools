package bdv.util.source.level;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.scijava.service.RenamableSource;
import sc.fiji.bdvpg.scijava.service.tree.inspect.ISourceInspector;
import sc.fiji.bdvpg.service.ISourceService;
import sc.fiji.bdvpg.source.SourceHelper;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.HashSet;
import java.util.Set;

import static sc.fiji.bdvpg.scijava.service.tree.inspect.SourceInspector.appendInspectorResult;


/**
 * A source which exposes a subset of resolution levels from an origin source.
 * <p>
 * This allows "cropping" the resolution pyramid by specifying a range [minLevel, maxLevel].
 * The new source remaps levels so that:
 * <ul>
 *   <li>New level 0 corresponds to original minLevel</li>
 *   <li>New level n corresponds to original minLevel + n</li>
 *   <li>New getNumMipmapLevels() returns maxLevel - minLevel + 1</li>
 * </ul>
 *
 * @param <T> the pixel type
 */
public class MappedLevelSource<T> implements Source<T>, ISourceInspector {

    private final Source<T> origin;
    private final int minLevel;
    private final int maxLevel;
    private final String name;

    /**
     * Creates a new MappedLevelSource wrapping the origin source with a restricted level range.
     *
     * @param origin the original source to wrap
     * @param name the name for this source
     * @param minLevel the minimum level index (inclusive) from the origin source
     * @param maxLevel the maximum level index (inclusive) from the origin source
     * @throws IllegalArgumentException if minLevel &gt; maxLevel, minLevel &lt; 0,
     *         or maxLevel &gt;= origin.getNumMipmapLevels()
     */
    public MappedLevelSource(Source<T> origin, String name, int minLevel, int maxLevel) {
        int numLevels = origin.getNumMipmapLevels();
        if (minLevel < 0) {
            throw new IllegalArgumentException("minLevel (" + minLevel + ") must be >= 0");
        }
        if (maxLevel >= numLevels) {
            throw new IllegalArgumentException("maxLevel (" + maxLevel + ") must be < origin's number of levels (" + numLevels + ")");
        }
        if (minLevel > maxLevel) {
            throw new IllegalArgumentException("minLevel (" + minLevel + ") must be <= maxLevel (" + maxLevel + ")");
        }
        this.origin = origin;
        this.name = name;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
    }

    /**
     * Maps a level index from this source to the corresponding level in the origin source.
     *
     * @param level the level index in this source
     * @return the corresponding level index in the origin source
     */
    private int mapLevel(int level) {
        return minLevel + level;
    }

    @Override
    public boolean isPresent(int t) {
        return origin.isPresent(t);
    }

    @Override
    public RandomAccessibleInterval<T> getSource(int t, int level) {
        return origin.getSource(t, mapLevel(level));
    }

    @Override
    public RealRandomAccessible<T> getInterpolatedSource(int t, int level, Interpolation method) {
        return origin.getInterpolatedSource(t, mapLevel(level), method);
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        origin.getSourceTransform(t, mapLevel(level), transform);
    }

    @Override
    public T getType() {
        return origin.getType();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return origin.getVoxelDimensions();
    }

    @Override
    public int getNumMipmapLevels() {
        return maxLevel - minLevel + 1;
    }

    /**
     * Returns the origin source that is being wrapped.
     *
     * @return the origin source
     */
    public Source<T> getOriginSource() {
        return origin;
    }

    /**
     * Returns the minimum level index from the origin source.
     *
     * @return the minimum level
     */
    public int getMinLevel() {
        return minLevel;
    }

    /**
     * Returns the maximum level index from the origin source.
     *
     * @return the maximum level
     */
    public int getMaxLevel() {
        return maxLevel;
    }

    @Override
    public Set<SourceAndConverter<?>> inspect(DefaultMutableTreeNode parent, SourceAndConverter<?> source,
                                               ISourceService sourceAndConverterService,
                                               boolean registerIntermediateSources) {
        DefaultMutableTreeNode nameNode = new DefaultMutableTreeNode("Name: " + this.name);
        parent.add(nameNode);

        DefaultMutableTreeNode levelRangeNode = new DefaultMutableTreeNode(
                "Level range: [" + minLevel + ", " + maxLevel + "] (of " + origin.getNumMipmapLevels() + " original levels)");
        parent.add(levelRangeNode);

        DefaultMutableTreeNode originalSource = new DefaultMutableTreeNode("Origin Source");
        parent.add(originalSource);

        HashSet<SourceAndConverter<?>> subSources = new HashSet<>();

        if (!sourceAndConverterService.getSourcesFromSpimSource(getOriginSource()).isEmpty()) {
            sourceAndConverterService.getSourcesFromSpimSource(getOriginSource()).forEach((src) -> {
                DefaultMutableTreeNode wrappedSourceNode =
                        new DefaultMutableTreeNode(new RenamableSource(src));
                originalSource.add(wrappedSourceNode);
                subSources.addAll(appendInspectorResult(wrappedSourceNode, src,
                        sourceAndConverterService, registerIntermediateSources));
            });
        } else {
            SourceAndConverter<?> src = SourceHelper.createSourceAndConverter(origin);
            if (registerIntermediateSources) {
                sourceAndConverterService.register(src);
            }
            DefaultMutableTreeNode wrappedSourceNode = new DefaultMutableTreeNode(
                    new RenamableSource(src));
            originalSource.add(wrappedSourceNode);
            subSources.addAll(appendInspectorResult(wrappedSourceNode, src,
                    sourceAndConverterService, registerIntermediateSources));
        }

        return subSources;
    }
}
