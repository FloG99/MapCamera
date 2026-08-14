package de.flog99.mapcamera;

import de.flog99.mapgui.camera.CameraShot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one part of the sequence with a real invariant behind it: the blades must be shut before the flash.
 *
 * <p>The timeline is driven half by a clock and half by an off-thread reply, so a fast reply is the case that
 * breaks it - timing the reveal from the reply rather than from the close flashes through a half-open shutter.
 */
class ShutterTest {

    private static final int SIDE = 128;

    /** Read off the sequence rather than restated, so retuning it does not mean rewriting these. */
    private static final int PRESS = 1000;
    private static final int SHUT = PRESS + Shutter.CLOSE_MS;
    private static final int OPENED = SHUT + Shutter.FLASH_MS + Shutter.OPEN_MS;
    private static final int OVER = OPENED + Shutter.HOLD_MS + 1;

    private static CameraShot shot() {
        return new CameraShot(SIDE, SIDE, new byte[SIDE * SIDE], "26.2");
    }

    @Test
    void bladesCloseOverTheFrame() {
        Shutter shutter = new Shutter();
        shutter.press(PRESS);

        assertTrue(shutter.running());
        assertEquals(0, shutter.coverage(PRESS), 0.001, "open at the moment of the press");
        assertTrue(shutter.coverage(PRESS + Shutter.CLOSE_MS / 2) > 0.3, "part way shut");
        assertEquals(1, shutter.coverage(SHUT), 0.001, "shut once the close is over");
    }

    @Test
    void staysShutUntilThePictureArrives() {
        Shutter shutter = new Shutter();
        shutter.press(PRESS);

        assertEquals(1, shutter.coverage(5000), 0.001, "a slow capture holds the shutter shut");
        assertEquals(0, shutter.flash(5000), 0.001, "and nothing flashes while it waits");
        assertTrue(shutter.tick(5000), "so the sequence is still running");
    }

    @Test
    void aFastCaptureStillWaitsForTheBlades() {
        Shutter shutter = new Shutter();
        shutter.press(PRESS);
        shutter.reveal(shot(), PRESS + 10);

        // The blades keep closing on the clock, or a picture back within a tick or two would slam them shut
        // and the closing animation would never play.
        assertTrue(shutter.coverage(PRESS + 10) < 0.2, "the blades are still closing, picture or no picture");
        assertEquals(1, shutter.coverage(SHUT), 0.001, "and are shut when the close says so, not before");

        assertEquals(0, shutter.flash(PRESS + 10), 0.001, "the flash has not started");
        assertTrue(shutter.flash(SHUT + 1) > 0, "it starts once the blades have met");
    }

    @Test
    void opensOnThePhotographAndThenLetsGo() {
        Shutter shutter = new Shutter();
        shutter.press(PRESS);

        CameraShot taken = shot();
        shutter.reveal(taken, SHUT);
        assertSame(taken, shutter.photo(), "the picture is what is shown, not the live view");

        assertEquals(0, shutter.coverage(OPENED), 0.001, "blades back open");
        assertTrue(shutter.tick(OPENED), "and the photograph is held for a moment");

        assertFalse(shutter.tick(OVER), "then the sequence ends");
        assertFalse(shutter.running());
        assertNull(shutter.photo(), "and the live view has it back");
    }

    @Test
    void aFailedCaptureOpensOnNothing() {
        Shutter shutter = new Shutter();
        shutter.press(PRESS);
        shutter.abort(PRESS + 50);

        assertNull(shutter.photo());
        assertEquals(0, shutter.flash(SHUT), 0.001, "nothing to flash for");
        assertEquals(0, shutter.coverage(OPENED), 0.001, "but the blades still open again");
        assertFalse(shutter.tick(OVER));
    }

    @Test
    void thePreviewIsNotWorthTakingWhileTheBladesAreOver() {
        Shutter shutter = new Shutter();
        assertFalse(shutter.hidesPreview(PRESS), "idle, so the live view runs");

        shutter.press(PRESS);
        assertTrue(shutter.hidesPreview(PRESS + Shutter.CLOSE_MS / 2));

        shutter.reveal(shot(), SHUT);
        assertFalse(shutter.hidesPreview(OPENED), "open again, though the photograph is still up");
    }
}
