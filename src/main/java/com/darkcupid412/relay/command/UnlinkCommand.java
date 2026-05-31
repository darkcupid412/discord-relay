package com.darkcupid412.relay.command;

import com.darkcupid412.relay.link.AccountLinkManager;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class UnlinkCommand extends AbstractAsyncCommand {
    private final AccountLinkManager linkManager;

    public UnlinkCommand(AccountLinkManager linkManager) {
        super("unlink", "Unlink your Discord account");
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
        UUID uuid = context.senderAs(PlayerRef.class).getUuid();
        context.sendMessage(linkManager.unlinkByPlayer(uuid)
            ? Message.raw("Your Discord account has been unlinked").color("#55FF55")
            : Message.raw("Your account is not linked to Discord").color("#FFAA00"));
        return CompletableFuture.completedFuture(null);
    }
}
