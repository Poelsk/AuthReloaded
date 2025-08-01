package io.github.poelsk.authreloaded.database;

import io.github.poelsk.authreloaded.model.PlayerData;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;

public class PlayerDataDAO {

    private final DatabaseManager dbManager;

    public PlayerDataDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public Optional<PlayerData> getPlayerByUUID(UUID uuid) {
        String sql = "SELECT * FROM auth_players WHERE uuid =?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                PlayerData data = new PlayerData(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("last_login_ip"),
                        rs.getTimestamp("registration_date")
                );
                return Optional.of(data);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public void createPlayer(PlayerData data) {
        String sql = "INSERT INTO auth_players(uuid, username, password_hash, last_login_ip, registration_date) VALUES(?,?,?,?,?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, data.getUuid().toString());
            pstmt.setString(2, data.getUsername());
            pstmt.setString(3, data.getPasswordHash());
            pstmt.setString(4, data.getLastLoginIp());
            pstmt.setTimestamp(5, data.getRegistrationDate());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateLastLoginIp(UUID uuid, String ip) {
        String sql = "UPDATE auth_players SET last_login_ip =? WHERE uuid =?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ip);
            pstmt.setString(2, uuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isPlayerRegistered(UUID uuid) {
        return getPlayerByUUID(uuid).isPresent();
    }
}