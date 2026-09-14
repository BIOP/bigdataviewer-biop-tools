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
package bdv.util;

import com.google.gson.*;
import net.imglib2.FinalRealInterval;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealTransform;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.persist.IClassRuntimeAdapter;

import java.lang.reflect.Type;

/**
 * Runtime adapter of {@link BoundedRealTransform} class
 *
 * TODO: fix how this serializer is done
 */

@Plugin(type = IClassRuntimeAdapter.class)
public class BoundedRealTransformAdapter implements IClassRuntimeAdapter<RealTransform, BoundedRealTransform> {

    private static Logger logger = LoggerFactory.getLogger(BoundedRealTransformAdapter.class);

    @Override
    public Class<? extends RealTransform> getBaseClass() {
        return RealTransform.class;
    }

    @Override
    public Class<? extends BoundedRealTransform> getRunTimeClass() {
        return BoundedRealTransform.class;
    }

    @Override
    public boolean useCustomAdapter() {
        return true;
    }

    @Override
    public BoundedRealTransform deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject obj = jsonElement.getAsJsonObject();

        RealTransform rt = jsonDeserializationContext.deserialize(obj.get("realTransform"), RealTransform.class);

        if (!(rt instanceof InvertibleRealTransform)) {
            logger.error("Error during deserialization of BoundedRealTransform : The serialized transform is not invertible");
            return null;
        }

        double[] min = jsonDeserializationContext.deserialize(obj.get("interval_min"), double[].class);

        double[] max = jsonDeserializationContext.deserialize(obj.get("interval_max"), double[].class);

        FinalRealInterval fri = new FinalRealInterval(min, max);

        return new BoundedRealTransform((InvertibleRealTransform) rt, fri);
    }

    @Override
    public JsonElement serialize(BoundedRealTransform brt, Type type, JsonSerializationContext jsonSerializationContext) {

        JsonObject obj = new JsonObject();

        FinalRealInterval fri = new FinalRealInterval(brt.getInterval());

        obj.add("realTransform", jsonSerializationContext.serialize(brt.getTransform(), RealTransform.class));

        obj.add("interval_min", jsonSerializationContext.serialize(fri.minAsDoubleArray()));

        obj.add("interval_max", jsonSerializationContext.serialize(fri.maxAsDoubleArray()));

        return obj;
    }
}
