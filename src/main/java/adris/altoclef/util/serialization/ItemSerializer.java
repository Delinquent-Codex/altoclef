package adris.altoclef.util.serialization;

import adris.altoclef.util.helpers.ItemHelper;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.List;
import net.minecraft.world.item.Item;

public class ItemSerializer extends StdSerializer<List<Item>> {
    public ItemSerializer() {
        super(List.class, false);
    }

    @Override
    public void serialize(List<Item> items, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartArray();
        for (Item item : items) {
            String key = ItemHelper.trimItemName(item.getDescriptionId());
            gen.writeString(key);
        }
        gen.writeEndArray();
    }
}
