package adris.altoclef.multiversion;

import adris.altoclef.util.helpers.ItemComponentHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ToolMaterialVer {

    public static int getMiningLevel(ItemStack stack) {
        return ItemComponentHelper.getMiningLevel(stack);
    }

    public static int getMiningLevel(Item item) {
        return ItemComponentHelper.getMiningLevel(item);
    }

}
