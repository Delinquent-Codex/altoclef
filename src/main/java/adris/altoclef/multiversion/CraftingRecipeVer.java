package adris.altoclef.multiversion;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

public class CraftingRecipeVer {


    @Pattern
    private static ItemStack getOutput(CraftingRecipe craftingRecipe) {
        return craftingRecipe.display().isEmpty() ? ItemStack.EMPTY : craftingRecipe.display().get(0).result().resolveForFirstStack(new net.minecraft.util.context.ContextMap.Builder().create(new net.minecraft.util.context.ContextKeySet.Builder().build()));
    }

}
