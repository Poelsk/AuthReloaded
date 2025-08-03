package io.github.poelsk.authreloaded.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PremiumVerificationService {

    private final Map<UUID, Long> lastVerificationAttempt = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> verificationCache = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> cacheCleanupTasks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "AuthReloaded-Premium-Cache");
        t.setDaemon(true);
        return t;
    });

    private static final long RATE_LIMIT_MS = 30000;
    private static final long CACHE_DURATION_MS = 300000;

    public enum VerificationResult {
        VERIFIED_PREMIUM,
        NOT_PREMIUM,
        USERNAME_MISMATCH,
        VERIFICATION_ERROR,
        RATE_LIMITED
    }

    public VerificationResult verifyPremium(Player player) {
        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName();

        Long lastAttempt = lastVerificationAttempt.get(playerUUID);
        if (lastAttempt != null && System.currentTimeMillis() - lastAttempt < RATE_LIMIT_MS) {
            return VerificationResult.RATE_LIMITED;
        }

        Boolean cachedResult = verificationCache.get(playerUUID);
        if (cachedResult != null) {
            return cachedResult ? VerificationResult.VERIFIED_PREMIUM : VerificationResult.NOT_PREMIUM;
        }

        lastVerificationAttempt.put(playerUUID, System.currentTimeMillis());

        try {
            String mojangUUID = getUUIDFromUsername(playerName);
            if (mojangUUID == null) {
                cacheResult(playerUUID, false);
                return VerificationResult.NOT_PREMIUM;
            }

            String normalizedPlayerUUID = playerUUID.toString().replace("-", "");
            if (!mojangUUID.equalsIgnoreCase(normalizedPlayerUUID)) {
                cacheResult(playerUUID, false);
                return VerificationResult.USERNAME_MISMATCH;
            }

            if (verifyProfile(mojangUUID)) {
                cacheResult(playerUUID, true);
                return VerificationResult.VERIFIED_PREMIUM;
            } else {
                cacheResult(playerUUID, false);
                return VerificationResult.NOT_PREMIUM;
            }

        } catch (Exception e) {
            System.err.println("Error during premium verification for " + playerName + ": " + e.getMessage());
            return VerificationResult.VERIFICATION_ERROR;
        }
    }

    public boolean isPremiumPlayer(String playerName) {
        try {
            String mojangUUID = getUUIDFromUsername(playerName);
            if (mojangUUID == null) {
                return false;
            }

            return verifyProfile(mojangUUID);
        } catch (Exception e) {
            System.err.println("Error checking if player is premium: " + e.getMessage());
            return false;
        }
    }

    private String getUUIDFromUsername(String username) throws IOException {
        URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "AuthReloaded-Plugin/1.0");

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
                    return jsonResponse.get("id").getAsString();
                }
            } else if (responseCode == 204) {
                return null;
            } else {
                throw new IOException("Mojang API returned code: " + responseCode);
            }
        } finally {
            connection.disconnect();
        }
    }

    private boolean verifyProfile(String uuid) throws IOException {
        URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "AuthReloaded-Plugin/1.0");

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();

                    return jsonResponse.has("properties") &&
                            jsonResponse.getAsJsonArray("properties").size() > 0;
                }
            }
            return false;
        } finally {
            connection.disconnect();
        }
    }

    private void cacheResult(UUID playerUUID, boolean isPremium) {
        verificationCache.put(playerUUID, isPremium);

        ScheduledFuture<?> oldTask = cacheCleanupTasks.get(playerUUID);
        if (oldTask != null && !oldTask.isDone()) {
            oldTask.cancel(false);
        }

        ScheduledFuture<?> cleanupTask = scheduler.schedule(() -> {
            verificationCache.remove(playerUUID);
            cacheCleanupTasks.remove(playerUUID);
        }, CACHE_DURATION_MS, TimeUnit.MILLISECONDS);

        cacheCleanupTasks.put(playerUUID, cleanupTask);
    }

    public void clearCache(UUID playerUUID) {
        verificationCache.remove(playerUUID);
        lastVerificationAttempt.remove(playerUUID);

        ScheduledFuture<?> task = cacheCleanupTasks.remove(playerUUID);
        if (task != null && !task.isDone()) {
            task.cancel(false);
        }
    }

    public void shutdown() {
        cacheCleanupTasks.values().forEach(task -> {
            if (!task.isDone()) {
                task.cancel(false);
            }
        });
        cacheCleanupTasks.clear();

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}