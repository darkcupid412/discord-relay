package com.darkcupid412.relay.stats;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.DiscoverZoneEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ZoneDiscoveryStatsSystem extends EntityEventSystem<EntityStore, DiscoverZoneEvent.Display> {
    private final StatsManager stats;

    public ZoneDiscoveryStatsSystem(StatsManager stats) {
        super(DiscoverZoneEvent.Display.class);
        this.stats = stats;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, DiscoverZoneEvent.Display event) {
        PlayerRef player = store.getComponent(chunk.getReferenceTo(index), PlayerRef.getComponentType());
        if (player != null) {
            stats.addZoneDiscovered(player.getUuid());
        }
    }
}
