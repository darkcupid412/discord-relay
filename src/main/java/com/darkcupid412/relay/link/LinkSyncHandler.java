package com.darkcupid412.relay.link;

import com.darkcupid412.relay.config.RelayConfig;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

public final class LinkSyncHandler implements AccountLinkManager.LinkListener {
    private static final int MAX_NICKNAME = 32;

    private final Supplier<JDA> jdaSupplier;
    private final Supplier<RelayConfig> config;
    private final HytaleLogger logger;
    private final Function<UUID, Optional<String>> cachedName;

    public LinkSyncHandler(Supplier<JDA> jdaSupplier, Supplier<RelayConfig> config, HytaleLogger logger, Function<UUID, Optional<String>> cachedName) {
        this.jdaSupplier = jdaSupplier;
        this.config = config;
        this.logger = logger;
        this.cachedName = cachedName;
    }

    @Override
    public void onLink(UUID playerUuid, String discordId, String discordName) {
        sync(discordId, playerUuid, true);
    }

    @Override
    public void onUnlink(UUID playerUuid, String discordId) {
        sync(discordId, playerUuid, false);
    }

    private void sync(String discordId, UUID playerUuid, boolean linked) {
        JDA jda = jdaSupplier.get();
        if (jda == null) {
            return;
        }
        RelayConfig cfg = config.get();
        Set<String> roleIds = cfg.getLinkedRoleIds();
        boolean syncNickname = cfg.isSyncNickname();
        if (roleIds.isEmpty() && !syncNickname) {
            return;
        }
        Guild guild = resolveGuild(jda, roleIds);
        if (guild == null) {
            return;
        }
        String nickname = linked && syncNickname ? buildNickname(cfg, playerUuid) : null;
        guild.retrieveMemberById(discordId).queue(member -> {
            for (String roleId : roleIds) {
                Role role = guild.getRoleById(roleId);
                if (role == null) {
                    continue;
                }
                String action = linked ? "add role" : "remove role";
                try {
                    if (linked) {
                        guild.addRoleToMember(member, role).queue(ok -> {}, err -> logSync(action, discordId, err));
                    } else {
                        guild.removeRoleFromMember(member, role).queue(ok -> {}, err -> logSync(action, discordId, err));
                    }
                } catch (RuntimeException e) {
                    logSync(action, discordId, e);
                }
            }
            if (syncNickname && (!linked || nickname != null)) {
                try {
                    guild.modifyNickname(member, linked ? nickname : null).queue(ok -> {}, err -> logSync("set nickname", discordId, err));
                } catch (RuntimeException e) {
                    logSync("set nickname", discordId, e);
                }
            }
        }, err -> logger.atWarning().log("Link sync: Discord member %s not found (%s)", discordId, err.getMessage()));
    }

    private Guild resolveGuild(JDA jda, Set<String> roleIds) {
        for (String roleId : roleIds) {
            Role role = jda.getRoleById(roleId);
            if (role != null) {
                return role.getGuild();
            }
        }
        List<Guild> guilds = jda.getGuilds();
        return guilds.isEmpty() ? null : guilds.get(0);
    }

    private String buildNickname(RelayConfig cfg, UUID playerUuid) {
        PlayerRef player = Universe.get().getPlayer(playerUuid);
        String name = player != null ? player.getUsername() : cachedName.apply(playerUuid).orElse(null);
        if (name == null) {
            return null;
        }
        return truncate(cfg.getNicknameFormat().replace("%player%", name), MAX_NICKNAME);
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
        return value.substring(0, end);
    }

    private void logSync(String action, String discordId, Throwable err) {
        logger.atWarning().log("Link sync: failed to %s for %s (%s)", action, discordId, err.getMessage());
    }
}
