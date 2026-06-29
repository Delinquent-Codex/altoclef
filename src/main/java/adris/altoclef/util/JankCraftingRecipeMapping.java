package adris.altoclef.util;

import adris.altoclef.multiversion.RecipeVer;
import adris.altoclef.multiversion.recipemanager.RecipeManagerWrapper;
import adris.altoclef.multiversion.recipemanager.WrappedRecipeEntry;
import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * For crafting table/inventory recipe book crafting, we need to figure out identifiers given a recipe.
 */
public class JankCraftingRecipeMapping {
    private static final HashMap<Item, List<WrappedRecipeEntry>> recipeMapping = new HashMap<>();
    private static ClientLevel mappedWorld;

    /**
     * Reloads the recipe mapping.
     */
    private static void reloadRecipeMapping() {
        Minecraft client = Minecraft.getInstance();
        recipeMapping.clear();
        mappedWorld = client.level;

        RecipeManagerWrapper recipes = RecipeManagerWrapper.of(client);
        if (recipes == null) return;

        for (WrappedRecipeEntry recipe : recipes.values()) {
            ClientLevel world = client.level;
            if (world == null) continue;
            Item output = RecipeVer.getOutput(recipe.value(), world).getItem();
            if (output != net.minecraft.world.item.Items.AIR) {
                recipeMapping.computeIfAbsent(output, k -> new ArrayList<>()).add(recipe);
            }
        }
    }

    /**
     * Retrieves the mapped recipe for a given output item from the Minecraft crafting recipe.
     *
     * @param recipe The crafting recipe to check against.
     * @param output The output item of the recipe.
     * @return An Optional containing the mapped recipe entry if found, or an empty Optional if not found.
     */
    public static Optional<WrappedRecipeEntry> getMinecraftMappedRecipe(CraftingRecipe recipe, Item output) {
        ClientLevel currentWorld = Minecraft.getInstance().level;
        if (currentWorld != mappedWorld || !recipeMapping.containsKey(output)) {
            reloadRecipeMapping();
        }

        // Check if the output item is present in the recipe mapping
        if (recipeMapping.containsKey(output)) {
            // Iterate through all the recipes mapped to the output item
            for (WrappedRecipeEntry checkRecipe : recipeMapping.get(output)) {
                // Create a list of item targets to satisfy
                List<ItemTarget> toSatisfy = Arrays.stream(recipe.getSlots())
                        .filter(itemTarget -> itemTarget != null && !itemTarget.isEmpty())
                        .collect(Collectors.toList());
                // Check if the recipe has ingredients
                if (checkRecipe.entry().craftingRequirements().isPresent()) {
                    List<Ingredient> requirements = checkRecipe.entry().craftingRequirements().get();
                    long nonEmptyRequirementCount = requirements.stream().filter(ingredient -> !ingredient.isEmpty()).count();
                    if (nonEmptyRequirementCount != toSatisfy.size()) {
                        continue;
                    }

                    boolean allIngredientsMatched = true;
                    for (Ingredient ingredient : requirements) {
                        // Skip empty ingredients
                        if (ingredient.isEmpty()) {
                            continue;
                        }

                        boolean ingredientMatched = false;
                        for (int i = 0; i < toSatisfy.size(); ++i) {
                            ItemTarget target = toSatisfy.get(i);
                            if (Arrays.stream(target.getMatches())
                                    .anyMatch(item -> ingredient.acceptsItem(BuiltInRegistries.ITEM.wrapAsHolder(item)))) {
                                toSatisfy.remove(i);
                                ingredientMatched = true;
                                break;
                            }
                        }

                        if (!ingredientMatched) {
                            allIngredientsMatched = false;
                            break;
                        }
                    }

                    if (!allIngredientsMatched) {
                        continue;
                    }
                }
                // Check if all the item targets have been satisfied
                if (toSatisfy.isEmpty()) {
                    return Optional.of(checkRecipe);
                }
            }
        }
        return Optional.empty();
    }
}
