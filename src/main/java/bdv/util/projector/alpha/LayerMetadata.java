package bdv.util.projector.alpha;

import bdv.viewer.SourceAndConverter;
/**
 * Minimal interface for linking sources to their layer
 */
public interface LayerMetadata {
    Layer getLayer(SourceAndConverter<?> sac);
}
