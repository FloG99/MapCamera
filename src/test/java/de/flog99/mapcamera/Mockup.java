package de.flog99.mapcamera;

import de.flog99.mapgui.MapColors;
import de.flog99.mapgui.MapImage;
import de.flog99.mapgui.camera.CameraShot;
import de.flog99.mapgui.ui.Node;
import de.flog99.mapgui.ui.PaintContext;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static de.flog99.mapgui.ui.Ui.Draw;
import static de.flog99.mapgui.ui.Ui.Image;
import static de.flog99.mapgui.ui.Ui.Overlay;

/**
 * Renders every camera back to a PNG strip, without a server.
 *
 * <pre>{@code ./gradlew mockup}</pre>
 *
 * <p>Because the interesting part of this screen lasts about a fifth of a second. Looking at the shutter in game
 * means taking a photograph and hoping to catch the two frames where the blades are half shut; here every stage of
 * it is a column, at whatever moment is worth seeing. One row per camera body, so a change to a shared part can be
 * checked against both faces at once.
 *
 * <p>The real layout engine, the real map font and the real 143-colour palette, so what comes out is quantised
 * exactly as the map will be.
 */
public final class Mockup {

    private static final int SCALE = 3;
    private static final int GAP = 8;

    private record Frame(String caption, @Nullable CameraShot picture, double coverage, double flash, @Nullable String status, boolean selfie, int film) {
    }

    public static void main(String[] args) throws Exception {
        CameraShot scene = scene();

        List<Frame> frames = List.of(
                new Frame("cold", null, 0, 0, null, false, 12),
                new Frame("live", scene, 0, 0, null, false, 12),
                new Frame("selfie", scene, 0, 0, null, true, 12),
                new Frame("zoomed", scene, 0, 0, "2x", false, 3),
                new Frame("flash", scene, 1, 0.8, null, false, 3),
                new Frame("opening", scene, 0.4, 0, null, false, 2),
                new Frame("no film", scene, 0, 0, "Out of film", false, 0)
        );

        List<BufferedImage> rows = new ArrayList<>();
        for (CameraBody body : CameraBody.all()) {
            List<BufferedImage> shots = new ArrayList<>();
            for (Frame frame : frames) {
                shots.add(MapImage.scaled(render(body, frame), SCALE));
            }
            rows.add(MapImage.strip(shots, GAP, Color.BLACK));
        }

        Path out = Path.of(args.length > 0 ? args[0] : "build/mockup.png");
        MapImage.write(stack(rows), out);
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    /** One 128x128 map, painted through the same nodes and the same painter the server would use. */
    private static BufferedImage render(CameraBody body, Frame frame) throws Exception {
        Skin skin = body.skin();
        int side = body.windowSide();

        Node tree = Overlay(
                Image(plate(body)).fill().background(skin.dark()),
                body.frame(
                        Draw(context -> Lens.paint(context.painter(), context.bounds(), skin, frame.picture(),
                                frame.coverage(), frame.flash(), frame.status(), frame.film())).size(side, side),
                        mark(body, context -> Lens.flip(context.painter(), context.bounds(),
                                frame.selfie() ? skin.liveInk() : skin.idleInk())),
                        mark(body, context -> body.release(context.painter(), context.bounds())),
                        mark(body, context -> Lens.tiles(context.painter(), context.bounds(), skin.idleInk())))
        );

        return MapImage.of(tree, CameraBody.SIDE, CameraBody.SIDE);
    }

    private static Node mark(CameraBody body, Consumer<PaintContext> paint) {
        return Draw(paint).size(body.controlWidth(), body.controlHeight());
    }

    /** The rows one under another, so both cameras are in one picture. */
    private static BufferedImage stack(List<BufferedImage> rows) {
        int width = rows.stream().mapToInt(BufferedImage::getWidth).max().orElse(1);
        int height = rows.stream().mapToInt(BufferedImage::getHeight).sum() + GAP * (rows.size() - 1);

        BufferedImage all = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D pen = all.createGraphics();
        try {
            int at = 0;
            for (BufferedImage row : rows) {
                pen.drawImage(row, 0, at, null);
                at += row.getHeight() + GAP;
            }
        } finally {
            pen.dispose();
        }
        return all;
    }

    /**
     * A stand-in for a capture: sky over ground with a hill in it.
     *
     * <p>Palette indices rather than colours, which is what a real {@link CameraShot} holds - so this goes
     * through {@code VideoPlayer} by exactly the same path a photograph does.
     */
    private static CameraShot scene() {
        int side = 96;
        byte[] pixels = new byte[side * side];

        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int horizon = 52 + (int) (7 * Math.sin(x / 14.0)) - (x > 60 ? 12 : 0);
                byte colour = y < horizon
                        ? MapColors.INSTANCE.index(new Color(120 + y, 170 + y / 3, 235))
                        : MapColors.INSTANCE.index(new Color(60 + (x % 9) * 3, 110 + (y % 7) * 4, 45));
                pixels[y * side + x] = colour;
            }
        }
        return new CameraShot(side, side, pixels, "26.2");
    }

    /** Straight off the resources rather than through the body, which wants a plugin to log against. */
    private static BufferedImage plate(CameraBody body) throws Exception {
        try (InputStream stream = Mockup.class.getResourceAsStream(body == Polaroid.INSTANCE
                ? "/viewfinder.png" : "/viewfinder_digital.png")) {
            return ImageIO.read(stream);
        }
    }

}
