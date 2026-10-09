package com.rpgleveling.net;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.data.PlayerRpgData;
import com.rpgleveling.registry.RpgAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * <b>The only place that sends a payload.</b> Every mutation ends with {@link #syncTo}
 * so the client mirror can never drift from the server truth.
 */
public final class RpgNet {

    // Feedback codes (DESIGN 9.1).
    public static final int FB_OK = 0;
    public static final int FB_NO_POINTS = 1;
    public static final int FB_BAD_ARGS = 2;
    public static final int FB_RATE_LIMIT = 3;
    public static final int FB_LINE_DISABLED = 4;
    public static final int FB_LINE_CAP = 5;
    public static final int FB_ZERO_BASIS = 6;
    public static final int FB_COOLDOWN = 7;

    private RpgNet() {}

    /**
     * {@code true} once the connection has negotiated this payload.
     *
     * <p>NeoForge throws {@code UnsupportedOperationException("Payload ... may not be sent
     * to the client!")} when a payload is sent before the registration handshake finished.
     * That is reachable outside normal play - a headless GameTest player, or any caller
     * that fires before login completes - so every send goes through this guard.</p>
     */
    private static boolean canSend(ServerPlayer player) {
        if (player == null || player.connection == null) return false;
        return NetworkRegistry.hasChannel(player.connection, ClientboundRpgDataPayload.TYPE.id());
    }

    public static void syncTo(ServerPlayer player) {
        if (!canSend(player)) return;
        PacketDistributor.sendToPlayer(player, build(player));
    }

    public static ClientboundRpgDataPayload build(ServerPlayer player) {
        PlayerRpgData data = player.getData(RpgAttachments.PLAYER_DATA);
        return new ClientboundRpgDataPayload(
                data.level(),
                data.totalXp(),
                data.xpIntoLevel(),
                data.xpForNextLevel(),
                data.earnedPoints(),
                data.spentPoints(),
                data.unspentPoints(),
                data.pointsView().clone());
    }

    public static void feedback(ServerPlayer player, int code) {
        if (!canSend(player)) return;
        PacketDistributor.sendToPlayer(player, new ClientboundRpgFeedbackPayload(code));
    }

    /** Human readable reason, used by the client to render the failure. */
    public static String feedbackKey(int code) {
        return switch (code) {
            case FB_OK -> "gui.rpgleveling.feedback.ok";
            case FB_NO_POINTS -> "gui.rpgleveling.feedback.no_points";
            case FB_BAD_ARGS -> "gui.rpgleveling.feedback.bad_args";
            case FB_RATE_LIMIT -> "gui.rpgleveling.feedback.rate_limit";
            case FB_LINE_DISABLED -> "gui.rpgleveling.feedback.line_disabled";
            case FB_LINE_CAP -> "gui.rpgleveling.feedback.line_cap";
            case FB_ZERO_BASIS -> "gui.rpgleveling.feedback.zero_basis";
            case FB_COOLDOWN -> "gui.rpgleveling.feedback.cooldown";
            default -> "gui.rpgleveling.feedback.bad_args";
        };
    }

    /** Exposed for diagnostics: the max line index a packet may carry. */
    public static int maxLineIndex() {
        return com.rpgleveling.api.StatLine.COUNT;
    }

    /** True when the payload version matches what the server expects. */
    public static boolean configLoaded() {
        return RpgConfig.snapshot() != null;
    }
}