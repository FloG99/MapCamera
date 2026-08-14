package de.flog99.mapcamera;

import java.awt.Color;

/**
 * One camera's colours, read off its own item texture so the thing in your hand and the thing on the map match.
 *
 * @param deep     the darkest tone, for an empty frame and the shadow side of a moulding
 * @param dark     the body itself, and what the shutter blades are drawn in
 * @param mid      one step up: the seams between blades, a divider
 * @param hi       the light edge of anything raised, and an unlit control
 * @param accent   the one colour bright enough to mark something as live
 * @param flash    what a flash looks like on this body, which is very nearly white on all of them
 * @param text     the readout over the picture
 * @param liveInk  a mark that is switched on, and {@code idleInk} one that is not. Their own fields because they
 *                 invert between bodies: dark marks read on a cream panel and light ones on a black one
 * @param hoverInk under the cursor. A colour change rather than a shade, which does not read at this size
 */
public record Skin(Color deep, Color dark, Color mid, Color hi, Color accent, Color flash, Color text,
                   Color liveInk, Color idleInk, Color hoverInk) {
}
