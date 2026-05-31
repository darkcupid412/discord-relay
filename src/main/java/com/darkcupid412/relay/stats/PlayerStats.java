package com.darkcupid412.relay.stats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Mutable per-player statistics, serialized to player_stats.json. */
public final class PlayerStats {
    private String username = "";
    private long playtimeSeconds;
    private long firstJoin;
    private long lastSeen;
    private long joinCount;
    private long blocksPlaced;
    private long blocksBroken;
    private long deaths;
    private long kills;
    private long messages;
    private long zonesDiscovered;
    private double distance;
    private float lastHealth;
    private float maxHealth;
    // Concrete type so Gson deserializes into a thread-safe map (it is read and written off the world thread).
    private ConcurrentHashMap<String, Long> dailyPlaytime = new ConcurrentHashMap<>();

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public long getPlaytimeSeconds() { return playtimeSeconds; }
    public void addPlaytime(long seconds) { this.playtimeSeconds += seconds; }
    public long getFirstJoin() { return firstJoin; }
    public void setFirstJoin(long epochMillis) { this.firstJoin = epochMillis; }
    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long epochMillis) { this.lastSeen = epochMillis; }
    public long getJoinCount() { return joinCount; }
    public void addJoin() { this.joinCount++; }
    public long getBlocksPlaced() { return blocksPlaced; }
    public void addBlockPlaced() { this.blocksPlaced++; }
    public long getBlocksBroken() { return blocksBroken; }
    public void addBlockBroken() { this.blocksBroken++; }
    public long getDeaths() { return deaths; }
    public void addDeath() { this.deaths++; }
    public long getKills() { return kills; }
    public void addKill() { this.kills++; }
    public long getMessages() { return messages; }
    public void addMessage() { this.messages++; }
    public long getZonesDiscovered() { return zonesDiscovered; }
    public void addZone() { this.zonesDiscovered++; }
    public double getDistance() { return distance; }
    public void addDistance(double blocks) { this.distance += blocks; }
    public float getLastHealth() { return lastHealth; }
    public float getMaxHealth() { return maxHealth; }
    public void setHealth(float current, float max) { this.lastHealth = current; this.maxHealth = max; }
    public Map<String, Long> getDailyPlaytime() {
        if (dailyPlaytime == null) {
            dailyPlaytime = new ConcurrentHashMap<>();
        }
        return dailyPlaytime;
    }
    public void addDailyPlaytime(String date, long seconds) {
        getDailyPlaytime().merge(date, seconds, Long::sum);
    }
}
