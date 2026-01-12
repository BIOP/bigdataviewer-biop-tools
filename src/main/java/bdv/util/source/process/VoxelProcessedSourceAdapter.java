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
package bdv.util.source.process;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceVoxelProcessor;
import com.google.gson.*;
import net.imglib2.type.numeric.NumericType;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.adapter.source.ISourceAdapter;
import sc.fiji.bdvpg.services.SourceAndConverterAdapter;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.lang.reflect.Type;

/**
 * JSON adapter for serializing and deserializing {@link VoxelProcessedSource}.
 * <p>
 * This adapter handles the serialization of:
 * <ul>
 *   <li>The source name</li>
 *   <li>The origin source (as a reference ID)</li>
 *   <li>The processor (via registered processor adapters)</li>
 *   <li>The output type</li>
 * </ul>
 * </p>
 */
@Plugin(type = ISourceAdapter.class)
public class VoxelProcessedSourceAdapter implements ISourceAdapter<VoxelProcessedSource> {

    private static final Logger logger = LoggerFactory.getLogger(VoxelProcessedSourceAdapter.class);

    SourceAndConverterAdapter sacSerializer;

    @Override
    public void setSacSerializer(SourceAndConverterAdapter sacSerializer) {
        this.sacSerializer = sacSerializer;
    }

    @Override
    public Class<VoxelProcessedSource> getSourceClass() {
        return VoxelProcessedSource.class;
    }

    @Override
    public JsonElement serialize(SourceAndConverter sac, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject obj = new JsonObject();

        VoxelProcessedSource<?, ?> source = (VoxelProcessedSource<?, ?>) sac.getSpimSource();

        obj.addProperty("type", VoxelProcessedSource.class.getSimpleName());
        obj.addProperty("name", source.getName());

        // Serialize the origin source ID
        Source<?> originSource = source.getOriginSource();
        Integer originId = sacSerializer.getSourceToId().get(originSource);
        if (originId == null) {
            logger.error("Cannot serialize VoxelProcessedSource '{}': origin source '{}' not identified",
                    source.getName(), originSource.getName());
            return null;
        }
        obj.addProperty("origin_source_id", originId);

        // Serialize the processor
        VoxelProcessedSource.Processor<?, ?> processor = source.getProcessor();
        obj.addProperty("processor_class", processor.getClass().getName());
        try {
            obj.add("processor", jsonSerializationContext.serialize(processor));
        } catch (Exception e) {
            logger.error("Cannot serialize processor of type '{}' for VoxelProcessedSource '{}': {}",
                    processor.getClass().getName(), source.getName(), e.getMessage());
            return null;
        }

        // Serialize the output type
        NumericType<?> outputType = source.getOutputType();
        obj.addProperty("output_type_class", outputType.getClass().getName());

        return obj;
    }

    @Override
    public SourceAndConverter deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject obj = jsonElement.getAsJsonObject();

        String name = obj.getAsJsonPrimitive("name").getAsString();
        int originSourceId = obj.getAsJsonPrimitive("origin_source_id").getAsInt();
        String processorClassName = obj.getAsJsonPrimitive("processor_class").getAsString();
        String outputTypeClassName = obj.getAsJsonPrimitive("output_type_class").getAsString();

        // Deserialize the origin source
        SourceAndConverter<?> originSac;
        if (sacSerializer.getIdToSac().containsKey(originSourceId)) {
            originSac = sacSerializer.getIdToSac().get(originSourceId);
        } else {
            JsonElement element = sacSerializer.idToJsonElement.get(originSourceId);
            originSac = sacSerializer.getGson().fromJson(element, SourceAndConverter.class);
        }

        if (originSac == null) {
            logger.error("Couldn't deserialize origin source (id={}) for VoxelProcessedSource '{}'", originSourceId, name);
            return null;
        }

        // Deserialize the processor
        VoxelProcessedSource.Processor<?, ?> processor;
        try {
            Class<?> processorClass = Class.forName(processorClassName);
            processor = (VoxelProcessedSource.Processor<?, ?>) jsonDeserializationContext.deserialize(
                    obj.get("processor"), processorClass);
        } catch (ClassNotFoundException e) {
            logger.error("Processor class '{}' not found for VoxelProcessedSource '{}'", processorClassName, name);
            return null;
        } catch (Exception e) {
            logger.error("Failed to deserialize processor for VoxelProcessedSource '{}': {}", name, e.getMessage());
            return null;
        }

        // Create the output type instance
        NumericType<?> outputType;
        try {
            Class<?> outputTypeClass = Class.forName(outputTypeClassName);
            outputType = (NumericType<?>) outputTypeClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.error("Failed to create output type '{}' for VoxelProcessedSource '{}': {}",
                    outputTypeClassName, name, e.getMessage());
            return null;
        }

        // Create the VoxelProcessedSource using SourceVoxelProcessor
        SourceVoxelProcessor sourceVoxelProcessor = new SourceVoxelProcessor(
                name,
                originSac,
                processor,
                outputType,
                Runtime.getRuntime().availableProcessors()
        );

        SourceAndConverter<?> sac = sourceVoxelProcessor.get();

        SourceAndConverterServices.getSourceAndConverterService().register(sac);

        return sac;
    }
}
