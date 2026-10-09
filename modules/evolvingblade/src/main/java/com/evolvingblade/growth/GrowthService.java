package com.evolvingblade.growth;

import java.util.Objects;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.item.StarEaterItem;
import com.evolvingblade.network.EBPayloads;
import com.evolvingblade.registry.EBAttachments;
import com.evolvingblade.registry.EBItems;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 服务器权威的成长服务：本模组<b>唯一</b>写入玩家成长数据的入口。
 *
 * <p>权威性约定：
 * <ul>
 *   <li>所有写入都发生在服务器主线程（由事件/命令驱动），客户端从不提交计数、阶段、伤害或赠剑标记；</li>
 *   <li>计数一律使用饱和加法，畸形输入在边界就被拒绝；</li>
 *   <li>模式选择是原子的：<b>拒绝 null</b>，已选择过的玩家不允许再改（管理员强制除外）；</li>
 *   <li>赠剑只有"真的放进了一个空的主背包槽位"才标记 {@link GiftState#GRANTED}；
 *       背包满（含创造模式）时保持 {@link GiftState#PENDING}，既不掉落也不复制。</li>
 * </ul>
 */
public final class GrowthService {

    private GrowthService() {}

    /** 读取玩家成长数据（不存在时自动创建默认值）。 */
    public static GrowthData data(Player player) {
        return player.getData(EBAttachments.GROWTH);
    }

    /** 覆盖写入玩家成长数据。 */
    public static void set(Player player, GrowthData data) {
        player.setData(EBAttachments.GROWTH, data);
    }

    /** 玩家当前阶段（未选模式恒为 0）。 */
    public static int stage(Player player) {
        return data(player).stage(EBConfig.settings());
    }

    /** 记一次普通敌对击杀。 */
    public static GrowthData creditHostileKill(ServerPlayer player) {
        GrowthData updated = data(player).creditHostileKill(EBConfig.settings().hostilePointWeight());
        set(player, updated);
        // 计数**任何**变化都要立刻同步 + 立刻协调属性：
        // 否则用别的武器拿到的击杀会让仪表盘计数过期，直到下一次升阶才刷新。
        syncAfterChange(player);
        return updated;
    }

    /** 记一次 Boss 击杀（不会同时算普通击杀）。 */
    public static GrowthData creditBossKill(ServerPlayer player) {
        GrowthData updated = data(player).creditBossKill(EBConfig.settings().bossPointWeight());
        set(player, updated);
        syncAfterChange(player);
        return updated;
    }

    /**
     * 单点出口：成长数据（计数 / 模式 / 阶段）变化之后立刻
     * <b>协调持有者属性</b>并下发权威快照。
     *
     * <p>不等周期性 tick：玩家可能在两次击杀之间一直握着剑，属性必须当场正确。
     */
    public static void syncAfterChange(ServerPlayer player) {
        if (player == null) {
            return;
        }
        // 立刻把当前阶段的攻击/攻速差值写到持有者身上（幂等）。
        com.evolvingblade.combat.StageAttributes.reconcile(player);
        sendSnapshot(player);
    }

    /** 下发权威快照（赠剑状态由服务器数据推导，客户端永远不能自己声明）。 */
    public static void sendSnapshot(ServerPlayer player) {
        EBPayloads.sendState(player, data(player).giftState() == GiftState.GRANTED);
    }

    /**
     * 玩家自行选择成长模式。已选择过的玩家会被拒绝（返回 {@code false}），
     * 由命令/网络层负责回显；保留计数与赠剑标记。
     *
     * @throws NullPointerException {@code mode} 为 null（未选择不是"可选值"，而是"尚未选择"这一初始状态）
     */
    public static boolean selectMode(Player player, GrowthMode mode) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(player, "player");
        GrowthData current = data(player);
        if (current.hasMode()) {
            return false;
        }
        set(player, current.withMode(mode));
        return true;
    }

    /**
     * 网络层的"选择模式"入口：在服务器主线程上<b>原子地</b>完成"选择 + 赠剑 + 回发权威状态"。
     *
     * <p>规则：
     * <ul>
     *   <li>已选择过的玩家：拒绝（返回 {@code false}），只回发当前权威状态，绝不改模式、绝不再发一把剑；</li>
     *   <li>背包满（含创造模式）：保持 {@link GiftState#PENDING} 并明确告知玩家"待发放"，
     *       客户端不会收到任何"成功"的假象；</li>
     *   <li>无论成功失败，最后都下发服务器权威快照，界面据此显示真实模式与赠剑状态。</li>
     * </ul>
     *
     * @throws NullPointerException {@code player}/{@code mode} 为 null
     */
    public static boolean chooseMode(ServerPlayer player, GrowthMode mode) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(mode, "mode");
        GrowthData current = data(player);
        if (current.hasMode()) {
            // 锁定：重复/伪造的包只回发真实状态。
            sendSnapshot(player);
            return false;
        }
        set(player, current.withMode(mode));
        boolean delivered = requestGift(player);
        if (!delivered) {
            player.displayClientMessage(Component.translatable("message.evolvingblade.gift_pending"), false);
        } else {
            player.displayClientMessage(Component.translatable("message.evolvingblade.mode_selected",
                    Component.translatable("evolvingblade.mode." + mode.id())), false);
        }
        // 模式是阶段计算的一部分：立刻协调属性 + 下发权威快照。
        syncAfterChange(player);
        return true;
    }

    /**
     * 管理员强制设定模式（权限由命令层校验）。保留计数与赠剑标记。
     *
     * @throws NullPointerException {@code mode} 为 null
     */
    public static void forceMode(Player player, GrowthMode mode) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(player, "player");
        GrowthData current = data(player);
        set(player, current.withMode(mode));
        if (player instanceof ServerPlayer serverPlayer) {
            syncAfterChange(serverPlayer);
        }
    }

    /**
     * 管理员设定原始计数：按当前配置权重重算成长点，保留模式与赠剑标记。
     *
     * @throws IllegalArgumentException 任一计数为负
     * @throws NullPointerException     {@code player} 为 null
     */
    public static GrowthData setCounters(Player player, long hostileKills, long bossKills) {
        // 先校验数值边界，再触碰玩家对象，保证"负数一定被拒绝"这一契约与调用顺序无关。
        StageCalculator.requireNonNegative("hostileKills", hostileKills);
        StageCalculator.requireNonNegative("bossKills", bossKills);
        Objects.requireNonNull(player, "player");
        GrowthSettings settings = EBConfig.settings();
        GrowthData updated = data(player).withCountersRecomputed(hostileKills, bossKills,
                settings.hostilePointWeight(), settings.bossPointWeight());
        set(player, updated);
        if (player instanceof ServerPlayer serverPlayer) {
            // 管理员改动同样立刻协调属性 + 立刻下发（不等 20 tick）。
            syncAfterChange(serverPlayer);
        }
        return updated;
    }

    /**
     * 确认赠剑（由选择界面在二次确认后经由网络层触发）。
     *
     * @return 最终是否已经成功交付
     */
    public static boolean requestGift(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        GrowthData current = data(player);
        if (current.giftState() == GiftState.GRANTED) {
            // 已经发过，永远不再发第二把。
            return true;
        }
        if (!current.hasMode()) {
            // 未选择模式不发剑。
            return false;
        }
        if (current.giftState() != GiftState.PENDING) {
            current = current.withGiftState(GiftState.PENDING);
            set(player, current);
        }
        return tryDeliverGift(player);
    }

    /**
     * 尝试交付待发放的剑。
     *
     * <p><b>刻意不使用 {@code Inventory.add()}</b>：1.21.1 中它在创造模式
     * （{@code hasInfiniteMaterials()}）且背包已满时会清空入参并返回 {@code true}，
     * 实际什么都没放进背包。这里改为：
     * <ol>
     *   <li>只在<b>主背包 36 格</b>里找一个真正为空的槽位（盔甲/副手不参与）；</li>
     *   <li>没有空位 → 保持 {@link GiftState#PENDING}（不掉落、不复制），等下次重试；</li>
     *   <li>写入后<b>复核该槽位确实放着噬星之刃</b>，才写入 {@link GiftState#GRANTED}。</li>
     * </ol>
     *
     * @return 是否已经成功交付
     */
    public static boolean tryDeliverGift(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        GrowthData current = data(player);
        if (current.giftState() == GiftState.GRANTED) {
            return true;
        }
        if (current.giftState() != GiftState.PENDING) {
            return false;
        }
        Inventory inventory = player.getInventory();
        int slot = GiftDelivery.findFreeSlot(Inventory.INVENTORY_SIZE, index -> inventory.getItem(index).isEmpty());
        if (slot < 0) {
            // 背包（含创造模式）真的满了：保持待发放，绝不掉落、绝不复制。
            EvolvingBlade.LOGGER.debug("[EvolvingBlade] 玩家 {} 主背包没有空位，赠剑保持待发放状态",
                    player.getGameProfile().getName());
            return false;
        }
        // 写入前后各复核一次，避免覆盖任何已有物品。
        if (!inventory.getItem(slot).isEmpty()) {
            return false;
        }
        ItemStack stack = new ItemStack(EBItems.starEater());
        inventory.setItem(slot, stack);
        ItemStack placed = inventory.getItem(slot);
        if (placed.isEmpty() || !placed.is(EBItems.starEater())) {
            EvolvingBlade.LOGGER.warn("[EvolvingBlade] 玩家 {} 的赠剑写入槽位 {} 失败，保持待发放",
                    player.getGameProfile().getName(), slot);
            return false;
        }
        set(player, current.withGiftState(GiftState.GRANTED));
        EvolvingBlade.LOGGER.info("[EvolvingBlade] 已向玩家 {} 发放噬星之刃（槽位 {}）",
                player.getGameProfile().getName(), slot);
        return true;
    }

    /** 判断物品栈是否是本剑。 */
    public static boolean holdsStarEater(Player player) {
        return StarEaterItem.isStarEater(player.getMainHandItem());
    }

    /**
     * <b>当前持剑有效攻击</b>（服务器权威）。
     *
     * <p>只有玩家真的主手持着本剑时才返回数值——<b>预览背包里的剑不会借用当前武器的加成</b>，
     * 因为预览时手上拿的是别的东西，那个数字对本剑没有意义。
     * 非持有状态返回 {@link Double#NaN}，UI 必须据此显示"未持剑"而不是显示一个假数字。
     *
     * <p>该值已包含：标准属性 base、物品基线、第三方 flat / 百分比加伤，
     * 并由原版 {@code RangedAttribute} 钳到 2048。实际近战伤害仍会受附魔、目标护甲与事件影响，
     * 所以它<b>不等于</b>"最终伤害"，只等于"属性表上的攻击力"。
     */
    public static double effectiveAttackDamage(Player player) {
        if (player == null || !holdsStarEater(player)) {
            return Double.NaN;
        }
        return player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
    }

    /** 当前持剑有效攻速（服务器权威）；非持剑状态返回 {@link Double#NaN}。 */
    public static double effectiveAttackSpeed(Player player) {
        if (player == null || !holdsStarEater(player)) {
            return Double.NaN;
        }
        return player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
    }
}
