package de.flog99.mapcamera;

import de.flog99.mapgui.ui.Align;
import de.flog99.mapgui.ui.Insets;
import de.flog99.mapgui.ui.Justify;
import de.flog99.mapgui.ui.Node;
import de.flog99.mapgui.ui.Painter;
import de.flog99.mapgui.ui.Rect;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static de.flog99.mapgui.ui.Ui.Column;
import static de.flog99.mapgui.ui.Ui.Overlay;
import static de.flog99.mapgui.ui.Ui.Spacer;

/**
 * The instant camera: a navy plastic body with a cream front panel, and the controls in a bar along the bottom.
 *
 * <p>The numbers here are the same ones {@code tools/art.py} draws with, and they have to stay that way - the window
 * it cuts out is where the picture is placed over it. Change one and re-run the script.
 */
public final class Polaroid implements CameraBody {

    public static final Polaroid INSTANCE = new Polaroid();

    public static final int WINDOW_X = 16;
    public static final int WINDOW_Y = 8;
    public static final int WINDOW = 96;

    public static final int BUTTON_TOP = 109;
    public static final int BUTTON_HEIGHT = 16;

    /** Same as {@link #WINDOW_X}, so the bar lines up with the window rather than floating wider than it. */
    public static final int BUTTON_MARGIN = WINDOW_X;

    /** How much room each mark on the bar is given. None has a plate under it - at this size that is mostly plate. */
    public static final int CONTROL_WIDTH = 14;
    public static final int CONTROL_HEIGHT = 14;

    /** Read off the camera model's own texture, so the thing in your hand and the thing on the map match. */
    private static final Color DEEP = new Color(15, 18, 28);
    private static final Color DARK = new Color(29, 32, 45);
    private static final Color MID = new Color(46, 50, 66);
    private static final Color HI = new Color(131, 135, 148);

    /** The cream front panel, which is what the marks on the bar sit against. */
    public static final Color CREAM = new Color(222, 213, 191);

    /** The one accent bright enough to mark something as live on cream. */
    private static final Color ACCENT = new Color(91, 188, 244);

    private static final Color FLASH = new Color(255, 253, 247);

    private static final Color TEXT = new Color(239, 234, 221);

    /** The release, in three reds so it reads as domed rather than as a flat dot. */
    private static final Color RELEASE = new Color(214, 84, 84);
    private static final Color RELEASE_RIM = new Color(138, 44, 44);
    private static final Color RELEASE_LIT = new Color(244, 134, 134);

    /** Dark for a live mark and grey for an idle one: these sit on cream, so lit is the dark one. */
    private static final Skin SKIN = new Skin(DEEP, DARK, MID, HI, ACCENT, FLASH, TEXT, DARK, HI, ACCENT);

    private BufferedImage plate;

    private Polaroid() {
    }

    @Override
    public String id() {
        return "polaroid";
    }

    @Override
    public Skin skin() {
        return SKIN;
    }

    @Override
    public int windowSide() {
        return WINDOW;
    }

    @Override
    public int controlWidth() {
        return CONTROL_WIDTH;
    }

    @Override
    public int controlHeight() {
        return CONTROL_HEIGHT;
    }

    /**
     * The window with the bar under it. The window has to land exactly in the hole the bezel leaves it, which is
     * what {@code ViewfinderLayoutTest} checks.
     *
     * @param window  a node already sized {@link #WINDOW} square
     * @param swap    the lens-swap mark, off at the left
     * @param release the shutter release, in the middle of the bar the way a camera puts its own
     * @param tiles   the 2x2 toggle, off at the right where it balances the swap
     */
    @Override
    public Node frame(Node window, Node swap, Node release, Node tiles) {
        return Column(
                window,
                Spacer(),
                // Overlaid rather than laid out in a row: the release is centred on the whole bar, which a row
                // cannot do while something else sits at either end of it.
                Overlay(
                        swap.place(Justify.START, Align.CENTER),
                        release.place(Justify.CENTER, Align.CENTER),
                        tiles.place(Justify.END, Align.CENTER)
                ).height(BUTTON_HEIGHT).fillWidth()
        ).padding(new Insets(WINDOW_Y, BUTTON_MARGIN, SIDE - BUTTON_TOP - BUTTON_HEIGHT, BUTTON_MARGIN))
                .align(Align.CENTER)
                .fill();
    }

    /** A red button in the middle of the body, the way a camera wears its own, and with no frame around it. */
    @Override
    public void release(Painter painter, Rect bounds) {
        int centreX = bounds.x() + bounds.width() / 2;
        int centreY = bounds.y() + bounds.height() / 2;

        // On the cream panel, so the rim is what separates it from the panel rather than from the body.
        painter.circle(centreX, centreY, 6, DEEP, null);
        painter.circle(centreX, centreY, 5, RELEASE_RIM, null);
        painter.circle(centreX, centreY, 4, RELEASE, null);
        // Off centre, up and to the left, which is where the light comes from everywhere else on the body.
        painter.circle(centreX - 1, centreY - 2, 1, RELEASE_LIT, null);
    }

    @Override
    @Nullable
    public BufferedImage plate(Plugin plugin) {
        if (plate == null) {
            plate = CameraBody.read(plugin, "viewfinder.png");
        }
        return plate;
    }
}
