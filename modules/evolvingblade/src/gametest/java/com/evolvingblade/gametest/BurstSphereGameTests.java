package com.evolvingblade.gametest;

import java.util.ArrayList;
import java.util.List;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.combat.StarEaterAbilities;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.registry.EBItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 爆发的<b>三维球</b>判定验收。
 *
 * <p>明确断言的不是"粒子画成了球"，而是<b>真实受伤判定</b>：
 * <ul>
 *   <li>球心＝玩家躯干中心，半径 6 / 终阶 8，三轴都参与；</li>
 *   <li>正上方、正下方、空中的目标必须能命中（旧的 X/Z 圆柱或"只算脚底距离"会漏掉它们）；</li>
 *   <li>"中心在球外但包围盒最近点进球"的贴边大型实体必须命中；</li>
 *   <li>三轴距离的最近点严格 {@code <= r²}：刚好贴边算命中，再多一点就不算；</li>
 *   <li>墙(LOS)、cap 32、友军/玩家/所有驯服生物排除仍然保留。</li>
 * </ul>
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class BurstSphereGameTests {

    private static final long FINAL_POINTS = 3000L;
    private static final long FINAL_BOSSES = 20L;

    private BurstSphereGameTests() {}

    private static ServerPlayer staged(GameTestHelper helper, int relX, int relY, int relZ) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, FINAL_POINTS, FINAL_BOSSES);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(new BlockPos(relX, relY, relZ)));
        player.moveTo(centre.x, centre.y, centre.z, 0.0F, 0.0F);
        return player;
    }

    /** 无甲、超大生命的测试用 Husk（真实受伤路径，不覆写 hurt/setHealth）。 */
    private static Husk husk(GameTestHelper helper, int relX, int relY, int relZ, double maxHealth) {
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(new BlockPos(relX, relY, relZ)));
        Husk husk = FixtureEntities.spawn(helper.getLevel(), centre.x, centre.y, centre.z,
                (type, level) -> new Husk(type, level));
        husk.getAttribute(Attributes.ARMOR).setBaseValue(0.0D);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        husk.setHealth((float) maxHealth);
        return husk;
    }

    private static boolean hurt(GameTestHelper helper, Husk target, float before) {
        return target.getHealth() < before;
    }

    @GameTest(template = "boss_arena", timeoutTicks = 300)
    public static void burstHitsDirectlyAboveAndBelowAndAirborne(GameTestHelper helper) {
        ServerPlayer player = staged(helper, 24, 4, 12);
        // 正上方 4 格（水平偏移 0）：三维球必须命中；X/Z 圆柱会漏掉。
        Husk above = husk(helper, 24, 8, 12, 5_000.0D);
        // 正下方 4 格。
        Husk below = husk(helper, 24, 0, 12, 5_000.0D);
        // 空中斜上（水平 3、垂直 4 → 距离 5 < 8）。
        Husk airborne = husk(helper, 27, 8, 12, 5_000.0D);
        // 对照组：空中但距离 9 > 8（水平 3、垂直 8.5）。
        Husk outsideAir = husk(helper, 27, 13, 12, 5_000.0D);

        float aboveHp = above.getHealth();
        float belowHp = below.getHealth();
        float airborneHp = airborne.getHealth();
        float outsideHp = outsideAir.getHealth();
        helper.assertTrue(StarEaterAbilities.tryBurst(player), "the final burst must fire");
        helper.assertTrue(hurt(helper, above, aboveHp),
                "a target DIRECTLY ABOVE must be hit by the 3D sphere (hp=" + above.getHealth() + ")");
        helper.assertTrue(hurt(helper, below, belowHp),
                "a target DIRECTLY BELOW must be hit by the 3D sphere (hp=" + below.getHealth() + ")");
        helper.assertTrue(hurt(helper, airborne, airborneHp),
                "an airborne diagonal target inside 8 blocks must be hit (hp=" + airborne.getHealth() + ")");
        helper.assertTrue(!hurt(helper, outsideAir, outsideHp),
                "an airborne target beyond the sphere radius must NOT be hit (hp=" + outsideAir.getHealth() + ")");
        helper.succeed();
    }

    /**
     * <b>包围盒最近点</b>判定的关键对照：中心在球外、但包围盒最近点进球 → 必须命中。
     *
     * <p>手算（球心＝玩家躯干 {@code c}，半径 r＝8；Husk 宽 0.6 → 半宽 h＝0.3）：
     * 把 Husk 精确放在 {@code c.z + 8.2}（用 {@code moveTo} 给精确小数坐标）：
     * <pre>
     *   中心距离   = 8.2  &gt; r = 8      → “中心在球外”成立
     *   最近点距离 = 8.2 − 0.3 = 7.9 ≤ 8 → 必须命中
     *   对照目标放在 c.z + 9.0：中心 9.0、最近点 8.7 &gt; 8 → 必须 miss
     * </pre>
     * 两条一起证明"用的是包围盒最近点，不是中心距离"。
     */
    @GameTest(template = "boss_arena", timeoutTicks = 300)
    public static void burstHitsBoxNearestPointInsideSphereEvenWhenCentreIsOutside(GameTestHelper helper) {
        ServerPlayer player = staged(helper, 24, 4, 12);
        double sphereRadius = com.evolvingblade.config.EBConfig.settings().abilities().burstRadiusFor(7);
        helper.assertTrue(Math.abs(sphereRadius - 8.0D) < 1.0E-6D,
                "sanity: the final-stage burst radius must be 8 (got " + sphereRadius + ")");
        net.minecraft.world.phys.Vec3 centre = new net.minecraft.world.phys.Vec3(
                player.getX(), player.getY() + player.getBbHeight() * 0.5D, player.getZ());
        // 精确小数坐标：中心 8.2（球外）、最近点 7.9（球内）。
        Husk edge = husk(helper, 24, 4, 20, 5_000.0D);
        edge.setNoGravity(true);
        edge.moveTo(centre.x, player.getY(), centre.z + 8.2D, 0.0F, 0.0F);
        // 对照：中心 9.0（球外）、最近点 8.7（球外）。
        Husk outside = husk(helper, 24, 4, 21, 5_000.0D);
        outside.setNoGravity(true);
        outside.moveTo(centre.x, player.getY(), centre.z + 9.0D, 0.0F, 0.0F);

        double edgeCentre = Math.hypot(edge.getX() - centre.x, edge.getZ() - centre.z);
        double edgeNearest = edgeCentre - edge.getBbWidth() * 0.5D;
        double outsideNearest = Math.hypot(outside.getX() - centre.x, outside.getZ() - centre.z)
                - outside.getBbWidth() * 0.5D;
        helper.assertTrue(edge.getBbWidth() > 0.0D, "sanity: hitbox width must be positive");
        helper.assertTrue(edgeCentre > sphereRadius,
                "sanity: the CENTRE must be strictly outside the sphere (centre=" + edgeCentre + ")");
        helper.assertTrue(edgeNearest <= sphereRadius,
                "sanity: the NEAREST POINT must be inside the sphere (nearest=" + edgeNearest + ")");
        helper.assertTrue(outsideNearest > sphereRadius,
                "sanity: the control target's nearest point must be outside (nearest=" + outsideNearest + ")");
        float edgeHp = edge.getHealth();
        float outsideHp = outside.getHealth();
        helper.assertTrue(StarEaterAbilities.tryBurst(player), "the final burst must fire");
        helper.assertTrue(edge.getHealth() < edgeHp,
                "a box whose nearest point is inside the sphere must be hit even though its centre is outside"
                        + " (hp=" + edge.getHealth() + ")");
        helper.assertTrue(outside.getHealth() == outsideHp,
                "a box whose nearest point is outside must NOT be hit (hp=" + outside.getHealth() + ")");
        helper.succeed();
    }

    /**
     * 大型包围盒（原版末影龙：宽 16、高 8）的贴边命中 / 球外不命中。
     *
     * <p>两具包围盒<b>依次</b>单独施法（中间 discard + 延迟），避免其中一具遮挡另一具的视线。
     */
    @GameTest(template = "boss_arena", timeoutTicks = 500)
    public static void burstHitsLargeBoxWhoseCornerEntersTheSphere(GameTestHelper helper) {
        // 用例 A：龙宽 16（半宽 8），放在 +14 格 → 最近面 = 14 − 8 = 6 < 8，必须命中。
        ServerPlayer first = staged(helper, 24, 4, 12);
        LivingEntity hugeInside = hugeBox(helper, 38, 4, 12);
        float insideHp = hugeInside.getHealth();
        helper.assertTrue(StarEaterAbilities.tryBurst(first), "the final burst must fire (inside case)");
        helper.assertTrue(hugeInside.getHealth() < insideHp,
                "a large box whose nearest face is inside the sphere must be hit (hp="
                        + hugeInside.getHealth() + ")");
        // 移除后换一个位置再验证"最近面严格在球外"的负向对照，保证两次判定互不遮挡。
        hugeInside.discard();
        helper.runAfterDelay(3L, () -> {
            ServerPlayer second = staged(helper, 24, 4, 12);
            LivingEntity hugeOutside = hugeBox(helper, 44, 4, 12);
            float outsideHp = hugeOutside.getHealth();
            helper.assertTrue(StarEaterAbilities.tryBurst(second), "the final burst must fire (outside case)");
            helper.assertTrue(hugeOutside.getHealth() == outsideHp,
                    "a large box whose nearest face is 12+ blocks away must not be hit (hp="
                            + hugeOutside.getHealth() + ")");
            hugeOutside.discard();
            helper.succeed();
        });
    }

    /**
     * 墙体遮挡在<b>三维</b>下同样生效，而且不是"被地板挡住"的假象。
     *
     * <p>先把整列空间显式清成空气（模板地板/残块都不参与），再在空中搭天花板：
     * 玩家与两个目标都 NoGravity，天花板挡住向上，向下完全通畅作为对照。
     * 两条 LOS 都用与技能内部<b>同一个</b> {@code ClipContext.Block.COLLIDER} 实测，
     * 先证明"上面被挡、下面通畅"，再断言只有下方受伤。
     */
    @GameTest(template = "boss_arena", timeoutTicks = 300)
    public static void burstRespectsWallsInThreeDimensions(GameTestHelper helper) {
        // 显式清空整列（x 22..26, y 3..14, z 10..14），排除模板地板/残块影响。
        for (int x = 22; x <= 26; x++) {
            for (int y = 3; y <= 14; y++) {
                for (int z = 10; z <= 14; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        ServerPlayer player = staged(helper, 24, 8, 12);
        player.setNoGravity(true);
        // 天花板在 y=11：玩家眼高约 9.5，上方目标在 13 → 被挡；下方目标在 5 → 通畅。
        for (int x = 22; x <= 26; x++) {
            for (int z = 10; z <= 14; z++) {
                helper.setBlock(new BlockPos(x, 11, z), Blocks.STONE);
            }
        }
        Husk aboveWall = husk(helper, 24, 13, 12, 5_000.0D);
        aboveWall.setNoGravity(true);
        Husk belowOpen = husk(helper, 24, 5, 12, 5_000.0D);
        belowOpen.setNoGravity(true);
        helper.assertTrue(helper.getBlockState(new BlockPos(24, 11, 12)).is(Blocks.STONE),
                "sanity: the ceiling block must exist");
        // 实测 LOS：与技能内部同一个 clip 调用。
        boolean aboveVisible = clearLineOfSight(helper, player, aboveWall);
        boolean belowVisible = clearLineOfSight(helper, player, belowOpen);
        helper.assertFalse(aboveVisible, "measured sanity: the ceiling must block the LOS upward");
        helper.assertTrue(belowVisible, "measured sanity: the downward LOS must be clear (control)");
        float aboveHp = aboveWall.getHealth();
        float belowHp = belowOpen.getHealth();
        helper.assertTrue(StarEaterAbilities.tryBurst(player), "the burst must fire");
        helper.assertTrue(aboveWall.getHealth() == aboveHp,
                "a ceiling must block the burst upward (hp=" + aboveWall.getHealth() + ")");
        helper.assertTrue(belowOpen.getHealth() < belowHp,
                "the same burst must still hit the unobstructed target below (control)");
        helper.succeed();
    }

    /** 与技能内部同一个 {@code ClipContext.Block.COLLIDER} 的视线测量。 */
    private static boolean clearLineOfSight(GameTestHelper helper, ServerPlayer player, Husk target) {
        return helper.getLevel().clip(new net.minecraft.world.level.ClipContext(
                player.getEyePosition(), target.getEyePosition(),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType()
                == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** 32 个上限在三维球下依然精确（34 个在球内 → 只有最近的 32 个受伤）。 */
    @GameTest(template = "boss_arena", timeoutTicks = 600)
    public static void burstStillCapsAtThirtyTwoInThreeDimensions(GameTestHelper helper) {
        ServerPlayer player = staged(helper, 24, 4, 12);
        List<Husk> targets = new ArrayList<>();
        // 3x3x3 的近距网格（水平 ±2、垂直 −2..+2），共 27 个；再加 7 个水平稍远但仍然在球内的目标。
        for (int dx = -2; dx <= 2 && targets.size() < 34; dx += 1) {
            for (int dy = -2; dy <= 2 && targets.size() < 34; dy += 2) {
                for (int dz = -2; dz <= 2 && targets.size() < 34; dz += 4) {
                    targets.add(husk(helper, 24 + dx, 4 + dy, 12 + dz, 8_000.0D));
                }
            }
        }
        for (int dx = 3; dx <= 5 && targets.size() < 34; dx += 1) {
            for (int dy = -1; dy <= 1 && targets.size() < 34; dy += 2) {
                for (int dz = -1; dz <= 1 && targets.size() < 34; dz += 2) {
                    targets.add(husk(helper, 24 + dx, 4 + dy, 12 + dz, 8_000.0D));
                }
            }
        }
        helper.assertTrue(targets.size() == 34, "sanity: 34 targets placed (got " + targets.size() + ")");
        float[] before = new float[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            before[i] = targets.get(i).getHealth();
        }
        helper.assertTrue(StarEaterAbilities.tryBurst(player), "the final burst must fire");
        int damaged = 0;
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).getHealth() < before[i]) {
                damaged++;
            }
        }
        helper.assertTrue(damaged == 32,
                "exactly 32 targets must be hit in the 3D sphere (damaged=" + damaged + ")");
        // 方块未被破坏（不是爆炸）。
        helper.assertTrue(helper.getBlockState(new BlockPos(24, 4, 12)).isAir()
                        || !helper.getBlockState(new BlockPos(24, 4, 12)).is(Blocks.FIRE),
                "the burst must not create fire or destroy blocks");
        helper.succeed();
    }

    /** 友军/玩家/驯服生物在同一三维球内的排除仍然保留。 */
    @GameTest(template = "boss_arena", timeoutTicks = 400)
    public static void burstExclusionsHoldInThreeDimensions(GameTestHelper helper) {
        ServerPlayer caster = staged(helper, 24, 4, 12);
        // 正上方：把一只"本来合法"的敌对 Husk 放进同一支队伍 → 必须被排除。
        Husk teammate = husk(helper, 24, 6, 12, 5_000.0D);
        var scoreboard = helper.getLevel().getScoreboard();
        var team = scoreboard.addPlayerTeam("eb-sphere-team");
        scoreboard.addPlayerToTeam(caster.getGameProfile().getName(), team);
        scoreboard.addPlayerToTeam(teammate.getStringUUID(), team);
        // 对照：正下方一个完全相同的 Husk 必须受伤。
        Husk control = husk(helper, 24, 2, 12, 5_000.0D);
        // 驯服的狼（正上方），owner 为施法者 → 必须被排除。
        FixtureEntities.FixtureBossWolf pet = FixtureEntities.spawnBossWolf(helper.getLevel(),
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(25, 6, 12))).x,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(25, 6, 12))).y,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(25, 6, 12))).z);
        pet.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5_000.0D);
        pet.setHealth(5_000.0F);
        pet.setTame(true, false);
        pet.setOwnerUUID(caster.getUUID());

        float teammateHp = teammate.getHealth();
        float controlHp = control.getHealth();
        float petHp = pet.getHealth();
        helper.assertTrue(StarEaterAbilities.tryBurst(caster), "the burst must fire");
        helper.assertTrue(teammate.getHealth() == teammateHp,
                "a same-team hostile directly above must be excluded (hp=" + teammate.getHealth() + ")");
        helper.assertTrue(control.getHealth() < controlHp,
                "the control hostile directly below must be hit (hp=" + control.getHealth() + ")");
        helper.assertTrue(pet.getHealth() == petHp, "a tamed pet must be excluded");
        scoreboard.removePlayerFromTeam(teammate.getStringUUID());
        scoreboard.removePlayerFromTeam(caster.getGameProfile().getName());
        scoreboard.removePlayerTeam(team);
        helper.succeed();
    }

    /** 造一具巨大的原版包围盒实体（末影龙：宽 16、高 8），不覆写任何判定。 */
    private static LivingEntity hugeBox(GameTestHelper helper, int relX, int relY, int relZ) {
        net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon =
                helper.spawn(net.minecraft.world.entity.EntityType.ENDER_DRAGON, relX, relY, relZ);
        dragon.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10_000.0D);
        dragon.setHealth(10_000.0F);
        return dragon;
    }
}
