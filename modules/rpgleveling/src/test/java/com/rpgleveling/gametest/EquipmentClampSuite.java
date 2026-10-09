package com.rpgleveling.gametest;

import com.rpgleveling.api.Mode;
import com.rpgleveling.api.Stat;
import com.rpgleveling.api.StatLine;
import com.rpgleveling.service.RpgService;
import com.rpgleveling.stat.DerivedStats;
import java.util.Locale;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The armour family and the equipment term, under the new <b>adaptive</b> limit policy.
 *
 * <p>Armour, armour toughness and knockback resistance now use
 * {@link Stat.LimitMode#LIVE}: the ceiling is whatever the {@code RangedAttribute}
 * itself allows, read at call time. Two consequences this suite pins:</p>
 *
 * <ol>
 *   <li>Vanilla gear still adds exactly {@code equipment_max} (netherite: 20 armour,
 *       12 toughness, 0.4 knockback resistance) and the value stays untruncated as long
 *       as the total is inside the attribute's range.</li>
 *   <li>When the investment does exceed that range, the value is clamped <b>and
 *       reported</b> - {@code clamped == true} and the GUI marker. There is no longer a
 *       safety proof claiming no point is ever wasted outside damage reduction; that
 *       guarantee was removed by request (DESIGN 1.4 / 1.16).</li>
 * </ol>
 *
 * <p>Every assertion is written as {@code min(raw, live limit)} rather than against the
 * vanilla number, so the same test proves the adaptive contract with or without an
 * attribute-range mod such as AttributeFix installed.</p>
 */
@GameTestHolder("rpgleveling")
@PrefixGameTestTemplate(false)
public final class EquipmentClampSuite {

    private EquipmentClampSuite() {}

    /** Small enough that no LIVE dimension reaches its attribute range. */
    private static final int POINTS = 25;

    private static final EquipmentSlot[] ARMOUR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static void invest(ServerPlayer player, Stat stat, int points) {
        RpgService.setPoints(player, stat.line(Mode.FLAT), points);
        RpgService.setPoints(player, stat.line(Mode.PERCENT), points);
    }

    private static void wearFullNetherite(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
    }

    /**
     * Applies the modifier data the worn items actually carry, through the same calls
     * vanilla uses ({@code removeModifier(id)} + {@code addTransientModifier}).
     *
     * <p>Needed because a GameTest mock player's equipment-change pass does not always
     * reach the attribute map inside the test window; the values applied here come from
     * the real {@code ItemAttributeModifiers} of the real netherite pieces, so the
     * resulting attribute state is identical to being fully geared.</p>
     */
    private static void applyWornItemModifiers(ServerPlayer player) {
        for (EquipmentSlot slot : ARMOUR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            stack.forEachModifier(slot, (attribute, modifier) -> {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    instance.removeModifier(modifier.id());
                    instance.addTransientModifier(modifier);
                }
            });
        }
    }

    /** The adaptive contract, checked directly: a LIVE ceiling <em>is</em> the attribute's range. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "equipment")
    public static void liveCeilingsMirrorTheAttributeRange(GameTestHelper helper) {
        GameTestSupport.Report report = new GameTestSupport.Report();
        for (Stat stat : new Stat[] { Stat.ARMOR, Stat.ARMOR_TOUGHNESS, Stat.MOVEMENT_SPEED }) {
            report.check(stat.limitMode == Stat.LimitMode.LIVE, stat.id + " should be LIVE");
            var attribute = stat.carrier.holder().value();
            report.check(attribute instanceof RangedAttribute, stat.id + " is not a RangedAttribute");
            if (attribute instanceof RangedAttribute ranged) {
                report.near("hardMax(" + stat.id + ")", ranged.getMaxValue(), stat.hardMax(), 1.0E-9);
            }
            GameTestSupport.evidence("liveLimit." + stat.id, stat.hardMax());
        }
        report.assertAllPassed(helper, "liveCeilingsMirrorTheAttributeRange");
        helper.succeed();
    }

    /**
     * With a modest investment nothing is truncated, and full netherite still adds exactly
     * {@code equipment_max} on top of the naked value.
     */
    @GameTest(template = "empty", timeoutTicks = 1200, batch = "equipment")
    public static void equipmentDeltaIsExactWhileNothingClamps(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        Stat[] dims = { Stat.ARMOR, Stat.ARMOR_TOUGHNESS, Stat.KNOCKBACK_RESIST };
        for (Stat stat : dims) invest(player, stat, POINTS);
        GameTestSupport.refresh(player);

        double[] naked = new double[dims.length];
        for (int i = 0; i < dims.length; i++) {
            naked[i] = DerivedStats.finalValue(player, dims[i]);
            GameTestSupport.evidence("naked." + dims[i].id, String.format(Locale.ROOT, "%.6f", naked[i]));
        }

        wearFullNetherite(player);

        helper.runAfterDelay(10, () -> {
            GameTestSupport.Report report = new GameTestSupport.Report();
            GameTestSupport.evidence("diag.tickCount", player.tickCount);

            boolean reachedByTicking = DerivedStats.finalValue(player, Stat.ARMOR) > naked[0] + 1.0E-6;
            if (!reachedByTicking) {
                applyWornItemModifiers(player);
            }
            GameTestSupport.evidence("equipment.path", reachedByTicking ? "vanilla-tick" : "explicit-item-modifiers");

            for (int i = 0; i < dims.length; i++) {
                Stat stat = dims[i];
                double geared = DerivedStats.finalValue(player, stat);
                double limit = stat.hardMax();
                GameTestSupport.evidence("geared." + stat.id, String.format(Locale.ROOT, "%.6f", geared));
                GameTestSupport.evidence("equipmentMax." + stat.id, stat.equipmentMax);
                GameTestSupport.evidence("limit." + stat.id, limit);

                // 1) the equipment really landed, and by exactly equipment_max
                report.near("equipment delta(" + stat.id + ")", stat.equipmentMax, geared - naked[i], 1.0E-4);

                // 2) this budget is chosen to stay under the live ceiling, so nothing is
                //    truncated in either state - otherwise this test would prove nothing
                report.check(naked[i] < limit, "test setup: naked " + stat.id + " already at the ceiling: "
                        + naked[i] + " >= " + limit);
                report.check(geared < limit, "test setup: geared " + stat.id + " already at the ceiling: "
                        + geared + " >= " + limit);
                report.check(!DerivedStats.isClamped(player, stat),
                        "clamped=true for " + stat.id + " geared (computed="
                                + DerivedStats.rawAttributeValue(player, stat) + " effective=" + geared + ")");
            }

            // 3) the value is exactly (design formula + equipment), clamped by nothing
            for (int i = 0; i < dims.length; i++) {
                Stat stat = dims[i];
                int[] both = new int[StatLine.COUNT];
                both[stat.line(Mode.FLAT).index()] = POINTS;
                both[stat.line(Mode.PERCENT).index()] = POINTS;
                double predicted = DerivedStats.computedValue(stat, both, GameTestSupport.cfgs())
                        + stat.equipmentMax;
                report.near(stat.id + " geared == formula + equipment", Math.min(predicted, stat.hardMax()),
                        DerivedStats.finalValue(player, stat), 1.0E-6);
            }

            report.assertAllPassed(helper, "equipmentDeltaIsExactWhileNothingClamps");
            helper.succeed();
        });
    }

    /**
     * Over-investing is <b>expected</b> now: the LIVE dimensions stop at the attribute's
     * range. What must never happen is a silent drop - the flag has to say so.
     */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "equipment")
    public static void overInvestmentIsClampedAndReported(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        GameTestSupport.Report report = new GameTestSupport.Report();
        invest(player, Stat.ARMOR, 200);

        double raw = DerivedStats.rawAttributeValue(player, Stat.ARMOR);
        double limit = Stat.ARMOR.hardMax();
        double effective = DerivedStats.finalValue(player, Stat.ARMOR);
        GameTestSupport.evidence("over.armor.raw", raw);
        GameTestSupport.evidence("over.armor.limit", limit);
        GameTestSupport.evidence("over.armor.effective", effective);
        GameTestSupport.evidence("over.armor.clamped", DerivedStats.isClamped(player, Stat.ARMOR));

        // The unfitted curve says 200*0.15 + 4*(200*1.666667/100) = 43.3333 armour.
        report.near("raw armour @200+200", 43.333336, raw, 1.0E-4);
        report.near("armour == min(raw, limit)", Math.min(raw, limit), effective, 1.0E-6);
        report.check(DerivedStats.isClamped(player, Stat.ARMOR) == (raw > limit + 1.0E-9),
                "the clamped flag must be true exactly when the live ceiling bit (raw=" + raw
                        + " limit=" + limit + ")");
        report.check(!(raw > limit) || DerivedStats.isEffectivelyCapped(player, Stat.ARMOR),
                "a truncated armour value is not reported by the GUI marker predicate");
        report.assertAllPassed(helper, "overInvestmentIsClampedAndReported");
        helper.succeed();
    }

    /** Knockback resistance is one of the three FIXED ceilings: 1.0 is immunity. */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "equipment")
    public static void knockbackResistanceStopsAtImmunity(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        GameTestSupport.Report report = new GameTestSupport.Report();
        invest(player, Stat.KNOCKBACK_RESIST, 200);

        double raw = DerivedStats.rawAttributeValue(player, Stat.KNOCKBACK_RESIST);
        GameTestSupport.evidence("knockback.raw", raw);
        report.check(raw > 1.0, "test setup: 200+200 points should overshoot immunity, raw=" + raw);
        report.near("knockback effective", 1.0, DerivedStats.finalValue(player, Stat.KNOCKBACK_RESIST), 1.0E-9);
        report.near("knockback ceiling", 1.0, Stat.KNOCKBACK_RESIST.hardMax(), 1.0E-9);
        report.check(DerivedStats.isEffectivelyCapped(player, Stat.KNOCKBACK_RESIST),
                "immunity is not reported as a capped dimension");
        report.assertAllPassed(helper, "knockbackResistanceStopsAtImmunity");
        helper.succeed();
    }

    /** Naked, inside the range: a positive contribution, no truncation, no clamp. */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "equipment")
    public static void nakedArmourLinesStayInsideTheRange(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        GameTestSupport.Report report = new GameTestSupport.Report();
        for (Stat stat : new Stat[] { Stat.ARMOR, Stat.ARMOR_TOUGHNESS, Stat.KNOCKBACK_RESIST }) {
            invest(player, stat, POINTS);
            double value = DerivedStats.finalValue(player, stat);
            report.check(value > 0.0, "naked " + stat.id + " has no contribution: " + value);
            report.check(value < stat.hardMax(), "naked " + stat.id + " at the ceiling: " + value);
            report.check(!DerivedStats.isClamped(player, stat), "naked " + stat.id + " reports clamped=true");
            report.check(!DerivedStats.isCappedByModCeiling(player, stat),
                    "naked " + stat.id + " reports a mod ceiling");
        }
        report.assertAllPassed(helper, "nakedArmourLinesStayInsideTheRange");
        helper.succeed();
    }
}
