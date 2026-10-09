package com.evolvingblade.client;

import com.evolvingblade.network.EBPayloads;

/**
 * 客户端缓存：只保存服务器下发的权威快照，绝不自行计算成长/伤害。
 */
public final class EBClientState {

    private static EBPayloads.StateSnapshot snapshot;
    /** 本次登录是否已经自动弹出过选择界面。 */
    private static boolean promptHandled;
    /** 玩家是否主动关闭了选择界面（关闭后只在按 G 或重新登录时再弹）。 */
    private static boolean dismissed;
    /** 需要在本 tick 之后弹出（等待世界/玩家就绪）。 */
    private static boolean pendingOpen;

    private EBClientState() {}

    public static void accept(EBPayloads.StateSnapshot value) {
        snapshot = value;
        if (!value.hasMode() && !promptHandled) {
            // 登录包可能早于客户端世界/玩家创建（或仍在加载界面），这里只排队，不直接开屏。
            pendingOpen = true;
        }
    }

    public static EBPayloads.StateSnapshot snapshot() {
        return snapshot;
    }

    public static void resetOnLogin() {
        snapshot = null;
        promptHandled = false;
        dismissed = false;
        pendingOpen = false;
    }

    public static boolean consumePendingOpen() {
        if (pendingOpen && !promptHandled) {
            pendingOpen = false;
            promptHandled = true;
            return true;
        }
        return false;
    }

    public static boolean isDismissed() {
        return dismissed;
    }

    public static void dismiss() {
        dismissed = true;
        pendingOpen = false;
        promptHandled = true;
    }

    /** 按 G 手动打开时允许再次弹出。 */
    public static void allowManualOpen() {
        dismissed = false;
    }
}
