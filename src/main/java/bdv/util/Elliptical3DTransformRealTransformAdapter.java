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
import net.imglib2.realtransform.RealTransform;
import org.scijava.plugin.Plugin;
import sc.fiji.persist.IClassRuntimeAdapter;

import java.lang.reflect.Type;
import java.util.Map;

@Plugin(type = IClassRuntimeAdapter.class)
public class Elliptical3DTransformRealTransformAdapter implements IClassRuntimeAdapter<RealTransform, Elliptical3DTransform> {
    @Override
    public Class<? extends RealTransform> getBaseClass() {
        return RealTransform.class;
    }

    @Override
    public Class<? extends Elliptical3DTransform> getRunTimeClass() {
        return Elliptical3DTransform.class;
    }

    @Override
    public boolean useCustomAdapter() {
        return true;
    }

    @Override
    public Elliptical3DTransform deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject obj = jsonElement.getAsJsonObject();

        Map<String, Double> params = jsonDeserializationContext.deserialize(obj.get("ellipse_params"), Map.class);

        Elliptical3DTransform elliptical3DTransform = new Elliptical3DTransform();

        elliptical3DTransform.setParameters(params);

        if (obj.has("name")) { // for older transform compatibility
            elliptical3DTransform.setName(obj.getAsJsonPrimitive("name").getAsString());
        }

        return elliptical3DTransform;
    }

    @Override
    public JsonElement serialize(Elliptical3DTransform elliptical3DTransform, Type type, JsonSerializationContext jsonSerializationContext) {
        Elliptical3DTransform rt = elliptical3DTransform;

        JsonObject obj = new JsonObject();

        obj.addProperty("name", elliptical3DTransform.getName());

        obj.add("ellipse_params", jsonSerializationContext.serialize(rt.getParameters()));

        return obj;
    }
}
