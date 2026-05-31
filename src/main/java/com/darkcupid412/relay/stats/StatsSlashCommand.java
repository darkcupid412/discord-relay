package com.darkcupid412.relay.stats;

import com.darkcupid412.relay.discord.DiscordSlashCommand;
import com.darkcupid412.relay.link.AccountLinkManager;
import java.util.Map;
import java.util.UUID;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

/** Shows a player's stats as a Components V2 card with their avatar. No name shows the caller's own stats if linked. */
public final class StatsSlashCommand implements DiscordSlashCommand {
    private static final int ACCENT = 5793266;

    private final StatsManager stats;
    private final AccountLinkManager linkManager;

    public StatsSlashCommand(StatsManager stats, AccountLinkManager linkManager) {
        this.stats = stats;
        this.linkManager = linkManager;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("stats", "Show server stats for a player, or yourself if linked")
            .addOption(OptionType.STRING, "player", "The in game player name", false);
    }

    @Override
    public String execute(SlashCommandInteractionEvent event) {
        UUID uuid = resolve(event);
        if (uuid == null) {
            return notFound(event);
        }
        PlayerStats player = stats.get(uuid);
        return "**" + player.getUsername() + "**\n" + String.join("\n", StatsFormat.lines(player, stats.livePlaytimeSeconds(uuid), stats.isOnline(uuid)));
    }

    @Override
    public MessageCreateData reply(SlashCommandInteractionEvent event) {
        UUID uuid = resolve(event);
        if (uuid == null) {
            return MessageCreateData.fromContent(notFound(event));
        }
        PlayerStats p = stats.get(uuid);
        long playtime = stats.livePlaytimeSeconds(uuid);
        boolean online = stats.isOnline(uuid);
        Container card = Container.of(
            TextDisplay.of("## " + p.getUsername() + "\n-# Player Statistics"),
            MediaGallery.of(MediaGalleryItem.fromUrl("https://crafthead.net/hytale/avatar/" + uuid + "/128")),
            Separator.createDivider(Separator.Spacing.SMALL),
            TextDisplay.of("**Health** ─ " + StatsFormat.health(p.getLastHealth(), p.getMaxHealth(), online)),
            TextDisplay.of("**Playtime** ─ " + StatsFormat.duration(playtime)),
            TextDisplay.of("**First Joined** ─ " + StatsFormat.date(p.getFirstJoin())),
            TextDisplay.of("**Last Seen** ─ " + (online ? "Online now" : StatsFormat.lastSeen(p.getLastSeen()))),
            TextDisplay.of("**Times Joined** ─ " + p.getJoinCount()),
            Separator.createDivider(Separator.Spacing.SMALL),
            TextDisplay.of("**Deaths** ─ " + p.getDeaths() + "  ╱  **Kills** ─ " + p.getKills()),
            TextDisplay.of("**Messages** ─ " + p.getMessages()),
            TextDisplay.of("**Distance** ─ " + StatsFormat.distance(p.getDistance())),
            TextDisplay.of("**Blocks Placed** ─ " + p.getBlocksPlaced() + "  ╱  **Broken** ─ " + p.getBlocksBroken()),
            TextDisplay.of("**Zones Discovered** ─ " + p.getZonesDiscovered()))
            .withAccentColor(ACCENT);
        return new MessageCreateBuilder().useComponentsV2().setComponents(card).build();
    }

    private UUID resolve(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption("player");
        if (option != null) {
            String query = option.getAsString();
            for (Map.Entry<UUID, PlayerStats> entry : stats.all().entrySet()) {
                if (entry.getValue().getUsername().equalsIgnoreCase(query)) {
                    return entry.getKey();
                }
            }
            return null;
        }
        return linkManager == null ? null : linkManager.getPlayerUuid(event.getUser().getId()).orElse(null);
    }

    private static String notFound(SlashCommandInteractionEvent event) {
        return event.getOption("player") != null
            ? "No stats found for that player."
            : "Link your Discord account with `/link`, or use `/stats <player>`.";
    }
}
