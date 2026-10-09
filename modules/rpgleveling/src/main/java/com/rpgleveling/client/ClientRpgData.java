package com.rpgleveling.client;

import com.rpgleveling.net.ClientboundRpgDataPayload;

/**
 * Read-only client mirror of the server snapshot.
 *
 * <p>The client never computes a level or a point total: it renders what the server
 * sent and nothing else, which is what makes display spoofing impossible.</p>
 */
public final class ClientRpgData {

    private static volatile ClientboundRpgDataPayload snapshot;
    private static volatile int lastFeedback = -1;

    private ClientRpgData() {}

    public static void accept(ClientboundRpgDataPayload payload) {
        // A hostile or version-mismatched server must not be able to make the screen
        // index past the end of the point table (C-06).
        if (payload == null || payload.points() == null
                || payload.points().length != com.rpgleveling.api.StatLine.COUNT) {
            return;
        }
        snapshot = payload;
    }

    public static ClientboundRpgDataPayload get() {
        return snapshot;
    }

    public static boolean has() {
        return snapshot != null;
    }

    public static void clear() {
        snapshot = null;
        lastFeedback = -1;
    }

    public static void setFeedback(int code) {
        lastFeedback = code;
    }

    public static int lastFeedback() {
        return lastFeedback;
    }
}