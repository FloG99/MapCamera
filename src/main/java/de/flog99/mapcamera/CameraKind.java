package de.flog99.mapcamera;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * One camera a server has switched on: what it looks like, how it is crafted, and what a photograph off it costs.
 *
 * <p>Several are allowed, and each carries its own {@link RecipeSpec} - two cameras crafted the same way would be
 * one camera with two names.
 *
 * @param body what it is on the map. Named in config, so two cameras can share one design
 */
public record CameraKind(String id, CameraBody body, ItemLook look, RecipeSpec recipe, Costs costs) {

    /**
     * What a photograph costs, from {@code photo:} unless the camera answers it itself.
     *
     * <p>The keys are spelled the same in either place, so moving one onto a camera is a copy rather than a
     * translation.
     *
     * @param twoByTwo whether the 2x2 toggle is offered at all. A player still chooses shot by shot
     */
    public record Costs(boolean filmRequired, int cooldownMillis, boolean twoByTwo) {

        public static Costs read(ConfigurationSection photo) {
            return new Costs(
                    photo.getBoolean("film-required", true),
                    Math.max(0, photo.getInt("cooldown-seconds", 3)) * 1000,
                    photo.getBoolean("2x2-shots", true));
        }

        static Costs read(ConfigurationSection camera, Costs shared) {
            return new Costs(
                    camera.getBoolean("film-required", shared.filmRequired()),
                    camera.contains("cooldown-seconds")
                            ? Math.max(0, camera.getInt("cooldown-seconds")) * 1000
                            : shared.cooldownMillis(),
                    camera.getBoolean("2x2-shots", shared.twoByTwo()));
        }
    }

    public boolean filmRequired() {
        return costs.filmRequired();
    }

    public int cooldownMillis() {
        return costs.cooldownMillis();
    }

    public boolean twoByTwo() {
        return costs.twoByTwo();
    }

    /**
     * The cooldown group the wind-on is counted against, one per camera so a slow camera does not lock a fast one.
     *
     * <p>A group at all because no group counts against the item's <b>type</b>, and the camera and the film are the
     * same type - so cooling the camera would sweep the film with it.
     */
    public Key cooldownGroup() {
        return Key.key("mapcamera", "camera_" + id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_"));
    }

    /**
     * Every camera under {@code cameras:}, in the order written, skipping the ones turned off.
     *
     * <p>Empty is allowed and means what it says: no camera can be crafted, given or held, and one already in an
     * inventory opens nothing. Turning them all off is a way to run the plugin with nothing switched on.
     */
    public static List<CameraKind> read(ConfigurationSection cameras, Trigger trigger, Costs shared, Logger log) {
        List<CameraKind> found = new ArrayList<>();

        for (String id : cameras.getKeys(false)) {
            ConfigurationSection section = cameras.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", false)) continue;

            found.add(one(id, section, trigger, shared, log));
        }

        if (found.isEmpty()) {
            log.info("No camera under 'cameras:' is enabled, so none can be crafted or held. "
                    + "Set 'enabled: true' on the one you want.");
        }
        return List.copyOf(found);
    }

    private static CameraKind one(String id, ConfigurationSection section, Trigger trigger, Costs shared, Logger log) {
        String named = section.getString("body", id);
        CameraBody body = CameraBody.byId(named);
        if (!body.id().equals(named.trim().toLowerCase(Locale.ROOT))) {
            log.warning("'cameras." + id + ".body' is not a camera design, so it will look like the "
                    + body.id() + " one. Try one of " + designs() + ".");
        }

        // The lore may name whatever takes the photograph, so it stays true when a server changes the shutter.
        ItemLook look = ItemLook.read(section, Material.KNOWLEDGE_BOOK, 1,
                        "mapcamera:" + body.id(), "minecraft:player_head", log)
                .replacing("%shutter%", trigger.gesture());

        ConfigurationSection recipe = section.getConfigurationSection("recipe");
        return new CameraKind(id, body, look,
                RecipeSpec.read(recipe != null ? recipe : section.createSection("recipe"), log),
                Costs.read(section, shared));
    }

    private static String designs() {
        return String.join(", ", CameraBody.all().stream().map(CameraBody::id).toList());
    }
}
