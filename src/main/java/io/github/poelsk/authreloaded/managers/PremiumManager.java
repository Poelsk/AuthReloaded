package io.github.poelsk.authreloaded.managers;

import io.github.poelsk.authreloaded.AuthReloaded;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PremiumManager {

    private final AuthReloaded plugin;
    private final Map<UUID, Boolean> premiumCache = new ConcurrentHashMap<>();

    public PremiumManager(AuthReloaded plugin) {
        this.plugin = plugin;
        createPremiumTable();
    }

    private void createPremiumTable() {
        String dbType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        String sql;

        switch (dbType) {
            case "sqlite":
                sql = "CREATE TABLE IF NOT EXISTS auth_premium (" +
                        "uuid TEXT(36) PRIMARY KEY, " +
                        "is_premium INTEGER NOT NULL DEFAULT 0, " +
                        "verified_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "last_verification TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                        ");";
                break;
            case "mysql":
                sql = "CREATE TABLE IF NOT EXISTS auth_premium (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "is_premium BOOLEAN NOT NULL DEFAULT FALSE, " +
                        "verified_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "last_verification TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                        ");";
                break;
            case "h2":
                sql = "CREATE TABLE IF NOT EXISTS auth_premium (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "is_premium BOOLEAN NOT NULL DEFAULT FALSE, " +
                        "verified_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "last_verification TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                        ");";
                break;
            default:
                throw new IllegalStateException("Unsupported database type: " + dbType);
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create premium table!");
            e.printStackTrace();
        }
    }

    public boolean isPremium(UUID playerUUID) {
        Boolean cached = premiumCache.get(playerUUID);
        if (cached != null) {
            return cached;
        }

        String sql = "SELECT is_premium FROM auth_premium WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                boolean isPremium;
                String dbType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
                if ("sqlite".equals(dbType)) {
                    isPremium = rs.getInt("is_premium") == 1;
                } else {
                    isPremium = rs.getBoolean("is_premium");
                }
                premiumCache.put(playerUUID, isPremium);
                return isPremium;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error checking premium status for " + playerUUID);
            e.printStackTrace();
        }

        premiumCache.put(playerUUID, false);
        return false;
    }

    public void setPremium(UUID playerUUID, boolean isPremium) {
        String dbType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        String sql;

        switch (dbType) {
            case "sqlite":
                sql = "INSERT OR REPLACE INTO auth_premium (uuid, is_premium, verified_date, last_verification) " +
                        "VALUES (?, ?, datetime('now'), datetime('now'))";
                break;
            case "mysql":
                sql = "INSERT INTO auth_premium (uuid, is_premium, verified_date, last_verification) " +
                        "VALUES (?, ?, NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE is_premium = VALUES(is_premium), last_verification = NOW()";
                break;
            case "h2":
                sql = "MERGE INTO auth_premium (uuid, is_premium, verified_date, last_verification) " +
                        "VALUES (?, ?, NOW(), NOW())";
                break;
            default:
                throw new IllegalStateException("Unsupported database type: " + dbType);
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUUID.toString());

            if ("sqlite".equals(dbType)) {
                stmt.setInt(2, isPremium ? 1 : 0);
            } else {
                stmt.setBoolean(2, isPremium);
            }

            stmt.executeUpdate();
            premiumCache.put(playerUUID, isPremium);

            plugin.getLogger().info("Player " + playerUUID + " premium status set to: " + isPremium);

        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting premium status for " + playerUUID + ": " + e.getMessage());
            throw new RuntimeException("Database operation failed", e);
        }
    }

    public void removePremiumStatus(UUID playerUUID) {
        String sql = "DELETE FROM auth_premium WHERE uuid = ?";

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUUID.toString());
            stmt.executeUpdate();

            premiumCache.remove(playerUUID);

        } catch (SQLException e) {
            plugin.getLogger().warning("Error removing premium status for " + playerUUID);
            e.printStackTrace();
        }
    }

    public void clearCache(UUID playerUUID) {
        premiumCache.remove(playerUUID);
    }

    public void clearAllCache() {
        premiumCache.clear();
    }
}