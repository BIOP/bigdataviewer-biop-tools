package bdv.util.projector.alpha;

/**
 * Minimal interface for layers : being able to get an alpha value, and
 * know whether to skip it or not, even though the skipping part should
 * be done upstream, by removing the sources from the projector
 */
public interface Layer extends Comparable<Layer>{
    /**
     * @return the alpha value of this layer
     */
    float getAlpha(); // 1 = fully opaque

    /**
     * Returns the blending mode for this layer.
     *
     * @return 0 for SUM blending mode (currently only SUM is implemented)
     */
    int getBlendingMode(); // 0 = SUM, 1 = AVERAGE TODO , currently only sum
    boolean skip();
}
