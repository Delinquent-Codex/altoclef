package adris.altoclef.tasks.slot;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.world.item.ItemStack;

public class EnsureFreeCursorSlotTask extends Task {

    @Override
    protected void onStart() {
        // YEET
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();

        if (!cursor.isEmpty()) {
            setDebugState("Safely stowing cursor stack");
            StorageHelper.tryStowCursorStack(mod);
            return null;
        }
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof EnsureFreeCursorSlotTask;
    }


    // And filling this in will make it look ok in the task tree
    @Override
    protected String toDebugString() {
        return "Breaking the cursor slot";
    }
}
