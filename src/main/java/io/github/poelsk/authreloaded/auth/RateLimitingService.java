package io.github.poelsk.authreloaded.auth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RateLimitingService {

    private final Map<UUID, AttemptTracker> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, AttemptTracker> ipAttempts = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AuthReloaded-RateLimit-Cleanup");
        t.setDaemon(true);
        return t;
    });

    private static final int MAX_ATTEMPTS_PER_PLAYER = 5;
    private static final int MAX_ATTEMPTS_PER_IP = 15;
    private static final long COOLDOWN_MINUTES = 10;
    private static final long CLEANUP_INTERVAL_MINUTES = 5;

    public RateLimitingService() {
        cleanup.scheduleAtFixedRate(this::cleanupExpiredEntries,
                CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    public enum LimitResult {
        ALLOWED,
        PLAYER_LIMITED,
        IP_LIMITED,
        BOTH_LIMITED
    }

    public LimitResult canAttemptLogin(UUID playerUUID, String ip) {
        boolean playerLimited = isPlayerLimited(playerUUID);
        boolean ipLimited = isIPLimited(ip);

        if (playerLimited && ipLimited) {
            return LimitResult.BOTH_LIMITED;
        } else if (playerLimited) {
            return LimitResult.PLAYER_LIMITED;
        } else if (ipLimited) {
            return LimitResult.IP_LIMITED;
        } else {
            return LimitResult.ALLOWED;
        }
    }

    public void recordFailedAttempt(UUID playerUUID, String ip) {
        long currentTime = System.currentTimeMillis();

        loginAttempts.compute(playerUUID, (key, tracker) -> {
            if (tracker == null || tracker.isExpired(currentTime)) {
                return new AttemptTracker(currentTime);
            } else {
                tracker.addAttempt(currentTime);
                return tracker;
            }
        });

        // Record IP attempt
        if (ip != null && !ip.equals("unknown")) {
            ipAttempts.compute(ip, (key, tracker) -> {
                if (tracker == null || tracker.isExpired(currentTime)) {
                    return new AttemptTracker(currentTime);
                } else {
                    tracker.addAttempt(currentTime);
                    return tracker;
                }
            });
        }
    }

    public void recordSuccessfulLogin(UUID playerUUID, String ip) {
        loginAttempts.remove(playerUUID);
        if (ip != null && !ip.equals("unknown")) {
            ipAttempts.remove(ip);
        }
    }
    public long getRemainingCooldown(UUID playerUUID, String ip) {
        long playerCooldown = getPlayerCooldown(playerUUID);
        long ipCooldown = getIPCooldown(ip);
        return Math.max(playerCooldown, ipCooldown);
    }

    private boolean isPlayerLimited(UUID playerUUID) {
        AttemptTracker tracker = loginAttempts.get(playerUUID);
        return tracker != null &&
                !tracker.isExpired(System.currentTimeMillis()) &&
                tracker.getAttemptCount() >= MAX_ATTEMPTS_PER_PLAYER;
    }

    private boolean isIPLimited(String ip) {
        if (ip == null || ip.equals("unknown")) {
            return false;
        }

        AttemptTracker tracker = ipAttempts.get(ip);
        return tracker != null &&
                !tracker.isExpired(System.currentTimeMillis()) &&
                tracker.getAttemptCount() >= MAX_ATTEMPTS_PER_IP;
    }

    private long getPlayerCooldown(UUID playerUUID) {
        AttemptTracker tracker = loginAttempts.get(playerUUID);
        if (tracker == null || tracker.isExpired(System.currentTimeMillis())) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - tracker.getFirstAttemptTime();
        long remaining = TimeUnit.MINUTES.toMillis(COOLDOWN_MINUTES) - elapsed;
        return Math.max(0, TimeUnit.MILLISECONDS.toMinutes(remaining));
    }

    private long getIPCooldown(String ip) {
        if (ip == null || ip.equals("unknown")) {
            return 0;
        }

        AttemptTracker tracker = ipAttempts.get(ip);
        if (tracker == null || tracker.isExpired(System.currentTimeMillis())) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - tracker.getFirstAttemptTime();
        long remaining = TimeUnit.MINUTES.toMillis(COOLDOWN_MINUTES) - elapsed;
        return Math.max(0, TimeUnit.MILLISECONDS.toMinutes(remaining));
    }

    private void cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();

        loginAttempts.entrySet().removeIf(entry -> entry.getValue().isExpired(currentTime));
        ipAttempts.entrySet().removeIf(entry -> entry.getValue().isExpired(currentTime));
    }

    public void shutdown() {
        cleanup.shutdown();
        try {
            if (!cleanup.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanup.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanup.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class AttemptTracker {
        private final long firstAttemptTime;
        private int attemptCount;
        private long lastAttemptTime;

        public AttemptTracker(long firstAttemptTime) {
            this.firstAttemptTime = firstAttemptTime;
            this.attemptCount = 1;
            this.lastAttemptTime = firstAttemptTime;
        }

        public void addAttempt(long currentTime) {
            this.attemptCount++;
            this.lastAttemptTime = currentTime;
        }

        public boolean isExpired(long currentTime) {
            return (currentTime - firstAttemptTime) > TimeUnit.MINUTES.toMillis(COOLDOWN_MINUTES);
        }

        public int getAttemptCount() {
            return attemptCount;
        }

        public long getFirstAttemptTime() {
            return firstAttemptTime;
        }
    }
}