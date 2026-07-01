package adris.altoclef.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraftingBatchPlannerTest {
    @Test
    void plansPlanksAndSticksWithMultiOutputRecipes() {
        assertEquals(2, CraftingBatchPlanner.craftsRequired(8, 0, 4));
        assertEquals(1, CraftingBatchPlanner.craftsRequired(8, 4, 4));
    }

    @Test
    void plansCraftingTableToolsAndBread() {
        assertEquals(1, CraftingBatchPlanner.craftsRequired(1, 0, 1));
        assertEquals(1, CraftingBatchPlanner.requiredPerIngredientSlot(1, 0, 1));
        assertEquals(0, CraftingBatchPlanner.craftsRequired(1, 1, 1));
    }

    @Test
    void accountsForPartialOutputStacks() {
        assertEquals(2, CraftingBatchPlanner.requiredPerIngredientSlot(10, 3, 4));
    }

    @Test
    void nearFullInventoryRequiresStackOrFreedIngredientSlot() {
        assertFalse(CraftingBatchPlanner.hasOutputCapacity(0, false, 0));
        assertTrue(CraftingBatchPlanner.hasOutputCapacity(0, false, 1));
        assertTrue(CraftingBatchPlanner.hasOutputCapacity(0, true, 0));
    }
}
