package adris.altoclef.tasks.slot;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.StorageHelper;
import baritone.api.utils.input.Input;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class InventoryUiRecoveryTask extends Task {
    private static final int MAX_ATTEMPTS = 100;
    private int attempts;
    private boolean failed;

    @Override
    protected void onStart() {
        attempts = 0;
        failed = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        mod.getInputControls().release(Input.CLICK_LEFT);
        mod.getInputControls().release(Input.CLICK_RIGHT);
        mod.getClientBaritone().getPathingBehavior().forceCancel();

        if (!StorageHelper.getItemStackInCursorSlot().isEmpty()) {
            if (attempts >= MAX_ATTEMPTS) {
                failed = true;
                mod.getStabilityDiagnostics().setRecentFailure(failureReason());
                return null;
            }
            attempts++;
            setDebugState("Returning cursor stack safely");
            if (!StorageHelper.tryStowCursorStack(mod) && attempts % 20 == 0) {
                mod.getItemStorage().setDirty();
            }
            return null;
        }

        mod.getSlotHandler().clearCursorSource();
        if (Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>) {
            setDebugState("Closing stale container screen");
            StorageHelper.closeScreen();
            return null;
        }

        mod.getItemStorage().setDirty();
        mod.getCraftingRecipeTracker().setDirty();
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef mod = AltoClef.getInstance();
        mod.getInputControls().release(Input.CLICK_LEFT);
        mod.getInputControls().release(Input.CLICK_RIGHT);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof InventoryUiRecoveryTask;
    }

    @Override
    protected String toDebugString() {
        return "Recovering inventory UI transaction";
    }

    public boolean failed() {
        return failed;
    }

    public String failureReason() {
        return "UI recovery could not place cursor stack after " + attempts + " attempts";
    }

    @Override
    public boolean isFinished() {
        return failed || StorageHelper.getItemStackInCursorSlot().isEmpty()
                && !(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>);
    }
}
