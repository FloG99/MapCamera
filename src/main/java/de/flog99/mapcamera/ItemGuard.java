package de.flog99.mapcamera;

import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Stops the camera being used as the item it is made of.
 *
 * <p>The default materials have no action worth keeping, but a server may configure one that does - and the head
 * model the camera falls back to without a pack is both placeable and wearable, either of which would take the
 * camera out of the player's hand.
 *
 * <p>MapGUI already swallows clicks while the viewfinder is focused, so this covers the tick before it opens and
 * anyone without {@code mapcamera.use}.
 */
public final class ItemGuard implements Listener {

    private final CameraItems items;

    public ItemGuard(CameraItems items) {
        this.items = items;
    }

    /**
     * Denies the item's own use, which is what placing and wearing both are. The block's use is left alone, so
     * the camera can still open a chest.
     *
     * <p>Then it puts back what the client already drew: placement is predicted client-side, so refusing is not
     * the same as undoing and the two blocks the prediction could have touched are resent as they really are.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.hasItem() || !items.isOurs(event.getItem())) return;

        event.setUseItemInHand(Event.Result.DENY);
        event.getPlayer().updateInventory();

        Block clicked = event.getClickedBlock();
        if (clicked == null || !event.getItem().getType().isBlock()) return;

        Block against = clicked.getRelative(event.getBlockFace());
        event.getPlayer().sendBlockChange(clicked.getLocation(), clicked.getBlockData());
        event.getPlayer().sendBlockChange(against.getLocation(), against.getBlockData());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (items.isOurs(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    /** A dispenser will happily place a head, and a camera in a wall is a camera nobody can pick back up. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (items.isOurs(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
