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
package ch.epfl.biop.source.deconvolve;

import bdv.util.source.process.VoxelProcessedSource;
import bdv.viewer.SourceAndConverter;
import com.google.gson.*;
import net.imglib2.type.numeric.RealType;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.persist.IClassRuntimeAdapter;

import java.lang.reflect.Type;

/**
 * JSON adapter for {@link DeconvolutionProcessor}.
 * <p>
 * Serializes all deconvolution parameters including the PSF source.
 * </p>
 */
@Plugin(type = IClassRuntimeAdapter.class)
public class DeconvolutionProcessorAdapter implements IClassRuntimeAdapter<VoxelProcessedSource.Processor, DeconvolutionProcessor> {

    private static final Logger logger = LoggerFactory.getLogger(DeconvolutionProcessorAdapter.class);

    @Override
    public Class<? extends VoxelProcessedSource.Processor> getBaseClass() {
        return VoxelProcessedSource.Processor.class;
    }

    @Override
    public Class<? extends DeconvolutionProcessor> getRunTimeClass() {
        return DeconvolutionProcessor.class;
    }

    @Override
    public boolean useCustomAdapter() {
        return true;
    }

    @Override
    public JsonElement serialize(DeconvolutionProcessor processor, Type type, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();

        // Serialize basic parameters
        obj.add("cell_dimensions", context.serialize(processor.getCellDimensions()));
        obj.add("overlap", context.serialize(processor.getOverlap()));
        obj.addProperty("num_iterations", processor.getNumIterations());
        obj.addProperty("non_circulant", processor.isNonCirculant());
        obj.addProperty("regularization_factor", processor.getRegularizationFactor());

        // Serialize PSF source via the SourceAndConverter serialization mechanism
        SourceAndConverter<?> psfSource = processor.getPsfSource();
        obj.add("psf_source", context.serialize(psfSource, SourceAndConverter.class));
        obj.add("raw_source", context.serialize(processor.getRawSource(), SourceAndConverter.class));

        return obj;
    }

    @Override
    public DeconvolutionProcessor deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = jsonElement.getAsJsonObject();

        // Deserialize basic parameters
        int[] cellDimensions = context.deserialize(obj.get("cell_dimensions"), int[].class);
        int[] overlap = context.deserialize(obj.get("overlap"), int[].class);
        int numIterations = obj.getAsJsonPrimitive("num_iterations").getAsInt();
        boolean nonCirculant = obj.getAsJsonPrimitive("non_circulant").getAsBoolean();
        float regularizationFactor = obj.getAsJsonPrimitive("regularization_factor").getAsFloat();

        // Deserialize PSF source
        @SuppressWarnings("unchecked")
        SourceAndConverter<? extends RealType<?>> psfSource =
                (SourceAndConverter<? extends RealType<?>>) context.deserialize(obj.get("psf_source"), SourceAndConverter.class);
        SourceAndConverter<? extends RealType<?>> rawSource =
                (SourceAndConverter<? extends RealType<?>>) context.deserialize(obj.get("raw_source"), SourceAndConverter.class);

        DeconvolutionProcessor p = new DeconvolutionProcessor<>(cellDimensions, overlap, numIterations, nonCirculant, regularizationFactor, psfSource);
        p.initialize(rawSource);
        return p;
    }
}
