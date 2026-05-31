package com.darkcupid412.relay.command;

import com.darkcupid412.relay.link.AccountLinkManager;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LinkCommand extends AbstractAsyncCommand {
    private final AccountLinkManager linkManager;

    public LinkCommand(AccountLinkManager linkManager) {
        super("link", "Link your Discord account");
        this.linkManager = linkManager;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command can only be used by players"));
            return CompletableFuture.completedFuture(null);
        }
        PlayerRef player = context.senderAs(PlayerRef.class);
        UUID uuid = player.getUuid();
        if (linkManager.isLinked(uuid)) {
            context.sendMessage(Message.raw("Your account is already linked. Use /unlink first.").color("#FFAA00"));
            return CompletableFuture.completedFuture(null);
        }
        String code = linkManager.generateCode(uuid, player.getUsername());
        context.sendMessage(Message.raw("Discord link code: ").color("#55FF55").insert(Message.raw(code).color("#FFFFFF").bold(true)));
        context.sendMessage(Message.raw("Run /link " + code + " in Discord to finish. The code expires in 5 minutes.").color("#AAAAAA"));
        return CompletableFuture.completedFuture(null);
    }
}
