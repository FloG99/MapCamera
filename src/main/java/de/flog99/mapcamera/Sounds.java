package de.flog99.mapcamera;

import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * The two moments the camera makes a noise: the press, and the print landing.
 *
 * <p>Each is a list rather than one sound, because a camera is a mechanism and one sample never reads as one. Any
 * sound key the server knows, including a pack's own.
 */
public record Sounds(List<Cue> shutter, List<Cue> printed) {

    /** One sound, written in config as {@code "key volume pitch"}. */
    public record Cue(String key, float volume, float pitch) {

        void playTo(Player player) {
            player.playSound(player, key, SoundCategory.PLAYERS, volume, pitch);
        }
    }

    private static final List<Cue> SHUTTER = List.of(
            new Cue("minecraft:ui.button.click", 0.9f, 1.0f),
            new Cue("minecraft:block.wooden_button.click_on", 0.9f, 0.9f)
    );

    private static final List<Cue> PRINTED = List.of(
            new Cue("minecraft:entity.item_frame.add_item", 0.8f, 0.9f),
            new Cue("minecraft:entity.item_pickup", 0.6f, 1.0f)
    );

    public static Sounds read(ConfigurationSection section, Logger log) {
        return new Sounds(cues(section, "shutter", SHUTTER, log), cues(section, "printed", PRINTED, log));
    }

    public void snap(Player player) {
        shutter.forEach(cue -> cue.playTo(player));
    }

    public void landed(Player player) {
        printed.forEach(cue -> cue.playTo(player));
    }

    /** An empty list is silence, which is a real answer - so only a list that was left out falls back. */
    private static List<Cue> cues(ConfigurationSection section, String key, List<Cue> fallback, Logger log) {
        if (!section.contains(key)) return fallback;

        return section.getStringList(key).stream().map(line -> cue(line, section, key, log)).filter(Objects::nonNull).toList();
    }

    /**
     * {@code "key volume pitch"}, with the two numbers optional.
     *
     * <p>Not validated against the server's sound registry: a pack may add its own, and a key that names nothing
     * simply plays nothing.
     */
    private static Cue cue(String line, ConfigurationSection section, String key, Logger log) {
        String[] parts = line.trim().split("\\s+");
        if (parts[0].isEmpty()) return null;

        try {
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            return new Cue(parts[0].toLowerCase(Locale.ROOT), volume, pitch);
        } catch (NumberFormatException notANumber) {
            log.warning("'" + section.getCurrentPath() + "." + key + "' has \"" + line
                    + "\" in it, which is not \"key volume pitch\". That one is skipped.");
            return null;
        }
    }
}
