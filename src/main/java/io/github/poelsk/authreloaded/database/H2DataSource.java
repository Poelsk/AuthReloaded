package io.github.poelsk.authreloaded.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.poelsk.authreloaded.AuthReloaded;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;

public class H2DataSource implements IDataSource {

    private final AuthReloaded plugin;

    public H2DataSource(AuthReloaded plugin) {
        this.plugin = plugin;
    }

    @Override
    public HikariDataSource getDataSource() {
        HikariConfig config = new HikariConfig();
        ConfigurationSection dbConfig = plugin.getConfig().getConfigurationSection("database.h2");

        String dbPath = dbConfig.getString("file", "database").replace("{pluginDir}", plugin.getDataFolder().getAbsolutePath());
        File dbFile = new File(dbPath);
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }

        config.setJdbcUrl("jdbc:h2:" + dbFile.getAbsolutePath());
        config.setUsername(dbConfig.getString("username", "sa"));
        config.setPassword(dbConfig.getString("password", ""));

        ConfigurationSection poolSettings = plugin.getConfig().getConfigurationSection("database.pool-settings");
        if (poolSettings!= null) {
            config.setMaximumPoolSize(poolSettings.getInt("maximum-pool-size", 10));
            config.setConnectionTimeout(poolSettings.getInt("connection-timeout", 30000));
            config.setIdleTimeout(poolSettings.getInt("idle-timeout", 600000));
            config.setMaxLifetime(poolSettings.getInt("max-lifetime", 1800000));
        }

        return new HikariDataSource(config);
    }
}