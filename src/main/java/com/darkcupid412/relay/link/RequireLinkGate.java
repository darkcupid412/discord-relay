package com.darkcupid412.relay.link;

import com.darkcupid412.relay.config.RelayConfig;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Require a linked account to play. Denies an unlinked player's connection with a one time link code,
 * and kicks a player who unlinks while online. Modeled on DiscordSRV's login gate, which never freezes
 * the player in world, it just refuses the connection (Hytale has no movement lock anyway).
 */
public final class RequireLinkGate implements AccountLinkManager.LinkListener {
    private static final long MESSAGE_DELAY_SECONDS = 4L;
    private static final long TITLE_REFRESH_SECONDS = 8L;

    private final AccountLinkManager linkManager;
    private final Supplier<RelayConfig> config;
    private final BooleanSupplier active;
    private final Map<UUID, List<ScheduledFuture<?>>> pendingTasks = new ConcurrentHashMap<>();

    public RequireLinkGate(AccountLinkManager linkManager, Supplier<RelayConfig> config, BooleanSupplier active) {
        this.linkManager = linkManager;
        this.config = config;
        this.active = active;
    }

    public void onPlayerConnect(PlayerConnectEvent event) {
        RelayConfig cfg = config.get();
        // Fail open while the bot is offline: with no way to link, do not lock players out.
        if (!cfg.isLinkingRequireToPlay() || !active.getAsBoolean()) {
            return;
        }
        PlayerRef player = event.getPlayerRef();
        if (cfg.getLinkingBypassNames().contains(player.getUsername().toLowerCase())) {
            return;
        }
        UUID uuid = player.getUuid();
        if (linkManager.isLinked(uuid)) {
            return;
        }
        // A reconnect supersedes the previous attempt's timers. Without this, a kick scheduled by an
        // earlier connection fires mid-load on the new session and shows a bare "Connection closed", and
        // it would carry a code that generateCode has already invalidated.
        cancelPending(uuid);
        // The disconnect screen will not render a plugin reason in this client build, so the code is shown
        // in world once the player has spawned: a chat line plus a repeated on-screen title, then a kick
        // after the grace period if they still have not linked. Linking during the window lets them stay.
        String code = linkManager.generateCode(uuid, player.getUsername());
        String message = cfg.getNotLinkedKickFormat().replace("%code%", code);
        long grace = Math.max(MESSAGE_DELAY_SECONDS + 1, cfg.getLinkingGraceSeconds());
        List<ScheduledFuture<?>> tasks = new ArrayList<>(3);
        tasks.add(HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> deliver(uuid, message), MESSAGE_DELAY_SECONDS, TimeUnit.SECONDS));
        tasks.add(HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> showTitle(uuid, code), MESSAGE_DELAY_SECONDS, TITLE_REFRESH_SECONDS, TimeUnit.SECONDS));
        tasks.add(HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> kick(uuid, message), grace, TimeUnit.SECONDS));
        pendingTasks.put(uuid, tasks);
    }

    private void cancelPending(UUID uuid) {
        List<ScheduledFuture<?>> tasks = pendingTasks.remove(uuid);
        if (tasks != null) {
            tasks.forEach(task -> task.cancel(false));
        }
    }

    private void deliver(UUID uuid, String message) {
        PlayerRef player = online(uuid);
        if (player != null && !linkManager.isLinked(uuid)) {
            player.sendMessage(Message.raw(message).color("#FFAA00"));
        }
    }

    /** Re-shown on a timer so it stays on screen for the whole grace window. */
    private void showTitle(UUID uuid, String code) {
        PlayerRef player = online(uuid);
        if (player == null || linkManager.isLinked(uuid)) {
            return;
        }
        EventTitleUtil.showEventTitleToPlayer(
            player,
            Message.raw("Discord Link Required: " + code).color("#FFAA00"),
            Message.raw("Run /link in Discord to keep playing"),
            true, null, 10.0F, 0.5F, 0.5F);
    }

    private void kick(UUID uuid, String message) {
        cancelPending(uuid);
        PlayerRef player = online(uuid);
        if (player != null && !linkManager.isLinked(uuid)) {
            disconnect(player, message);
        }
    }

    private void disconnect(PlayerRef player, String message) {
        // The client does not render a plugin's disconnect reason on the disconnect screen for an in-world
        // kick (it always shows "Connection closed"), confirmed against raw text and bundled translation
        // keys alike. The code is delivered in game chat instead; this just enforces the kick on the world
        // thread (which avoids the world-store removal timeout).
        UUID worldUuid = player.getWorldUuid();
        World world = worldUuid == null || Universe.get() == null ? null : Universe.get().getWorld(worldUuid);
        Runnable close = () -> player.getPacketHandler().disconnect(Message.raw(message));
        if (world != null) {
            world.execute(close);
        } else {
            close.run();
        }
    }

    private static PlayerRef online(UUID uuid) {
        return Universe.get() == null ? null : Universe.get().getPlayer(uuid);
    }

    public boolean shouldRestrict(PlayerRef player) {
        RelayConfig cfg = config.get();
        if (!cfg.isLinkingRequireToPlay() || !active.getAsBoolean()) {
            return false;
        }
        if (cfg.getLinkingBypassNames().contains(player.getUsername().toLowerCase())) {
            return false;
        }
        return !linkManager.isLinked(player.getUuid());
    }

    @Override
    public void onLink(UUID playerUuid, String discordId, String discordName) {
        // Linked during the grace window: stop the prompts, hide the title, and keep them connected.
        cancelPending(playerUuid);
        PlayerRef player = online(playerUuid);
        if (player != null) {
            EventTitleUtil.hideEventTitleFromPlayer(player, 0.5F);
        }
    }

    @Override
    public void onUnlink(UUID playerUuid, String discordId) {
        if (!config.get().isLinkingRequireToPlay()) {
            return;
        }
        PlayerRef player = online(playerUuid);
        if (player != null) {
            disconnect(player, config.get().getUnlinkedKickFormat());
        }
    }
}
