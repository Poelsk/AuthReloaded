package io.github.poelsk.authreloaded.model;

import java.sql.Timestamp;
import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private final String username;
    private final String passwordHash;
    private final String lastLoginIp;
    private final Timestamp registrationDate;

    public PlayerData(UUID uuid, String username, String passwordHash, String lastLoginIp, Timestamp registrationDate) {
        this.uuid = uuid;
        this.username = username;
        this.passwordHash = passwordHash;
        this.lastLoginIp = lastLoginIp;
        this.registrationDate = registrationDate;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public Timestamp getRegistrationDate() {
        return registrationDate;
    }
}