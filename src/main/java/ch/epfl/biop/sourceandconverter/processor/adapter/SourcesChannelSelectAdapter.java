package ch.epfl.biop.sourceandconverter.processor.adapter;

import ch.epfl.biop.sourceandconverter.processor.SourcesChannelsSelect;
import com.google.gson.*;

import java.lang.reflect.Type;
/**
 * Adapter of the {@link SourcesChannelsSelect} class
 */
public class SourcesChannelSelectAdapter implements JsonSerializer<SourcesChannelsSelect>,
        JsonDeserializer<SourcesChannelsSelect> {

    @Override
    public SourcesChannelsSelect deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        Integer[] channels_indices = context.deserialize(json.getAsJsonObject().get("channels_indices"), Integer[].class);
        return new SourcesChannelsSelect(channels_indices);
    }

    @Override
    public JsonElement serialize(SourcesChannelsSelect scs, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", SourcesChannelsSelect.class.getSimpleName());
        obj.add("channels_indices", context.serialize(scs.channels_indices.toArray(new Integer[0]), Integer[].class));
        return obj;
    }
}
