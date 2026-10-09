package com.rpgleveling.util;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Shared logger. {@code System.out} is banned mod-wide so automation can parse the log. */
public final class RpgLog {
    public static final Logger LOG = LogUtils.getLogger();

    private RpgLog() {}
}
