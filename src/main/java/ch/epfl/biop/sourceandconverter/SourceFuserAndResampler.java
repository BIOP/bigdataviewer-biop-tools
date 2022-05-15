package ch.epfl.biop.sourceandconverter;

import bdv.util.VolatileSource;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// TODO : check that at least a source is there
// TODO : Make volatile source if possible

public class SourceFuserAndResampler implements Runnable, Function<List<SourceAndConverter>, SourceAndConverter> {

    List<SourceAndConverter> sacs_in;

    SourceAndConverter model;

    boolean reuseMipMaps;

    boolean interpolate;

    boolean cache;

    int cacheX, cacheY, cacheZ, cacheBounds;

    int defaultMipMapLevel;

    private String name;

    String blendingMode;

    private int nThreads;

    public SourceFuserAndResampler(List<SourceAndConverter> sacs_in,
                                   String blendingMode,
                                   SourceAndConverter model,
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
        this.sacs_in = sacs_in;
        this.interpolate = interpolate;
        this.cache = cache;
        this.defaultMipMapLevel = defaultMipMapLevel;
        this.cacheX = cacheX;
        this.cacheY = cacheY;
        this.cacheZ = cacheZ;
        this.cacheBounds = cacheBounds;
    }

    @Override
    public void run() {

    }

    public SourceAndConverter get() {
        return apply(sacs_in);
    }

    @Override
    public SourceAndConverter apply(List<SourceAndConverter> srcs) {
        SourceAndConverter sacExample = srcs.get(0);
        // TODO : check all types are ok

        List<Source> sources = new ArrayList<>();
        Map<Source, Interpolation> interpolationMap = new HashMap<>();

        boolean volatileIsPossible = true;

        for (SourceAndConverter sac:srcs) {
            sources.add(sac.getSpimSource());
            volatileIsPossible = volatileIsPossible&&(sac.asVolatile()!=null);
            interpolationMap.put(sac.getSpimSource(), interpolate?Interpolation.NLINEAR:Interpolation.NEARESTNEIGHBOR);
        }

        Source srcRsampled =
                new AlphaFusedResampledSource(
                        sources,
                        blendingMode,
                        model.getSpimSource(),
                        name,
                        reuseMipMaps,
                        cache,
                        interpolationMap,
                        defaultMipMapLevel,cacheX,cacheY,cacheZ, cacheBounds);

        SourceAndConverter sac;

        if (volatileIsPossible) {
            SourceAndConverter vsac;
            Source vsrcRsampled;
            if (cache) {
                vsrcRsampled = new VolatileSource(srcRsampled, new SharedQueue(nThreads));
            } else {

                sources = new ArrayList<>();
                interpolationMap = new HashMap<>();

                for (SourceAndConverter sac_in:srcs) {
                    sources.add(sac_in.asVolatile().getSpimSource());
                    interpolationMap.put(sac_in.asVolatile().getSpimSource(), interpolate?Interpolation.NLINEAR:Interpolation.NEARESTNEIGHBOR);
                }

                vsrcRsampled = new AlphaFusedResampledSource(
                        sources,
                        blendingMode,
                        model.getSpimSource(),
                        name,
                        reuseMipMaps,
                        false,
                        interpolationMap,
                        defaultMipMapLevel,cacheX,cacheY,cacheZ, cacheBounds);
            }
            vsac = new SourceAndConverter(vsrcRsampled,
                    SourceAndConverterHelper.cloneConverter(sacExample.asVolatile().getConverter(), sacExample.asVolatile()));
            sac = new SourceAndConverter<>(srcRsampled,
                    SourceAndConverterHelper.cloneConverter(sacExample.getConverter(), sacExample ),vsac);
        } else {
            sac = new SourceAndConverter<>(srcRsampled,
                    SourceAndConverterHelper.cloneConverter(sacExample.getConverter(), sacExample));
        }

        return sac;
    }
}
