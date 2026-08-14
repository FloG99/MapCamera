package de.flog99.mapcamera;

import de.flog99.mapgui.camera.CameraShot;
import org.jetbrains.annotations.Nullable;

/**
 * The snap: blades close, the frame flashes, and they open again on the photograph that was taken.
 *
 * <p>A timeline rather than eased values, because the middle of it waits on something that is not a clock. The
 * blades start closing on the press so the camera answers immediately, but the capture runs off-thread. So the
 * closed phase has no fixed length: it lasts until the picture arrives, and only then is the rest of it timed.
 */
public final class Shutter {

    static final int CLOSE_MS = 330;
    static final int FLASH_MS = 135;
    static final int OPEN_MS = 390;
    static final int HOLD_MS = 900;

    private static final long IDLE = 0;

    private long startedAt = IDLE;
    private long revealAt = IDLE;
    private CameraShot photo;

    /** Blades start closing now. The picture is not here yet and is not waited for. */
    public void press(long now) {
        startedAt = now;
        revealAt = IDLE;
        photo = null;
    }

    /**
     * The picture arrived, so the rest of the sequence can be timed from here.
     *
     * <p>Never before the blades have finished closing, or a capture that came back in one tick would flash
     * over a half-shut shutter.
     */
    public void reveal(CameraShot taken, long now) {
        if (startedAt == IDLE) return;

        photo = taken;
        revealAt = Math.max(now, startedAt + CLOSE_MS);
    }

    /** The capture failed. The blades open on the live view again with nothing held. */
    public void abort(long now) {
        if (startedAt == IDLE) return;

        photo = null;
        revealAt = Math.max(now, startedAt + CLOSE_MS);
    }

    public boolean running() {
        return startedAt != IDLE;
    }

    /** Whether the live preview is worth capturing right now, which it is not while the blades are over it. */
    public boolean hidesPreview(long now) {
        return running() && coverage(now) > 0;
    }

    /** Drives the clock and says whether anything still has to be drawn, which is what {@code keepDrawing} asks. */
    public boolean tick(long now) {
        if (startedAt == IDLE) return false;

        if (revealAt != IDLE && now >= revealAt + FLASH_MS + OPEN_MS + HOLD_MS) {
            startedAt = IDLE;
            revealAt = IDLE;
            photo = null;
            return false;
        }
        return true;
    }

    /** The picture to show under the blades, or null to leave the live view there. */
    @Nullable
    public CameraShot photo() {
        return photo;
    }

    /** How much of the frame the blades cover, 0 open to 1 shut. */
    public double coverage(long now) {
        if (startedAt == IDLE) return 0;

        // The close runs on the clock whether or not the picture is back, or a capture that returns within a
        // tick or two would slam the blades shut and skip the animation.
        if (now < startedAt + CLOSE_MS) {
            return Math.max(0, (now - startedAt) / (double) CLOSE_MS);
        }
        if (revealAt == IDLE) return 1;

        long since = now - revealAt;
        if (since < FLASH_MS) return 1;

        return Math.max(0, 1 - (since - FLASH_MS) / (double) OPEN_MS);
    }

    /** How strong the flash is over the frame, 0 to 1. Only ever while the blades are shut. */
    public double flash(long now) {
        if (startedAt == IDLE || revealAt == IDLE || photo == null) return 0;

        long since = now - revealAt;
        if (since < 0 || since >= FLASH_MS) return 0;

        return 1 - since / (double) FLASH_MS;
    }
}
