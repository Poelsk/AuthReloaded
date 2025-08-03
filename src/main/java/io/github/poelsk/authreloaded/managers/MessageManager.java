package io.github.poelsk.authreloaded.managers;

import io.github.poelsk.authreloaded.AuthReloaded;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MessageManager {

    public final AuthReloaded plugin;
    private FileConfiguration langConfig;

    public MessageManager(AuthReloaded plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        String lang = plugin.getConfig().getString("messages.language", "en-US");
        File langFile = new File(plugin.getDataFolder(), lang + ".yml");

        if (!langFile.exists()) {
            plugin.saveResource("lang/" + lang + ".yml", false);
            File sourceFile = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
            if (sourceFile.exists()) {
                sourceFile.renameTo(langFile);
                File langDir = new File(plugin.getDataFolder(), "lang");
                if (langDir.exists() && langDir.isDirectory() && langDir.list().length == 0) {
                    langDir.delete();
                }
            }
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        try (InputStream defLangStream = plugin.getResource("lang/" + lang + ".yml")) {
            if (defLangStream != null) {
                langConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defLangStream, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load default language file: " + e.getMessage());
        }
    }

    public String getRawMessage(String key) {
        return langConfig.getString(key, "Message not found: " + key);
    }

    public void sendMessage(CommandSender sender, String key, String... args) {
        String message = getRawMessage(key);
        String prefix = getRawMessage("prefix");

        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", args[i]);
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + message));
    }
}