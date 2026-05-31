package com.darkcupid412.relay.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.damage.event.KillFeedEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Relays the game's own kill feed death message to Discord. We read the message the engine already
 * builds via Damage.getDeathMessage instead of reconstructing it from damage cause ids, which is why
 * the old build reported every death as "environment".
 */
public final class DeathEventSystem extends EntityEventSystem<EntityStore, KillFeedEvent.DecedentMessage> {
    private final BooleanSupplier active;
    private final Consumer<String> relay;

    public DeathEventSystem(BooleanSupplier active, Consumer<String> relay) {
        super(KillFeedEvent.DecedentMessage.class);
        this.active = active;
        this.relay = relay;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, KillFeedEvent.DecedentMessage event) {
        if (!active.getAsBoolean()) {
            return;
        }
        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        PlayerRef victim = store.getComponent(targetRef, PlayerRef.getComponentType());
        if (victim == null) {
            return;
        }
        Message message = event.getDamage().getDeathMessage(targetRef, store);
        relay.accept(toThirdPerson(message.getAnsiMessage(), victim.getUsername()));
    }

    // The engine's kill feed message is second person ("You were killed by X"); it only builds the third
    // person form on the client. Rewrite it to name the player for the Discord broadcast.
    private static String toThirdPerson(String message, String name) {
        if (message.startsWith("You were ")) {
            return name + " was " + message.substring("You were ".length());
        }
        if (message.startsWith("You ")) {
            return name + " " + message.substring("You ".length());
        }
        return name + " " + message;
    }
}
