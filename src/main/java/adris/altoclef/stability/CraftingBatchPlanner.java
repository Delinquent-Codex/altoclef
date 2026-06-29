package adris.altoclef.stability;

public final class CraftingBatchPlanner {
    private CraftingBatchPlanner() {
    }

    public static int remainingOutputs(int targetCount, int currentCount) {
        return Math.max(0, targetCount - currentCount);
    }

    public static int craftsRequired(int targetCount, int currentCount, int outputPerCraft) {
        int remaining = remainingOutputs(targetCount, currentCount);
        return remaining == 0 ? 0 : (remaining + outputPerCraft - 1) / outputPerCraft;
    }

    public static int requiredPerIngredientSlot(int targetCount, int currentCount, int outputPerCraft) {
        return craftsRequired(targetCount, currentCount, outputPerCraft);
    }

    public static boolean hasOutputCapacity(int freeSlots, boolean canStackOutput, int ingredientSlotsFreed) {
        return canStackOutput || freeSlots + ingredientSlotsFreed > 0;
    }
}
