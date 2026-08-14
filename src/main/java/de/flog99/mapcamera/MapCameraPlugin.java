package de.flog99.mapcamera;

import de.flog99.mapgui.HandOptions;
import de.flog99.mapgui.HeldTrigger;
import de.flog99.mapgui.MapGui;
import de.flog99.mapgui.Screen;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * A camera you can craft, hold and take photographs with.
 *
 * <p>Everything on screen is MapGUI's: the viewfinder is a fake map faked into the offhand while the camera is in
 * the main one, and the live picture in it is {@code MapGui.get().camera()}. This plugin owns the item, the film,
 * the shutter and what a photograph turns into, and none of the drawing.
 */
public final class MapCameraPlugin extends JavaPlugin implements Listener {

    private CameraConfig config;
    private CameraItems items;
    private Shooting shooting;
    private Recipes recipes;
    private PackServer pack;
    private HeldTrigger trigger;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = CameraConfig.read(this);
        items = new CameraItems(this);
        shooting = new Shooting(this, items, config);
        recipes = new Recipes(this, items);

        // MapGUI's camera is a server-side renderer and cannot see what any client has, so it is handed the same
        // zip out of this jar - otherwise a photograph of somebody holding a camera shows a knowledge book.
        // First, because the hash it measures is what clients are then offered the pack under.
        String hash = MapGui.get().camera().useResourcePack(this, PackServer.PACK);
        pack = new PackServer(this, config.pack());
        pack.start(hash);

        items.update(config, pack.offering());
        recipes.register(config);
        openWhileHeld();

        getServer().getPluginManager().registerEvents(new ItemGuard(items), this);
        getServer().getPluginManager().registerEvents(this, this);
        CameraCommands.register(this);
    }

    @Override
    public void onDisable() {
        closeViewfinders();
        if (pack != null) {
            pack.stop();
        }
        recipes.unregister();
    }

    public CameraConfig config() {
        return config;
    }

    public CameraItems items() {
        return items;
    }

    public Shooting shooting() {
        return shooting;
    }

    /**
     * Re-reads config.yml and rebuilds everything made from it.
     *
     * <p>Open viewfinders are closed rather than migrated: each holds the settings it opened with, and taking the
     * camera out again is cheaper than making every field re-readable. The command tree is not rebuilt, so a
     * camera switched on here has no {@code /mapcamera give} branch until a restart.
     */
    public void reload() {
        reloadConfig();
        config = CameraConfig.read(this);
        shooting.reload(config);

        closeViewfinders();
        recipes.unregister();

        // The port and the address can both have changed. The pack itself never does - it is in the jar - which is
        // why MapGUI is not asked again and the measured hash is carried over.
        String hash = pack.hash();
        pack.stop();
        pack = new PackServer(this, config.pack());
        pack.start(hash);

        items.update(config, pack.offering());
        recipes.register(config);
        openWhileHeld();
    }

    /** The pack, and the recipes, for somebody who was not here when either was set up. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        pack.offerTo(event.getPlayer());
        recipes.discoverFor(event.getPlayer());
        items.regroupCameras(event.getPlayer());
    }

    /**
     * The standing rule that puts a viewfinder in the offhand of whoever is holding a camera.
     *
     * <p>{@code ALWAYS} is what makes the marks clickable, and it costs the player their attack and place clicks
     * for as long as the camera is out. The predicate runs for one stack per player per tick, so it reads the
     * stack's data view rather than its meta.
     */
    private void openWhileHeld() {
        trigger = MapGui.get().openWhileHolding(items::isCamera, HandOptions.Focus.ALWAYS, this::viewfinderFor);
    }

    /**
     * Null opens nothing, which is how a permission is enforced for a screen nobody asked to open - and what a
     * camera held on a server that has since switched every camera off gets.
     */
    @Nullable
    private Screen viewfinderFor(Player player) {
        if (!player.hasPermission("mapcamera.use")) return null;

        CameraKind camera = items.cameraOf(player.getInventory().getItemInMainHand());
        return camera == null ? null : new ViewfinderScreen(this, camera);
    }

    private void closeViewfinders() {
        if (trigger != null) {
            trigger.cancel();
            trigger = null;
        }
    }
}
