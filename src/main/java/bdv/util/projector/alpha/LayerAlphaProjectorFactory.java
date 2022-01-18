package bdv.util.projector.alpha;

import bdv.viewer.SourceAndConverter;
import bdv.viewer.render.AccumulateProjector;
import bdv.viewer.render.AccumulateProjectorFactory;
import bdv.viewer.render.VolatileProjector;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.FloatType;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;

/**
 * Projector which can be used by {@link bdv.BigDataViewer} in order to handle sources transparency and alpha blending
 *
 * For this projector to work as expected, each source should be associated to an alpha source also present in the projector
 *
 * Currently, this repository (bigdataviewer-playground-display) provides a mechanism to semi-conveniently use this projector.
 * Briefly, if a BigDataViewer window is created via the use of {@link sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier},
 * listeners are created which:
 * * synchronizes the display of alpha sources each time a new source is displayed in bdv
 * * in fact, this synchronization mechanism CREATES the alpha source when needed
 * * a caching mechanism using weak keys in {@link sc.fiji.bdvpg.services.SourceAndConverterServices} allows to reuse
 * alpha sources when needed ( in a different window for instance )
 *
 * Alpha sources {@link bdv.util.source.alpha.IAlphaSource} and {@link bdv.util.source.alpha.AlphaSource} are using
 * {@link FloatType} because these are 32 bits, which allows to trick the projector into fitting a float value into
 * the space of an ARGB int value ( see {@link bdv.util.source.alpha.AlphaConverter} and accumulate method in here
 * which uses `Float.intBitsToFloat(access_alpha.get().get());`
 *
 * In terms of performance, this projector goes around 2.6 x slower than the default projector : 30% for the projector
 * overhead + factor 2 because the number of sources are multiplied by 2.
 *
 * @author Nicolas Chiaruttini, EPFL, 2021
 */

public class LayerAlphaProjectorFactory implements AccumulateProjectorFactory<ARGBType> {

    /**
     * Most simple interface.
     * Allows for backward compatibility.
     * This default interface makes this projector equivalent to bdv's default projector
     */
    transient SourcesMetadata sourcesMeta = new SourcesMetadata() {
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
        }
    };

    /**
     * A single default Layer, fully opaque
     */
    Layer defaultLayer = new Layer() {

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

    /**
     * Most simple layer metadata, all sources are in the default layer
     */
    transient LayerMetadata layerMeta = sac -> defaultLayer;

    public LayerAlphaProjectorFactory() {

    }

    public LayerAlphaProjectorFactory(SourcesMetadata sourcesMeta, LayerMetadata layerMeta) {
        this.sourcesMeta = sourcesMeta;
        this.layerMeta = layerMeta;
    }

    public void setSourcesMeta(SourcesMetadata sourcesMeta) {
        this.sourcesMeta = sourcesMeta;
    }

    public void setLayerMeta(LayerMetadata layerMeta) {
        this.layerMeta = layerMeta;
    }

    public VolatileProjector createProjector(
            final List< VolatileProjector > sourceProjectors,
            final List<SourceAndConverter< ? >> sources,
            final List<? extends RandomAccessible<? extends ARGBType>> sourceScreenImages,
            final RandomAccessibleInterval<ARGBType> targetScreenImage,
            final int numThreads,
            final ExecutorService executorService )
    {
        return new AccumulateProjectorARGBGeneric(sourcesMeta, layerMeta, sourceProjectors, sources, sourceScreenImages, targetScreenImage, numThreads, executorService );
    }

    /**
     * Briefly the sources are sorted by order given by the {@link Layer#compareTo(Object)} method,
     * and each source copies the layer property into an array. The indexing is a bit annoying to do,
     * but only done once.
     */
    public static class AccumulateProjectorARGBGeneric extends AccumulateProjector< ARGBType, ARGBType >
    {

        final boolean[] source_is_alpha; // flags if the source in an alpha source ( not displayed )
        final boolean[] source_has_alpha; // flag if the source has an alpha channel, present in the list of sources
        final int[] source_linked_alpha_source_index; // index of the alpha channel, if any ( has_alpha is false otherwise )
        final int[] sources_sorted_indices; // index of sources, ordered from lower to higher layer
        final boolean[] source_layer_skip;
        final float[] source_layer_alpha;
        final int[] source_layer_mode;
        final boolean[] source_layer_next; // Flags when it's the start of the next layer

        public AccumulateProjectorARGBGeneric(
                SourcesMetadata sourcesMeta,
                LayerMetadata layerMeta,
                final List< VolatileProjector > sourceProjectors,
                final List<SourceAndConverter< ? >> sources,
                final List< ? extends RandomAccessible< ? extends ARGBType > > sourceScreenImages,
                final RandomAccessibleInterval< ARGBType > target,
                final int numThreads,
                final ExecutorService executorService
                )
        {
            super( sourceProjectors, sourceScreenImages, target, numThreads, executorService );
            source_linked_alpha_source_index = new int[sources.size()];
            source_is_alpha = new boolean[sources.size()];
            source_has_alpha = new boolean[sources.size()];

            // Let's sort which sources are alpha, which are not, and which contain alpha channels, actually present in the projector
            for (int index_source=0; index_source<sources.size(); index_source++) {
                SourceAndConverter<?> source = sources.get(index_source);
                int index_alpha_source;
                if (sourcesMeta.isAlphaSource(source)) {
                    source_is_alpha[index_source] = true;
                } else {
                    // It's not an alpha source
                    if (sourcesMeta.hasAlphaSource(source)) {
                        // It has an alpha, source, but is it in the list of sources ?
                        index_alpha_source = sources.indexOf(sourcesMeta.getAlphaSource(source)); // returns -1 if the source does not exist
                        if (index_alpha_source!=-1) {
                            source_has_alpha[index_source] = true;
                            source_linked_alpha_source_index[index_source] = index_alpha_source;
                        } else {
                            source_has_alpha[index_source] = false;
                        }
                    } else {
                        source_has_alpha[index_source] = false;
                    }
                }
            }

            // Now let's take layers into account
            // Simply puts all layers into an array
            Layer[] layer_array = new Layer[sources.size()];
            for (int index_source=0; index_source<sources.size(); index_source++) {
                layer_array[index_source] = layerMeta.getLayer(sources.get(index_source));
            }

            // We need to re-index the sources to iterate them from lowest layer to the highest id of the layers
            sources_sorted_indices = IntStream.range(0,sources.size())
                    .boxed()
                    .sorted(Comparator.comparing(index -> layerMeta.getLayer(sources.get(index)), Layer::compareTo))
                    .mapToInt(i -> i).toArray();

            // Many duplicated values, but convenient : stores layer properties for all sources
            source_layer_skip = new boolean[sources.size()];
            source_layer_alpha = new float[sources.size()];
            source_layer_mode = new int[sources.size()];
            source_layer_next= new boolean[sources.size()]; // Flags when it's the start of the next layer

            for (int i=0;i<sources.size();i++) {
                int source_index = sources_sorted_indices[i];
                Layer current_layer = layer_array[source_index];
                source_layer_skip[source_index] = current_layer.skip();
                source_layer_alpha[source_index] = current_layer.getAlpha();
                source_layer_mode[source_index] = current_layer.getBlendingMode();
                if (i==sources.size()-1) {
                    source_layer_next[source_index] = true; // Last source : need to draw this last layer
                } else {
                    source_layer_next[source_index] = current_layer.compareTo(layer_array[sources_sorted_indices[i+1]])!=0;//((current_layer.getId())!=(layer_array[sources_sorted_indices[i+1]].getId()));
                }

            }

        }

        /**
         * In the accumulator, the xSum temp variables store the current value of the previous layers while
         * the xLayer temp variables store the current layer being accumulated. Once a layer is finished,
         * xSum is updated with xSum = (1-alpha).xSum + alpha.xLayer
         * @param accesses accesses
         * @param target target pixel
         */
        @Override
        protected void accumulate(final Cursor< ? extends ARGBType >[] accesses, final ARGBType target )
        {
            int aSum = 255, rSum = 255, gSum = 255, bSum = 255;
            int aLayer = 0, rLayer = 0, gLayer = 0, bLayer = 0;

            // Initialisation before the loop
            int current_source_index;
            boolean skip_current_layer;
            int nSources = 0;
            float totalAlpha = 0;

            for (int i=0;i<accesses.length;i++) {
                current_source_index = sources_sorted_indices[i];
                skip_current_layer = source_layer_skip[current_source_index];
                if (!skip_current_layer) {
                    if (!source_is_alpha[current_source_index]) {
                        // Has an alpha channel : uses the alpha channel for the projection
                        if (source_has_alpha[current_source_index]) {
                            final Cursor< ? extends ARGBType > access = accesses[current_source_index];
                            final Cursor< ? extends ARGBType > access_alpha = accesses[source_linked_alpha_source_index[current_source_index]];
                            final float alpha = Float.intBitsToFloat(access_alpha.get().get());
                            final int value = access.get().get();
                            final int a = (int) (ARGBType.alpha( value )*alpha);
                            final int r = (int) (ARGBType.red( value )*alpha);
                            final int g = (int) (ARGBType.green( value )*alpha);
                            final int b = (int) (ARGBType.blue( value )*alpha);
                            if (a!=0) { // But why is a equal to zero ?
                                aLayer += a;
                                rLayer += r;
                                gLayer += g;
                                bLayer += b;
                                if (alpha > 0) nSources++;
                                totalAlpha += alpha;
                            }
                        } else {
                            // No alpha channel: standard sum
                            final int value = accesses[current_source_index].get().get();
                            final int a = ARGBType.alpha( value );
                            final int r = ARGBType.red( value );
                            final int g = ARGBType.green( value );
                            final int b = ARGBType.blue( value );
                            if (a!=0) { // But why is a equal to zero ?
                                aLayer += a;
                                rLayer += r;
                                gLayer += g;
                                bLayer += b;
                                nSources++;
                                totalAlpha += 1;
                            }
                        }
                    }
                }
                if (source_layer_next[current_source_index]) {
                    // Append layer value
                    if (nSources>0) {
                        if (!skip_current_layer) {
                            float alpha = source_layer_alpha[current_source_index] * totalAlpha / (float) nSources;
                            aSum = (int)((1-alpha)*aSum+alpha*aLayer);
                            rSum = (int)((1-alpha)*rSum+alpha*rLayer);
                            gSum = (int)((1-alpha)*gSum+alpha*gLayer);
                            bSum = (int)((1-alpha)*bSum+alpha*bLayer);
                        }
                    }
                    aLayer = 0;
                    rLayer = 0;
                    gLayer = 0;
                    bLayer = 0;
                    nSources = 0;
                    totalAlpha = 0;
                }
            }

            if ( aSum > 255 )
                aSum = 255;
            if ( rSum > 255 )
                rSum = 255;
            if ( gSum > 255 )
                gSum = 255;
            if ( bSum > 255 )
                bSum = 255;
            target.set( ARGBType.rgba( rSum, gSum, bSum, aSum ) );
        }
    }

    /**
     * Minimal interface required for the projector in order to link a source to its alpha buddy
     */
    public interface SourcesMetadata {
        boolean isAlphaSource(SourceAndConverter<?> sac);
        boolean hasAlphaSource(SourceAndConverter<?> sac);
        SourceAndConverter<FloatType> getAlphaSource(SourceAndConverter<?> sac);
    }

    /**
     * Minimal interface for linking sources to their layer
     */
    public interface LayerMetadata {
        Layer getLayer(SourceAndConverter<?> sac);
    }

    /**
     * Minimal interface for layers : being able to get an alpha value, and
     * know whether to skip it or not, even though the skipping part should
     * be done upstream, by removing the sources from the projector
     */
    public interface Layer extends Comparable<Layer> {
        float getAlpha(); // 1 = fully opaque
        int getBlendingMode(); // 0 = SUM, 1 = AVERAGE TODO , currently only sum
        boolean skip();
    }

}
