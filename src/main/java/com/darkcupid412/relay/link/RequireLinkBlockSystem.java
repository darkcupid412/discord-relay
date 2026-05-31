package com.darkcupid412.relay.link;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.CancellableEcsEvent;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Cancels a cancellable action (block place/break) for a player the require-to-play gate is restricting.
 * The ECS registry keys systems by class, so the two concrete actions are separate subclasses.
 */
public abstract class RequireLinkBlockSystem<E extends CancellableEcsEvent> extends EntityEventSystem<EntityStore, E> {
    private final RequireLinkGate gate;

    protected RequireLinkBlockSystem(Class<E> eventClass, RequireLinkGate gate) {
        super(eventClass);
        this.gate = gate;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, E event) {
        PlayerRef player = store.getComponent(chunk.getReferenceTo(index), PlayerRef.getComponentType());
        if (player != null && gate.shouldRestrict(player)) {
            event.setCancelled(true);
        }
    }

    public static final class Place extends RequireLinkBlockSystem<PlaceBlockEvent> {
        public Place(RequireLinkGate gate) {
            super(PlaceBlockEvent.class, gate);
        }
    }

    public static final class Break extends RequireLinkBlockSystem<BreakBlockEvent> {
        public Break(RequireLinkGate gate) {
            super(BreakBlockEvent.class, gate);
        }
    }
}
