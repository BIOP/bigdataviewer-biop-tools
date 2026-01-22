package ch.epfl.biop.sourceandconverter.transform;

import bdv.util.source.level.MappedLevelSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;

import java.util.function.Function;

import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

/**
 * Creates a new {@link SourceAndConverter} that exposes only a subset of resolution levels
 * from the original source.
 * <p>
 * This is useful for "cropping" the resolution pyramid to exclude either
 * high-resolution (fine detail) or low-resolution (coarse) levels.
 */
public class SourceLevelMapper implements Runnable, Function<SourceAndConverter, SourceAndConverter> {

    private final SourceAndConverter sacIn;
    private final int minLevel;
    private final int maxLevel;
    private final String name;

    /**
     * Creates a new SourceLevelMapper.
     *
     * @param sacIn the input SourceAndConverter to wrap
     * @param minLevel the minimum level index (inclusive) to keep
     * @param maxLevel the maximum level index (inclusive) to keep
     * @param name the name for the resulting source
     */
    public SourceLevelMapper(SourceAndConverter sacIn, int minLevel, int maxLevel, String name) {
        this.sacIn = sacIn;
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
    public SourceAndConverter get() {
        return this.apply(this.sacIn);
    }

    @Override
    public SourceAndConverter apply(SourceAndConverter src) {
        Source srcMapped = new MappedLevelSource(src.getSpimSource(), this.name, minLevel, maxLevel);
        SourceAndConverter sac;
        if (src.asVolatile() != null) {
            MappedLevelSource vsrcMapped = new MappedLevelSource(
                    src.asVolatile().getSpimSource(), this.name, minLevel, maxLevel);
            SourceAndConverter vsac = new SourceAndConverter(
                    (Source) vsrcMapped,
                    SourceAndConverterHelper.cloneConverter(src.asVolatile().getConverter(), src.asVolatile()));
            sac = new SourceAndConverter(
                    srcMapped,
                    SourceAndConverterHelper.cloneConverter(src.getConverter(), src),
                    vsac);
        } else {
            sac = new SourceAndConverter(
                    srcMapped,
                    SourceAndConverterHelper.cloneConverter(src.getConverter(), src));
        }
        return sac;
    }
}
