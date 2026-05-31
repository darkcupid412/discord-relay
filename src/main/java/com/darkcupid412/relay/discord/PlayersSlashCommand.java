package com.darkcupid412.relay.discord;

import com.darkcupid412.relay.command.PlayersCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class PlayersSlashCommand implements DiscordSlashCommand {
    @Override
    public SlashCommandData data() {
        return Commands.slash("players", "Shows online players");
    }

    @Override
    public String execute(SlashCommandInteractionEvent event) {
        // Clamp to stay under Discord's 2000 character message limit on a very full server.
        String list = PlayersCommand.formatPlayerList();
        return list.length() <= 1900 ? list : list.substring(0, 1900) + "...";
    }
}
