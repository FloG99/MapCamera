package de.flog99.mapcamera;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Everything a player reads on the viewfinder, from config.
 *
 * <p>Plain text, not MiniMessage: these are painted onto a 128 pixel map by the map font, which has no colours and
 * does not wrap. Item names and lore are MiniMessage and live in {@link ItemLook}.
 */
public record Messages(
        String outOfFilm,
        String needFilm,
        String noTextures,
        String loadingTextures,
        String exposureFailed,
        String zoom,
        String shutterHint,
        String lensAround,
        String lensForward,
        String mode2x2,
        String mode1x1,
        String mode2x2Free,
        String mode1x1Free,
        String printFailed) {

    public static Messages read(ConfigurationSection section, Trigger trigger) {
        return new Messages(
                section.getString("out-of-film", "Out of film"),
                section.getString("need-film", "Need %film% film"),
                section.getString("no-textures", "No textures yet"),
                section.getString("loading-textures", "Loading textures"),
                section.getString("exposure-failed", "Exposure failed"),
                section.getString("zoom", "%zoom%x"),
                section.getString("shutter-hint", "%shutter% for photo").replace("%shutter%", trigger.named()),
                section.getString("lens-around", "Turn the lens around"),
                section.getString("lens-forward", "Turn the lens forward"),
                section.getString("2x2", "2x2 - costs 4 film"),
                section.getString("1x1", "1x1 - costs 1 film"),
                section.getString("2x2-free", "2x2 - 4 maps"),
                section.getString("1x1-free", "1x1 - 1 map"),
                section.getString("print-failed", "<red>That photograph could not be printed onto a map."));
    }

    public String needFilm(int sheets) {
        return sheets == 1 ? outOfFilm : needFilm.replace("%film%", String.valueOf(sheets));
    }

    public String zoom(String factor) {
        return zoom.replace("%zoom%", factor);
    }

    /** The grid captions, the -free pair being for a camera that charges no film and so must not name any. */
    public String mode2x2(boolean film) {
        return film ? mode2x2 : mode2x2Free;
    }

    public String mode1x1(boolean film) {
        return film ? mode1x1 : mode1x1Free;
    }
}
