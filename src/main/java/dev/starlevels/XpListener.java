package dev.starlevels;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class XpListener implements Listener {
    private final StarLevelsPlugin plugin;
    private BukkitTask playtime;

    XpListener(StarLevelsPlugin plugin) { this.plugin = plugin; }

    void startTasks() {
        if (playtime != null) playtime.cancel();
        if (!plugin.getConfig().getBoolean("xp.playtime.enabled", true)) return;
        long ticks = Math.max(1, plugin.getConfig().getInt("xp.playtime.interval-seconds", 60)) * 20L;
        playtime = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double amt = plugin.getConfig().getDouble("xp.playtime.xp", 5);
            for (Player p : Bukkit.getOnlinePlayers()) plugin.giveXp(p, amt);
        }, ticks, ticks);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        LevelManager.Data d = plugin.levels().getOrCreate(p.getUniqueId(), p.getName());
        d.name = p.getName();
        plugin.display().update(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.display().remove(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        double def = plugin.getConfig().getDouble("xp.blocks-default", 0);
        plugin.giveXp(p, plugin.getConfig().getDouble("xp.blocks." + e.getBlock().getType().name(), def));
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        if (e.getEntity() instanceof Player) {
            plugin.giveXp(killer, plugin.getConfig().getDouble("xp.player-kill", 0));
            return;
        }
        double def = e.getEntity() instanceof Monster ? plugin.getConfig().getDouble("xp.mobs-default", 5) : 0;
        plugin.giveXp(killer, plugin.getConfig().getDouble("xp.mobs." + e.getEntityType().name(), def));
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            plugin.giveXp(e.getPlayer(), plugin.getConfig().getDouble("xp.fishing", 10));
        }
    }

    /** Level-up fireworks must never hurt anyone. */
    @EventHandler
    public void onFireworkDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Firework f && f.getScoreboardTags().contains("sl_fw")) e.setCancelled(true);
    }
}
