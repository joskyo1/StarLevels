package dev.starlevels;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.UUID;

/** Shows the star + level badge before player names (name tag, tab list) and in chat. */
public final class DisplayManager implements Listener {
    private final StarLevelsPlugin plugin;

    DisplayManager(StarLevelsPlugin plugin) { this.plugin = plugin; }

    private static String teamName(UUID id) { return "sl_" + id.toString().substring(0, 8); }

    public Component badge(LevelManager.Data d) {
        return Component.text(plugin.getConfig().getString("display.star", "★") + d.level, plugin.levels().starColor(d));
    }

    public void update(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam(teamName(id));
        boolean on = plugin.getConfig().getBoolean("display.enabled", true)
                && plugin.getConfig().getBoolean("display.nametag", true);
        LevelManager.Data d = plugin.levels().get(id);
        if (!on || d == null) {
            if (t != null) t.unregister();
            return;
        }
        if (t == null) t = sb.registerNewTeam(teamName(id));
        t.prefix(badge(d).append(Component.space()));
        if (!t.hasEntry(p.getName())) t.addEntry(p.getName());
    }

    public void remove(Player p) {
        Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName(p.getUniqueId()));
        if (t != null) t.unregister();
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p.getUniqueId());
    }

    public void removeAll() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : new ArrayList<>(sb.getTeams())) if (t.getName().startsWith("sl_")) t.unregister();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.getConfig().getBoolean("display.enabled", true)
                || !plugin.getConfig().getBoolean("display.chat", true)) return;
        LevelManager.Data d = plugin.levels().get(e.getPlayer().getUniqueId());
        if (d == null) return;
        Component b = badge(d).append(Component.space());
        ChatRenderer prev = e.renderer();
        e.renderer((source, name, msg, viewer) -> b.append(prev.render(source, name, msg, viewer)));
    }
}
