package dev.starlevels;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Only ever loaded when Vault is present. */
final class VaultCurrency implements Currency {
    private final Economy eco;

    private VaultCurrency(Economy eco) { this.eco = eco; }

    static VaultCurrency create(StarLevelsPlugin plugin) {
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : new VaultCurrency(rsp.getProvider());
    }

    @Override public double balance(OfflinePlayer p) { return eco.getBalance(p); }
    @Override public boolean has(OfflinePlayer p, double a) { return eco.has(p, a); }
    @Override public void deposit(OfflinePlayer p, double a) { eco.depositPlayer(p, a); }
    @Override public boolean withdraw(OfflinePlayer p, double a) { return eco.withdrawPlayer(p, a).transactionSuccess(); }
    @Override public String format(double a) { return eco.format(a); }
}
