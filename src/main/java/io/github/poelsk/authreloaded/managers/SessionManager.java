package io.github.poelsk.authreloaded.managers;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final Map<UUID, PlayerSession> activeSessions = new ConcurrentHashMap<>();

    public void createSession(Player player) {
        activeSessions.put(player.getUniqueId(), new PlayerSession(player.getUniqueId()));
    }

    public void endSession(Player player) {
        activeSessions.remove(player.getUniqueId());
    }

    public boolean isLoggedIn(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    private static class PlayerSession {
        private final UUID playerUUID;
        private final long loginTime;

        public PlayerSession(UUID playerUUID) {
            this.playerUUID = playerUUID;
            this.loginTime = System.currentTimeMillis();
        }
    }
}