package io.github.poelsk.authreloaded.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.poelsk.authreloaded.AuthReloaded;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PremiumVerificationService {

    private final AuthReloaded plugin;
    private final Map<UUID, Long> lastVerificationAttempt = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> verificationCache = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> cacheCleanupTasks = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerSkinHashes = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "AuthReloaded-Premium-Cache");
        t.setDaemon(true);
        return t;
    });

    private static final long RATE_LIMIT_MS = 30000;
    private static final long CACHE_DURATION_MS = 300000;
    private static final long SKIN_CHECK_DELAY_MS = 5000; // 5 segundos para que cargue la skin

    public enum VerificationResult {
        VERIFIED_PREMIUM,
        NOT_PREMIUM,
        USERNAME_MISMATCH,
        VERIFICATION_ERROR,
        RATE_LIMITED,
        SKIN_VERIFICATION_PENDING,
        SKIN_MISMATCH
    }

    public PremiumVerificationService(AuthReloaded plugin) {
        this.plugin = plugin;
    }

    public VerificationResult verifyPremium(Player player) {
        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName();

        plugin.getLogger().info("[PremiumVerification] Starting premium verification for player: " + playerName + " (UUID: " + playerUUID + ")");
        plugin.getLogger().info("[PremiumVerification] Server online mode: " + Bukkit.getOnlineMode());

        Long lastAttempt = lastVerificationAttempt.get(playerUUID);
        if (lastAttempt != null && System.currentTimeMillis() - lastAttempt < RATE_LIMIT_MS) {
            plugin.getLogger().info("[PremiumVerification] Rate limited for player: " + playerName);
            return VerificationResult.RATE_LIMITED;
        }

        Boolean cachedResult = verificationCache.get(playerUUID);
        if (cachedResult != null) {
            plugin.getLogger().info("[PremiumVerification] Using cached result for player: " + playerName + " - " + (cachedResult ? "PREMIUM" : "NOT_PREMIUM"));
            return cachedResult ? VerificationResult.VERIFIED_PREMIUM : VerificationResult.NOT_PREMIUM;
        }

        lastVerificationAttempt.put(playerUUID, System.currentTimeMillis());

        try {
            plugin.getLogger().info("[PremiumVerification] Step 1: Checking Mojang API for username: " + playerName);
            String mojangUUID = getUUIDFromUsername(playerName);
            if (mojangUUID == null) {
                plugin.getLogger().info("[PremiumVerification] Username '" + playerName + "' not found in Mojang API - not premium");
                cacheResult(playerUUID, false);
                return VerificationResult.NOT_PREMIUM;
            }

            plugin.getLogger().info("[PremiumVerification] Mojang returned UUID: " + mojangUUID + " for username: " + playerName);

            plugin.getLogger().info("[PremiumVerification] Step 2: Verifying profile and getting skin data");
            String expectedSkinHash = getProfileSkinHash(mojangUUID);
            if (expectedSkinHash == null) {
                plugin.getLogger().info("[PremiumVerification] Could not get skin data for premium account - verification failed");
                cacheResult(playerUUID, false);
                return VerificationResult.NOT_PREMIUM;
            }

            plugin.getLogger().info("[PremiumVerification] Expected skin hash from Mojang: " + expectedSkinHash);

            if (Bukkit.getOnlineMode()) {
                String normalizedPlayerUUID = playerUUID.toString().replace("-", "");
                plugin.getLogger().info("[PremiumVerification] Player's actual UUID (normalized): " + normalizedPlayerUUID);

                if (!mojangUUID.equalsIgnoreCase(normalizedPlayerUUID)) {
                    plugin.getLogger().info("[PremiumVerification] UUID mismatch! Expected: " + normalizedPlayerUUID + ", Got: " + mojangUUID);
                    cacheResult(playerUUID, false);
                    return VerificationResult.USERNAME_MISMATCH;
                }
            }

            plugin.getLogger().info("[PremiumVerification] Step 3: Scheduling skin verification");
            schedulePlayerSkinVerification(player, expectedSkinHash);

            return VerificationResult.SKIN_VERIFICATION_PENDING;

        } catch (Exception e) {
            plugin.getLogger().severe("[PremiumVerification] Error during premium verification for " + playerName + ": " + e.getMessage());
            e.printStackTrace();
            return VerificationResult.VERIFICATION_ERROR;
        }
    }

    private void schedulePlayerSkinVerification(Player player, String expectedSkinHash) {
        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName();

        playerSkinHashes.put(playerUUID, expectedSkinHash);

        Bukkit.getScheduler().runTaskLaterAsynchronously(
                plugin,
                () -> {
                    if (!player.isOnline()) {
                        playerSkinHashes.remove(playerUUID);
                        return;
                    }

                    try {
                        String actualSkinHash = getPlayerSkinHash(player);
                        plugin.getLogger().info("[PremiumVerification] Player " + playerName + " actual skin hash: " + actualSkinHash);
                        plugin.getLogger().info("[PremiumVerification] Expected skin hash: " + expectedSkinHash);

                        if (actualSkinHash != null && actualSkinHash.equals(expectedSkinHash)) {
                            plugin.getLogger().info("[PremiumVerification] Skin verification SUCCESS for " + playerName);
                            cacheResult(playerUUID, true);

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (player.isOnline()) {
                                    handleSuccessfulVerification(player);
                                }
                            });
                        } else {
                            plugin.getLogger().info("[PremiumVerification] Skin verification FAILED for " + playerName + " - skin mismatch");
                            cacheResult(playerUUID, false);

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (player.isOnline()) {
                                    handleFailedVerification(player, VerificationResult.SKIN_MISMATCH);
                                }
                            });
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("[PremiumVerification] Error during skin verification for " + playerName + ": " + e.getMessage());
                        cacheResult(playerUUID, false);
                    } finally {
                        playerSkinHashes.remove(playerUUID);
                    }
                },
                SKIN_CHECK_DELAY_MS / 50
        );
    }

    private String getPlayerSkinHash(Player player) {
        try {
            var profile = player.getPlayerProfile();
            var properties = profile.getProperties();

            for (var property : properties) {
                if ("textures".equals(property.getName())) {
                    String value = property.getValue();
                    if (value != null) {
                        return hashString(value);
                    }
                }
            }

            plugin.getLogger().info("[PremiumVerification] No texture property found for player: " + player.getName());
            return null;
        } catch (Exception e) {
            plugin.getLogger().severe("[PremiumVerification] Error getting player skin hash: " + e.getMessage());
            return null;
        }
    }

    private String getProfileSkinHash(String uuid) throws IOException {
        URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "AuthReloaded-Plugin/1.0");

        try {
            int responseCode = connection.getResponseCode();
            plugin.getLogger().info("[PremiumVerification] Session server response code: " + responseCode + " for UUID: " + uuid);

            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    String responseBody = response.toString();
                    plugin.getLogger().info("[PremiumVerification] Session server response: " + responseBody);

                    JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                    if (jsonResponse.has("properties")) {
                        JsonArray properties = jsonResponse.getAsJsonArray("properties");
                        for (JsonElement prop : properties) {
                            JsonObject property = prop.getAsJsonObject();
                            if ("textures".equals(property.get("name").getAsString())) {
                                String value = property.get("value").getAsString();
                                return hashString(value);
                            }
                        }
                    }

                    plugin.getLogger().info("[PremiumVerification] No textures property found in profile");
                    return null;
                }
            } else if (responseCode == 429) {
                throw new IOException("Session server rate limited");
            }
            return null;
        } finally {
            connection.disconnect();
        }
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            plugin.getLogger().severe("[PremiumVerification] Error hashing string: " + e.getMessage());
            return null;
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
            plugin.getLogger().info("[PremiumVerification] Mojang API response code: " + responseCode + " for username: " + username);

            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    String responseBody = response.toString();
                    plugin.getLogger().info("[PremiumVerification] Mojang API response: " + responseBody);

                    JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                    String uuid = jsonResponse.get("id").getAsString();
                    plugin.getLogger().info("[PremiumVerification] Extracted UUID from Mojang: " + uuid);
                    return uuid;
                }
            } else if (responseCode == 204) {
                plugin.getLogger().info("[PremiumVerification] Mojang API returned 204 - username not found: " + username);
                return null;
            } else if (responseCode == 429) {
                throw new IOException("Mojang API rate limited");
            } else {
                throw new IOException("Mojang API returned code: " + responseCode);
            }
        } finally {
            connection.disconnect();
        }
    }

    private void handleSuccessfulVerification(Player player) {
        plugin.getPremiumManager().setPremium(player.getUniqueId(), true);

        if (!Bukkit.getOnlineMode()) {
            plugin.getSessionManager().createSession(player);
        }

        plugin.getMessageManager().sendMessage(player, "premium_verified_success");
        plugin.getLogger().info("Player " + player.getName() + " has been verified as premium with skin verification.");
    }

    private void handleFailedVerification(Player player, VerificationResult result) {
        switch (result) {
            case SKIN_MISMATCH:
                plugin.getMessageManager().sendMessage(player, "premium_skin_mismatch");
                break;
            case NOT_PREMIUM:
                plugin.getMessageManager().sendMessage(player, "premium_verification_failed");
                break;
            case USERNAME_MISMATCH:
                plugin.getMessageManager().sendMessage(player, "premium_username_mismatch");
                break;
            case VERIFICATION_ERROR:
                plugin.getMessageManager().sendMessage(player, "premium_verification_error");
                break;
            default:
                plugin.getMessageManager().sendMessage(player, "premium_verification_error");
                break;
        }
    }

    public boolean isPremiumPlayer(String playerName) {
        try {
            String mojangUUID = getUUIDFromUsername(playerName);
            if (mojangUUID == null) {
                return false;
            }

            String skinHash = getProfileSkinHash(mojangUUID);
            return skinHash != null;
        } catch (Exception e) {
            plugin.getLogger().severe("[PremiumVerification] Error checking if player is premium: " + e.getMessage());
            return false;
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
        playerSkinHashes.remove(playerUUID);

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
        playerSkinHashes.clear();

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