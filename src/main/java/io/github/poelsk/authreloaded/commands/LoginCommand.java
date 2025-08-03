package io.github.poelsk.authreloaded.commands;

import io.github.poelsk.authreloaded.AuthReloaded;
import io.github.poelsk.authreloaded.auth.AuthenticationService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LoginCommand implements CommandExecutor {

    private final AuthReloaded plugin;

    public LoginCommand(AuthReloaded plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().sendMessage(sender, "player_only_command");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 1) {
            plugin.getMessageManager().sendMessage(player, "login_usage");
            return true;
        }

        String password = args[0];

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthenticationService.AuthResult result = plugin.getAuthenticationService().loginPlayer(player, password);
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getAuthenticationService().handleAuthResult(player, result, "login");
            });
        });

        return true;
    }
}