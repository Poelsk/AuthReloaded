package io.github.poelsk.authreloaded.managers;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final Set<UUID> loggedInPlayers = ConcurrentHashMap.newKeySet();

    public void createSession(Player player) {
        loggedInPlayers.add(player.getUniqueId());
    }

    public void endSession(Player player) {
        loggedInPlayers.remove(player.getUniqueId());
    }

    public boolean isLoggedIn(Player player) {
        return loggedInPlayers.contains(player.getUniqueId());
    }

    public boolean isLoggedIn(UUID playerUUID) {
        return loggedInPlayers.contains(playerUUID);
    }

    public int getLoggedInCount() {
        return loggedInPlayers.size();
    }

    public void clearAllSessions() {
        loggedInPlayers.clear();
    }
}