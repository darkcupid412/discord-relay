package com.darkcupid412.relay.link;

import com.darkcupid412.relay.discord.DiscordSlashCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class LinkSlashCommand implements DiscordSlashCommand {
    private final AccountLinkManager linkManager;

    public LinkSlashCommand(AccountLinkManager linkManager) {
        this.linkManager = linkManager;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("link", "Link your Discord account to your in game account")
            .addOption(OptionType.STRING, "code", "The code from /link in game", false);
    }

    @Override
    public String execute(SlashCommandInteractionEvent event) {
        String discordId = event.getUser().getId();
        if (linkManager.isLinked(discordId)) {
            return "Your account is already linked. Use `/unlink` to remove it.";
        }
        OptionMapping codeOption = event.getOption("code");
        if (codeOption == null) {
            return "**How to link:**\n1. Join the server\n2. Type `/link` in game\n3. Run `/link <code>` here with the code you receive.";
        }
        String author = event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();
        return switch (linkManager.processCode(codeOption.getAsString(), discordId, author)) {
            case SUCCESS -> "Account linked successfully!";
            case INVALID_CODE -> "Invalid code. Use `/link` in game to get a new one.";
            case CODE_EXPIRED -> "That code has expired. Use `/link` in game to get a new one.";
            case DISCORD_ALREADY_LINKED -> "Your Discord is already linked to another account. Use `/unlink` first.";
            case PLAYER_ALREADY_LINKED -> "That in game account is already linked to another Discord user.";
        };
    }
}
