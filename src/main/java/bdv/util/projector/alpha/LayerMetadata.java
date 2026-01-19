package bdv.util.projector.alpha;

import bdv.viewer.SourceAndConverter;
/**
 * Minimal interface for linking sources to their layer
 */
public interface LayerMetadata {
    /**
     * Returns the layer associated with the given source and converter.
     *
     * @param sac the source and converter
     * @return the layer containing this source
     */
    Layer getLayer(SourceAndConverter<?> sac);
}
