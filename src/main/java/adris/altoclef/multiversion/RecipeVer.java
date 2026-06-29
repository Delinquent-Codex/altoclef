package adris.altoclef.multiversion;

import net.minecraft.world.item.ItemStack;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

public class RecipeVer {


    private static final ContextMap EMPTY_RECIPE_CONTEXT = new ContextMap.Builder().create(new ContextKeySet.Builder().build());

    public static ItemStack getOutput(RecipeDisplay recipe, Level world) {
        return recipe.result().resolveForFirstStack(EMPTY_RECIPE_CONTEXT);
    }


}
