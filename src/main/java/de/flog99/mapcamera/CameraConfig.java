package de.flog99.mapcamera;

import de.flog99.mapgui.camera.Camera;
import de.flog99.mapgui.camera.CameraOptions;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The whole of config.yml, read once and handed round as a value.
 *
 * <p>Re-read rather than mutated on {@code /mapcamera reload}: the plugin swaps one of these for another and
 * rebuilds everything made from it, so nothing holds a half-updated view.
 */
public record CameraConfig(
        List<CameraKind> cameras,
        ItemLook film,
        Photo photo,
        Viewfinder viewfinder,
        Sounds sounds,
        Messages messages,
        boolean autoDiscover,
        RecipeSpec filmRecipe,
        PackServer.PackOptions pack) {

    /** What a photograph is once it exists, as opposed to what it costs - that is {@link CameraKind}'s. */
    public record Photo(String name, boolean stamp) {
    }

    /**
     * The screen and the lens: the preview's pixel count, what fires the shutter, and the two zoom ladders.
     *
     * @param shot one set of camera settings for both the preview and the photograph, so the framing a player lines
     *             up is the framing they get. Only the pixel count differs
     */
    public record Viewfinder(int previewSize, Trigger trigger, List<Double> zoomSteps, List<Double> selfieZoomSteps,
                             CameraOptions shot) {

        public CameraOptions preview() {
            return shot.size(previewSize);
        }

        /** The field of view at a zoom step, which is all zooming is: a narrower cone over the same pixels. */
        public float fovAt(int step, boolean selfie) {
            return (float) (shot.fov() / zoomFactor(step, selfie));
        }

        public double zoomFactor(int step, boolean selfie) {
            List<Double> ladder = steps(selfie);
            return ladder.get(Math.clamp(step, 0, ladder.size() - 1));
        }

        public int maxZoomStep(boolean selfie) {
            return steps(selfie).size() - 1;
        }

        /** Where the wheel starts, and where it goes back to when the lens turns round: 1, wherever that sits. */
        public int defaultZoomStep(boolean selfie) {
            return steps(selfie).indexOf(1.0);
        }

        /** Pointed outward the ladder climbs; turned around the lens is already at arm's length, so that one widens. */
        private List<Double> steps(boolean selfie) {
            return selfie ? selfieZoomSteps : zoomSteps;
        }
    }

    public static CameraConfig read(Plugin plugin) {
        FileConfiguration config = plugin.getConfig();
        Logger log = plugin.getLogger();

        ConfigurationSection view = section(config, "viewfinder");
        ConfigurationSection photo = section(config, "photo");
        ConfigurationSection recipes = section(config, "recipes");

        Trigger trigger = trigger(view, log);
        CameraOptions shot = CameraOptions.defaults()
                .size(Camera.MAP_SIZE)
                .fov(view.getInt("fov", 70))
                .maxDistance(view.getInt("max-distance", 0))
                .entities(view.getBoolean("entities", true))
                .clouds(view.getBoolean("clouds", true))
                .fog(view.getBoolean("fog", true));

        return new CameraConfig(
                CameraKind.read(section(config, "cameras"), trigger, CameraKind.Costs.read(photo), log),
                ItemLook.read(section(config, "film"), Material.KNOWLEDGE_BOOK, 64,
                        "mapcamera:film", "minecraft:paper", log),
                new Photo(photo.getString("name", "<white>Photograph"), photo.getBoolean("stamp", true)),
                new Viewfinder(
                        Math.clamp(view.getInt("size", 96), 16, Camera.MAP_SIZE),
                        trigger,
                        zoomSteps(view, "zoom-steps", List.of(1.0, 1.5, 2.0, 3.0, 4.0, 6.0), log),
                        zoomSteps(view, "selfie-zoom-steps", List.of(0.5, 0.65, 0.8, 1.0, 1.25, 1.5, 2.0), log),
                        shot),
                Sounds.read(section(config, "sounds"), log),
                Messages.read(section(config, "messages"), trigger),
                recipes.getBoolean("auto-discover", true),
                RecipeSpec.read(section(recipes, "film"), log),
                pack(section(config, "resource-pack"))
        );
    }

    /** The first camera that is switched on, or null if none is. */
    @Nullable
    public CameraKind defaultCamera() {
        return cameras.isEmpty() ? null : cameras.getFirst();
    }

    /** The camera with this id, falling back to the first one - a reload may have turned the named one off. */
    @Nullable
    public CameraKind camera(String id) {
        return cameras.stream().filter(camera -> camera.id().equals(id)).findFirst().orElseGet(this::defaultCamera);
    }

    /**
     * A url the owner hosts wins over the built-in server, since filling it in is a statement about which copy
     * they mean.
     */
    private static PackServer.PackOptions pack(ConfigurationSection section) {
        return new PackServer.PackOptions(
                section.getString("url", ""),
                section.getBoolean("serve", true),
                section.getInt("port", 8321),
                section.getString("address", "localhost"),
                section.getString("prompt", "<gold>MapCamera</gold><gray> - models for the camera and film"),
                section.getBoolean("required", false));
    }

    /**
     * Sanitised, because a bad ladder here is a camera that cannot be aimed.
     *
     * <p>Steps below 1 zoom out and are allowed; zero and below are not, since the field of view is divided by
     * this. Sorted so the wheel steps in one direction, and 1 is always in there because it is where the camera opens.
     */
    private static List<Double> zoomSteps(ConfigurationSection view, String key, List<Double> fallback, Logger log) {
        List<Double> configured = view.getDoubleList(key).stream()
                .filter(step -> step > 0)
                .distinct()
                .sorted()
                .toList();

        if (configured.isEmpty()) {
            if (!view.getDoubleList(key).isEmpty()) {
                log.warning("'viewfinder." + key + "' had nothing usable in it - every step must be above 0. Using the defaults.");
            }
            return fallback;
        }
        if (configured.contains(1.0)) return configured;

        return Stream.concat(Stream.of(1.0), configured.stream()).sorted().toList();
    }

    private static Trigger trigger(ConfigurationSection view, Logger log) {
        String named = view.getString("shutter", "swap-hands").trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return Trigger.valueOf(named);
        } catch (IllegalArgumentException unknown) {
            log.warning("viewfinder.shutter is \"" + view.getString("shutter")
                    + "\", which is not swap-hands, left-click or right-click. Using swap-hands.");
            return Trigger.SWAP_HANDS;
        }
    }

    /** An absent section reads as an empty one, so a config missing a block falls back to defaults rather than throwing. */
    private static ConfigurationSection section(ConfigurationSection parent, String name) {
        ConfigurationSection found = parent.getConfigurationSection(name);
        return found != null ? found : parent.createSection(name);
    }
}
