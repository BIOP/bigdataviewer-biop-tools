/*-
 * #%L
 * BigDataViewer-Playground
 * %%
 * Copyright (C) 2019 - 2026 Nicolas Chiaruttini, EPFL - Robert Haase, MPI CBG - Christian Tischer, EMBL
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
package bdv.util.source.labkit;

import bdv.viewer.SourceAndConverter;
import com.google.gson.*;
import org.scijava.Context;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.adapter.source.ISourceAdapter;
import sc.fiji.bdvpg.services.SourceAndConverterAdapter;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceLabkitClassifier;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON adapter for serializing and deserializing {@link LabkitSource}.
 * <p>
 * This adapter allows LabkitSource instances to be saved and restored
 * as part of BDV session serialization.
 * </p>
 * <p>
 * Note: Only sources created with a classifier path can be serialized.
 * Sources created with a pre-loaded Segmenter cannot be serialized as
 * the trained model state is not easily serializable.
 * </p>
 */
@Plugin(type = ISourceAdapter.class)
public class LabkitSourceAdapter implements ISourceAdapter<LabkitSource> {

    private static final Logger logger = LoggerFactory.getLogger(LabkitSourceAdapter.class);

    SourceAndConverterAdapter sacSerializer;

    @Override
    public void setSacSerializer(SourceAndConverterAdapter sacSerializer) {
        this.sacSerializer = sacSerializer;
    }

    @Override
    public Class<LabkitSource> getSourceClass() {
        return LabkitSource.class;
    }

    @Override
    public JsonElement serialize(SourceAndConverter sac, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject obj = new JsonObject();

        LabkitSource<?> source = (LabkitSource<?>) sac.getSpimSource();

        obj.addProperty("type", LabkitSource.class.getSimpleName());
        obj.addProperty("name", source.getName());
        obj.addProperty("classifier_path", source.getClassifierPath());
        obj.addProperty("resolution_level", source.getResolutionLevel());
        obj.addProperty("use_gpu", source.isUseGpu());

        // Check if classifier path is available (required for serialization)
        if (source.getClassifierPath() == null) {
            logger.error("Cannot serialize LabkitSource '{}': no classifier path available (source was created with a pre-loaded Segmenter)", source.getName());
            return null;
        }

        // Serialize the input source IDs
        SourceAndConverter<?>[] inputSources = source.getInputSources();
        JsonArray sourceIds = new JsonArray();
        for (SourceAndConverter<?> inputSource : inputSources) {
            Integer id = sacSerializer.getSourceToId().get(inputSource.getSpimSource());
            if (id == null) {
                logger.error("Cannot serialize LabkitSource '{}': input source '{}' not identified",
                        source.getName(), inputSource.getSpimSource().getName());
                return null;
            }
            sourceIds.add(id);
        }
        obj.add("input_source_ids", sourceIds);

        return obj;
    }

    @Override
    public SourceAndConverter deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject obj = jsonElement.getAsJsonObject();

        String name = obj.getAsJsonPrimitive("name").getAsString();
        String classifierPath = obj.getAsJsonPrimitive("classifier_path").getAsString();
        int resolutionLevel = obj.getAsJsonPrimitive("resolution_level").getAsInt();
        boolean useGpu = obj.has("use_gpu") && obj.getAsJsonPrimitive("use_gpu").getAsBoolean();

        // Deserialize input sources
        JsonArray sourceIdsArray = obj.getAsJsonArray("input_source_ids");
        List<SourceAndConverter<?>> inputSourcesList = new ArrayList<>();

        for (JsonElement idElement : sourceIdsArray) {
            int sourceId = idElement.getAsInt();
            SourceAndConverter<?> inputSac;

            if (sacSerializer.getIdToSac().containsKey(sourceId)) {
                // Already deserialized
                inputSac = sacSerializer.getIdToSac().get(sourceId);
            } else {
                // Should be deserialized first
                JsonElement element = sacSerializer.idToJsonElement.get(sourceId);
                inputSac = sacSerializer.getGson().fromJson(element, SourceAndConverter.class);
            }

            if (inputSac == null) {
                logger.error("Couldn't deserialize input source (id={}) for LabkitSource '{}'", sourceId, name);
                return null;
            }

            inputSourcesList.add(inputSac);
        }

        SourceAndConverter<?>[] inputSources = inputSourcesList.toArray(new SourceAndConverter[0]);

        // Get the SciJava context
        Context context = sacSerializer.getScijavaContext();

        // Create the LabkitSource using SourceLabkitClassifier
        SourceLabkitClassifier classifier = new SourceLabkitClassifier(
                inputSources,
                classifierPath,
                context,
                name,
                resolutionLevel,
                useGpu
        );

        SourceAndConverter sac = classifier.get();

        SourceAndConverterServices.getSourceAndConverterService().register(sac);

        return sac;
    }
}
