package io.github.poelsk.authreloaded;

import io.github.poelsk.authreloaded.auth.AuthenticationService;
import io.github.poelsk.authreloaded.auth.PasswordService;
import io.github.poelsk.authreloaded.auth.PremiumVerificationService;
import io.github.poelsk.authreloaded.auth.RateLimitingService;
import io.github.poelsk.authreloaded.commands.AdminCommand;
import io.github.poelsk.authreloaded.commands.LoginCommand;
import io.github.poelsk.authreloaded.commands.RegisterCommand;
import io.github.poelsk.authreloaded.commands.PremiumCommand;
import io.github.poelsk.authreloaded.database.DatabaseManager;
import io.github.poelsk.authreloaded.database.PlayerDataDAO;
import io.github.poelsk.authreloaded.listeners.ActionLimiterListener;
import io.github.poelsk.authreloaded.listeners.PlayerConnectionListener;
import io.github.poelsk.authreloaded.managers.MessageManager;
import io.github.poelsk.authreloaded.managers.PlayerStatusManager;
import io.github.poelsk.authreloaded.managers.PremiumManager;
import io.github.poelsk.authreloaded.managers.SessionManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuthReloaded extends JavaPlugin {

    private DatabaseManager databaseManager;
    private PlayerDataDAO playerDataDAO;
    private PasswordService passwordService;
    private SessionManager sessionManager;
    private MessageManager messageManager;
    private AuthenticationService authenticationService;
    private PlayerStatusManager playerStatusManager;
    private PremiumManager premiumManager;
    private PremiumVerificationService premiumVerificationService;
    private RateLimitingService rateLimitingService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messageManager = new MessageManager(this);
        this.databaseManager = new DatabaseManager(this);

        databaseManager.initializeDatabase();

        this.playerDataDAO = new PlayerDataDAO(this.databaseManager);
        this.passwordService = new PasswordService();
        this.sessionManager = new SessionManager();
        this.playerStatusManager = new PlayerStatusManager();
        this.rateLimitingService = new RateLimitingService();
        this.authenticationService = new AuthenticationService(playerDataDAO, passwordService, sessionManager, messageManager, rateLimitingService);
        this.premiumManager = new PremiumManager(this);
        this.premiumVerificationService = new PremiumVerificationService(this); // Pasar instancia del plugin

        registerCommands();
        registerListeners();

        getLogger().info("AuthReloaded has been enabled!");
    }

    @Override
    public void onDisable() {
        if (rateLimitingService != null) {
            rateLimitingService.shutdown();
            getLogger().info("Rate limiting service shutdown complete.");
        }

        if (premiumVerificationService != null) {
            premiumVerificationService.shutdown();
            getLogger().info("Premium verification service shutdown complete.");
        }

        if (databaseManager != null) {
            databaseManager.close();
            getLogger().info("Database connections closed.");
        }

        getLogger().info("AuthReloaded has been disabled.");
    }

    private void registerCommands() {
        this.getCommand("login").setExecutor(new LoginCommand(this));
        this.getCommand("register").setExecutor(new RegisterCommand(this));
        this.getCommand("authreload").setExecutor(new AdminCommand(this));
        this.getCommand("premium").setExecutor(new PremiumCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ActionLimiterListener(this), this);
    }

    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public PlayerDataDAO getPlayerDataDAO() {
        return playerDataDAO;
    }

    public PlayerStatusManager getPlayerStatusManager() {
        return playerStatusManager;
    }

    public PremiumManager getPremiumManager() {
        return premiumManager;
    }

    public PremiumVerificationService getPremiumVerificationService() {
        return premiumVerificationService;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public RateLimitingService getRateLimitingService() {
        return rateLimitingService;
    }
}