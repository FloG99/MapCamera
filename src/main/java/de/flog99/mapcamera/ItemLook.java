package de.flog99.mapcamera;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * What one of this plugin's items looks like, read from config.
 *
 * <p>Two settings give an item a face, and they compose rather than compete:
 *
 * <ul>
 *   <li>{@code item-model} names the model to draw in place of the item's own. A {@code minecraft:} model needs no
 *       pack, which is what makes the film a sheet of paper for free.
 *   <li>{@code head-texture} is a base64 profile value. Under {@code item-model: minecraft:player_head} it gives
 *       any item the shape of a head, because vanilla's head model reads the profile off <i>the stack it is
 *       drawing</i> rather than off the item type. So the item underneath can be something that is not a block.
 * </ul>
 *
 * @param material     the base item, chosen for having no action of its own rather than for its texture
 * @param name         a MiniMessage display name, or empty for the material's own
 * @param withPack     the model to draw where the resource pack is being handed out, or null for none
 * @param withoutPack  the model to draw where it is not. The same as {@code withPack} unless {@code item-model} was
 *                     left as {@code auto}
 * @param maxStackSize how many go in a slot, whatever the material allows. Clamped to 1 to 64
 */
public record ItemLook(Material material, String name, List<String> lore, String headTexture,
                       @Nullable NamespacedKey withPack, @Nullable NamespacedKey withoutPack, int maxStackSize) {

    /** The only model that reads a profile, and so the only one {@code head-texture} means anything under. */
    private static final NamespacedKey HEAD_MODEL = NamespacedKey.minecraft("player_head");

    /** Every head shares one id: the profile carries the texture, and the uuid only has to be stable. */
    private static final UUID HEAD_ID = UUID.nameUUIDFromBytes("MapCamera".getBytes());

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /**
     * @param packModel  what {@code auto} means where the pack is served
     * @param plainModel what it means where it is not: something vanilla already draws
     */
    public static ItemLook read(ConfigurationSection section, Material fallback, int stackTo,
                                String packModel, String plainModel, Logger log) {
        Material material = Material.matchMaterial(section.getString("material", fallback.name()));
        if (material == null || !material.isItem()) {
            log.warning("'" + section.getCurrentPath() + ".material' is not an item, using " + fallback + ".");
            material = fallback;
        }

        String stated = section.getString("item-model", "auto").trim();
        boolean automatic = stated.equalsIgnoreCase("auto");

        NamespacedKey withPack = keyed(automatic ? packModel : stated, section, log);
        NamespacedKey withoutPack = automatic ? keyed(plainModel, section, log) : withPack;

        String texture = section.getString("head-texture", "").trim();
        if (!texture.isEmpty() && !HEAD_MODEL.equals(withPack) && !HEAD_MODEL.equals(withoutPack)) {
            log.warning("'" + section.getCurrentPath() + ".head-texture' is set, but nothing here is drawn as a head,"
                    + " so it will never be seen. Only 'item-model: " + HEAD_MODEL + "' reads one.");
        }

        return new ItemLook(material, section.getString("name", ""), section.getStringList("lore"), texture,
                withPack, withoutPack, Math.clamp(section.getInt("max-stack-size", stackTo), 1, 64));
    }

    /** The same look with a token swapped through its lore, so config text can name a configured thing. */
    public ItemLook replacing(String token, String value) {
        List<String> swapped = lore.stream().map(line -> line.replace(token, value)).toList();

        return new ItemLook(material, name, swapped, headTexture, withPack, withoutPack, maxStackSize);
    }

    /** Applies everything but the plugin's own tag, which {@link CameraItems} adds on top. */
    public ItemStack build(int amount, boolean packServed) {
        NamespacedKey model = packServed ? withPack : withoutPack;

        ItemStack stack = new ItemStack(material, amount);
        stack.editMeta(meta -> decorate(meta, model));

        // Only under the head model, which is the one thing that reads it - anywhere else it is a component
        // nothing looks at. Set through the data component rather than through SkullMeta, which only exists for a
        // skull item: that is what lets the head be drawn on a knowledge book, and so what lets the camera not be
        // a block.
        if (HEAD_MODEL.equals(model) && !headTexture.isEmpty()) {
            PlayerProfile profile = Bukkit.createProfile(HEAD_ID);
            profile.setProperty(new ProfileProperty("textures", headTexture));
            stack.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(profile));
        }
        return stack;
    }

    private void decorate(ItemMeta meta, @Nullable NamespacedKey model) {
        if (!name.isEmpty()) {
            meta.displayName(plain(name));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(ItemLook::plain).toList());
        }
        meta.setItemModel(model);
        // Set either way, so the item does not quietly inherit whatever the base material happens to allow.
        meta.setMaxStackSize(maxStackSize);
    }

    @Nullable
    private static NamespacedKey keyed(String model, ConfigurationSection section, Logger log) {
        if (model.isEmpty()) return null;

        NamespacedKey key = NamespacedKey.fromString(model);
        if (key == null) {
            log.warning("'" + section.getCurrentPath() + ".item-model' is not a valid key: " + model);
        }
        return key;
    }

    /** Item text is italic by default, which nothing written for an item ever wants. */
    private static Component plain(String text) {
        return MINI.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }
}
