package dev.starlevels;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Commands implements CommandExecutor, TabCompleter {
    private final StarLevelsPlugin plugin;

    Commands(StarLevelsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        try {
            switch (cmd.getName().toLowerCase(Locale.ROOT)) {
                case "level" -> level(s, a);
                case "levels" -> { if (s instanceof Player p) plugin.guis().openLevels(p, 0); else s.sendMessage("Players only."); }
                case "shop" -> { if (s instanceof Player p) plugin.guis().openShop(p, 0, false); else s.sendMessage("Players only."); }
                case "coins" -> {
                    if (s instanceof Player p) {
                        Currency c = plugin.currency();
                        s.sendMessage(Util.msg("<gray>Balance: <gold>" + c.format(c.balance(p))));
                    }
                }
                case "sl" -> sl(s, a);
                case "shopadmin" -> shopAdmin(s, a);
                default -> {}
            }
        } catch (NumberFormatException ex) {
            s.sendMessage(Util.msg("<red>That isn't a valid number."));
        }
        return true;
    }

    // ---------------------------------------------------------------- /level

    private void level(CommandSender s, String[] a) {
        LevelManager lm = plugin.levels();
        LevelManager.Data d;
        if (a.length > 0) d = lm.findByName(a[0]);
        else if (s instanceof Player p) d = lm.get(p.getUniqueId());
        else { s.sendMessage("Specify a player."); return; }
        if (d == null) { s.sendMessage(Util.msg("<red>Unknown player.")); return; }

        s.sendMessage(Util.msg(Util.tag(lm.starColor(d)) + plugin.getConfig().getString("display.star", "★")
                + d.level + " <white>" + d.name));
        double need = lm.xpNeeded(d.level);
        if (need <= 0) s.sendMessage(Util.msg("<gold>MAX LEVEL"));
        else s.sendMessage(Util.msg("<gray>XP: <white>" + (int) d.xp + "<gray>/<white>" + (int) need + " " + Util.bar(d.xp, need, 20)));
    }

    // ---------------------------------------------------------------- /sl

    private LevelManager.Data target(CommandSender s, String name) {
        LevelManager.Data d = plugin.levels().findByName(name);
        if (d == null) s.sendMessage(Util.msg("<red>No player named <white>" + name + "<red> has joined yet."));
        return d;
    }

    private void sl(CommandSender s, String[] a) {
        LevelManager lm = plugin.levels();
        if (a.length == 0) {
            s.sendMessage(Util.msg("<gold>StarLevels admin"));
            s.sendMessage(Util.mm("<gray>/sl gui <dark_gray>- open the player GUI"));
            s.sendMessage(Util.mm("<gray>/sl set|add|remove <player> <levels>"));
            s.sendMessage(Util.mm("<gray>/sl addxp <player> <xp>"));
            s.sendMessage(Util.mm("<gray>/sl color <player> <color|#hex|reset>"));
            s.sendMessage(Util.mm("<gray>/sl reset <player>   /sl reload"));
            return;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) { plugin.reload(); s.sendMessage(Util.msg("<green>Reloaded config.")); return; }
        if (sub.equals("gui")) {
            if (s instanceof Player p) plugin.guis().openLevels(p, 0); else s.sendMessage("Players only.");
            return;
        }
        if (a.length < 2) { s.sendMessage(Util.msg("<red>Usage: /sl " + sub + " <player> ...")); return; }
        LevelManager.Data d = target(s, a[1]);
        if (d == null) return;

        switch (sub) {
            case "set", "add", "remove" -> {
                if (a.length < 3) { s.sendMessage(Util.msg("<red>Usage: /sl " + sub + " <player> <levels>")); return; }
                int n = Integer.parseInt(a[2]);
                int newLevel = sub.equals("set") ? n : sub.equals("add") ? d.level + n : d.level - n;
                lm.setLevel(d.id, newLevel);
                s.sendMessage(Util.msg("<white>" + d.name + " <gray>is now level <white>" + d.level));
            }
            case "addxp" -> {
                if (a.length < 3) { s.sendMessage(Util.msg("<red>Usage: /sl addxp <player> <xp>")); return; }
                int gained = lm.addXp(d.id, Double.parseDouble(a[2]));
                s.sendMessage(Util.msg("<white>" + d.name + " <gray>is level <white>" + d.level + (gained > 0 ? " <green>(+" + gained + ")" : "")));
            }
            case "reset" -> { lm.setLevel(d.id, 1); s.sendMessage(Util.msg("<white>" + d.name + " <gray>reset to level 1.")); }
            case "color" -> {
                if (a.length < 3) { s.sendMessage(Util.msg("<red>Usage: /sl color <player> <color|#hex|reset>")); return; }
                if (a[2].equalsIgnoreCase("reset")) {
                    lm.setColor(d.id, "");
                    s.sendMessage(Util.msg("<gray>Star color reset to tier default."));
                    return;
                }
                TextColor c = Util.parseColor(a[2]);
                if (c == null) { s.sendMessage(Util.msg("<red>Unknown color. Use a name like gold/aqua or a hex like #ff55ff.")); return; }
                lm.setColor(d.id, a[2].toLowerCase(Locale.ROOT));
                s.sendMessage(Util.msg("<gray>Star color set to " + Util.tag(c) + "★"));
            }
            default -> s.sendMessage(Util.msg("<red>Unknown subcommand."));
        }
    }

    // ---------------------------------------------------------------- /shopadmin

    private void shopAdmin(CommandSender s, String[] a) {
        ShopManager shop = plugin.shop();
        if (a.length == 0) {
            s.sendMessage(Util.msg("<gold>Shop admin"));
            s.sendMessage(Util.mm("<gray>/shopadmin edit <dark_gray>- open the shop editor GUI"));
            s.sendMessage(Util.mm("<gray>/shopadmin add <price> <level> <dark_gray>- sell the item in your hand"));
            s.sendMessage(Util.mm("<gray>/shopadmin remove|list"));
            s.sendMessage(Util.mm("<gray>/shopadmin setprice|setlevel <id> <value>"));
            return;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "edit" -> { if (s instanceof Player p) plugin.guis().openShop(p, 0, true); }
            case "add" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); return; }
                if (a.length < 3) { s.sendMessage(Util.msg("<red>Usage: /shopadmin add <price> <level>")); return; }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) { s.sendMessage(Util.msg("<red>Hold the item you want to sell.")); return; }
                ShopManager.ShopItem si = shop.add(hand, Double.parseDouble(a[1]), Math.max(1, Integer.parseInt(a[2])));
                s.sendMessage(Util.msg("<green>Added to the shop with id <white>" + si.id));
            }
            case "remove" -> {
                if (a.length < 2) { s.sendMessage(Util.msg("<red>Usage: /shopadmin remove <id>")); return; }
                s.sendMessage(Util.msg(shop.remove(a[1]) ? "<green>Removed." : "<red>No item with that id."));
            }
            case "setprice", "setlevel" -> {
                if (a.length < 3) { s.sendMessage(Util.msg("<red>Usage: /shopadmin " + a[0] + " <id> <value>")); return; }
                ShopManager.ShopItem si = shop.get(a[1]);
                if (si == null) { s.sendMessage(Util.msg("<red>No item with that id.")); return; }
                if (a[0].equalsIgnoreCase("setprice")) si.price = Math.max(0, Double.parseDouble(a[2]));
                else si.level = Math.max(1, Math.min(plugin.levels().maxLevel(), Integer.parseInt(a[2])));
                shop.save();
                s.sendMessage(Util.msg("<green>Updated."));
            }
            case "list" -> {
                for (ShopManager.ShopItem si : shop.items()) {
                    s.sendMessage(Util.mm("<gray>#" + si.id + " <white>" + si.item.getAmount() + "x " + si.item.getType().name()
                            + " <gray>- <gold>" + plugin.currency().format(si.price) + " <gray>- level <white>" + si.level));
                }
            }
            default -> s.sendMessage(Util.msg("<red>Unknown subcommand."));
        }
    }

    // ---------------------------------------------------------------- tab complete

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        String cur = a[a.length - 1].toLowerCase(Locale.ROOT);
        if (name.equals("level") && a.length == 1) players(out);
        else if (name.equals("sl")) {
            if (a.length == 1) out.addAll(List.of("gui", "set", "add", "remove", "addxp", "color", "reset", "reload"));
            else if (a.length == 2) players(out);
            else if (a.length == 3 && a[0].equalsIgnoreCase("color")) {
                out.addAll(List.of("reset", "gold", "aqua", "red", "green", "blue", "yellow", "white", "light_purple", "dark_purple", "#ff55ff"));
            }
        } else if (name.equals("shopadmin")) {
            if (a.length == 1) out.addAll(List.of("edit", "add", "remove", "setprice", "setlevel", "list"));
            else if (a.length == 2 && List.of("remove", "setprice", "setlevel").contains(a[0].toLowerCase(Locale.ROOT))) {
                for (ShopManager.ShopItem si : plugin.shop().items()) out.add(si.id);
            }
        }
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }

    private void players(List<String> out) {
        for (LevelManager.Data d : plugin.levels().all()) out.add(d.name);
    }
}
