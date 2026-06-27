package adris.altoclef.util.helpers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;

public final class ItemComponentHelper {

    private ItemComponentHelper() {
    }

    public static boolean isTool(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(DataComponents.TOOL);
    }

    public static boolean isTool(Item item) {
        return item != null && item.components().has(DataComponents.TOOL);
    }

    public static boolean isWeapon(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(DataComponents.WEAPON);
    }

    public static boolean isWeapon(Item item) {
        return item != null && item.components().has(DataComponents.WEAPON);
    }

    public static boolean isSword(Item item) {
        return isInTag(item, ItemTags.SWORDS);
    }

    public static boolean isEquippable(Item item) {
        return item == Items.SHIELD || getEquipmentSlot(item) != null;
    }

    public static boolean isArmor(Item item) {
        EquipmentSlot slot = getEquipmentSlot(item);
        return slot != null && slot.isArmor();
    }

    public static EquipmentSlot getEquipmentSlot(Item item) {
        if (item == Items.SHIELD) {
            return EquipmentSlot.OFFHAND;
        }
        if (item == null) {
            return null;
        }
        Equippable equippable = item.components().get(DataComponents.EQUIPPABLE);
        return equippable == null ? null : equippable.slot();
    }

    public static String getToolFamily(Item item) {
        if (isInTag(item, ItemTags.PICKAXES)) return "pickaxe";
        if (isInTag(item, ItemTags.AXES)) return "axe";
        if (isInTag(item, ItemTags.SHOVELS)) return "shovel";
        if (isInTag(item, ItemTags.HOES)) return "hoe";
        if (isInTag(item, ItemTags.SWORDS)) return "sword";
        if (isInTag(item, ItemTags.SPEARS)) return "spear";
        return "tool";
    }

    public static int getMiningLevel(ItemStack stack) {
        return stack == null || stack.isEmpty() ? -1 : getMiningLevel(stack.getItem());
    }

    public static int getMiningLevel(Item item) {
        if (isInTag(item, ItemTags.WOODEN_TOOL_MATERIALS) || isInTag(item, ItemTags.GOLD_TOOL_MATERIALS)) {
            return 0;
        }
        if (isInTag(item, ItemTags.STONE_TOOL_MATERIALS) || isInTag(item, ItemTags.COPPER_TOOL_MATERIALS)) {
            return 1;
        }
        if (isInTag(item, ItemTags.IRON_TOOL_MATERIALS)) {
            return 2;
        }
        if (isInTag(item, ItemTags.DIAMOND_TOOL_MATERIALS)) {
            return 3;
        }
        if (isInTag(item, ItemTags.NETHERITE_TOOL_MATERIALS)) {
            return 4;
        }
        return -1;
    }

    public static double getAttackDamage(Item item) {
        return item == null ? 1.0D : getAttackDamage(item.getDefaultInstance());
    }

    public static double getAttackDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 1.0D;
        }
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return modifiers.compute(Attributes.ATTACK_DAMAGE, 1.0D, EquipmentSlot.MAINHAND);
    }

    private static boolean isInTag(Item item, TagKey<Item> tag) {
        return item != null && BuiltInRegistries.ITEM.wrapAsHolder(item).is(tag);
    }
}
