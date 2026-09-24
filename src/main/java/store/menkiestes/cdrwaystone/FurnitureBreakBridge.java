package store.menkiestes.cdrwaystone;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerEvent;

import java.lang.reflect.Method;

/**
 * Runtime bridge for ItemsAdder furniture destruction.
 *
 * Two paths are supported:
 * 1) direct staff hit observation, which works even while the protected
 *    furniture damage event is cancelled by CdrWaystone;
 * 2) ItemsAdder FurnitureBreakEvent when the installed ItemsAdder build emits it.
 *
 * Both paths converge on the same atomic Waystone cleanup.
 */
public final class FurnitureBreakBridge implements Listener {
    private final CdrWaystonePlugin plugin;

    public FurnitureBreakBridge(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Observe the final damage event state without changing it. CdrWaystone's
     * normal protection listener cancels damage to the furniture entity, but an
     * administrator intentionally left-clicking the official furniture is still
     * treated as a destroy request. Cleanup runs one tick later to avoid entity
     * mutation during the damage callback itself.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onFurnitureHit(EntityDamageByEntityEvent event) {
        WaystoneData data = plugin.visuals().waystoneFromEntity(event.getEntity());
        if (data == null) return;
        if (!(event.getDamager() instanceof Player player)) return;

        if (!player.hasPermission("cdrwaystone.admin")) {
            plugin.feedback().action(player, "§cOfficial Waystone is protected");
            return;
        }
        if (data.permanent()) {
            plugin.feedback().action(player, "§6Permanent Waystone");
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> removeNode(data, player, true));
    }

    @SuppressWarnings("unchecked")
    public void registerItemsAdderEvent() {
        try {
            Class<?> rawClass = Class.forName("dev.lone.itemsadder.api.Events.FurnitureBreakEvent");
            if (!Event.class.isAssignableFrom(rawClass)) {
                plugin.getLogger().warning("ItemsAdder FurnitureBreakEvent is not a Bukkit Event; native break bridge disabled.");
                return;
            }

            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Method getBukkitEntity = rawClass.getMethod("getBukkitEntity");

            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    this,
                    EventPriority.HIGHEST,
                    (listener, event) -> handleItemsAdderBreak(event, getBukkitEntity),
                    plugin,
                    true
            );
            plugin.getLogger().info("ItemsAdder furniture break event bridge enabled.");
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().info("ItemsAdder FurnitureBreakEvent not available; direct furniture destroy fallback remains enabled.");
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("Could not register ItemsAdder furniture break bridge: " + ex.getMessage());
        }
    }

    private void handleItemsAdderBreak(Event event, Method getBukkitEntity) {
        try {
            Object rawEntity = getBukkitEntity.invoke(event);
            if (!(rawEntity instanceof Entity entity)) return;

            WaystoneData data = plugin.visuals().waystoneFromEntity(entity);
            if (data == null) return;

            Player player = event instanceof PlayerEvent playerEvent ? playerEvent.getPlayer() : null;
            if (player == null || !player.hasPermission("cdrwaystone.admin")) {
                if (event instanceof Cancellable cancellable) cancellable.setCancelled(true);
                if (player != null) plugin.feedback().action(player, "§cOfficial Waystone is protected");
                return;
            }

            if (data.permanent()) {
                if (event instanceof Cancellable cancellable) cancellable.setCancelled(true);
                plugin.feedback().action(player, "§6Permanent Waystone");
                return;
            }

            // ItemsAdder owns the furniture removal on this path. CdrWaystone
            // only removes its invisible anchors and persistent records.
            plugin.getServer().getScheduler().runTask(plugin, () -> removeNode(data, player, false));
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Furniture break bridge failed: " + ex.getMessage());
        }
    }

    private void removeNode(WaystoneData data, Player player, boolean removeFurniture) {
        if (plugin.registry().get(data.id()) == null) return;

        if (removeFurniture) plugin.visuals().remove(data);
        else plugin.visuals().removeCollision(data);

        plugin.registry().remove(data);
        plugin.discovery().forgetWaystone(data.id());
        if (player.isOnline()) plugin.feedback().action(player, "§eWaystone removed");
    }
}
