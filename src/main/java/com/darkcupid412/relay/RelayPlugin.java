package com.darkcupid412.relay;

import com.darkcupid412.relay.command.LinkCommand;
import com.darkcupid412.relay.command.PlayersCommand;
import com.darkcupid412.relay.command.UnlinkCommand;
import com.darkcupid412.relay.config.ConfigStore;
import com.darkcupid412.relay.config.RelayConfig;
import com.darkcupid412.relay.discord.DiscordBot;
import com.darkcupid412.relay.discord.DiscordCommandSender;
import com.darkcupid412.relay.discord.PlayersSlashCommand;
import com.darkcupid412.relay.link.AccountLinkManager;
import com.darkcupid412.relay.link.BanSyncHandler;
import com.darkcupid412.relay.link.DiscordNameMarkerProvider;
import com.darkcupid412.relay.link.LinkSlashCommand;
import com.darkcupid412.relay.link.NameplateSync;
import com.darkcupid412.relay.link.LinkSyncHandler;
import com.darkcupid412.relay.link.RequireLinkBlockSystem;
import com.darkcupid412.relay.link.RequireLinkGate;
import com.darkcupid412.relay.link.UnlinkSlashCommand;
import com.darkcupid412.relay.stats.BlockBreakStatsSystem;
import com.darkcupid412.relay.stats.BlockPlaceStatsSystem;
import com.darkcupid412.relay.stats.DeathStatsSystem;
import com.darkcupid412.relay.stats.PlaytimeSlashCommand;
import com.darkcupid412.relay.stats.StatsManager;
import com.darkcupid412.relay.stats.StatsSlashCommand;
import com.darkcupid412.relay.stats.ZoneDiscoveryStatsSystem;
import com.darkcupid412.relay.systems.DeathEventSystem;
import com.darkcupid412.relay.systems.ZoneDiscoveryBroadcastSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class RelayPlugin extends JavaPlugin {
    private ConfigStore config;
    private DiscordBot discord;
    private AccountLinkManager linkManager;
    private AfkTracker afkTracker;
    private StatsManager statsManager;
    private BanSyncHandler banSync;

    public RelayPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getLogger().atInfo().log("DiscordRelay %s loading", getManifest().getVersion());
        this.config = new ConfigStore(getDataDirectory(), getLogger());
        this.config.load();
        getCommandRegistry().registerCommand(new PlayersCommand());
        if (config.get().isAccountLinkingEnabled()) {
            this.linkManager = new AccountLinkManager(getDataDirectory(), getLogger());
            getCommandRegistry().registerCommand(new LinkCommand(linkManager));
            getCommandRegistry().registerCommand(new UnlinkCommand(linkManager));
        }
        if (config.get().isUpdateCheckEnabled()) {
            new UpdateChecker(getLogger(), String.valueOf(getManifest().getVersion()), config.get().getUpdateRepo()).checkAsync();
        }
        if (config.get().isStatsEnabled()) {
            this.statsManager = new StatsManager(getDataDirectory(), getLogger());
            this.statsManager.start();
            getEntityStoreRegistry().registerSystem(new BlockPlaceStatsSystem(statsManager));
            getEntityStoreRegistry().registerSystem(new BlockBreakStatsSystem(statsManager));
            getEntityStoreRegistry().registerSystem(new DeathStatsSystem(statsManager));
            getEntityStoreRegistry().registerSystem(new ZoneDiscoveryStatsSystem(statsManager));
            getEventRegistry().registerGlobal(PlayerChatEvent.class, e -> statsManager.addMessage(e.getSender().getUuid()));
            getEventRegistry().register(PlayerConnectEvent.class, e -> statsManager.onConnect(e.getPlayerRef().getUuid(), e.getPlayerRef().getUsername()));
            getEventRegistry().register(PlayerDisconnectEvent.class, e -> statsManager.onDisconnect(e.getPlayerRef().getUuid()));
        }
    }

    @Override
    protected void start() {
        // Route JDA logging through the bundled slf4j-nop backend instead of its own System.err
        // fallback (which ignores the plugin classloader). Must run before any JDA class loads.
        System.setProperty("net.dv8tion.jda.disableFallbackLogger", "true");
        RelayConfig cfg = config.get();
        if (cfg.getDiscordToken().isEmpty() || cfg.getChatChannelId().isEmpty()) {
            getLogger().atWarning().log("Discord not configured, relay disabled. Set DiscordToken and ChatChannelId in config.yml");
            return;
        }

        this.discord = new DiscordBot(getLogger(), config::get, getDataDirectory());
        this.discord.setChatHandler(this::handleDiscordChat);
        if (!cfg.getConsoleChannelId().isEmpty()) {
            this.discord.setConsoleCommandHandler(this::handleConsoleCommand);
        }
        this.discord.registerSlashCommand(new PlayersSlashCommand());
        if (linkManager != null) {
            this.discord.registerSlashCommand(new LinkSlashCommand(linkManager));
            this.discord.registerSlashCommand(new UnlinkSlashCommand(linkManager));
            linkManager.addListener(new LinkSyncHandler(this.discord::getJda, config::get, getLogger(), linkManager::getCachedName));
            if (cfg.isLinkingRequireToPlay()) {
                RequireLinkGate gate = new RequireLinkGate(linkManager, config::get, this::relayActive);
                linkManager.addListener(gate);
                getEventRegistry().register(PlayerConnectEvent.class, gate::onPlayerConnect);
                getEntityStoreRegistry().registerSystem(new RequireLinkBlockSystem.Place(gate));
                getEntityStoreRegistry().registerSystem(new RequireLinkBlockSystem.Break(gate));
            }
            if (cfg.isShowDiscordNameIngame()) {
                DiscordNameMarkerProvider markers = new DiscordNameMarkerProvider(linkManager);
                getEventRegistry().register((short) 0, AddWorldEvent.class, (String) null,
                    (AddWorldEvent e) -> e.getWorld().getWorldMapManager().addMarkerProvider("playerIcons", markers));
                int existing = 0;
                if (Universe.get() != null) {
                    for (World world : Universe.get().getWorlds().values()) {
                        world.getWorldMapManager().addMarkerProvider("playerIcons", markers);
                        existing++;
                    }
                }
                NameplateSync nameplates = new NameplateSync(linkManager, getLogger());
                linkManager.addListener(nameplates);
                getEventRegistry().register((short) 0, AddPlayerToWorldEvent.class, (String) null, nameplates::onAddPlayerToWorld);
                getEventRegistry().registerGlobal(PlayerChatEvent.class, nameplates::onChat);
                getLogger().atInfo().log("Discord name in game enabled (map, nameplate, chat) for %d loaded world(s)", existing);
            }
        }
        // Ban sync needs account linking; the ban broadcast does not, so this runs outside the linkManager block.
        boolean banSyncOn = linkManager != null && cfg.isBanSyncEnabled();
        if (banSyncOn || cfg.isBanBroadcastEnabled()) {
            this.banSync = new BanSyncHandler(linkManager, this.discord::getJda, config::get, getLogger(),
                this.discord::sendModerationBroadcast, this::resolvePlayerName);
            if (banSyncOn) {
                this.discord.setBanSyncListener(banSync);
            }
        }
        if (cfg.isZoneDiscoveryBroadcastEnabled()) {
            getEntityStoreRegistry().registerSystem(new ZoneDiscoveryBroadcastSystem(
                this::relayActive, (name, zone) -> discord.sendZoneDiscovery(name, zone)));
        }
        if (statsManager != null) {
            this.discord.registerSlashCommand(new StatsSlashCommand(statsManager, linkManager));
            this.discord.registerSlashCommand(new PlaytimeSlashCommand(statsManager));
        }

        if (cfg.isChatEnabled()) {
            getEventRegistry().registerGlobal(PlayerChatEvent.class, this::onPlayerChat);
        }
        if (cfg.isJoinLeaveEnabled() || linkManager != null) {
            getEventRegistry().register(PlayerConnectEvent.class, this::onPlayerConnect);
        }
        if (cfg.isJoinLeaveEnabled()) {
            getEventRegistry().register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        }
        if (cfg.isDeathEnabled()) {
            getEntityStoreRegistry().registerSystem(new DeathEventSystem(
                () -> relayActive() && config.get().isDeathEnabled(),
                text -> discord.sendDeath(text)));
        }
        if (cfg.isServerStatusEnabled()) {
            this.discord.setAnnounceServerStatus(true);
        }
        if (cfg.isAfkEnabled()) {
            this.afkTracker = new AfkTracker(config::get, this::relayActive, getLogger(), (name, afk) -> discord.sendAfk(name, afk));
            this.afkTracker.start();
        }

        if (this.discord.start()) {
            getLogger().atInfo().log("DiscordRelay ready");
            if (banSync != null) {
                banSync.start();
            }
        } else {
            getLogger().atSevere().log("DiscordRelay failed to start; relay is inactive");
        }
    }

    @Override
    protected void shutdown() {
        getLogger().atInfo().log("DiscordRelay shutting down");
        if (afkTracker != null) {
            afkTracker.stop();
        }
        if (banSync != null) {
            banSync.stop();
        }
        if (statsManager != null) {
            statsManager.stop();
        }
        if (discord != null) {
            if (config.get().isServerStatusEnabled() && discord.isConnected()) {
                discord.sendServerStop();
            }
            discord.stop();
        }
    }

    private void onPlayerChat(PlayerChatEvent event) {
        if (relayActive() && config.get().isChatEnabled()) {
            PlayerRef sender = event.getSender();
            String filtered = config.get().filterChat(event.getContent());
            if (filtered != null) {
                String name = sender.getUsername();
                if (config.get().isShowDiscordNameIngame() && linkManager != null) {
                    name = linkManager.getDiscordName(sender.getUuid()).orElse(name);
                }
                discord.sendChat(name, sender.getUuid(), filtered);
            }
        }
    }

    private void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef ref = event.getPlayerRef();
        if (linkManager != null) {
            linkManager.refreshIngameName(ref.getUuid(), ref.getUsername());
        }
        if (config.get().isLinkingRequireToPlay() && linkManager != null && !linkManager.isLinked(ref.getUuid())) {
            return;
        }
        if (relayActive() && config.get().isJoinLeaveEnabled()) {
            discord.sendJoin(ref.getUsername(), ref.getUuid());
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef ref = event.getPlayerRef();
        if (config.get().isLinkingRequireToPlay() && linkManager != null && !linkManager.isLinked(ref.getUuid())) {
            return;
        }
        if (relayActive() && config.get().isJoinLeaveEnabled()) {
            discord.sendLeave(ref.getUsername(), ref.getUuid());
        }
    }

    private void handleDiscordChat(String discordId, String author, String message) {
        RelayConfig cfg = config.get();
        if (linkManager != null && cfg.isLinkingRequireToChat() && !linkManager.isLinked(discordId)) {
            return;
        }
        String filtered = cfg.filterChat(message);
        if (filtered == null) {
            return;
        }
        String name = author;
        if (linkManager != null && cfg.isLinkingShowIngameName()) {
            name = linkManager.getIngameName(discordId).orElse(author);
        }
        String formatted = cfg.getDiscordToGameFormat().replace("%player%", name).replace("%message%", filtered);
        Message msg = Message.raw(formatted);
        String color = cfg.getDiscordToGameColor();
        if (!color.isEmpty()) {
            msg = msg.color(color);
        }
        if (Universe.get() == null) {
            return;
        }
        for (PlayerRef player : Universe.get().getPlayers()) {
            player.sendMessage(msg);
        }
    }

    private String handleConsoleCommand(String author, String command) {
        String base = command.split(" ")[0].toLowerCase();
        if (base.contains(":")) {
            base = base.substring(base.indexOf(':') + 1);
        }
        RelayConfig cfg = config.get();
        if (!cfg.getConsoleAllowedCommands().contains(base)) {
            return ":no_entry: Command `" + base + "` is not in the console allowlist (ConsoleAllowedCommands)";
        }
        if (cfg.getCommandBlacklist().contains(base)) {
            return ":no_entry: Command `" + base + "` is blacklisted";
        }
        DiscordCommandSender sender = new DiscordCommandSender(author);
        try {
            CommandManager.get().handleCommand(sender, command).get(10L, TimeUnit.SECONDS);
            String output = sender.getOutput();
            if (output.length() > 1950) {
                int cut = output.lastIndexOf('\n', 1950);
                output = cut > 0 ? output.substring(0, cut) + "\n... (truncated)" : output.substring(0, 1950) + "... (truncated)";
            }
            return "```\n" + output + "\n```";
        } catch (Exception e) {
            return ":x: Error: " + e.getMessage();
        }
    }

    private boolean relayActive() {
        return discord != null && discord.isConnected();
    }

    private String resolvePlayerName(UUID uuid) {
        if (uuid == null) {
            return "Unknown";
        }
        if (statsManager != null) {
            String name = statsManager.peekUsername(uuid);
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        if (linkManager != null) {
            String name = linkManager.getCachedName(uuid).orElse(null);
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        // getUsername is a final-field read, safe off the ban poll thread (unlike entity component access).
        if (Universe.get() != null) {
            PlayerRef player = Universe.get().getPlayer(uuid);
            if (player != null) {
                return player.getUsername();
            }
        }
        return uuid.toString().substring(0, 8);
    }
}
