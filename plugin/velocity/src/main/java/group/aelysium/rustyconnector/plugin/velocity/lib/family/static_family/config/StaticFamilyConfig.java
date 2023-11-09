package group.aelysium.rustyconnector.plugin.velocity.lib.family.static_family.config;

import group.aelysium.rustyconnector.toolkit.velocity.family.UnavailableProtocol;
import group.aelysium.rustyconnector.core.lib.config.YAML;
import group.aelysium.rustyconnector.toolkit.velocity.load_balancing.AlgorithmType;
import group.aelysium.rustyconnector.toolkit.velocity.util.LiquidTimestamp;

import java.io.File;
import java.text.ParseException;

public class StaticFamilyConfig extends YAML {
    private String parent_family = "";
    private boolean firstConnection_loadBalancing_weighted = false;
    private String firstConnection_loadBalancing_algorithm = "ROUND_ROBIN";
    private boolean firstConnection_loadBalancing_persistence_enabled = false;
    private int firstConnection_loadBalancing_persistence_attempts = 5;

    private UnavailableProtocol consecutiveConnections_homeServer_ifUnavailable = UnavailableProtocol.ASSIGN_NEW_HOME;
    private LiquidTimestamp consecutiveConnections_homeServer_expiration = null;
    private boolean whitelist_enabled = false;
    private String whitelist_name = "whitelist-template";

    public StaticFamilyConfig(File configPointer) {
        super(configPointer);
    }

    public String getParent_family() { return parent_family; }

    public boolean isFirstConnection_loadBalancing_weighted() {
        return firstConnection_loadBalancing_weighted;
    }

    public boolean isFirstConnection_loadBalancing_persistence_enabled() {
        return firstConnection_loadBalancing_persistence_enabled;
    }

    public int getFirstConnection_loadBalancing_persistence_attempts() {
        return firstConnection_loadBalancing_persistence_attempts;
    }

    public String getFirstConnection_loadBalancing_algorithm() {
        return firstConnection_loadBalancing_algorithm;
    }

    public boolean isWhitelist_enabled() {
        return whitelist_enabled;
    }

    public String getWhitelist_name() {
        return whitelist_name;
    }

    public UnavailableProtocol getConsecutiveConnections_homeServer_ifUnavailable() {
        return consecutiveConnections_homeServer_ifUnavailable;
    }
    public LiquidTimestamp getConsecutiveConnections_homeServer_expiration() {
        return consecutiveConnections_homeServer_expiration;
    }

    public void register() throws IllegalStateException {
        try {
            this.parent_family = this.getNode(this.data, "parent-family", String.class);
        } catch (Exception ignore) {
            this.parent_family = "";
        }

        this.firstConnection_loadBalancing_weighted = this.getNode(this.data,"first-connection.load-balancing.weighted",Boolean.class);
        this.firstConnection_loadBalancing_algorithm = this.getNode(this.data,"first-connection.load-balancing.algorithm",String.class);

        try {
            Enum.valueOf(AlgorithmType.class, this.firstConnection_loadBalancing_algorithm);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The load balancing algorithm: "+this.firstConnection_loadBalancing_algorithm +" doesn't exist!");
        }

        this.firstConnection_loadBalancing_persistence_enabled = this.getNode(this.data,"first-connection.load-balancing.persistence.enabled",Boolean.class);
        this.firstConnection_loadBalancing_persistence_attempts = this.getNode(this.data,"first-connection.load-balancing.persistence.attempts",Integer.class);
        if(this.firstConnection_loadBalancing_persistence_enabled && this.firstConnection_loadBalancing_persistence_attempts <= 0)
            throw new IllegalStateException("Load balancing persistence must allow at least 1 attempt.");

        try {
            this.consecutiveConnections_homeServer_ifUnavailable = UnavailableProtocol.valueOf(this.getNode(this.data,"consecutive-connections.home-server.if-unavailable",String.class));
        } catch (IllegalArgumentException ignore) {
            throw new IllegalStateException("You must provide a valid option for [consecutive-connections.home-server.if-unavailable] in your static family configs!");
        }
        try {
            String expiration = this.getNode(this.data, "consecutive-connections.home-server.expiration", String.class);
            if(expiration.equals("NEVER")) this.consecutiveConnections_homeServer_expiration = null;
            else this.consecutiveConnections_homeServer_expiration = LiquidTimestamp.from(expiration);
        } catch (ParseException e) {
            throw new IllegalStateException("You must provide a valid time value for [consecutive-connections.home-server.expiration] in your static family configs!");
        }

        this.whitelist_enabled = this.getNode(this.data,"whitelist.enabled",Boolean.class);
        this.whitelist_name = this.getNode(this.data,"whitelist.name",String.class);
        if(this.whitelist_enabled && this.whitelist_name.equals(""))
            throw new IllegalStateException("whitelist.name cannot be empty in order to use a whitelist in a family!");

        this.whitelist_name = this.whitelist_name.replaceFirst("\\.yml$|\\.yaml$","");
    }
}
