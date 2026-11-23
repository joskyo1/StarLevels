package dev.starlevels;

import org.bukkit.OfflinePlayer;

/** Fallback currency stored in data.yml, used when Vault is not installed. */
public final class BuiltInCurrency implements Currency {
    private final StarLevelsPlugin plugin;

    BuiltInCurrency(StarLevelsPlugin plugin) { this.plugin = plugin; }

    private LevelManager.Data data(OfflinePlayer p) { return plugin.levels().get(p.getUniqueId()); }

    @Override public double balance(OfflinePlayer p) { LevelManager.Data d = data(p); return d == null ? 0 : d.coins; }
    @Override public boolean has(OfflinePlayer p, double a) { return balance(p) >= a; }

    @Override public void deposit(OfflinePlayer p, double a) {
        LevelManager.Data d = data(p);
        if (d == null) return;
        d.coins += a;
        plugin.levels().markDirty();
    }

    @Override public boolean withdraw(OfflinePlayer p, double a) {
        LevelManager.Data d = data(p);
        if (d == null || d.coins < a) return false;
        d.coins -= a;
        plugin.levels().markDirty();
        return true;
    }

    @Override public String format(double a) {
        return plugin.getConfig().getString("economy.symbol", "$") + Util.fmt(a);
    }
}
