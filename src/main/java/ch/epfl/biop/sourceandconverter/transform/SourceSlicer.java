package ch.epfl.biop.sourceandconverter.transform;

import bdv.util.SlicedSource;
import bdv.util.VolatileSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterUtils;

import java.util.function.Function;

public class SourceSlicer implements Runnable, Function<SourceAndConverter, SourceAndConverter> {

    SourceAndConverter sac_in;

    SourceAndConverter model;

    boolean reuseMipMaps;

    boolean interpolate;

    boolean cache;

    public SourceSlicer(SourceAndConverter sac_in, SourceAndConverter model, boolean reuseMipmaps, boolean cache, boolean interpolate) {
        this.reuseMipMaps = reuseMipmaps;
        this.model = model;
        this.sac_in = sac_in;
        this.interpolate = interpolate;
        this.cache = cache;
    }

    @Override
    public void run() {

    }

    public SourceAndConverter get() {
        return apply(sac_in);
    }

    @Override
    public SourceAndConverter apply(SourceAndConverter src) {
        Source srcRsampled =
                new SlicedSource(
                        src.getSpimSource(),
                        model.getSpimSource(),
                        reuseMipMaps,
                        cache,
                        interpolate);

        SourceAndConverter sac;
        if (src.asVolatile()!=null) {
            SourceAndConverter vsac;
            Source vsrcRsampled;
            if (cache) {
                vsrcRsampled =
                        new VolatileSource(srcRsampled)/*
                            src.asVolatile().getSpimSource(),
                            model.getSpimSource(),
                            reuseMipMaps,
                            interpolate)*/;
            } else {
                vsrcRsampled =
                        new SlicedSource(
                            src.asVolatile().getSpimSource(),
                            model.getSpimSource(),
                            reuseMipMaps,
                            false,
                            interpolate);
            }
            vsac = new SourceAndConverter(vsrcRsampled,
                    SourceAndConverterUtils.cloneConverter(src.asVolatile().getConverter()));
            sac = new SourceAndConverter<>(srcRsampled,
                    SourceAndConverterUtils.cloneConverter(src.getConverter()),vsac);
        } else {
            sac = new SourceAndConverter<>(srcRsampled,
                    SourceAndConverterUtils.cloneConverter(src.getConverter()));
        }

        return sac;
    }
}
