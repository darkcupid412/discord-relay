package com.darkcupid412.relay;

import com.darkcupid412.relay.config.RelayConfig;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.joml.Vector3d;

/** Polls player positions and reports when a player goes idle (AFK) or moves again. */
public final class AfkTracker {
    private static final double MOVE_THRESHOLD_SQ = 0.25;
    private static final long POLL_SECONDS = 30L;

    private final Supplier<RelayConfig> config;
    private final BooleanSupplier active;
    private final HytaleLogger logger;
    private final BiConsumer<String, Boolean> announce;
    private final Map<UUID, double[]> lastPosition = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMoved = new ConcurrentHashMap<>();
    private final Set<UUID> afk = ConcurrentHashMap.newKeySet();
    private ScheduledFuture<?> task;

    public AfkTracker(Supplier<RelayConfig> config, BooleanSupplier active, HytaleLogger logger, BiConsumer<String, Boolean> announce) {
        this.config = config;
        this.active = active;
        this.logger = logger;
        this.announce = announce;
    }

    public void start() {
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(this::poll, POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        if (task != null) {
            task.cancel(false);
        }
    }

    private void poll() {
        try {
            if (!active.getAsBoolean() || Universe.get() == null) {
                return;
            }
            long now = System.currentTimeMillis();
            long timeoutMs = Math.max(1, config.get().getAfkTimeoutMinutes()) * 60_000L;
            Set<UUID> online = new HashSet<>();
            for (PlayerRef player : Universe.get().getPlayers()) {
                UUID uuid = player.getUuid();
                online.add(uuid);
                Vector3d pos = player.getTransform().getPosition();
                double[] previous = lastPosition.get(uuid);
                if (previous == null || distanceSq(previous, pos) > MOVE_THRESHOLD_SQ) {
                    lastPosition.put(uuid, new double[] {pos.x, pos.y, pos.z});
                    lastMoved.put(uuid, now);
                    if (afk.remove(uuid)) {
                        announce.accept(player.getUsername(), false);
                    }
                } else if (!afk.contains(uuid) && now - lastMoved.getOrDefault(uuid, now) >= timeoutMs) {
                    afk.add(uuid);
                    announce.accept(player.getUsername(), true);
                }
            }
            lastPosition.keySet().retainAll(online);
            lastMoved.keySet().retainAll(online);
            afk.retainAll(online);
        } catch (Exception e) {
            logger.atWarning().log("AFK poll failed: %s", e.getMessage());
        }
    }

    private static double distanceSq(double[] a, Vector3d b) {
        double dx = a[0] - b.x;
        double dy = a[1] - b.y;
        double dz = a[2] - b.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
