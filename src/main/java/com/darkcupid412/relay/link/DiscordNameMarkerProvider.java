package com.darkcupid412.relay.link;

import com.hypixel.hytale.protocol.packets.worldmap.HeightDeltaIconComponent;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.PlayerMarkerComponent;
import com.hypixel.hytale.server.core.asset.type.gameplay.WorldMapConfig;
import com.hypixel.hytale.server.core.asset.type.gameplay.worldmap.PlayersMapMarkerConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

/**
 * Replaces the engine's native player markers (registered under "playerIcons") so a linked player shows
 * their Discord name on the world map instead of their Hytale username. Mirrors OtherPlayersMarkerProvider,
 * swapping only the marker label.
 */
public final class DiscordNameMarkerProvider implements WorldMapManager.MarkerProvider {
    private final AccountLinkManager linkManager;

    public DiscordNameMarkerProvider(AccountLinkManager linkManager) {
        this.linkManager = linkManager;
    }

    @Override
    public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
        WorldMapConfig config = world.getGameplayConfig().getWorldMapConfig();
        if (!config.isDisplayPlayers()) {
            return;
        }
        PlayersMapMarkerConfig players = config.getPlayersConfig();
        Predicate<PlayerRef> filter = collector.getPlayerMapFilter();
        for (PlayerRef other : world.getPlayerRefs()) {
            if (other.getUuid().equals(player.getUuid()) || (filter != null && filter.test(other))) {
                continue;
            }
            String name = linkManager.getDiscordName(other.getUuid()).orElse(other.getUsername());
            MapMarker marker = new MapMarkerBuilder("Player-" + other.getUuid(), "Player.png", other.getTransform())
                .withCustomName(name)
                .withComponent(new PlayerMarkerComponent(other.getUuid()))
                .withComponent(new HeightDeltaIconComponent(
                    players.getIconSwapHeightDelta(), players.getAboveIcon(),
                    players.getIconSwapHeightDelta(), players.getBelowIcon()))
                .build();
            collector.addIgnoreViewDistance(marker);
        }
    }
}
