package com.darkcupid412.relay.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Plugin configuration. The codec drives loading, saving, and schema generation, so adding a field
 * here is all that is needed to expose a new option. Missing keys fall back to the defaults set below.
 */
public final class RelayConfig {
    private String discordToken = "";
    private String chatChannelId = "";
    private String consoleChannelId = "";
    private String joinLeaveChannelId = "";
    private boolean chatEnabled = true;
    private boolean joinLeaveEnabled = true;
    private boolean deathEnabled = true;
    private boolean serverStatusEnabled = true;
    private boolean accountLinkingEnabled = true;
    private String chatFormat = "**%player%**: %message%";
    private String joinFormat = ":green_circle: **%player%** joined the server";
    private String leaveFormat = ":red_circle: **%player%** left the server";
    private String deathFormat = ":skull: %message%";
    private String[] deathMessages = {
        ":skull: %message%",
        ":skull_and_crossbones: %message%",
        ":coffin: %message% Better luck next time.",
        ":ghost: %message%"
    };
    private String discordToGameFormat = "[Discord] %player%: %message%";
    private String discordToGameColor = "";
    private String serverStartFormat = ":white_check_mark: **Server started**";
    private String serverStopFormat = ":octagonal_sign: **Server stopped**";
    private boolean useEmbeds = true;
    private boolean joinLeaveEmbed = true;
    private boolean deathEmbed = true;
    private boolean serverStatusEmbed = true;
    private String chatWebhookUrl = "";
    private boolean chatWebhookAutoCreate = true;
    private String chatFilterWords = "";
    private String chatFilterReplacement = "***";
    private String chatFilterMode = "censor";
    private boolean avatarEnabled = true;
    private String avatarStyle = "thumbnail";
    private String commandBlacklist = "stop,restart,op,ban,whitelist,kick";
    private String consoleAllowedCommands = "players,list,online,help";
    private String consoleRoleId = "";
    private String consoleReadyFormat = ":keyboard: **Console ready** - type commands here";
    private int afkTimeoutMinutes = 5;
    private boolean afkEnabled = false;
    private String afkFormat = ":zzz: **%player%** is now AFK";
    private String afkReturnFormat = ":wave: **%player%** is no longer AFK";
    private String linkedRoleId = "";
    private boolean syncNickname = false;
    private String nicknameFormat = "%player%";
    private boolean linkingRequireToChat = false;
    private boolean linkingShowIngameName = false;
    private boolean showDiscordNameIngame = false;
    private boolean linkingRequireToPlay = false;
    private String notLinkedKickFormat = "You must link your Discord account to play - run /link %code% in Discord.";
    private String unlinkedKickFormat = "Your Discord account was unlinked. Reconnect to link again.";
    private String linkingBypassNames = "";
    private int linkingGraceSeconds = 30;
    private boolean liveStatusEnabled = false;
    private String liveStatusChannelId = "";
    private int liveStatusIntervalSeconds = 60;
    private boolean updateCheckEnabled = false;
    private String updateRepo = "darkcupid412/discord-relay";
    private boolean statsEnabled = false;
    private boolean banSyncEnabled = false;
    private String banSyncDiscordBanReason = "You are banned from this server's Discord.";
    private String banSyncGuildId = "";
    private boolean zoneDiscoveryBroadcastEnabled = false;
    private String zoneDiscoveryFormat = ":map: %player% discovered %zone%";
    private boolean banBroadcastEnabled = false;
    private String banBroadcastFormat = ":hammer: %player% was banned %duration% by %by%%reason%";
    private String unbanBroadcastFormat = ":unlock: %player% was unbanned";

    public static final BuilderCodec<RelayConfig> CODEC = BuilderCodec.builder(RelayConfig.class, RelayConfig::new)
        .append(new KeyedCodec<>("DiscordToken", Codec.STRING), (c, v) -> c.discordToken = v, c -> c.discordToken).add()
        .append(new KeyedCodec<>("ChatChannelId", Codec.STRING), (c, v) -> c.chatChannelId = v, c -> c.chatChannelId).add()
        .append(new KeyedCodec<>("ConsoleChannelId", Codec.STRING), (c, v) -> c.consoleChannelId = v, c -> c.consoleChannelId).add()
        .append(new KeyedCodec<>("JoinLeaveChannelId", Codec.STRING), (c, v) -> c.joinLeaveChannelId = v, c -> c.joinLeaveChannelId).add()
        .append(new KeyedCodec<>("ChatEnabled", Codec.BOOLEAN), (c, v) -> c.chatEnabled = v, c -> c.chatEnabled).add()
        .append(new KeyedCodec<>("JoinLeaveEnabled", Codec.BOOLEAN), (c, v) -> c.joinLeaveEnabled = v, c -> c.joinLeaveEnabled).add()
        .append(new KeyedCodec<>("DeathEnabled", Codec.BOOLEAN), (c, v) -> c.deathEnabled = v, c -> c.deathEnabled).add()
        .append(new KeyedCodec<>("ServerStatusEnabled", Codec.BOOLEAN), (c, v) -> c.serverStatusEnabled = v, c -> c.serverStatusEnabled).add()
        .append(new KeyedCodec<>("AccountLinkingEnabled", Codec.BOOLEAN), (c, v) -> c.accountLinkingEnabled = v, c -> c.accountLinkingEnabled).add()
        .append(new KeyedCodec<>("ChatFormat", Codec.STRING), (c, v) -> c.chatFormat = v, c -> c.chatFormat).add()
        .append(new KeyedCodec<>("JoinFormat", Codec.STRING), (c, v) -> c.joinFormat = v, c -> c.joinFormat).add()
        .append(new KeyedCodec<>("LeaveFormat", Codec.STRING), (c, v) -> c.leaveFormat = v, c -> c.leaveFormat).add()
        .append(new KeyedCodec<>("DeathFormat", Codec.STRING), (c, v) -> c.deathFormat = v, c -> c.deathFormat).add()
        .append(new KeyedCodec<>("DeathMessages", Codec.STRING_ARRAY), (c, v) -> c.deathMessages = v, c -> c.deathMessages).add()
        .append(new KeyedCodec<>("DiscordToGameFormat", Codec.STRING), (c, v) -> c.discordToGameFormat = v, c -> c.discordToGameFormat).add()
        .append(new KeyedCodec<>("DiscordToGameColor", Codec.STRING), (c, v) -> c.discordToGameColor = v, c -> c.discordToGameColor).add()
        .append(new KeyedCodec<>("ServerStartFormat", Codec.STRING), (c, v) -> c.serverStartFormat = v, c -> c.serverStartFormat).add()
        .append(new KeyedCodec<>("ServerStopFormat", Codec.STRING), (c, v) -> c.serverStopFormat = v, c -> c.serverStopFormat).add()
        .append(new KeyedCodec<>("UseEmbeds", Codec.BOOLEAN), (c, v) -> c.useEmbeds = v, c -> c.useEmbeds).add()
        .append(new KeyedCodec<>("JoinLeaveEmbed", Codec.BOOLEAN), (c, v) -> c.joinLeaveEmbed = v, c -> c.joinLeaveEmbed).add()
        .append(new KeyedCodec<>("DeathEmbed", Codec.BOOLEAN), (c, v) -> c.deathEmbed = v, c -> c.deathEmbed).add()
        .append(new KeyedCodec<>("ServerStatusEmbed", Codec.BOOLEAN), (c, v) -> c.serverStatusEmbed = v, c -> c.serverStatusEmbed).add()
        .append(new KeyedCodec<>("ChatWebhookUrl", Codec.STRING), (c, v) -> c.chatWebhookUrl = v, c -> c.chatWebhookUrl).add()
        .append(new KeyedCodec<>("ChatWebhookAutoCreate", Codec.BOOLEAN), (c, v) -> c.chatWebhookAutoCreate = v, c -> c.chatWebhookAutoCreate).add()
        .append(new KeyedCodec<>("ChatFilterWords", Codec.STRING), (c, v) -> c.chatFilterWords = v, c -> c.chatFilterWords).add()
        .append(new KeyedCodec<>("ChatFilterReplacement", Codec.STRING), (c, v) -> c.chatFilterReplacement = v, c -> c.chatFilterReplacement).add()
        .append(new KeyedCodec<>("ChatFilterMode", Codec.STRING), (c, v) -> c.chatFilterMode = v, c -> c.chatFilterMode).add()
        .append(new KeyedCodec<>("AvatarEnabled", Codec.BOOLEAN), (c, v) -> c.avatarEnabled = v, c -> c.avatarEnabled).add()
        .append(new KeyedCodec<>("AvatarStyle", Codec.STRING), (c, v) -> c.avatarStyle = v, c -> c.avatarStyle).add()
        .append(new KeyedCodec<>("CommandBlacklist", Codec.STRING), (c, v) -> c.commandBlacklist = v, c -> c.commandBlacklist).add()
        .append(new KeyedCodec<>("ConsoleAllowedCommands", Codec.STRING), (c, v) -> c.consoleAllowedCommands = v, c -> c.consoleAllowedCommands).add()
        .append(new KeyedCodec<>("ConsoleRoleId", Codec.STRING), (c, v) -> c.consoleRoleId = v, c -> c.consoleRoleId).add()
        .append(new KeyedCodec<>("ConsoleReadyFormat", Codec.STRING), (c, v) -> c.consoleReadyFormat = v, c -> c.consoleReadyFormat).add()
        .append(new KeyedCodec<>("AfkTimeoutMinutes", Codec.INTEGER), (c, v) -> c.afkTimeoutMinutes = v, c -> c.afkTimeoutMinutes).add()
        .append(new KeyedCodec<>("AfkEnabled", Codec.BOOLEAN), (c, v) -> c.afkEnabled = v, c -> c.afkEnabled).add()
        .append(new KeyedCodec<>("AfkFormat", Codec.STRING), (c, v) -> c.afkFormat = v, c -> c.afkFormat).add()
        .append(new KeyedCodec<>("AfkReturnFormat", Codec.STRING), (c, v) -> c.afkReturnFormat = v, c -> c.afkReturnFormat).add()
        .append(new KeyedCodec<>("LinkedRoleId", Codec.STRING), (c, v) -> c.linkedRoleId = v, c -> c.linkedRoleId).add()
        .append(new KeyedCodec<>("SyncNickname", Codec.BOOLEAN), (c, v) -> c.syncNickname = v, c -> c.syncNickname).add()
        .append(new KeyedCodec<>("NicknameFormat", Codec.STRING), (c, v) -> c.nicknameFormat = v, c -> c.nicknameFormat).add()
        .append(new KeyedCodec<>("LinkingRequireToChat", Codec.BOOLEAN), (c, v) -> c.linkingRequireToChat = v, c -> c.linkingRequireToChat).add()
        .append(new KeyedCodec<>("LinkingShowIngameName", Codec.BOOLEAN), (c, v) -> c.linkingShowIngameName = v, c -> c.linkingShowIngameName).add()
        .append(new KeyedCodec<>("ShowDiscordNameIngame", Codec.BOOLEAN), (c, v) -> c.showDiscordNameIngame = v, c -> c.showDiscordNameIngame).add()
        .append(new KeyedCodec<>("LinkingRequireToPlay", Codec.BOOLEAN), (c, v) -> c.linkingRequireToPlay = v, c -> c.linkingRequireToPlay).add()
        .append(new KeyedCodec<>("NotLinkedKickFormat", Codec.STRING), (c, v) -> c.notLinkedKickFormat = v, c -> c.notLinkedKickFormat).add()
        .append(new KeyedCodec<>("UnlinkedKickFormat", Codec.STRING), (c, v) -> c.unlinkedKickFormat = v, c -> c.unlinkedKickFormat).add()
        .append(new KeyedCodec<>("LinkingBypassNames", Codec.STRING), (c, v) -> c.linkingBypassNames = v, c -> c.linkingBypassNames).add()
        .append(new KeyedCodec<>("LinkingGraceSeconds", Codec.INTEGER), (c, v) -> c.linkingGraceSeconds = v, c -> c.linkingGraceSeconds).add()
        .append(new KeyedCodec<>("LiveStatusEnabled", Codec.BOOLEAN), (c, v) -> c.liveStatusEnabled = v, c -> c.liveStatusEnabled).add()
        .append(new KeyedCodec<>("LiveStatusChannelId", Codec.STRING), (c, v) -> c.liveStatusChannelId = v, c -> c.liveStatusChannelId).add()
        .append(new KeyedCodec<>("LiveStatusIntervalSeconds", Codec.INTEGER), (c, v) -> c.liveStatusIntervalSeconds = v, c -> c.liveStatusIntervalSeconds).add()
        .append(new KeyedCodec<>("UpdateCheckEnabled", Codec.BOOLEAN), (c, v) -> c.updateCheckEnabled = v, c -> c.updateCheckEnabled).add()
        .append(new KeyedCodec<>("UpdateRepo", Codec.STRING), (c, v) -> c.updateRepo = v, c -> c.updateRepo).add()
        .append(new KeyedCodec<>("StatsEnabled", Codec.BOOLEAN), (c, v) -> c.statsEnabled = v, c -> c.statsEnabled).add()
        .append(new KeyedCodec<>("BanSyncEnabled", Codec.BOOLEAN), (c, v) -> c.banSyncEnabled = v, c -> c.banSyncEnabled).add()
        .append(new KeyedCodec<>("BanSyncDiscordBanReason", Codec.STRING), (c, v) -> c.banSyncDiscordBanReason = v, c -> c.banSyncDiscordBanReason).add()
        .append(new KeyedCodec<>("BanSyncGuildId", Codec.STRING), (c, v) -> c.banSyncGuildId = v, c -> c.banSyncGuildId).add()
        .append(new KeyedCodec<>("ZoneDiscoveryBroadcastEnabled", Codec.BOOLEAN), (c, v) -> c.zoneDiscoveryBroadcastEnabled = v, c -> c.zoneDiscoveryBroadcastEnabled).add()
        .append(new KeyedCodec<>("ZoneDiscoveryFormat", Codec.STRING), (c, v) -> c.zoneDiscoveryFormat = v, c -> c.zoneDiscoveryFormat).add()
        .append(new KeyedCodec<>("BanBroadcastEnabled", Codec.BOOLEAN), (c, v) -> c.banBroadcastEnabled = v, c -> c.banBroadcastEnabled).add()
        .append(new KeyedCodec<>("BanBroadcastFormat", Codec.STRING), (c, v) -> c.banBroadcastFormat = v, c -> c.banBroadcastFormat).add()
        .append(new KeyedCodec<>("UnbanBroadcastFormat", Codec.STRING), (c, v) -> c.unbanBroadcastFormat = v, c -> c.unbanBroadcastFormat).add()
        .build();

    public String getDiscordToken() { return orDefault(discordToken, ""); }
    public String getChatChannelId() { return orDefault(chatChannelId, ""); }
    public String getConsoleChannelId() { return orDefault(consoleChannelId, ""); }
    public String getJoinLeaveChannelId() { return orDefault(joinLeaveChannelId, ""); }
    public boolean isChatEnabled() { return chatEnabled; }
    public boolean isJoinLeaveEnabled() { return joinLeaveEnabled; }
    public boolean isDeathEnabled() { return deathEnabled; }
    public boolean isServerStatusEnabled() { return serverStatusEnabled; }
    public boolean isAccountLinkingEnabled() { return accountLinkingEnabled; }
    public String getChatFormat() { return orDefault(chatFormat, "**%player%**: %message%"); }
    public String getJoinFormat() { return orDefault(joinFormat, ":green_circle: **%player%** joined the server"); }
    public String getLeaveFormat() { return orDefault(leaveFormat, ":red_circle: **%player%** left the server"); }
    public String getDeathFormat() { return orDefault(deathFormat, ":skull: %message%"); }
    public String[] getDeathMessages() { return deathMessages; }
    public String getDiscordToGameFormat() { return orDefault(discordToGameFormat, "[Discord] %player%: %message%"); }
    public String getDiscordToGameColor() { return orDefault(discordToGameColor, ""); }
    public String getChatFilterMode() { return orDefault(chatFilterMode, "censor"); }
    public String getServerStartFormat() { return orDefault(serverStartFormat, ":white_check_mark: **Server started**"); }
    public String getServerStopFormat() { return orDefault(serverStopFormat, ":octagonal_sign: **Server stopped**"); }
    public boolean isUseEmbeds() { return useEmbeds; }
    public boolean useJoinLeaveEmbed() { return useEmbeds && joinLeaveEmbed; }
    public boolean useDeathEmbed() { return useEmbeds && deathEmbed; }
    public boolean useServerStatusEmbed() { return useEmbeds && serverStatusEmbed; }
    public String getChatWebhookUrl() { return orDefault(chatWebhookUrl, ""); }
    public boolean isChatWebhookAutoCreate() { return chatWebhookAutoCreate; }
    public boolean isAvatarEnabled() { return avatarEnabled; }
    public String getAvatarStyle() { return orDefault(avatarStyle, "thumbnail"); }
    public Set<String> getCommandBlacklist() { return parseCsv(commandBlacklist); }
    public Set<String> getConsoleAllowedCommands() { return parseCsv(consoleAllowedCommands); }
    public String getConsoleRoleId() { return orDefault(consoleRoleId, ""); }
    public String getConsoleReadyFormat() { return orDefault(consoleReadyFormat, ""); }
    public int getAfkTimeoutMinutes() { return afkTimeoutMinutes; }
    public boolean isAfkEnabled() { return afkEnabled; }
    public String getAfkFormat() { return orDefault(afkFormat, ":zzz: **%player%** is now AFK"); }
    public String getAfkReturnFormat() { return orDefault(afkReturnFormat, ":wave: **%player%** is no longer AFK"); }
    public Set<String> getLinkedRoleIds() { return parseCsv(linkedRoleId).stream().filter(RelayConfig::isSnowflake).collect(Collectors.toSet()); }
    public boolean isSyncNickname() { return syncNickname; }
    public String getNicknameFormat() { return orDefault(nicknameFormat, "%player%"); }
    public boolean isLinkingRequireToChat() { return linkingRequireToChat; }
    public boolean isLinkingShowIngameName() { return linkingShowIngameName; }
    public boolean isShowDiscordNameIngame() { return showDiscordNameIngame; }
    public boolean isLinkingRequireToPlay() { return linkingRequireToPlay; }
    public String getNotLinkedKickFormat() { return orDefault(notLinkedKickFormat, "You must link your Discord account to play. Code: %code%"); }
    public String getUnlinkedKickFormat() { return orDefault(unlinkedKickFormat, "Your Discord account was unlinked. Reconnect to link again."); }
    public Set<String> getLinkingBypassNames() { return parseCsv(linkingBypassNames); }
    public int getLinkingGraceSeconds() { return linkingGraceSeconds; }
    public boolean isLiveStatusEnabled() { return liveStatusEnabled; }
    public String getLiveStatusChannelId() { return orDefault(liveStatusChannelId, ""); }
    public int getLiveStatusIntervalSeconds() { return liveStatusIntervalSeconds; }
    public boolean isUpdateCheckEnabled() { return updateCheckEnabled; }
    public String getUpdateRepo() { return orDefault(updateRepo, ""); }
    public boolean isStatsEnabled() { return statsEnabled; }
    public boolean isBanSyncEnabled() { return banSyncEnabled; }
    public String getBanSyncDiscordBanReason() { return orDefault(banSyncDiscordBanReason, "You are banned from this server's Discord."); }
    public String getBanSyncGuildId() { return orDefault(banSyncGuildId, ""); }
    public boolean isZoneDiscoveryBroadcastEnabled() { return zoneDiscoveryBroadcastEnabled; }
    public String getZoneDiscoveryFormat() { return orDefault(zoneDiscoveryFormat, ":map: %player% discovered %zone%"); }
    public boolean isBanBroadcastEnabled() { return banBroadcastEnabled; }
    public String getBanBroadcastFormat() { return orDefault(banBroadcastFormat, ":hammer: %player% was banned %duration% by %by%%reason%"); }
    public String getUnbanBroadcastFormat() { return orDefault(unbanBroadcastFormat, ":unlock: %player% was unbanned"); }

    private static Set<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(",")).map(s -> s.trim().toLowerCase()).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    /** Returns the filtered message, or null if it should be dropped (block mode with a match). */
    public String filterChat(String message) {
        if (message == null) {
            return null;
        }
        Set<String> words = parseCsv(chatFilterWords);
        if (words.isEmpty()) {
            return message;
        }
        if ("block".equalsIgnoreCase(orDefault(chatFilterMode, "censor"))) {
            for (String word : words) {
                if (Pattern.compile("(?i)\\b" + Pattern.quote(word) + "\\b").matcher(message).find()) {
                    return null;
                }
            }
            return message;
        }
        String replacement = Matcher.quoteReplacement(orDefault(chatFilterReplacement, "***"));
        String result = message;
        for (String word : words) {
            result = result.replaceAll("(?i)\\b" + Pattern.quote(word) + "\\b", replacement);
        }
        return result;
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static boolean isSnowflake(String value) {
        return !value.isEmpty() && value.chars().allMatch(Character::isDigit);
    }
}
