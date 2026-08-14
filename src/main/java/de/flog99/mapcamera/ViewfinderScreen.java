package de.flog99.mapcamera;

import de.flog99.mapgui.Click;
import de.flog99.mapgui.HandOptions;
import de.flog99.mapgui.MapGui;
import de.flog99.mapgui.Screen;
import de.flog99.mapgui.camera.Camera;
import de.flog99.mapgui.camera.CameraAssets;
import de.flog99.mapgui.camera.CameraFeed;
import de.flog99.mapgui.camera.CameraOptions;
import de.flog99.mapgui.camera.CameraShot;
import de.flog99.mapgui.ui.Align;
import de.flog99.mapgui.ui.CustomPaint;
import de.flog99.mapgui.ui.Justify;
import de.flog99.mapgui.ui.Node;
import de.flog99.mapgui.ui.PaintContext;
import de.flog99.mapgui.ui.State;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;

import static de.flog99.mapgui.ui.Ui.Draw;
import static de.flog99.mapgui.ui.Ui.Gap;
import static de.flog99.mapgui.ui.Ui.Image;
import static de.flog99.mapgui.ui.Ui.Overlay;
import static de.flog99.mapgui.ui.Ui.Row;
import static de.flog99.mapgui.ui.Ui.Spinner;

/**
 * The camera's back: a live view of what the lens sees, and the marks under it.
 *
 * <p><b>There is no shutter button on the map, and that is the design.</b> Both cursor axes come off the player's
 * head, so a button here has to be pointed at - and pointing at anything turns the camera at it. The shutter is a
 * key, or a mouse button that ignores where the cursor is; what is left on the map are two toggles, which are safe
 * because re-aiming after one costs nothing.
 */
public final class ViewfinderScreen extends Screen {

    /** How long a passing complaint stays on the frame. */
    private static final int NOTICE_MS = 2500;

    private final MapCameraPlugin plugin;
    private final CameraConfig.Viewfinder view;
    private final Messages messages;

    /** Taken off the item that opened the screen, so it stays right for as long as the screen is open. */
    private final CameraKind camera;

    private final CameraBody body;
    private final Shutter shutter = new Shutter();

    private final State<CameraShot> live = state(null);
    private final State<Boolean> selfie = state(false);
    private final State<Boolean> twoByTwoOn = state(false);
    private final State<Integer> zoom;
    private final State<String> notice = state(null);

    private boolean taking;
    private long noticeUntil;

    /** MapGUI's, opened when the screen opens and closed with it. */
    private CameraFeed feed;

    public ViewfinderScreen(MapCameraPlugin plugin, CameraKind camera) {
        this.plugin = plugin;
        this.camera = camera;
        this.body = camera.body();
        this.view = plugin.config().viewfinder();
        this.messages = plugin.config().messages();
        // Not a field initialiser: those run before the constructor body, and this one needs the config.
        this.zoom = state(view.defaultZoomStep(false));
    }

    @Override
    public Component title() {
        return Component.text("Viewfinder", NamedTextColor.GOLD);
    }

    /** {@code openWhileHolding} carries it in the offhand whatever this says, so this is only for a direct open. */
    @Override
    public HandOptions hand() {
        return HandOptions.offhand().focus(HandOptions.Focus.ALWAYS);
    }

    /**
     * Only while sneaking, so the rest of the time the frame is just the frame.
     *
     * <p>Sneak is already the gesture that reaches the zoom. The shutter is unaffected: it never asks where the
     * cursor is.
     */
    @Override
    public boolean cursor() {
        return sneaking();
    }

    /** The marks are drawn as unreachable without a cursor, so this has to redraw. */
    @Override
    protected void onSneak(boolean sneaking) {
        invalidate();
    }

    @Override
    protected void onOpen() {
        MapGui.get().camera().prepare();
        feed = MapGui.get().camera().feed(player(), this::wanted, live::set);
    }

    @Override
    protected void onClose() {
        feed.close();
    }

    /**
     * What the next preview frame should look like, or null for a tick that does not want one.
     *
     * <p>MapGUI does the pacing, the one-at-a-time and the not-while-put-away. What is left is the states only
     * this screen knows about.
     */
    @Nullable
    private CameraOptions wanted() {
        if (taking || shutter.hidesPreview(System.currentTimeMillis())) return null;
        if (!MapGui.get().camera().assets().ready()) return null;

        return framing(view.preview());
    }

    /** The shutter runs on wall time rather than on the animator, so it asks for its own frames. */
    @Override
    protected boolean keepDrawing() {
        return shutter.tick(System.currentTimeMillis()) || noticeStanding();
    }

    /**
     * The wheel, which MapGUI hands over on <b>sneak+scroll</b>.
     *
     * <p>Plain scroll stays the player's hotbar, or reaching for the zoom would put the camera away. Returning true
     * claims the notch, which is what stops the hotbar selection changing underneath.
     */
    @Override
    protected boolean onScroll(int notches) {
        zoom.set(Math.clamp(zoom.get() - notches, 0, view.maxZoomStep(selfie.get())));
        return true;
    }

    /** Where the lens points and how far it reaches, which the preview and the photograph must agree on. */
    private CameraOptions framing(CameraOptions options) {
        return options.selfie(selfie.get()).fov(view.fovAt(zoom.get(), selfie.get()));
    }

    // ---- the tree ----

    @Override
    protected Node build() {
        // The bare body colour behind the plate, so a missing file is a plain camera rather than no camera.
        return Overlay(
                Image(body.plate(plugin)).fill().background(body.skin().dark()),
                body.frame(
                        window(),
                        lensMark(),
                        releaseMark(),
                        gridMark())
        );
    }

    /** The window, with a spinner over it while the textures are still coming down. */
    private Node window() {
        int side = body.windowSide();
        CustomPaint window = Draw(this::paintWindow).size(side, side);
        if (!(MapGui.get().camera().assets() instanceof CameraAssets.Loading)) return window;

        return Overlay(window, Row(Spinner().color(body.skin().hi()))
                .justify(Justify.CENTER).align(Align.CENTER).fill())
                .size(side, side);
    }

    /** The lens swap: the two arrows a phone puts on the same control, with no plate under them. */
    private Node lensMark() {
        boolean on = selfie.get();
        CustomPaint mark = Draw(context -> Lens.flip(context.painter(), context.bounds(), ink(on, context.hovered())))
                .size(body.controlWidth(), body.controlHeight());

        // Nothing to hover and nothing to press without a cursor, so neither is offered.
        if (!sneaking()) return mark;

        return mark.caption(on ? messages.lensForward() : messages.lensAround())
                .onClick(() -> turnLens(!on));
    }

    /**
     * The 2x2 mode: four maps in a square instead of one, at four times the pixels and four times the film.
     *
     * <p>An empty node where the server has not turned it on, which keeps the spacing without leaving a control
     * that does nothing.
     */
    private Node gridMark() {
        if (!camera.twoByTwo()) return Gap(body.controlWidth(), body.controlHeight());

        boolean on = twoByTwoOn.get();
        CustomPaint mark = Draw(context -> Lens.tiles(context.painter(), context.bounds(), ink(on, context.hovered())))
                .size(body.controlWidth(), body.controlHeight());

        if (!sneaking()) return mark;

        boolean film = camera.filmRequired();
        return mark.caption(on ? messages.mode1x1(film) : messages.mode2x2(film))
                .onClick(() -> twoByTwoOn.set(!on));
    }

    /**
     * The shutter release, which is a picture of a control rather than a control.
     *
     * <p>It carries a caption anyway, which is what makes a node hoverable, so the cursor can be asked which key
     * it means.
     */
    private Node releaseMark() {
        return Draw(context -> body.release(context.painter(), context.bounds()))
                .caption(messages.shutterHint())
                .size(body.controlWidth(), body.controlHeight());
    }

    /** Either button works the marks, so a player need not find out which one this screen wanted. */
    @Override
    public Click activateOn() {
        return Click.BOTH;
    }

    /**
     * The shutter, where a server has put it on a mouse button rather than on the key.
     *
     * <p>Sneak decides: sneaking, the click works the marks; not sneaking, it is the shutter. The marks are only
     * live while sneaking anyway, so nothing is lost.
     */
    @Override
    protected boolean clickedAnywhere(int x, int y, Click with) {
        Click button = view.trigger().button();
        if (button == null || with != button || sneaking()) return false;

        press();
        return true;
    }

    /** MapGUI hands the key over because an offhand map has nothing to swap. */
    @Override
    protected void onSwapHands() {
        if (view.trigger() == Trigger.SWAP_HANDS) {
            press();
        }
    }

    /**
     * Turns the lens round, and puts the zoom back to 1.
     *
     * <p>The two ladders do not line up step for step, so carrying the index across would land on whatever
     * happened to be at that position.
     */
    private void turnLens(boolean on) {
        selfie.set(on);
        zoom.set(view.defaultZoomStep(on));
    }

    /** Lit when its mode is on, dim when it is not, and something else again under the cursor - which way round is the body's. */
    private Color ink(boolean on, boolean hovered) {
        Skin skin = body.skin();
        if (hovered) return skin.hoverInk();

        return on ? skin.liveInk() : skin.idleInk();
    }

    // ---- the window ----

    private void paintWindow(PaintContext context) {
        long now = System.currentTimeMillis();
        CameraShot frame = shutter.photo() != null ? shutter.photo() : live.get();

        Lens.paint(context.painter(), context.bounds(), body.skin(), frame, shutter.coverage(now), shutter.flash(now),
                readout(), plugin.items().filmLeft(player(), camera));
    }

    /**
     * The one line of text on the camera, or nothing at all.
     *
     * <p>Nothing is the ordinary answer: it speaks up only for the states a player cannot work out by looking. No
     * wind-on line either - vanilla sweeps the item in the hotbar for that.
     */
    @Nullable
    private String readout() {
        if (notice.get() != null) return notice.get();

        return switch (MapGui.get().camera().assets()) {
            // No percentage: the spinner over the window is already saying this, and saying it better.
            case CameraAssets.Loading ignored -> messages.loadingTextures();
            case CameraAssets.Unavailable ignored -> player().hasPermission("mapcamera.admin") ? "/mapcamera debug" : messages.noTextures();
            case CameraAssets.Ready ignored -> {
                if (plugin.items().filmLeft(player(), camera) == 0) yield messages.outOfFilm();

                double factor = view.zoomFactor(zoom.get(), selfie.get());
                yield factor == 1 ? null : messages.zoom(trim(factor));
            }
        };
    }

    /** Maps to a side this shot will come out as: two in 2x2 mode, one otherwise. */
    private int across() {
        return camera.twoByTwo() && twoByTwoOn.get() ? Photos.GRID_ACROSS : 1;
    }

    /** "1.5x" keeps its half and "2x" does not carry a pointless ".0". */
    private static String trim(double factor) {
        return factor == Math.rint(factor) ? String.valueOf((int) factor) : String.valueOf(factor);
    }

    // ---- the shutter ----

    /** A message that clears itself, so a one-off complaint does not sit over the frame for ever. */
    private void notice(String text) {
        notice.set(text);
        noticeUntil = System.currentTimeMillis() + NOTICE_MS;
    }

    /** Clearing it here is what draws the frame it disappears on. */
    private boolean noticeStanding() {
        if (notice.get() == null) return false;
        if (System.currentTimeMillis() <= noticeUntil) return true;

        notice.set(null);
        return false;
    }

    /**
     * Press the shutter.
     *
     * <p>The blades start closing before the capture is asked for, so the camera answers on the frame the key was
     * pressed rather than two ticks later. Film is only checked here, not taken - it is spent when the photograph
     * exists, so a capture that fails owes no refund.
     */
    private void press() {
        Player player = player();
        Shooting shooting = plugin.shooting();

        if (taking || shutter.running()) return;
        if (!MapGui.get().camera().assets().ready()) {
            MapGui.get().camera().prepare();
            notice(messages.noTextures());
            return;
        }
        if (shooting.windOnLeft(player, camera) > 0) return;

        int sheets = Photos.filmFor(across());
        int left = plugin.items().filmLeft(player, camera);
        if (left >= 0 && left < sheets) {
            notice(messages.needFilm(sheets));
            return;
        }

        taking = true;
        notice.set(null);
        shooting.startWindOn(player, camera);
        shutter.press(System.currentTimeMillis());
        shooting.snap(player);

        CameraOptions shot = framing(view.shot()).size(across() * Camera.MAP_SIZE);
        MapGui.get().camera().capture(player, shot, taken -> {
            taking = false;

            if (taken == null) {
                notice(messages.exposureFailed());
                shooting.clearWindOn(player, camera);
                shutter.abort(System.currentTimeMillis());
                return;
            }

            shutter.reveal(taken, System.currentTimeMillis());
            shooting.develop(player, camera, taken);
        });
    }
}
