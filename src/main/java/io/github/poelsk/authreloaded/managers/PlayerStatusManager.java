package io.github.poelsk.authreloaded.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStatusManager {

    private final Map<UUID, Boolean> pendingPlayers = new ConcurrentHashMap<>();

    public void setPendingStatus(UUID uuid, boolean isRegistered) {
        pendingPlayers.put(uuid, isRegistered);
    }

    public Boolean getPendingStatus(UUID uuid) {
        return pendingPlayers.get(uuid);
    }

    public void removePendingStatus(UUID uuid) {
        pendingPlayers.remove(uuid);
    }
}