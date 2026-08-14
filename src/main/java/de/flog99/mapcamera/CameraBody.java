package de.flog99.mapcamera;

import de.flog99.mapgui.camera.Camera;
import de.flog99.mapgui.ui.Node;
import de.flog99.mapgui.ui.Painter;
import de.flog99.mapgui.ui.Rect;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * A camera's face on the map: the plate it is drawn on, where its screen sits, and where its controls go.
 *
 * <p>The only thing that differs between cameras, so adding one is a body and a texture rather than a plugin. All
 * of them carry the same three marks - lens swap, shutter release, 2x2 toggle - and only place and colour them.
 */
public interface CameraBody {

    /** A held map, which is the only place a viewfinder is carried. */
    int SIDE = Camera.MAP_SIZE;

    /** What config calls it, and what a crafted camera remembers so it opens as itself later. */
    String id();

    Skin skin();

    /** The live picture, square. Kept at the preview's own size where it can be, so nothing is resampled. */
    int windowSide();

    int controlWidth();

    int controlHeight();

    /** The three controls in their places over the plate. Geometry only, so it can be checked without a server. */
    Node frame(Node window, Node swap, Node release, Node tiles);

    /** The shutter release mark. A picture of a control rather than a control - the shutter is a key or a click. */
    void release(Painter painter, Rect bounds);

    /** The drawn body, or null if it could not be read - which is a plain camera rather than no camera. */
    @Nullable
    BufferedImage plate(Plugin plugin);

    static List<CameraBody> all() {
        return List.of(Polaroid.INSTANCE, Digital.INSTANCE);
    }

    /** The body by name, or the first one. Falls back rather than fails: the name may come off an item somebody is holding. */
    static CameraBody byId(@Nullable String id) {
        if (id != null) {
            String wanted = id.trim().toLowerCase(Locale.ROOT);
            for (CameraBody body : all()) {
                if (body.id().equals(wanted)) return body;
            }
        }
        return Polaroid.INSTANCE;
    }

    /** Reads a plate out of the jar. Null if it is missing, which every body draws as its own bare colour instead. */
    @Nullable
    static BufferedImage read(Plugin plugin, String resource) {
        try (InputStream stream = plugin.getResource(resource)) {
            if (stream == null) {
                plugin.getLogger().warning(resource + " is missing from the jar, so that camera body will not be drawn.");
                return null;
            }
            return ImageIO.read(stream);
        } catch (Exception failure) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + resource + ", so that camera body will not be drawn.", failure);
            return null;
        }
    }
}
