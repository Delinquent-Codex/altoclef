package adris.altoclef.control;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemComponentHelper;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.CursorSlot;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.EmptyMapItem;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FoodOnAStickItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SpawnEggItem;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;


public class SlotHandler {

    private final AltoClef mod;

    private final TimerGame slotActionTimer = new TimerGame(0);
    private boolean overrideTimerOnce = false;
    private Slot cursorSource;
    private int cursorSourceContainerId = -1;

    public SlotHandler(AltoClef mod) {
        this.mod = mod;
    }

    private void forceAllowNextSlotAction() {
        overrideTimerOnce = true;
    }

    public boolean canDoSlotAction() {
        if (overrideTimerOnce) {
            overrideTimerOnce = false;
            return true;
        }
        slotActionTimer.setInterval(mod.getModSettings().getContainerItemMoveDelay());
        return slotActionTimer.elapsed();
    }

    public void registerSlotAction() {
        mod.getItemStorage().registerSlotAction();
        slotActionTimer.reset();
    }


    public void clickSlot(Slot slot, int mouseButton, ContainerInput type) {
        if (!canDoSlotAction()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        int containerId = player == null ? -1 : player.containerMenu.containerId;
        ItemStack cursorBefore = StorageHelper.getItemStackInCursorSlot().copy();
        ItemStack stackBeforeClick = type == ContainerInput.THROW
                ? StorageHelper.getItemStackInSlot(slot)
                : cursorBefore;
        boolean outsideDrop = slot.getWindowSlot() == Slot.UNDEFINED.getWindowSlot()
                && type == ContainerInput.PICKUP && !stackBeforeClick.isEmpty();
        if (outsideDrop && !mod.getInventoryPolicy().canDiscard(stackBeforeClick)) {
            mod.getStabilityDiagnostics().setRecentFailure("prevented discard of reserved "
                    + stackBeforeClick.getItem().getDescriptionId() + " from " + discardCaller());
            return;
        }
        if ((outsideDrop || type == ContainerInput.THROW) && !stackBeforeClick.isEmpty() && mod.getPlayer() != null) {
            mod.getInventoryPolicy().recordDeliberateDrop(stackBeforeClick.copy(), mod.getPlayer().position());
        }

        if (slot.getWindowSlot() == -1) {
            clickSlot(PlayerSlot.UNDEFINED, 0, ContainerInput.PICKUP);
            return;
        }
        // NOT THE CASE! We may have something in the cursor slot to place.
        //if (getItemStackInSlot(slot).isEmpty()) return getItemStackInSlot(slot);

        clickWindowSlot(slot.getWindowSlot(), mouseButton, type);
        if (type == ContainerInput.PICKUP) {
            updateCursorSource(slot, containerId, cursorBefore, StorageHelper.getItemStackInCursorSlot());
        }
    }

    public boolean tryReturnCursorToSource() {
        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
        if (cursor.isEmpty()) {
            clearCursorSource();
            return true;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || cursorSource == null || player.containerMenu.containerId != cursorSourceContainerId) {
            clearCursorSource();
            return false;
        }
        int windowSlot = cursorSource.getWindowSlot();
        if (windowSlot < 0 || windowSlot >= player.containerMenu.slots.size()) {
            clearCursorSource();
            return false;
        }
        net.minecraft.world.inventory.Slot target = player.containerMenu.getSlot(windowSlot);
        ItemStack targetStack = target.getItem();
        boolean canFit = target.mayPlace(cursor) && (targetStack.isEmpty()
                || ItemHelper.canStackTogether(cursor, targetStack));
        if (!canFit) {
            clearCursorSource();
            return false;
        }
        clickSlot(cursorSource, 0, ContainerInput.PICKUP);
        return StorageHelper.getItemStackInCursorSlot().isEmpty();
    }

    public boolean hasTrackedCursorSource() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && cursorSource != null
                && player.containerMenu.containerId == cursorSourceContainerId;
    }

    public void clearCursorSource() {
        cursorSource = null;
        cursorSourceContainerId = -1;
    }

    private void updateCursorSource(Slot clicked, int containerId, ItemStack before, ItemStack after) {
        if (after.isEmpty()) {
            clearCursorSource();
            return;
        }
        if (clicked.getWindowSlot() == Slot.UNDEFINED.getWindowSlot()) {
            return;
        }
        if (before.isEmpty() || before.getItem() != after.getItem() || before.getCount() != after.getCount()) {
            cursorSource = clicked;
            cursorSourceContainerId = containerId;
        }
    }

    private static String discardCaller() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(frame -> frame.getClassName().startsWith("adris.altoclef")
                        && !frame.getClassName().equals(SlotHandler.class.getName()))
                .map(frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1)
                        + "." + frame.getMethodName())
                .findFirst().orElse("unknown"));
    }

    private void clickSlotForce(Slot slot, int mouseButton, ContainerInput type) {
        forceAllowNextSlotAction();
        clickSlot(slot, mouseButton, type);
    }

    private void clickWindowSlot(int windowSlot, int mouseButton, ContainerInput type) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        registerSlotAction();
        int syncId = player.containerMenu.containerId;

        try {
            mod.getController().handleContainerInput(syncId, windowSlot, mouseButton, type, player);
        } catch (Exception e) {
            Debug.logWarning("Slot Click Error (ignored)");
            e.printStackTrace();
        }
    }

    public void forceEquipItemToOffhand(Item toEquip) {
        if (StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT).getItem() == toEquip) {
            return;
        }
        List<Slot> currentItemSlot = mod.getItemStorage().getSlotsWithItemPlayerInventory(false,
                toEquip);
        for (Slot CurrentItemSlot : currentItemSlot) {
            if (!Slot.isCursor(CurrentItemSlot)) {
                mod.getSlotHandler().clickSlot(CurrentItemSlot, 0, ContainerInput.PICKUP);
            } else {
                mod.getSlotHandler().clickSlot(PlayerSlot.OFFHAND_SLOT, 0, ContainerInput.PICKUP);
            }
        }
    }

    public boolean forceEquipItem(Item toEquip) {

        // Already equipped
        if (StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot()).getItem() == toEquip) return true;

        // Always equip to the second slot. First + last is occupied by baritone.
        mod.getPlayer().getInventory().setSelectedSlot(1);

        // If our item is in our cursor, simply move it to the hotbar.
        boolean inCursor = StorageHelper.getItemStackInSlot(CursorSlot.SLOT).getItem() == toEquip;

        List<Slot> itemSlots = mod.getItemStorage().getSlotsWithItemScreen(toEquip);
        if (!itemSlots.isEmpty()) {
            for (Slot ItemSlots : itemSlots) {
                int hotbar = 1;
                //_mod.getPlayer().getInventory().swapSlotWithHotbar();
                clickSlotForce(Objects.requireNonNull(ItemSlots), inCursor ? 0 : hotbar, inCursor ? ContainerInput.PICKUP : ContainerInput.SWAP);
                //registerSlotAction();
            }
            return true;
        }
        return false;
    }

    public boolean forceDeequipHitTool() {
        return forceDeequip(ItemComponentHelper::isTool);
    }

    public void forceDeequipRightClickableItem() {
        forceDeequip(stack -> {
                    Item item = stack.getItem();
                    return item instanceof BucketItem // water,lava,milk,fishes
                            || item instanceof EnderEyeItem
                            || item == Items.BOW
                            || item == Items.CROSSBOW
                            || item == Items.FLINT_AND_STEEL || item == Items.FIRE_CHARGE
                            || item == Items.ENDER_PEARL
                            || item instanceof FireworkRocketItem
                            || item instanceof SpawnEggItem
                            || item == Items.END_CRYSTAL
                            || item == Items.EXPERIENCE_BOTTLE
                            || item instanceof PotionItem // also includes splash/lingering
                            || item == Items.TRIDENT
                            || item == Items.WRITABLE_BOOK
                            || item == Items.WRITTEN_BOOK
                            || item instanceof FishingRodItem
                            || item instanceof FoodOnAStickItem
                            || item == Items.COMPASS
                            || item instanceof EmptyMapItem
                            || ItemComponentHelper.isEquippable(item)
                            || item == Items.LEAD
                            || item == Items.SHIELD;
                }
        );
    }

    /**
     * Tries to de-equip any item that we don't want equipped.
     *
     * @param isBad: Whether an item is bad/shouldn't be equipped
     * @return Whether we successfully de-equipped, or if we didn't have the item equipped at all.
     */
    public boolean forceDeequip(Predicate<ItemStack> isBad) {
        ItemStack equip = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot());
        ItemStack cursor = StorageHelper.getItemStackInSlot(CursorSlot.SLOT);
        if (isBad.test(cursor)) {
            // Throw away cursor slot OR move
            Optional<Slot> fittableSlots = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(equip, false);
            if (fittableSlots.isEmpty()) {
                // Try to swap items with the first non-bad slot.
                for (Slot slot : Slot.getCurrentScreenSlots()) {
                    if (!isBad.test(StorageHelper.getItemStackInSlot(slot))) {
                        clickSlotForce(slot, 0, ContainerInput.PICKUP);
                        return false;
                    }
                }
                if (ItemHelper.canThrowAwayStack(mod, cursor)) {
                    clickSlotForce(PlayerSlot.UNDEFINED, 0, ContainerInput.PICKUP);
                    return true;
                }
                // Can't throw :(
                return false;
            } else {
                // Put in the empty/available slot.
                clickSlotForce(fittableSlots.get(), 0, ContainerInput.PICKUP);
                return true;
            }
        } else if (isBad.test(equip)) {
            // Pick up the item
            clickSlotForce(PlayerSlot.getEquipSlot(), 0, ContainerInput.PICKUP);
            return false;
        } else if (equip.isEmpty() && !cursor.isEmpty()) {
            // cursor is good and equip is empty, so finish filling it in.
            clickSlotForce(PlayerSlot.getEquipSlot(), 0, ContainerInput.PICKUP);
            return true;
        }
        // We're already de-equipped
        return true;
    }

    public void forceEquipSlot(Slot slot) {
        Slot target = PlayerSlot.getEquipSlot();
        clickSlotForce(slot, target.getInventorySlot(), ContainerInput.SWAP);
    }

    public boolean forceEquipItem(Item[] matches, boolean unInterruptable) {
        return forceEquipItem(new ItemTarget(matches, 1), unInterruptable);
    }

    public boolean forceEquipItem(ItemTarget toEquip, boolean unInterruptable) {
        if (toEquip == null) return false;

        //If the bot try to eat
        if (mod.getFoodChain().needsToEat() && !unInterruptable) { //unless we really need to force equip the item
            return false; //don't equip the item for now
        }

        Slot target = PlayerSlot.getEquipSlot();
        // Already equipped
        if (toEquip.matches(StorageHelper.getItemStackInSlot(target).getItem())) return true;

        for (Item item : toEquip.getMatches()) {
            if (mod.getItemStorage().hasItem(item)) {
                if (forceEquipItem(item)) return true;
            }
        }
        return false;
    }

    // By default, don't force equip if the bot is eating.
    public boolean forceEquipItem(Item... toEquip) {
        return forceEquipItem(toEquip, false);
    }

    public void refreshInventory() {
        if (Minecraft.getInstance().player == null)
            return;
        for (int i = 0; i < Minecraft.getInstance().player.getInventory().getContainerSize(); ++i) {
            Slot slot = Slot.getFromCurrentScreenInventory(i);
            clickSlotForce(slot, 0, ContainerInput.PICKUP);
            clickSlotForce(slot, 0, ContainerInput.PICKUP);
        }
    }
}
