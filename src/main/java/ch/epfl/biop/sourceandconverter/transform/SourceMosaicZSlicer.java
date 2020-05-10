package ch.epfl.biop.sourceandconverter.transform;

import bdv.util.ZSlicedSource;
import bdv.util.VolatileSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterUtils;

import java.util.function.Function;
import java.util.function.Supplier;

public class SourceMosaicZSlicer implements Runnable, Function<SourceAndConverter, SourceAndConverter> {

    SourceAndConverter sac_in;

    SourceAndConverter model;

    boolean reuseMipMaps;

    boolean interpolate;

    boolean cache;

    Supplier<Long> subSlicer;

    public SourceMosaicZSlicer(SourceAndConverter sac_in,
                               SourceAndConverter model,
                               boolean reuseMipmaps,
                               boolean cache,
                               boolean interpolate,
                               Supplier<Long> subSlicer) {
        this.reuseMipMaps = reuseMipmaps;
        this.model = model;
        this.sac_in = sac_in;
        this.interpolate = interpolate;
        this.cache = cache;
        this.subSlicer = subSlicer;
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
                new ZSlicedSource(
                        src.getSpimSource(),
                        model.getSpimSource(),
                        reuseMipMaps,
                        cache,
                        interpolate,
                        subSlicer);

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
                        new ZSlicedSource(
                            src.asVolatile().getSpimSource(),
                            model.getSpimSource(),
                            reuseMipMaps,
                            false,
                            interpolate,
                            subSlicer);
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