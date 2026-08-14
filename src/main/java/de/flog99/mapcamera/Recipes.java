package de.flog99.mapcamera;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers what config says is craftable, and takes it away again.
 *
 * <p>One recipe per camera a server has switched on, plus one for film - there is one film whatever cameras exist.
 */
public final class Recipes {

    private final Plugin plugin;
    private final CameraItems items;

    private final List<NamespacedKey> registered = new ArrayList<>();

    private boolean autoDiscover;

    public Recipes(Plugin plugin, CameraItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void register(CameraConfig config) {
        autoDiscover = config.autoDiscover();

        for (CameraKind camera : config.cameras()) {
            add("camera_" + camera.id(), camera.recipe(), items.camera(camera));
        }
        add("film", config.filmRecipe(), items.film(1));

        if (!registered.isEmpty()) {
            plugin.getServer().updateRecipes();
        }
    }

    public void unregister() {
        registered.forEach(plugin.getServer()::removeRecipe);
        registered.clear();
    }

    /** Puts them in the recipe book, since a recipe nobody is told about is a recipe nobody finds. */
    public void discoverFor(Player player) {
        if (autoDiscover && !registered.isEmpty()) {
            player.discoverRecipes(registered);
        }
    }

    private void add(String name, RecipeSpec spec, ItemStack result) {
        NamespacedKey key = new NamespacedKey(plugin, name);
        Recipe recipe = spec.toRecipe(key, result);
        if (recipe == null) return;

        // Left over from a previous enable if a reload swapped the plugin without a restart.
        plugin.getServer().removeRecipe(key);
        plugin.getServer().addRecipe(recipe);
        registered.add(key);
    }
}
