package com.darkcupid412.relay.stats;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class StatsFormat {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private StatsFormat() {
    }

    static List<String> lines(PlayerStats stats, long playtimeSeconds, boolean online) {
        return List.of(
            ":heart: Health: " + health(stats.getLastHealth(), stats.getMaxHealth(), online),
            ":clock3: Playtime: " + duration(playtimeSeconds),
            ":calendar: First joined: " + date(stats.getFirstJoin()),
            ":eyes: Last seen: " + (online ? "Online now" : lastSeen(stats.getLastSeen())),
            ":door: Times joined: " + stats.getJoinCount(),
            ":bricks: Blocks placed: " + stats.getBlocksPlaced(),
            ":pick: Blocks broken: " + stats.getBlocksBroken(),
            ":skull: Deaths: " + stats.getDeaths(),
            ":crossed_swords: Kills: " + stats.getKills(),
            ":speech_balloon: Messages: " + stats.getMessages(),
            ":map: Zones discovered: " + stats.getZonesDiscovered(),
            ":footprints: Distance: " + distance(stats.getDistance()));
    }

    static String duration(long seconds) {
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (days > 0 || hours > 0) {
            sb.append(hours).append("h ");
        }
        sb.append(minutes).append("m");
        return sb.toString();
    }

    static String distance(double blocks) {
        if (blocks >= 1000.0) {
            return String.format("%.1f km", blocks / 1000.0);
        }
        return String.format("%.0f blocks", blocks);
    }

    static String date(long epochMillis) {
        return epochMillis <= 0L ? "Unknown" : DATE.format(Instant.ofEpochMilli(epochMillis));
    }

    static String health(float current, float max, boolean online) {
        if (max <= 0f) {
            return online ? "Unknown" : "Unknown *(offline)*";
        }
        String value = String.format("%.0f/%.0f", current, max);
        return online ? value : value + " *(offline)*";
    }

    static String lastSeen(long epochMillis) {
        if (epochMillis <= 0L) {
            return "Unknown";
        }
        long diff = System.currentTimeMillis() - epochMillis;
        if (diff < 60000L) {
            return "Just now";
        }
        if (diff < 3600000L) {
            long minutes = diff / 60000L;
            return minutes + (minutes == 1L ? " minute ago" : " minutes ago");
        }
        if (diff < 86400000L) {
            long hours = diff / 3600000L;
            return hours + (hours == 1L ? " hour ago" : " hours ago");
        }
        if (diff < 604800000L) {
            long days = diff / 86400000L;
            return days + (days == 1L ? " day ago" : " days ago");
        }
        return date(epochMillis);
    }
}
