/*-
 * #%L
 * Tiled GPU Deconvolution for BigDataViewer-Playground - BIOP - EPFL
 * %%
 * Copyright (C) 2024 - 2025 EPFL
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package ch.epfl.biop.sourceandconverter.deconvolve;

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
 * JSON adapter for {@link DeconvolutionProcessorCast}.
 * <p>
 * Serializes all deconvolution parameters including the PSF source.
 * </p>
 */
@Plugin(type = IClassRuntimeAdapter.class)
public class DeconvolutionProcessorCastAdapter implements IClassRuntimeAdapter<VoxelProcessedSource.Processor, DeconvolutionProcessorCast> {

    private static final Logger logger = LoggerFactory.getLogger(DeconvolutionProcessorCastAdapter.class);

    @Override
    public Class<? extends VoxelProcessedSource.Processor> getBaseClass() {
        return VoxelProcessedSource.Processor.class;
    }

    @Override
    public Class<? extends DeconvolutionProcessorCast> getRunTimeClass() {
        return DeconvolutionProcessorCast.class;
    }

    @Override
    public boolean useCustomAdapter() {
        return true;
    }

    @Override
    public JsonElement serialize(DeconvolutionProcessorCast processor, Type type, JsonSerializationContext context) {
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
    public DeconvolutionProcessorCast deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
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

        DeconvolutionProcessorCast p = new DeconvolutionProcessorCast<>(cellDimensions, overlap, numIterations, nonCirculant, regularizationFactor, psfSource);
        p.initialize(rawSource);
        return p;
    }
}
