package adris.altoclef.tasks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraftGenericManuallyTaskTest {
    @Test
    void clearsItemInheritedFromPreviousRecipe() {
        assertTrue(CraftGenericManuallyTask.shouldClearCraftSlot(false, false, false));
    }

    @Test
    void keepsMatchingIngredientAndEmptySlot() {
        assertFalse(CraftGenericManuallyTask.shouldClearCraftSlot(false, false, true));
        assertFalse(CraftGenericManuallyTask.shouldClearCraftSlot(false, true, false));
    }

    @Test
    void clearsOccupiedSlotThatRecipeLeavesEmpty() {
        assertTrue(CraftGenericManuallyTask.shouldClearCraftSlot(true, false, false));
    }
}
