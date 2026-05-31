package com.darkcupid412.relay.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class PlayersCommand extends AbstractAsyncCommand {
    public PlayersCommand() {
        super("players", "Lists online players");
        addAliases("list", "online");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        context.sendMessage(Message.raw(formatPlayerList()));
        return CompletableFuture.completedFuture(null);
    }

    /** Shared so the Discord side relays the same text. */
    public static String formatPlayerList() {
        Collection<PlayerRef> players = Universe.get().getPlayers();
        if (players.isEmpty()) {
            return "No players online";
        }
        String names = players.stream()
            .map(PlayerRef::getUsername)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.joining(", "));
        return "Players online (" + players.size() + "): " + names;
    }
}
