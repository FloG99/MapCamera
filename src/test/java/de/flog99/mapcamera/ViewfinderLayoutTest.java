package de.flog99.mapcamera;

import de.flog99.mapgui.MapTextFont;
import de.flog99.mapgui.ui.LayoutContext;
import de.flog99.mapgui.ui.Node;
import de.flog99.mapgui.ui.Rect;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static de.flog99.mapgui.ui.Ui.Box;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The widgets and the body they sit in are drawn by two different things - a layout engine here and a Python
 * script there - and nothing but these numbers makes them agree.
 *
 * <p>So each body is arranged for real and its rects are checked against the holes its plate actually has in it. A
 * pixel out is invisible in the code and obvious on the map.
 */
class ViewfinderLayoutTest {

    private static final LayoutContext CONTEXT = new LayoutContext(MapTextFont.INSTANCE);

    private static Node arranged(CameraBody body) {
        int side = body.windowSide();
        Node frame = body.frame(
                Box(Color.BLACK).size(side, side),
                Box(Color.GRAY).size(body.controlWidth(), body.controlHeight()),
                Box(Color.GRAY).size(body.controlWidth(), body.controlHeight()),
                Box(Color.GRAY).size(body.controlWidth(), body.controlHeight()));

        frame.measure(CONTEXT, CameraBody.SIDE, CameraBody.SIDE);
        frame.arrange(CONTEXT, new Rect(0, 0, CameraBody.SIDE, CameraBody.SIDE));
        return frame;
    }

    private static Node windowOf(Node frame) {
        return frame.children().getFirst();
    }

    private static Node controlsOf(Node frame) {
        return frame.children().getLast();
    }

    @Test
    void thePolaroidWindowSitsExactlyInItsBezel() {
        Rect window = windowOf(arranged(Polaroid.INSTANCE)).bounds();

        assertEquals(Polaroid.WINDOW_X, window.x(), "left edge of the viewfinder");
        assertEquals(Polaroid.WINDOW_Y, window.y(), "top edge of the viewfinder");
        assertEquals(Polaroid.WINDOW, window.width());
        assertEquals(Polaroid.WINDOW, window.height());
    }

    @Test
    void theDigitalScreenSitsExactlyInItsSurround() {
        Rect window = windowOf(arranged(Digital.INSTANCE)).bounds();

        assertEquals(Digital.WINDOW_X, window.x());
        assertEquals(Digital.WINDOW_Y, window.y());
        assertEquals(Digital.WINDOW, window.width());
        assertEquals(Digital.WINDOW, window.height());
    }

    /** The polaroid puts them in a bar: swap left, release centred on the map, big mode right. */
    @Test
    void thePolaroidBarRunsLeftToRight() {
        Node bar = controlsOf(arranged(Polaroid.INSTANCE));
        Rect swap = bar.children().get(0).bounds();
        Rect release = bar.children().get(1).bounds();
        Rect tiles = bar.children().get(2).bounds();

        assertEquals(Polaroid.BUTTON_MARGIN, swap.x(), "the swap tucks into the left corner");
        assertEquals(CameraBody.SIDE / 2, release.x() + release.width() / 2, "the release is centred on the map");
        assertEquals(CameraBody.SIDE - Polaroid.BUTTON_MARGIN, tiles.x() + tiles.width(),
                "and the big-mode mark tucks into the right corner");
    }

    /** All three controls in the column beside the screen, spread over its height, on the strip and not over it. */
    @Test
    void theDigitalControlsRunDownTheStrip() {
        Node column = controlsOf(arranged(Digital.INSTANCE));
        Rect swap = column.children().get(0).bounds();
        Rect release = column.children().get(2).bounds();
        Rect tiles = column.children().get(4).bounds();

        assertTrue(swap.x() >= Digital.STRIP_X, "the marks sit on the strip, not over the picture");
        assertEquals(Digital.WINDOW_Y, swap.y(), "the first is level with the top of the screen");
        assertEquals(Digital.WINDOW_Y + Digital.WINDOW, tiles.bottom(), "and the last with the bottom of it");
        assertTrue(release.y() > swap.bottom() && release.bottom() < tiles.y(), "the release between them");
    }

    /** Whatever the shape of a body's tree, nothing it lays out may run off the map. */
    @Test
    void nothingRunsOffTheMap() {
        for (CameraBody body : CameraBody.all()) {
            everythingInside(arranged(body), body.id());
        }
    }

    private static void everythingInside(Node node, String body) {
        Rect at = node.bounds();
        assertTrue(at.x() >= 0 && at.y() >= 0 && at.right() <= CameraBody.SIDE && at.bottom() <= CameraBody.SIDE,
                body + " lays something out at " + at);

        node.children().forEach(child -> everythingInside(child, body));
    }

    /** The plates are build artifacts of a script, so a jar carrying the wrong one is worth catching here. */
    @Test
    void everyBodyPlateIsOneMapSquare() throws Exception {
        for (String plate : new String[]{"/viewfinder.png", "/viewfinder_digital.png"}) {
            try (InputStream stream = ViewfinderLayoutTest.class.getResourceAsStream(plate)) {
                assertNotNull(stream, plate + " is missing from the resources");

                BufferedImage body = ImageIO.read(stream);
                assertEquals(CameraBody.SIDE, body.getWidth(), plate);
                assertEquals(CameraBody.SIDE, body.getHeight(), plate);
            }
        }
    }
}
