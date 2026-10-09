package com.evolvingblade.combat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.growth.GrowthData;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.growth.GrowthSettings;
import com.evolvingblade.item.StarEaterItem;
import com.evolvingblade.registry.EBDataComponents;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * 当前持有者的阶段化属性<b>协调器</b>（服务器权威）。
 *
 * <h2>为什么需要它</h2>
 * 原版只在物品栈组件变化或装备变化时重算物品带来的属性修饰符；
 * <b>玩家阶段变化（升级）时原版不会重新评估同一把剑</b>。
 * 因此本模组把"阶段相对出厂基线的增量"做成施加在<b>玩家</b>身上的独立 transient 修饰符。
 *
 * <h2>本模组只写自己的 id</h2>
 * <ul>
 *   <li>{@link #DAMAGE_ID} / {@link #SPEED_ID}：<b>阶段增量</b>
 *       {@code delta(stage) = 配置阶段值 − 出厂阶段0值（7.0 / 1.6）}。
 *       每个阶段的绝对配置值各自生效：只改 stage0 就只影响 stage0。</li>
 *   <li>{@link #BASELINE_DAMAGE_ID} / {@link #BASELINE_SPEED_ID}：<b>保留槽位补偿</b>。
 *       原版装备 tick 还没把 <b>当前物品</b> 的基线挂上时（首次拿到本剑，
 *       或刚换成另一把武器、槽位里还留着上一把的值），这一份基线还没进总值，
 *       本模组用自己的 id 补上"当前物品声明值 − 观测值"这份缺口。
 *       与阶段增量分开记账，所以阶段 0 可以完全没有阶段增量修饰符。</li>
 * </ul>
 * 本模组<b>绝不写入</b> {@code minecraft:base_attack_damage} / {@code base_attack_speed}
 * 这两个原版保留槽位，也绝不动任何第三方 id；差值公式<b>不读</b>
 * {@code AttributeInstance.getValue()}、<b>不减</b> {@code getBaseValue()}、
 * <b>不减</b>任何第三方 {@code ADD_VALUE}。因此别人的属性 base 改动、
 * 对保留 id 的合法改动、flat / 乘法加伤都会被完整保留。
 *
 * <h2>补偿必须幂等</h2>
 * 重复调用 {@link #reconcile} 不能把补偿"用掉"。规则只有一条：
 * <b>只有当观测到的保留槽位确实等于当前物品声明的基线值时，才认为原版已经结算并撤掉补偿。</b>
 * 否则（一直缺席 / 一直是上一把武器的旧值）就维持同一份补偿。
 * 一旦"本物品已经见过结算"，之后再出现的不同数值就视为第三方对保留 id 的合法改动，
 * 不再补偿、也不抵减。
 */
public final class StageAttributes {

    /** 阶段增量修饰符 id（卸下时移除）。 */
    public static final ResourceLocation DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(EvolvingBlade.MODID, "stage_attack_damage");
    public static final ResourceLocation SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(EvolvingBlade.MODID, "stage_attack_speed");

    /** 保留槽位补偿修饰符 id（与阶段增量分开，保证阶段 0 没有阶段增量修饰符）。 */
    public static final ResourceLocation BASELINE_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(EvolvingBlade.MODID, "baseline_attack_damage");
    public static final ResourceLocation BASELINE_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(EvolvingBlade.MODID, "baseline_attack_speed");

    /** 单个映射/集合的硬上限：远大于任何真实并发玩家数。 */
    private static final int MAX_TRACKED = 65_536;

    /**
     * 每个玩家、每个属性各一份的补偿状态（<b>不</b>依赖任何跨 tick 的持久身份记录）。
     *
     * @param identity     当前物品身份指纹（用于在换武器时重置状态）
     * @param sawDeclared  是否已经观察到"保留槽位 == 当前物品声明值"（原版结算完成）
     * @param compensating 当前是否正在施加保留槽位补偿
     */
    private static final class CompState {
        String identity = "";
        boolean sawDeclared;
        boolean compensating;
    }

    private static final Map<UUID, CompState> DAMAGE_STATE = new ConcurrentHashMap<>();
    private static final Map<UUID, CompState> SPEED_STATE = new ConcurrentHashMap<>();

    private StageAttributes() {}

    /**
     * 协调持有者的属性、把阶段快照写进手上的剑、并返回当前阶段。
     *
     * <p>幂等：重复调用不会累加、也不会把补偿"用掉"。只有观测到保留槽位确实等于
     * 当前物品声明值时才撤掉补偿；一直缺席或一直是旧武器的值就维持同一份补偿。
     */
    public static int reconcile(Player player) {
        if (player == null) {
            return 0;
        }
        ItemStack main = player.getMainHandItem();
        if (!StarEaterItem.isStarEater(main)) {
            // 非本剑：只移除本模组的修饰符与状态，绝不触碰原版基线与别人的修饰符。
            clear(player);
            return 0;
        }
        GrowthSettings settings = EBConfig.settings();
        int stage = GrowthService.stage(player);
        applyStageDelta(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, BASELINE_DAMAGE_ID, DAMAGE_STATE,
                main, settings.stageAttackDamage(stage), StarEaterItem.FACTORY_STAGE_ZERO_ATTACK_DAMAGE,
                StarEaterItem.BASE_DAMAGE_MODIFIER, true);
        applyStageDelta(player, Attributes.ATTACK_SPEED, SPEED_ID, BASELINE_SPEED_ID, SPEED_STATE,
                main, settings.stageAttackSpeed(stage), StarEaterItem.FACTORY_STAGE_ZERO_ATTACK_SPEED,
                StarEaterItem.BASE_SPEED_MODIFIER, false);
        // 仅用于模型/tooltip 的阶段快照：变化时才写入，避免无谓的网络同步。
        if (main.getOrDefault(EBDataComponents.STAGE.get(), -1) != stage) {
            main.set(EBDataComponents.STAGE.get(), stage);
        }
        return stage;
    }

    /** 移除本模组在玩家身上施加的全部修饰符与全部补偿状态（卸下/换手/非本剑时调用）。 */
    public static void clear(Player player) {
        if (player == null) {
            return;
        }
        removeModifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
        removeModifier(player, Attributes.ATTACK_DAMAGE, BASELINE_DAMAGE_ID);
        removeModifier(player, Attributes.ATTACK_SPEED, SPEED_ID);
        removeModifier(player, Attributes.ATTACK_SPEED, BASELINE_SPEED_ID);
        UUID id = player.getUUID();
        DAMAGE_STATE.remove(id);
        SPEED_STATE.remove(id);
    }

    /** 服务器停止/玩家登出时清理所有状态，保证有界。 */
    public static void clearInjectionRegistry() {
        DAMAGE_STATE.clear();
        SPEED_STATE.clear();
    }

    /** 本玩家自己是否正在接受保留槽位补偿（诊断/测试用；不要用全局计数代替）。 */
    public static boolean hasPendingCompensation(Player player, boolean attack) {
        if (player == null) {
            return false;
        }
        CompState state = (attack ? DAMAGE_STATE : SPEED_STATE).get(player.getUUID());
        return state != null && state.compensating;
    }

    /** 本玩家最近一次观测到的保留槽位值。没有观测值时返回 {@link Double#NaN}。 */
    public static double observedReservedSlot(Player player, boolean attack) {
        if (player == null) {
            return Double.NaN;
        }
        AttributeInstance instance = player.getAttribute(attack ? Attributes.ATTACK_DAMAGE : Attributes.ATTACK_SPEED);
        ResourceLocation id = attack ? Item.BASE_ATTACK_DAMAGE_ID : Item.BASE_ATTACK_SPEED_ID;
        AttributeModifier modifier = instance == null ? null : instance.getModifier(id);
        return modifier == null ? Double.NaN : modifier.amount();
    }

    /** 跟踪中的玩家数（<b>只</b>用于有界性诊断；不构成任何玩家残留的证据）。 */
    public static int trackedPlayers() {
        return Math.max(DAMAGE_STATE.size(), SPEED_STATE.size());
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /**
     * 施加阶段增量 + 保留槽位补偿。
     *
     * @param stageTarget       配置里当前阶段的绝对目标值
     * @param factoryStageZero  出厂阶段 0 的绝对目标值（7.0 / 1.6），阶段增量的基准
     * @param staticBaseline    物品静态基线修饰值（+6.0 / −2.4），读不到组件时的回落值
     * @param attack            true=攻击力，false=攻速
     */
    private static void applyStageDelta(Player player,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation stageId, ResourceLocation baselineId, Map<UUID, CompState> states,
            ItemStack currentItem, double stageTarget, double factoryStageZero, double staticBaseline,
            boolean attack) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        UUID uuid = player.getUUID();
        if (states.size() > MAX_TRACKED) {
            states.clear();
        }
        CompState state = states.computeIfAbsent(uuid, key -> new CompState());

        String identity = itemIdentity(currentItem, attack);
        if (!identity.equals(state.identity)) {
            // 换了一把（不同物品 / 不同声明基线）：状态从头开始，避免把上一把的状态套到新剑上。
            state.identity = identity;
            state.sawDeclared = false;
            state.compensating = false;
        }

        double declared = declaredBaseline(currentItem, attack);
        AttributeModifier reserved = instance.getModifier(
                attack ? Item.BASE_ATTACK_DAMAGE_ID : Item.BASE_ATTACK_SPEED_ID);
        double observed = reserved == null ? Double.NaN : reserved.amount();

        if (Double.isFinite(observed) && Math.abs(observed - declared) < 1.0E-6D) {
            // 原版已经用当前物品的声明值结算了这个槽位。
            state.sawDeclared = true;
            state.compensating = false;
        } else if (state.sawDeclared) {
            // 本物品已经结算过；后来出现的不同数值只可能是第三方对保留 id 的合法改动
            // 或本玩家之外的写入 —— 一律保留，绝不抵减。
            state.compensating = false;
        } else {
            // 槽位缺席，或仍是上一把武器的旧值：这一份基线还没进总值，补足缺口。
            // 重复 reconcile 会一直走到这里，因此补偿是<b>幂等</b>的。
            state.compensating = true;
        }

        double stageDelta = stageTarget - factoryStageZero;
        double compensation = state.compensating
                ? declared - (Double.isFinite(observed) ? observed : 0.0D)
                : 0.0D;

        applyOrRemove(instance, stageId, stageDelta);
        applyOrRemove(instance, baselineId, compensation);
    }

    /** 写入（或更新）一个 ADD_VALUE 修饰符；数值为 0/非法时移除它。 */
    private static void applyOrRemove(AttributeInstance instance, ResourceLocation id, double amount) {
        if (!Double.isFinite(amount) || Math.abs(amount) < 1.0E-6D) {
            instance.removeModifier(id);
            return;
        }
        instance.addOrUpdateTransientModifier(
                new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    /**
     * 当前物品栈声明的保留槽位修饰值。
     *
     * <p>直接读物品自己的 {@code ATTRIBUTE_MODIFIERS} 组件——原版装备 tick 挂到玩家身上的
     * 就是这个值。读不到（组件被清掉 / 栈为空）时回落到物品的静态常量。
     */
    private static double declaredBaseline(ItemStack stack, boolean attack) {
        ResourceLocation wanted = attack ? Item.BASE_ATTACK_DAMAGE_ID : Item.BASE_ATTACK_SPEED_ID;
        try {
            ItemAttributeModifiers modifiers = stack.getOrDefault(
                    net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                    ItemAttributeModifiers.EMPTY);
            for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
                if (entry.modifier().id().equals(wanted)
                        && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                    return entry.modifier().amount();
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            // 读不到就回落静态常量：宁可少补偿，也不猜一个更大的数字。
        }
        return attack ? StarEaterItem.BASE_DAMAGE_MODIFIER : StarEaterItem.BASE_SPEED_MODIFIER;
    }

    /** 物品身份指纹：注册 id + 该属性声明的基线值（换武器时用它重置状态）。 */
    private static String itemIdentity(ItemStack stack, boolean attack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                + "|" + declaredBaseline(stack, attack);
    }

    private static void removeModifier(Player player,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    /** 供 UI/tooltip 使用：当前持有者的阶段（不修改任何状态）。 */
    public static int currentStage(Player player) {
        GrowthData data = GrowthService.data(player);
        return data.stage(EBConfig.settings());
    }
}
