package com.evolvingblade.util;

import java.util.function.Consumer;

/**
 * 有界日志：反射探测之类的"可能刷屏"的诊断信息只允许输出 N 条，其余静默丢弃并计数。
 *
 * <p>纯 Java，可单元测试。
 */
public final class BoundedLog {

    private final int maxMessages;
    private final Consumer<String> sink;
    private int emitted;
    private int suppressed;

    public BoundedLog(int maxMessages, Consumer<String> sink) {
        if (maxMessages < 0) {
            throw new IllegalArgumentException("maxMessages must be >= 0");
        }
        this.maxMessages = maxMessages;
        this.sink = sink;
    }

    /**
     * 输出一条信息。
     *
     * @return 真正输出了返回 {@code true}；因为超出上限被静默丢弃返回 {@code false}
     */
    public boolean log(String message) {
        if (this.emitted < this.maxMessages) {
            this.emitted++;
            this.sink.accept(message);
            return true;
        }
        this.suppressed++;
        return false;
    }

    public int emittedCount() {
        return this.emitted;
    }

    public int suppressedCount() {
        return this.suppressed;
    }

    public boolean exhausted() {
        return this.emitted >= this.maxMessages;
    }
}
