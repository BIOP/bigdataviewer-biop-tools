/*-
 * #%L
 * BigDataViewer tools and plugins for Fiji: fusion, deconvolution, registration and data processing.
 * %%
 * Copyright (C) 2018 - 2026 EPFL BIOP and University of Geneva
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
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
