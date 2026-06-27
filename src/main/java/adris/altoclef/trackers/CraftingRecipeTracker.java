package adris.altoclef.trackers;

import adris.altoclef.AltoClef;
import adris.altoclef.multiversion.recipemanager.RecipeManagerWrapper;
import adris.altoclef.multiversion.recipemanager.WrappedRecipeEntry;
import adris.altoclef.util.RecipeTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

// TODO remove those ugly "ensureUpdate" statements, realistically we only need to update only upon joining a world
public class CraftingRecipeTracker extends Tracker{


    private final HashMap<Item, List<adris.altoclef.util.CraftingRecipe>> itemRecipeMap = new HashMap<>();
    private final HashMap<adris.altoclef.util.CraftingRecipe, ItemStack> recipeResultMap = new HashMap<>();
    private static final ContextMap EMPTY_RECIPE_CONTEXT = new ContextMap.Builder().create(new ContextKeySet.Builder().build());

    private boolean shouldRebuild;

    public CraftingRecipeTracker(TrackerManager manager) {
        super(manager);
        shouldRebuild = true;
    }

    public List<adris.altoclef.util.CraftingRecipe> getRecipeForItem(Item item) {
        ensureUpdated();

        if (!hasRecipeForItem(item)) {
            mod.logWarning("trying to access recipe for unknown item: "+item);
            return Collections.emptyList();
        }

        return itemRecipeMap.get(item);
    }

    public adris.altoclef.util.CraftingRecipe getFirstRecipeForItem(Item item) {
        ensureUpdated();

        if (!hasRecipeForItem(item)) {
            mod.logWarning("trying to access recipe for unknown item: "+item);
            return null;
        }

        return itemRecipeMap.get(item).get(0);
    }

    public List<RecipeTarget> getRecipeTarget(Item item, int targetCount) {
        ensureUpdated();

        List<RecipeTarget> targets = new ArrayList<>();
        for (adris.altoclef.util.CraftingRecipe recipe : getRecipeForItem(item)) {
            targets.add(new RecipeTarget(item, targetCount, recipe));
        }

        return targets;
    }

    public RecipeTarget getFirstRecipeTarget(Item item, int targetCount) {
        ensureUpdated();

        return new RecipeTarget(item, targetCount, getFirstRecipeForItem(item));
    }

    public boolean hasRecipeForItem(Item item) {
        ensureUpdated();
        return itemRecipeMap.containsKey(item);
    }

    public ItemStack getRecipeResult(adris.altoclef.util.CraftingRecipe recipe) {
        ensureUpdated();

        if (!hasRecipe(recipe)) {
            mod.logWarning("Trying to get result for unknown recipe: "+recipe);
            return null;
        }
        ItemStack result = recipeResultMap.get(recipe);

        return new ItemStack(result.getItem(), result.getCount());
    }

    public boolean hasRecipe(adris.altoclef.util.CraftingRecipe recipe) {
        ensureUpdated();
        return recipeResultMap.containsKey(recipe);
    }


    @Override
    protected void updateState() {
        if (!shouldRebuild) return;

        // rebuild once we are in game
        if (!AltoClef.inGame()) return;

        ClientPacketListener networkHandler =  Minecraft.getInstance().getConnection();
        if (networkHandler == null) return;

        if (Minecraft.getInstance().player == null) return;

        RecipeManagerWrapper recipeManager = RecipeManagerWrapper.of(Minecraft.getInstance());
        if (recipeManager == null) return;

        for (WrappedRecipeEntry recipe : recipeManager.values()) {
            List<ItemStack> results = recipe.entry().resultItems(EMPTY_RECIPE_CONTEXT);
            if (results.isEmpty()
                    || recipe.entry().craftingRequirements().isEmpty()
                    || recipe.entry().craftingRequirements().get().isEmpty()) {
                continue;
            }

            ItemStack displayResult = results.get(0);
            ItemStack result = new ItemStack(displayResult.getItem(), displayResult.getCount());

            Item[][] altoclefRecipeItems = getCraftingRecipeItems(recipe);

            adris.altoclef.util.CraftingRecipe altoclefRecipe = adris.altoclef.util.CraftingRecipe.newShapedRecipe(altoclefRecipeItems, result.getCount());

            if (itemRecipeMap.containsKey(result.getItem())) {
                itemRecipeMap.get(result.getItem()).add(altoclefRecipe);
            } else {
                List<adris.altoclef.util.CraftingRecipe> recipes = new ArrayList<>();
                recipes.add(altoclefRecipe);

                itemRecipeMap.put(result.getItem(), recipes);
            }

            recipeResultMap.put(altoclefRecipe, result);
        }

        itemRecipeMap.replaceAll((k,v) -> Collections.unmodifiableList(v));

        shouldRebuild = false;
    }

    private static Item[][] getCraftingRecipeItems(WrappedRecipeEntry recipe) {
        List<Ingredient> ingredients = recipe.entry().craftingRequirements().orElseThrow();
        int sourceWidth;
        int sourceHeight;
        int gridWidth;

        if (recipe.value() instanceof ShapedCraftingRecipeDisplay shaped) {
            sourceWidth = shaped.width();
            sourceHeight = shaped.height();
            gridWidth = sourceWidth <= 2 && sourceHeight <= 2 ? 2 : 3;
        } else if (recipe.value() instanceof ShapelessCraftingRecipeDisplay) {
            sourceWidth = Math.min(ingredients.size(), ingredients.size() <= 4 ? 2 : 3);
            sourceHeight = (ingredients.size() + sourceWidth - 1) / sourceWidth;
            gridWidth = ingredients.size() <= 4 ? 2 : 3;
        } else {
            sourceWidth = 3;
            sourceHeight = (ingredients.size() + sourceWidth - 1) / sourceWidth;
            gridWidth = 3;
        }

        Item[][] result = new Item[gridWidth * gridWidth][];
        for (int sourceIndex = 0; sourceIndex < ingredients.size(); sourceIndex++) {
            int sourceX = sourceIndex % sourceWidth;
            int sourceY = sourceIndex / sourceWidth;
            if (sourceY >= sourceHeight || sourceX >= gridWidth || sourceY >= gridWidth) continue;

            Ingredient ingredient = ingredients.get(sourceIndex);
            Item[] items = ingredient.display()
                    .resolveForStacks(EMPTY_RECIPE_CONTEXT)
                    .stream()
                    .map(ItemStack::getItem)
                    .distinct()
                    .toArray(Item[]::new);

            if (items.length != 0) {
                result[sourceY * gridWidth + sourceX] = items;
            }
        }

        return result;
    }

    @Override
    protected void reset() {
       shouldRebuild = true;
       itemRecipeMap.clear();
       recipeResultMap.clear();
    }

    @Override
    protected boolean isDirty() {
        return shouldRebuild;
    }
}
