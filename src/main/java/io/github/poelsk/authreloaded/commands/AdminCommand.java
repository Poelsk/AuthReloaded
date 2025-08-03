package io.github.poelsk.authreloaded.commands;

import io.github.poelsk.authreloaded.AuthReloaded;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class AdminCommand implements CommandExecutor {

    private final AuthReloaded plugin;

    public AdminCommand(AuthReloaded plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("authreloaded.admin")) {
            plugin.getMessageManager().sendMessage(sender, "no_permission");
            return true;
        }

        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            plugin.reloadConfig();
            plugin.getMessageManager().loadMessages();
            plugin.getMessageManager().sendMessage(sender, "config_reloaded");
        } else {
            plugin.getMessageManager().sendMessage(sender, "admin_usage");
        }

        return true;
    }
}