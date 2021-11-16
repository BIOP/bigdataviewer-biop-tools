package ch.epfl.biop.sourceandconverter.processor.adapter;

import ch.epfl.biop.sourceandconverter.processor.SourcesIdentity;
import ch.epfl.biop.sourceandconverter.processor.SourcesProcessComposer;
import com.google.gson.*;

import java.lang.reflect.Type;

/**
 * Adapter of the {@link SourcesIdentity} class
 */
public class SourcesIdentityAdapter implements JsonSerializer<SourcesIdentity>,
        JsonDeserializer<SourcesIdentity> {

    @Override
    public SourcesIdentity deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return new SourcesIdentity();
    }

    @Override
    public JsonElement serialize(SourcesIdentity spc, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", SourcesIdentity.class.getSimpleName());
        return obj;
    }
}
