package com.darkcupid412.relay.link;

import com.darkcupid412.relay.config.RelayConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.accesscontrol.AccessControlModule;
import com.hypixel.hytale.server.core.modules.accesscontrol.provider.AccessProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Two way ban sync and a ban broadcast. A player banned in game (the engine writes the ban to bans.json) is
 * banned on Discord, and a Discord ban blocks the linked player from connecting and kicks them if online. The
 * engine's ban provider is private and emits no event, so the in game side is read from the bans.json file on
 * a timer. Optionally, new bans and unbans are also announced to the chat channel. Existing bans are baselined
 * at startup so a restart neither re-syncs nor re-announces them; only later changes propagate. Sync needs
 * account linking (to map the player to a Discord user); the broadcast does not, so linkManager may be null.
 */
public final class BanSyncHandler extends ListenerAdapter implements AccessProvider {
    private static final long POLL_SECONDS = 10L;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final AccountLinkManager linkManager;
    private final Supplier<JDA> jda;
    private final Supplier<RelayConfig> config;
    private final HytaleLogger logger;
    private final Consumer<String> broadcaster;
    private final Function<UUID, String> nameResolver;
    private final Path bansFile = Paths.get("bans.json");
    private final Gson gson = new Gson();
    private final Set<String> discordBanned = ConcurrentHashMap.newKeySet();
    private final Set<UUID> knownGameBans = ConcurrentHashMap.newKeySet();
    private ScheduledFuture<?> pollTask;

    public BanSyncHandler(AccountLinkManager linkManager, Supplier<JDA> jda, Supplier<RelayConfig> config, HytaleLogger logger,
                          Consumer<String> broadcaster, Function<UUID, String> nameResolver) {
        this.linkManager = linkManager;
        this.jda = jda;
        this.config = config;
        this.logger = logger;
        this.broadcaster = broadcaster;
        this.nameResolver = nameResolver;
    }

    public void start() {
        if (config.get().isBanSyncEnabled() && linkManager != null) {
            AccessControlModule access = AccessControlModule.get();
            if (access != null) {
                access.registerAccessProvider(this);
            } else {
                logger.atWarning().log("AccessControlModule unavailable; Discord-to-game ban gating disabled");
            }
        }
        Map<UUID, BanInfo> baseline = readGameBans();
        if (baseline != null) {
            knownGameBans.addAll(baseline.keySet());
        }
        pollTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(this::pollGameBans, POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        if (pollTask != null) {
            pollTask.cancel(false);
        }
    }

    /** AccessProvider: deny a connection whose linked Discord account is banned. */
    @Override
    public CompletableFuture<Optional<Message>> getDisconnectReason(UUID uuid) {
        if (config.get().isBanSyncEnabled() && linkManager != null) {
            Optional<String> discordId = linkManager.getDiscordId(uuid);
            if (discordId.isPresent() && discordBanned.contains(discordId.get())) {
                return CompletableFuture.completedFuture(Optional.of(Message.raw(config.get().getBanSyncDiscordBanReason())));
            }
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public void onReady(ReadyEvent event) {
        String guildId = config.get().getBanSyncGuildId();
        if (!guildId.isBlank() && event.getJDA().getGuildById(guildId) == null) {
            logger.atWarning().log("BanSyncGuildId %s is not a guild this bot is in; ban sync will do nothing", guildId);
        }
        for (Guild guild : guilds(event.getJDA())) {
            guild.retrieveBanList().forEachAsync(ban -> {
                discordBanned.add(ban.getUser().getId());
                return true;
            }).thenRun(this::kickBannedOnline).exceptionally(err -> {
                logger.atWarning().log("Could not read Discord ban list (Ban Members permission?): %s", err.getMessage());
                return null;
            });
        }
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        discordBanned.add(event.getUser().getId());
        kickBannedOnline();
    }

    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        discordBanned.remove(event.getUser().getId());
    }

    private void kickBannedOnline() {
        if (!config.get().isBanSyncEnabled() || linkManager == null || Universe.get() == null) {
            return;
        }
        for (PlayerRef player : Universe.get().getPlayers()) {
            Optional<String> discordId = linkManager.getDiscordId(player.getUuid());
            if (discordId.isPresent() && discordBanned.contains(discordId.get())) {
                disconnect(player);
            }
        }
    }

    private void disconnect(PlayerRef player) {
        UUID worldUuid = player.getWorldUuid();
        World world = worldUuid == null || Universe.get() == null ? null : Universe.get().getWorld(worldUuid);
        Runnable kick = () -> player.getPacketHandler().disconnect(Message.raw(config.get().getBanSyncDiscordBanReason()));
        if (world != null) {
            world.execute(kick);
        } else {
            kick.run();
        }
    }

    /** Game side: read bans.json and propagate new bans and unbans to Discord (sync and/or broadcast). */
    private void pollGameBans() {
        try {
            RelayConfig cfg = config.get();
            boolean sync = cfg.isBanSyncEnabled() && linkManager != null;
            boolean broadcast = cfg.isBanBroadcastEnabled();
            if (!sync && !broadcast) {
                return;
            }
            Map<UUID, BanInfo> current = readGameBans();
            // A read failure returns null (not an empty map) so a transient I/O or parse error does not look
            // like "every ban was lifted" and mass-unban or mass-announce everyone.
            if (current == null) {
                return;
            }
            for (Map.Entry<UUID, BanInfo> entry : current.entrySet()) {
                if (knownGameBans.add(entry.getKey())) {
                    if (sync) {
                        linkManager.getDiscordId(entry.getKey()).ifPresent(id -> banInDiscord(id, entry.getValue().reason()));
                    }
                    if (broadcast) {
                        broadcastBan(entry.getKey(), entry.getValue());
                    }
                }
            }
            knownGameBans.removeIf(uuid -> {
                if (!current.containsKey(uuid)) {
                    if (sync) {
                        linkManager.getDiscordId(uuid).ifPresent(this::unbanInDiscord);
                    }
                    if (broadcast) {
                        broadcastUnban(uuid);
                    }
                    return true;
                }
                return false;
            });
        } catch (Exception e) {
            logger.atWarning().log("Ban sync poll failed: %s", e.getMessage());
        }
    }

    private void broadcastBan(UUID target, BanInfo info) {
        String duration = info.expiresOn() != null ? "until " + DATE.format(Instant.ofEpochMilli(info.expiresOn())) : "permanently";
        String reason = info.reason() == null || info.reason().isBlank() ? "" : " (reason: " + info.reason() + ")";
        String message = config.get().getBanBroadcastFormat()
            .replace("%player%", nameResolver.apply(target))
            .replace("%duration%", duration)
            .replace("%by%", resolveBy(info.by()))
            .replace("%reason%", reason);
        broadcaster.accept(message);
    }

    private void broadcastUnban(UUID target) {
        broadcaster.accept(config.get().getUnbanBroadcastFormat().replace("%player%", nameResolver.apply(target)));
    }

    /** Bans issued from the console have no (or a zero) issuer uuid. */
    private String resolveBy(UUID by) {
        if (by == null || (by.getMostSignificantBits() == 0 && by.getLeastSignificantBits() == 0)) {
            return "the server";
        }
        return nameResolver.apply(by);
    }

    /** Returns the bans currently in effect, or null if the file could not be read or parsed. */
    private Map<UUID, BanInfo> readGameBans() {
        Map<UUID, BanInfo> out = new HashMap<>();
        if (!Files.exists(bansFile)) {
            return out;
        }
        long now = System.currentTimeMillis();
        try (Reader reader = Files.newBufferedReader(bansFile)) {
            JsonElement root = gson.fromJson(reader, JsonElement.class);
            if (root != null && root.isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray()) {
                    JsonObject ban = element.getAsJsonObject();
                    if (ban.has("target") && isInEffect(ban, now)) {
                        try {
                            UUID uuid = UUID.fromString(ban.get("target").getAsString());
                            Long expiresOn = ban.has("expiresOn") && !ban.get("expiresOn").isJsonNull() ? ban.get("expiresOn").getAsLong() : null;
                            out.put(uuid, new BanInfo(string(ban, "reason"), parseUuid(ban, "by"), expiresOn));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.atWarning().log("Could not read bans.json: %s", e.getMessage());
            return null;
        }
        return out;
    }

    /** A timed ban with an expiresOn in the past is no longer active; infinite bans have no expiresOn. */
    private static boolean isInEffect(JsonObject ban, long now) {
        return !ban.has("expiresOn") || ban.get("expiresOn").isJsonNull() || ban.get("expiresOn").getAsLong() > now;
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static UUID parseUuid(JsonObject o, String key) {
        String value = string(o, key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record BanInfo(String reason, UUID by, Long expiresOn) {}

    private void banInDiscord(String discordId, String reason) {
        JDA client = jda.get();
        if (client == null) {
            return;
        }
        String auditReason = reason == null || reason.isBlank() ? "Banned in game" : "Banned in game: " + reason;
        for (Guild guild : guilds(client)) {
            guild.ban(UserSnowflake.fromId(discordId), 0, TimeUnit.SECONDS).reason(auditReason)
                .queue(ok -> {}, err -> logger.atWarning().log("Failed to ban %s on Discord: %s", discordId, err.getMessage()));
        }
    }

    private void unbanInDiscord(String discordId) {
        JDA client = jda.get();
        if (client == null) {
            return;
        }
        for (Guild guild : guilds(client)) {
            guild.unban(UserSnowflake.fromId(discordId)).queue(ok -> {}, err -> {});
        }
    }

    /** The guild(s) to sync bans with: just the configured one if set, otherwise every guild the bot is in. */
    private List<Guild> guilds(JDA client) {
        String guildId = config.get().getBanSyncGuildId();
        if (guildId != null && !guildId.isBlank()) {
            Guild guild = client.getGuildById(guildId);
            return guild == null ? List.of() : List.of(guild);
        }
        return client.getGuilds();
    }
}
