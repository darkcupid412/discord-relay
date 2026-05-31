package com.darkcupid412.relay.link;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Links Discord accounts to in game accounts via short codes, and persists the links to disk. */
public final class AccountLinkManager {
    public interface LinkListener {
        void onLink(UUID playerUuid, String discordId, String discordName);

        void onUnlink(UUID playerUuid, String discordId);
    }

    public enum LinkResult {
        SUCCESS, INVALID_CODE, CODE_EXPIRED, DISCORD_ALREADY_LINKED, PLAYER_ALREADY_LINKED
    }

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final long CODE_EXPIRY_MS = 300_000L;

    private final Path dataFile;
    private final HytaleLogger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, UUID> discordToPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerToDiscord = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerToDiscordName = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerToName = new ConcurrentHashMap<>();
    private final Map<String, PendingLink> pending = new ConcurrentHashMap<>();
    private final List<LinkListener> listeners = new CopyOnWriteArrayList<>();

    public AccountLinkManager(Path dataDirectory, HytaleLogger logger) {
        this.dataFile = dataDirectory.resolve("linked_accounts.json");
        this.logger = logger;
        load();
    }

    public void addListener(LinkListener listener) {
        listeners.add(listener);
    }

    public String generateCode(UUID playerUuid, String playerName) {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> e.getValue().playerUuid.equals(playerUuid) || now > e.getValue().expiresAt);
        String code = createCode();
        pending.put(code, new PendingLink(playerUuid, playerName, now + CODE_EXPIRY_MS));
        return code;
    }

    public synchronized LinkResult processCode(String code, String discordId, String discordName) {
        PendingLink p = pending.remove(code.toUpperCase());
        if (p == null) {
            return LinkResult.INVALID_CODE;
        }
        if (System.currentTimeMillis() > p.expiresAt) {
            return LinkResult.CODE_EXPIRED;
        }
        if (discordToPlayer.containsKey(discordId)) {
            return LinkResult.DISCORD_ALREADY_LINKED;
        }
        if (playerToDiscord.containsKey(p.playerUuid)) {
            return LinkResult.PLAYER_ALREADY_LINKED;
        }
        discordToPlayer.put(discordId, p.playerUuid);
        playerToDiscord.put(p.playerUuid, discordId);
        if (discordName != null) {
            playerToDiscordName.put(p.playerUuid, discordName);
        }
        if (p.playerName != null) {
            playerToName.put(p.playerUuid, p.playerName);
        }
        save();
        listeners.forEach(l -> l.onLink(p.playerUuid, discordId, discordName));
        return LinkResult.SUCCESS;
    }

    public synchronized boolean unlinkByPlayer(UUID playerUuid) {
        String discordId = playerToDiscord.remove(playerUuid);
        if (discordId == null) {
            return false;
        }
        discordToPlayer.remove(discordId);
        playerToDiscordName.remove(playerUuid);
        playerToName.remove(playerUuid);
        save();
        listeners.forEach(l -> l.onUnlink(playerUuid, discordId));
        return true;
    }

    public synchronized boolean unlinkByDiscord(String discordId) {
        UUID playerUuid = discordToPlayer.remove(discordId);
        if (playerUuid == null) {
            return false;
        }
        playerToDiscord.remove(playerUuid);
        playerToDiscordName.remove(playerUuid);
        playerToName.remove(playerUuid);
        save();
        listeners.forEach(l -> l.onUnlink(playerUuid, discordId));
        return true;
    }

    public Optional<UUID> getPlayerUuid(String discordId) {
        return Optional.ofNullable(discordToPlayer.get(discordId));
    }

    public Optional<String> getDiscordId(UUID playerUuid) {
        return Optional.ofNullable(playerToDiscord.get(playerUuid));
    }

    public Optional<String> getDiscordName(UUID playerUuid) {
        return Optional.ofNullable(playerToDiscordName.get(playerUuid));
    }

    public Optional<String> getIngameName(String discordId) {
        UUID uuid = discordToPlayer.get(discordId);
        return uuid == null ? Optional.empty() : Optional.ofNullable(playerToName.get(uuid));
    }

    public Optional<String> getCachedName(UUID playerUuid) {
        return Optional.ofNullable(playerToName.get(playerUuid));
    }

    public synchronized void refreshIngameName(UUID playerUuid, String name) {
        if (name == null || !playerToDiscord.containsKey(playerUuid)) {
            return;
        }
        if (!name.equals(playerToName.get(playerUuid))) {
            playerToName.put(playerUuid, name);
            save();
        }
    }

    public boolean isLinked(String discordId) {
        return discordToPlayer.containsKey(discordId);
    }

    public boolean isLinked(UUID playerUuid) {
        return playerToDiscord.containsKey(playerUuid);
    }

    private String createCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (pending.containsKey(code));
        return code;
    }

    private void load() {
        if (!Files.exists(dataFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Map<String, LinkData> data = gson.fromJson(reader, new TypeToken<Map<String, LinkData>>() {}.getType());
            if (data != null) {
                data.forEach((discordId, ld) -> {
                    try {
                        UUID uuid = UUID.fromString(ld.uuid);
                        discordToPlayer.put(discordId, uuid);
                        playerToDiscord.put(uuid, discordId);
                        if (ld.username != null) {
                            playerToDiscordName.put(uuid, ld.username);
                        }
                        if (ld.ign != null) {
                            playerToName.put(uuid, ld.ign);
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                });
            }
        } catch (Exception e) {
            // Broad on purpose: a corrupt or truncated file throws JsonSyntaxException, which must not abort
            // plugin startup. Better to start with no links than to fail to enable.
            logger.atWarning().log("Failed to load linked accounts: %s", e.getMessage());
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            Map<String, LinkData> data = new HashMap<>();
            discordToPlayer.forEach((discordId, uuid) -> data.put(discordId, new LinkData(uuid.toString(), playerToDiscordName.get(uuid), playerToName.get(uuid))));
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
            logger.atWarning().log("Failed to save linked accounts: %s", e.getMessage());
        }
    }

    private static final class PendingLink {
        final UUID playerUuid;
        final String playerName;
        final long expiresAt;

        PendingLink(UUID playerUuid, String playerName, long expiresAt) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.expiresAt = expiresAt;
        }
    }

    private static final class LinkData {
        String uuid;
        String username;
        String ign;

        LinkData() {
        }

        LinkData(String uuid, String username, String ign) {
            this.uuid = uuid;
            this.username = username;
            this.ign = ign;
        }
    }
}
