package ch.epfl.biop.source.transform;

import bdv.util.WrapVolatileSource;
import bdv.util.ZSlicedSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import sc.fiji.bdvpg.source.SourceHelper;

import java.util.function.Function;
import java.util.function.Supplier;

public class SourceMosaicZSlicer implements Runnable, Function<SourceAndConverter, SourceAndConverter> {

    SourceAndConverter source_in;

    SourceAndConverter model;

    boolean reuseMipMaps;

    boolean interpolate;

    boolean cache;

    Supplier<Long> subSlicer;

    public SourceMosaicZSlicer(SourceAndConverter source_in,
                               SourceAndConverter model,
                               boolean reuseMipmaps,
                               boolean cache,
                               boolean interpolate,
                               Supplier<Long> subSlicer) {
        this.reuseMipMaps = reuseMipmaps;
        this.model = model;
        this.source_in = source_in;
        this.interpolate = interpolate;
        this.cache = cache;
        this.subSlicer = subSlicer;
    }

    @Override
    public void run() {

    }

    public SourceAndConverter get() {
        return apply(source_in);
    }

    @Override
    public SourceAndConverter apply(SourceAndConverter src) {
        Source srcRsampled =
                new ZSlicedSource(
                        src.getSpimSource(),
                        model.getSpimSource(),
                        "ZSliced_"+src.getSpimSource().getName(),
                        reuseMipMaps,
                        cache,
                        interpolate,
                        subSlicer);

        SourceAndConverter source;
        if (src.asVolatile()!=null) {
            SourceAndConverter vsource;
            Source vsrcRsampled;
            if (cache) {
                vsrcRsampled =
                        new WrapVolatileSource(srcRsampled)/*
                            src.asVolatile().getSpimSource(),
                            model.getSpimSource(),
                            reuseMipMaps,
                            interpolate)*/;
            } else {
                vsrcRsampled =
                        new ZSlicedSource(
                            src.asVolatile().getSpimSource(),
                            model.getSpimSource(),
                                "ZSliced_"+src.getSpimSource().getName(),
                            reuseMipMaps,
                            false,
                            interpolate,
                            subSlicer);
            }
            vsource = new SourceAndConverter(vsrcRsampled,
                    SourceHelper.cloneConverter(src.asVolatile().getConverter(), src.asVolatile()));
            source = new SourceAndConverter<>(srcRsampled,
                    SourceHelper.cloneConverter(src.getConverter(), src),vsource);
        } else {
            source = new SourceAndConverter<>(srcRsampled,
                    SourceHelper.cloneConverter(src.getConverter(), src));
        }

        return source;
    }
}
