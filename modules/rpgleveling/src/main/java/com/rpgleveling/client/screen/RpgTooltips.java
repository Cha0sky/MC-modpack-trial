package com.rpgleveling.client.screen;

import com.rpgleveling.api.Curve;
import com.rpgleveling.api.Mode;
import com.rpgleveling.api.Stat;
import com.rpgleveling.api.StatLine;
import com.rpgleveling.data.SaturatingCurve;
import com.rpgleveling.stat.LineCfg;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Tooltip text, including the diminishing-returns hint produced by the curve inverse. */
public final class RpgTooltips {

    private RpgTooltips() {}

    public static List<Component> forLine(StatLine line, LineCfg cfg, int points, double value, boolean showCurveHint) {
        List<Component> lines = new ArrayList<>(6);
        // The row shows the bare dimension name (the mode has its own column), so the
        // tooltip is where the two modes of a dimension are told apart.
        lines.add(Component.translatable(line.langKey())
                .append(Component.literal(" \u00b7 "))
                .append(Component.translatable(line.mode == Mode.FLAT
                        ? "gui.rpgleveling.mode.flat" : "gui.rpgleveling.mode.percent")));
        lines.add(Component.translatable("gui.rpgleveling.tooltip.points", points));
        lines.add(Component.translatable("gui.rpgleveling.tooltip.current",
                String.format(Locale.ROOT, "%.4f", value)));

        if (cfg.curve() == Curve.SATURATING && showCurveHint && cfg.cap() > 0.0) {
            double ratio = SaturatingCurve.ratio(points, cfg.halfPoints());
            double need = SaturatingCurve.pointsToGain(points, cfg.halfPoints(), 0.05);
            lines.add(Component.translatable("gui.rpgleveling.curve.hint",
                    String.format(Locale.ROOT, "%.1f", ratio * 100.0),
                    String.format(Locale.ROOT, "%.0f", need)));
            lines.add(Component.translatable("gui.rpgleveling.curve.cap",
                    String.format(Locale.ROOT, "%.4f", cfg.cap())));
        }
        if (line.mode == Mode.PERCENT) {
            lines.add(Component.translatable("gui.rpgleveling.tooltip.basis." + cfg.basis().name().toLowerCase(Locale.ROOT),
                    String.format(Locale.ROOT, "%.2f", cfg.basisValue())));
        }
        return lines;
    }

    /** Grey note shown when a TOTAL percent line currently has a zero basis. */
    public static Component zeroBasisHint(Stat stat) {
        return Component.translatable("gui.rpgleveling.zero_basis", Component.translatable(stat.langKey()));
    }
}
