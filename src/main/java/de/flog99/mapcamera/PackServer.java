package de.flog99.mapcamera;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Hands the bundled resource pack to players, from wherever the pack is being kept.
 *
 * <p>Minecraft only takes a pack from a URL, so the question is never whether to host it, only where. Either
 * somewhere the owner already hosts, named in {@code resource-pack.url}, or here over the HTTP server below.
 *
 * <p>That server answers one path, only to GET, and the path is the pack's own SHA-1 - so it doubles as a cache
 * key. Anything else gets a 404, and no route reads a request path off disk.
 *
 * <p><b>The address is what a server owner has to get right</b>, since the URL is fetched by the <i>client</i>.
 * That and a hosted copy that has gone stale are both checked at startup: the symptom either way is an item that
 * merely looks broken.
 */
public final class PackServer {

    /** Public because MapGUI is handed the same file, so that both routes cannot drift onto different zips. */
    static final String PACK = "pack/MapCamera-resourcepack.zip";

    /** The copy left in the plugin's folder for an owner who hosts it themselves. */
    private static final String EXPORT = "resourcepack.zip";

    /** Long enough for a slow host, short enough that nothing waits on it. Off the main thread regardless. */
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(20);

    private final Plugin plugin;
    private final PackOptions options;

    private HttpServer http;
    private byte[] zip;
    private String sha1;
    private UUID id;

    /** What players are told to fetch, or null if they are told nothing. */
    private String handout;

    public PackServer(Plugin plugin, PackOptions options) {
        this.plugin = plugin;
        this.options = options;
    }

    /** @param url a copy the owner hosts, which beats everything below it. Blank to use the built-in server */
    public record PackOptions(String url, boolean serve, int port, String address, String prompt, boolean required) {

        /** Whether the pack lives somewhere else, which is the one case where no port is opened here. */
        public boolean hosted() {
            return url != null && !url.isBlank();
        }
    }

    /** True if players are being offered the pack at all, by either route. */
    public boolean offering() {
        return handout != null;
    }

    /** The hash this was started with, so a reload can hand it to the replacement rather than measure it again. */
    @Nullable
    public String hash() {
        return sha1;
    }

    /**
     * @param hash the pack's SHA-1 as MapGUI measured it, from {@code useResourcePack}, or null if it did not read
     *             the pack. Taken from there rather than measured again so the file players download and the file
     *             captures are drawn with cannot be different ones
     */
    public void start(@Nullable String hash) {
        try (InputStream stream = plugin.getResource(PACK)) {
            if (stream == null) {
                plugin.getLogger().warning("The resource pack is missing from the jar, so it cannot be handed out.");
                return;
            }
            zip = stream.readAllBytes();
        } catch (Exception failure) {
            plugin.getLogger().log(Level.WARNING, "Could not read the bundled resource pack.", failure);
            return;
        }

        sha1 = hash != null ? hash : HexFormat.of().formatHex(digest(zip));
        // Stable for a given pack, so a client that reconnects is offered the same pack rather than a new one.
        id = UUID.nameUUIDFromBytes(sha1.getBytes(StandardCharsets.UTF_8));

        // Written whichever route is in use, so that deciding to host it somewhere else is never a job of
        // getting a file out of a jar first.
        export();

        if (options.hosted()) {
            handout = options.url();
            plugin.getLogger().info("Players will be sent the resource pack from " + handout + ".");
            checkHosted();
            return;
        }

        if (!options.serve()) {
            plugin.getLogger().info("The resource pack is not being handed out, so the camera and film wear models"
                    + " vanilla already draws. Set resource-pack.url or resource-pack.serve to change that.");
            return;
        }

        try {
            http = HttpServer.create(new InetSocketAddress(options.port()), 0);
            http.createContext("/", this::serve);
            http.start();
        } catch (Exception failure) {
            plugin.getLogger().log(Level.WARNING, "Could not open the resource pack server on port " + options.port()
                    + ", so players get no pack and the camera renders as a missing model. Free that port, set"
                    + " resource-pack.port to another, or host the pack yourself and name it in resource-pack.url.",
                    failure);
            http = null;
            return;
        }

        handout = "http://" + options.address() + ":" + options.port() + "/" + sha1 + ".zip";
        plugin.getLogger().info("Serving the resource pack at " + handout + " (" + zip.length / 1024 + " KB).");

        // Stated rather than warned about. On a test server this address is the right one, and a warning that
        // fires every start of a correct setup is a warning people learn to read past.
        if (unreachable(options.address())) {
            plugin.getLogger().info("That address only resolves on this machine, which is right for a test server"
                    + " and wrong for a public one - players elsewhere would fail to download it. Set"
                    + " resource-pack.address to what players connect to, or resource-pack.url to a copy you host.");
        }
    }

    /**
     * Leaves the pack in the plugin's folder, and says so only when the bytes have changed - which is exactly
     * when somebody hosting their own copy has to upload it again.
     */
    private void export() {
        Path file = plugin.getDataFolder().toPath().resolve(EXPORT);
        try {
            if (Files.isRegularFile(file) && Arrays.equals(Files.readAllBytes(file), zip)) return;

            Files.createDirectories(file.getParent());
            Files.write(file, zip);
            plugin.getLogger().info("Wrote the resource pack to plugins/" + plugin.getName() + "/" + EXPORT
                    + " (SHA-1 " + sha1 + "). Anyone hosting their own copy needs to upload this one.");
        } catch (Exception failure) {
            plugin.getLogger().log(Level.WARNING, "Could not write " + EXPORT + " out of the jar.", failure);
        }
    }

    /**
     * Fetches a hosted copy once at startup to see whether it is still this pack, which catches the quietest
     * failure this plugin has: a pack updated in the jar and never re-uploaded.
     *
     * <p>Only a definite mismatch is a warning. Not being able to reach it proves nothing.
     */
    private void checkHosted() {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try (HttpClient client = HttpClient.newBuilder().connectTimeout(CHECK_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL).build()) {

                HttpRequest request = HttpRequest.newBuilder(URI.create(handout)).timeout(CHECK_TIMEOUT).GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    plugin.getLogger().warning("The pack at " + handout + " answered HTTP " + response.statusCode()
                            + " rather than a file, so no client will be able to download it.");
                    return;
                }

                // One byte past our own length is all it takes to know it is a different file, so a URL pointing
                // at something enormous cannot pull it onto this server.
                try (InputStream body = response.body()) {
                    byte[] served = body.readNBytes(zip.length + 1);
                    if (Arrays.equals(served, zip)) {
                        plugin.getLogger().info("Checked the pack at " + handout + " - it is this one.");
                        return;
                    }

                    plugin.getLogger().warning("The pack at " + handout + " is not the one this version of "
                            + plugin.getName() + " ships (" + served.length + " bytes there against " + zip.length
                            + " here), so every client will reject it and the camera will render as a missing"
                            + " model. Upload plugins/" + plugin.getName() + "/" + EXPORT + " over it.");
                }
            } catch (Exception unreachable) {
                plugin.getLogger().info("Could not check the pack at " + handout + " from here (" + unreachable
                        + "). That may only mean this server cannot reach it - what matters is that players can.");
            }
        });
    }

    /** Whether an address is one that only resolves on the machine it is typed on. */
    private static boolean unreachable(String address) {
        String host = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        return host.isEmpty() || host.equals("localhost") || host.equals("127.0.0.1")
                || host.equals("::1") || host.equals("0.0.0.0");
    }

    public void stop() {
        if (http != null) {
            http.stop(0);
            http = null;
        }
    }

    /** Offered rather than forced unless {@code required}, and never resent to a client that has it. */
    public void offerTo(Player player) {
        if (!offering()) return;

        Component prompt = MiniMessage.miniMessage().deserialize(options.prompt());
        player.setResourcePack(id, handout, sha1, prompt, options.required());
    }

    /** One file at one path, and a 404 for everything else. The path is compared, never used to find anything. */
    private void serve(HttpExchange exchange) {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod()) || !exchange.getRequestURI().getPath().equals("/" + sha1 + ".zip")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, zip.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(zip);
            }
        } catch (Exception ignored) {
            // A client that hung up mid-download is not worth a stack trace, and there is nothing to retry.
        }
    }

    /** Only reached when MapGUI could not read the pack, so there is no hash of its to borrow. */
    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(bytes);
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-1 is required of every JVM", impossible);
        }
    }
}
