package com.darkcupid412.relay.discord;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A command sender for commands run from the Discord console relay. It captures the command output for
 * relaying back. Like a real server console it has full permissions on purpose: access is controlled at the
 * Discord layer (only an admin or the configured ConsoleRoleId may use the console channel) and by the
 * ConsoleAllowedCommands allowlist and CommandBlacklist, both checked before any command reaches this sender.
 * It deliberately does not resolve in-game permissions for its synthetic UUID, which would be fragile and
 * could break legitimate admin commands.
 */
public final class DiscordCommandSender implements CommandSender {
    private static final UUID DISCORD_UUID = new UUID(0L, 1L);
    private final String author;
    private final List<String> output = new ArrayList<>();

    public DiscordCommandSender(String author) {
        this.author = author;
    }

    @Override
    public void sendMessage(Message message) {
        String text = stripAnsi(message.getAnsiMessage());
        if (!text.isBlank()) {
            output.add(text);
        }
    }

    @Override
    public String getUsername() {
        return "Discord:" + author;
    }

    @Override
    public UUID getUuid() {
        return DISCORD_UUID;
    }

    // Full permissions by design; the console relay is gated by Discord role and the command allowlist.
    @Override
    public boolean hasPermission(String id) {
        return true;
    }

    @Override
    public boolean hasPermission(String id, boolean def) {
        return true;
    }

    public String getOutput() {
        return output.isEmpty() ? "Command executed (no output)" : String.join("\n", output);
    }

    private static String stripAnsi(String text) {
        return text == null ? "" : text.replaceAll("\\[[;\\d]*m", "");
    }
}
