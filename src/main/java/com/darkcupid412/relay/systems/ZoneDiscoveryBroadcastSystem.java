package com.darkcupid412.relay.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.DiscoverZoneEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * Broadcasts a player's zone discovery to Discord. The engine renders the zone name through a client side
 * translation (server.map.zone.<id>), so only the raw id is available server side; it is prettified for the
 * message. Only display discoveries (the ones shown to the player on screen) are broadcast.
 */
public final class ZoneDiscoveryBroadcastSystem extends EntityEventSystem<EntityStore, DiscoverZoneEvent.Display> {
    private final BooleanSupplier active;
    private final BiConsumer<String, String> relay;

    public ZoneDiscoveryBroadcastSystem(BooleanSupplier active, BiConsumer<String, String> relay) {
        super(DiscoverZoneEvent.Display.class);
        this.active = active;
        this.relay = relay;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, DiscoverZoneEvent.Display event) {
        if (!active.getAsBoolean()) {
            return;
        }
        WorldMapTracker.ZoneDiscoveryInfo info = event.getDiscoveryInfo();
        if (!info.display() || info.zoneName() == null || info.zoneName().isBlank()) {
            return;
        }
        PlayerRef player = store.getComponent(chunk.getReferenceTo(index), PlayerRef.getComponentType());
        if (player != null) {
            relay.accept(player.getUsername(), prettify(info.zoneName()));
        }
    }

    // Zone ids are translation key suffixes like "howling_peaks"; render them as "Howling Peaks".
    private static String prettify(String id) {
        String[] words = id.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder sb = new StringBuilder(id.length());
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.length() == 0 ? id : sb.toString();
    }
}
