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
package ch.epfl.biop.source.processor;

import bdv.viewer.SourceAndConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SourcesChannelsSelect implements SourcesProcessor {

    final public List<Integer> channels_indices;

    public SourcesChannelsSelect(List<Integer> channels_indices) {
        this.channels_indices = channels_indices;
    }

    public SourcesChannelsSelect(Integer... channels_indices) {
        this.channels_indices = Arrays.asList(channels_indices);
    }

    public SourcesChannelsSelect(int... channels_indices) {
        this.channels_indices = new ArrayList<>(channels_indices.length);
        for (int channels_index : channels_indices) {
            this.channels_indices.add(channels_index);
        }
    }

    public SourcesChannelsSelect(int channel) {
        this.channels_indices = new ArrayList<>(1);
        this.channels_indices.add(channel);
    }

    @Override
    public SourceAndConverter[] apply(SourceAndConverter[] sourceAndConverters) {
        SourceAndConverter[] sourcesSelected = new SourceAndConverter[channels_indices.size()];
        int idx = 0;
        for (Integer index : channels_indices) {
            sourcesSelected[idx] = sourceAndConverters[index];
            idx++;
        }
        return sourcesSelected;
    }

    public String toString() {
        return "Ch["+channels_indices.stream().map(Object::toString).collect(Collectors.joining(","))+"]";
    }
}
