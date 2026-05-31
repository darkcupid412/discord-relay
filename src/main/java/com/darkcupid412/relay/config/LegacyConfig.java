package com.darkcupid412.relay.config;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/**
 * Maps a parsed 1.5.1 config.yml (nested sections) to the flat 2.0 config keys, so an upgrading user keeps
 * their settings. Only the one time first run migration uses this. Old options with no 2.0 equivalent (or a
 * changed meaning) are left out and fall back to the new defaults.
 */
final class LegacyConfig {
    /** Old keys intentionally not carried over (removed, or their meaning changed in 2.0). */
    static final List<String> DROPPED = List.of(
        "Discord.CommandPrefix", "Linking.KickTimeout", "Linking.RequireToPlayMessage",
        "AFK.MessageEnabled", "Debug.Enabled");

    private LegacyConfig() {
    }

    static JsonObject toJson(Map<String, Object> yaml) {
        JsonObject o = new JsonObject();
        str(o, yaml, "Discord", "Token", "DiscordToken");
        str(o, yaml, "Discord", "ChatChannelId", "ChatChannelId");
        str(o, yaml, "Discord", "ConsoleChannelId", "ConsoleChannelId");
        str(o, yaml, "Discord", "JoinLeaveChannelId", "JoinLeaveChannelId");
        bool(o, yaml, "Discord", "UseEmbeds", "UseEmbeds");
        str(o, yaml, "Discord", "ChatWebhookUrl", "ChatWebhookUrl");

        bool(o, yaml, "Features", "Chat", "ChatEnabled");
        bool(o, yaml, "Features", "JoinLeave", "JoinLeaveEnabled");
        bool(o, yaml, "Features", "Death", "DeathEnabled");
        bool(o, yaml, "Features", "ServerStatus", "ServerStatusEnabled");
        bool(o, yaml, "Features", "AccountLinking", "AccountLinkingEnabled");

        str(o, yaml, "Formats", "DiscordToGame", "DiscordToGameFormat");
        str(o, yaml, "Formats", "DiscordToGameColor", "DiscordToGameColor");
        str(o, yaml, "Formats", "Chat", "ChatFormat");
        str(o, yaml, "Formats", "Join", "JoinFormat");
        str(o, yaml, "Formats", "Leave", "LeaveFormat");
        str(o, yaml, "Formats", "Death", "DeathFormat");
        str(o, yaml, "Formats", "ServerStart", "ServerStartFormat");
        str(o, yaml, "Formats", "ServerStop", "ServerStopFormat");
        str(o, yaml, "Formats", "ConsoleReady", "ConsoleReadyFormat");

        str(o, yaml, "Console", "CommandBlacklist", "CommandBlacklist");

        bool(o, yaml, "Linking", "RequireToChat", "LinkingRequireToChat");
        bool(o, yaml, "Linking", "ShowIngameName", "LinkingShowIngameName");
        str(o, yaml, "Linking", "LinkedRoleId", "LinkedRoleId");
        bool(o, yaml, "Linking", "SyncNickname", "SyncNickname");
        str(o, yaml, "Linking", "NicknameFormat", "NicknameFormat");
        bool(o, yaml, "Linking", "RequireToPlay", "LinkingRequireToPlay");
        bool(o, yaml, "Linking", "ShowDiscordNameIngame", "ShowDiscordNameIngame");

        // In 1.5.1 the filter had its own on/off flag; in 2.0 it is on whenever ChatFilterWords is set.
        if (boolVal(yaml, "Chat", "FilterEnabled")) {
            str(o, yaml, "Chat", "FilteredWords", "ChatFilterWords");
        }
        str(o, yaml, "Chat", "FilterMode", "ChatFilterMode");

        bool(o, yaml, "Avatar", "Enabled", "AvatarEnabled");
        str(o, yaml, "Avatar", "Style", "AvatarStyle");

        bool(o, yaml, "AFK", "Enabled", "AfkEnabled");
        intg(o, yaml, "AFK", "TimeoutMinutes", "AfkTimeoutMinutes");

        bool(o, yaml, "Stats", "Enabled", "StatsEnabled");
        return o;
    }

    @SuppressWarnings("unchecked")
    private static Object raw(Map<String, Object> yaml, String section, String key) {
        Object s = yaml.get(section);
        return s instanceof Map<?, ?> map ? ((Map<String, Object>) map).get(key) : null;
    }

    private static void str(JsonObject o, Map<String, Object> yaml, String section, String key, String newKey) {
        Object v = raw(yaml, section, key);
        if (v != null) {
            o.addProperty(newKey, v.toString());
        }
    }

    private static void bool(JsonObject o, Map<String, Object> yaml, String section, String key, String newKey) {
        Object v = raw(yaml, section, key);
        if (v instanceof Boolean b) {
            o.addProperty(newKey, b);
        }
    }

    private static boolean boolVal(Map<String, Object> yaml, String section, String key) {
        return raw(yaml, section, key) instanceof Boolean b && b;
    }

    private static void intg(JsonObject o, Map<String, Object> yaml, String section, String key, String newKey) {
        Object v = raw(yaml, section, key);
        if (v instanceof Number n) {
            o.addProperty(newKey, n.intValue());
        }
    }
}
