package bdv.util.projector.alpha;

import bdv.viewer.SourceAndConverter;
import net.imglib2.type.numeric.real.FloatType;
/**
 * Minimal interface required for the projector in order to link a source to its alpha buddy
 */
public interface SourcesMetadata {
    boolean isAlphaSource(SourceAndConverter<?> source);
    boolean hasAlphaSource(SourceAndConverter<?> source);
    SourceAndConverter<FloatType> getAlphaSource(SourceAndConverter<?> source);
}
