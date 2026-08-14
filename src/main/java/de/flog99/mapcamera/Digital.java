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
import static de.flog99.mapgui.ui.Ui.Row;
import static de.flog99.mapgui.ui.Ui.Spacer;

/**
 * The digital camera: this map <b>is</b> the camera's screen rather than a picture of the camera, so nothing of the
 * body is drawn here. A machined surround, the live view, and the three marks in a column down the right.
 *
 * <p>Marks sit on black, so a lit one is the bright one - the reverse of {@link Polaroid}, whose panel is cream.
 *
 * <p>The numbers here are the same ones {@code tools/art.py} draws the bezel with. Change one and re-run the script.
 */
public final class Digital implements CameraBody {

    public static final Digital INSTANCE = new Digital();

    /** The live view, at the preview's own size so a frame is drawn one pixel per pixel and nothing is resampled. */
    public static final int WINDOW = 96;

    public static final int WINDOW_X = 9;
    public static final int WINDOW_Y = 16;

    /** The column the three controls sit in, down the right where a digital camera keeps its buttons. */
    public static final int STRIP_X = WINDOW_X + WINDOW + 2;
    public static final int STRIP_WIDTH = SIDE - STRIP_X - 3;

    public static final int CONTROL_WIDTH = 14;
    public static final int CONTROL_HEIGHT = 14;

    /** Black leatherette and cool metal, read off the item texture so the thing in your hand and the map match. */
    private static final Color DEEP = new Color(10, 10, 12);
    private static final Color DARK = new Color(30, 27, 26);
    private static final Color MID = new Color(44, 40, 39);

    public static final Color SILVER = new Color(168, 172, 178);
    public static final Color SILVER_LIT = new Color(203, 207, 213);
    public static final Color SILVER_DARK = new Color(112, 117, 124);

    /** A warm amber, deliberately nothing like the red of the release - both are fourteen pixels across. */
    public static final Color ACCENT = new Color(228, 178, 82);

    private static final Color FLASH = new Color(255, 255, 255);
    private static final Color TEXT = new Color(232, 232, 228);

    /** The release, in three reds so it reads as domed. Deeper than the polaroid's, to suit a dark body. */
    private static final Color RELEASE = new Color(206, 62, 58);
    private static final Color RELEASE_RIM = new Color(108, 26, 24);
    private static final Color RELEASE_LIT = new Color(255, 140, 136);

    /** A mark that is off: on black that is a dim cool grey. */
    private static final Color IDLE = new Color(108, 114, 124);

    /** On glows amber, off is grey, and the cursor goes to white rather than to the accent, which already means "on". */
    private static final Skin SKIN = new Skin(DEEP, DARK, MID, SILVER, ACCENT, FLASH, TEXT, ACCENT, IDLE, TEXT);

    private BufferedImage plate;

    private Digital() {
    }

    @Override
    public String id() {
        return "digital";
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
     * The picture, with all three marks in a column beside it. Together on purpose: a red dot alone on a camera
     * reads as a recording light, and beside two toggles it reads as the third button.
     *
     * @param window a node already sized {@link #WINDOW} square
     */
    @Override
    public Node frame(Node window, Node swap, Node release, Node tiles) {
        return Row(
                window,
                Spacer(),
                Column(swap, Spacer(), release, Spacer(), tiles)
                        .width(STRIP_WIDTH)
                        .align(Align.CENTER)
                        .fillHeight()
                // Top, right, bottom, left.
        ).padding(new Insets(WINDOW_Y, SIDE - STRIP_X - STRIP_WIDTH, SIDE - WINDOW_Y - WINDOW, WINDOW_X))
                .justify(Justify.START)
                .fill();
    }

    /**
     * The shutter release: a domed red button in a machined socket.
     *
     * <p>The socket is what makes it read as a button rather than as a lamp. Its highlight is up and to the left,
     * where the light is on everything else here.
     */
    @Override
    public void release(Painter painter, Rect bounds) {
        int centreX = bounds.x() + bounds.width() / 2;
        int centreY = bounds.y() + bounds.height() / 2;

        painter.circle(centreX, centreY, 6, MID, SILVER_DARK);
        painter.circle(centreX, centreY, 5, DEEP, null);
        painter.circle(centreX, centreY, 4, RELEASE_RIM, null);
        painter.circle(centreX, centreY, 3, RELEASE, null);
        painter.circle(centreX - 1, centreY - 1, 1, RELEASE_LIT, null);
    }

    @Override
    @Nullable
    public BufferedImage plate(Plugin plugin) {
        if (plate == null) {
            plate = CameraBody.read(plugin, "viewfinder_digital.png");
        }
        return plate;
    }
}
