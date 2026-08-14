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
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * What one of this plugin's items looks like, read from config.
 *
 * <p>Three settings give an item a face, and they compose rather than compete:
 *
 * <ul>
 *   <li>{@code item-model} names the model to draw in place of the item's own. Left as {@code auto} it names a
 *       <b>vanilla</b> model - paper, a head - which every client can draw, pack or no pack.
 *   <li>A {@code custom_model_data} string carries which of this plugin's models the item really wants. The pack
 *       overrides that vanilla item with a {@code select} on the string: the case draws the camera, the fallback
 *       draws what vanilla drew. So the model is <i>chosen by the client that has the pack</i> rather than by the
 *       server when the item is minted - which is the only way a player who declined the pack sees anything but a
 *       missing-texture cube.
 *   <li>{@code head-texture} is a base64 profile value. Under {@code item-model: minecraft:player_head} it gives
 *       any item the shape of a head, because vanilla's head model reads the profile off <i>the stack it is
 *       drawing</i> rather than off the item type. So the item underneath can be something that is not a block.
 * </ul>
 *
 * @param material        the base item, chosen for having no action of its own rather than for its texture
 * @param name            a MiniMessage display name, or empty for the material's own
 * @param model           the model to draw, or null for the material's own
 * @param customModelData the string the pack switches on, or null where {@code item-model} was set by hand and the
 *                        server owner is naming a model themselves
 * @param maxStackSize    how many go in a slot, whatever the material allows. Clamped to 1 to 64
 */
public record ItemLook(Material material, String name, List<String> lore, String headTexture,
                       @Nullable NamespacedKey model, @Nullable String customModelData, int maxStackSize) {

    /** The only model that reads a profile, and so the only one {@code head-texture} means anything under. */
    private static final NamespacedKey HEAD_MODEL = NamespacedKey.minecraft("player_head");

    /** Every head shares one id: the profile carries the texture, and the uuid only has to be stable. */
    private static final UUID HEAD_ID = UUID.nameUUIDFromBytes("MapCamera".getBytes());

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /**
     * @param packModel  this plugin's model, which under {@code auto} becomes the string the pack switches on
     * @param plainModel the vanilla model the item is drawn as, and what a client without the pack is left with
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

        // Named by hand, the server owner has said what they want drawn, so nothing is layered under it.
        NamespacedKey model = keyed(automatic ? plainModel : stated, section, log);
        String switching = automatic ? packModel : null;

        String texture = section.getString("head-texture", "").trim();
        if (!texture.isEmpty() && !HEAD_MODEL.equals(model)) {
            log.warning("'" + section.getCurrentPath() + ".head-texture' is set, but nothing here is drawn as a head,"
                    + " so it will never be seen. Only 'item-model: " + HEAD_MODEL + "' reads one.");
        }

        return new ItemLook(material, section.getString("name", ""), section.getStringList("lore"), texture,
                model, switching, Math.clamp(section.getInt("max-stack-size", stackTo), 1, 64));
    }

    /** The same look with a token swapped through its lore, so config text can name a configured thing. */
    public ItemLook replacing(String token, String value) {
        List<String> swapped = lore.stream().map(line -> line.replace(token, value)).toList();

        return new ItemLook(material, name, swapped, headTexture, model, customModelData, maxStackSize);
    }

    /** Applies everything but the plugin's own tag, which {@link CameraItems} adds on top. */
    public ItemStack build(int amount) {
        ItemStack stack = new ItemStack(material, amount);
        stack.editMeta(this::decorate);

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

    private void decorate(ItemMeta meta) {
        if (!name.isEmpty()) {
            meta.displayName(plain(name));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(ItemLook::plain).toList());
        }
        meta.setItemModel(model);
        if (customModelData != null) {
            CustomModelDataComponent switching = meta.getCustomModelDataComponent();
            switching.setStrings(List.of(customModelData));
            meta.setCustomModelDataComponent(switching);
        }
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
