package dev.starlevels;

import org.bukkit.OfflinePlayer;

public interface Currency {
    double balance(OfflinePlayer p);
    boolean has(OfflinePlayer p, double amount);
    void deposit(OfflinePlayer p, double amount);
    boolean withdraw(OfflinePlayer p, double amount);
    String format(double amount);
}
