package io.github.poelsk.authreloaded.database;

import com.zaxxer.hikari.HikariDataSource;
import io.github.poelsk.authreloaded.AuthReloaded;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final AuthReloaded plugin;
    private HikariDataSource dataSource;
    private IDataSource dataSourceProvider;

    public DatabaseManager(AuthReloaded plugin) {
        this.plugin = plugin;
    }

    public void initializeDatabase() {
        FileConfiguration config = plugin.getConfig();
        String dbType = config.getString("database.type", "sqlite").toLowerCase();

        switch (dbType) {
            case "mysql":
                dataSourceProvider = new MySQLDataSource(plugin);
                break;
            case "h2":
                dataSourceProvider = new H2DataSource(plugin);
                break;
            case "sqlite":
            default:
                dataSourceProvider = new SQLiteDataSource(plugin);
                break;
        }

        this.dataSource = dataSourceProvider.getDataSource();
        createTables();
    }

    private void createTables() {
        String sql = "CREATE TABLE IF NOT EXISTS auth_players (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "username VARCHAR(16) NOT NULL, " +
                "password_hash VARCHAR(60) NOT NULL, " +
                "last_login_ip VARCHAR(45), " +
                "registration_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ");";

        if (plugin.getConfig().getString("database.type", "sqlite").equalsIgnoreCase("sqlite")) {
            sql = "CREATE TABLE IF NOT EXISTS auth_players (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "uuid TEXT(36) NOT NULL UNIQUE, " +
                    "username TEXT(16) NOT NULL, " +
                    "password_hash TEXT(60) NOT NULL, " +
                    "last_login_ip TEXT(45), " +
                    "registration_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ");";
        }


        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create database tables!");
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource!= null &&!dataSource.isClosed()) {
            dataSource.close();
        }
    }
}