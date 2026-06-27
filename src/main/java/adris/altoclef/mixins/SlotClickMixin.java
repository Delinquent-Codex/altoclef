package adris.altoclef.mixins;

import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.SlotClickChangedEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@Mixin(AbstractContainerMenu.class)
public abstract class SlotClickMixin {

    @Unique
    private List<ItemStack> altoclef$beforeStacks;

    @Inject(
            method = "clicked",
            at = @At("HEAD")
    )
    private void beforeSlotClick(int slotIndex, int button, ContainerInput actionType, Player player, CallbackInfo ci) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        List<Slot> afterSlots = self.slots;
        altoclef$beforeStacks = new ArrayList<>(afterSlots.size());
        for (Slot slot : afterSlots) {
            altoclef$beforeStacks.add(slot.getItem().copy());
        }
    }

    @Inject(
            method = "clicked",
            at = @At("RETURN")
    )
    private void afterSlotClick(int slotIndex, int button, ContainerInput actionType, Player player, CallbackInfo ci) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (altoclef$beforeStacks == null) {
            return;
        }
        List<Slot> afterSlots = self.slots;
        for (int i = 0; i < altoclef$beforeStacks.size(); ++i) {
            ItemStack before = altoclef$beforeStacks.get(i);
            ItemStack after = afterSlots.get(i).getItem();
            if (!ItemStack.matches(before, after)) {
                adris.altoclef.util.slots.Slot slot = adris.altoclef.util.slots.Slot.getFromCurrentScreen(i);
                EventBus.publish(new SlotClickChangedEvent(slot, before, after));
            }
        }
        altoclef$beforeStacks = null;
    }

}
