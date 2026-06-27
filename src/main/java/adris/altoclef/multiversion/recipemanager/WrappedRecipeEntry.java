package adris.altoclef.multiversion.recipemanager;

import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

public record WrappedRecipeEntry(RecipeDisplayEntry entry) {

    public RecipeDisplayId id() {
        return entry.id();
    }

    public RecipeDisplay value() {
        return entry.display();
    }
}
