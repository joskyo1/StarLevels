package dev.starlevels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class GuiManager implements Listener {
    private final StarLevelsPlugin plugin;

    GuiManager(StarLevelsPlugin plugin) { this.plugin = plugin; }

    // ---------------------------------------------------------------- holders

    static abstract class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }
    static final class ShopHolder extends Holder { int page; boolean edit; final List<String> ids = new ArrayList<>(); }
    static final class ShopEditHolder extends Holder { String id; int page; }
    static final class LevelsHolder extends Holder { int page; final List<UUID> ids = new ArrayList<>(); }
    static final class PlayerHolder extends Holder { UUID target; }
    static final class ColorHolder extends Holder { UUID target; }

    private static final String[] COLORS = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
    private static final Material[] COLOR_MATS = {
            Material.BLACK_CONCRETE, Material.BLUE_CONCRETE, Material.GREEN_CONCRETE, Material.CYAN_CONCRETE,
            Material.RED_CONCRETE, Material.PURPLE_CONCRETE, Material.ORANGE_CONCRETE, Material.LIGHT_GRAY_CONCRETE,
            Material.GRAY_CONCRETE, Material.BLUE_WOOL, Material.LIME_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.RED_WOOL, Material.MAGENTA_CONCRETE, Material.YELLOW_CONCRETE, Material.WHITE_CONCRETE};

    private boolean isAdmin(Player p) { return p.hasPermission("starlevels.admin"); }
    private boolean isShopAdmin(Player p) { return p.hasPermission("starlevels.shop.admin"); }

    private int levelOf(Player p) {
        LevelManager.Data d = plugin.levels().get(p.getUniqueId());
        return d == null ? 1 : d.level;
    }

    // ================================================================ SHOP

    public void openShop(Player p, int page, boolean edit) {
        ShopHolder h = new ShopHolder();
        List<ShopManager.ShopItem> all = new ArrayList<>(plugin.shop().items());
        all.sort(Comparator.comparingInt((ShopManager.ShopItem s) -> s.level).thenComparingDouble(s -> s.price));
        int pages = Math.max(1, (int) Math.ceil(all.size() / 45.0));
        page = Math.max(0, Math.min(page, pages - 1));
        h.page = page;
        h.edit = edit && isShopAdmin(p);
        h.inv = Bukkit.createInventory(h, 54, Util.mm((h.edit ? "<red>Shop Editor" : "<gold>★ Level Shop")
                + " <dark_gray>(" + (page + 1) + "/" + pages + ")"));
        int lvl = levelOf(p);
        for (int i = 0; i < 45; i++) {
            int idx = page * 45 + i;
            if (idx < all.size()) {
                ShopManager.ShopItem si = all.get(idx);
                h.ids.add(si.id);
                h.inv.setItem(i, shopDisplay(si, lvl, h.edit));
            } else {
                h.ids.add(null);
            }
        }
        for (int i = 45; i < 54; i++) h.inv.setItem(i, Util.filler());
        if (page > 0) h.inv.setItem(45, Util.item(Material.ARROW, "<yellow>Previous page"));
        if (page < pages - 1) h.inv.setItem(53, Util.item(Material.ARROW, "<yellow>Next page"));
        h.inv.setItem(49, shopInfo(p, h.edit));
        if (isShopAdmin(p)) {
            h.inv.setItem(51, h.edit
                    ? Util.item(Material.LIME_DYE, "<green>Edit mode: ON", "<gray>Click to switch back to shopping")
                    : Util.item(Material.GRAY_DYE, "<gray>Edit mode: OFF", "<yellow>Click to edit the shop"));
        }
        p.openInventory(h.inv);
    }

    private ItemStack shopInfo(Player p, boolean edit) {
        Currency c = plugin.currency();
        if (edit) {
            return Util.item(Material.BOOK, "<yellow>Editing the shop",
                    "<gray>Click an item you're holding in", "<gray>your <white>own inventory<gray> to add it.",
                    "<gray>Click a shop item to edit it.",
                    "<gray>Shift + right-click removes it.");
        }
        return Util.item(Material.NETHER_STAR, "<yellow>Your stats",
                "<gray>Level: <white>★" + levelOf(p),
                "<gray>Balance: <gold>" + c.format(c.balance(p)));
    }

    private ItemStack shopDisplay(ShopManager.ShopItem si, int playerLevel, boolean edit) {
        ItemStack it = si.item.clone();
        ItemMeta m = it.getItemMeta();
        List<Component> lore = m.hasLore() && m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
        boolean ok = playerLevel >= si.level;
        lore.add(Component.empty());
        lore.add(Util.mm("<gray>Price: <gold>" + plugin.currency().format(si.price)));
        lore.add(Util.mm("<gray>Required level: " + (ok ? "<green>" : "<red>") + "★" + si.level));
        if (edit) {
            lore.add(Util.mm("<yellow>Click: <white>edit"));
            lore.add(Util.mm("<yellow>Shift + right-click: <white>remove"));
        } else {
            lore.add(Util.mm(ok ? "<green>Click to buy" : "<red>Locked: reach level " + si.level));
        }
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private void shopClick(Player p, ShopHolder h, InventoryClickEvent e, boolean top, int slot) {
        if (!top) {
            // bottom inventory: in edit mode, clicking one of your items adds it to the shop
            ItemStack cur = e.getCurrentItem();
            if (h.edit && isShopAdmin(p) && cur != null && !cur.getType().isAir()) {
                ShopManager.ShopItem si = plugin.shop().add(cur, 100, 1);
                p.sendMessage(Util.msg("<green>Added to the shop. Set its price and level below."));
                openShopEdit(p, si.id, h.page);
            }
            return;
        }
        ItemStack cur = e.getCurrentItem();
        boolean arrow = cur != null && cur.getType() == Material.ARROW;
        if (slot == 45 && arrow) { openShop(p, h.page - 1, h.edit); return; }
        if (slot == 53 && arrow) { openShop(p, h.page + 1, h.edit); return; }
        if (slot == 51 && isShopAdmin(p)) { openShop(p, h.page, !h.edit); return; }
        if (slot >= 45) return;

        String id = h.ids.get(slot);
        if (id == null) return;
        ShopManager.ShopItem si = plugin.shop().get(id);
        if (si == null) { openShop(p, h.page, h.edit); return; }
        if (h.edit && isShopAdmin(p)) {
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.shop().remove(id);
                openShop(p, h.page, true);
            } else {
                openShopEdit(p, id, h.page);
            }
        } else {
            plugin.shop().buy(p, si);
            h.inv.setItem(49, shopInfo(p, false));
        }
    }

    // ---- shop item editor

    public void openShopEdit(Player p, String id, int page) {
        if (plugin.shop().get(id) == null) { openShop(p, page, true); return; }
        ShopEditHolder h = new ShopEditHolder();
        h.id = id;
        h.page = page;
        h.inv = Bukkit.createInventory(h, 45, Util.mm("<red>Edit shop item"));
        fillShopEdit(h);
        p.openInventory(h.inv);
    }

    private void fillShopEdit(ShopEditHolder h) {
        ShopManager.ShopItem si = plugin.shop().get(h.id);
        Inventory inv = h.inv;
        inv.clear();
        for (int i = 0; i < 45; i++) inv.setItem(i, Util.filler());
        inv.setItem(4, si.item.clone());

        inv.setItem(10, Util.item(Material.RED_STAINED_GLASS_PANE, "<red>-5 levels"));
        inv.setItem(11, Util.item(Material.RED_STAINED_GLASS_PANE, "<red>-1 level"));
        inv.setItem(13, Util.item(Material.NETHER_STAR, "<yellow>Required level: <white>★" + si.level));
        inv.setItem(15, Util.item(Material.LIME_STAINED_GLASS_PANE, "<green>+1 level"));
        inv.setItem(16, Util.item(Material.LIME_STAINED_GLASS_PANE, "<green>+5 levels"));

        inv.setItem(19, Util.item(Material.RED_STAINED_GLASS_PANE, "<red>-1000"));
        inv.setItem(20, Util.item(Material.RED_STAINED_GLASS_PANE, "<red>-100"));
        inv.setItem(21, Util.item(Material.RED_STAINED_GLASS_PANE, "<red>-10"));
        inv.setItem(22, Util.item(Material.GOLD_INGOT, "<yellow>Price: <gold>" + plugin.currency().format(si.price),
                "<gray>Exact price: <white>/shopadmin setprice " + si.id + " <amount>"));
        inv.setItem(23, Util.item(Material.LIME_STAINED_GLASS_PANE, "<green>+10"));
        inv.setItem(24, Util.item(Material.LIME_STAINED_GLASS_PANE, "<green>+100"));
        inv.setItem(25, Util.item(Material.LIME_STAINED_GLASS_PANE, "<green>+1000"));

        inv.setItem(36, Util.item(Material.ARROW, "<yellow>Back to shop"));
        inv.setItem(44, Util.item(Material.BARRIER, "<red>Remove from shop"));
    }

    private void shopEditClick(Player p, ShopEditHolder h, int slot) {
        if (!isShopAdmin(p)) { p.closeInventory(); return; }
        ShopManager.ShopItem si = plugin.shop().get(h.id);
        if (si == null) { openShop(p, h.page, true); return; }
        int max = plugin.levels().maxLevel();
        switch (slot) {
            case 10 -> si.level = Math.max(1, si.level - 5);
            case 11 -> si.level = Math.max(1, si.level - 1);
            case 15 -> si.level = Math.min(max, si.level + 1);
            case 16 -> si.level = Math.min(max, si.level + 5);
            case 19 -> si.price = Math.max(0, si.price - 1000);
            case 20 -> si.price = Math.max(0, si.price - 100);
            case 21 -> si.price = Math.max(0, si.price - 10);
            case 23 -> si.price += 10;
            case 24 -> si.price += 100;
            case 25 -> si.price += 1000;
            case 36 -> { plugin.shop().save(); openShop(p, h.page, true); return; }
            case 44 -> { plugin.shop().remove(h.id); openShop(p, h.page, true); return; }
            default -> { return; }
        }
        plugin.shop().save();
        fillShopEdit(h);
    }

    // ================================================================ LEVELS LIST

    public void openLevels(Player p, int page) {
        LevelsHolder h = new LevelsHolder();
        List<LevelManager.Data> all = new ArrayList<>(plugin.levels().all());
        all.sort(Comparator.comparingInt((LevelManager.Data d) -> d.level).thenComparingDouble(d -> d.xp).reversed());
        int pages = Math.max(1, (int) Math.ceil(all.size() / 45.0));
        page = Math.max(0, Math.min(page, pages - 1));
        h.page = page;
        h.inv = Bukkit.createInventory(h, 54, Util.mm("<gold>★ Player Levels <dark_gray>(" + (page + 1) + "/" + pages + ")"));
        boolean admin = isAdmin(p);
        for (int i = 0; i < 45; i++) {
            int idx = page * 45 + i;
            if (idx >= all.size()) break;
            LevelManager.Data d = all.get(idx);
            h.ids.add(d.id);
            h.inv.setItem(i, head(d, admin));
        }
        for (int i = 45; i < 54; i++) h.inv.setItem(i, Util.filler());
        if (page > 0) h.inv.setItem(45, Util.item(Material.ARROW, "<yellow>Previous page"));
        if (page < pages - 1) h.inv.setItem(53, Util.item(Material.ARROW, "<yellow>Next page"));
        h.inv.setItem(49, Util.item(Material.BOOK, "<yellow>" + all.size() + " players",
                admin ? "<gray>Click a player to edit them" : "<gray>Sorted by level"));
        p.openInventory(h.inv);
    }

    private ItemStack head(LevelManager.Data d, boolean admin) {
        LevelManager levels = plugin.levels();
        TextColor col = levels.starColor(d);
        OfflinePlayer op = Bukkit.getOfflinePlayer(d.id);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(op);
        sm.displayName(Util.mm(Util.tag(col) + plugin.getConfig().getString("display.star", "★") + d.level + " <white>" + d.name));
        double need = levels.xpNeeded(d.level);
        List<Component> lore = new ArrayList<>();
        lore.add(Util.mm(need <= 0 ? "<gold>MAX LEVEL"
                : "<gray>XP: <white>" + (int) d.xp + "<gray>/<white>" + (int) need));
        if (need > 0) lore.add(Util.mm(Util.bar(d.xp, need, 20)));
        lore.add(Util.mm("<gray>Star color: " + Util.tag(col) + (d.color == null || d.color.isEmpty() ? "tier default" : d.color)));
        lore.add(Util.mm(op.isOnline() ? "<green>Online" : "<dark_gray>Offline"));
        if (admin) { lore.add(Component.empty()); lore.add(Util.mm("<yellow>Click to edit")); }
        sm.lore(lore);
        head.setItemMeta(sm);
        return head;
    }

    // ================================================================ PLAYER EDITOR

    public void openPlayer(Player p, UUID target) {
        LevelManager.Data d = plugin.levels().get(target);
        if (d == null) return;
        PlayerHolder h = new PlayerHolder();
        h.target = target;
        h.inv = Bukkit.createInventory(h, 45, Util.mm("<gold>Edit <white>" + d.name));
        fillPlayer(h);
        p.openInventory(h.inv);
    }

    private void fillPlayer(PlayerHolder h) {
        LevelManager lm = plugin.levels();
        LevelManager.Data d = lm.get(h.target);
        Inventory inv = h.inv;
        inv.clear();
        for (int i = 0; i < 45; i++) inv.setItem(i, Util.filler());
        inv.setItem(4, head(d, false));

        inv.setItem(10, Util.item(Material.RED_STAINED_GLASS_PANE, "<red>-10 levels"));
        inv.setItem(11, Util.item(Material.RED_STAINED_GLASS_PANE, "<red>-5 levels"));
        inv.setItem(12, Util.item(Material.RED_STAINED_GLASS_PANE, "<red>-1 level"));
        inv.setItem(13, Util.item(Material.NETHER_STAR, Util.tag(lm.starColor(d)) + "Level ★" + d.level,
                "<gray>Max level: <white>" + lm.maxLevel(), "<gray>Changing level resets XP progress."));
        inv.setItem(14, Util.item(Material.LIME_STAINED_GLASS_PANE, "<green>+1 level"));
        inv.setItem(15, Util.item(Material.LIME_STAINED_GLASS_PANE, "<green>+5 levels"));
        inv.setItem(16, Util.item(Material.LIME_STAINED_GLASS_PANE, "<green>+10 levels"));

        inv.setItem(20, Util.item(Material.EXPERIENCE_BOTTLE, "<green>+100 XP"));
        inv.setItem(21, Util.item(Material.EXPERIENCE_BOTTLE, "<green>+1000 XP"));
        inv.setItem(23, Util.item(Material.NAME_TAG, Util.tag(lm.starColor(d)) + "Star color",
                "<gray>Click to pick a color"));
        inv.setItem(24, Util.item(Material.TNT, "<red>Reset to level 1", "<yellow>Shift-click to confirm"));
        Currency c = plugin.currency();
        inv.setItem(26, Util.item(Material.GOLD_NUGGET, "<gold>Balance: " + c.format(c.balance(Bukkit.getOfflinePlayer(h.target))),
                "<green>Left-click: +100", "<red>Right-click: -100"));

        inv.setItem(36, Util.item(Material.ARROW, "<yellow>Back to player list"));
    }

    private void playerClick(Player p, PlayerHolder h, int slot, InventoryClickEvent e) {
        if (!isAdmin(p)) { p.closeInventory(); return; }
        LevelManager lm = plugin.levels();
        LevelManager.Data d = lm.get(h.target);
        if (d == null) { p.closeInventory(); return; }
        switch (slot) {
            case 10 -> lm.setLevel(h.target, d.level - 10);
            case 11 -> lm.setLevel(h.target, d.level - 5);
            case 12 -> lm.setLevel(h.target, d.level - 1);
            case 14 -> lm.setLevel(h.target, d.level + 1);
            case 15 -> lm.setLevel(h.target, d.level + 5);
            case 16 -> lm.setLevel(h.target, d.level + 10);
            case 20 -> lm.addXp(h.target, 100);
            case 21 -> lm.addXp(h.target, 1000);
            case 23 -> { openColor(p, h.target); return; }
            case 24 -> {
                if (!e.isShiftClick()) { p.sendMessage(Util.msg("<yellow>Shift-click to confirm the reset.")); return; }
                lm.setLevel(h.target, 1);
            }
            case 26 -> {
                OfflinePlayer op = Bukkit.getOfflinePlayer(h.target);
                if (e.isRightClick()) plugin.currency().withdraw(op, 100); else plugin.currency().deposit(op, 100);
            }
            case 36 -> { openLevels(p, 0); return; }
            default -> { return; }
        }
        fillPlayer(h);
    }

    // ================================================================ COLOR PICKER

    public void openColor(Player p, UUID target) {
        LevelManager.Data d = plugin.levels().get(target);
        if (d == null) return;
        ColorHolder h = new ColorHolder();
        h.target = target;
        h.inv = Bukkit.createInventory(h, 27, Util.mm("<gold>Star color: <white>" + d.name));
        for (int i = 0; i < 27; i++) h.inv.setItem(i, Util.filler());
        for (int i = 0; i < COLORS.length; i++) {
            int slot = (i / 8) * 9 + (i % 8);
            h.inv.setItem(slot, Util.item(COLOR_MATS[i], "<" + COLORS[i] + ">★ " + COLORS[i].replace('_', ' '),
                    COLORS[i].equalsIgnoreCase(d.color) ? "<green>Currently selected" : "<gray>Click to apply"));
        }
        h.inv.setItem(18, Util.item(Material.BARRIER, "<white>Tier default", "<gray>Use the color for their level"));
        h.inv.setItem(22, Util.item(Material.PAPER, "<yellow>Custom hex color",
                "<gray>Use <white>/sl color " + d.name + " #RRGGBB"));
        h.inv.setItem(26, Util.item(Material.ARROW, "<yellow>Back"));
        p.openInventory(h.inv);
    }

    private void colorClick(Player p, ColorHolder h, int slot) {
        if (!isAdmin(p)) { p.closeInventory(); return; }
        if (slot == 26) { openPlayer(p, h.target); return; }
        if (slot == 18) { plugin.levels().setColor(h.target, ""); openColor(p, h.target); return; }
        if (slot < 0 || slot > 17 || slot % 9 >= 8) return;
        int i = (slot / 9) * 8 + (slot % 9);
        if (i >= COLORS.length) return;
        plugin.levels().setColor(h.target, COLORS[i]);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openColor(p, h.target);
    }

    // ================================================================ EVENTS

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory topInv = e.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int raw = e.getRawSlot();
        boolean top = raw >= 0 && raw < topInv.getSize();

        if (h instanceof ShopHolder sh) {
            shopClick(p, sh, e, top, raw);
        } else if (!top) {
            return;
        } else if (h instanceof ShopEditHolder eh) {
            shopEditClick(p, eh, raw);
        } else if (h instanceof LevelsHolder lh) {
            ItemStack cur = e.getCurrentItem();
            boolean arrow = cur != null && cur.getType() == Material.ARROW;
            if (raw == 45 && arrow) openLevels(p, lh.page - 1);
            else if (raw == 53 && arrow) openLevels(p, lh.page + 1);
            else if (raw < 45 && raw < lh.ids.size() && isAdmin(p)) openPlayer(p, lh.ids.get(raw));
        } else if (h instanceof PlayerHolder ph) {
            playerClick(p, ph, raw, e);
        } else if (h instanceof ColorHolder ch) {
            colorClick(p, ch, raw);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
