package io.github.poelsk.authreloaded.listeners;

import io.github.poelsk.authreloaded.AuthReloaded;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerConnectionListener implements Listener {

    private final AuthReloaded plugin;

    public PlayerConnectionListener(AuthReloaded plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (Bukkit.getOnlineMode()) {
            return;
        }

        UUID playerUUID = event.getUniqueId();
        boolean isRegistered = plugin.getPlayerDataDAO().isPlayerRegistered(playerUUID);
        plugin.getPlayerStatusManager().setPendingStatus(playerUUID, isRegistered);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (Bukkit.getOnlineMode()) {
            plugin.getSessionManager().createSession(player);
            return;
        }

        Boolean isRegistered = plugin.getPlayerStatusManager().getPendingStatus(player.getUniqueId());
        plugin.getPlayerStatusManager().removePendingStatus(player.getUniqueId());

        if (isRegistered != null) {
            if (isRegistered) {
                plugin.getMessageManager().sendMessage(player, "login_prompt");
            } else {
                plugin.getMessageManager().sendMessage(player, "register_prompt");
            }
        } else {
            player.kickPlayer(plugin.getMessageManager().getRawMessage("error_login_check"));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getSessionManager().endSession(event.getPlayer());
        plugin.getPlayerStatusManager().removePendingStatus(event.getPlayer().getUniqueId());
    }
}