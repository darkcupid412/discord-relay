package com.darkcupid412.relay.link;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Shows a linked player's Discord name in world: above their head (Nameplate + DisplayNameComponent, set on
 * the world thread via store.putComponent) and in chat (by overriding the PlayerChatEvent formatter). The
 * nameplate is applied when a linked player enters a world and when they link, and reset on unlink. It is
 * driven off AddPlayerToWorldEvent (not connect) because a world or dimension change re-creates the entity
 * without the nameplate, and that event fires for both the initial join and every later world change.
 */
public final class NameplateSync implements AccountLinkManager.LinkListener {
    private static final long APPLY_DELAY_SECONDS = 4L;

    private final AccountLinkManager linkManager;
    private final HytaleLogger logger;

    public NameplateSync(AccountLinkManager linkManager, HytaleLogger logger) {
        this.linkManager = linkManager;
        this.logger = logger;
    }

    public void onChat(PlayerChatEvent event) {
        event.setFormatter((sender, message) -> Message.translation("server.chat.playerMessage")
            .param("username", linkManager.getDiscordName(sender.getUuid()).orElse(sender.getUsername()))
            .param("message", message));
    }

    public void onAddPlayerToWorld(AddPlayerToWorldEvent event) {
        World world = event.getWorld();
        // The freshly added entity is not fully spawned yet, and the per-entity holder does not reliably
        // expose its PlayerRef here. Defer, then on the destination world thread re-apply the nameplate to
        // every linked player in that world (idempotent), which covers the arriving player after a change.
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                world.execute(() -> {
                    for (PlayerRef player : world.getPlayerRefs()) {
                        linkManager.getDiscordName(player.getUuid()).ifPresent(name -> setName(player, name));
                    }
                });
            } catch (Exception ignored) {
                // The world may have unloaded during the delay (e.g. shutdown); nothing to re-apply.
            }
        }, APPLY_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void onLink(UUID playerUuid, String discordId, String discordName) {
        PlayerRef player = online(playerUuid);
        World world = worldOf(player);
        if (player != null && world != null && discordName != null) {
            world.execute(() -> setName(player, discordName));
        }
    }

    @Override
    public void onUnlink(UUID playerUuid, String discordId) {
        PlayerRef player = online(playerUuid);
        World world = worldOf(player);
        if (player != null && world != null) {
            world.execute(() -> setName(player, player.getUsername()));
        }
    }

    private void setName(PlayerRef player, String name) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            logger.atWarning().log("Nameplate skip: %s has no valid in-world reference yet", player.getUsername());
            return;
        }
        Store<EntityStore> store = ref.getStore();
        Nameplate nameplate = store.ensureAndGetComponent(ref, Nameplate.getComponentType());
        nameplate.setText(name);
        store.putComponent(ref, DisplayNameComponent.getComponentType(), new DisplayNameComponent(Message.raw(name)));
    }

    private static World worldOf(PlayerRef player) {
        if (player == null) {
            return null;
        }
        UUID worldUuid = player.getWorldUuid();
        return worldUuid == null || Universe.get() == null ? null : Universe.get().getWorld(worldUuid);
    }

    private static PlayerRef online(UUID uuid) {
        return Universe.get() == null ? null : Universe.get().getPlayer(uuid);
    }
}
