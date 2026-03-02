package ch.epfl.biop.source.transform;

import bdv.util.source.level.MappedLevelSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;

import java.util.function.Function;

import sc.fiji.bdvpg.source.SourceHelper;

/**
 * Creates a new {@link SourceAndConverter} that exposes only a subset of resolution levels
 * from the original source.
 * <p>
 * This is useful for "cropping" the resolution pyramid to exclude either
 * high-resolution (fine detail) or low-resolution (coarse) levels.
 */
public class SourceLevelMapper<T> implements Runnable, Function<SourceAndConverter<T>, SourceAndConverter<T>> {

    private final SourceAndConverter<T> sourceIn;
    private final int minLevel;
    private final int maxLevel;
    private final String name;

    /**
     * Creates a new SourceLevelMapper.
     *
     * @param sourceIn the input SourceAndConverter to wrap
     * @param minLevel the minimum level index (inclusive) to keep
     * @param maxLevel the maximum level index (inclusive) to keep
     * @param name the name for the resulting source
     */
    public SourceLevelMapper(SourceAndConverter<T> sourceIn, int minLevel, int maxLevel, String name) {
        this.sourceIn = sourceIn;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.name = name;
    }

    @Override
    public void run() {
    }

    /**
     * Returns the transformed SourceAndConverter with cropped resolution levels.
     *
     * @return a new SourceAndConverter with the specified level range
     */
    public SourceAndConverter<T> get() {
        return this.apply(this.sourceIn);
    }

    @Override
    public SourceAndConverter<T> apply(SourceAndConverter src) {
        Source srcMapped = new MappedLevelSource(src.getSpimSource(), this.name, minLevel, maxLevel);
        SourceAndConverter source;
        if (src.asVolatile() != null) {
            MappedLevelSource vsrcMapped = new MappedLevelSource(
                    src.asVolatile().getSpimSource(), this.name, minLevel, maxLevel);
            SourceAndConverter vsource = new SourceAndConverter(
                    (Source) vsrcMapped,
                    SourceHelper.cloneConverter(src.asVolatile().getConverter(), src.asVolatile()));
            source = new SourceAndConverter(
                    srcMapped,
                    SourceHelper.cloneConverter(src.getConverter(), src),
                    vsource);
        } else {
            source = new SourceAndConverter(
                    srcMapped,
                    SourceHelper.cloneConverter(src.getConverter(), src));
        }
        return source;
    }
}
