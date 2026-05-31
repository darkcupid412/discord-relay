package com.darkcupid412.relay.stats;

import com.darkcupid412.relay.discord.DiscordSlashCommand;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongFunction;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

/** Playtime leaderboard over a selectable range (all time, today, last 7 or 30 days), paginated 10 per page. */
public final class PlaytimeSlashCommand implements DiscordSlashCommand {
    private static final int PER_PAGE = 10;

    private final StatsManager stats;

    public PlaytimeSlashCommand(StatsManager stats) {
        this.stats = stats;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("playtime", "Show the playtime leaderboard")
            .addOptions(
                new OptionData(OptionType.STRING, "range", "Time range", false)
                    .addChoice("All time", "all")
                    .addChoice("Today", "day")
                    .addChoice("Last 7 days", "week")
                    .addChoice("Last 30 days", "month"),
                new OptionData(OptionType.INTEGER, "page", "Page number (10 per page)", false).setMinValue(1));
    }

    @Override
    public String execute(SlashCommandInteractionEvent event) {
        return body(event);
    }

    @Override
    public MessageCreateData reply(SlashCommandInteractionEvent event) {
        Container card = Container.of(
            TextDisplay.of("## :clock3: Playtime leaderboard" + rangeLabel(rangeDays(event))),
            Separator.createDivider(Separator.Spacing.SMALL),
            TextDisplay.of(body(event)));
        return new MessageCreateBuilder().useComponentsV2().setComponents(card).build();
    }

    /** 0 means all time, otherwise the number of days (including today) to look back. */
    private static int rangeDays(SlashCommandInteractionEvent event) {
        OptionMapping range = event.getOption("range");
        return switch (range == null ? "all" : range.getAsString()) {
            case "day" -> 1;
            case "week" -> 7;
            case "month" -> 30;
            default -> 0;
        };
    }

    private static String rangeLabel(int days) {
        return switch (days) {
            case 1 -> " (today)";
            case 7 -> " (last 7 days)";
            case 30 -> " (last 30 days)";
            default -> "";
        };
    }

    private String body(SlashCommandInteractionEvent event) {
        int days = rangeDays(event);
        ToLongFunction<Map.Entry<UUID, PlayerStats>> metric = days == 0
            ? e -> e.getValue().getPlaytimeSeconds()
            : e -> stats.recentPlaytimeSeconds(e.getKey(), days);
        List<Map.Entry<UUID, PlayerStats>> sorted = stats.all().entrySet().stream()
            .filter(e -> metric.applyAsLong(e) > 0)
            .sorted(Comparator.comparingLong(metric).reversed())
            .toList();
        if (sorted.isEmpty()) {
            return "No playtime recorded yet.";
        }
        int pages = (sorted.size() + PER_PAGE - 1) / PER_PAGE;
        OptionMapping pageOpt = event.getOption("page");
        int page = Math.min(pageOpt == null ? 1 : Math.max(1, pageOpt.getAsInt()), pages);
        int start = (page - 1) * PER_PAGE;
        int end = Math.min(start + PER_PAGE, sorted.size());
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            Map.Entry<UUID, PlayerStats> entry = sorted.get(i);
            String name = entry.getValue().getUsername().isEmpty() ? "Unknown" : entry.getValue().getUsername();
            sb.append("**").append(i + 1).append(".** ").append(name)
                .append(" - ").append(StatsFormat.duration(metric.applyAsLong(entry))).append("\n");
        }
        sb.append("\n-# Page ").append(page).append('/').append(pages).append(" - ").append(sorted.size()).append(" players");
        return sb.toString();
    }
}
