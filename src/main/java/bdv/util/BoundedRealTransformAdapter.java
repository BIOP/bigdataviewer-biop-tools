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