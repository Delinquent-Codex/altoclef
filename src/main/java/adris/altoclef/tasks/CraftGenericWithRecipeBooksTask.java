package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.multiversion.recipemanager.WrappedRecipeEntry;
import adris.altoclef.tasks.slot.EnsureFreePlayerCraftingGridTask;
import adris.altoclef.tasks.slot.ReceiveCraftingOutputSlotTask;
import adris.altoclef.tasksystem.ITaskUsesCraftingGrid;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.JankCraftingRecipeMapping;
import adris.altoclef.util.RecipeTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.CraftingTableSlot;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

public class CraftGenericWithRecipeBooksTask extends Task implements ITaskUsesCraftingGrid {
    private static final int MANUAL_FALLBACK_TICKS = 40;

    private final RecipeTarget target;
    private int noOutputTicks;
    private boolean manualFallback;

    public CraftGenericWithRecipeBooksTask(RecipeTarget target) {
        this.target = target;
    }

    /**
     * This method is called when the mod starts.
     */
    @Override
    protected void onStart() {
        noOutputTicks = 0;
        manualFallback = false;
    }

    /**
     * This method handles the logic for the onTick event.
     * It checks various conditions and performs actions accordingly.
     *
     * @return The next task to execute.
     */
    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        if (manualFallback) {
            return new CraftGenericManuallyTask(target);
        }

        // Check if the big crafting UI or player inventory UI is open
        boolean isBigCraftingOpen = StorageHelper.isBigCraftingOpen();
        boolean isPlayerInventoryOpen = StorageHelper.isPlayerInventoryOpen();

        // Get the item stack in the cursor slot
        ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot();

        // Check if neither the big crafting UI nor the player inventory UI is open
        if (!isBigCraftingOpen && !isPlayerInventoryOpen) {
            // Check if the cursor stack is not empty
            if (!cursorStack.isEmpty()) {
                StorageHelper.tryStowCursorStack(mod);
            } else {
                // Close the screen
                StorageHelper.closeScreen();
            }
            return null;
        }

        if (!isBigCraftingOpen && !StorageHelper.isPlayerInventoryScreenOpen()) {
            StorageHelper.openPlayerInventoryScreen();
            return null;
        }

        // Determine the output slot based on whether the big crafting UI is open
        Slot outputSlot = isBigCraftingOpen ? CraftingTableSlot.OUTPUT_SLOT : PlayerSlot.CRAFT_OUTPUT_SLOT;
        // Get the item stack in the output slot
        ItemStack output = StorageHelper.getItemStackInSlot(outputSlot);

        // Check if the output item matches the target item and the target count has not been reached
        if (target.getOutputItem() == output.getItem() && mod.getItemStorage().getItemCount(target.getOutputItem()) < target.getTargetCount()) {
            noOutputTicks = 0;
            // Return a task to receive the crafting output slot
            return new ReceiveCraftingOutputSlotTask(outputSlot, target.getTargetCount());
        }

        // Check if the cursor stack is not empty
        if (!cursorStack.isEmpty()) {
            StorageHelper.tryStowCursorStack(mod);
            return null;
        }

        if (shouldUseManualFallback(++noOutputTicks)) {
            manualFallback = true;
            mod.getCraftingRecipeTracker().setDirty();
            mod.getStabilityDiagnostics().setRecentFailure("recipe-book placement stalled for "
                    + target.getOutputItem().getDescriptionId() + "; using manual crafting");
            setDebugState("Recipe book stalled; using manual crafting");
            return new CraftGenericManuallyTask(target);
        }

        // Check if neither the big crafting UI nor the player inventory UI is open
        if (!isBigCraftingOpen) {
            PlayerSlot[] playerInputSlots = PlayerSlot.CRAFT_INPUT_SLOTS;
            for (PlayerSlot playerInputSlot : playerInputSlots) {
                ItemStack playerInput = StorageHelper.getItemStackInSlot(playerInputSlot);
                if (!playerInput.isEmpty()) {
                    // Return a task to ensure a free player crafting grid
                    return new EnsureFreePlayerCraftingGridTask();
                }
            }
        }

        Optional<WrappedRecipeEntry> recipeToSend = JankCraftingRecipeMapping.getMinecraftMappedRecipe(target.getRecipe(), target.getOutputItem());
        if (recipeToSend.isPresent()) {
            if (mod.getSlotHandler().canDoSlotAction()) {
                LocalPlayer player = Minecraft.getInstance().player;
                assert player != null;
                // Click the recipe to send it
                mod.getController().handlePlaceRecipe(player.containerMenu.containerId, recipeToSend.get().id(), true);
                mod.getSlotHandler().registerSlotAction();
            }
        }

        return null;
    }

    static boolean shouldUseManualFallback(int noOutputTicks) {
        return noOutputTicks >= MANUAL_FALLBACK_TICKS;
    }

    /**
     * This method is called when the task is interrupted.
     *
     * @param interruptTask The task that interrupted the current task.
     */
    @Override
    protected void onStop(Task interruptTask) {

    }

    /**
     * Checks if a given Task object is equal to this CraftGenericWithRecipeBooksTask object.
     *
     * @param other The Task object to compare with.
     * @return True if the given Task is equal to this CraftGenericWithRecipeBooksTask, false otherwise.
     */
    @Override
    protected boolean isEqual(Task other) {
        // Check if the other Task is an instance of CraftGenericWithRecipeBooksTask
        if (other instanceof CraftGenericWithRecipeBooksTask) {
            CraftGenericWithRecipeBooksTask task = (CraftGenericWithRecipeBooksTask) other;

            // Check if the target of the other task is equal to the target of this task
            boolean isEqual = task.target.equals(target);

            // Log a message if the targets are not equal
            if (!isEqual) {
                Debug.logInternal("Task targets are not equal");
            }

            // Return the result of the equality check
            return isEqual;
        }

        // Log a message if the other Task is not an instance of CraftGenericWithRecipeBooksTask
        Debug.logInternal("Task is not an instance of CraftGenericWithRecipeBooksTask");

        // Return false if the other Task is not an instance of CraftGenericWithRecipeBooksTask
        return false;
    }

    /**
     * Returns a debug string representation of the object.
     *
     * @return The debug string representation.
     */
    @Override
    protected String toDebugString() {
        // Return the debug string.
        return getClass().getSimpleName() + " (w/ RECIPE): " + target;
    }
}
