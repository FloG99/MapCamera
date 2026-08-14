package de.flog99.mapcamera;

import de.flog99.mapgui.MapGui;
import de.flog99.mapgui.camera.CameraAssets;
import de.flog99.mapgui.camera.CameraStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * {@code /mapcamera debug} - why the viewfinder is running at the rate it is, and what that is costing.
 *
 * <p>Three questions in order: what rate am I getting, what is holding it there, and what would move it. Anything
 * that does not serve that chain is left out.
 *
 * <p>All of it comes from {@link CameraStats}, which is API - so a server with MapGUI's own commands turned off
 * loses nothing.
 */
final class Diagnostics {

    private Diagnostics() {
    }

    static void print(CommandSender sender) {
        CameraStats stats = MapGui.get().camera().stats();

        String textures = textures();
        sender.sendMessage(Component.text("MapCamera", NamedTextColor.GOLD)
                .append(textures == null ? Component.empty() : Component.text("  " + textures, NamedTextColor.YELLOW)));

        addRate(sender, stats);
        addCost(sender, stats);
        addTrouble(sender, stats);
    }

    /**
     * The rate this player is getting and the reason it is that and not more.
     *
     * <p>Both together or neither is worth printing: 3 fps under a 10 fps cap is a budget that ran out, and 3 fps
     * under a 3 fps cap is a setting somebody chose. They call for opposite actions and look identical alone.
     */
    private static void addRate(CommandSender sender, CameraStats stats) {
        CameraStats.Live live = stats.live();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(line("previews", live.viewers() == 0 ? "none open" : live.viewers() + " open", holdingIt(stats)));
            return;
        }

        double fps = MapGui.get().camera().frameRate(player);
        int others = Math.max(0, live.viewers() - 1);

        sender.sendMessage(line("your preview",
                fps <= 0 ? "not open" : String.format(Locale.ROOT, "%.1f fps", fps),
                fps <= 0 ? "hold a camera to open one" : holdingIt(stats)
                        + (others == 0 ? "" : ", shared with " + others + " other" + (others == 1 ? "" : "s"))));
    }

    /** Which of the two limits is the binding one. MapGUI works that out; the wording is ours. */
    private static String holdingIt(CameraStats stats) {
        return switch (stats.bound()) {
            case NOTHING_OPEN -> "nothing to hold back";
            case FPS_CEILING -> "at the " + stats.liveFpsCeiling() + " fps cap";
            case TICK_BUDGET -> String.format(Locale.ROOT, "held by the %.1fms/t budget", stats.liveMaxMillisPerTick());
            case UNLIMITED -> "no limit set";
        };
    }

    /**
     * What one frame costs the tick, split three ways because the three call for different fixes: chunk columns
     * copied, entities in shot, and the chests and signs in range.
     *
     * <p>Each carries what it went through beside what it cost - a slow stage is either a lot of things or
     * expensive things, and those have opposite answers. The per-tick total underneath is for the server owner.
     */
    private static void addCost(CommandSender sender, CameraStats stats) {
        if (stats.captures() == 0) {
            sender.sendMessage(line("frames", "none in the last few seconds", ""));
            return;
        }

        sender.sendMessage(Component.text("  each frame  ", NamedTextColor.GRAY)
                .append(Component.text(String.format(Locale.ROOT, "%.1fms", stats.mainMillisEach()), NamedTextColor.WHITE))
                .append(Component.text(String.format(Locale.ROOT,
                        "   blocks %.1fms (%.0f chunks, %.0f%% reused), entities %.1fms (%.0f, %.0f%% reused), tile entities %.1fms (%.0f)",
                        stats.blockMillisEach(), stats.blocks().chunksEach(), stats.blocks().reusedPercent(),
                        stats.entityMillisEach(), stats.entitiesEach(), stats.entitiesReusedPercent(),
                        stats.blockEntityMillisEach(), stats.blockEntitiesEach()), NamedTextColor.DARK_GRAY)));

        // What it costs the SERVER rather than what it costs a frame: the same work divided over every tick,
        // which is the figure the budget is written in and the only one a server owner has to care about.
        sender.sendMessage(Component.text("  costs the server  ", NamedTextColor.GRAY)
                .append(Component.text(String.format(Locale.ROOT, "%.2fms/t", stats.mainMillisPerTick()), tickColor(stats.tickPercent())))
                .append(Component.text(String.format(Locale.ROOT, "   %.1f%% of every tick, slowest frame %.1fms",
                        stats.tickPercent(), stats.worstMainMillis()), NamedTextColor.DARK_GRAY)));
    }

    /** Nothing at all when nothing is wrong, which is what makes any of these worth reading when they appear. */
    private static void addTrouble(CommandSender sender, CameraStats stats) {
        if (stats.dropped() > 0) {
            sender.sendMessage(Component.text("  turned away  ", NamedTextColor.RED)
                    .append(Component.text(stats.dropped() + " captures", NamedTextColor.WHITE))
                    .append(Component.text("   asked for faster than they can be drawn", NamedTextColor.DARK_GRAY)));
        }

        // Only ours can be unpaced here, and a feed always asks - so this appearing means a path that does not.
        if (stats.unpacedPerSecond() > 0 && (stats.liveMaxMillisPerTick() > 0 || stats.liveFpsCeiling() > 0)) {
            sender.sendMessage(Component.text("  unpaced  ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format(Locale.ROOT, "%.1f/s", stats.unpacedPerSecond()), NamedTextColor.WHITE))
                    .append(Component.text("   taken outside the budget", NamedTextColor.DARK_GRAY)));
        }

        if (stats.lastFailure() != null) {
            sender.sendMessage(Component.text("  failed  ", NamedTextColor.RED)
                    .append(Component.text(stats.lastFailure().reason(), NamedTextColor.WHITE)));
        }
    }

    /** Null once the textures are there, since a working camera is not something anybody needs telling about. */
    private static String textures() {
        return switch (MapGui.get().camera().assets()) {
            case CameraAssets.Ready ignored -> null;
            case CameraAssets.Loading ignored -> "still initializing";
            case CameraAssets.Unavailable unavailable -> "no textures - " + unavailable.fix();
        };
    }

    /** One percent of a tick is noticeable on a busy server, five is a problem. */
    private static NamedTextColor tickColor(double percent) {
        if (percent >= 5) return NamedTextColor.RED;
        if (percent >= 1) return NamedTextColor.YELLOW;

        return NamedTextColor.GREEN;
    }

    private static Component line(String name, String value, String note) {
        return Component.text("  " + name + "  ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE))
                .append(note.isEmpty() ? Component.empty() : Component.text("   " + note, NamedTextColor.DARK_GRAY));
    }
}
