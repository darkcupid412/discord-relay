package com.darkcupid412.relay.stats;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.event.KillFeedEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Counts a death for the victim, and a kill for the killer when one player kills another. */
public final class DeathStatsSystem extends EntityEventSystem<EntityStore, KillFeedEvent.DecedentMessage> {
    private final StatsManager stats;

    public DeathStatsSystem(StatsManager stats) {
        super(KillFeedEvent.DecedentMessage.class);
        this.stats = stats;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, KillFeedEvent.DecedentMessage event) {
        Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
        PlayerRef victim = store.getComponent(victimRef, PlayerRef.getComponentType());
        if (victim == null) {
            return;
        }
        stats.addDeath(victim.getUuid());
        if (event.getDamage().getSource() instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> killerRef = entitySource.getRef();
            if (killerRef != null && killerRef.isValid()) {
                PlayerRef killer = store.getComponent(killerRef, PlayerRef.getComponentType());
                if (killer != null && !killer.getUuid().equals(victim.getUuid())) {
                    stats.addKill(killer.getUuid());
                }
            }
        }
    }
}
