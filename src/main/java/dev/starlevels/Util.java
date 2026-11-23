package dev.starlevels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Locale;

final class Util {
    static final MiniMessage MM = MiniMessage.miniMessage();

    private Util() {}

    /** MiniMessage without the default italic (for item names / lore). */
    static Component mm(String s) {
        return MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    /** Chat message with the plugin prefix. */
    static Component msg(String s) {
        return MM.deserialize("<gray>[<gold>★<gray>] <white>" + s);
    }

    static TextColor parseColor(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (s.startsWith("#")) return TextColor.fromHexString(s);
        return NamedTextColor.NAMES.value(s);
    }

    static String tag(TextColor c) {
        return "<" + c.asHexString() + ">";
    }

    static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format(Locale.US, "%.2f", d);
    }

    static String bar(double cur, double max, int len) {
        int filled = max <= 0 ? len : (int) Math.max(0, Math.min(len, Math.round(cur / max * len)));
        return "<green>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(len - filled);
    }

    static ItemStack item(Material m, String name, String... lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.displayName(mm(name));
        if (lore.length > 0) meta.lore(Arrays.stream(lore).map(Util::mm).toList());
        i.setItemMeta(meta);
        return i;
    }

    static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ");
    }
}
