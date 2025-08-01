package io.github.poelsk.authreloaded.auth;

import io.github.poelsk.authreloaded.database.PlayerDataDAO;
import io.github.poelsk.authreloaded.managers.MessageManager;
import io.github.poelsk.authreloaded.managers.SessionManager;
import io.github.poelsk.authreloaded.model.PlayerData;
import org.bukkit.entity.Player;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public class AuthenticationService {

    private final PlayerDataDAO playerDataDAO;
    private final PasswordService passwordService;
    private final SessionManager sessionManager;
    private final MessageManager messageManager;

    public enum AuthResult {
        SUCCESS,
        ALREADY_LOGGED_IN,
        ALREADY_REGISTERED,
        NOT_REGISTERED,
        INCORRECT_PASSWORD,
        FAILURE
    }

    public AuthenticationService(PlayerDataDAO playerDataDAO, PasswordService passwordService, SessionManager sessionManager, MessageManager messageManager) {
        this.playerDataDAO = playerDataDAO;
        this.passwordService = passwordService;
        this.sessionManager = sessionManager;
        this.messageManager = messageManager;
    }

    public AuthResult registerPlayer(Player player, String password) {
        if (playerDataDAO.getPlayerByUUID(player.getUniqueId()).isPresent()) {
            return AuthResult.ALREADY_REGISTERED;
        }

        String hashedPassword = passwordService.hashPassword(password);
        PlayerData data = new PlayerData(
                player.getUniqueId(),
                player.getName(),
                hashedPassword,
                player.getAddress().getAddress().getHostAddress(),
                Timestamp.from(Instant.now())
        );

        try {
            playerDataDAO.createPlayer(data);
            sessionManager.createSession(player);
            return AuthResult.SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            return AuthResult.FAILURE;
        }
    }

    public AuthResult loginPlayer(Player player, String password) {
        if (sessionManager.isLoggedIn(player)) {
            return AuthResult.ALREADY_LOGGED_IN;
        }

        Optional<PlayerData> playerDataOpt = playerDataDAO.getPlayerByUUID(player.getUniqueId());
        if (playerDataOpt.isEmpty()) {
            return AuthResult.NOT_REGISTERED;
        }

        PlayerData playerData = playerDataOpt.get();
        if (passwordService.checkPassword(password, playerData.getPasswordHash())) {
            sessionManager.createSession(player);
            playerDataDAO.updateLastLoginIp(player.getUniqueId(), player.getAddress().getAddress().getHostAddress());
            return AuthResult.SUCCESS;
        } else {
            return AuthResult.INCORRECT_PASSWORD;
        }
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
            case FAILURE:
                messageManager.sendMessage(player, "error_generic");
                break;
        }
    }
}