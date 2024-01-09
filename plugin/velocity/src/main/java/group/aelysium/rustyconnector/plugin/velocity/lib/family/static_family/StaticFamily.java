package group.aelysium.rustyconnector.plugin.velocity.lib.family.static_family;

import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import group.aelysium.rustyconnector.plugin.velocity.event_handlers.EventDispatch;
import group.aelysium.rustyconnector.plugin.velocity.lib.config.ConfigService;
import group.aelysium.rustyconnector.plugin.velocity.lib.family.Family;
import group.aelysium.rustyconnector.plugin.velocity.lib.config.configs.LoadBalancerConfig;
import group.aelysium.rustyconnector.plugin.velocity.lib.players.Player;
import group.aelysium.rustyconnector.plugin.velocity.lib.whitelist.WhitelistService;
import group.aelysium.rustyconnector.toolkit.velocity.events.player.FamilyPreJoinEvent;
import group.aelysium.rustyconnector.toolkit.velocity.family.UnavailableProtocol;
import group.aelysium.rustyconnector.core.lib.lang.LangService;
import group.aelysium.rustyconnector.toolkit.velocity.family.static_family.IStaticFamily;
import group.aelysium.rustyconnector.toolkit.velocity.load_balancing.AlgorithmType;
import group.aelysium.rustyconnector.toolkit.velocity.players.IPlayer;
import group.aelysium.rustyconnector.toolkit.velocity.server.IMCLoader;
import group.aelysium.rustyconnector.toolkit.velocity.util.LiquidTimestamp;
import group.aelysium.rustyconnector.toolkit.velocity.util.DependencyInjector;
import group.aelysium.rustyconnector.plugin.velocity.central.Tinder;
import group.aelysium.rustyconnector.plugin.velocity.lib.config.configs.StaticFamilyConfig;
import group.aelysium.rustyconnector.plugin.velocity.lib.lang.ProxyLang;
import group.aelysium.rustyconnector.plugin.velocity.lib.load_balancing.LeastConnection;
import group.aelysium.rustyconnector.plugin.velocity.lib.load_balancing.LoadBalancer;
import group.aelysium.rustyconnector.plugin.velocity.lib.load_balancing.MostConnection;
import group.aelysium.rustyconnector.plugin.velocity.lib.load_balancing.RoundRobin;
import group.aelysium.rustyconnector.plugin.velocity.lib.server.MCLoader;
import group.aelysium.rustyconnector.plugin.velocity.lib.storage.StorageService;
import group.aelysium.rustyconnector.plugin.velocity.lib.whitelist.Whitelist;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.rmi.ConnectException;
import java.util.List;
import java.util.Optional;

import static group.aelysium.rustyconnector.toolkit.velocity.family.Metadata.STATIC_FAMILY_META;
import static group.aelysium.rustyconnector.toolkit.velocity.util.DependencyInjector.inject;

public class StaticFamily extends Family implements IStaticFamily {

    protected LiquidTimestamp homeServerExpiration;
    protected UnavailableProtocol unavailableProtocol;
    protected ResidenceDataEnclave dataEnclave;

    private StaticFamily(DependencyInjector.DI1<StorageService> deps, Settings settings) {
        super(settings.id(), new Family.Settings(settings.displayName(), settings.loadBalancer(), settings.parentFamily(), settings.whitelist()), STATIC_FAMILY_META);
        this.unavailableProtocol = settings.unavailableProtocol();
        this.homeServerExpiration = settings.homeServerExpiration();
        this.dataEnclave = new ResidenceDataEnclave(deps.d1());
    }

    public UnavailableProtocol unavailableProtocol() {
        return this.unavailableProtocol;
    }

    public LiquidTimestamp homeServerExpiration() {
        return this.homeServerExpiration;
    }
    public ResidenceDataEnclave dataEnclave() {
        return this.dataEnclave;
    }

    public IMCLoader connect(IPlayer player) throws RuntimeException {
        EventDispatch.Safe.fireAndForget(new FamilyPreJoinEvent(this, player));

        StaticFamilyConnector connector = new StaticFamilyConnector(this, player);
        return connector.connect();
    }

    /**
     * Initializes all server families based on the configs.
     * By the time this runs, the configuration file should be able to guarantee that all values are present.
     *
     * @return A list of all server families.
     */
    public static StaticFamily init(DependencyInjector.DI5<List<Component>, LangService, StorageService, WhitelistService, ConfigService> deps, String familyName) throws Exception {
        Tinder api = Tinder.get();
        List<Component> bootOutput = deps.d1();
        LangService lang = deps.d2();
        StorageService storage = deps.d3();
        WhitelistService whitelistService = deps.d4();

        StaticFamilyConfig config = StaticFamilyConfig.construct(api.dataFolder(), familyName, lang, deps.d5());

        AlgorithmType loadBalancerAlgorithm;
        LoadBalancer.Settings loadBalancerSettings;
        {
            LoadBalancerConfig loadBalancerConfig = LoadBalancerConfig.construct(api.dataFolder(), config.getFirstConnection_loadBalancer(), lang);

            loadBalancerAlgorithm = loadBalancerConfig.getAlgorithm();

            loadBalancerSettings = new LoadBalancer.Settings(
                    loadBalancerConfig.isWeighted(),
                    loadBalancerConfig.isPersistence_enabled(),
                    loadBalancerConfig.getPersistence_attempts()
            );
        }

        Whitelist.Reference whitelist = null;
        if (config.isWhitelist_enabled())
            whitelist = Whitelist.init(inject(bootOutput, lang, whitelistService, deps.d5()), config.getWhitelist_name());

        LoadBalancer loadBalancer;
        switch (loadBalancerAlgorithm) {
            case ROUND_ROBIN -> loadBalancer = new RoundRobin(loadBalancerSettings);
            case LEAST_CONNECTION -> loadBalancer = new LeastConnection(loadBalancerSettings);
            case MOST_CONNECTION -> loadBalancer = new MostConnection(loadBalancerSettings);
            default -> throw new RuntimeException("The id used for "+familyName+"'s load balancer is invalid!");
        }

        Settings settings = new Settings(
                familyName,
                config.displayName(),
                loadBalancer,
                config.getParent_family(),
                whitelist,
                storage,
                config.getConsecutiveConnections_homeServer_ifUnavailable(),
                config.getConsecutiveConnections_homeServer_expiration()
        );
        StaticFamily family = new StaticFamily(inject(deps.d3()), settings);

        try {
            family.dataEnclave().updateExpirations(config.getConsecutiveConnections_homeServer_expiration(), family);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("There was an issue with MySQL! " + e.getMessage());
        }

        return family;
    }

    @Override
    public MCLoader connect(PlayerChooseInitialServerEvent event) {
        return null;
    }

    public record Settings(
            String id,
            String displayName,
            LoadBalancer loadBalancer,
            Family.Reference parentFamily,
            Whitelist.Reference whitelist,
            StorageService storageService,
            UnavailableProtocol unavailableProtocol,
            LiquidTimestamp homeServerExpiration
    ) {}
}

class StaticFamilyConnector {
    private final StaticFamily family;
    private final IPlayer player;
    private Component postConnectionError = null;

    public StaticFamilyConnector(StaticFamily family, IPlayer player) {
        this.family = family;
        this.player = player;
    }

    public IMCLoader connect() throws RuntimeException {
        if(this.family.loadBalancer().size() == 0)
            throw new RuntimeException("There are no servers for you to connect to!");

        this.validateWhitelist();

        return this.establishAnyConnection();
    }

    public void validateWhitelist() throws RuntimeException {
        if(!(this.family.whitelist() == null)) {
            Whitelist familyWhitelist = this.family.whitelist();

            if (!familyWhitelist.validate(this.player))
                throw new RuntimeException(familyWhitelist.message());
        }
    }

    /**
     * Establish a connection to anything that's available.
     * If a home server is available, connect to that.
     * If not, check the load balancer and route the player into the proper server.
     * @return The player server that this player was connected to.
     */
    public IMCLoader establishAnyConnection() {
        try {
            return this.connectHomeServer();
        } catch (Exception ignore) {}

        return establishNewConnection(true);
    }

    /**
     * Establish a new connection to the family.
     * This will ignore whether the player has a home family.
     *
     * After a connection is established, the player's home family will be set or updated.
     * @return The player server that this player was connected to.
     */
    public IMCLoader establishNewConnection(boolean shouldRegisterNew) {
        IMCLoader server;
        if(this.family.loadBalancer().persistent() && this.family.loadBalancer().attempts() > 1)
            server = this.connectPersistent();
        else
            server = this.connectSingleton();

        this.sendPostConnectErrorMessage();

        if(!shouldRegisterNew) return server;

        try {
            this.family.dataEnclave().save(new Player.Reference(player.uuid()), server, this.family);
        } catch (Exception e) {
            Tinder.get().logger().send(Component.text("Unable to save "+ this.player.username() +" home server into MySQL! Their home server will only be saved until the server shuts down, or they log out!", NamedTextColor.RED));
            e.printStackTrace();
        }

        return server;
    }

    /**
     * Connects the player to their home server.
     * If the player's home server is unavailable and `UnavailableProtocol.ASSIGN_NEW_HOME` is set;
     * this will call `.establishNewConnection()`
     * @return The player server that this player was connected to.
     * @throws RuntimeException If no server could be connected to.
     */
    public IMCLoader connectHomeServer() throws RuntimeException {
        try {
            Optional<ServerResidence> residence = this.family.dataEnclave().fetch(new Player.Reference(player.uuid()), this.family);

            if(residence.isPresent()) {
                try {
                    if (!residence.orElseThrow().server().connect(this.player))
                        throw new RuntimeException("There was an issue connecting you to the server!");

                    return residence.orElseThrow().server();
                } catch (Exception ignore) {}
            }
            switch (this.family.unavailableProtocol()) {
                case ASSIGN_NEW_HOME -> {
                    this.family.dataEnclave().delete(new Player.Reference(player.uuid()), this.family);
                    return this.establishNewConnection(true);
                }
                case CONNECT_WITH_ERROR -> {
                    this.postConnectionError = ProxyLang.MISSING_HOME_SERVER;
                    return this.establishNewConnection(false);
                }
                case CANCEL_CONNECTION_ATTEMPT -> {
                    player.sendMessage(ProxyLang.BLOCKED_STATIC_FAMILY_JOIN_ATTEMPT);
                    return null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new RuntimeException("There was an issue connecting you to the server!");
    }

    private IMCLoader connectSingleton() {
        IMCLoader server = this.family.loadBalancer().current();
        try {
            if(!server.validatePlayer(this.player))
                throw new RuntimeException("The server you're trying to connect to is full!");


            if (!server.connect(this.player))
                throw new RuntimeException("There was an issue connecting you to the server!");

            this.family.loadBalancer().iterate();

            return server;
        } catch (RuntimeException | ConnectException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private IMCLoader connectPersistent() {
        int attemptsLeft = this.family.loadBalancer().attempts();

        for (int attempt = 1; attempt <= attemptsLeft; attempt++) {
            boolean isFinal = (attempt == attemptsLeft);
            IMCLoader server = this.family.loadBalancer().current(); // Get the server that is currently listed as highest priority

            try {
                if (!server.validatePlayer(this.player))
                    throw new RuntimeException("The server you're trying to connect to is full!");

                if (!server.connect(this.player))
                    throw new RuntimeException("There was an issue connecting you to the server!");

                this.family.loadBalancer().forceIterate();

                return server;
            } catch (Exception e) {
                if (isFinal)
                    this.player.disconnect(Component.text(e.getMessage()));
            }
            this.family.loadBalancer().forceIterate();
        }

        throw new RuntimeException("Unable to connect you to the server!");
    }

    public void sendPostConnectErrorMessage() {
        if(this.postConnectionError == null) return;
        this.player.sendMessage(this.postConnectionError);
    }
}
