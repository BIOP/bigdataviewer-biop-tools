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
import com.google.gson.*;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.persist.IClassRuntimeAdapter;

import java.lang.reflect.Type;

/**
 * JSON adapter for {@link DeconvolutionProcessor}.
 * <p>
 * Serializes all deconvolution parameters including the PSF (stored as raw float data).
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

        // Serialize PSF as raw float array with dimensions
        RandomAccessibleInterval<? extends RealType<?>> psf = processor.getPsf();
        long[] dims = psf.dimensionsAsLongArray();
        obj.add("psf_dimensions", context.serialize(dims));

        // Convert PSF to float array
        float[] psfData = new float[(int) (dims[0] * dims[1] * dims[2])];
        Cursor<? extends RealType<?>> cursor = Views.flatIterable(psf).cursor();
        int i = 0;
        while (cursor.hasNext()) {
            psfData[i++] = cursor.next().getRealFloat();
        }
        obj.add("psf_data", context.serialize(psfData));

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

        // Deserialize PSF
        long[] psfDims = context.deserialize(obj.get("psf_dimensions"), long[].class);
        float[] psfData = context.deserialize(obj.get("psf_data"), float[].class);

        // Reconstruct PSF as ArrayImg
        ArrayImg<FloatType, FloatArray> psf = ArrayImgs.floats(psfData, psfDims);

        return new DeconvolutionProcessor<>(cellDimensions, overlap, numIterations, nonCirculant, regularizationFactor, psf);
    }
}
