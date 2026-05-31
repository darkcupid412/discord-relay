package com.darkcupid412.relay.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.BsonUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.BsonDocument;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads and writes config.yml as commented, self-documenting YAML. The engine codec stays the type-safe
 * source of truth: YAML is parsed to a map and decoded through {@link RelayConfig#CODEC}, and on write the
 * codec's values are rendered back as commented YAML. Only the comments and section order live here.
 *
 * <p>Upgrades are handled on first load: a 1.5.1 nested config.yml is converted to the flat 2.0 keys, and a
 * config.json from the interim JSON format is converted to config.yml. The old file is always kept (renamed
 * .old or copied .bak) and existing values are preserved.
 */
public final class ConfigStore {
    private record Section(String title, String[][] keys) {}

    // Section title, then {key, comment} pairs. Keys not listed here are still written (under "Other"), so a
    // forgotten entry never drops an option; keys listed but absent from the schema are simply skipped.
    private static final Section[] LAYOUT = {
        new Section("Discord connection", new String[][]{
            {"DiscordToken", "Bot token from the Discord Developer Portal. Keep this secret."},
            {"ChatChannelId", "Channel ID for the two way chat relay. Required."},
            {"ConsoleChannelId", "Channel ID for the server console relay. Empty disables it."},
            {"JoinLeaveChannelId", "Channel ID for join and leave messages. Empty uses the chat channel."},
        }),
        new Section("Feature toggles", new String[][]{
            {"ChatEnabled", "Relay chat between the game and Discord."},
            {"JoinLeaveEnabled", "Announce players joining and leaving."},
            {"DeathEnabled", "Announce player deaths."},
            {"ServerStatusEnabled", "Announce server start and stop."},
            {"AccountLinkingEnabled", "Enable /link account linking and the features that depend on it."},
            {"StatsEnabled", "Track per player stats (playtime, blocks, deaths, kills, messages, zones)."},
            {"AfkEnabled", "Announce when a player goes AFK and when they return."},
            {"LiveStatusEnabled", "Keep one live updating status embed instead of repeated messages."},
            {"UpdateCheckEnabled", "Check GitHub for a newer plugin release on startup."},
        }),
        new Section("Message formats (placeholders: %player%, %message%)", new String[][]{
            {"ChatFormat", "Game chat as it appears in Discord."},
            {"JoinFormat", "Player join message."},
            {"LeaveFormat", "Player leave message."},
            {"DeathFormat", "Death message used when DeathMessages is empty. %message% is the game's death text."},
            {"DeathMessages", "Random pool of death formats, one picked per death. Empty falls back to DeathFormat."},
            {"DiscordToGameFormat", "Discord message as it appears in game."},
            {"DiscordToGameColor", "Color for the in game Discord line: a name like aqua or hex like #7289da. Empty for default."},
            {"ServerStartFormat", "Server start message."},
            {"ServerStopFormat", "Server stop message."},
        }),
        new Section("Embeds", new String[][]{
            {"UseEmbeds", "Master switch for rich embeds. Off sends plain text everywhere."},
            {"JoinLeaveEmbed", "Use an embed for join and leave (combined with UseEmbeds)."},
            {"DeathEmbed", "Use an embed for deaths (combined with UseEmbeds)."},
            {"ServerStatusEmbed", "Use an embed for server start and stop (combined with UseEmbeds)."},
        }),
        new Section("Webhook and avatars", new String[][]{
            {"ChatWebhookUrl", "Webhook URL to relay game chat as the player (name and avatar). Empty auto creates one if enabled below."},
            {"ChatWebhookAutoCreate", "Create a chat webhook automatically when ChatWebhookUrl is empty. Needs the Manage Webhooks permission."},
            {"AvatarEnabled", "Show player avatars on messages."},
            {"AvatarStyle", "Avatar style for join and leave embeds: compact, thumbnail, or large."},
        }),
        new Section("Chat filter", new String[][]{
            {"ChatFilterWords", "Comma separated words to filter from relayed chat (both directions). Empty disables it."},
            {"ChatFilterReplacement", "Replacement for filtered words in censor mode."},
            {"ChatFilterMode", "censor (replace the word) or block (drop the whole message)."},
        }),
        new Section("Console relay", new String[][]{
            {"ConsoleAllowedCommands", "Comma separated allowlist of commands the Discord console may run (default deny)."},
            {"CommandBlacklist", "Comma separated commands that can never run from the Discord console."},
            {"ConsoleRoleId", "Role allowed to use the console channel. Empty requires Discord Administrator."},
            {"ConsoleReadyFormat", "Message posted in the console channel when the bot is ready."},
        }),
        new Section("AFK detection", new String[][]{
            {"AfkTimeoutMinutes", "Minutes without movement before a player is marked AFK."},
            {"AfkFormat", "Message when a player goes AFK."},
            {"AfkReturnFormat", "Message when a player returns from AFK."},
        }),
        new Section("Account linking", new String[][]{
            {"LinkedRoleId", "Comma separated Discord role ID(s) granted on link and removed on unlink."},
            {"SyncNickname", "Set the linked member's Discord nickname to their in game name."},
            {"NicknameFormat", "Nickname format when SyncNickname is on. %player% is the in game name."},
            {"LinkingRequireToChat", "Drop Discord to game messages from users who have not linked."},
            {"LinkingShowIngameName", "Relay linked users under their in game name instead of their Discord name."},
            {"LinkingRequireToPlay", "Require a linked account to play. Unlinked players get a code and are kicked after the grace period."},
            {"NotLinkedKickFormat", "Shown to an unlinked player under require to play. %code% is their link code."},
            {"UnlinkedKickFormat", "Shown when a player is kicked for unlinking while online."},
            {"LinkingBypassNames", "Comma separated player names exempt from require to play."},
            {"LinkingGraceSeconds", "Seconds an unlinked player has to link before being kicked."},
        }),
        new Section("Discord name in game", new String[][]{
            {"ShowDiscordNameIngame", "Show a linked player's Discord name in game: map marker, nameplate, and chat."},
        }),
        new Section("Live status embed", new String[][]{
            {"LiveStatusChannelId", "Channel for the live status embed. Empty uses the chat channel."},
            {"LiveStatusIntervalSeconds", "Seconds between live status updates. Minimum 15."},
        }),
        new Section("Updates", new String[][]{
            {"UpdateRepo", "GitHub owner/repo to check for new releases."},
        }),
        new Section("Ban sync (needs account linking)", new String[][]{
            {"BanSyncEnabled", "Two way ban sync: a game ban bans the linked Discord user and a Discord ban blocks the player."},
            {"BanSyncDiscordBanReason", "Message shown when a Discord banned player is blocked from joining."},
            {"BanSyncGuildId", "Restrict ban sync to one guild ID. Empty uses every guild the bot is in."},
        }),
        new Section("Ban broadcast", new String[][]{
            {"BanBroadcastEnabled", "Announce new bans and unbans (read from the game's ban list) to the chat channel."},
            {"BanBroadcastFormat", "Ban announcement. Placeholders: %player%, %duration%, %by%, %reason%."},
            {"UnbanBroadcastFormat", "Unban announcement. Placeholder: %player%."},
        }),
        new Section("Zone discovery broadcast", new String[][]{
            {"ZoneDiscoveryBroadcastEnabled", "Announce when a player discovers a new zone."},
            {"ZoneDiscoveryFormat", "Zone discovery announcement. Placeholders: %player%, %zone%."},
        }),
    };

    private final Path file;
    private final HytaleLogger logger;
    private RelayConfig config;

    public ConfigStore(Path dataDir, HytaleLogger logger) {
        this.file = dataDir.resolve("config.yml");
        this.logger = logger;
    }

    public RelayConfig get() {
        return config;
    }

    public void load() {
        if (Files.exists(file)) {
            loadYaml();
            return;
        }
        Path json = file.resolveSibling("config.json");
        if (Files.exists(json) && migrateFromJson(json)) {
            return;
        }
        config = RelayConfig.CODEC.getDefaultValue();
        write(config);
        logger.atInfo().log("Created default config.yml");
    }

    private void loadYaml() {
        String text;
        Map<String, Object> data;
        try {
            text = Files.readString(file);
            data = new Yaml().load(text);
        } catch (Exception e) {
            logger.atSevere().log("config.yml could not be read, using defaults (file left unchanged): %s", e.getMessage());
            config = RelayConfig.CODEC.getDefaultValue();
            return;
        }
        if (data == null) {
            data = Map.of();
        }
        if (isLegacy(data)) {
            migrateFromLegacy(data);
            return;
        }
        try {
            config = decode(new Gson().toJson(data));
        } catch (Exception e) {
            logger.atSevere().log("config.yml could not be parsed, using defaults (file left unchanged): %s", e.getMessage());
            config = RelayConfig.CODEC.getDefaultValue();
            return;
        }
        migrateIfNeeded(data, text);
    }

    /** A 1.5.1 config.yml nests options under section maps (Discord:, Features:); 2.0 uses flat keys. */
    private static boolean isLegacy(Map<String, Object> data) {
        return data.get("Discord") instanceof Map || data.get("Features") instanceof Map;
    }

    private void migrateFromLegacy(Map<String, Object> data) {
        try {
            config = decode(new Gson().toJson(LegacyConfig.toJson(data)));
        } catch (Exception e) {
            logger.atWarning().log("Could not migrate the 1.5.1 config.yml; using defaults: %s", e.getMessage());
            config = RelayConfig.CODEC.getDefaultValue();
            return;
        }
        backup(file, "config.yml.old");
        write(config);
        logger.atInfo().log("Migrated 1.5.1 config.yml to the 2.0 format (original kept as config.yml.old). "
            + "These removed or changed options reset to defaults: %s", String.join(", ", LegacyConfig.DROPPED));
    }

    /** Converts the interim JSON era config.json to config.yml, retiring the old file. */
    private boolean migrateFromJson(Path json) {
        try {
            config = decode(stripComments(Files.readString(json)));
        } catch (Exception e) {
            logger.atWarning().log("Could not migrate config.json; a fresh config.yml will be written: %s", e.getMessage());
            return false;
        }
        write(config);
        try {
            Files.move(json, json.resolveSibling("config.json.old"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            logger.atWarning().log("Migrated config.json but could not rename it to config.json.old: %s", e.getMessage());
        }
        logger.atInfo().log("Migrated config.json to config.yml (original kept as config.json.old)");
        return true;
    }

    /** Adds keys a newer version introduced, or rewrites a file that is not yet commented; backs up first. */
    private void migrateIfNeeded(Map<String, Object> data, String text) {
        List<String> missing = new ArrayList<>();
        for (String key : RelayConfig.CODEC.getEntries().keySet()) {
            if (!data.containsKey(key)) {
                missing.add(key);
            }
        }
        boolean uncommented = !text.contains("# =====");
        if (missing.isEmpty() && !uncommented) {
            return;
        }
        backup(file, "config.yml.bak");
        write(config);
        if (!missing.isEmpty()) {
            logger.atInfo().log("config.yml migrated: added %d new key(s) at defaults (backup at config.yml.bak): %s",
                missing.size(), String.join(", ", missing));
        } else {
            logger.atInfo().log("config.yml rewritten with documentation comments (backup at config.yml.bak)");
        }
    }

    private RelayConfig decode(String json) throws Exception {
        return RelayConfig.CODEC.decodeJson(new RawJsonReader(json.strip().toCharArray()), new ExtraInfo());
    }

    private void backup(Path src, String backupName) {
        try {
            Files.copy(src, src.resolveSibling(backupName), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            logger.atWarning().log("Could not back up %s: %s", src.getFileName(), e.getMessage());
        }
    }

    private void write(RelayConfig cfg) {
        try {
            Files.writeString(file, render(cfg));
        } catch (Exception e) {
            logger.atWarning().log("Failed to write config.yml: %s", e.getMessage());
        }
    }

    /** Renders the config as commented YAML, values pulled from the codec so every type serializes correctly. */
    static String render(RelayConfig cfg) {
        BsonDocument encoded = RelayConfig.CODEC.encode(cfg, new ExtraInfo());
        JsonObject values = JsonParser.parseString(encoded.toJson(BsonUtil.SETTINGS)).getAsJsonObject();

        StringBuilder sb = new StringBuilder(4096);
        sb.append("# DiscordRelay configuration\n");
        Set<String> emitted = new LinkedHashSet<>();
        for (Section section : LAYOUT) {
            List<String[]> present = new ArrayList<>();
            for (String[] entry : section.keys()) {
                if (values.has(entry[0]) && emitted.add(entry[0])) {
                    present.add(entry);
                }
            }
            if (present.isEmpty()) {
                continue;
            }
            sb.append("\n# ===== ").append(section.title()).append(" =====\n\n");
            for (String[] entry : present) {
                appendEntry(sb, entry[0], entry[1], values);
            }
        }
        List<String[]> other = new ArrayList<>();
        for (String key : values.keySet()) {
            if (emitted.add(key)) {
                other.add(new String[]{key, null});
            }
        }
        if (!other.isEmpty()) {
            sb.append("\n# ===== Other =====\n\n");
            for (String[] entry : other) {
                appendEntry(sb, entry[0], entry[1], values);
            }
        }
        return sb.toString();
    }

    private static void appendEntry(StringBuilder sb, String key, String comment, JsonObject values) {
        if (comment != null) {
            sb.append("# ").append(comment).append('\n');
        }
        JsonElement value = values.get(key);
        if (value != null && value.isJsonArray()) {
            if (value.getAsJsonArray().isEmpty()) {
                sb.append(key).append(": []\n\n");
                return;
            }
            sb.append(key).append(":\n");
            for (JsonElement element : value.getAsJsonArray()) {
                sb.append("  - ").append(scalar(element)).append('\n');
            }
            sb.append('\n');
            return;
        }
        sb.append(key).append(": ").append(scalar(value)).append("\n\n");
    }

    /** A single YAML scalar. Strings are double quoted and escaped so emoji, colons, and # are safe. */
    private static String scalar(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "\"\"";
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isString()) {
            String s = primitive.getAsString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
            return '"' + s + '"';
        }
        return primitive.getAsString();
    }

    /** Removes // line and block comments from JSON, leaving anything inside string values intact. */
    static String stripComments(String json) {
        StringBuilder out = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
                continue;
            }
            if (c == '/' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == '/') {
                    i += 2;
                    while (i < json.length() && json.charAt(i) != '\n') {
                        i++;
                    }
                    if (i < json.length()) {
                        out.append('\n');
                    }
                    continue;
                }
                if (next == '*') {
                    i += 2;
                    while (i + 1 < json.length() && !(json.charAt(i) == '*' && json.charAt(i + 1) == '/')) {
                        i++;
                    }
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
