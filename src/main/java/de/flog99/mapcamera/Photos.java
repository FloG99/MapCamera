package de.flog99.mapcamera;

import de.flog99.mapgui.MapGui;
import de.flog99.mapgui.camera.CameraShot;
import de.flog99.mapgui.map.MapPrinter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Turns a capture into something a player can hang on a wall: a real vanilla map, locked as a cartography table
 * locks one, so it survives this plugin and MapGUI both being removed.
 *
 * <p><b>Which spends a map id the world keeps forever</b>, and nothing reclaims it. That is what the wind-on and
 * the film are for - see {@link MapPrinter}.
 */
public final class Photos {

    /** Maps to a side in 2x2 mode, so one shot is four maps, four film and 256 pixels. */
    public static final int GRID_ACROSS = 2;

    private static final DateTimeFormatter TAKEN_ON = DateTimeFormatter.ofPattern("d MMM yyyy");

    /** The grid a photograph was taken as, {@code "1x1"} or {@code "2x2"}. */
    private final NamespacedKey grid;

    /** Which piece of that grid this map is, from 1 in reading order. Always 1 on a single map. */
    private final NamespacedKey piece;

    public Photos(Plugin plugin) {
        this.grid = new NamespacedKey(plugin, "grid");
        this.piece = new NamespacedKey(plugin, "piece");
    }

    /** The film a shot this many maps across costs, which is one a map. */
    public static int filmFor(int across) {
        return across * across;
    }

    /**
     * Prints the capture and puts it in the player's hands, or at their feet if they have no room.
     *
     * <p>Empty if the pixels did not cut into whole maps, which is checked rather than assumed: what gets printed
     * has to follow the capture that actually arrived.
     */
    public List<ItemStack> give(Player player, CameraShot shot, CameraConfig.Photo settings) {
        int across = MapPrinter.mapsAcross(shot);
        if (across < 1) return List.of();

        List<ItemStack> printed = MapGui.get().printer().print(player.getWorld(), shot);
        if (printed.size() != filmFor(across)) return List.of();

        // MapPrinter cuts in reading order, left to right and top to bottom, which is the order the tags number.
        for (int i = 0; i < printed.size(); i++) {
            stamp(printed.get(i), player, settings, across, i + 1);
        }

        Map<Integer, ItemStack> spare = player.getInventory().addItem(printed.toArray(ItemStack[]::new));
        for (ItemStack overflow : spare.values()) {
            Item dropped = player.getWorld().dropItem(player.getEyeLocation(), overflow);
            dropped.setOwner(player.getUniqueId());
        }
        return printed;
    }

    /**
     * The name, and who took it and when under it.
     *
     * <p>{@code itemName} rather than {@code displayName}, which is the whole point: an item frame floats a
     * <b>custom</b> name over itself for anyone looking at the frame, and reads no other component to do it. An
     * item name is still the item's name in every tooltip and every inventory, so a photograph in the hand says
     * what it is and a photograph on a wall is the picture and nothing else.
     *
     * <p>Not where it was taken. A photograph is not a set of coordinates, and it hands them to whoever picks
     * it up.
     *
     * <p>Which grid it came from and which piece it is go in the item's own data instead. They are for whoever
     * is sorting a wall out - a datapack, another plugin, a command - and a number written into a line the
     * server owner configured in their own language would serve nobody.
     */
    private void stamp(ItemStack photo, Player player, CameraConfig.Photo settings, int across, int number) {
        photo.editMeta(meta -> {
            if (!settings.name().isBlank()) {
                meta.itemName(MiniMessage.miniMessage().deserialize(settings.name()).decoration(TextDecoration.ITALIC, false));
            }
            if (settings.stamp()) {
                meta.lore(List.of(Component.text(player.getName() + ", " + LocalDate.now().format(TAKEN_ON), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
            }
        });

        photo.editPersistentDataContainer(data -> {
            data.set(grid, PersistentDataType.STRING, across + "x" + across);
            data.set(piece, PersistentDataType.INTEGER, number);
        });
    }
}
