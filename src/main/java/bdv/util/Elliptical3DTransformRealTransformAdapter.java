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

        return elliptical3DTransform;
    }

    @Override
    public JsonElement serialize(Elliptical3DTransform elliptical3DTransform, Type type, JsonSerializationContext jsonSerializationContext) {
        Elliptical3DTransform rt = elliptical3DTransform;

        JsonObject obj = new JsonObject();

        //obj.addProperty("type", Elliptical3DTransform.class.getSimpleName());

        obj.add("ellipse_params", jsonSerializationContext.serialize(rt.getParameters()));

        return obj;
    }
}
