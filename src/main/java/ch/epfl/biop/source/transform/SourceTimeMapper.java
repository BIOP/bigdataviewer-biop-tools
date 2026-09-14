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

import bdv.util.source.time.MappedTimeSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;

import java.util.function.Function;
import sc.fiji.bdvpg.source.SourceHelper;

public class SourceTimeMapper implements Runnable, Function<SourceAndConverter, SourceAndConverter> {

    SourceAndConverter source_in;
    Function<Integer, Integer> timeMapper;
    private String name;

    public SourceTimeMapper(SourceAndConverter source_in, Function<Integer, Integer> timeMapper, String name) {
        this.name = name;
        this.timeMapper = timeMapper;
        this.source_in = source_in;
    }

    public void run() {
    }

    public SourceAndConverter get() {
        return this.apply(this.source_in);
    }

    public SourceAndConverter apply(SourceAndConverter src) {
        Source srcRsampled = new MappedTimeSource(src.getSpimSource(), this.name, timeMapper);
        SourceAndConverter source;
        if (src.asVolatile() != null) {
            MappedTimeSource vsrcRsampled = new MappedTimeSource(src.asVolatile().getSpimSource(), this.name, timeMapper);
            SourceAndConverter vsource = new SourceAndConverter((Source)vsrcRsampled, SourceHelper.cloneConverter(src.asVolatile().getConverter(), src.asVolatile()));
            source = new SourceAndConverter(srcRsampled, SourceHelper.cloneConverter(src.getConverter(), src), vsource);
        } else {
            source = new SourceAndConverter(srcRsampled, SourceHelper.cloneConverter(src.getConverter(), src));
        }
        return source;
    }
}
