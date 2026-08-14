package de.flog99.mapcamera;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Mints this plugin's items, and recognises them again afterwards.
 *
 * <p>Recognition is by a tag in the item's own data rather than by material or by name, since both of those are
 * config: a camera crafted before an admin changed their mind has to keep working after it.
 */
public final class CameraItems {

    private static final String CAMERA = "camera";
    private static final String FILM = "film";

    private final NamespacedKey kind;

    /** Which camera it is, kept beside {@link #kind} so every "is this one of ours" guard works unchanged. */
    private final NamespacedKey design;

    private CameraConfig config;

    public CameraItems(Plugin plugin) {
        this.kind = new NamespacedKey(plugin, "kind");
        this.design = new NamespacedKey(plugin, "design");
    }

    /** Called on startup and again on every reload. Nothing may be minted before it. */
    public void update(CameraConfig value) {
        this.config = value;
    }

    /** One of the cameras a server has switched on, carrying which one it is so it behaves as that one. */
    public ItemStack camera(CameraKind camera) {
        ItemStack stack = grouped(tagged(camera.look().build(1), CAMERA), camera);
        stack.editPersistentDataContainer(data -> data.set(design, PersistentDataType.STRING, camera.id()));
        return stack;
    }

    public ItemStack film(int amount) {
        return tagged(config.film().build(amount), FILM);
    }

    /**
     * Which camera this is: the face it opens as, and the rules it is shot under.
     *
     * <p>The tag names the camera rather than the design it wears, since two cameras may share a body and would
     * then have to charge the same film. Null when no camera is switched on, or when the one this was crafted as
     * has been turned off and there is nothing left to fall back to.
     */
    @Nullable
    public CameraKind cameraOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return config.defaultCamera();

        String named = stack.getPersistentDataContainer().get(design, PersistentDataType.STRING);
        return named == null ? config.defaultCamera() : config.camera(named);
    }

    public boolean isCamera(@Nullable ItemStack stack) {
        return CAMERA.equals(kindOf(stack));
    }

    public boolean isFilm(@Nullable ItemStack stack) {
        return FILM.equals(kindOf(stack));
    }

    /** Either of ours, for the guard that keeps them from being placed or worn. */
    public boolean isOurs(@Nullable ItemStack stack) {
        return kindOf(stack) != null;
    }

    /**
     * Takes film, if this camera charges for it and there is any to take.
     *
     * <p>Called when the photograph exists rather than when the shutter is pressed, so a capture that fails costs
     * nothing and needs nothing put back.
     */
    public void spendFilm(Player player, CameraKind camera, int sheets) {
        if (!camera.filmRequired()) return;

        int owed = sheets;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && owed > 0; slot++) {
            if (!isFilm(contents[slot])) continue;

            ItemStack stack = contents[slot];
            int taken = Math.min(owed, stack.getAmount());
            stack.setAmount(stack.getAmount() - taken);
            owed -= taken;
            // Written back by slot rather than left to the array: whether getContents hands out live stacks or
            // copies is an implementation detail.
            player.getInventory().setItem(slot, stack.getAmount() <= 0 ? null : stack);
        }
    }

    /** How many shots are left, or -1 when this camera charges no film. */
    public int filmLeft(Player player, CameraKind camera) {
        if (!camera.filmRequired()) return -1;

        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isFilm(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Brings every camera this player carries into its own cooldown group, so the wind-on shows on all of them.
     *
     * <p>A camera minted before its own group existed is counted against its item <b>type</b> instead, and a
     * wind-on set on the group would sweep nothing. Cheap and idempotent, so it runs on join.
     */
    public void regroupCameras(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (!isCamera(stack)) continue;

            CameraKind camera = cameraOf(stack);
            if (camera == null || camera.cooldownMillis() <= 0 || camera.cooldownGroup().equals(groupOf(stack))) continue;

            player.getInventory().setItem(slot, grouped(stack, camera));
        }
    }

    /** An empty use_cooldown component would be a promise the plugin never keeps, so a camera with no wind-on gets none. */
    private static ItemStack grouped(ItemStack stack, CameraKind camera) {
        if (camera.cooldownMillis() <= 0) return stack;

        stack.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown
                .useCooldown(camera.cooldownMillis() / 1000f)
                .cooldownGroup(camera.cooldownGroup())
                .build());
        return stack;
    }

    @Nullable
    private static Key groupOf(ItemStack stack) {
        UseCooldown cooldown = stack.getData(DataComponentTypes.USE_COOLDOWN);
        return cooldown == null ? null : cooldown.cooldownGroup();
    }

    /**
     * Read off the stack's own data view, which copies nothing.
     *
     * <p>Called for one stack per player per tick by the trigger that opens the viewfinder, where
     * {@code getItemMeta} would clone the meta every time.
     */
    @Nullable
    private String kindOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        return stack.getPersistentDataContainer().get(kind, PersistentDataType.STRING);
    }

    private ItemStack tagged(ItemStack stack, String value) {
        stack.editPersistentDataContainer(data -> data.set(kind, PersistentDataType.STRING, value));
        return stack;
    }
}
