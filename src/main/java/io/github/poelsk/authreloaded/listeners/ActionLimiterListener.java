package io.github.poelsk.authreloaded.listeners;

import io.github.poelsk.authreloaded.AuthReloaded;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;

public class ActionLimiterListener implements Listener {

    private final AuthReloaded plugin;

    public ActionLimiterListener(AuthReloaded plugin) {
        this.plugin = plugin;
    }

    private boolean isAllowed(Player player) {
        if (Bukkit.getOnlineMode()) {
            return true;
        }

        return plugin.getSessionManager().isLoggedIn(player);
    }

    private void cancelAndNotify(PlayerEvent event) {
        if (event instanceof PlayerMoveEvent) {
            PlayerMoveEvent moveEvent = (PlayerMoveEvent) event;
            if (moveEvent.getTo() != null &&
                    (moveEvent.getFrom().getBlockX() != moveEvent.getTo().getBlockX() ||
                            moveEvent.getFrom().getBlockY() != moveEvent.getTo().getBlockY() ||
                            moveEvent.getFrom().getBlockZ() != moveEvent.getTo().getBlockZ())) {
                moveEvent.setCancelled(true);
                plugin.getMessageManager().sendMessage(event.getPlayer(), "login_or_register_prompt");
            }
        } else if (event instanceof Cancellable) {
            ((Cancellable) event).setCancelled(true);
            plugin.getMessageManager().sendMessage(event.getPlayer(), "login_or_register_prompt");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isAllowed(event.getPlayer())) {
            cancelAndNotify(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!isAllowed(event.getPlayer())) {
            event.setCancelled(true);
            plugin.getMessageManager().sendMessage(event.getPlayer(), "login_or_register_prompt");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!isAllowed(event.getPlayer())) {
            String command = event.getMessage().split(" ")[0].toLowerCase();
            if (!command.equals("/login") && !command.equals("/register")) {
                event.setCancelled(true);
                plugin.getMessageManager().sendMessage(event.getPlayer(), "login_or_register_prompt");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isAllowed(event.getPlayer())) {
            cancelAndNotify(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isAllowed(event.getPlayer())) {
            event.setCancelled(true);
            plugin.getMessageManager().sendMessage(event.getPlayer(), "login_or_register_prompt");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isAllowed(event.getPlayer())) {
            event.setCancelled(true);
            plugin.getMessageManager().sendMessage(event.getPlayer(), "login_or_register_prompt");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDropItem(PlayerDropItemEvent event) {
        if (!isAllowed(event.getPlayer())) {
            cancelAndNotify(event);
        }
    }
}