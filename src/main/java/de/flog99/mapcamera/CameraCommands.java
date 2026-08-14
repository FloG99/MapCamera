package de.flog99.mapcamera;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * {@code /mapcamera}: hand out cameras and film, reload, and see what captures are costing.
 *
 * <p>The tree is built once at startup from the cameras that are switched on, so a reload that changes them needs a
 * restart to change the branches. Everything a branch does is looked up when it runs, so the behaviour is current.
 */
final class CameraCommands {

    /** A camera named this in config would collide with the film branch. */
    private static final String FILM = "film";

    private final MapCameraPlugin plugin;

    private CameraCommands(MapCameraPlugin plugin) {
        this.plugin = plugin;
    }

    static void register(MapCameraPlugin plugin) {
        CameraCommands commands = new CameraCommands(plugin);

        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("mapcamera")
                        .requires(source -> source.getSender().hasPermission("mapcamera.admin"))
                        .then(commands.give())
                        .then(Commands.literal("reload").executes(commands::reload))
                        // Ours rather than MapGUI's, so a server running MapGUI for this plugin alone can turn its
                        // whole /mapgui tree off.
                        .then(Commands.literal("debug").executes(context -> {
                            Diagnostics.print(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                        .build(),
                "Hand out cameras and film, reload the config, and see what captures are costing"));
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        plugin.reload();
        context.getSource().getSender().sendMessage(Component.text("MapCamera reloaded.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    /** A branch per camera that is switched on, plus one for film. */
    private LiteralArgumentBuilder<CommandSourceStack> give() {
        LiteralArgumentBuilder<CommandSourceStack> give = Commands.literal("give");
        for (CameraKind camera : plugin.config().cameras()) {
            if (!camera.id().equals(FILM)) {
                give = give.then(branch(camera.id()));
            }
        }
        return give.then(branch(FILM));
    }

    private LiteralArgumentBuilder<CommandSourceStack> branch(String what) {
        return Commands.literal(what)
                .executes(context -> give(context, what, self(context), 1))
                .then(Commands.argument("targets", ArgumentTypes.players())
                        .executes(context -> give(context, what, chosen(context), 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(context -> give(context, what, chosen(context), IntegerArgumentType.getInteger(context, "amount")))));
    }

    private int give(CommandContext<CommandSourceStack> context, String what, List<Player> targets, int amount) {
        CommandSender sender = context.getSource().getSender();
        if (targets.isEmpty()) {
            sender.sendMessage(Component.text("Name somebody to give that to.", NamedTextColor.RED));
            return 0;
        }

        // A reload can turn off the camera a branch was built for, and the branches are only rebuilt on a restart.
        CameraKind camera = what.equals(FILM) ? null : plugin.config().camera(what);
        if (camera == null && !what.equals(FILM)) {
            sender.sendMessage(Component.text("No camera is enabled, so there is none to give.", NamedTextColor.RED));
            return 0;
        }

        for (Player target : targets) {
            // A camera is one item however many were asked for: a stack of them is a stack of one thing that opens
            // one screen. Film is a consumable and stacks like one.
            ItemStack stack = camera == null ? plugin.items().film(amount) : plugin.items().camera(camera);

            target.getInventory().addItem(stack).values()
                    .forEach(spare -> target.getWorld().dropItem(target.getEyeLocation(), spare));
        }

        sender.sendMessage(Component.text("Gave " + what + " to " + targets.size() + " player(s).", NamedTextColor.GREEN));
        return targets.size();
    }

    private static List<Player> chosen(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getArgument("targets", PlayerSelectorArgumentResolver.class).resolve(context.getSource());
    }

    /** No target named, so it is whoever typed it - and nobody, if that was the console. */
    private static List<Player> self(CommandContext<CommandSourceStack> context) {
        return context.getSource().getSender() instanceof Player player ? List.of(player) : List.of();
    }
}
