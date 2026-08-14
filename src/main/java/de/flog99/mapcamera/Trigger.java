package de.flog99.mapcamera;

import de.flog99.mapgui.Click;
import org.jetbrains.annotations.Nullable;

/**
 * What takes the photograph.
 *
 * <p>Swap hands by default: both cursor axes come off the player's head, so it is the only one that cannot move the
 * camera as it fires. A left-click also swings the arm, which the client starts before the server hears the click.
 */
public enum Trigger {

    SWAP_HANDS("Press F", "press F"),
    LEFT_CLICK("Left-click", "left-click"),
    RIGHT_CLICK("Right-click", "right-click");

    private final String named;
    private final String gesture;

    Trigger(String named, String gesture) {
        this.named = named;
        this.gesture = gesture;
    }

    /** What a caption calls it. A key can only be named by its default bind, since what a player bound it to never reaches the server. */
    public String named() {
        return named;
    }

    /** The same mid-sentence, for lore that has already said what to do with it. */
    public String gesture() {
        return gesture;
    }

    /** Which mouse button presses it, or null when it is not a click at all. */
    @Nullable
    public Click button() {
        return switch (this) {
            case LEFT_CLICK -> Click.LEFT;
            case RIGHT_CLICK -> Click.RIGHT;
            case SWAP_HANDS -> null;
        };
    }
}
