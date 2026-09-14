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

import ch.epfl.biop.source.processor.SourcesProcessComposer;
import ch.epfl.biop.source.processor.SourcesProcessor;
import com.google.gson.*;

import java.lang.reflect.Type;

/**
 * Adapter of the {@link SourcesProcessComposer} class
 */
public class SourcesComposerAdapter implements JsonSerializer<SourcesProcessComposer>,
        JsonDeserializer<SourcesProcessComposer> {

    @Override
    public SourcesProcessComposer deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        SourcesProcessor sp1 = context.deserialize(json.getAsJsonObject().get("f1"), SourcesProcessor.class);
        SourcesProcessor sp2 = context.deserialize(json.getAsJsonObject().get("f2"), SourcesProcessor.class);
        return new SourcesProcessComposer(sp2,sp1);
    }

    @Override
    public JsonElement serialize(SourcesProcessComposer spc, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", SourcesProcessComposer.class.getSimpleName());
        obj.add("f1", context.serialize(spc.f1));
        obj.add("f2", context.serialize(spc.f2));
        return obj;
    }
}
