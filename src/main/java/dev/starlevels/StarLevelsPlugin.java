package dev.starlevels;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class StarLevelsPlugin extends JavaPlugin {
    private LevelManager levels;
    private DisplayManager display;
    private ShopManager shop;
    private GuiManager guis;
    private BuiltInCurrency builtIn;
    private XpListener xp;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        levels = new LevelManager(this);
        levels.load();
        builtIn = new BuiltInCurrency(this);
        shop = new ShopManager(this);
        shop.load();
        display = new DisplayManager(this);
        guis = new GuiManager(this);
        xp = new XpListener(this);

        getServer().getPluginManager().registerEvents(display, this);
        getServer().getPluginManager().registerEvents(guis, this);
        getServer().getPluginManager().registerEvents(xp, this);

        Commands c = new Commands(this);
        for (String n : new String[]{"level", "levels", "sl", "shop", "shopadmin", "coins"}) {
            PluginCommand pc = getCommand(n);
            if (pc != null) { pc.setExecutor(c); pc.setTabCompleter(c); }
        }

        xp.startTasks();
        Bukkit.getScheduler().runTaskTimer(this, levels::saveAsync, 20L * 60 * 5, 20L * 60 * 5);

        for (Player p : Bukkit.getOnlinePlayers()) levels.getOrCreate(p.getUniqueId(), p.getName());
        display.refreshAll();
    }

    @Override
    public void onDisable() {
        if (levels != null) levels.saveNow();
        if (shop != null) shop.save();
        if (display != null) display.removeAll();
    }

    public void reload() {
        reloadConfig();
        xp.startTasks();
        display.refreshAll();
    }

    /** Vault if installed (and not forced off), otherwise the built-in coin balance. */
    public Currency currency() {
        String prov = getConfig().getString("economy.provider", "AUTO").toUpperCase(Locale.ROOT);
        if (!prov.equals("BUILTIN") && getServer().getPluginManager().isPluginEnabled("Vault")) {
            try {
                Currency v = VaultCurrency.create(this);
                if (v != null) return v;
            } catch (Throwable ignored) {}
        }
        return builtIn;
    }

    /** Gives XP with the global multiplier applied and shows the action bar. */
    public void giveXp(Player p, double base) {
        if (base <= 0) return;
        LevelManager.Data d = levels.get(p.getUniqueId());
        if (d == null) return;
        double amt = base * getConfig().getDouble("xp.multiplier", 1.0);
        levels.addXp(p.getUniqueId(), amt);
        if (getConfig().getBoolean("xp.actionbar", true)) {
            double need = levels.xpNeeded(d.level);
            String progress = need <= 0 ? "<gold>MAX LEVEL" : "<yellow>" + (int) d.xp + "<gray>/<yellow>" + (int) need;
            p.sendActionBar(Util.mm("<gray>+<green>" + Util.fmt(Math.round(amt * 10) / 10.0) + " XP <dark_gray>| " + progress));
        }
    }

    public LevelManager levels() { return levels; }
    public DisplayManager display() { return display; }
    public ShopManager shop() { return shop; }
    public GuiManager guis() { return guis; }
}
