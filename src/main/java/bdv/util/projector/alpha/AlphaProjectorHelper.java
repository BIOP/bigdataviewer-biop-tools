package bdv.util.projector.alpha;

import bdv.viewer.SourceAndConverter;
import net.imglib2.type.numeric.real.FloatType;
import org.jetbrains.annotations.NotNull;

public class AlphaProjectorHelper {

    public static Layer getDefaultLayer() {
        return new Layer() {

            @Override
            public float getAlpha() {
                return 1;
            }

            @Override
            public int getBlendingMode() {
                return 0;
            }

            @Override
            public boolean skip() {
                return false;
            }

            @Override
            public int compareTo(@NotNull Layer o) {
                if (this.equals(o)) return 0; // Same object ?
                return -1; // No : then below
            }
        };
    }

    /**
     * Most simple interface.
     * Allows for backward compatibility.
     * This default interface makes this projector equivalent to bdv's default projector
     * @return a default SourcesMetadata implementation with no alpha sources
     */
    public static SourcesMetadata getDefaultSourcesMetadata() {
        return new SourcesMetadata() {
            @Override
            public boolean isAlphaSource(SourceAndConverter<?> sac) {
                return false;
            }

            @Override
            public boolean hasAlphaSource(SourceAndConverter<?> sac) {
                return false;
            }

            @Override
            public SourceAndConverter<FloatType> getAlphaSource(SourceAndConverter<?> sac) {
                return null;
            };
        };
    }

}
