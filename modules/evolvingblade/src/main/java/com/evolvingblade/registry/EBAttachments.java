package com.evolvingblade.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.combat.BossRateCodec;
import com.evolvingblade.combat.SwordHit;
import com.evolvingblade.growth.GrowthData;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Data Attachment 注册。
 *
 * <p>两类用途：
 * <ol>
 *   <li><b>持久化</b>：{@code growth} 保存成长数据与技能冷却（死亡/末地返回都会复制）；</li>
 *   <li><b>临时</b>（不序列化，随实体对象消失）：击杀来源标记、已入账标记、每玩家+目标速率限制表。
 *       非持久 attachment 不会写进存档（{@code AttachmentHolder#saveAttachments} 只处理带 serializer 的类型），
 *       因此不会泄漏也不会跨实体串味。</li>
 * </ol>
 */
public final class EBAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, EvolvingBlade.MODID);

    /** {@code evolvingblade:growth} —— 每玩家每存档独立的成长数据与技能冷却。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GrowthData>> GROWTH =
            ATTACHMENTS.register("growth", () -> AttachmentType.builder(() -> GrowthData.EMPTY)
                    .serialize(GrowthData.CODEC)
                    .copyOnDeath()
                    .build());

    /** {@code evolvingblade:kill_credited} —— "这条命已经入账"的永久标记（写在实体自身）。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> KILL_CREDITED =
            ATTACHMENTS.register("kill_credited", () -> AttachmentType.builder(() -> Boolean.FALSE).build());

    /**
     * {@code evolvingblade:sword_hit} —— 本剑最后一次有效命中该受害者的来源事实。
     * 用于"击杀回血"的来源判定，保证换剑不丢失/不冒充来源。
     *
     * <p>用显式的"空值哨兵"而不是 {@code null}：attachment 的默认值契约要求非 null，
     * {@link SwordHit#NONE} 表示"没有有效来源"。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SwordHit>> SWORD_HIT =
            ATTACHMENTS.register("sword_hit", () -> AttachmentType.builder(() -> SwordHit.NONE).build());

    /**
     * {@code evolvingblade:boss_rate} —— 每玩家+目标的"斩 Boss 加成"速率限制表。
     *
     * <p>已<b>序列化并 copyOnDeath</b>：每 20 tick 一次的每目标限速无法通过换维度、
     * 重生或重连绕过；编解码由 {@link BossRateCodec} 负责，遇到损坏数据不会崩服，
     * 且加载后条目数严格不超过上限（见 {@code BossRateCodec} 的保守裁剪规则）。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Map<UUID, Long>>> BOSS_RATE =
            ATTACHMENTS.register("boss_rate", () -> AttachmentType
                    .<Map<UUID, Long>>builder((java.util.function.Supplier<Map<UUID, Long>>) HashMap::new)
                    .serialize(BossRateCodec.CODEC)
                    .copyOnDeath()
                    .build());

    private EBAttachments() {}
}
