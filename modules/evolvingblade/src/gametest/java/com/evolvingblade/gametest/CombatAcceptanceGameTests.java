package com.evolvingblade.gametest;

import java.util.UUID;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.combat.StarEaterAbilities;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.registry.EBItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * COMBAT-ACCEPTANCE：真实实体 + 真实 {@code Player.attack} 下的吸血与斩 Boss 加成验收。
 *
 * <p>测量原则：
 * <ul>
 *   <li>吸血一律用目标"命中前 / 命中后"的<b>真实生命值差</b>（不是 requestedDamage / getNewDamage）；</li>
 *   <li>斩 Boss 加成用按 (目标 UUID + 伤害类型) 作用域的监听器同时记录
 *       <b>Incoming 原始值</b>与 <b>Pre→Post 的真实生命差</b>；</li>
 *   <li>所有夹具用 {@code helper.absolutePos(...)} 定位于本测试结构内，并断言在 bounds 内且区块已加载、实体可 tick；</li>
 *   <li>每个测试开头都 {@code reset()} 监听器（UUID 作用域），结束时再次清理。</li>
 * </ul>
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class CombatAcceptanceGameTests {

    private static final long STAGE3_POINTS = 350L;
    private static final long STAGE3_BOSSES = 1L;
    private static final long STAGE5_POINTS = 1200L;
    private static final long STAGE5_BOSSES = 6L;
    private static final long STAGE6_POINTS = 2000L;
    private static final long STAGE6_BOSSES = 12L;
    private static final long FINAL_POINTS = 3000L;
    private static final long FINAL_BOSSES = 20L;

    private CombatAcceptanceGameTests() {}

    // ------------------------------------------------------------------
    // 作用域监听器：UUID + 伤害类型
    // ------------------------------------------------------------------

    public static final class Scoped {
        volatile UUID victim;
        volatile String typeContains;
        volatile String clampType;
        volatile boolean cancelIncoming;
        volatile boolean zeroPre;
        volatile Float clampPre;

        volatile int incomingSeen;
        volatile int preSeen;
        volatile int postSeen;
        volatile String lastType;
        volatile float lastAmount;
        volatile float preHealth = Float.NaN;
        volatile float lastPostDelta = Float.NaN;
        /** 按伤害类型分开计数的原始值 / 事件数 / Pre→Post 真实生命差（用于一次攻击内区分主伤与 bonus）。 */
        final java.util.Map<String, Float> rawByType = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.Map<String, Integer> incomingByType = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.Map<String, Integer> postByType = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.Map<String, Float> postDeltaByType = new java.util.concurrent.ConcurrentHashMap<>();

        boolean matches(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
            UUID current = this.victim;
            if (current == null || !current.equals(entity.getUUID())) {
                return false;
            }
            String contains = this.typeContains;
            if (contains == null) {
                return true;
            }
            return typeId(source).contains(contains);
        }

        static String typeId(net.minecraft.world.damagesource.DamageSource source) {
            return source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("unknown");
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onIncoming(LivingIncomingDamageEvent event) {
            if (matches(event.getEntity(), event.getSource())) {
                String type = typeId(event.getSource());
                this.incomingSeen++;
                this.lastType = type;
                this.lastAmount = event.getAmount();
                this.incomingByType.merge(type, 1, Integer::sum);
                this.rawByType.put(type, event.getAmount());
                if (this.cancelIncoming) {
                    event.setCanceled(true);
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onPre(LivingDamageEvent.Pre event) {
            if (matches(event.getEntity(), event.getSource())) {
                String type = typeId(event.getSource());
                this.preSeen++;
                this.preHealth = event.getEntity().getHealth();
                boolean clampHere = this.clampPre != null
                        && (this.clampType == null || type.contains(this.clampType));
                if (clampHere) {
                    event.setNewDamage(this.clampPre);
                }
                if (this.zeroPre) {
                    event.setNewDamage(0.0F);
                }
                // 记录**本次实际生效**的伤害（含外部模组式改写 / 清零），而不是改写前的值。
                this.rawByType.put(type, event.getNewDamage());
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onPost(LivingDamageEvent.Post event) {
            if (matches(event.getEntity(), event.getSource())) {
                String type = typeId(event.getSource());
                this.postSeen++;
                this.postByType.merge(type, 1, Integer::sum);
                float before = this.preHealth;
                if (!Float.isNaN(before)) {
                    float delta = before - event.getEntity().getHealth();
                    this.lastPostDelta = delta;
                    this.postDeltaByType.put(type, delta);
                }
            }
        }

        float raw(String typePart) {
            return byPart(this.rawByType, typePart);
        }

        int incomingCount(String typePart) {
            for (var entry : this.incomingByType.entrySet()) {
                if (entry.getKey().contains(typePart)) {
                    return entry.getValue();
                }
            }
            return 0;
        }

        int postCount(String typePart) {
            for (var entry : this.postByType.entrySet()) {
                if (entry.getKey().contains(typePart)) {
                    return entry.getValue();
                }
            }
            return 0;
        }

        float postDelta(String typePart) {
            return byPart(this.postDeltaByType, typePart);
        }

        private static float byPart(java.util.Map<String, Float> map, String typePart) {
            for (var entry : map.entrySet()) {
                if (entry.getKey().contains(typePart)) {
                    return entry.getValue();
                }
            }
            return Float.NaN;
        }

        void reset() {
            this.victim = null;
            this.typeContains = null;
            this.clampType = null;
            this.cancelIncoming = false;
            this.zeroPre = false;
            this.clampPre = null;
            this.incomingSeen = 0;
            this.preSeen = 0;
            this.postSeen = 0;
            this.lastType = null;
            this.lastAmount = 0.0F;
            this.preHealth = Float.NaN;
            this.lastPostDelta = Float.NaN;
            this.rawByType.clear();
            this.incomingByType.clear();
            this.postByType.clear();
            this.postDeltaByType.clear();
        }
    }

    private static final Scoped SCOPED = new Scoped();

    static {
        NeoForge.EVENT_BUS.register(SCOPED);
    }

    // ------------------------------------------------------------------
    // 夹具（全部位于本测试结构内）
    // ------------------------------------------------------------------

    private static ServerPlayer stagedPlayer(GameTestHelper helper, long points, long bosses) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, points, bosses);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        return player;
    }

    /** 非创造的真实 ServerPlayer（GameTest 的 mock 玩家 isCreative() 恒为 true，会绕过无敌语义）。 */
    private static ServerPlayer nonCreativePlayer(GameTestHelper helper, long points, long bosses, String name) {
        var level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), name),
                net.minecraft.server.level.ClientInformation.createDefault());
        player.getAbilities().instabuild = false;
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, points, bosses);
        return player;
    }

    private static void charge(ServerPlayer player) {
        for (int i = 0; i < 40; i++) {
            player.doTick();
        }
    }

    private static void makeHealHeadroom(ServerPlayer player) {
        player.setHealth(4.0F);
    }

    private static double healed(ServerPlayer player, float before) {
        return player.getHealth() - before;
    }

    /** 断言相对坐标在结构 bounds 内、区块已加载且实体可 tick，返回该点的中心。 */
    private static Vec3 centreOf(GameTestHelper helper, int relX, int relY, int relZ) {
        BlockPos absolute = helper.absolutePos(new BlockPos(relX, relY, relZ));
        Vec3 centre = new Vec3(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        helper.assertTrue(helper.getBounds().contains(centre),
                "fixture position must be inside the actual test bounds (pos=" + centre + ", bounds="
                        + helper.getBounds() + ")");
        helper.assertTrue(helper.getLevel().isLoaded(absolute) && helper.getLevel().isPositionEntityTicking(absolute),
                "fixture chunk must be loaded and entity-ticking (pos=" + absolute + ")");
        return centre;
    }

    /** 非致命、不会被阳光点燃的普通目标（相对坐标，结构内）。 */
    private static Husk livingHusk(GameTestHelper helper, double maxHealth, int relX, int relZ) {
        Vec3 centre = centreOf(helper, relX, 1, relZ);
        Husk husk = FixtureEntities.spawn(helper.getLevel(), centre.x, centre.y, centre.z,
                (type, level) -> new Husk(type, level));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        husk.setHealth((float) maxHealth);
        return husk;
    }

    /** dev-only 超大生命 Boss 夹具（自身持有非空 ServerBossEvent 字段 + 真实 HUSK 类型）。 */
    private static FixtureEntities.LargeHealthFixtureHusk livingBoss(GameTestHelper helper, double maxHealth,
            int relX, int relZ) {
        Vec3 centre = centreOf(helper, relX, 1, relZ);
        FixtureEntities.LargeHealthFixtureHusk boss = FixtureEntities.spawn(helper.getLevel(), centre.x, centre.y,
                centre.z, FixtureEntities.LargeHealthFixtureHusk::new).withFixtureMaxHealth(maxHealth);
        boss.setHealth((float) maxHealth);
        return boss;
    }

    // ------------------------------------------------------------------
    // 吸血（真实 Player.attack；按真实生命差计算）
    // ------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void lifestealStage3IsFivePercentOfActualHealthLoss(GameTestHelper helper) {
        SCOPED.reset();
        ServerPlayer player = stagedPlayer(helper, STAGE3_POINTS, STAGE3_BOSSES);
        Husk victim = livingHusk(helper, 500.0D, 4, 4);
        charge(player);
        makeHealHeadroom(player);
        float before = player.getHealth();
        double hp0 = victim.getHealth();
        player.attack(victim);
        double loss = hp0 - victim.getHealth();
        double healed = healed(player, before);
        helper.assertTrue(victim.isAlive(), "the stage-3 target must survive (non-lethal)");
        helper.assertTrue(loss > 1.0D && loss < 4.0D / 0.05D,
                "sanity: loss must be real but below the cap threshold (loss=" + loss + ")");
        helper.assertTrue(Math.abs(healed - loss * 0.05D) < 0.05D,
                "stage 3 lifesteal must equal 5% of the ACTUAL health loss (loss=" + loss + ", healed=" + healed
                        + ", expected=" + (loss * 0.05D) + ")");
        SCOPED.reset();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void lifestealStage5IsCappedAtFour(GameTestHelper helper) {
        SCOPED.reset();
        ServerPlayer player = stagedPlayer(helper, STAGE5_POINTS, STAGE5_BOSSES);
        Husk victim = livingHusk(helper, 5_000.0D, 4, 4);
        charge(player);
        makeHealHeadroom(player);
        float before = player.getHealth();
        double hp0 = victim.getHealth();
        player.attack(victim);
        double loss = hp0 - victim.getHealth();
        helper.assertTrue(victim.isAlive(), "the target must survive");
        helper.assertTrue(loss * 0.05D > 4.0D + 1.0D, "sanity: enough loss to reach the cap (loss=" + loss + ")");
        helper.assertTrue(Math.abs(healed(player, before) - 4.0D) < 0.05D,
                "the 5% lifesteal must be capped at 4 (loss=" + loss + ", healed=" + healed(player, before) + ")");
        SCOPED.reset();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void lifestealStage6IsTenPercentCappedAtEight(GameTestHelper helper) {
        SCOPED.reset();
        ServerPlayer player = stagedPlayer(helper, STAGE6_POINTS, STAGE6_BOSSES);
        Husk victim = livingHusk(helper, 5_000.0D, 4, 4);
        charge(player);
        makeHealHeadroom(player);
        float before = player.getHealth();
        double hp0 = victim.getHealth();
        player.attack(victim);
        double loss = hp0 - victim.getHealth();
        helper.assertTrue(victim.isAlive(), "the target must survive");
        helper.assertTrue(loss * 0.10D > 8.0D + 1.0D, "sanity: enough loss to reach the 8 cap (loss=" + loss + ")");
        helper.assertTrue(Math.abs(healed(player, before) - 8.0D) < 0.05D,
                "the 10% lifesteal must be capped at 8 (loss=" + loss + ", healed=" + healed(player, before) + ")");
        SCOPED.reset();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void lifestealUsesRealPostArmorAndPostAbsorptionLoss(GameTestHelper helper) {
        SCOPED.reset();
        ServerPlayer player = stagedPlayer(helper, STAGE3_POINTS, STAGE3_BOSSES);

        // A) 无护甲对照（阶段 3，48 伤害，目标 500 HP 存活）。
        Husk bare = livingHusk(helper, 500.0D, 2, 3);
        charge(player);
        makeHealHeadroom(player);
        float barePlayer = player.getHealth();
        double bareHp = bare.getHealth();
        player.attack(bare);
        double bareLoss = bareHp - bare.getHealth();
        double bareHeal = healed(player, barePlayer);

        // B) 同一阶段、同一玩家、目标只多一件钻石胸甲：真实生命损失必须更小，回血按更小的实际损失计算。
        Husk armored = livingHusk(helper, 500.0D, 5, 3);
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        armored.tick(); // 原版在实体 tick 里才把装备的护甲值挂上
        helper.assertTrue(armored.getArmorValue() > 0,
                "sanity: the armored fixture must really have armor (armor=" + armored.getArmorValue() + ")");
        charge(player);
        makeHealHeadroom(player);
        float armoredPlayer = player.getHealth();
        double armoredHp = armored.getHealth();
        player.attack(armored);
        double armoredLoss = armoredHp - armored.getHealth();
        double armoredHeal = healed(player, armoredPlayer);

        helper.assertTrue(bare.isAlive() && armored.isAlive(), "both targets must survive (non-lethal)");
        helper.assertTrue(bareLoss < 48.0D, "sanity: stage-3 attribute damage is 48 (bare loss=" + bareLoss + ")");
        helper.assertTrue(armoredLoss < bareLoss,
                "armor must reduce the REAL health loss (bare=" + bareLoss + ", armored=" + armoredLoss + ")");
        helper.assertTrue(Math.abs(bareHeal - bareLoss * 0.05D) < 0.05D,
                "unarmored lifesteal must equal 5% of the real loss (loss=" + bareLoss + ", healed=" + bareHeal + ")");
        helper.assertTrue(Math.abs(armoredHeal - armoredLoss * 0.05D) < 0.05D,
                "armored lifesteal must equal 5% of the SMALLER real loss (loss=" + armoredLoss + ", healed="
                        + armoredHeal + ")");
        helper.assertTrue(armoredHeal < bareHeal && armoredHeal < 4.0D,
                "the armored heal must be smaller and must not be capped (bare=" + bareHeal + ", armored="
                        + armoredHeal + ")");

        // C) 部分吸收：吸收吃掉一部分伤害 → 真实生命损失更小，回血随之更小。
        Husk partial = livingHusk(helper, 500.0D, 2, 5);
        partial.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(1_000.0D);
        partial.setAbsorptionAmount(20.0F);
        charge(player);
        makeHealHeadroom(player);
        float partialPlayer = player.getHealth();
        double partialHp = partial.getHealth();
        player.attack(partial);
        double partialLoss = partialHp - partial.getHealth();
        double partialHeal = healed(player, partialPlayer);
        helper.assertTrue(partial.getAbsorptionAmount() < 20.0F,
                "sanity: absorption must have been consumed (left=" + partial.getAbsorptionAmount() + ")");
        helper.assertTrue(partialLoss > 0.0D && partialLoss < bareLoss,
                "partially absorbed hits must cause a smaller real health loss (partial=" + partialLoss
                        + ", bare=" + bareLoss + ")");
        helper.assertTrue(Math.abs(partialHeal - partialLoss * 0.05D) < 0.05D,
                "lifesteal must follow the post-absorption real loss (loss=" + partialLoss + ", healed="
                        + partialHeal + ")");

        // D) 完全吸收：真实生命损失 0 → 回血 0。
        Husk absorbed = livingHusk(helper, 500.0D, 5, 5);
        absorbed.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(100_000.0D);
        absorbed.setAbsorptionAmount(100_000.0F);
        double absorbedHp = absorbed.getHealth();
        charge(player);
        makeHealHeadroom(player);
        float absorbedPlayer = player.getHealth();
        player.attack(absorbed);
        double absorbedLoss = absorbedHp - absorbed.getHealth();
        helper.assertTrue(absorbed.getAbsorptionAmount() > 0.0F,
                "sanity: absorption must exist (left=" + absorbed.getAbsorptionAmount() + ")");
        helper.assertTrue(Math.abs(absorbedLoss) < 0.01D,
                "sanity: a fully absorbed hit causes no real health loss (loss=" + absorbedLoss + ")");
        helper.assertTrue(Math.abs(healed(player, absorbedPlayer)) < 0.01D,
                "a fully absorbed hit must not heal (healed=" + healed(player, absorbedPlayer) + ")");
        SCOPED.reset();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 斩 Boss 加成：百分比（未达上限）与上限
    // ------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void bossBonusStage5IsOnePercentBelowAndAtTheCap(GameTestHelper helper) {
        SCOPED.reset();
        ServerPlayer player = stagedPlayer(helper, STAGE5_POINTS, STAGE5_BOSSES);

        // 10000 HP：1% = 100，未达上限 200 → 原始加成必须精确等于 100。
        FixtureEntities.LargeHealthFixtureHusk small = livingBoss(helper, 10_000.0D, 3, 3);
        charge(player);
        SCOPED.reset();
        SCOPED.victim = small.getUUID();
        SCOPED.typeContains = "boss_slayer";
        double hp0 = small.getHealth();
        player.attack(small);
        helper.assertTrue(small.isAlive(), "the boss fixture must survive");
        helper.assertTrue(SCOPED.incomingSeen == 1,
                "exactly one boss_slayer event per primary hit (seen=" + SCOPED.incomingSeen + ")");
        helper.assertTrue(Math.abs(SCOPED.lastAmount - 100.0F) < 0.5F,
                "stage 5 bonus on 10000 HP must be exactly 1% = 100 (raw=" + SCOPED.lastAmount + ")");
        helper.assertTrue(Math.abs(SCOPED.lastPostDelta - SCOPED.lastAmount) < 0.5F,
                "without armor/resistance the bonus must take EXACTLY its raw value from health (raw="
                        + SCOPED.lastAmount + ", postDelta=" + SCOPED.lastPostDelta + ")");
        helper.assertTrue(hp0 - small.getHealth() > SCOPED.lastAmount,
                "total loss must exceed the bonus alone (main damage also landed)");

        // 100000 HP：1% = 1000 → 上限 200。
        FixtureEntities.LargeHealthFixtureHusk huge = livingBoss(helper, 100_000.0D, 6, 3);
        charge(player);
        SCOPED.reset();
        SCOPED.victim = huge.getUUID();
        SCOPED.typeContains = "boss_slayer";
        player.attack(huge);
        helper.assertTrue(Math.abs(SCOPED.lastAmount - 200.0F) < 0.5F,
                "stage 5 bonus on 100000 HP must be capped at 200 (raw=" + SCOPED.lastAmount + ")");
        SCOPED.reset();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void bossBonusFinalStageIsThreePercentBelowAndAtTheCap(GameTestHelper helper) {
        SCOPED.reset();
        ServerPlayer player = stagedPlayer(helper, FINAL_POINTS, FINAL_BOSSES);

        FixtureEntities.LargeHealthFixtureHusk small = livingBoss(helper, 10_000.0D, 3, 3);
        charge(player);
        SCOPED.reset();
        SCOPED.victim = small.getUUID();
        SCOPED.typeContains = "boss_slayer";
        player.attack(small);
        helper.assertTrue(small.isAlive(), "the boss fixture must survive");
        helper.assertTrue(SCOPED.incomingSeen == 1, "exactly one boss_slayer event (seen=" + SCOPED.incomingSeen + ")");
        helper.assertTrue(Math.abs(SCOPED.lastAmount - 300.0F) < 0.5F,
                "final-stage bonus on 10000 HP must be exactly 3% = 300 (raw=" + SCOPED.lastAmount + ")");
        helper.assertTrue(Math.abs(SCOPED.lastPostDelta - SCOPED.lastAmount) < 1.0F,
                "without armor/resistance the bonus must take EXACTLY its raw value from health (raw="
                        + SCOPED.lastAmount + ", postDelta=" + SCOPED.lastPostDelta + ")");

        FixtureEntities.LargeHealthFixtureHusk huge = livingBoss(helper, 100_000.0D, 6, 3);
        charge(player);
        SCOPED.reset();
        SCOPED.victim = huge.getUUID();
        SCOPED.typeContains = "boss_slayer";
        player.attack(huge);
        helper.assertTrue(Math.abs(SCOPED.lastAmount - 1_000.0F) < 0.5F,
                "final-stage bonus on 100000 HP must be capped at 1000 (raw=" + SCOPED.lastAmount + ")");
        SCOPED.reset();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void bossBonusBypassesArmorButMainDamageDoesNot(GameTestHelper helper) {
        SCOPED.reset();
        ServerPlayer player = stagedPlayer(helper, STAGE5_POINTS, STAGE5_BOSSES);

        // 无护甲 Boss：主伤害与加成。
        FixtureEntities.LargeHealthFixtureHusk bare = livingBoss(helper, 10_000.0D, 3, 3);
        charge(player);
        SCOPED.reset();
        SCOPED.victim = bare.getUUID();
        SCOPED.typeContains = "boss_slayer";
        player.attack(bare);
        float bareBonusPost = SCOPED.lastPostDelta;
        SCOPED.reset();
        bare.setHealth(bare.getMaxHealth());
        bare.invulnerableTime = 0;
        charge(player);
        SCOPED.victim = bare.getUUID();
        SCOPED.typeContains = "player_attack";
        double bareHp = bare.getHealth();
        player.attack(bare); // 同 tick 内第二次命中：加成被限速 → 只有主伤害
        double bareMain = bareHp - bare.getHealth();

        // 有护甲 Boss：同一个玩家，但目标不同 → 加成独立；主伤害必须变小，加成原始值不变。
        FixtureEntities.LargeHealthFixtureHusk armored = livingBoss(helper, 10_000.0D, 6, 3);
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        armored.tick(); // 装备护甲需要实体 tick 才生效
        helper.assertTrue(armored.getArmorValue() > 0,
                "sanity: the armored boss fixture must really have armor (armor=" + armored.getArmorValue() + ")");
        charge(player);
        SCOPED.reset();
        SCOPED.victim = armored.getUUID();
        SCOPED.typeContains = "boss_slayer";
        player.attack(armored);
        float armoredBonusPost = SCOPED.lastPostDelta;
        SCOPED.reset();
        armored.setHealth(armored.getMaxHealth());
        armored.invulnerableTime = 0;
        charge(player);
        SCOPED.victim = armored.getUUID();
        SCOPED.typeContains = "player_attack";
        double armoredHp = armored.getHealth();
        player.attack(armored);
        double armoredMain = armoredHp - armored.getHealth();

        helper.assertTrue(bare.isAlive() && armored.isAlive(), "both bosses must stay alive");
        helper.assertTrue(Math.abs(bareBonusPost - 100.0F) < 0.5F && Math.abs(armoredBonusPost - 100.0F) < 0.5F,
                "the bonus must take its FULL post value on both bosses — armor must not reduce it"
                        + " (bare postDelta=" + bareBonusPost + ", armored postDelta=" + armoredBonusPost + ")");
        helper.assertTrue(Math.abs(bareBonusPost - armoredBonusPost) < 0.01F,
                "the bonus's real health delta must be identical with and without armor (bare=" + bareBonusPost
                        + ", armored=" + armoredBonusPost + ")");
        helper.assertTrue(armoredMain < bareMain,
                "armor must reduce the MAIN damage (bare=" + bareMain + ", armored=" + armoredMain + ")");
        SCOPED.reset();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 限速（真实世界时钟推进）
    // ------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void bossBonusRateLimitUsesRealWorldTicksAndIsPerPlayer(GameTestHelper helper) {
        SCOPED.reset();
        ServerPlayer first = stagedPlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        ServerPlayer second = stagedPlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        FixtureEntities.LargeHealthFixtureHusk boss = livingBoss(helper, 100_000.0D, 4, 4);
        helper.runAfterDelay(1L, () -> {
            long start = StarEaterAbilities.clock(first);
            helper.assertTrue(StarEaterAbilities.applyBossBonus(first, boss),
                    "the first bonus for player A + target must be allowed");
            helper.assertFalse(StarEaterAbilities.applyBossBonus(first, boss),
                    "the same player + target inside the same tick must be refused");
            helper.assertTrue(StarEaterAbilities.applyBossBonus(second, boss),
                    "player B must have an independent rate limit for the same target");
            int window = EBConfig.settings().abilities().bossBonusCooldownTicks();
            helper.assertTrue(window == 20, "sanity: window is 20 ticks (window=" + window + ")");
            // 真实推进世界时钟 19 tick：仍然在 20 tick 窗口内 → 拒绝（并断言时钟真的走了 19 tick）。
            helper.runAfterDelay(19L, () -> {
                long now = StarEaterAbilities.clock(first);
                helper.assertTrue(boss.isAlive(), "the target must stay alive during the rate-limit check");
                helper.assertTrue(now - start == 19L,
                        "the world clock must really have advanced 19 ticks (start=" + start + ", now=" + now + ")");
                helper.assertFalse(StarEaterAbilities.applyBossBonus(first, boss),
                        "tick 19 after the first bonus must still be refused");
                helper.runAfterDelay(1L, () -> {
                    long later = StarEaterAbilities.clock(first);
                    helper.assertTrue(later - start == 20L,
                            "the world clock must really have advanced 20 ticks (start=" + start + ", later="
                                    + later + ")");
                    helper.assertTrue(StarEaterAbilities.applyBossBonus(first, boss),
                            "tick 20 after the first bonus must be accepted again");
                    helper.assertTrue(boss.isAlive(), "the target must still be alive at the end");
                    SCOPED.reset();
                    helper.succeed();
                });
            });
        });
    }

    // ------------------------------------------------------------------
    // 取消 / 无敌 / 抗性（只作用于 bonus 的来源隔离）
    // ------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void bossBonusCancelPreZeroAndInvulnerabilityAreHonoured(GameTestHelper helper) {
        SCOPED.reset();
        // A) 取消 bonus 的 Incoming：主伤害照常，bonus 的 Post 不存在、生命差为 0。
        ServerPlayer canceller = stagedPlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        FixtureEntities.LargeHealthFixtureHusk cancelled = livingBoss(helper, 100_000.0D, 3, 3);
        charge(canceller);
        SCOPED.reset();
        SCOPED.victim = cancelled.getUUID();
        SCOPED.typeContains = "boss_slayer";
        SCOPED.cancelIncoming = true;
        double hp0 = cancelled.getHealth();
        canceller.attack(cancelled);
        double mainLoss = hp0 - cancelled.getHealth();
        helper.assertTrue(SCOPED.incomingSeen > 0, "the bonus attempt must be observable");
        helper.assertTrue(SCOPED.postSeen == 0 && Float.isNaN(SCOPED.lastPostDelta),
                "a cancelled bonus must produce no Post/health loss (posts=" + SCOPED.postSeen + ")");
        helper.assertTrue(mainLoss > 0.0D, "the main damage must still land when only the bonus is cancelled");

        // B) Pre 把 bonus 清零：同样不能造成任何真实生命损失。
        SCOPED.reset();
        FixtureEntities.LargeHealthFixtureHusk zeroed = livingBoss(helper, 100_000.0D, 6, 3);
        SCOPED.victim = zeroed.getUUID();
        SCOPED.typeContains = "boss_slayer";
        SCOPED.zeroPre = true;
        double hpZero = zeroed.getHealth();
        StarEaterAbilities.applyBossBonus(canceller, zeroed);
        helper.assertTrue(SCOPED.preSeen > 0, "the bonus Pre event must fire");
        helper.assertTrue(Math.abs(zeroed.getHealth() - hpZero) < 0.01D
                        && (Float.isNaN(SCOPED.lastPostDelta) || Math.abs(SCOPED.lastPostDelta) < 0.01F),
                "a Pre-zeroed bonus must cause no real health loss (postDelta=" + SCOPED.lastPostDelta
                        + ", health=" + zeroed.getHealth() + ")");

        // C) 主伤害被取消 → 不会触发 bonus（同一次真实攻击内）。
        SCOPED.reset();
        ServerPlayer mainCanceller = stagedPlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        FixtureEntities.LargeHealthFixtureHusk mainCancelled = livingBoss(helper, 100_000.0D, 2, 5);
        charge(mainCanceller);
        SCOPED.victim = mainCancelled.getUUID();
        SCOPED.typeContains = "player_attack";
        SCOPED.cancelIncoming = true;
        double hpMain = mainCancelled.getHealth();
        mainCanceller.attack(mainCancelled);
        SCOPED.reset();
        SCOPED.victim = mainCancelled.getUUID();
        SCOPED.typeContains = "boss_slayer";
        helper.assertTrue(Math.abs(mainCancelled.getHealth() - hpMain) < 0.01D,
                "a cancelled main hit must deal no damage");
        helper.assertTrue(SCOPED.incomingSeen == 0,
                "a cancelled main hit must not trigger the boss bonus (seen=" + SCOPED.incomingSeen + ")");

        // D) 无敌：非创造玩家（GameTest mock 玩家 isCreative() 恒为 true 会绕过无敌语义）。
        ServerPlayer nonCreative = nonCreativePlayer(helper, FINAL_POINTS, FINAL_BOSSES, "eb-noncreative");
        FixtureEntities.LargeHealthFixtureHusk invulnerable = livingBoss(helper, 100_000.0D, 5, 5);
        invulnerable.setInvulnerable(true);
        helper.assertTrue(invulnerable.isInvulnerable(), "sanity: the fixture must be invulnerable");
        SCOPED.reset();
        SCOPED.victim = invulnerable.getUUID();
        SCOPED.typeContains = "boss_slayer";
        double hpInv = invulnerable.getHealth();
        StarEaterAbilities.applyBossBonus(nonCreative, invulnerable);
        helper.assertTrue(Math.abs(invulnerable.getHealth() - hpInv) < 0.01D
                        && SCOPED.incomingSeen == 0,
                "the bonus must not damage an invulnerable target (creative=" + nonCreative.isCreative() + ")");
        SCOPED.reset();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void bossBonusObeysResistanceWithIndependentPlayerAndTarget(GameTestHelper helper) {
        SCOPED.reset();
        // 基准：无抗性，玩家 A / 目标 A。
        ServerPlayer basePlayer = stagedPlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        FixtureEntities.LargeHealthFixtureHusk baseBoss = livingBoss(helper, 10_000.0D, 3, 3);
        charge(basePlayer);
        SCOPED.reset();
        SCOPED.victim = baseBoss.getUUID();
        SCOPED.typeContains = "boss_slayer";
        basePlayer.attack(baseBoss);
        float baseRaw = SCOPED.lastAmount;
        float basePost = SCOPED.lastPostDelta;
        boolean baseAlive = baseBoss.isAlive();

        // 抗性 II：**不同玩家 + 不同目标**，避免 20 tick 限速污染。
        ServerPlayer resistPlayer = stagedPlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        FixtureEntities.LargeHealthFixtureHusk resistBoss = livingBoss(helper, 10_000.0D, 6, 3);
        resistBoss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 1));
        charge(resistPlayer);
        SCOPED.reset();
        SCOPED.victim = resistBoss.getUUID();
        SCOPED.typeContains = "boss_slayer";
        resistPlayer.attack(resistBoss);
        float resistRaw = SCOPED.lastAmount;
        float resistPost = SCOPED.lastPostDelta;
        boolean resistAlive = resistBoss.isAlive();

        helper.assertTrue(baseAlive && resistAlive, "both targets must stay alive");
        helper.assertTrue(Math.abs(baseRaw - 300.0F) < 0.5F && Math.abs(resistRaw - 300.0F) < 0.5F,
                "both cases must actually trigger the bonus with the same raw value (base=" + baseRaw
                        + ", resisted=" + resistRaw + ")");
        helper.assertTrue(Math.abs(basePost - baseRaw) < 0.5F,
                "without resistance the bonus must take its full raw value from health (raw=" + baseRaw
                        + ", postDelta=" + basePost + ")");
        helper.assertTrue(resistPost > 0.0F && Math.abs(resistPost - resistRaw * 0.6F) < 1.0F,
                "Resistance II must reduce the bonus to ~60% (raw=" + resistRaw + ", postDelta=" + resistPost + ")");
        SCOPED.reset();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void lifestealIgnoresBonusDamageAndNeverRecurses(GameTestHelper helper) {
        SCOPED.reset();
        ServerPlayer player = stagedPlayer(helper, STAGE5_POINTS, STAGE5_BOSSES);
        FixtureEntities.LargeHealthFixtureHusk boss = livingBoss(helper, 10_000.0D, 4, 4);
        charge(player);
        makeHealHeadroom(player);

        // 一次真实攻击内，按 UUID 观察**所有**伤害类型：主伤被其它模组式的监听器压到 40，bonus 保持 100。
        SCOPED.reset();
        SCOPED.victim = boss.getUUID();
        SCOPED.typeContains = null;       // 观察该目标的全部伤害来源
        SCOPED.clampType = "player_attack";
        SCOPED.clampPre = 40.0F;
        float playerBefore = player.getHealth();
        double hp0 = boss.getHealth();
        player.attack(boss);
        double totalLoss = hp0 - boss.getHealth();
        double heal = healed(player, playerBefore);

        float mainRaw = SCOPED.raw("player_attack");
        int mainPosts = SCOPED.postCount("player_attack");
        int bonusIncoming = SCOPED.incomingCount("boss_slayer");
        int bonusPosts = SCOPED.postCount("boss_slayer");
        float bonusDelta = SCOPED.postDelta("boss_slayer");

        helper.assertTrue(Math.abs(mainRaw - 40.0F) < 0.5F,
                "sanity: the clamped main damage must be 40 (raw=" + mainRaw + ")");
        helper.assertTrue(mainPosts == 1, "sanity: exactly one main Post (posts=" + mainPosts + ")");
        helper.assertTrue(bonusIncoming == 1 && bonusPosts == 1,
                "exactly ONE bonus event must be produced by one primary hit (incoming=" + bonusIncoming
                        + ", posts=" + bonusPosts + ")");
        helper.assertTrue(Math.abs(bonusDelta - 100.0F) < 0.5F,
                "the bonus's real health delta must be exactly 100 (delta=" + bonusDelta + ")");
        helper.assertTrue(Math.abs(totalLoss - 140.0D) < 0.5D,
                "the single attack must cause exactly main 40 + bonus 100 = 140 real health loss (total="
                        + totalLoss + ")");
        helper.assertTrue(Math.abs(heal - 2.0D) < 0.05D,
                "lifesteal must be 5% of the 40-damage MAIN hit only (=2), never of main+bonus (healed=" + heal + ")");

        // 同 tick 再次进入生产入口：限速必须拒绝，且不能产生任何新的 bonus 事件（防递归观察）。
        SCOPED.reset();
        SCOPED.victim = boss.getUUID();
        SCOPED.typeContains = null;
        StarEaterAbilities.applyBossBonus(player, boss);
        helper.assertTrue(SCOPED.incomingCount("boss_slayer") == 0 && SCOPED.postCount("boss_slayer") == 0,
                "no additional bonus may be produced inside the rate window (incoming="
                        + SCOPED.incomingCount("boss_slayer") + ")");
        SCOPED.reset();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void underchargedSecondaryAndSkillNeverLifesteal(GameTestHelper helper) {
        SCOPED.reset();
        // A) 未满蓄力：真实攻击但攻击条为 0。
        ServerPlayer player = stagedPlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        Husk underchargedTarget = livingHusk(helper, 5_000.0D, 2, 3);
        makeHealHeadroom(player);
        float beforeA = player.getHealth();
        double underHp = underchargedTarget.getHealth();
        player.attack(underchargedTarget);
        helper.assertTrue(underchargedTarget.getHealth() < underHp, "sanity: the undercharged hit must deal damage");
        helper.assertTrue(Math.abs(healed(player, beforeA)) < 0.01D,
                "an undercharged hit must not lifesteal (healed=" + healed(player, beforeA) + ")");

        // B) 次要目标（非 PRIMARY 目标）的真实伤害：模拟"横扫/次要命中"，不吸血。
        // 注意：MAX_HEALTH 是 RangedAttribute，上限 1024，所以这里用阶段 3（48 伤害）的玩家 + 500 HP 目标，
        // 保证主目标**非致命**，且能直接对比"主攻击后 / 次要命中后"的玩家血量。
        ServerPlayer secondaryPlayer = stagedPlayer(helper, STAGE3_POINTS, STAGE3_BOSSES);
        Husk primary = livingHusk(helper, 500.0D, 5, 3);
        Husk secondary = livingHusk(helper, 500.0D, 5, 5);
        charge(secondaryPlayer);
        makeHealHeadroom(secondaryPlayer);
        float afterPrimary;
        double primaryHp = primary.getHealth();
        double secondaryHp = secondary.getHealth();
        secondaryPlayer.attack(primary); // 建立 PRIMARY 上下文（主目标会吸血，但只按主伤害）
        afterPrimary = secondaryPlayer.getHealth();
        secondary.hurt(helper.getLevel().damageSources().playerAttack(secondaryPlayer), 30.0F);
        double secondaryLoss = secondaryHp - secondary.getHealth();
        float afterSecondary = secondaryPlayer.getHealth();
        helper.assertTrue(primary.isAlive(), "sanity: the primary target must survive (non-lethal) (hp="
                + primary.getHealth() + "/" + primary.getMaxHealth() + ")");
        helper.assertTrue(primary.getHealth() < primaryHp, "sanity: the primary hit must deal real damage");
        helper.assertTrue(secondaryLoss > 0.0D,
                "sanity: the secondary target must take real damage (loss=" + secondaryLoss + ")");
        helper.assertTrue(Math.abs(afterSecondary - afterPrimary) < 0.01F,
                "a secondary (non-PRIMARY) target must not add ANY lifesteal: player health must be unchanged"
                        + " (afterPrimary=" + afterPrimary + ", afterSecondary=" + afterSecondary + ")");

        // C) 本模组技能伤害：真实有效伤害，但不吸血。
        Husk skillTarget = livingHusk(helper, 5_000.0D, 2, 5);
        makeHealHeadroom(player);
        float beforeC = player.getHealth();
        double skillHp = skillTarget.getHealth();
        skillTarget.hurt(helper.getLevel().damageSources()
                .source(StarEaterAbilities.SWORD_ABILITY, player, player), 10.0F);
        double skillLoss = skillHp - skillTarget.getHealth();
        helper.assertTrue(skillLoss > 0.0D, "sanity: the skill must deal real damage (loss=" + skillLoss + ")");
        helper.assertTrue(Math.abs(healed(player, beforeC)) < 0.01D,
                "our ability damage must never lifesteal (healed=" + healed(player, beforeC) + ")");
        SCOPED.reset();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void mainAndBonusNeverClearInvulnerableTime(GameTestHelper helper) {
        SCOPED.reset();
        ServerPlayer player = stagedPlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        FixtureEntities.LargeHealthFixtureHusk boss = livingBoss(helper, 100_000.0D, 4, 4);
        charge(player);
        boss.invulnerableTime = 0;
        player.attack(boss);
        helper.assertTrue(boss.invulnerableTime > 0,
                "vanilla sets the hurt cooldown; our code must not clear it (value=" + boss.invulnerableTime + ")");
        int afterMain = boss.invulnerableTime;
        StarEaterAbilities.applyBossBonus(player, boss);
        helper.assertTrue(boss.invulnerableTime >= afterMain,
                "the bonus path must not zero invulnerableTime (before=" + afterMain + ", after="
                        + boss.invulnerableTime + ")");
        SCOPED.reset();
        helper.succeed();
    }
}
