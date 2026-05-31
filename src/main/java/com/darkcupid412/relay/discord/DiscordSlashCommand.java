package com.darkcupid412.relay.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

/**
 * A Discord slash command. The bot defers the reply before calling the command (so a slow command never
 * trips Discord's 3 second timeout) and sends the result through the interaction hook. Commands return
 * plain text from execute; rich commands can override reply to send components.
 */
public interface DiscordSlashCommand {
    SlashCommandData data();

    String execute(SlashCommandInteractionEvent event);

    default MessageCreateData reply(SlashCommandInteractionEvent event) {
        return MessageCreateData.fromContent(execute(event));
    }
}
