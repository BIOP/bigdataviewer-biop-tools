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
package ch.epfl.biop.source.processor.adapter;

import ch.epfl.biop.source.processor.SourcesChannelsSelect;
import com.google.gson.*;

import java.lang.reflect.Type;
/**
 * Adapter of the {@link SourcesChannelsSelect} class
 */
public class SourcesChannelSelectAdapter implements JsonSerializer<SourcesChannelsSelect>,
        JsonDeserializer<SourcesChannelsSelect> {

    @Override
    public SourcesChannelsSelect deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        Integer[] channels_indices = context.deserialize(json.getAsJsonObject().get("channels_indices"), Integer[].class);
        return new SourcesChannelsSelect(channels_indices);
    }

    @Override
    public JsonElement serialize(SourcesChannelsSelect scs, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", SourcesChannelsSelect.class.getSimpleName());
        obj.add("channels_indices", context.serialize(scs.channels_indices.toArray(new Integer[0]), Integer[].class));
        return obj;
    }
}
