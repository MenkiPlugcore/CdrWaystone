package store.menkiestes.cdrwaystone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class FeedbackService {
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    public void action(Player player, String message) {
        if (player == null) return;
        player.sendActionBar(legacy.deserialize(message == null ? "" : message));
    }

    public void title(Player player, String title, String subtitle) {
        title(player, title, subtitle, 150, 1100, 300);
    }

    public void title(Player player, String title, String subtitle, long fadeInMs, long stayMs, long fadeOutMs) {
        if (player == null) return;
        player.showTitle(Title.title(
                legacy.deserialize(title == null ? "" : title),
                legacy.deserialize(subtitle == null ? "" : subtitle),
                Title.Times.times(Duration.ofMillis(fadeInMs), Duration.ofMillis(stayMs), Duration.ofMillis(fadeOutMs))
        ));
    }

    /** Player command feedback stays off chat; console still receives normal command output. */
    public void command(CommandSender sender, String message) {
        if (sender instanceof Player player) action(player, message);
        else sender.sendMessage(message == null ? "" : message);
    }
}
