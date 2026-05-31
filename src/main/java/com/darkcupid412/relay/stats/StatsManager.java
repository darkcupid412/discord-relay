package com.darkcupid412.relay.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.joml.Vector3d;

/** Tracks and persists per-player statistics. Distance and playtime are sampled by a background poll. */
public final class StatsManager {
    private static final long POLL_SECONDS = 30L;
    private static final int SAVE_EVERY_POLLS = 4;
    private static final double MAX_STEP_SQ = 100.0 * 100.0;

    private final Path dataFile;
    private final HytaleLogger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, PlayerStats> stats = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();
    private final Map<UUID, double[]> lastPosition = new ConcurrentHashMap<>();
    private ScheduledFuture<?> task;
    private int pollCount;

    public StatsManager(Path dataDirectory, HytaleLogger logger) {
        this.dataFile = dataDirectory.resolve("player_stats.json");
        this.logger = logger;
        load();
    }

    public void start() {
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(this::poll, POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        if (task != null) {
            task.cancel(false);
        }
        flushSessions();
        save();
    }

    public PlayerStats get(UUID playerUuid) {
        return stats.computeIfAbsent(playerUuid, u -> new PlayerStats());
    }

    /** The stored username for a player we have tracked, or null if unknown (no side effect, unlike get). */
    public String peekUsername(UUID playerUuid) {
        PlayerStats ps = stats.get(playerUuid);
        return ps == null ? null : ps.getUsername();
    }

    public Map<UUID, PlayerStats> all() {
        return stats;
    }

    public void onConnect(UUID playerUuid, String username) {
        PlayerStats s = get(playerUuid);
        s.setUsername(username);
        long now = System.currentTimeMillis();
        if (s.getFirstJoin() == 0L) {
            s.setFirstJoin(now);
        }
        s.addJoin();
        s.setLastSeen(now);
        sessionStart.put(playerUuid, now);
    }

    public void onDisconnect(UUID playerUuid) {
        flushSession(playerUuid);
        get(playerUuid).setLastSeen(System.currentTimeMillis());
        sessionStart.remove(playerUuid);
        lastPosition.remove(playerUuid);
        save();
    }

    public void addBlockPlaced(UUID playerUuid) { get(playerUuid).addBlockPlaced(); }
    public void addBlockBroken(UUID playerUuid) { get(playerUuid).addBlockBroken(); }
    public void addDeath(UUID playerUuid) { get(playerUuid).addDeath(); }
    public void addKill(UUID playerUuid) { get(playerUuid).addKill(); }
    public void addMessage(UUID playerUuid) { get(playerUuid).addMessage(); }
    public void addZoneDiscovered(UUID playerUuid) { get(playerUuid).addZone(); }

    public boolean isOnline(UUID playerUuid) {
        return Universe.get() != null && Universe.get().getPlayer(playerUuid) != null;
    }

    /** Playtime including the current session for an online player. */
    public long livePlaytimeSeconds(UUID playerUuid) {
        long base = get(playerUuid).getPlaytimeSeconds();
        Long start = sessionStart.get(playerUuid);
        return start == null ? base : base + (System.currentTimeMillis() - start) / 1000L;
    }

    private void poll() {
        try {
            flushSessions();
            if (Universe.get() != null) {
                for (PlayerRef player : Universe.get().getPlayers()) {
                    accumulateDistance(player);
                    sampleHealth(player);
                }
            }
            if (++pollCount % SAVE_EVERY_POLLS == 0) {
                save();
            }
        } catch (Exception e) {
            logger.atWarning().log("Stats poll failed: %s", e.getMessage());
        }
    }

    private void accumulateDistance(PlayerRef player) {
        UUID uuid = player.getUuid();
        Vector3d pos = player.getTransform().getPosition();
        double[] prev = lastPosition.get(uuid);
        if (prev != null) {
            double dx = pos.x - prev[0];
            double dy = pos.y - prev[1];
            double dz = pos.z - prev[2];
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq <= MAX_STEP_SQ) {
                get(uuid).addDistance(Math.sqrt(sq));
            }
        }
        lastPosition.put(uuid, new double[] {pos.x, pos.y, pos.z});
    }

    /** Reads the player's health off the world thread and caches it, so /stats can show it without a blocking round trip. */
    private void sampleHealth(PlayerRef player) {
        UUID worldUuid = player.getWorldUuid();
        if (worldUuid == null) {
            return;
        }
        World world = Universe.get().getWorld(worldUuid);
        if (world == null) {
            return;
        }
        world.execute(() -> {
            try {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null) {
                    return;
                }
                EntityStatMap statMap = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
                if (statMap == null) {
                    return;
                }
                EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
                if (health != null) {
                    get(player.getUuid()).setHealth(health.get(), health.getMax());
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void flushSessions() {
        long now = System.currentTimeMillis();
        sessionStart.replaceAll((uuid, start) -> {
            accruePlaytime(uuid, (now - start) / 1000L);
            return now;
        });
    }

    private void flushSession(UUID playerUuid) {
        Long start = sessionStart.get(playerUuid);
        if (start != null) {
            accruePlaytime(playerUuid, (System.currentTimeMillis() - start) / 1000L);
            sessionStart.put(playerUuid, System.currentTimeMillis());
        }
    }

    /** Adds elapsed playtime to the total and to today's bucket, pruning buckets older than 8 days. */
    private void accruePlaytime(UUID playerUuid, long seconds) {
        if (seconds <= 0) {
            return;
        }
        PlayerStats s = get(playerUuid);
        s.addPlaytime(seconds);
        s.addDailyPlaytime(LocalDate.now().toString(), seconds);
        String cutoff = LocalDate.now().minusDays(32).toString();
        s.getDailyPlaytime().keySet().removeIf(date -> date.compareTo(cutoff) < 0);
    }

    /** Playtime over the last {@code days} days (including today), summed from the daily buckets. */
    public long recentPlaytimeSeconds(UUID playerUuid, int days) {
        String cutoff = LocalDate.now().minusDays(Math.max(0, days - 1)).toString();
        return get(playerUuid).getDailyPlaytime().entrySet().stream()
            .filter(e -> e.getKey().compareTo(cutoff) >= 0)
            .mapToLong(Map.Entry::getValue).sum();
    }

    private void load() {
        if (!Files.exists(dataFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Map<String, PlayerStats> data = gson.fromJson(reader, new TypeToken<Map<String, PlayerStats>>() {}.getType());
            if (data != null) {
                data.forEach((id, ps) -> {
                    try {
                        stats.put(UUID.fromString(id), ps);
                    } catch (IllegalArgumentException ignored) {
                    }
                });
            }
        } catch (Exception e) {
            // Broad on purpose: a corrupt or truncated file throws JsonSyntaxException, and that must not
            // abort plugin startup. Degrade to empty stats rather than failing to enable.
            logger.atWarning().log("Failed to load player stats: %s", e.getMessage());
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            Map<String, PlayerStats> data = new HashMap<>();
            stats.forEach((uuid, ps) -> data.put(uuid.toString(), ps));
            Path tmp = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                gson.toJson(data, writer);
            }
            try {
                Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.atWarning().log("Failed to save player stats: %s", e.getMessage());
        }
    }
}
