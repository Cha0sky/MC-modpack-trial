package com.rpgleveling.registry;

import com.rpgleveling.data.DiscoveryData;
import com.rpgleveling.data.PlayerRpgData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The three data attachments.
 *
 * <p>{@code copyOnDeath()} covers both death/respawn and the End-to-Overworld
 * {@code PlayerEvent.Clone}, which together give the "no death penalty" requirement.</p>
 */
public final class RpgAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, RpgAttributes.MODID);

    /** Serializable + copied on death: level, XP and points survive everything. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerRpgData>> PLAYER_DATA =
            ATTACHMENT_TYPES.register("player_data",
                    () -> AttachmentType.serializable(PlayerRpgData::new).copyOnDeath().build());

    /** Discovery ledger; also a player achievement, so it is kept on death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DiscoveryData>> DISCOVERY =
            ATTACHMENT_TYPES.register("discovery",
                    () -> AttachmentType.serializable(DiscoveryData::new).copyOnDeath().build());

    /** Marker for spawner-spawned mobs (anti farm); pure memory, no serializer. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> SPAWNER_MOB =
            ATTACHMENT_TYPES.register("spawner_mob",
                    () -> AttachmentType.builder(() -> Boolean.FALSE).build());

    private RpgAttachments() {}
}
