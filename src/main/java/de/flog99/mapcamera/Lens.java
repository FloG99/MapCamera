package de.flog99.mapcamera;

import de.flog99.mapgui.camera.CameraShot;
import de.flog99.mapgui.media.Picture;
import de.flog99.mapgui.media.VideoPlayer;
import de.flog99.mapgui.ui.Border;
import de.flog99.mapgui.ui.Colors;
import de.flog99.mapgui.ui.Fill;
import de.flog99.mapgui.ui.Painter;
import de.flog99.mapgui.ui.Rect;
import de.flog99.mapgui.ui.Shape;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;

/**
 * Everything inside the bezel, painted bottom to top.
 *
 * <p>Needs no player, session or server: hand it a picture and four numbers and it draws the frame. Which is what
 * lets {@code Mockup} render the whole sequence to a PNG without a game running.
 */
public final class Lens {

    /** The swap arrows: one is ten by five, and the pair spans eleven with a row of daylight between them. */
    private static final int ARROW_WIDTH = 10;
    private static final int ARROW_SPAN = 11;

    /** The frame counter's mark: one blank print. White card, dark exposure, and nothing else. */
    private static final int CARD_WIDTH = 7;
    private static final int CARD_HEIGHT = 8;
    private static final Color CARD = new Color(252, 252, 242);
    private static final Color EXPOSURE = new Color(31, 32, 37);

    /** Clear of the bezel, and clear of each other: the readout starts at the left, the counter ends at the right. */
    private static final int INSET = 3;

    private Lens() {
    }

    /**
     * @param frame    the picture to show, or null for a dark frame
     * @param coverage how much of the frame the blades cover, 0 to 1
     * @param flash    how strong the flash is, 0 to 1
     * @param status   the readout line, or null for none
     * @param filmLeft how many shots are left, or negative when film is not charged for
     */
    public static void paint(Painter painter, Rect bounds, Skin skin, @Nullable CameraShot frame, double coverage, double flash, @Nullable String status, int filmLeft) {
        if (frame != null) {
            Picture.paint(painter, bounds, frame, VideoPlayer.Fit.COVER);
        } else {
            painter.fill(bounds, skin.deep());
        }

        blades(painter, bounds, skin, coverage);

        if (flash > 0) {
            painter.fill(bounds, Colors.alpha(skin.flash(), (int) Math.round(flash * 235)));
        }

        // Last, so the two things the player has to be able to read are never under a blade.
        if (status != null) {
            readout(painter, bounds, skin, status);
        }
        if (filmLeft >= 0) {
            frameCounter(painter, bounds, skin, filmLeft);
        }
    }

    /** Blades in the iris. Eight, so the opening is an octagon. */
    private static final int BLADES = 8;

    /** How far the set turns as it closes. Half a blade, which is the whole sweep of a real iris. */
    private static final double SWEEP_DEGREES = 180.0 / BLADES;

    /**
     * The iris: an octagonal opening with the blades filling everything around it.
     *
     * <p>Which is exactly {@code holeIn}, so the blades need no drawing of their own.
     */
    private static void blades(Painter painter, Rect bounds, Skin skin, double coverage) {
        if (coverage <= 0) return;

        // Measured to the corners, so at nothing-closed even the window's own corners are inside the opening and
        // no blade shows until they start to move.
        double reach = Math.hypot(bounds.width(), bounds.height()) / 2.0
                / Math.cos(Math.PI / BLADES) * (1 - coverage);

        Shape.Polygon opening = Shape.regularPolygon(
                bounds.x() + bounds.width() / 2.0,
                bounds.y() + bounds.height() / 2.0,
                reach, BLADES, SWEEP_DEGREES * coverage);

        // Trimmed to the window first, which keeps a wide-open iris from being tested over its own huge bounds.
        Shape aperture = opening.intersectionWith(Shape.of(bounds));
        painter.shape(aperture.holeIn(bounds), Fill.solid(skin.dark()), null);

        // The lit edge as eight lines along the opening's own sides. Outlining the blade shape instead would test
        // every pixel of the window a second time for a rim of about 300. Skipped once the opening is under a
        // pixel across, where all eight corners round together into a lit dot in a shut shutter.
        if (reach >= 1) {
            painter.polygon(skin.hi(), rounded(opening.xs()), rounded(opening.ys()));
        }

        seams(painter, bounds, skin, opening);
    }

    private static int[] rounded(double[] values) {
        int[] pixels = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            pixels[i] = (int) Math.round(values[i]);
        }
        return pixels;
    }

    /**
     * Each blade's edge, carried on past the corner it ends at.
     *
     * <p>The seam between two leaves is <b>the edge of a leaf</b> extended, not a line from the centre - which is
     * what gives an iris its pinwheel rather than a wheel of pie slices.
     */
    private static void seams(Painter painter, Rect bounds, Skin skin, Shape.Polygon opening) {
        double away = Math.hypot(bounds.width(), bounds.height());
        double[] xs = opening.xs();
        double[] ys = opening.ys();

        for (int i = 0; i < xs.length; i++) {
            int next = (i + 1) % xs.length;
            double dx = xs[next] - xs[i];
            double dy = ys[next] - ys[i];
            double length = Math.hypot(dx, dy);
            if (length <= 0) continue;

            painter.line(xs[next], ys[next], xs[next] + dx / length * away, ys[next] + dy / length * away, skin.mid());
        }
    }

    /**
     * The two arrows a phone puts on its own lens-swap button, one pointing each way.
     *
     * <p>A pixel at a time rather than filled triangles: a triangle small enough to fit here is three pixels of
     * base and reads as a blob, where a chevron of single pixels stays sharp.
     */
    public static void flip(Painter painter, Rect bounds, Color ink) {
        int x = bounds.x() + (bounds.width() - ARROW_WIDTH) / 2;
        // Rounded down rather than up, which sits the pair a pixel lower and centres it by eye.
        int y = bounds.y() + (bounds.height() - ARROW_SPAN + 1) / 2;

        arrow(painter, x, y, true, ink);
        arrow(painter, x, y + ARROW_SPAN - 5, false, ink);
    }

    /** One arrow, ten wide and five tall, with its tip on the end it points at. */
    private static void arrow(Painter painter, int x, int y, boolean rightwards, Color ink) {
        painter.line(x, y + 2, x + ARROW_WIDTH - 1, y + 2, ink);

        int tip = rightwards ? x + ARROW_WIDTH - 1 : x;
        int step = rightwards ? -1 : 1;

        painter.pixel(tip + step, y + 1, ink);
        painter.pixel(tip + step, y + 3, ink);
        painter.pixel(tip + step * 2, y, ink);
        painter.pixel(tip + step * 2, y + 4, ink);
    }

    /**
     * The 2x2 mark: four little frames in a square, which is what it makes.
     *
     * <p>Two pixels of gap between the panes - at this size one pixel reads as a smudge rather than as a gap.
     */
    public static void tiles(Painter painter, Rect bounds, Color ink) {
        int x = bounds.x() + (bounds.width() - TILE_SPAN) / 2;
        int y = bounds.y() + (bounds.height() - TILE_SPAN) / 2;

        for (int down = 0; down < 2; down++) {
            for (int across = 0; across < 2; across++) {
                painter.fill(new Rect(x + across * (TILE + TILE_GAP), y + down * (TILE + TILE_GAP), TILE, TILE), ink);
            }
        }
    }

    /** One pane, the gap between two, and what the pair comes to across. */
    private static final int TILE = 5;

    private static final int TILE_GAP = 2;

    private static final int TILE_SPAN = TILE * 2 + TILE_GAP;

    /** Shadowed rather than sat on a panel, which is how the game's own HUD stays legible over anything. */
    private static void readout(Painter painter, Rect bounds, Skin skin, String status) {
        painter.textLine(bounds.x() + INSET, bounds.y() + INSET, status, skin.text(), true);
    }

    /**
     * How many shots are left, top right, as a print and a number.
     *
     * <p>A picture of what it counts rather than "11 left", which would grow a word wider in every language.
     */
    private static void frameCounter(Painter painter, Rect bounds, Skin skin, int left) {
        String count = String.valueOf(left);
        int x = bounds.right() - INSET - CARD_WIDTH - 2 - painter.font().widthOf(count);
        int y = bounds.y() + INSET;

        filmIcon(painter, x, y);
        painter.textLine(x + CARD_WIDTH + 2, y, count, skin.text(), true);
    }

    /**
     * A blank print: a cream card with the dark exposure area on it, and the wide band along the bottom that is
     * what makes the shape recognisable at seven pixels.
     */
    private static void filmIcon(Painter painter, int x, int y) {
        painter.fill(new Rect(x, y, CARD_WIDTH, CARD_HEIGHT), CARD);

        // One pixel of card at the top and sides, three at the bottom.
        painter.fill(new Rect(x + 1, y + 1, CARD_WIDTH - 2, CARD_HEIGHT - 4), EXPOSURE);
    }
}
