package com.darkcupid412.relay.discord;

import com.darkcupid412.relay.config.RelayConfig;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.messages.MessageRequest;

public final class DiscordBot extends ListenerAdapter {

    @FunctionalInterface
    public interface InboundChatHandler {
        void handle(String discordId, String author, String message);
    }

    private static final Color GREEN = new Color(67, 181, 129);
    private static final Color RED = new Color(240, 71, 71);
    private static final Color DEATH = new Color(153, 45, 34);

    private final HytaleLogger logger;
    private final Supplier<RelayConfig> config;
    private final Path dataDirectory;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile JDA jda;
    private volatile TextChannel chatChannel;
    private volatile TextChannel joinLeaveChannel;
    private volatile TextChannel consoleChannel;
    private volatile String autoWebhookUrl;
    private InboundChatHandler chatHandler;
    private BiFunction<String, String, String> consoleCommandHandler;
    private final Map<String, DiscordSlashCommand> slashCommands = new LinkedHashMap<>();
    private boolean announceServerStatus;
    private volatile TextChannel statusChannel;
    private volatile String statusMessageId;
    private volatile boolean statusSendInFlight;
    private volatile ScheduledExecutorService statusScheduler;
    private volatile long statusStartTime;
    private volatile ScheduledFuture<?> presenceTask;
    private Object banSyncListener;

    public DiscordBot(HytaleLogger logger, Supplier<RelayConfig> config, Path dataDirectory) {
        this.logger = logger;
        this.config = config;
        this.dataDirectory = dataDirectory;
    }

    public void setChatHandler(InboundChatHandler handler) {
        this.chatHandler = handler;
    }

    public void setConsoleCommandHandler(BiFunction<String, String, String> handler) {
        this.consoleCommandHandler = handler;
    }

    public void registerSlashCommand(DiscordSlashCommand command) {
        slashCommands.put(command.data().getName(), command);
    }

    public void setAnnounceServerStatus(boolean announce) {
        this.announceServerStatus = announce;
    }

    /** Registers an extra JDA listener (ban sync) and enables the moderation intent so guild ban events arrive. */
    public void setBanSyncListener(Object listener) {
        this.banSyncListener = listener;
    }

    public boolean start() {
        try {
            MessageRequest.setDefaultMentions(EnumSet.noneOf(Message.MentionType.class));
            JDABuilder builder = JDABuilder.createDefault(config.get().getDiscordToken())
                .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                .addEventListeners(this);
            if (banSyncListener != null) {
                builder.enableIntents(GatewayIntent.GUILD_MODERATION).addEventListeners(banSyncListener);
            }
            this.jda = builder.build();
            return true;
        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Failed to start Discord bot");
            return false;
        }
    }

    public void stop() {
        if (presenceTask != null) {
            presenceTask.cancel(false);
        }
        if (statusScheduler != null) {
            statusScheduler.shutdownNow();
            try {
                statusScheduler.awaitTermination(2L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (statusChannel != null && statusMessageId != null) {
            try {
                statusChannel.editMessageEmbedsById(statusMessageId, buildStatusEmbed(false)).queue(ok -> {}, err -> {});
            } catch (Exception ignored) {
                // Best effort during shutdown; never let a failed status update block the rest of teardown.
            }
        }
        if (jda != null) {
            jda.shutdown();
            try {
                if (!jda.awaitShutdown(Duration.ofSeconds(5))) {
                    jda.shutdownNow();
                }
            } catch (InterruptedException e) {
                jda.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        try {
            httpClient.close();
        } catch (Exception ignored) {
        }
    }

    public boolean isConnected() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    public JDA getJda() {
        return jda;
    }

    private TextChannel resolveChannel(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            return jda.getTextChannelById(id);
        } catch (NumberFormatException e) {
            logger.atWarning().log("Invalid channel id: %s", id);
            return null;
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        String chatChannelId = config.get().getChatChannelId();
        this.chatChannel = resolveChannel(chatChannelId);
        if (chatChannel == null) {
            logger.atWarning().log("Chat channel not found: %s", chatChannelId);
        } else {
            logger.atInfo().log("Discord connected as %s", event.getJDA().getSelfUser().getName());
        }
        setupWebhook();
        this.joinLeaveChannel = resolveChannel(config.get().getJoinLeaveChannelId());
        this.consoleChannel = resolveChannel(config.get().getConsoleChannelId());
        if (consoleChannel != null) {
            sendConsoleReady();
        }
        if (announceServerStatus) {
            sendServerStart();
        }
        if (config.get().isLiveStatusEnabled()) {
            startLiveStatus();
        }
        startPresenceUpdater();
    }

    private void startPresenceUpdater() {
        updatePresence();
        presenceTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(this::updatePresence, 30L, 30L, TimeUnit.SECONDS);
    }

    private void updatePresence() {
        try {
            if (jda == null) {
                return;
            }
            int count = Universe.get() == null ? 0 : Universe.get().getPlayerCount();
            jda.getPresence().setActivity(Activity.playing(count == 1 ? "1 player online" : count + " players online"));
        } catch (Exception e) {
            logger.atWarning().log("Failed to update presence: %s", e.getMessage());
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        String channelId = event.getChannel().getId();
        String author = event.getMember() != null ? event.getMember().getEffectiveName() : event.getAuthor().getName();
        String content = event.getMessage().getContentDisplay();
        if (consoleChannel != null && channelId.equals(consoleChannel.getId())) {
            handleConsoleMessage(event.getMember(), author, content);
            return;
        }
        if (chatChannel != null && channelId.equals(chatChannel.getId()) && chatHandler != null) {
            chatHandler.handle(event.getAuthor().getId(), author, content);
        }
    }

    private void handleConsoleMessage(Member member, String author, String message) {
        if (message.isBlank() || consoleCommandHandler == null) {
            return;
        }
        if (!isConsoleAuthorized(member)) {
            consoleChannel.sendMessage(":no_entry: You are not allowed to use the server console.").queue();
            return;
        }
        String command = message.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        // Run off the JDA event thread: the handler blocks until the world thread runs the command (up to
        // ~10s), and holding a gateway worker that long can stall other Discord events.
        String toRun = command;
        CompletableFuture.runAsync(() -> {
            String response = consoleCommandHandler.apply(author, toRun);
            if (response != null && !response.isEmpty()) {
                consoleChannel.sendMessage(response).queue();
            }
        });
    }

    private boolean isConsoleAuthorized(Member member) {
        if (member == null) {
            return false;
        }
        String roleId = config.get().getConsoleRoleId();
        if (roleId.isEmpty()) {
            return member.hasPermission(Permission.ADMINISTRATOR);
        }
        return member.getRoles().stream().anyMatch(role -> role.getId().equals(roleId));
    }

    private void sendConsoleReady() {
        String format = config.get().getConsoleReadyFormat();
        if (format != null && !format.isEmpty()) {
            consoleChannel.sendMessage(format).queue();
        }
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        event.getGuild().updateCommands()
            .addCommands(slashCommands.values().stream().map(DiscordSlashCommand::data).toList())
            .queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        DiscordSlashCommand command = slashCommands.get(event.getName());
        if (command != null) {
            event.deferReply().queue();
            event.getHook().sendMessage(command.reply(event)).queue();
        }
    }

    /** Reuse a webhook we previously made in the chat channel, or create one, so chat relays as the player with their avatar. */
    private void setupWebhook() {
        if (chatChannel == null || !config.get().isChatWebhookAutoCreate() || !config.get().getChatWebhookUrl().isEmpty()) {
            return;
        }
        // Discord rejects webhook names containing "discord" or "clyde" (50035 Invalid Form Body).
        String name = "Chat Relay";
        chatChannel.retrieveWebhooks().queue(hooks -> {
            Webhook mine = hooks.stream().filter(h -> h.getToken() != null && name.equals(h.getName())).findFirst().orElse(null);
            if (mine != null) {
                autoWebhookUrl = mine.getUrl();
            } else {
                chatChannel.createWebhook(name).queue(
                    created -> autoWebhookUrl = created.getUrl(),
                    err -> logger.atWarning().log("Could not create chat webhook (Manage Webhooks permission?): %s", err.getMessage()));
            }
        }, err -> logger.atWarning().log("Could not list webhooks (Manage Webhooks permission?): %s", err.getMessage()));
    }

    public void sendChat(String player, UUID uuid, String message) {
        if (chatChannel == null) {
            return;
        }
        String configured = config.get().getChatWebhookUrl();
        String webhookUrl = configured.isEmpty() ? autoWebhookUrl : configured;
        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            String avatar = avatarUrl(uuid);
            String url = webhookUrl;
            CompletableFuture.runAsync(() -> {
                if (!sendViaWebhook(url, player, message, avatar)) {
                    sendChatFallback(player, message);
                }
            });
        } else {
            sendChatFallback(player, message);
        }
    }

    private void sendChatFallback(String player, String message) {
        String formatted = config.get().getChatFormat()
            .replace("%player%", escapeMarkdown(player))
            .replace("%message%", escapeMarkdown(message));
        chatChannel.sendMessage(formatted).queue();
    }

    private boolean sendViaWebhook(String webhookUrl, String username, String content, String avatarUrl) {
        try {
            StringBuilder json = new StringBuilder("{\"username\":\"").append(escapeJson(username))
                .append("\",\"content\":\"").append(escapeJson(content)).append("\"");
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                json.append(",\"avatar_url\":\"").append(escapeJson(avatarUrl)).append("\"");
            }
            json.append(",\"allowed_mentions\":{\"parse\":[]}}");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            logger.atWarning().log("Webhook send failed: %s", e.getMessage());
            return false;
        }
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    public void sendAfk(String player, boolean afk) {
        if (chatChannel == null) {
            return;
        }
        RelayConfig cfg = config.get();
        String format = afk ? cfg.getAfkFormat() : cfg.getAfkReturnFormat();
        chatChannel.sendMessage(clamp(format.replace("%player%", escapeMarkdown(player)), 1900)).queue();
    }

    public void sendZoneDiscovery(String player, String zone) {
        if (chatChannel == null) {
            return;
        }
        String message = config.get().getZoneDiscoveryFormat()
            .replace("%player%", escapeMarkdown(player))
            .replace("%zone%", escapeMarkdown(zone));
        chatChannel.sendMessage(clamp(message, 1900)).queue();
    }

    /** Posts a pre-built moderation notice (ban/unban). Mentions are suppressed globally, so reason text is safe. */
    public void sendModerationBroadcast(String message) {
        if (chatChannel == null || message == null || message.isBlank()) {
            return;
        }
        chatChannel.sendMessage(clamp(message, 1900)).queue();
    }

    public void sendJoin(String player, UUID uuid) {
        sendPresence(player, uuid, config.get().getJoinFormat(), GREEN);
    }

    public void sendLeave(String player, UUID uuid) {
        sendPresence(player, uuid, config.get().getLeaveFormat(), RED);
    }

    private void sendPresence(String player, UUID uuid, String format, Color color) {
        TextChannel channel = joinLeaveChannel != null ? joinLeaveChannel : chatChannel;
        if (channel == null) {
            return;
        }
        String message = clamp(format.replace("%player%", escapeMarkdown(player)), 256);
        RelayConfig cfg = config.get();
        if (!cfg.useJoinLeaveEmbed()) {
            channel.sendMessage(message).queue();
            return;
        }
        String avatarUrl = avatarUrl(uuid);
        EmbedBuilder embed = new EmbedBuilder().setColor(color);
        switch (cfg.getAvatarStyle().toLowerCase()) {
            case "compact" -> embed.setAuthor(message, null, avatarUrl);
            case "large" -> {
                embed.setDescription(message);
                if (avatarUrl != null) {
                    embed.setImage(avatarUrl);
                }
            }
            default -> {
                embed.setDescription(message);
                if (avatarUrl != null) {
                    embed.setThumbnail(avatarUrl);
                }
            }
        }
        sendEmbed(channel, embed.build(), message);
    }

    public void sendDeath(String deathMessage) {
        if (chatChannel == null) {
            return;
        }
        RelayConfig cfg = config.get();
        String message = clamp(pickDeathFormat(cfg).replace("%message%", escapeMarkdown(deathMessage)), 1900);
        if (cfg.useDeathEmbed()) {
            sendEmbed(chatChannel, new EmbedBuilder().setColor(DEATH).setDescription(message).build(), message);
        } else {
            chatChannel.sendMessage(message).queue();
        }
    }

    private String pickDeathFormat(RelayConfig cfg) {
        String[] pool = cfg.getDeathMessages();
        if (pool == null || pool.length == 0) {
            return cfg.getDeathFormat();
        }
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    public void sendServerStart() {
        if (chatChannel == null) {
            return;
        }
        RelayConfig cfg = config.get();
        if (cfg.useServerStatusEmbed()) {
            sendEmbed(chatChannel, new EmbedBuilder().setColor(GREEN).setDescription(cfg.getServerStartFormat()).build(), cfg.getServerStartFormat());
        } else {
            chatChannel.sendMessage(cfg.getServerStartFormat()).queue();
        }
    }

    public void sendServerStop() {
        if (chatChannel == null) {
            return;
        }
        RelayConfig cfg = config.get();
        // Blocking send so the message lands before the JVM exits during shutdown.
        try {
            if (cfg.useServerStatusEmbed()) {
                chatChannel.sendMessageEmbeds(new EmbedBuilder().setColor(RED).setDescription(cfg.getServerStopFormat()).build()).complete();
            } else {
                chatChannel.sendMessage(cfg.getServerStopFormat()).complete();
            }
        } catch (Exception e) {
            logger.atWarning().log("Failed to send server stop message: %s", e.getMessage());
        }
    }

    private void startLiveStatus() {
        if (statusScheduler != null) {
            return;
        }
        RelayConfig cfg = config.get();
        String channelId = cfg.getLiveStatusChannelId();
        this.statusChannel = channelId.isEmpty() ? chatChannel : resolveChannel(channelId);
        if (statusChannel == null) {
            logger.atWarning().log("Live status channel not found: %s", channelId);
            return;
        }
        this.statusMessageId = loadStatusMessageId();
        this.statusStartTime = System.currentTimeMillis();
        int interval = Math.max(15, cfg.getLiveStatusIntervalSeconds());
        this.statusScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "relay-live-status");
            thread.setDaemon(true);
            return thread;
        });
        statusScheduler.scheduleAtFixedRate(this::updateLiveStatus, 0L, interval, TimeUnit.SECONDS);
    }

    private void updateLiveStatus() {
        try {
            if (statusChannel == null) {
                return;
            }
            MessageEmbed embed = buildStatusEmbed(true);
            if (statusMessageId == null) {
                if (statusSendInFlight) {
                    return;
                }
                statusSendInFlight = true;
                statusChannel.sendMessageEmbeds(embed).queue(
                    msg -> { statusMessageId = msg.getId(); statusSendInFlight = false; saveStatusMessageId(msg.getId()); },
                    err -> statusSendInFlight = false);
            } else {
                statusChannel.editMessageEmbedsById(statusMessageId, embed).queue(ok -> {}, err -> {
                    if (err instanceof ErrorResponseException ere && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                        statusMessageId = null;
                    } else {
                        logger.atWarning().log("Live status edit failed: %s", err.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            logger.atWarning().log("Live status update failed: %s", e.getMessage());
        }
    }

    private String loadStatusMessageId() {
        Path file = dataDirectory.resolve("live_status.id");
        try {
            if (Files.exists(file)) {
                String id = Files.readString(file).trim();
                return id.isEmpty() ? null : id;
            }
        } catch (IOException e) {
            logger.atWarning().log("Failed to read live status id: %s", e.getMessage());
        }
        return null;
    }

    private void saveStatusMessageId(String id) {
        try {
            Files.writeString(dataDirectory.resolve("live_status.id"), id);
        } catch (IOException e) {
            logger.atWarning().log("Failed to save live status id: %s", e.getMessage());
        }
    }

    private MessageEmbed buildStatusEmbed(boolean online) {
        // online is false during shutdown, when the universe may already be torn down.
        int players = online && Universe.get() != null ? Universe.get().getPlayerCount() : 0;
        String uptime = formatUptime(System.currentTimeMillis() - statusStartTime);
        String dot = online ? ":green_circle:" : ":red_circle:";
        String description = "**Server:** " + dot + " " + (online ? "Online" : "Offline")
            + "\n\n**Players:** " + players
            + "\n\n**Uptime:** " + uptime;
        return new EmbedBuilder()
            .setColor(online ? GREEN : RED)
            .setTitle("Server Status")
            .setDescription(description)
            .setTimestamp(Instant.now())
            .build();
    }

    private static String formatUptime(long millis) {
        long seconds = millis / 1000L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (days > 0 || hours > 0) {
            sb.append(hours).append("h ");
        }
        sb.append(minutes).append("m");
        return sb.toString();
    }

    private void sendEmbed(TextChannel channel, MessageEmbed embed, String fallback) {
        channel.sendMessageEmbeds(embed).queue(ok -> {}, err -> channel.sendMessage(fallback).queue());
    }

    private String avatarUrl(UUID uuid) {
        if (uuid == null || !config.get().isAvatarEnabled()) {
            return null;
        }
        return "https://crafthead.net/hytale/avatar/" + uuid + "/64";
    }

    private static String clamp(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
        return value.substring(0, end);
    }

    private static String escapeMarkdown(String text) {
        return text.replace("\\", "\\\\")
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("~", "\\~")
            .replace("`", "\\`")
            .replace("|", "\\|")
            .replace("@", "\\@")
            .replace("#", "\\#");
    }
}
