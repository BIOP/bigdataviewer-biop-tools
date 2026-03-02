package ch.epfl.biop.source;

import bdv.util.WrapVolatileSource;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.cache.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import sc.fiji.bdvpg.source.SourceHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// TODO : check that at least a source is there
// TODO : Make volatile source if possible

public class SourceFuserAndResampler<T extends NumericType<T> & RealType<T> & NativeType<T>> implements Runnable, Function<List<SourceAndConverter<T>>, SourceAndConverter<T>> {

    List<SourceAndConverter<T>> sources_in;

    SourceAndConverter<?> model;

    boolean reuseMipMaps;

    boolean interpolate;

    boolean cache;

    int cacheX, cacheY, cacheZ;

    int defaultMipMapLevel;

    private String name;

    String blendingMode;

    private int nThreads;

    public SourceFuserAndResampler(List<SourceAndConverter<T>> sources_in,
                                   String blendingMode,
                                   SourceAndConverter<?> model,
                                   String name,
                                   boolean reuseMipmaps,
                                   boolean cache,
                                   boolean interpolate,
                                   int defaultMipMapLevel,
                                   int cacheX, int cacheY, int cacheZ, int cacheBounds,
                                   int nThreads ) {
        this.nThreads = nThreads;
        this.blendingMode = blendingMode;
        this.name = name;
        this.reuseMipMaps = reuseMipmaps;
        this.model = model;
        this.sources_in = sources_in;
        this.interpolate = interpolate;
        this.cache = cache;
        this.defaultMipMapLevel = defaultMipMapLevel;
        this.cacheX = cacheX;
        this.cacheY = cacheY;
        this.cacheZ = cacheZ;
    }

    @Override
    public void run() {

    }

    public SourceAndConverter<T> get() {
        return apply(sources_in);
    }

    @Override
    public SourceAndConverter<T> apply(List<SourceAndConverter<T>> srcs) {
        SourceAndConverter<?> sourceExample = srcs.get(0);
        // TODO : check all types are ok

        List<Source<T>> sources = new ArrayList<>();
        Map<Source<T>, Interpolation> interpolationMap = new HashMap<>();

        boolean volatileIsPossible = true;

        for (SourceAndConverter<T> source:srcs) {
            sources.add(source.getSpimSource());
            volatileIsPossible = volatileIsPossible&&(source.asVolatile()!=null);
            interpolationMap.put(source.getSpimSource(), interpolate?Interpolation.NLINEAR:Interpolation.NEARESTNEIGHBOR);
        }

        Source<?> srcRsampled =
                new AlphaFusedResampledSource<T>(
                        sources,
                        blendingMode,
                        model.getSpimSource(),
                        name,
                        reuseMipMaps,
                        cache,
                        interpolationMap,
                        defaultMipMapLevel,cacheX,cacheY,cacheZ);

        SourceAndConverter<T> source;

        if (volatileIsPossible) {
            SourceAndConverter vsource;
            Source vsrcRsampled;
            if (cache) {
                vsrcRsampled = new WrapVolatileSource(srcRsampled, new SharedQueue(nThreads));
            } else {

                sources = new ArrayList<>();
                interpolationMap = new HashMap<>();

                for (SourceAndConverter source_in:srcs) {
                    sources.add(source_in.asVolatile().getSpimSource());
                    interpolationMap.put(source_in.asVolatile().getSpimSource(), interpolate?Interpolation.NLINEAR:Interpolation.NEARESTNEIGHBOR);
                }

                vsrcRsampled = new AlphaFusedResampledSource(
                        sources,
                        blendingMode,
                        model.getSpimSource(),
                        name,
                        reuseMipMaps,
                        false,
                        interpolationMap,
                        defaultMipMapLevel,cacheX,cacheY,cacheZ);
            }
            vsource = new SourceAndConverter(vsrcRsampled,
                    SourceHelper.cloneConverter(sourceExample.asVolatile().getConverter(), sourceExample.asVolatile()));
            source = new SourceAndConverter(srcRsampled,
                    SourceHelper.cloneConverter(sourceExample.getConverter(), sourceExample ),vsource);
        } else {
            source = new SourceAndConverter(srcRsampled,
                    SourceHelper.cloneConverter(sourceExample.getConverter(), sourceExample));
        }

        return source;
    }
}
