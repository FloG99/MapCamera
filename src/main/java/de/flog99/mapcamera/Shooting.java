package de.flog99.mapcamera;

import de.flog99.mapgui.camera.CameraShot;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * What pressing the shutter costs and what it produces: the wind-on, the noises, and the print.
 *
 * <p>{@link ViewfinderScreen} drives it, and this is all it needs from the server - which keeps the screen off
 * the plugin class.
 */
public final class Shooting {

    /** Vanilla counts a cooldown in ticks and everything else here is in milliseconds. */
    private static final long MILLIS_PER_TICK = 50;

    private final CameraItems items;
    private final Photos photos;

    private CameraConfig config;

    public Shooting(Plugin plugin, CameraItems items, CameraConfig config) {
        this.items = items;
        this.photos = new Photos(plugin);
        this.config = config;
    }

    public void reload(CameraConfig value) {
        this.config = value;
    }

    /** Heard at the moment of the press, not when the picture lands. */
    public void snap(Player player) {
        config.sounds().snap(player);
    }

    /**
     * The picture came back, so it becomes maps and the film is spent.
     *
     * <p>Film is counted off what was actually printed rather than off what the screen asked for, and a
     * photograph that could not be printed costs neither film nor the wait for the next one.
     */
    public void develop(Player player, CameraKind camera, CameraShot shot) {
        List<ItemStack> printed = photos.give(player, shot, config.photo());
        if (printed.isEmpty()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(config.messages().printFailed()));
            clearWindOn(player, camera);
            return;
        }

        items.spendFilm(player, camera, printed.size());
        config.sounds().landed(player);
    }

    /**
     * The wind-on, kept as vanilla's own item cooldown rather than as a map of our own.
     *
     * <p>Which puts it where a player already looks: the white sweep over the item in the hotbar. It survives us
     * not being ticked and leaves the one line on a 128 pixel readout for something else.
     */
    public long windOnLeft(Player player, CameraKind camera) {
        return player.getCooldown(camera.cooldownGroup()) * MILLIS_PER_TICK;
    }

    public void startWindOn(Player player, CameraKind camera) {
        if (camera.cooldownMillis() <= 0) return;

        player.setCooldown(camera.cooldownGroup(), (int) Math.max(1, camera.cooldownMillis() / MILLIS_PER_TICK));
    }

    /** An exposure the server failed to take should not cost the player the wait for the next one. */
    public void clearWindOn(Player player, CameraKind camera) {
        player.setCooldown(camera.cooldownGroup(), 0);
    }
}
