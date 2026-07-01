package adris.altoclef.stability;

import adris.altoclef.AltoClef;
import adris.altoclef.multiversion.item.ItemVer;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemComponentHelper;
import adris.altoclef.util.helpers.ItemHelper;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class InventoryPolicy {
    private static final int DROP_SUPPRESSION_TICKS = 10 * 20;
    private final AltoClef mod;
    private final Map<Item, Reservation> reservations = new HashMap<>();
    private final DropSuppression<String> suppressedDrops = new DropSuppression<>(32, DROP_SUPPRESSION_TICKS, 6);

    public InventoryPolicy(AltoClef mod) {
        this.mod = mod;
    }

    public void tick() {
        reservations.clear();
        Task root = mod.getUserTaskChain().getCurrentTask();
        if (root != null) {
            List<ResourceTask> resourceTasks = new ArrayList<>();
            for (Task current = root; current != null; current = current.getSubTask()) {
                if (current instanceof ResourceTask resourceTask) resourceTasks.add(resourceTask);
            }
            for (int i = 0; i < resourceTasks.size(); i++) {
                ItemCategory category = i == resourceTasks.size() - 1
                        ? ItemCategory.REQUIRED_NOW : ItemCategory.REQUIRED_FOR_PARENT_TASK;
                for (ItemTarget target : resourceTasks.get(i).getItemTargets()) {
                    for (Item item : target.getMatches()) {
                        reserve(item, target.getTargetCount(), category);
                    }
                }
            }
        }
        mod.getStabilityDiagnostics().setInventoryReservations(describeReservations());
    }

    public ItemCategory category(ItemStack stack) {
        Reservation reservation = reservations.get(stack.getItem());
        if (reservation != null) return reservation.category();
        Item item = stack.getItem();
        if (ItemVer.isFood(stack) && item != Items.SPIDER_EYE) return ItemCategory.SURVIVAL_RESERVE;
        if (item == Items.GOLD_NUGGET) return ItemCategory.REQUIRED_FOR_PARENT_TASK;
        if (mod.getBehaviour().isProtected(item)) return ItemCategory.CRAFTING_INTERMEDIATE;
        if (BuiltInRegistries.ITEM.wrapAsHolder(item).is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) {
            return ItemCategory.DISPOSABLE;
        }
        if (Arrays.asList(ItemHelper.SAPLINGS).contains(item)) return ItemCategory.DISPOSABLE;
        if (ItemComponentHelper.isEquippable(item) || ItemComponentHelper.isTool(stack)
                || ItemComponentHelper.isWeapon(stack)) return ItemCategory.EQUIPMENT;
        if (item instanceof BlockItem) return ItemCategory.BUILDING_MATERIAL;
        if (mod.getModSettings().isThrowaway(item)) return ItemCategory.DISPOSABLE;
        return mod.getModSettings().shouldThrowawayUnusedItems()
                ? ItemCategory.REPLACEABLE : ItemCategory.REQUIRED_FOR_PARENT_TASK;
    }

    public boolean canDiscard(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemCategory category = category(stack);
        if (category == ItemCategory.BUILDING_MATERIAL) {
            return mod.getModSettings().isThrowaway(stack.getItem());
        }
        return category == ItemCategory.DISPOSABLE || category == ItemCategory.REPLACEABLE;
    }

    public void recordDeliberateDrop(ItemStack stack, Vec3 position) {
        if (stack.isEmpty() || mod.getWorld() == null) return;
        suppressedDrops.record(itemKey(stack.getItem()), toPosition(position), mod.getWorld().getGameTime());
    }

    public void reserveCraftingIntermediate(Item item, int count) {
        reserve(item, count, ItemCategory.CRAFTING_INTERMEDIATE);
        mod.getStabilityDiagnostics().setInventoryReservations(describeReservations());
    }

    public boolean shouldIgnorePickup(ItemEntity entity) {
        if (mod.getWorld() == null || entity == null || entity.getItem().isEmpty()) return false;
        Item item = entity.getItem().getItem();
        return suppressedDrops.isSuppressed(itemKey(item), toPosition(entity.position()), mod.getWorld().getGameTime(),
                isTaskRequired(item));
    }

    public void reset() {
        reservations.clear();
        suppressedDrops.clear();
    }

    public static boolean canRunRootInventoryCleanup(boolean childTransactionActive, boolean screenOpen) {
        return !childTransactionActive && !screenOpen;
    }

    public String describeReservations() {
        if (reservations.isEmpty()) return "none";
        StringJoiner result = new StringJoiner(",");
        reservations.entrySet().stream()
                .sorted(Comparator.comparing(entry -> itemKey(entry.getKey())))
                .limit(8)
                .forEach(entry -> result.add(itemKey(entry.getKey()) + "=" + entry.getValue().count()
                        + ":" + entry.getValue().category().name().toLowerCase()));
        if (reservations.size() > 8) result.add("+" + (reservations.size() - 8));
        return result.toString();
    }

    private void reserve(Item item, int count, ItemCategory category) {
        reservations.merge(item, new Reservation(count, category), (left, right) ->
                new Reservation(Math.max(left.count(), right.count()),
                        left.category().priority() >= right.category().priority() ? left.category() : right.category()));
    }

    private boolean isTaskRequired(Item item) {
        Reservation reservation = reservations.get(item);
        return reservation != null && (reservation.category() == ItemCategory.REQUIRED_NOW
                || reservation.category() == ItemCategory.REQUIRED_FOR_PARENT_TASK);
    }

    private static String itemKey(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static DropSuppression.Position toPosition(Vec3 position) {
        return new DropSuppression.Position(position.x, position.y, position.z);
    }

    private record Reservation(int count, ItemCategory category) {
    }

    public enum ItemCategory {
        TEMPORARILY_IGNORED_PICKUP(0),
        DISPOSABLE(1),
        REPLACEABLE(2),
        BUILDING_MATERIAL(3),
        CRAFTING_INTERMEDIATE(4),
        EQUIPMENT(5),
        SURVIVAL_RESERVE(6),
        REQUIRED_FOR_PARENT_TASK(7),
        REQUIRED_NOW(8);

        private final int priority;

        ItemCategory(int priority) {
            this.priority = priority;
        }

        int priority() {
            return priority;
        }
    }
}
