package store.menkiestes.cdrwaystone;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;

import java.lang.reflect.Method;

/**
 * Runtime-only bridge for ItemsAdder's FurnitureBreakEvent.
 * Keeps CdrWaystone free from a hard ItemsAdder compile dependency while
 * allowing a real furniture break to atomically remove the Waystone node.
 */
public final class FurnitureBreakBridge implements Listener {
    private final CdrWaystonePlugin plugin;

    public FurnitureBreakBridge(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public void register() {
        try {
            Class<?> rawClass = Class.forName("dev.lone.itemsadder.api.Events.FurnitureBreakEvent");
            if (!Event.class.isAssignableFrom(rawClass)) {
                plugin.getLogger().warning("ItemsAdder FurnitureBreakEvent is not a Bukkit Event; auto-remove bridge disabled.");
                return;
            }

            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Method getBukkitEntity = rawClass.getMethod("getBukkitEntity");

            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    this,
                    EventPriority.HIGHEST,
                    (listener, event) -> handle(event, getBukkitEntity),
                    plugin,
                    true
            );
            plugin.getLogger().info("ItemsAdder furniture break auto-remove bridge enabled.");
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().info("ItemsAdder FurnitureBreakEvent not available; furniture break bridge skipped.");
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("Could not register ItemsAdder furniture break bridge: " + ex.getMessage());
        }
    }

    private void handle(Event event, Method getBukkitEntity) {
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

            // Let ItemsAdder complete its own furniture removal first, then remove
            // CdrWaystone's invisible anchors and persistent node data next tick.
            plugin.getServer().getScheduler().runTask(plugin, () -> removeNode(data, player));
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Furniture break bridge failed: " + ex.getMessage());
        }
    }

    private void removeNode(WaystoneData data, Player player) {
        if (plugin.registry().get(data.id()) == null) return;
        plugin.visuals().removeCollision(data);
        plugin.registry().remove(data);
        plugin.discovery().forgetWaystone(data.id());
        if (player.isOnline()) plugin.feedback().action(player, "§eWaystone removed");
    }
}
