package io.github.poelsk.authreloaded.auth;

import io.github.poelsk.authreloaded.database.PlayerDataDAO;
import io.github.poelsk.authreloaded.managers.MessageManager;
import io.github.poelsk.authreloaded.managers.SessionManager;
import io.github.poelsk.authreloaded.model.PlayerData;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public class AuthenticationService {

    private final PlayerDataDAO playerDataDAO;
    private final PasswordService passwordService;
    private final SessionManager sessionManager;
    private final MessageManager messageManager;
    private final RateLimitingService rateLimitingService;

    public enum AuthResult {
        SUCCESS,
        ALREADY_LOGGED_IN,
        ALREADY_REGISTERED,
        NOT_REGISTERED,
        INCORRECT_PASSWORD,
        RATE_LIMITED,
        INVALID_PASSWORD,
        FAILURE
    }

    public AuthenticationService(PlayerDataDAO playerDataDAO, PasswordService passwordService,
                                 SessionManager sessionManager, MessageManager messageManager,
                                 RateLimitingService rateLimitingService) {
        this.playerDataDAO = playerDataDAO;
        this.passwordService = passwordService;
        this.sessionManager = sessionManager;
        this.messageManager = messageManager;
        this.rateLimitingService = rateLimitingService;
    }

    public AuthResult registerPlayer(Player player, String password) {
        if (player == null) {
            return AuthResult.FAILURE;
        }

        PasswordService.ValidationResult validation = passwordService.validatePassword(password);
        if (validation != PasswordService.ValidationResult.VALID) {
            return AuthResult.INVALID_PASSWORD;
        }

        if (playerDataDAO.getPlayerByUUID(player.getUniqueId()).isPresent()) {
            return AuthResult.ALREADY_REGISTERED;
        }

        String hashedPassword = passwordService.hashPassword(password);
        String playerIP = getPlayerIP(player);

        PlayerData data = new PlayerData(
                player.getUniqueId(),
                player.getName(),
                hashedPassword,
                playerIP,
                Timestamp.from(Instant.now())
        );

        try {
            playerDataDAO.createPlayer(data);
            sessionManager.createSession(player);

            rateLimitingService.recordSuccessfulLogin(player.getUniqueId(), playerIP);

            return AuthResult.SUCCESS;
        } catch (Exception e) {
            messageManager.plugin.getLogger().severe("Failed to register player " + player.getName() + ": " + e.getMessage());
            return AuthResult.FAILURE;
        }
    }

    public AuthResult loginPlayer(Player player, String password) {
        if (player == null) {
            return AuthResult.FAILURE;
        }

        if (sessionManager.isLoggedIn(player)) {
            return AuthResult.ALREADY_LOGGED_IN;
        }

        String playerIP = getPlayerIP(player);

        RateLimitingService.LimitResult limitResult = rateLimitingService.canAttemptLogin(player.getUniqueId(), playerIP);
        if (limitResult != RateLimitingService.LimitResult.ALLOWED) {
            return AuthResult.RATE_LIMITED;
        }

        Optional<PlayerData> playerDataOpt = playerDataDAO.getPlayerByUUID(player.getUniqueId());
        if (playerDataOpt.isEmpty()) {
            return AuthResult.NOT_REGISTERED;
        }

        PlayerData playerData = playerDataOpt.get();
        if (passwordService.checkPassword(password, playerData.getPasswordHash())) {
            sessionManager.createSession(player);
            playerDataDAO.updateLastLoginIp(player.getUniqueId(), playerIP);

            rateLimitingService.recordSuccessfulLogin(player.getUniqueId(), playerIP);

            return AuthResult.SUCCESS;
        } else {
            rateLimitingService.recordFailedAttempt(player.getUniqueId(), playerIP);
            return AuthResult.INCORRECT_PASSWORD;
        }
    }

    private String getPlayerIP(Player player) {
        if (player == null) {
            return "unknown";
        }

        try {
            InetSocketAddress address = player.getAddress();
            if (address != null && address.getAddress() != null) {
                return address.getAddress().getHostAddress();
            }
        } catch (Exception e) {
            if (messageManager != null && messageManager.plugin != null) {
                messageManager.plugin.getLogger().warning(
                        "Failed to get IP for player " + player.getName() + ": " + e.getMessage());
            }
        }
        return "unknown";
    }

    public void handleAuthResult(Player player, AuthResult result, String command) {
        switch (result) {
            case SUCCESS:
                if ("register".equals(command)) {
                    messageManager.sendMessage(player, "register_success");
                } else {
                    messageManager.sendMessage(player, "login_success");
                }
                break;
            case ALREADY_LOGGED_IN:
                messageManager.sendMessage(player, "already_logged_in");
                break;
            case ALREADY_REGISTERED:
                messageManager.sendMessage(player, "already_registered");
                break;
            case NOT_REGISTERED:
                messageManager.sendMessage(player, "not_registered");
                break;
            case INCORRECT_PASSWORD:
                messageManager.sendMessage(player, "password_incorrect");
                break;
            case RATE_LIMITED:
                String playerIP = getPlayerIP(player);
                long cooldown = rateLimitingService.getRemainingCooldown(player.getUniqueId(), playerIP);
                messageManager.sendMessage(player, "rate_limited", String.valueOf(cooldown));
                break;
            case INVALID_PASSWORD:
                messageManager.sendMessage(player, "invalid_password",
                        String.valueOf(passwordService.getMinPasswordLength()),
                        String.valueOf(passwordService.getMaxPasswordLength()));
                break;
            case FAILURE:
                messageManager.sendMessage(player, "error_generic");
                break;
        }
    }
}