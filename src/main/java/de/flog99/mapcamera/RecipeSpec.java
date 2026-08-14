package de.flog99.mapcamera;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * One craftable, as config describes it.
 *
 * <p>Materials are resolved when the config is read rather than when the recipe is registered, so a typo is
 * one warning at startup naming the line to fix instead of a stack trace out of the crafting table.
 *
 * @param ingredients shapeless only, and one entry per <b>item</b> rather than per kind: a line asking for two
 *                    paper is two entries here, which is the flat list {@link ShapelessRecipe} wants
 */
public record RecipeSpec(boolean enabled, boolean shaped, int amount, List<String> shape, Map<Character, Material> keys, List<Material> ingredients) {

    /** What a crafting grid holds, and so the most items a shapeless recipe can ask for. */
    private static final int GRID_SLOTS = 9;

    private static final RecipeSpec OFF = new RecipeSpec(false, true, 1, List.of(), Map.of(), List.of());

    public static RecipeSpec read(ConfigurationSection section, Logger log) {
        if (!section.getBoolean("enabled", true)) return OFF;

        boolean shaped = !"shapeless".equalsIgnoreCase(section.getString("type", "shaped"));
        int amount = Math.max(1, section.getInt("amount", 1));
        String where = section.getCurrentPath();

        if (!shaped) {
            List<Material> ingredients = new ArrayList<>();
            section.getStringList("ingredients").forEach(line -> ingredient(line, where, log, ingredients));

            if (ingredients.isEmpty()) {
                log.warning("'" + where + "' is shapeless with no usable ingredients, so it is off.");
                return OFF;
            }
            if (ingredients.size() > GRID_SLOTS) {
                log.warning("'" + where + ".ingredients' comes to " + ingredients.size() + " items and a crafting grid"
                        + " holds " + GRID_SLOTS + ", so it is off.");
                return OFF;
            }
            return new RecipeSpec(true, false, amount, List.of(), Map.of(), List.copyOf(ingredients));
        }

        List<String> rows = section.getStringList("shape");
        if (rows.isEmpty() || rows.size() > 3 || rows.stream().anyMatch(row -> row.length() > 3)) {
            log.warning("'" + where + ".shape' must be one to three rows of at most three characters, so it is off.");
            return OFF;
        }

        Map<Character, Material> keys = new LinkedHashMap<>();
        ConfigurationSection keySection = section.getConfigurationSection("keys");
        if (keySection != null) {
            for (String key : keySection.getKeys(false)) {
                if (key.length() != 1) {
                    log.warning("'" + where + ".keys." + key + "' must be a single character.");
                    continue;
                }
                Material material = material(keySection.getString(key, ""), where, log);
                if (material != null) {
                    keys.put(key.charAt(0), material);
                }
            }
        }

        // A shape naming a key that was dropped would craft from thin air, so it goes off rather than half on.
        for (String row : rows) {
            for (char slot : row.toCharArray()) {
                if (slot != ' ' && !keys.containsKey(slot)) {
                    log.warning("'" + where + ".shape' uses '" + slot + "', which no key defines, so it is off.");
                    return OFF;
                }
            }
        }

        return new RecipeSpec(true, true, amount, List.copyOf(rows), Map.copyOf(keys), List.of());
    }

    /** Null when the spec is off, so a caller registers whatever it gets back and nothing else. */
    @Nullable
    public Recipe toRecipe(NamespacedKey key, ItemStack result) {
        if (!enabled) return null;

        ItemStack output = result.clone();
        output.setAmount(amount);

        if (!shaped) {
            ShapelessRecipe recipe = new ShapelessRecipe(key, output);
            ingredients.forEach(recipe::addIngredient);
            return recipe;
        }

        ShapedRecipe recipe = new ShapedRecipe(key, output);
        recipe.shape(shape.toArray(String[]::new));
        keys.forEach(recipe::setIngredient);
        return recipe;
    }

    /**
     * One line of a shapeless list: {@code MATERIAL} or {@code MATERIAL count}.
     *
     * <p>Added to {@code into} once per item asked for, since a shapeless recipe counts items and not kinds.
     */
    private static void ingredient(String line, String where, Logger log, List<Material> into) {
        String[] parts = line.trim().split("\\s+");
        Material material = material(parts[0], where, log);
        if (material == null) return;

        int count = 1;
        if (parts.length > 1) {
            try {
                count = Math.max(1, Integer.parseInt(parts[1]));
            } catch (NumberFormatException notANumber) {
                log.warning("'" + where + ".ingredients' has \"" + line + "\" in it, which is not \"MATERIAL count\"."
                        + " Taking one.");
            }
        }

        for (int i = 0; i < count; i++) {
            into.add(material);
        }
    }

    @Nullable
    private static Material material(String name, String where, Logger log) {
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            log.warning("'" + where + "' names '" + name + "', which is not an item.");
            return null;
        }
        return material;
    }
}
