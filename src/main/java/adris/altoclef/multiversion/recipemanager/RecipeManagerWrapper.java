package adris.altoclef.multiversion.recipemanager;

import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.Collection;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

public class RecipeManagerWrapper {

    private final Collection<WrappedRecipeEntry> recipes;

    public static RecipeManagerWrapper of(ClientRecipeBook recipeBook) {
        if (recipeBook == null) return null;

        return new RecipeManagerWrapper(recipeBook.getCollections().stream()
                .flatMap(collection -> collection.getRecipes().stream())
                .map(WrappedRecipeEntry::new)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    public static RecipeManagerWrapper of(RecipeManager recipeManager) {
        if (recipeManager == null) return null;

        Collection<WrappedRecipeEntry> recipes = new LinkedHashSet<>();
        for (RecipeHolder<?> recipe : recipeManager.getRecipes()) {
            if (!(recipe.value() instanceof CraftingRecipe)) continue;
            recipeManager.listDisplaysForRecipe(recipe.id(), display -> recipes.add(new WrappedRecipeEntry(display)));
        }

        return new RecipeManagerWrapper(recipes);
    }

    public static RecipeManagerWrapper of(Minecraft client) {
        if (client == null) return null;

        MinecraftServer server = client.getSingleplayerServer();
        if (server != null) {
            return of(server.getRecipeManager());
        }

        if (client.player != null) {
            return of(client.player.getRecipeBook());
        }

        return null;
    }


    private RecipeManagerWrapper(Collection<WrappedRecipeEntry> recipes) {
        this.recipes = recipes;
    }

    public Collection<WrappedRecipeEntry> values() {
        return recipes;
    }



}
