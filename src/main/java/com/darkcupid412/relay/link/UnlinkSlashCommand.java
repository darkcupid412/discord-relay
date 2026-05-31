package com.darkcupid412.relay.link;

import com.darkcupid412.relay.discord.DiscordSlashCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class UnlinkSlashCommand implements DiscordSlashCommand {
    private final AccountLinkManager linkManager;

    public UnlinkSlashCommand(AccountLinkManager linkManager) {
        this.linkManager = linkManager;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("unlink", "Unlink your Discord account from your in game account");
    }

    @Override
    public String execute(SlashCommandInteractionEvent event) {
        return linkManager.unlinkByDiscord(event.getUser().getId())
            ? "Your account has been unlinked."
            : "Your Discord is not linked to any account.";
    }
}
