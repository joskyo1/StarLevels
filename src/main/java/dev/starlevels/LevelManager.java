package dev.starlevels;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LevelManager {

    public static final class Data {
        public final UUID id;
        public volatile String name;
        public volatile int level = 1;
        public volatile double xp = 0;
        public volatile String color = "";   // empty = tier color
        public volatile double coins = 0;

        Data(UUID id, String name) { this.id = id; this.name = name; }
    }

    private final StarLevelsPlugin plugin;
    private final File file;
    private final Map<UUID, Data> map = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    LevelManager(StarLevelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    // ---------- storage ----------

    void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("players");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                Data d = new Data(id, s.getString(k + ".name", "?"));
                d.level = Math.max(1, s.getInt(k + ".level", 1));
                d.xp = s.getDouble(k + ".xp");
                d.color = s.getString(k + ".color", "");
                d.coins = s.getDouble(k + ".coins");
                map.put(id, d);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        for (Data d : map.values()) {
            String p = "players." + d.id;
            y.set(p + ".name", d.name);
            y.set(p + ".level", d.level);
            y.set(p + ".xp", d.xp);
            y.set(p + ".color", d.color);
            y.set(p + ".coins", d.coins);
        }
        return y.saveToString();
    }

    private void write(String content) {
        try {
            plugin.getDataFolder().mkdirs();
            Files.writeString(file.toPath(), content);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }

    void saveAsync() {
        if (!dirty) return;
        dirty = false;
        String s = serialize();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(s));
    }

    void saveNow() { dirty = false; write(serialize()); }

    public void markDirty() { dirty = true; }

    // ---------- lookup ----------

    public Data get(UUID id) { return map.get(id); }

    public Data getOrCreate(UUID id, String name) {
        return map.computeIfAbsent(id, k -> { dirty = true; return new Data(k, name); });
    }

    public Data findByName(String name) {
        for (Data d : map.values()) if (d.name.equalsIgnoreCase(name)) return d;
        return null;
    }

    public Collection<Data> all() { return map.values(); }

    // ---------- math ----------

    public int maxLevel() { return Math.max(1, plugin.getConfig().getInt("leveling.max-level", 100)); }

    /** XP required to go from `level` to `level + 1`. 0 at max level. */
    public double xpNeeded(int level) {
        if (level >= maxLevel()) return 0;
        double base = plugin.getConfig().getDouble("leveling.base-xp", 100);
        double exp = plugin.getConfig().getDouble("leveling.exponent", 1.5);
        return Math.max(1, Math.floor(base * Math.pow(level, exp)));
    }

    // ---------- changing levels ----------

    public int addXp(UUID id, double amount) {
        Data d = map.get(id);
        if (d == null || amount <= 0) return 0;
        int max = maxLevel();
        if (d.level >= max) return 0;

        d.xp += amount;
        if (plugin.currency() instanceof BuiltInCurrency) {
            d.coins += amount * plugin.getConfig().getDouble("economy.built-in.coins-per-xp", 0);
        }
        int gained = 0;
        while (d.level < max && d.xp >= xpNeeded(d.level)) {
            d.xp -= xpNeeded(d.level);
            d.level++;
            gained++;
            rewards(d, d.level);
        }
        if (d.level >= max) d.xp = 0;
        dirty = true;
        if (gained > 0) {
            plugin.display().update(id);
            celebrate(d);
        }
        return gained;
    }

    /** Admin set: no effects, XP progress resets. */
    public void setLevel(UUID id, int level) {
        Data d = map.get(id);
        if (d == null) return;
        d.level = Math.max(1, Math.min(maxLevel(), level));
        d.xp = 0;
        dirty = true;
        plugin.display().update(id);
    }

    public void setColor(UUID id, String color) {
        Data d = map.get(id);
        if (d == null) return;
        d.color = color == null ? "" : color;
        dirty = true;
        plugin.display().update(id);
    }

    // ---------- colors ----------

    public TextColor starColor(Data d) {
        TextColor c = Util.parseColor(d.color);
        return c != null ? c : tierColor(d.level);
    }

    public TextColor tierColor(int level) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("display.tiers");
        int best = -1;
        String bestVal = "white";
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try {
                    int min = Integer.parseInt(k);
                    if (min <= level && min > best) { best = min; bestVal = s.getString(k, "white"); }
                } catch (NumberFormatException ignored) {}
            }
        }
        TextColor c = Util.parseColor(bestVal);
        return c != null ? c : NamedTextColor.WHITE;
    }

    // ---------- rewards & effects ----------

    private void rewards(Data d, int newLevel) {
        Player p = Bukkit.getPlayer(d.id);
        double money = plugin.getConfig().getDouble("rewards.money-per-level", 0) * newLevel;
        if (money > 0) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(d.id);
            plugin.currency().deposit(op, money);
        }
        for (String cmd : plugin.getConfig().getStringList("rewards.commands." + newLevel)) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("%player%", d.name).replace("%level%", String.valueOf(newLevel)));
        }
        if (p != null && money > 0) {
            p.sendMessage(Util.msg("<green>Level reward: <gold>" + plugin.currency().format(money)));
        }
    }

    private void celebrate(Data d) {
        Player p = Bukkit.getPlayer(d.id);
        if (p == null) return;
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("effects.level-up.enabled", true)) return;
        TextColor col = starColor(d);
        String hex = Util.tag(col);
        String star = c.getString("display.star", "★");

        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(
                c.getString("effects.level-up.sound", "entity.player.levelup").toLowerCase()));
        if (sound != null) p.playSound(p.getLocation(), sound, 1f, 1f);

        try {
            Particle particle = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(
                    c.getString("effects.level-up.particle", "totem_of_undying").toLowerCase()));
            if (particle != null) {
                p.getWorld().spawnParticle(particle, p.getLocation().add(0, 1, 0),
                        c.getInt("effects.level-up.particle-count", 60), 0.5, 1, 0.5, 0.15);
            }
        } catch (Exception ignored) {}

        if (c.getBoolean("effects.level-up.firework", true)) {
            Firework fw = p.getWorld().spawn(p.getLocation(), Firework.class, f -> {
                FireworkMeta m = f.getFireworkMeta();
                m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.STAR)
                        .withColor(Color.fromRGB(col.red(), col.green(), col.blue()))
                        .withFade(Color.WHITE).trail(true).build());
                m.setPower(0);
                f.setFireworkMeta(m);
                f.addScoreboardTag("sl_fw");
            });
            fw.detonate();
        }

        if (c.getBoolean("effects.level-up.title", true)) {
            p.showTitle(Title.title(
                    Util.mm(hex + "<bold>" + star + " LEVEL UP! " + star),
                    Util.mm("<gray>You reached level " + hex + d.level),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }

        int every = c.getInt("effects.level-up.broadcast-every", 10);
        if (every > 0 && d.level % every == 0) {
            Bukkit.broadcast(Util.msg(hex + star + d.level + " <white>" + d.name + " <gray>reached level " + hex + d.level + "<gray>!"));
        }
    }
}
