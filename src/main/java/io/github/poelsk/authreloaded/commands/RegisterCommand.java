package io.github.poelsk.authreloaded.commands;

import io.github.poelsk.authreloaded.AuthReloaded;
import io.github.poelsk.authreloaded.auth.AuthenticationService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RegisterCommand implements CommandExecutor {

    private final AuthReloaded plugin;

    public RegisterCommand(AuthReloaded plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().sendMessage(sender, "player_only_command");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 2) {
            plugin.getMessageManager().sendMessage(player, "register_usage");
            return true;
        }

        if (!args[0].equals(args[1])) {
            plugin.getMessageManager().sendMessage(player, "passwords_do_not_match");
            return true;
        }

        String password = args[0];

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthenticationService.AuthResult result = plugin.getAuthenticationService().registerPlayer(player, password);
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getAuthenticationService().handleAuthResult(player, result, "register");
            });
        });

        return true;
    }
}