package com.evolvingblade.item;

import java.util.List;

import com.evolvingblade.growth.GrowthSettings;
import com.evolvingblade.registry.EBDataComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;

/**
 * 噬星之刃。单手剑，<b>不可破坏</b>且<b>抗火</b>。
 *
 * <p>物品自带的属性组件固定为<b>阶段 0</b> 的基线（总攻击 7 / 总攻速 1.6，即修饰值 +6.0 / −2.4）；
 * 阶段差异由 {@code StageAttributes} 施加在<b>持有者玩家</b>身上的 transient 修饰符提供。
 * 因此：
 * <ul>
 *   <li>物品上不存任何权威成长数据；复制/新造的剑由持有者阶段决定强度；</li>
 *   <li>{@code evolvingblade:stage} 组件只驱动模型与 tooltip；</li>
 *   <li>阶段数值已包含玩家自身基础攻击 1.0，修饰值 = 总值 − 1.0，绝不重复 +1。</li>
 * </ul>
 */
public class StarEaterItem extends SwordItem {

    /** 物品 ID 路径。 */
    public static final String ITEM_ID = "star_eater";

    /** 阶段 0 物品自带攻击力修饰值（总攻击 7 − 玩家基础 1.0）。 */
    public static final double BASE_DAMAGE_MODIFIER = GrowthSettings.defaults().stageDamageModifier(0);
    /** 阶段 0 物品自带攻速修饰值（总攻速 1.6 − 属性基础 4.0）。 */
    public static final double BASE_SPEED_MODIFIER = GrowthSettings.defaults().stageAttackSpeedModifier(0);

    /**
     * <b>出厂</b>阶段 0 的总攻击 / 总攻速（7.0 / 1.6），与服务器配置无关。
     *
     * <p>为什么需要这两个常量：阶段差值必须相对<b>物品静态出厂基线</b>计算，
     * 而不是相对配置里的 {@code stageAttackDamage(0)}。否则把配置的 stage0 从 7 改成 12 时，
     * 差值仍是 {@code stage(0) − 7 = 5}，实际总值却停在 7——那一档配置等于没生效。
     * 以出厂基线的差值 {@code stage(stage) − 7} 则会让八阶段整体平移（含阶段 0）。
     */
    public static final double FACTORY_STAGE_ZERO_ATTACK_DAMAGE =
            GrowthSettings.defaults().stageAttackDamage(0);
    /** 出厂阶段 0 总攻速（1.6）。 */
    public static final double FACTORY_STAGE_ZERO_ATTACK_SPEED =
            GrowthSettings.defaults().stageAttackSpeed(0);

    public StarEaterItem(Item.Properties properties) {
        super(Tiers.NETHERITE, properties);
    }

    /** 出厂属性（阶段 0 基线）。 */
    public static ItemAttributeModifiers defaultAttributes() {
        return attributesFor(0, GrowthSettings.defaults());
    }

    /** 按给定阶段构造"物品基线"属性（阶段差异由持有者的修饰符补足）。 */
    public static ItemAttributeModifiers attributesFor(int stage, GrowthSettings settings) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, settings.stageDamageModifier(stage),
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, settings.stageAttackSpeedModifier(stage),
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build()
                // 关键：**不在 tooltip 里显示这两个固定基线行**。
                // 基线只是"阶段 0 地板"，真正强度由持有者阶段决定，显示它会让高阶持有者看到过小的数字。
                // 权威数值改由客户端 tooltip 事件用服务器快照渲染（见 client/EBClient）。
                .withTooltip(false);
    }

    public static Item.Properties createProperties() {
        return new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant()
                .component(DataComponents.UNBREAKABLE, new Unbreakable(true))
                .component(DataComponents.ATTRIBUTE_MODIFIERS, defaultAttributes())
                .component(EBDataComponents.STAGE.get(), 0);
    }

    public static boolean isStarEater(ItemStack stack) {
        return stack.getItem() instanceof StarEaterItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        // 通用端只写"与持有者无关"的事实；阶段/伤害/攻速由客户端 tooltip 事件按服务器快照追加，
        // 因此**不读**物品上的 stage 组件（复制/伪造组件不会影响显示，更不影响强度）。
        tooltipComponents.add(Component.translatable("tooltip.evolvingblade.star_eater.unbreakable"));
        tooltipComponents.add(Component.translatable("tooltip.evolvingblade.star_eater.fire_resistant"));
    }
}
