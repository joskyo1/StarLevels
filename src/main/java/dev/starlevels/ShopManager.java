package dev.starlevels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShopManager {

    public static final class ShopItem {
        public final String id;
        public final ItemStack item;
        public double price;
        public int level;

        ShopItem(String id, ItemStack item, double price, int level) {
            this.id = id; this.item = item; this.price = price; this.level = level;
        }
    }

    private final StarLevelsPlugin plugin;
    private final File file;
    private final Map<String, ShopItem> items = new LinkedHashMap<>();
    private int nextId = 1;

    ShopManager(StarLevelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shop.yml");
    }

    void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getInt("next-id", 1);
        ConfigurationSection s = y.getConfigurationSection("items");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            ItemStack it = s.getItemStack(k + ".item");
            if (it == null) continue;
            items.put(k, new ShopItem(k, it, s.getDouble(k + ".price"), Math.max(1, s.getInt(k + ".level", 1))));
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-id", nextId);
        for (ShopItem si : items.values()) {
            String p = "items." + si.id;
            y.set(p + ".item", si.item);
            y.set(p + ".price", si.price);
            y.set(p + ".level", si.level);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save shop.yml: " + e.getMessage());
        }
    }

    public Collection<ShopItem> items() { return items.values(); }
    public ShopItem get(String id) { return items.get(id); }

    public ShopItem add(ItemStack stack, double price, int level) {
        String id = String.valueOf(nextId++);
        ShopItem si = new ShopItem(id, stack.clone(), price, level);
        items.put(id, si);
        save();
        return si;
    }

    public boolean remove(String id) {
        boolean r = items.remove(id) != null;
        if (r) save();
        return r;
    }

    public void buy(Player p, ShopItem si) {
        LevelManager.Data d = plugin.levels().get(p.getUniqueId());
        int lvl = d == null ? 1 : d.level;
        if (lvl < si.level) {
            p.sendMessage(Util.msg("<red>You need level <white>★" + si.level + "<red> to buy this."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        Currency cur = plugin.currency();
        if (!cur.has(p, si.price)) {
            p.sendMessage(Util.msg("<red>You can't afford this. It costs <gold>" + cur.format(si.price) + "<red>."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        // simulate inventory space first so nobody pays for an item that doesn't fit
        Inventory temp = Bukkit.createInventory(null, 36);
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && i < 36; i++) {
            if (contents[i] != null) temp.setItem(i, contents[i].clone());
        }
        if (!temp.addItem(si.item.clone()).isEmpty()) {
            p.sendMessage(Util.msg("<red>Your inventory is full."));
            return;
        }
        if (!cur.withdraw(p, si.price)) {
            p.sendMessage(Util.msg("<red>Payment failed."));
            return;
        }
        p.getInventory().addItem(si.item.clone());
        p.sendMessage(Util.msg("<green>Purchased ")
                .append(si.item.displayName())
                .append(Component.text(" for " + cur.format(si.price), NamedTextColor.GOLD)));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }
}
