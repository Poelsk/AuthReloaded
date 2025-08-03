package io.github.poelsk.authreloaded.commands;

import io.github.poelsk.authreloaded.AuthReloaded;
import io.github.poelsk.authreloaded.auth.PremiumVerificationService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PremiumCommand implements CommandExecutor {

    private final AuthReloaded plugin;

    public PremiumCommand(AuthReloaded plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().sendMessage(sender, "player_only_command");
            return true;
        }

        Player player = (Player) sender;

        // Verificar si ya está logueado
        if (plugin.getSessionManager().isLoggedIn(player)) {
            plugin.getMessageManager().sendMessage(player, "already_logged_in");
            return true;
        }

        // Verificar si ya es premium
        if (plugin.getPremiumManager().isPremium(player.getUniqueId())) {
            plugin.getMessageManager().sendMessage(player, "already_premium");
            return true;
        }

        plugin.getMessageManager().sendMessage(player, "premium_verification_start");

        // Verificación asíncrona con Mojang
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PremiumVerificationService.VerificationResult result =
                    plugin.getPremiumVerificationService().verifyPremium(player);

            // Volver al hilo principal para procesar el resultado
            Bukkit.getScheduler().runTask(plugin, () -> {
                handleVerificationResult(player, result);
            });
        });

        return true;
    }

    private void handleVerificationResult(Player player, PremiumVerificationService.VerificationResult result) {
        switch (result) {
            case VERIFIED_PREMIUM:
                plugin.getPremiumManager().setPremium(player.getUniqueId(), true);
                plugin.getSessionManager().createSession(player);
                plugin.getMessageManager().sendMessage(player, "premium_verified_success");
                break;

            case NOT_PREMIUM:
                plugin.getMessageManager().sendMessage(player, "premium_verification_failed");
                break;

            case USERNAME_MISMATCH:
                plugin.getMessageManager().sendMessage(player, "premium_username_mismatch");
                break;

            case VERIFICATION_ERROR:
                plugin.getMessageManager().sendMessage(player, "premium_verification_error");
                break;

            case RATE_LIMITED:
                plugin.getMessageManager().sendMessage(player, "premium_verification_rate_limit");
                break;
        }
    }
}