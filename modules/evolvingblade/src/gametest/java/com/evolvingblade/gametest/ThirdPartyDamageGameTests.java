package com.evolvingblade.gametest;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.combat.CombatMath;
import com.evolvingblade.combat.EffectiveDamage;
import com.evolvingblade.combat.StageAttributes;
import com.evolvingblade.combat.StarEaterAbilities;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.registry.EBItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 第三方加伤的<b>真实集成</b>回归：事件式物品属性、数据组件、原版保留 id、
 * 首次切剑、升级、交换、卸下残留，以及"技能按有效攻击取值"。
 *
 * <p>与 {@link AttributeMathGameTests} 的分工：那里验证的是"玩家身上的属性代数"，
 * 这里验证的是"第三方<b>从物品侧</b>提出的修改能不能真的走到玩家属性上"——
 * 也就是 1.0.0 里 {@code ensureBaseline} 每次强制改写保留槽位所破坏的那条路径。
 *
 * <p>关键不变量：
 * <ul>
 *   <li>第三方 id 从不被抹掉；</li>
 *   <li>原版保留 id（{@code minecraft:base_attack_damage}）由原版装备 tick 自己维护，
 *       本模组永不写入，因此不会与之打架；</li>
 *   <li>反复协调/升级不会累加；卸下后不残留；</li>
 *   <li>把"已乘过的 getValue"反减为 ADD 的路径在结构上不存在（差值只用纯净 ADD 数值）。</li>
 * </ul>
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class ThirdPartyDamageGameTests {

    private static final long STAGE0 = 0L;
    private static final long STAGE7_POINTS = 3000L;
    private static final long STAGE7_BOSSES = 20L;

    private static final ResourceLocation THIRD_PARTY =
            ResourceLocation.fromNamespaceAndPath("ebtest", "third_party_attack");
    private static final ResourceLocation THIRD_PARTY_ITEM =
            ResourceLocation.fromNamespaceAndPath("ebtest", "third_party_item_attack");
    private static final ResourceLocation THIRD_PARTY_COMPONENT =
            ResourceLocation.fromNamespaceAndPath("ebtest", "third_party_component_attack");

    /**
     * 事件式第三方：只给"带本测试 nonce 标记的那一把剑"追加 +11 攻击。
     *
     * <p><b>为什么必须用 nonce 而不是全局 boolean</b>：GameTest 是<b>并行</b>跑的，
     * 一个全局开关会在异步 tick 窗口内给<b>所有</b>玩家的本剑都加 +11，
     * 污染其它测试（实测把 pristine 快照从 1500 抬到 1511）。
     * 这里改为比对 {@code CUSTOM_DATA} 里的 nonce：
     * 只有本测试用 {@link #markedSword} 造出来的那把栈会被加伤，
     * 别的测试用普通 {@code new ItemStack(EBItems.starEater())} 造的剑完全不受影响。
     *
     * <p>监听器<b>按测试注册/注销</b>（见 {@link #withItemListener}），不再挂在静态块里。
     */
    private static final class ThirdPartyItemListener {
        private final java.util.UUID nonce;

        ThirdPartyItemListener(java.util.UUID nonce) {
            this.nonce = nonce;
        }

        @SubscribeEvent
        public void onItemAttributes(ItemAttributeModifierEvent event) {
            if (!hasNonce(event.getItemStack(), this.nonce)) {
                return;
            }
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(THIRD_PARTY_ITEM, 11.0D, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
    }

    /** 本测试专用的 nonce 标记键（CUSTOM_DATA 里的一条自定义 NBT）。 */
    private static final String NONCE_KEY = "ebtest_nonce";

    private static ItemStack markedSword(java.util.UUID nonce) {
        ItemStack stack = new ItemStack(EBItems.starEater());
        net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
        data.putString(NONCE_KEY, nonce.toString());
        stack.set(DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(data));
        return stack;
    }

    private static boolean hasNonce(ItemStack stack, java.util.UUID nonce) {
        if (stack == null || stack.isEmpty() || nonce == null) {
            return false;
        }
        net.minecraft.world.item.component.CustomData data =
                stack.get(DataComponents.CUSTOM_DATA);
        return data != null && nonce.toString().equals(data.copyTag().getString(NONCE_KEY));
    }

    /**
     * 在<b>本测试自己的作用域</b>内注册事件监听器，并保证一定注销。
     *
     * <p>{@code try/finally} 覆盖整个测试体：即使断言失败抛异常，监听器也会被注销，
     * 不会泄漏到并行运行的其它测试。
     */
    private static void withItemListener(java.util.UUID nonce, Runnable body) {
        ThirdPartyItemListener listener = new ThirdPartyItemListener(nonce);
        NeoForge.EVENT_BUS.register(listener);
        try {
            body.run();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }

    private ThirdPartyDamageGameTests() {}

    private static ServerPlayer staged(GameTestHelper helper, long points, long bosses) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, points, bosses);
        return player;
    }

    /**
     * 把玩家放进<b>当前 GameTest 模板内部</b>。
     *
     * <p>为什么必须这样做：GameTest 模板位于随机的数百万格坐标上，而
     * {@code makeMockServerPlayerInLevel()} 造出来的模拟玩家留在世界原点附近。
     * 一旦在两者之间做 {@code Level.clip(...)}（LOS 检查），就会横跨巨距加载区块而
     * 把服务器主线程卡死（实测线程栈停在
     * {@code ServerChunkCache.getChunk → BlockGetter.clip}）。
     *
     * <p>用 {@code helper.absolutePos(...)} 把<b>模板相对坐标</b>换算成绝对坐标，
     * 玩家与目标才真正落在同一个模板里、相距只有几格，射线不会强制加载远处区块。
     */
    private static ServerPlayer stagedInTemplate(GameTestHelper helper, long points, long bosses,
            int relX, int relY, int relZ) {
        ServerPlayer player = staged(helper, points, bosses);
        net.minecraft.core.BlockPos absolute = helper.absolutePos(
                new net.minecraft.core.BlockPos(relX, relY, relZ));
        net.minecraft.world.phys.Vec3 centre = net.minecraft.world.phys.Vec3.atCenterOf(absolute);
        player.moveTo(centre.x, centre.y, centre.z, 0.0F, 0.0F);
        player.setNoGravity(true);
        return player;
    }

    /** 玩家与目标的真实距离（用于夹具 sanity，防止再次出现"相距数百万格"的夹具）。 */
    private static double distance(ServerPlayer player, net.minecraft.world.entity.Entity target) {
        return player.position().distanceTo(target.position());
    }

    private static void equipSword(ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        GrowthService.syncAfterChange(player);
    }

    private static double attack(ServerPlayer player) {
        return player.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /** 诊断用：把当前 ATTACK_DAMAGE 上的所有修饰符打印成一行（只在断言失败信息里用）。 */
    private static String describeAttack(ServerPlayer player) {
        StringBuilder text = new StringBuilder("[base=")
                .append(player.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue())
                .append(" total=").append(attack(player)).append(" | ");
        for (AttributeModifier modifier : player.getAttribute(Attributes.ATTACK_DAMAGE).getModifiers()) {
            text.append(modifier.id()).append('=').append(modifier.amount())
                    .append('(').append(modifier.operation()).append(") ");
        }
        return text.append(']').toString();
    }

    // ------------------------------------------------------------------
    // 真实第三方路径
    // ------------------------------------------------------------------

    /**
     * 事件式物品属性（{@code ItemAttributeModifierEvent} → {@code ItemStack.getAttributeModifiers()}）
     * 必须真的走到玩家属性上，并且与阶段差值<b>叠加</b>而不是互相覆盖。
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void itemAttributeEventBonusSurvivesReconcile(GameTestHelper helper) {
        java.util.UUID nonce = java.util.UUID.randomUUID();
        withItemListener(nonce, () -> {
            ServerPlayer player = staged(helper, STAGE7_POINTS, STAGE7_BOSSES);
            // 只有带本测试 nonce 的这把剑会被监听器加伤；别的测试的剑不受影响。
            player.setItemInHand(InteractionHand.MAIN_HAND, markedSword(nonce));
            GrowthService.syncAfterChange(player);
            // 装备 tick 把物品修饰符（含第三方 +11）挂到玩家身上。
            player.doTick();
            double withBonus = attack(player);
            helper.assertTrue(Math.abs(withBonus - (1500.0D + 11.0D)) < 0.01D,
                    "an ItemAttributeModifierEvent +11 must reach the player attribute and stack on the stage total"
                            + " (value=" + withBonus + ", expected=1511); modifiers=" + describeAttack(player));
            // 第三方 id 必须仍在（我们只动自己的 id）。
            helper.assertTrue(
                    player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(THIRD_PARTY_ITEM) != null,
                    "the third-party modifier id must never be removed by our reconciliation");
            // 反复协调/升级都不能累加，也不能把第三方抹掉。
            for (int i = 0; i < 6; i++) {
                GrowthService.syncAfterChange(player);
                player.doTick();
            }
            helper.assertTrue(Math.abs(attack(player) - 1511.0D) < 0.01D,
                    "repeated reconcile must not accumulate on top of a third-party bonus (value="
                            + attack(player) + ", expected=1511)");
            helper.assertTrue(
                    player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(THIRD_PARTY_ITEM) != null,
                    "the third-party modifier must survive repeated reconciliation");
            // 升级（同模式更多点数）后总量仍然是"新阶段 + 第三方"。
            GrowthService.setCounters(player, 0L, 0L);
            String stageZero = describeAttack(player);
            helper.assertTrue(Math.abs(attack(player) - (7.0D + 11.0D)) < 0.01D,
                    "after dropping back to stage 0 the total must be 7 + third-party 11 (value="
                            + attack(player) + "); modifiers=" + stageZero);
            helper.assertTrue(
                    player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(THIRD_PARTY_ITEM) != null,
                    "the third-party modifier must survive a stage change");
        });
        helper.succeed();
    }

    /**
     * 数据组件式第三方：把 {@code ATTRIBUTE_MODIFIERS} 组件换成"原版基线 + 第三方百分比"，
     * 我们的差值必须<b>叠加</b>在乘法之前（乘法只由属性管线执行一次）。
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void dataComponentMultiplierAppliesExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = staged(helper, STAGE0, 0L);
        ItemStack stack = new ItemStack(EBItems.starEater());
        // 阶段 0 目标总攻击 7：基线 +6，再让第三方 50% TOTAL 乘一次 → (1+6)*1.5 = 10.5。
        ItemAttributeModifiers thirdParty = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 6.0D,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(THIRD_PARTY_COMPONENT, 0.5D,
                                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                        EquipmentSlotGroup.MAINHAND)
                .build();
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, thirdParty);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        GrowthService.syncAfterChange(player);
        player.doTick();
        double value = attack(player);
        helper.assertTrue(Math.abs(value - 10.5D) < 0.01D,
                "a data-component 1.5x TOTAL must be applied exactly once by the attribute pipeline"
                        + " (value=" + value + ", expected=10.5)");
        // 关键：我们的差值永远是 ADD，且不会把 1.5 倍的乘法结果反减掉——
        // 阶段 0 差值为 0，所以本模组修饰符必须不存在。
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(StageAttributes.DAMAGE_ID) == null,
                "at stage 0 the stage delta must be 0 (no modifier), even with a third-party multiplier");
        helper.assertTrue(
                player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(THIRD_PARTY_COMPONENT) != null,
                "the third-party multiplier id must survive");
        helper.succeed();
    }

    /**
     * 同 vanilla 基线 id 被第三方合法改写：原版装备 tick 会用它自己那一份覆盖，
     * 而<b>我们绝不能抢先写</b>（1.0.0 的 ensureBaseline 正是在这里造成覆盖）。
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void vanillaReservedBaselineIdIsNeverWrittenByUs(GameTestHelper helper) {
        ServerPlayer player = staged(helper, STAGE7_POINTS, STAGE7_BOSSES);
        equipSword(player);
        player.doTick();
        var baseline = player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(Item.BASE_ATTACK_DAMAGE_ID);
        helper.assertTrue(baseline != null, "vanilla must own the reserved baseline slot after an equip tick");
        double expectedBaseline = com.evolvingblade.item.StarEaterItem.BASE_DAMAGE_MODIFIER;
        helper.assertTrue(Math.abs(baseline.amount() - expectedBaseline) < 1.0E-6D
                        && baseline.operation() == AttributeModifier.Operation.ADD_VALUE,
                "the reserved baseline slot must be exactly the item's own stage-0 value"
                        + " (amount=" + baseline.amount() + ", expected=" + expectedBaseline + ")");
        // 第三方在同一 id 上提出的<b>合法增量</b>必须完整保留：
        // 我们只读不写这个槽位，也不从自己的差值里把它减掉。
        // 它把基线从 +6 改成 +9（相对物品基线 +3），终阶总值应当随之 =1503，而不是被"补回"1500。
        player.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 9.0D, AttributeModifier.Operation.ADD_VALUE));
        StageAttributes.reconcile(player);
        var afterReconcile = player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(Item.BASE_ATTACK_DAMAGE_ID);
        helper.assertTrue(Math.abs(afterReconcile.amount() - 9.0D) < 1.0E-6D,
                "our reconcile must never rewrite the vanilla reserved baseline slot"
                        + " (amount=" + afterReconcile.amount() + ", expected=9.0 — written by 'third party')");
        double total = attack(player);
        helper.assertTrue(Math.abs(total - 1503.0D) < 0.01D,
                "a third-party delta on the reserved baseline slot must be PRESERVED,"
                        + " not cancelled by our stage delta (value=" + total + ", expected=1503 = 1500 + (9-6))");
        // 我们再协调几次也不能把它"抹平"回 1500。
        for (int i = 0; i < 4; i++) {
            StageAttributes.reconcile(player);
            player.doTick();
        }
        helper.assertTrue(Math.abs(attack(player) - 1503.0D) < 0.01D,
                "repeated reconciliation must keep the third-party baseline delta (value=" + attack(player) + ")");
        helper.succeed();
    }

    /**
     * 第三方把保留槽位往上加（+6 → +15，相对物品基线 +9）时，终阶总值必须是 <b>1509</b>。
     *
     * <p>这是对 1.1.0 早期断言（错误地期望 1500）的修正：阶段差值只加"相对阶段 0 的增量"，
     * <b>不得</b>为了把数字拉回 1500 而抵消别人合法的 +9。
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void thirdPartyBaselineIncreaseIsPreservedNotCancelled(GameTestHelper helper) {
        ServerPlayer player = staged(helper, STAGE7_POINTS, STAGE7_BOSSES);
        equipSword(player);
        player.doTick();
        helper.assertTrue(Math.abs(attack(player) - 1500.0D) < 0.01D,
                "sanity: pristine stage 7 must be 1500 (value=" + attack(player) + ")");
        // 第三方把 minecraft:base_attack_damage 从 6 改成 15（+9）。
        player.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 15.0D, AttributeModifier.Operation.ADD_VALUE));
        StageAttributes.reconcile(player);
        double total = attack(player);
        helper.assertTrue(Math.abs(total - 1509.0D) < 0.01D,
                "a legitimate third-party +9 on the reserved baseline id must be preserved:"
                        + " expected 1509, got " + total);
        var slot = player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(Item.BASE_ATTACK_DAMAGE_ID);
        helper.assertTrue(slot != null && Math.abs(slot.amount() - 15.0D) < 1.0E-6D,
                "the reserved slot must still read 15 (amount=" + (slot == null ? "null" : slot.amount()) + ")");
        var ours = player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(StageAttributes.DAMAGE_ID);
        helper.assertTrue(ours != null && Math.abs(ours.amount() - (1500.0D - 7.0D)) < 0.01D,
                "our delta must stay the pure stage difference (1493), not 1484"
                        + " (amount=" + (ours == null ? "null" : ours.amount()) + ")");
        helper.succeed();
    }

    /**
     * 首次切剑（同一 tick 内切剑 + 攻击，原版装备 tick 还没跑）：阶段差值必须自己补上物品基线，
     * 而不是依赖"写保留槽位"。
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void firstSwingWithoutEquipmentTickIsExact(GameTestHelper helper) {
        ServerPlayer player = staged(helper, STAGE7_POINTS, STAGE7_BOSSES);
        // 刻意不调用 doTick()：模拟"刚拿到剑、原版基线还没挂上"。
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        StageAttributes.reconcile(player);
        double immediate = attack(player);
        helper.assertTrue(Math.abs(immediate - 1500.0D) < 0.01D,
                "the first swing before vanilla's equipment tick must still total the stage value"
                        + " (value=" + immediate + ", expected=1500)");
        // 原版装备 tick 之后补偿必须撤销，总量不变（不出现 +6 的双算）。
        player.doTick();
        double afterTick = attack(player);
        helper.assertTrue(Math.abs(afterTick - 1500.0D) < 0.01D,
                "after vanilla applies the reserved baseline the total must stay exact (value=" + afterTick
                        + ", expected=1500)");
        // 再协调一次：此时保留槽位已由原版挂上，补偿登记必须被撤销。
        StageAttributes.reconcile(player);
        helper.assertTrue(Math.abs(attack(player) - 1500.0D) < 0.01D,
                "reconciling again must not change the total (value=" + attack(player) + ")");
        // 按<b>本玩家</b>检查残留（不能用全局计数：并行测试的其它玩家可能仍持剑）。
        helper.assertFalse(StageAttributes.hasPendingCompensation(player, true),
                "this player's attack compensation must be cleared once the slot is present");
        helper.assertFalse(StageAttributes.hasPendingCompensation(player, false),
                "this player's speed compensation must be cleared once the slot is present");
        helper.succeed();
    }

    /**
     * 补偿必须<b>幂等</b>：保留槽位一直缺席时，重复 {@code reconcile} 必须维持同一份补偿，
     * 不能"读一次就当成第三方合法改动"而把补偿吃掉（这正是上一版 1501≠1507 的根因）。
     * 同时验证：缺席补偿用<b>本模组独立 id</b>，阶段 0 的<b>阶段增量</b>修饰符不存在。
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void repeatedReconcileKeepsMissingSlotCompensation(GameTestHelper helper) {
        ServerPlayer player = staged(helper, STAGE7_POINTS, STAGE7_BOSSES);
        // 刻意不 doTick：模拟"原版装备 tick 还没把保留槽位挂上"。
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        StageAttributes.reconcile(player);
        double first = attack(player);
        double firstSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        helper.assertTrue(Math.abs(first - 1500.0D) < 0.01D,
                "first reconcile with a missing slot must total 1500 (value=" + first + "); "
                        + describeAttack(player));
        helper.assertTrue(Math.abs(firstSpeed - 2.4D) < 0.01D,
                "attack speed must be 2.4 even with a missing speed slot (value=" + firstSpeed + ")");
        helper.assertTrue(StageAttributes.hasPendingCompensation(player, true),
                "the attack compensation must be active while the slot is missing");
        helper.assertTrue(StageAttributes.hasPendingCompensation(player, false),
                "the speed compensation must be active while the slot is missing");
        // 反复协调 8 次：数值必须一模一样（既不累加、也不丢补偿）。
        for (int i = 0; i < 8; i++) {
            StageAttributes.reconcile(player);
            helper.assertTrue(Math.abs(attack(player) - 1500.0D) < 0.01D,
                    "repeated reconcile must keep the compensation (iteration " + i
                            + ", value=" + attack(player) + "); " + describeAttack(player));
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - 2.4D) < 0.01D,
                    "repeated reconcile must keep the speed compensation (iteration " + i
                            + ", value=" + player.getAttributeValue(Attributes.ATTACK_SPEED) + ")");
        }
        helper.assertTrue(StageAttributes.hasPendingCompensation(player, true),
                "the compensation must still be active after repeated reconciles");
        // 原版结算之后（doTick 挂上槽位）：补偿撤销，总值不变。
        player.doTick();
        StageAttributes.reconcile(player);
        helper.assertTrue(Math.abs(attack(player) - 1500.0D) < 0.01D,
                "after vanilla settles the slot the total must stay 1500 (value=" + attack(player) + ")");
        helper.assertFalse(StageAttributes.hasPendingCompensation(player, true),
                "the compensation must be removed once vanilla supplied the declared baseline");
        helper.assertFalse(StageAttributes.hasPendingCompensation(player, false),
                "the speed compensation must be removed as well");
        helper.succeed();
    }

    /**
     * 阶段 0：<b>没有</b>阶段增量修饰符（delta = 0），
     * 缺席时的保留槽位补偿走<b>独立的本模组 id</b>，并且不改动原版保留 id。
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void stageZeroKeepsCompensationSeparateFromStageDelta(GameTestHelper helper) {
        ServerPlayer player = staged(helper, STAGE0, 0L);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        StageAttributes.reconcile(player);
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(StageAttributes.DAMAGE_ID) == null,
                "stage 0 must not carry a stage-delta modifier (delta = 0)");
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_SPEED).getModifier(StageAttributes.SPEED_ID) == null,
                "stage 0 must not carry a speed stage-delta modifier");
        helper.assertTrue(
                player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(StageAttributes.BASELINE_DAMAGE_ID) != null,
                "but the missing-slot compensation must exist under its own id");
        helper.assertTrue(Math.abs(attack(player) - 7.0D) < 0.01D,
                "stage 0 total must still be exactly 7 (value=" + attack(player) + "); " + describeAttack(player));
        // 原版保留 id 只能由原版维护：我们不写入它，但也不得删除原版已挂的那一份。
        player.doTick();
        StageAttributes.reconcile(player);
        var vanillaBaseline = player.getAttribute(Attributes.ATTACK_DAMAGE)
                .getModifier(Item.BASE_ATTACK_DAMAGE_ID);
        helper.assertTrue(vanillaBaseline != null,
                "vanilla must own the reserved baseline slot after an equip tick");
        helper.assertTrue(Math.abs(vanillaBaseline.amount()
                        - com.evolvingblade.item.StarEaterItem.BASE_DAMAGE_MODIFIER) < 1.0E-6D,
                "the vanilla reserved slot must keep the item's declared value (amount="
                        + vanillaBaseline.amount() + ")");
        helper.assertTrue(Math.abs(attack(player) - 7.0D) < 0.01D,
                "stage 0 must remain 7 after the equip tick (value=" + attack(player) + ")");
        helper.assertFalse(StageAttributes.hasPendingCompensation(player, true),
                "the compensation must be gone once vanilla supplied the slot");
        helper.succeed();
    }

    /** 升级（同 tick 内的属性协调）必须立刻正确，且不覆盖第三方 id。 */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void stageUpgradeKeepsThirdPartyAddAndIsImmediate(GameTestHelper helper) {
        ServerPlayer player = staged(helper, STAGE0, 0L);
        player.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                new AttributeModifier(THIRD_PARTY, 13.0D, AttributeModifier.Operation.ADD_VALUE));
        equipSword(player);
        helper.assertTrue(Math.abs(attack(player) - 20.0D) < 0.01D,
                "stage 0 + third-party 13 must total 20 (value=" + attack(player) + ")");
        GrowthService.setCounters(player, STAGE7_POINTS, STAGE7_BOSSES);
        helper.assertTrue(GrowthService.stage(player) == 7, "sanity: stage 7 after counters change");
        double jumped = attack(player);
        helper.assertTrue(Math.abs(jumped - (1500.0D + 13.0D)) < 0.01D,
                "after an immediate upgrade the total must be stage 7's 1500 plus the third-party 13"
                        + " (value=" + jumped + ", expected=1513)");
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(THIRD_PARTY) != null,
                "the third-party modifier must survive an upgrade");
        helper.succeed();
    }

    /** 交换到别的武器：只移除本模组的差值，第三方与其它物品的基线不受影响、也不残留。 */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void swappingAwayLeavesNoResidue(GameTestHelper helper) {
        ServerPlayer player = staged(helper, STAGE7_POINTS, STAGE7_BOSSES);
        player.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                new AttributeModifier(THIRD_PARTY, 5.0D, AttributeModifier.Operation.ADD_VALUE));
        equipSword(player);
        player.doTick();
        // 换成一把普通剑：我们的阶段差值必须消失，第三方仍在。
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD));
        GrowthService.syncAfterChange(player);
        player.doTick();
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(StageAttributes.DAMAGE_ID) == null,
                "our stage modifier must be removed when the blade is swapped out");
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(THIRD_PARTY) != null,
                "an unrelated third-party modifier must NOT be removed on swap");
        // 卸下（空手）：依然没有本模组残留。
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        GrowthService.syncAfterChange(player);
        player.doTick();
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(StageAttributes.DAMAGE_ID) == null,
                "unequipping must not leave our stage modifier behind");
        // 残留检查必须针对<b>本玩家</b>：GameTest 并行时其它测试玩家可能仍持剑，
        // 全局计数（trackedPlayers）根本不是本玩家的残留证据。
        helper.assertFalse(StageAttributes.hasPendingCompensation(player, true),
                "this player must have no pending attack baseline compensation after unequip");
        helper.assertFalse(StageAttributes.hasPendingCompensation(player, false),
                "this player must have no pending speed baseline compensation after unequip");
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_SPEED).getModifier(StageAttributes.SPEED_ID) == null,
                "our speed stage modifier must be removed on unequip");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 技能按"有效攻击"取值
    // ------------------------------------------------------------------

    /**
     * 技能伤害必须<b>等于系数 × 服务端有效攻击</b>：正加伤必须真的增强技能，
     * 阶段配置值只是"关闭继承"时的回退口径，<b>不是</b>天花板。
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void abilityDamageTracksEffectiveAttackWithoutStageCeiling(GameTestHelper helper) {
        var settings = EBConfig.settings();
        // 阶段 3 基础 48：有效攻击 1000 → 剑气 1000（不再被 48 夹住）。
        double over = CombatMath.waveDamage(settings, 3, 1000.0D, true);
        helper.assertTrue(Math.abs(over - 1000.0D) < 1.0E-6D,
                "wave damage must be 1.0 x effective attack, not clamped to the stage base (value=" + over + ")");
        // 低有效攻击同样跟随。
        double under = CombatMath.waveDamage(settings, 3, 30.0D, true);
        helper.assertTrue(Math.abs(under - 30.0D) < 1.0E-6D,
                "wave damage must follow a lower effective attack (value=" + under + ", expected=30)");
        // 关闭继承：完全回到阶段基础值，与有效攻击无关。
        double off = CombatMath.waveDamage(settings, 3, 1000.0D, false);
        helper.assertTrue(Math.abs(off - 48.0D) < 1.0E-6D,
                "with inheritance off the wave damage must be the stage base (value=" + off + ")");
        // 爆发系数 1.5：阶段 4 有效攻击 1000 → 1500。
        double burst = CombatMath.burstDamage(settings, 4, 1000.0D, true);
        helper.assertTrue(Math.abs(burst - 1500.0D) < 1.0E-6D,
                "burst damage must be 1.5 x effective attack (value=" + burst + ", expected=1500)");
        // 非法有效攻击（NaN / 负数）必须回落到阶段基础值，绝不产生 NaN 伤害。
        helper.assertTrue(Math.abs(CombatMath.waveDamage(settings, 3, Double.NaN, true) - 48.0D) < 1.0E-6D,
                "a NaN effective attack must fall back to the stage base");
        helper.assertTrue(Math.abs(CombatMath.waveDamage(settings, 3, -5.0D, true) - 48.0D) < 1.0E-6D,
                "a negative effective attack must fall back to the stage base");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 真实战斗数值：标准属性加伤与乘法加伤都必须体现在技能上
    // ------------------------------------------------------------------

    /**
     * 标准属性加伤（第三方对 {@code minecraft:attack_damage} 的 ADD）+ 乘法，
     * 必须体现在<b>真实生命损失</b>上（不是只测纯函数）。
     *
     * <p><b>刻意用阶段 4（裂空，基础 120）而不是终阶</b>：一手核实原版把
     * {@code ATTACK_DAMAGE} 硬钳在 <b>2048</b>（{@code RangedAttribute(..., 0.0, 2048.0)}），
     * 终阶 1513 × 1.5 = 2269.5 会被钳到 2048，测出来的就不是"技能跟随有效攻击"了。
     * 阶段 4：基础 120 + 第三方 13 = 133；再 ×1.5 = 199.5，都远低于 2048。
     * <pre>
     *   剑气 raw = 1.0 × 133   = 133      → 真实掉血 133
     *   爆发 raw = 1.5 × 133   = 199.5    → 真实掉血 199.5
     * </pre>
     * 目标是无甲、超大生命的活体，所以生命损失应精确等于 raw（护甲/抗性/吸收都不参与）。
     */
    @GameTest(template = "boss_arena", timeoutTicks = 400)
    public static void standardAttributeBonusAndMultiplierChangeRealAbilityDamage(GameTestHelper helper) {
        // 顺手把"原版上限 2048"这个事实固定下来（本模组不改它）。
        helper.assertTrue(Attributes.ATTACK_DAMAGE.value()
                        instanceof net.minecraft.world.entity.ai.attributes.RangedAttribute ranged
                        && ranged.getMaxValue() == 2048.0D,
                "vanilla caps ATTACK_DAMAGE at 2048; this test therefore uses stage 4 (120) to stay well below it");

        ServerPlayer player = stagedInTemplate(helper, 700L, 3L, 24, 4, 12); // 阶段 4：裂空，基础攻击 120
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        GrowthService.syncAfterChange(player);
        player.doTick();
        GrowthService.syncAfterChange(player);
        helper.assertTrue(GrowthService.stage(player) == 4,
                "sanity: the fixture must be stage 4 (stage=" + GrowthService.stage(player) + ")");
        double pristine = attack(player);
        helper.assertTrue(Math.abs(pristine - 120.0D) < 0.01D,
                "sanity: pristine stage 4 must be 120 (value=" + pristine + "); " + describeAttack(player));

        // 第三方 +13（标准 flat 加伤）。
        player.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                new AttributeModifier(THIRD_PARTY, 13.0D, AttributeModifier.Operation.ADD_VALUE));
        StageAttributes.reconcile(player);
        double withAdd = attack(player);
        helper.assertTrue(Math.abs(withAdd - 133.0D) < 0.01D,
                "the +13 must be preserved (value=" + withAdd + "); " + describeAttack(player));

        var settings = EBConfig.settings();
        double expectedWave = CombatMath.waveDamage(settings, 4, withAdd, true);
        helper.assertTrue(Math.abs(expectedWave - 133.0D) < 1.0E-6D,
                "the wave must take 1.0 x effective attack (got " + expectedWave + ")");

        // 真实伤害：模板内正东 3 格、无甲、10000 生命的活体。
        FixtureEntities.LargeHealthFixtureHusk victim = noArmorHusk(helper, 27, 4, 12, 10_000.0D);
        victim.setNoGravity(true);
        // 夹具 sanity：两者必须在同一个模板内、相距 3 格左右。
        // 没有这几条断言时，一旦玩家留在世界原点而目标在模板里，距离会是数百万格，
        // LOS 检查会横跨巨距加载区块并卡死服务器（实测）。
        helper.assertTrue(Math.abs(distance(player, victim) - 3.0D) < 0.1D,
                "sanity: the player and the wave target must be ~3 blocks apart inside the template"
                        + " (distance=" + distance(player, victim) + ")");
        helper.assertTrue(player.getEyePosition().distanceTo(victim.getEyePosition()) < 24.0D,
                "sanity: the target must be inside the wave range (eye distance="
                        + player.getEyePosition().distanceTo(victim.getEyePosition()) + ")");
        helper.assertTrue(Math.abs(victim.getX() - player.getX()) < 8.0D
                        && Math.abs(victim.getZ() - player.getZ()) < 8.0D,
                "sanity: the target must be in the local template area, not millions of blocks away"
                        + " (target=" + victim.position() + ", player=" + player.position() + ")");
        aimAt(player, victim);
        // 视线方向必须真的指向目标（模拟玩家的 lookAt 可能只发包不改 rotation）。
        double[] direction = {player.getLookAngle().x, player.getLookAngle().y, player.getLookAngle().z};
        var toTarget = victim.getEyePosition().subtract(player.getEyePosition()).normalize();
        double dot = direction[0] * toTarget.x + direction[1] * toTarget.y + direction[2] * toTarget.z;
        helper.assertTrue(dot > 0.999D,
                "the cast direction must point at the target (dot=" + dot + ", look=" + player.getLookAngle()
                        + ", toTarget=" + toTarget + ")");
        helper.assertTrue(clearSight(helper, player, victim),
                "sanity: the line of sight to the wave target must be clear");
        helper.assertTrue(StarEaterAbilities.isValidTarget(player, victim),
                "sanity: the fixture must be a valid ability target (hostile/boss classification)");
        double before = victim.getHealth();
        helper.assertTrue(StarEaterAbilities.tryWave(player), "the wave must fire");
        double loss = before - victim.getHealth();
        helper.assertTrue(Math.abs(loss - 133.0D) < 0.5D,
                "the wave must really remove 133 health for effective attack 133 (loss=" + loss + ")");

        // 再加 50% TOTAL 乘法：有效攻击变成 199.5，技能必须随之上浮。
        player.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                new AttributeModifier(THIRD_PARTY_ITEM, 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        StageAttributes.reconcile(player);
        double withMult = attack(player);
        // 手算：(1 + 6 + 13 + 113) × 1.5 = 133 × 1.5 = 199.5
        helper.assertTrue(Math.abs(withMult - 199.5D) < 0.05D,
                "the 1.5x TOTAL must multiply the whole total once (value=" + withMult
                        + ", expected=199.5); " + describeAttack(player));
        double expectedBurst = CombatMath.burstDamage(settings, 4, withMult, true);
        helper.assertTrue(Math.abs(expectedBurst - 299.25D) < 1.0E-6D,
                "the burst must take 1.5 x effective attack (got " + expectedBurst + ")");
        FixtureEntities.LargeHealthFixtureHusk burstVictim = noArmorHusk(helper, 21, 4, 12, 20_000.0D);
        burstVictim.setNoGravity(true);
        helper.assertTrue(Math.abs(distance(player, burstVictim) - 3.0D) < 0.1D,
                "sanity: the burst target must also be in the same template, ~3 blocks west"
                        + " (distance=" + distance(player, burstVictim) + ")");
        double beforeBurst = burstVictim.getHealth();
        helper.assertTrue(StarEaterAbilities.tryBurst(player), "the burst must fire");
        double burstLoss = beforeBurst - burstVictim.getHealth();
        helper.assertTrue(Math.abs(burstLoss - 299.25D) < 1.0D,
                "the burst must really remove 1.5 x effective attack (loss=" + burstLoss
                        + ", expected=299.25)");
        helper.succeed();
    }

    /**
     * 显式把玩家的朝向设成指向目标：`Entity.lookAt` 对 ServerPlayer 只改 rotation 并发包，
     * 在某些模拟玩家上 `getLookAngle()` 不会跟着变；这里直接算 yaw/pitch 并 set，
     * 保证技能射线方向与目标方向一致。
     */
    private static void aimAt(ServerPlayer player, net.minecraft.world.entity.LivingEntity target) {
        net.minecraft.world.phys.Vec3 from = player.getEyePosition();
        net.minecraft.world.phys.Vec3 to = target.getEyePosition();
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float) (Math.toDegrees(Math.atan2(-dy, horizontal)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setOldPosAndRot();
        player.setYHeadRot(yaw);
    }

    /** 与技能内部同一套 {@code ClipContext.Block.COLLIDER} 的视线测量。 */
    private static boolean clearSight(GameTestHelper helper, ServerPlayer player,
            net.minecraft.world.entity.LivingEntity target) {
        return helper.getLevel().clip(new net.minecraft.world.level.ClipContext(
                player.getEyePosition(), target.getEyePosition(),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType()
                == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** 无甲、超大生命的活体目标（走真实受伤路径，不覆写 hurt/setHealth）。 */
    private static FixtureEntities.LargeHealthFixtureHusk noArmorHusk(GameTestHelper helper, int relX, int relY,
            int relZ, double maxHealth) {
        net.minecraft.world.phys.Vec3 centre = net.minecraft.world.phys.Vec3
                .atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(relX, relY, relZ)));
        FixtureEntities.LargeHealthFixtureHusk entity = FixtureEntities.spawn(helper.getLevel(), centre.x, centre.y,
                centre.z, FixtureEntities.LargeHealthFixtureHusk::new).withFixtureMaxHealth(maxHealth);
        entity.getAttribute(Attributes.ARMOR).setBaseValue(0.0D);
        entity.setHealth((float) maxHealth);
        return entity;
    }

    // ------------------------------------------------------------------
    // 服务器把 stage0 从 7 改成 12：八阶段必须整体生效
    // ------------------------------------------------------------------

    /**
     * 阶段增量必须相对<b>物品出厂阶段 0（7.0 / 1.6）</b>计算，而每个阶段的<b>绝对配置值各自生效</b>。
     *
     * <p>把配置的 stage0 攻击从 7 改成 12、stage0 攻速从 1.6 改成 2.0（其余阶段不动）：
     * <ul>
     *   <li>阶段 0 总值 = <b>12</b>（delta = 12 − 7 = +5，加物品基线 6 与玩家 base 1）；</li>
     *   <li>阶段 7 总值 = <b>1500</b>（配置里 stage7 仍是 1500，delta = 1500 − 7 = 1493）；
     *       早先"改 stage0 会让全部阶段平移"的说法是错的，已从这里纠正；</li>
     *   <li>攻速同理：阶段 0 = 2.0、阶段 7 = 2.4。</li>
     * </ul>
     * 测试结束<b>无论成败</b>都恢复原配置快照，避免污染并行运行的其它 GameTest。
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void customStageZeroConfigurationShiftsAllStages(GameTestHelper helper) {
        var original = EBConfig.settings();
        try {
            java.util.List<Double> attack = new java.util.ArrayList<>(original.stageAttackDamage());
            java.util.List<Double> speed = new java.util.ArrayList<>(original.stageAttackSpeed());
            attack.set(0, 12.0D);
            speed.set(0, 2.0D);
            EBConfig.overrideForTesting(original, attack, speed);
            var custom = EBConfig.settings();
            helper.assertTrue(Math.abs(custom.stageAttackDamage(0) - 12.0D) < 1.0E-6D,
                    "sanity: the overridden stage-0 attack must be 12 (got " + custom.stageAttackDamage(0) + ")");
            helper.assertTrue(Math.abs(custom.stageAttackDamage(7) - 1500.0D) < 1.0E-6D,
                    "sanity: the untouched stage-7 attack must stay 1500 (got "
                            + custom.stageAttackDamage(7) + ")");

            // 阶段 0：绝对配置值 12 必须真的生效（delta = 12 − 7 = 5）。
            ServerPlayer zero = staged(helper, 0L, 0L);
            zero.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
            StageAttributes.reconcile(zero);
            helper.assertTrue(Math.abs(attack(zero) - 12.0D) < 0.01D,
                    "a stage-0 attack of 12 must actually apply at stage 0 (value=" + attack(zero) + "); "
                            + describeAttack(zero));
            helper.assertTrue(Math.abs(zero.getAttributeValue(Attributes.ATTACK_SPEED) - 2.0D) < 0.01D,
                    "a stage-0 speed of 2.0 must actually apply at stage 0 (value="
                            + zero.getAttributeValue(Attributes.ATTACK_SPEED) + ")");

            // 终阶：配置未改动 → 仍是 1500 / 2.4（不会被 stage0 的改动平移）。
            ServerPlayer finalStage = staged(helper, STAGE7_POINTS, STAGE7_BOSSES);
            finalStage.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
            GrowthService.syncAfterChange(finalStage);
            helper.assertTrue(Math.abs(attack(finalStage) - 1500.0D) < 0.01D,
                    "the final stage must keep its own configured 1500 (value=" + attack(finalStage)
                            + "); " + describeAttack(finalStage));
            helper.assertTrue(Math.abs(finalStage.getAttributeValue(Attributes.ATTACK_SPEED) - 2.4D) < 0.01D,
                    "the final-stage speed must keep its own configured 2.4 (value="
                            + finalStage.getAttributeValue(Attributes.ATTACK_SPEED) + ")");
        } finally {
            EBConfig.restoreAfterTesting(original);
        }
        helper.assertTrue(Math.abs(EBConfig.settings().stageAttackDamage(0) - 7.0D) < 1.0E-6D,
                "the configuration snapshot must be restored after the test");
        helper.succeed();
    }

    /** 服务器快照必须同时给出"阶段基础攻击"与"当前持剑有效攻击"，并且只在持剑时给有效值。 */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void snapshotExposesBothBasesOnlyWhileHolding(GameTestHelper helper) {
        ServerPlayer player = staged(helper, STAGE7_POINTS, STAGE7_BOSSES);
        equipSword(player);
        player.doTick();
        // 快照自己会先协调属性，所以这里读到的就是本 tick 的真实有效攻击。
        var pristine = com.evolvingblade.network.EBPayloads.snapshotFor(player, false);
        helper.assertTrue(pristine.holdingStarEater(), "the snapshot must report the blade as held");
        helper.assertTrue(Math.abs(pristine.stageBaseAttack() - 1500.0D) < 0.01D,
                "the snapshot must expose the stage base attack (got " + pristine.stageBaseAttack() + ")");
        helper.assertTrue(Double.isFinite(pristine.effectiveHeldAttack())
                        && Math.abs(pristine.effectiveHeldAttack() - 1500.0D) < 0.01D,
                "while holding, the snapshot must expose the server effective attack (got "
                        + pristine.effectiveHeldAttack() + "); modifiers=" + describeAttack(player));
        // 第三方 +5：两行必须分别显示"阶段基础"和"有效"，且第三方增量被完整保留。
        // 期望值<b>不随基线槽位是否存在而变</b>——缺席补偿的唯一目的就是让数值保持一致：
        // pristine = 1500，+5 后 = 1505。
        player.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                new AttributeModifier(THIRD_PARTY, 5.0D, AttributeModifier.Operation.ADD_VALUE));
        var boosted = com.evolvingblade.network.EBPayloads.snapshotFor(player, false);
        helper.assertTrue(Math.abs(boosted.stageBaseAttack() - 1500.0D) < 0.01D,
                "the stage base line must NOT absorb third-party bonuses (got " + boosted.stageBaseAttack() + ")");
        helper.assertTrue(Math.abs(boosted.effectiveHeldAttack() - 1505.0D) < 0.01D,
                "the effective line must include the third-party +5 exactly once"
                        + " (got " + boosted.effectiveHeldAttack() + ", expected=1505); modifiers="
                        + describeAttack(player));
        // 非持剑：必须给出 NaN，UI 才不会借用别的武器加成。
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var empty = com.evolvingblade.network.EBPayloads.snapshotFor(player, false);
        helper.assertFalse(empty.holdingStarEater(), "the snapshot must report 'not holding' after unequip");
        helper.assertTrue(Double.isNaN(empty.effectiveHeldAttack()),
                "not holding must yield NaN (never a borrowed number), got " + empty.effectiveHeldAttack());
        // 有效攻击上限由原版 RangedAttribute 提供（2048），这里记录事实而不是假设。
        helper.assertTrue(Attributes.ATTACK_DAMAGE.value() instanceof net.minecraft.world.entity.ai.attributes.RangedAttribute ranged
                        && ranged.getMaxValue() == 2048.0D,
                "vanilla caps ATTACK_DAMAGE at 2048; ability damage is bounded by it (class="
                        + Attributes.ATTACK_DAMAGE.value().getClass().getName() + ")");
        helper.succeed();
    }

    /** 纯计算层：三维球与有效攻击取值的边界（不需要世界）。 */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sphereAndEffectiveDamageBoundaries(GameTestHelper helper) {
        // 正上方 / 正下方：三轴都参与，必须命中。
        helper.assertTrue(EffectiveDamage.sphereIntersectsAabb(0.0D, 0.0D, 0.0D, 6.0D,
                -0.3D, 5.8D, -0.3D, 0.3D, 6.2D, 0.3D),
                "a target directly ABOVE must be inside a 3D sphere");
        helper.assertTrue(EffectiveDamage.sphereIntersectsAabb(0.0D, 0.0D, 0.0D, 6.0D,
                -0.3D, -6.2D, -0.3D, 0.3D, -5.8D, 0.3D),
                "a target directly BELOW must be inside a 3D sphere");
        // 手算：盒 minX=4.6（中心 5.2、半宽 0.6）→ 最近点 x=4.6 < 6 → 命中。
        helper.assertTrue(EffectiveDamage.sphereIntersectsAabb(0.0D, 0.0D, 0.0D, 6.0D,
                4.6D, -0.5D, -0.5D, 5.8D, 0.5D, 0.5D),
                "a box whose nearest point is inside r must be hit");
        // 手算：最近点 x=5.6 < 6 → 命中；再外移 0.5（最近点 6.1）→ 37.21 > 36 → miss。
        helper.assertTrue(EffectiveDamage.sphereIntersectsAabb(0.0D, 0.0D, 0.0D, 6.0D,
                5.6D, -0.5D, -0.5D, 6.8D, 0.5D, 0.5D),
                "nearest point 5.6 <= 6 must still be inside");
        helper.assertFalse(EffectiveDamage.sphereIntersectsAabb(0.0D, 0.0D, 0.0D, 6.0D,
                6.1D, -0.5D, -0.5D, 7.3D, 0.5D, 0.5D),
                "nearest point 6.1 > 6 must be outside (37.21 > 36)");
        // 两轴都超出：最近点 (6.2,6.2,0) → 距离² = 76.88 > 36 → miss（证明不是 X/Z 圆柱）。
        helper.assertFalse(EffectiveDamage.sphereIntersectsAabb(0.0D, 0.0D, 0.0D, 6.0D,
                6.2D, 6.2D, -0.2D, 6.8D, 6.8D, 0.2D),
                "a box outside on TWO axes must miss (proving it is not an X/Z cylinder)");
        // 边界：最近点 x 恰好 = 6.0（整数坐标，无浮点歧义）→ 36 <= 36 → 命中。
        helper.assertTrue(EffectiveDamage.sphereIntersectsAabb(0.0D, 0.0D, 0.0D, 6.0D,
                -0.5D, -0.5D, -0.5D, 6.0D, 0.5D, 0.5D),
                "the exact boundary (nearest distance == r) must count as a hit");
        // 有效攻击取值：低于/高于阶段基础都必须按有效攻击，不被阶段配置夹住。
        helper.assertTrue(Math.abs(EffectiveDamage.abilityDamage(48.0D, 1.0D, 30.0D, true) - 30.0D) < 1.0E-9D,
                "effective attack below the stage base must be used as-is");
        helper.assertTrue(Math.abs(EffectiveDamage.abilityDamage(48.0D, 1.0D, 1000.0D, true) - 1000.0D) < 1.0E-9D,
                "effective attack ABOVE the stage base must not be clamped to it");
        helper.assertTrue(Math.abs(EffectiveDamage.abilityDamage(48.0D, 1.5D, 30.0D, false) - 72.0D) < 1.0E-9D,
                "inheritance off must ignore the effective attack entirely");
        helper.assertTrue(EffectiveDamage.abilityDamage(48.0D, Double.NaN, 30.0D, true) == 0.0D,
                "a NaN multiplier must degrade to 0 rather than NaN");
        helper.succeed();
    }
}
