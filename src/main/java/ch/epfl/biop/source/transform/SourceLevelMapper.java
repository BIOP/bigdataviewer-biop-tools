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

import bdv.util.source.level.MappedLevelSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;

import java.util.function.Function;

import sc.fiji.bdvpg.source.SourceHelper;

/**
 * Creates a new {@link SourceAndConverter} that exposes only a subset of resolution levels
 * from the original source.
 * <p>
 * This is useful for "cropping" the resolution pyramid to exclude either
 * high-resolution (fine detail) or low-resolution (coarse) levels.
 */
public class SourceLevelMapper<T> implements Runnable, Function<SourceAndConverter<T>, SourceAndConverter<T>> {

    private final SourceAndConverter<T> sourceIn;
    private final int minLevel;
    private final int maxLevel;
    private final String name;

    /**
     * Creates a new SourceLevelMapper.
     *
     * @param sourceIn the input SourceAndConverter to wrap
     * @param minLevel the minimum level index (inclusive) to keep
     * @param maxLevel the maximum level index (inclusive) to keep
     * @param name the name for the resulting source
     */
    public SourceLevelMapper(SourceAndConverter<T> sourceIn, int minLevel, int maxLevel, String name) {
        this.sourceIn = sourceIn;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.name = name;
    }

    @Override
    public void run() {
    }

    /**
     * Returns the transformed SourceAndConverter with cropped resolution levels.
     *
     * @return a new SourceAndConverter with the specified level range
     */
    public SourceAndConverter<T> get() {
        return this.apply(this.sourceIn);
    }

    @Override
    public SourceAndConverter<T> apply(SourceAndConverter src) {
        Source srcMapped = new MappedLevelSource(src.getSpimSource(), this.name, minLevel, maxLevel);
        SourceAndConverter source;
        if (src.asVolatile() != null) {
            MappedLevelSource vsrcMapped = new MappedLevelSource(
                    src.asVolatile().getSpimSource(), this.name, minLevel, maxLevel);
            SourceAndConverter vsource = new SourceAndConverter(
                    (Source) vsrcMapped,
                    SourceHelper.cloneConverter(src.asVolatile().getConverter(), src.asVolatile()));
            source = new SourceAndConverter(
                    srcMapped,
                    SourceHelper.cloneConverter(src.getConverter(), src),
                    vsource);
        } else {
            source = new SourceAndConverter(
                    srcMapped,
                    SourceHelper.cloneConverter(src.getConverter(), src));
        }
        return source;
    }
}
