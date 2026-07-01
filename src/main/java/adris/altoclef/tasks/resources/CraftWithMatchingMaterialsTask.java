package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.CraftInInventoryTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.container.CraftInTableTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.CraftingRecipe;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RecipeTarget;
import adris.altoclef.stability.MatchingMaterialPlanner;
import java.util.Arrays;
import net.minecraft.world.item.Item;

public abstract class CraftWithMatchingMaterialsTask extends ResourceTask {

    private final ItemTarget target;
    private final CraftingRecipe recipe;
    private final boolean[] sameMask;

    private final ItemTarget sameResourceTarget;
    private final int sameResourcePerRecipe;

    public CraftWithMatchingMaterialsTask(ItemTarget target, CraftingRecipe recipe, boolean[] sameMask) {
        super(target);
        this.target = target;
        this.recipe = recipe;
        this.sameMask = sameMask;
        int sameResourceRequiredCount = 0;
        ItemTarget sameResourceTarget = null;
        if (recipe.getSlotCount() != sameMask.length) {
            Debug.logError("Invalid CraftWithMatchingMaterialsTask constructor parameters: Recipe size must equal \"sameMask\" size.");
        }
        for (int i = 0; i < recipe.getSlotCount(); ++i) {
            if (sameMask[i]) {
                sameResourceRequiredCount++;
                sameResourceTarget = recipe.getSlot(i);
            }
        }
        this.sameResourceTarget = sameResourceTarget;

        sameResourcePerRecipe = sameResourceRequiredCount;
    }

    private static CraftingRecipe generateSameRecipe(CraftingRecipe diverseRecipe, Item sameItem, boolean[] sameMask) {
        ItemTarget[] result = new ItemTarget[diverseRecipe.getSlotCount()];
        for (int i = 0; i < result.length; ++i) {
            if (sameMask[i]) {
                result[i] = new ItemTarget(sameItem, 1);
            } else {
                result[i] = diverseRecipe.getSlot(i);
            }
        }
        return CraftingRecipe.newShapedRecipe(result, diverseRecipe.outputCount());
    }

    @Override
    protected void onResourceStart(AltoClef mod) {

    }

    @Override
    protected Task onResourceTick(AltoClef mod) {

        int currentTargetCount = mod.getItemStorage().getItemCount(target);
        int remainingOutputs = Math.max(0, target.getTargetCount() - currentTargetCount);
        MatchingMaterialPlanner.Selection<Item> selection = MatchingMaterialPlanner.select(
                        Arrays.asList(sameResourceTarget.getMatches()),
                        item -> getExpectedTotalCountOfSameItem(mod, item),
                        item -> mod.getItemStorage().getItemCount(item),
                        remainingOutputs, sameResourcePerRecipe, recipe.outputCount())
                .orElse(null);
        if (selection == null) return null;

        mod.getInventoryPolicy().reserveCraftingIntermediate(selection.material(), selection.materialRequired());
        if (!selection.canCommit()) {
            return getSpecificSameResourceTask(mod, selection.material(), selection.materialRequired());
        }

        Item output = getSpecificItemCorrespondingToMajorityResource(selection.material());
        if (output == null) {
            mod.getStabilityDiagnostics().setRecentFailure("no output mapping for matching material "
                    + selection.material().getDescriptionId());
            return null;
        }
        CraftingRecipe sameRecipe = generateSameRecipe(recipe, selection.material(), sameMask);
        int toCraftTotal = mod.getItemStorage().getItemCount(output)
                + selection.craftsRequired() * recipe.outputCount();
        RecipeTarget recipeTarget = new RecipeTarget(output, toCraftTotal, sameRecipe);
        return recipe.isBig() ? new CraftInTableTask(recipeTarget) : new CraftInInventoryTask(recipeTarget);
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {

    }


    // Virtual
    protected int getExpectedTotalCountOfSameItem(AltoClef mod, Item sameItem) {
        return mod.getItemStorage().getItemCount(sameItem);
    }

    protected Task getSpecificSameResourceTask(AltoClef mod, Item toGet, int count) {
        return TaskCatalogue.getItemTask(toGet, count);
    }

    protected abstract Item getSpecificItemCorrespondingToMajorityResource(Item majority);
}
