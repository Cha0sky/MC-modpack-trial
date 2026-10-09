# DESIGN-API-NOTES —— NeoForge 1.21.1 API 取证记录

> 本文件是 `docs/DESIGN.md` 的证据附录。**所有签名均来自实际 grep / javap，不是记忆。**
> `DESIGN.md` 中的每条 API 结论都能在这里找到对应证据行。

## 0. 取证源与路径简写

| 简写 | 绝对路径 | 说明 |
|---|---|---|
| `MC` | `C:\Users\俞皓天\Desktop\InfiniteCore-Mod\build\neoForm\neoFormJoined1.21.1-20240808.144430\steps\unzipSources\unpacked\` | 已解压的 **Minecraft 1.21.1 + NeoForge 补丁** 联合源码（mod 实际编译目标） |
| `NF` | `.research\neoforge-sources\`（工程内相对路径） | 从 `%USERPROFILE%\.gradle\caches\modules-2\files-2.1\net.neoforged\neoforge\21.1.248\f0378c9b210dfc258170b6412a85fdffe4aea7fe\neoforge-21.1.248-sources.jar` 解压出的 NeoForge 21.1.248 源码（953 个 .java） |
| `FML` | `%USERPROFILE%\.gradle\caches\modules-2\files-2.1\net.neoforged.fancymodloader\loader\4.0.43\fb10b7bf2f568a9676ad8b426b19c23badbbd98a\loader-4.0.43.jar` | FancyModLoader（含 `net.neoforged.fml.*`，**无源码 jar，用 javap 取证**） |
| `BUS` | `%USERPROFILE%\.gradle\caches\modules-2\files-2.1\net.neoforged\bus\8.0.5\5b2d33285ab5d1554e9798ad98c40d6ea3868bd5\bus-8.0.5.jar` | 事件总线（**无源码 jar，用 javap 取证**） |
| `JDK` | `%APPDATA%\.minecraft\runtime\java-runtime-delta\bin\javap.exe` | JDK 21.0.7 |

> **关于 `.research\neoforge-sources`**：这是 architect 为取证解压的**研究缓存**，不是 mod 源码、不在交付物范围内。
> 建议 code 阶段直接复用；`.gitignore` 加一行 `.research/`，或首次提交前删除。

---

## 1. Attribute 体系

### 1.1 `Attribute` / `RangedAttribute` / `Sentiment`

**证据**：`MC\net\minecraft\world\entity\ai\attributes\Attribute.java`

```java
public class Attribute implements net.neoforged.neoforge.common.extensions.IAttributeExtension {
    public static final Codec<Holder<Attribute>> CODEC = BuiltInRegistries.ATTRIBUTE.holderByNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Attribute>> STREAM_CODEC =
        ByteBufCodecs.holderRegistry(Registries.ATTRIBUTE);

    private final double defaultValue;
    private boolean syncable;
    private final String descriptionId;
    private Attribute.Sentiment sentiment = Attribute.Sentiment.POSITIVE;   // 默认 POSITIVE

    protected Attribute(String descriptionId, double defaultValue)          // <- protected
    public double getDefaultValue()
    public boolean isClientSyncable()
    public Attribute setSyncable(boolean watch)                            // <- 链式，返回 Attribute
    public Attribute setSentiment(Attribute.Sentiment sentiment)           // <- 链式，返回 Attribute
    public double sanitizeValue(double value)                              // 基类：原样返回
    public String getDescriptionId()

    public static enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE }           // L95-107
}
```

**证据**：`MC\net\minecraft\world\entity\ai\attributes\RangedAttribute.java`

```java
public class RangedAttribute extends Attribute {
    public RangedAttribute(String descriptionId, double defaultValue, double min, double max)  // L18
    public double getMinValue()
    public double getMaxValue()
    @Override public double sanitizeValue(double value) {
        return Double.isNaN(value) ? this.minValue : Mth.clamp(value, this.minValue, this.maxValue);
    }
}
```

**关键结论**：
- `RangedAttribute` 的 `min/max` 是 **final 字段、构造期确定**，运行期无法修改 ⇒ 原版 Attribute 的数值天花板是**硬天花板**（见 1.3 表）。
- `sanitizeValue` 在 `AttributeInstance.calculateValue()` 最后一步被调用 ⇒ 所有 modifier 叠加完才钳制。

### 1.2 `AttributeModifier.Operation` 只有三个值

**证据**：`MC\net\minecraft\world\entity\ai\attributes\AttributeModifier.java` L22、L63-89

```java
public record AttributeModifier(ResourceLocation id, double amount, AttributeModifier.Operation operation)

public static enum Operation implements StringRepresentable {
    ADD_VALUE("add_value", 0),
    ADD_MULTIPLIED_BASE("add_multiplied_base", 1),
    ADD_MULTIPLIED_TOTAL("add_multiplied_total", 2);
}
```

**1.20.x 的 `ADDITION` / `MULTIPLY_BASE` / `MULTIPLY_TOTAL` 在 1.21.x 已改名** ⇒ DESIGN 必须用新名字。

**证据**：`MC\net\minecraft\world\entity\ai\attributes\AttributeInstance.java` L145-163

```java
private double calculateValue() {
    double d0 = this.getBaseValue();
    for (AttributeModifier m : getModifiersOrEmpty(Operation.ADD_VALUE))            d0 += m.amount();
    double d1 = d0;
    for (AttributeModifier m : getModifiersOrEmpty(Operation.ADD_MULTIPLIED_BASE))  d1 += d0 * m.amount();
    for (AttributeModifier m : getModifiersOrEmpty(Operation.ADD_MULTIPLIED_TOTAL)) d1 *= 1.0 + m.amount();
    return this.attribute.value().sanitizeValue(d1);
}
```

**推导出的叠加语义（整个设计的地基）**：

```
value = sanitize( (base + SUM ADD_VALUE) * (1 + SUM ADD_MULTIPLIED_BASE) * PROD (1 + ADD_MULTIPLIED_TOTAL) )
```

- `ADD_MULTIPLIED_BASE` 的多个来源先**求和**再一次性乘 ⇒ **不会互相复利**。
- `ADD_MULTIPLIED_TOTAL` 的多个来源**连乘** ⇒ 指数膨胀。**本 mod 全面禁用。**
- 所以：**flat 线一律 `ADD_VALUE`，percent 线一律 `ADD_MULTIPLIED_BASE`。**

### 1.3 原版 `Attributes` 常量表（含硬天花板）

**证据**：`MC\net\minecraft\world\entity\ai\attributes\Attributes.java`

| 常量 | 注册名 | 默认值 | min | **max（硬天花板）** | syncable | 行 |
|---|---|---|---|---|---|---|
| `Attributes.ARMOR` | `generic.armor` | 0.0 | 0.0 | **30.0** | 是 | L15-17 |
| `Attributes.ARMOR_TOUGHNESS` | `generic.armor_toughness` | 0.0 | 0.0 | **20.0** | 是 | L21-23 |
| `Attributes.ATTACK_DAMAGE` | `generic.attack_damage` | 2.0 | 0.0 | **2048.0** | 否 | L27-29 |
| `Attributes.KNOCKBACK_RESISTANCE` | `generic.knockback_resistance` | 0.0 | 0.0 | **1.0** | 否 | L86-89 |
| `Attributes.MAX_HEALTH` | `generic.max_health` | 20.0 | 1.0 | **1024.0** | 是 | L102-104 |
| `Attributes.MOVEMENT_SPEED` | `generic.movement_speed` | 0.7 | 0.0 | 1024.0 | 是 | L114-117 |

**NeoForge 的两处魔改（必须知道）**：

```java
// Attributes.java L86-89
public static final Holder<Attribute> KNOCKBACK_RESISTANCE = register(
    "generic.knockback_resistance",
    new net.neoforged.neoforge.common.PercentageAttribute("attribute.name.generic.knockback_resistance", 0.0, 0.0, 1.0));

// Attributes.java L114-117  —— scaleFactor = 1000，因为移动速度有 0.001 的刻度
public static final Holder<Attribute> MOVEMENT_SPEED = register(
    "generic.movement_speed",
    new net.neoforged.neoforge.common.PercentageAttribute("attribute.name.generic.movement_speed", 0.7, 0.0, 1024.0, 1000)
        .setSyncable(true));
```

**玩家实体的 base 值 != Attribute 默认值**（证据：`MC\net\minecraft\world\entity\player\Player.java` L228-231）：

```java
public static AttributeSupplier.Builder createAttributes() {
    return LivingEntity.createLivingAttributes()
        .add(Attributes.ATTACK_DAMAGE, 1.0)      // <- 不是 2.0
        .add(Attributes.MOVEMENT_SPEED, 0.1F)    // <- 不是 0.7
        ...
}
```

⇒ **算百分比时必须用玩家实际 base（攻击 1.0 / 移速 0.1 / 生命 20.0 / 护甲 0.0），不能抄 Attribute 默认值。**

### 1.4 `PercentageAttribute`（NeoForge 附加类）

**证据**：`MC\net\neoforged\neoforge\common\PercentageAttribute.java`

```java
public class PercentageAttribute extends RangedAttribute {
    protected final double scaleFactor;
    public PercentageAttribute(String pDescriptionId, double pDefaultValue, double pMin, double pMax, double scaleFactor)
    public PercentageAttribute(String pDescriptionId, double pDefaultValue, double pMin, double pMax)   // scaleFactor = 100
    @Override public MutableComponent toValueComponent(Operation op, double value, TooltipFlag flag)
}
```

用于「值本身就是百分比」的自定义属性（如暴击率），tooltip 显示 `+12.5%` 而不是 `+0.125`。

### 1.5 Attribute 注册：`DeferredRegister`

**证据**：`MC\net\neoforged\neoforge\registries\DeferredRegister.java`

```java
public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key, String namespace)   // L113
public static <B> DeferredRegister<B> create(ResourceLocation registryName, String modid)                // L129
public <I extends T> DeferredHolder<T, I> register(final String name, final Supplier<? extends I> sup)   // L214
public void register(IEventBus bus)                                                                      // L315
```

**NeoForge 自己的用法**（证据：`MC\net\neoforged\neoforge\common\NeoForgeMod.java` L188、L201）：

```java
private static final DeferredRegister<Attribute> ATTRIBUTES =
    DeferredRegister.create(Registries.ATTRIBUTE, NeoForgeVersion.MOD_ID);            // L188
public static final Holder<Attribute> NAMETAG_DISTANCE = ATTRIBUTES.register(
    "nametag_distance",
    () -> new RangedAttribute("neoforge.name_tag_distance", 64.0D, 0.0D, 64.0)
              .setSyncable(true).setSentiment(Sentiment.NEUTRAL));                     // L201
```

⇒ `DeferredHolder<Attribute, Attribute>` 可直接赋给 `Holder<Attribute>` 字段。

### 1.6 `AttributeInstance` 增删改 API

**证据**：`MC\net\minecraft\world\entity\ai\attributes\AttributeInstance.java`

```java
public Holder<Attribute> getAttribute()                                     // L43
public double getBaseValue() / public void setBaseValue(double)             // L47 / L51
public Set<AttributeModifier> getModifiers()                                // L63
@Nullable public AttributeModifier getModifier(ResourceLocation id)         // L68
public boolean hasModifier(ResourceLocation id)                             // L72
public void addOrUpdateTransientModifier(AttributeModifier modifier)        // L86   ** 幂等 **
public void addTransientModifier(AttributeModifier modifier)                // L94   ** 重复会抛异常 **
public void addOrReplacePermanentModifier(AttributeModifier modifier)       // L98   ** 会写盘 **
public void addPermanentModifier(AttributeModifier modifier)                // L104  ** 会写盘 **
public boolean removeModifier(ResourceLocation id)                          // L118
public void removeModifiers()                                               // L130
public double getValue()                                                    // L136
```

```java
// L76-84  重复 id 会抛 "Modifier is already applied on this attribute!"
private void addModifier(AttributeModifier modifier) {
    AttributeModifier prev = this.modifierById.putIfAbsent(modifier.id(), modifier);
    if (prev != null) throw new IllegalArgumentException("Modifier is already applied on this attribute!");
    ...
}
// L86-92  addOrUpdateTransientModifier 用 put 覆盖，不抛异常 => 幂等
public void addOrUpdateTransientModifier(AttributeModifier modifier) {
    AttributeModifier prev = this.modifierById.put(modifier.id(), modifier);
    if (modifier != prev) { this.getModifiers(modifier.operation()).put(modifier.id(), modifier); this.setDirty(); }
}
```

⇒ **防重复叠加的唯一正确做法：固定 ResourceLocation + `addOrUpdateTransientModifier`。用 `addTransientModifier` 第二次调用会崩服。**
⇒ **不用 `addPermanentModifier`**：`AttributeInstance.save()`（L181-199）只序列化 `permanentModifiers`，会与 Attachment 双写冲突。

**取 AttributeInstance 的入口**（证据：`MC\net\minecraft\world\entity\LivingEntity.java`）：

```java
public AttributeInstance getAttribute(Holder<Attribute> attribute)     // L2006  @Nullable
public double getAttributeValue(Holder<Attribute> attribute)          // L2010
public void heal(float healAmount)                                    // L1117
public float getHealth() / public void setHealth(float)               // L1126 / L1130
public void hurt(DamageSource, float)                                 // 内部 fire LivingIncomingDamageEvent
```

### 1.7 给原版实体补 Attribute：`EntityAttributeModificationEvent`

**证据**：`MC\net\neoforged\neoforge\event\entity\EntityAttributeModificationEvent.java`

```java
public class EntityAttributeModificationEvent extends Event implements IModBusEvent {   // ** mod 总线 **
    public void add(EntityType<? extends LivingEntity> entityType, Holder<Attribute> attribute, double value)  // L43
    public void add(EntityType<? extends LivingEntity> entityType, Holder<Attribute> attribute)                // L49
    public boolean has(EntityType<? extends LivingEntity> entityType, Holder<Attribute> attribute)             // L53
    public List<EntityType<? extends LivingEntity>> getTypes()                                                 // L58
}
```

**必须在 mod 总线；给 `EntityType.PLAYER` 加之前先 `has` 判重。**

---

## 2. 事件（NeoForge 1.21.1 准确类名）

### 2.1 生命周期类

**证据**：`MC\net\neoforged\neoforge\event\entity\player\PlayerEvent.java`

| 类 | 行 | 关键方法 |
|---|---|---|
| `PlayerEvent.Clone` | L231-255 | `Player getOriginal()`、`boolean isWasDeath()`。javadoc：「caused by death, or by traveling from the End to the overworld」 |
| `PlayerEvent.PlayerLoggedInEvent` | L422 | — |
| `PlayerEvent.PlayerLoggedOutEvent` | L428 | — |
| `PlayerEvent.PlayerRespawnEvent` | L434-450 | `boolean isEndConquered()` |
| `PlayerEvent.PlayerChangedDimensionEvent` | L452-469 | `ResourceKey<Level> getFrom() / getTo()` |
| `PlayerEvent.ItemCraftedEvent` | L390-407 | `ItemStack getCrafting()`、`Container getInventory()` |
| `PlayerEvent.ItemSmeltedEvent` | L409-420 | `ItemStack getSmelting()` |

**证据**：`MC\net\neoforged\neoforge\event\tick\PlayerTickEvent.java`

```java
public abstract class PlayerTickEvent extends PlayerEvent {
    public static class Pre  extends PlayerTickEvent { public Pre(Player player) }
    public static class Post extends PlayerTickEvent { public Post(Player player) }   // L52
}
```

javadoc 明确警告（L37 / L50）：**server 和 client 都会 fire，必须自己判 `player.level().isClientSide()`。**
（1.20.x 的 `TickEvent.PlayerTickEvent` 在 1.21.x 已拆成 `PlayerTickEvent.Pre/Post`。）

### 2.2 战斗 / 伤害 / XP 类

| 类 | 文件 | 关键结论 |
|---|---|---|
| `LivingIncomingDamageEvent` | `...\event\entity\living\LivingIncomingDamageEvent.java` | **1.20.x 的 `LivingHurtEvent` 改名为此**。`getContainer()` `getSource()` `getAmount()` `getOriginalAmount()` `setAmount(float)`；`addReductionModifier(DamageContainer.Reduction, IReductionFunction)`；`setInvulnerabilityTicks(int)`；`implements ICancellableEvent` |
| `LivingDamageEvent.Pre` | `...\LivingDamageEvent.java` L48-84 | `getContainer()` `getSource()` `getNewDamage()` `getOriginalDamage()` `setNewDamage(float)`。javadoc：**armor 与 potion 已结算，absorption 尚未结算** |
| `LivingDamageEvent.Post` | 同上 L97-156 | `getOriginalDamage()` `getSource()` `getNewDamage()` `getBlockedDamage()` `getShieldDamage()` `getPostAttackInvulnerabilityTicks()` `getReduction(Reduction)`。**不可改数值** |
| `LivingHealEvent` | `...\LivingHealEvent.java` | `getAmount()` `setAmount(float)`，`ICancellableEvent` |
| `LivingKnockBackEvent` | `...\LivingKnockBackEvent.java` | `getRatioX()` `setStrength(float)` `setRatioX(double)`，`ICancellableEvent` |
| `LivingExperienceDropEvent` | `...\LivingExperienceDropEvent.java` | `@Nullable Player getAttackingPlayer()`、`int getOriginalExperience()`、`int getDroppedExperience()`、`void setDroppedExperience(int)`，`ICancellableEvent` |
| `LivingDropsEvent` | `...\LivingDropsEvent.java` | `DamageSource getSource()`、`Collection<ItemEntity> getDrops()`、`boolean isRecentlyHit()`，`ICancellableEvent` |
| `CriticalHitEvent` | `...\event\entity\player\CriticalHitEvent.java` | `Entity getTarget()`、`float getDamageMultiplier() / setDamageMultiplier(float)`（负值抛 `UnsupportedOperationException`）、`boolean isCriticalHit() / setCriticalHit(boolean)`、`isVanillaCritical()`、`getVanillaMultiplier()`、`setDisableSweep(boolean)`。**javadoc L18：fired on both the logical client and logical server** |
| `ItemFishedEvent` | `...\event\entity\player\ItemFishedEvent.java` | `NonNullList<ItemStack> getDrops()`、`int getRodDamage() / damageRodBy(int)`、`FishingHook getHookEntity()` |
| `TradeWithVillagerEvent` | `...\event\entity\player\TradeWithVillagerEvent.java` | `extends PlayerEvent`（`getEntity()` 即玩家） |
| `AdvancementEvent.AdvancementEarnEvent` | `...\event\entity\player\AdvancementEvent.java` L45 | — |
| `PlayerXpEvent.XpChange` | `...\event\entity\player\PlayerXpEvent.java` L46-61 | `int getAmount() / setAmount(int)`，`ICancellableEvent`；javadoc：**由 `Player#giveExperiencePoints(int)` 触发** |

### 2.3 方块 / 刷怪类

**证据**：`MC\net\neoforged\neoforge\event\level\BlockDropsEvent.java`

```java
public class BlockDropsEvent extends BlockEvent implements ICancellableEvent {
    public List<ItemEntity> getDrops()             // javadoc: "A mutable list of item entities"
    @Nullable public BlockEntity getBlockEntity()
    @Nullable public Entity getBreaker()
    public ItemStack getTool()
    @Override public ServerLevel getLevel()        // ** 一定是 ServerLevel => 天然只在服务端 fire **
    public int getDroppedExperience()
    public void setDroppedExperience(int)          // 负值抛 IllegalArgumentException
}
```

构造期已经算好 `EnchantmentHelper.processBlockExperience(level, tool, state.getExpDrop(...))`（L57）⇒ 挖掘 XP 直接可取。

**证据**：`MC\net\neoforged\neoforge\event\entity\living\FinalizeSpawnEvent.java`

```java
public class FinalizeSpawnEvent extends MobSpawnEvent implements ICancellableEvent {
    public MobSpawnType getSpawnType()                          // L88
    @Nullable public Either<BlockEntity, Entity> getSpawner()   // L122
    public void setSpawnCancelled(boolean)                      // L134
}
```

**证据**：`MC\net\minecraft\world\entity\MobSpawnType.java`

```java
public enum MobSpawnType { NATURAL, ..., SPAWNER, MOB_SUMMONED, SPAWN_EGG, TRIAL_SPAWNER;
    public boolean isSpawner()      { return spawnType == SPAWNER || spawnType == TRIAL_SPAWNER; }   // L23
    public boolean isTrialSpawner() { return spawnType == TRIAL_SPAWNER; }                           // L27
}
```

⇒ **防刷怪塔的正确钩子。**

---

## 3. 伤害结算管线（逐行取证）

**证据 A：`MC\net\neoforged\neoforge\common\damagesource\DamageContainer.java` L24-38 官方 javadoc**

```
The Damage Sequence
1.  LivingEntity#hurt is invoked on the recipient from the source of the attack.
2.  Entity#isInvulnerableTo is invoked and fires EntityInvulnerabilityCheckEvent.
3.  After determining the entity is vulnerable, the DamageContainer is instantiated for the entity.
4.  LivingIncomingDamageEvent is fired.
5.  LivingShieldBlockEvent fires and the result determines if shield effects apply.
6.  LivingEntity#actuallyHurt is called.
7.  armor, magic, and mob_effect reductions are captured in the DamageContainer.
8.  LivingDamageEvent.Pre is fired.
9.  absorption reductions are captured in the DamageContainer.
10. if the damage is not zero, entity health is modified and recorded and LivingDamageEvent.Post is fired.
```

**证据 B：`MC\net\minecraft\world\entity\LivingEntity.java` L1785-1807 实际代码**

```java
protected void actuallyHurt(DamageSource damageSource, float damageAmount) {
    if (!this.isInvulnerableTo(damageSource)) {
        this.damageContainers.peek().setReduction(Reduction.ARMOR,
            this.damageContainers.peek().getNewDamage()
              - this.getDamageAfterArmorAbsorb(damageSource, this.damageContainers.peek().getNewDamage()));   // L1787
        this.getDamageAfterMagicAbsorb(damageSource, this.damageContainers.peek().getNewDamage());            // L1788
        float damage = net.neoforged.neoforge.common.CommonHooks
                           .onLivingDamagePre(this, this.damageContainers.peek());                            // L1789 ** LivingDamageEvent.Pre **
        this.damageContainers.peek().setReduction(Reduction.ABSORPTION,
            Math.min(this.getAbsorptionAmount(), damage));                                                    // L1790
        ...
        if (f1 != 0.0F) { this.getCombatTracker().recordDamage(...); this.setHealth(this.getHealth() - f1); } // L1799-1804
        net.neoforged.neoforge.common.CommonHooks.onLivingDamagePost(this, this.damageContainers.peek());     // L1805 ** LivingDamageEvent.Post **
    }
}
```

**证据 C：`getDamageAfterMagicAbsorb` 内部顺序（L1739-1779）**

```java
if (damageSource.is(DamageTypeTags.BYPASSES_EFFECTS)) return damageAmount;                      // L1740
if (this.hasEffect(MobEffects.DAMAGE_RESISTANCE) && !...BYPASSES_RESISTANCE) {                  // L1743
    int i = (amplifier + 1) * 5; int j = 25 - i;
    ... setReduction(Reduction.MOB_EFFECTS, f2);                                                // L1751  <- 抗性提升
}
if (damageAmount <= 0.0F) return 0.0F;                                                          // L1760
else if (damageSource.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) return damageAmount;            // L1762
else {
    f3 = EnchantmentHelper.getDamageProtection(serverlevel, this, damageSource);                 // L1767
    if (f3 > 0.0F) { damageAmount = CombatRules.getDamageAfterMagicAbsorb(damageAmount, f3);
                     setReduction(Reduction.ENCHANTMENTS, getNewDamage() - damageAmount); }      // L1773-1774 <- 保护附魔
}
```

**证据 D：`MC\net\minecraft\world\damagesource\CombatRules.java`**

```java
public static final float MAX_ARMOR = 20.0F;
public static final float ARMOR_PROTECTION_DIVIDER = 25.0F;
public static final float BASE_ARMOR_TOUGHNESS = 2.0F;
public static final float MIN_ARMOR_RATIO = 0.2F;

public static float getDamageAfterAbsorb(LivingEntity entity, float damage, DamageSource src,
                                         float armorValue, float armorToughness) {
    float f  = 2.0F + armorToughness / 4.0F;                                     // 韧性影响有效护甲
    float f1 = Mth.clamp(armorValue - damage / f, armorValue * 0.2F, 20.0F);     // ** 有效护甲钳到 [0.2*armor, 20]
    float f2 = f1 / 25.0F;
    ItemStack weapon = src.getWeaponItem();
    float f3 = (weapon != null && entity.level() instanceof ServerLevel sl)
             ? Mth.clamp(EnchantmentHelper.modifyArmorEffectiveness(sl, weapon, entity, src, f2), 0.0F, 1.0F)
             : f2;
    return damage * (1.0F - f3);
}
public static float getDamageAfterMagicAbsorb(float damage, float enchantModifiers) {
    float f = Mth.clamp(enchantModifiers, 0.0F, 20.0F);
    return damage * (1.0F - f / 25.0F);
}
```

⇒ 护甲减伤是「有效护甲 / 25」，且除子被钳到 20 ⇒ **纯堆护甲最多 80% 减伤**。

**证据 E：`DamageContainer.Reduction` 枚举（L40-53）**

```java
public enum Reduction {
    INVULNERABILITY, ARMOR, ENCHANTMENTS, MOB_EFFECTS, ABSORPTION, INNATE_RESISTANCE
}
```

⇒ 只有 6 种，**无法新增** ⇒ 自定义减伤必须挂既有类型，或走 `LivingDamageEvent.Pre` / `setAmount`。

**证据 F：`DamageContainer.addModifier` 与 `IReductionFunction`**

```java
// DamageContainer.java L111
public void addModifier(Reduction type, IReductionFunction reductionFunction)
//  javadoc L99-108: "only the LivingIncomingDamageEvent happens early enough"
//                   "if the vanilla reduction is not triggered, the reduction function will not execute"
// IReductionFunction.java L23
float modify(DamageContainer container, float reductionIn);
```

**陷阱**：把自定义减伤挂到 `Reduction.ARMOR` 可行（L1787 无条件调用 `setReduction(ARMOR, ..)`）；
但挂到 `Reduction.ENCHANTMENTS` **不可靠**——只有 `f3 > 0`（有保护附魔）时才触发（L1772）。

---

## 4. 生命恢复与自然回血的关系

**证据**：`MC\net\minecraft\world\food\FoodData.java` L39-79

```java
public void tick(Player player) {
    Difficulty difficulty = player.level().getDifficulty();
    ...
    boolean flag = player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
    if (flag && this.saturationLevel > 0.0F && player.isHurt() && this.foodLevel >= 20) {   // 分支 A
        this.tickTimer++;
        if (this.tickTimer >= 10) { float f = Math.min(this.saturationLevel, 6.0F);
                                    player.heal(f / 6.0F); this.addExhaustion(f); this.tickTimer = 0; }
    } else if (flag && this.foodLevel >= 18 && player.isHurt()) {                            // 分支 B
        this.tickTimer++;
        if (this.tickTimer >= 80) { player.heal(1.0F); this.addExhaustion(6.0F); this.tickTimer = 0; }
    } else if (this.foodLevel <= 0) { ... player.hurt(player.damageSources().starve(), 1.0F); }
    else { this.tickTimer = 0; }
}
```

⇒ 原版自然回血**完全在 `FoodData.tick` 内**，与 `PlayerTickEvent.Post` 无冲突；我们的 regen 是**额外叠加**，不是替换。
⇒ 两个分支都要求 `player.isHurt()`（证据 `Player.java` L1813）⇒ **满血时原版不回血**；自定义 regen 默认也应 `only_when_hurt = true` 保持一致。

取 FoodData 的入口（`MC\net\minecraft\world\entity\player\Player.java`）：

```java
public void causeFoodExhaustion(float exhaustion)   // L1793
public FoodData getFoodData()                       // L1805
public boolean isHurt()                             // L1813
```

---

## 5. 玩家 XP 与等级

**证据**：`MC\net\minecraft\world\entity\player\Player.java`

```java
public void giveExperiencePoints(int xpPoints)      // L1718   <- PlayerXpEvent.XpChange 从这里 fire
public int getXpNeededForNextLevel()                // L1782
```

⇒ 原版等级会被铁砧 / 附魔消耗、被 `/xp` 直接改写，**不能作为 RPG 等级的唯一真相**（见 DESIGN 第 7 节）。

---

## 6. 数据持久化：Data Attachment

**证据**：`MC\net\neoforged\neoforge\registries\NeoForgeRegistries.java`

```java
public static final Registry<AttachmentType<?>> ATTACHMENT_TYPES =
    new RegistryBuilder<>(Keys.ATTACHMENT_TYPES).create();                        // L43
public static final class Keys {                                                  // L47
    public static final ResourceKey<Registry<AttachmentType<?>>> ATTACHMENT_TYPES = key("attachment_types");   // L58
}
```

**证据**：`MC\net\neoforged\neoforge\attachment\AttachmentType.java`

```java
public static <T> Builder<T> builder(Supplier<T> defaultValueSupplier)                              // L95
public static <T> Builder<T> builder(Function<IAttachmentHolder, T> defaultValueConstructor)         // L109
public static <S extends Tag, T extends INBTSerializable<S>> Builder<T> serializable(Supplier<T>)    // L119
public static <S extends Tag, T extends INBTSerializable<S>> Builder<T> serializable(Function<..,T>) // L132

public static class Builder<T> {
    public Builder<T> serialize(IAttachmentSerializer<?, T> serializer)                     // L168
    public Builder<T> serialize(Codec<T> codec)                                             // L184
    public Builder<T> serialize(Codec<T> codec, Predicate<? super T> shouldSerialize)       // L196
    public Builder<T> copyOnDeath()                                                         // L224  ** 要求先 serialize **
    public Builder<T> copyHandler(IAttachmentCopyHandler<T>)                                // L238
    public Builder<T> sync(AttachmentSyncHandler<T> syncHandler)                            // L250
    public Builder<T> sync(StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec)     // L261
    public Builder<T> sync(BiPredicate<IAttachmentHolder, ServerPlayer> sendToPlayer,
                           StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec)     // L273
    public AttachmentType<T> build()                                                        // L294
}
```

**类 javadoc 关键句（L41-45）**

```
<h3>Entity-exclusive behavior:</h3>
- Serializable entity attachments are not copied on death by default (but they are copied when returning from the end).
- Serializable entity attachments can opt into copying on death via Builder#copyOnDeath().
```

⇒ `copyOnDeath()` 同时覆盖「死亡重生」与「End -> Overworld 的 PlayerEvent.Clone」两条路径 ⇒ **完美满足「死亡零惩罚」。**
⚠ `copyOnDeath()` 在 `serializer == null` 时抛 `IllegalStateException`（L225-227）。

**证据**：`MC\net\neoforged\neoforge\attachment\IAttachmentHolder.java`

```java
boolean hasAttachments()
boolean hasData(AttachmentType<?> type)   /  default <T> boolean hasData(Supplier<AttachmentType<T>> type)
<T> T getData(AttachmentType<T> type)     /  default <T> T getData(Supplier<AttachmentType<T>> type)   // 不存在会写入默认值
default <T> Optional<T> getExistingData(AttachmentType<T> type)
<T> @Nullable T setData(AttachmentType<T> type, T data)
<T> @Nullable T removeData(AttachmentType<T> type)
default void syncData(AttachmentType<?> type)
```

⇒ `Player` 实现 `IAttachmentHolder`，直接 `player.getData(...)` / `player.setData(...)`。

⚠ **重要**：`sync(..)` 的语义是「synced to all clients that **receive the holding object**」（L257 javadoc）。对玩家实体而言**是否包含自己并不明确**。
⇒ DESIGN 决定：**UI 关键数据用显式自定义 payload（`PacketDistributor.sendToPlayer`）推送**，行为确定、可验证。

---

## 7. 网络

**证据**：`MC\net\neoforged\neoforge\network\event\RegisterPayloadHandlersEvent.java`

```java
public class RegisterPayloadHandlersEvent extends Event implements IModBusEvent {   // ** mod 总线 **
    public PayloadRegistrar registrar(String version)                               // L40
}
```

类 javadoc（L23-29）要点：
- payload **不要在 StreamCodec 里写自己的 id**，框架自动写；
- 必须注册后才能发送；
- **默认 handler 会被 `MainThreadPayloadHandler` 包装，在主线程执行**；
- 单机 / LAN 的内存连接**也会走序列化**。

**证据**：`MC\net\neoforged\neoforge\network\registration\PayloadRegistrar.java`

```java
public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>, IPayloadHandler<T>)   // L44
public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>, IPayloadHandler<T>)   // L52
public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(...)        // L62
public <T extends CustomPacketPayload> PayloadRegistrar configurationToClient(...)    // L70
public PayloadRegistrar executesOn(HandlerThread thread)   // L129
public PayloadRegistrar versioned(String version)          // L145
public PayloadRegistrar optional()                         // L158
```

**证据**：`MC\net\minecraft\network\protocol\common\custom\CustomPacketPayload.java`

```java
public interface CustomPacketPayload {
    CustomPacketPayload.Type<? extends CustomPacketPayload> type();
    static <B extends ByteBuf, T extends CustomPacketPayload> StreamCodec<B, T> codec(StreamMemberEncoder<B,T>, StreamDecoder<B,T>)
    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> createType(String id)
    public static record Type<T extends CustomPacketPayload>(ResourceLocation id) {}
}
```

**证据**：`MC\net\minecraft\network\codec\StreamCodec.java`

```java
static <B, V> StreamCodec<B, V> of(final StreamEncoder<B, V> encoder, final StreamDecoder<B, V> decoder)   // L15
static <B, C, T1..T5> StreamCodec<B, C> composite(...)     // L208
static <B, C, T1..T6> StreamCodec<B, C> composite(...)     // L243   <- 最大 6 元
```

⇒ `composite` 最多 6 个字段；我们的数据包字段更多 ⇒ **必须用 `StreamCodec.of(encoder, decoder)` 手写。**

**证据**：`MC\net\minecraft\network\FriendlyByteBuf.java`

```java
public FriendlyByteBuf writeVarInt(int input)         // L556
public int readVarInt()                               // L522
public FriendlyByteBuf writeVarLong(long value)       // L564
public long readVarLong()                             // L526
public FriendlyByteBuf writeVarIntArray(int[] array)  // L304
```

**证据**：`MC\net\neoforged\neoforge\network\PacketDistributor.java`

```java
public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... payloads)              // L38
public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload, CustomPacketPayload...)  // L50  **
public static void sendToPlayersInDimension(ServerLevel level, ...)                                       // L57
public static void sendToAllPlayers(...)                                                                  // L81
public static void sendToPlayersTrackingEntity(Entity entity, ...)                                        // L89
```

**证据**：`MC\net\neoforged\neoforge\network\handling\IPayloadContext.java`

```java
ICommonPacketListener listener();
default Connection connection()
Player player();                                          // server-bound -> 发送者 ServerPlayer；client-bound -> LocalPlayer
default void reply(CustomPacketPayload payload)
default void disconnect(Component reason)
CompletableFuture<Void> enqueueWork(Runnable task)
<T> CompletableFuture<T> enqueueWork(Supplier<T> task)
PacketFlow flow();
default ConnectionProtocol protocol()
void handle(CustomPacketPayload payload)
```

---

## 8. 配置

**证据（javap，`FML` jar）**

```
public final class net.neoforged.fml.config.ModConfig$Type extends java.lang.Enum<...> {
  public static final ModConfig$Type COMMON;
  public static final ModConfig$Type CLIENT;
  public static final ModConfig$Type SERVER;
  public static final ModConfig$Type STARTUP;
}
public abstract class net.neoforged.fml.ModContainer {
  public final java.lang.String getModId();
  public abstract net.neoforged.bus.api.IEventBus getEventBus();
  public void registerConfig(ModConfig$Type, IConfigSpec);
  public void registerConfig(ModConfig$Type, IConfigSpec, java.lang.String fileName);
}
```

**NeoForge 官方用法（证据 `MC\net\neoforged\neoforge\common\NeoForgeMod.java` L568-570）**

```java
container.registerConfig(ModConfig.Type.CLIENT, NeoForgeConfig.clientSpec);
container.registerConfig(ModConfig.Type.SERVER, NeoForgeConfig.serverSpec);
container.registerConfig(ModConfig.Type.COMMON, NeoForgeConfig.commonSpec);
```

**证据**：`MC\net\neoforged\neoforge\common\ModConfigSpec.java`

```java
public Builder comment(String comment)                                        // L803
public Builder comment(String... comment)                                     // L808
public Builder push(String path)                                              // L842
public Builder push(List<String> path)                                        // L846
public ModConfigSpec build()                                                  // L877
public <V extends Comparable<? super V>> ConfigValue<V> defineInRange(String path, V def, V min, V max, Class<V>)  // L351
public DoubleValue defineInRange(String path, double def, double min, double max)   // L753
public IntValue    defineInRange(String path, int    def, int    min, int    max)   // L770
public LongValue   defineInRange(String path, long   def, long   min, long   max)   // L787
```

⇒ `ConfigValue` 懒加载（`.get()` 时才读），可以在 static 初始化里**循环生成** 130 条配置项。

---

## 9. 事件总线与注册入口

**证据（javap，`BUS` + `FML` jar）**

```
public interface net.neoforged.bus.api.IEventBus {
  void register(java.lang.Object);
  <T extends Event> void addListener(java.util.function.Consumer<T>);
  <T extends Event> void addListener(java.lang.Class<T>, java.util.function.Consumer<T>);
  <T extends Event> void addListener(EventPriority, java.util.function.Consumer<T>);
  <T extends Event> void addListener(EventPriority, boolean, java.util.function.Consumer<T>);
  void unregister(java.lang.Object);
  <T extends Event> T post(T);
  void start();
}
public final class EventPriority extends Enum { HIGHEST, HIGH, NORMAL, LOW, LOWEST }
public interface SubscribeEvent { EventPriority priority(); boolean receiveCanceled(); }
public interface net.neoforged.fml.common.EventBusSubscriber {
  Dist[] value(); String modid(); EventBusSubscriber$Bus bus();
}
public final class EventBusSubscriber$Bus extends Enum { GAME, MOD }
public interface net.neoforged.fml.common.Mod { String value(); Dist[] dist(); }
```

**证据**：`MC\net\neoforged\neoforge\common\NeoForge.java` L17

```java
public static final IEventBus EVENT_BUS = BusBuilder.builder().startShutdown().classChecker(...)
```

⇒ **游戏总线 = `NeoForge.EVENT_BUS`；mod 总线 = `@Mod` 构造器注入的 `IEventBus` 或 `@EventBusSubscriber(bus = Bus.MOD)`。**

---

## 10. 客户端：按键与 GUI

**证据**：`MC\net\minecraft\client\KeyMapping.java`

```java
public static final String CATEGORY_MOVEMENT  = "key.categories.movement";     // L21
public static final String CATEGORY_GAMEPLAY  = "key.categories.gameplay";     // L24
public static final String CATEGORY_INTERFACE = "key.categories.ui";           // L26
public KeyMapping(String name, int keyCode, String category)                                   // L88
public KeyMapping(String name, InputConstants.Type type, int keyCode, String category)         // L92
public boolean isDown()                                                                        // L102
public boolean consumeClick()                                                                  // L110  **
// NeoForge 追加的重载（带 IKeyConflictContext / KeyModifier）
public KeyMapping(String, IKeyConflictContext, InputConstants.Type, int, String)               // L222
public KeyMapping(String, IKeyConflictContext, InputConstants.Key, String)                     // L229
public KeyMapping(String, IKeyConflictContext, KeyModifier, InputConstants.Type, int, String)  // L236
public KeyMapping(String, IKeyConflictContext, KeyModifier, InputConstants.Key, String)        // L243
```

**1.21.1 的 category 参数是 `String`（lang key），不是枚举。**

**证据**：`MC\net\neoforged\neoforge\client\event\RegisterKeyMappingsEvent.java`

```java
public class RegisterKeyMappingsEvent extends Event implements IModBusEvent {   // ** mod 总线，仅客户端 **
    public void register(KeyMapping key)                                        // L35
}
```

**证据**：`MC\net\neoforged\neoforge\client\event\ClientTickEvent.java`

```java
public abstract class ClientTickEvent extends Event { public static class Pre ...; public static class Post ... }
```

**证据**：`MC\net\minecraft\client\gui\GuiGraphics.java`

```java
public void fill(int minX, int minY, int maxX, int maxY, int color)                                  // L257
public void fill(int minX, int minY, int maxX, int maxY, int z, int color)                           // L271
public void fillGradient(int x1, int y1, int x2, int y2, int colorFrom, int colorTo)                 // L332
public void drawCenteredString(Font font, Component text, int x, int y, int color)                   // L424
public int  drawString(Font font, Component text, int x, int y, int color)                           // L556
public int  drawString(Font font, Component text, int x, int y, int color, boolean dropShadow)       // L572
public void blit(ResourceLocation atlasLocation, int x, int y, int uOffset, int vOffset, int uWidth, int vHeight)  // L789
public void blitSprite(ResourceLocation sprite, int x, int y, int width, int height)                 // L677
public void renderItem(ItemStack stack, int x, int y)                                                // L1196
public void renderTooltip(Font font, Component text, int mouseX, int mouseY)                         // L1411
public void renderTooltip(Font font, List<Component> tooltipLines, Optional<TooltipComponent> vtc, int mouseX, int mouseY)  // L1398
```

**1.21.1 的 GUI 全部走 `GuiGraphics`（1.19- 的 `PoseStack` 首参已移除）。**

**证据**：`MC\net\minecraft\client\gui\screens\Screen.java`

```java
protected Screen(Component title)                                                               // L99
public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)          // L132
public boolean keyPressed(int keyCode, int scanCode, int modifiers)                             // L150
public void onClose()                                                                           // L224
protected void init()                                                                           // L365
public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) // L377
```

**证据**：`MC\net\minecraft\client\gui\components\Button.java`

```java
public static Button.Builder builder(Component message, Button.OnPress onPress)   // L22
public static class Builder { ... }
```

**证据**：`MC\net\minecraft\client\gui\screens\inventory\AbstractContainerScreen.java`

```java
public AbstractContainerScreen(T menu, Inventory playerInventory, Component title)   // L106
protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY)         // L237
```

⇒ 走 Menu 方案需要额外注册 `MenuType` + `RegisterMenuScreensEvent`。**DESIGN 决定不走 Menu，用纯 `Screen`。**

---

## 11. 指令

**证据**：`MC\net\neoforged\neoforge\event\RegisterCommandsEvent.java`

```java
public class RegisterCommandsEvent extends Event {                 // ** 游戏总线 **
    public CommandDispatcher<CommandSourceStack> getDispatcher()   // L36
    public Commands.CommandSelection getCommandSelection()         // L43
    public CommandBuildContext getBuildContext()                   // L50
}
```

**证据**：`MC\net\minecraft\commands\Commands.java` L419

```java
public static LiteralArgumentBuilder<CommandSourceStack> literal(String name)
```

**证据**：`MC\net\minecraft\commands\CommandSourceStack.java`

```java
public boolean hasPermission(int level)                                          // L390
public ServerPlayer getPlayerOrException() throws CommandSyntaxException         // L415
public void sendSuccess(Supplier<Component> messageSupplier, boolean allowLogging)  // L480
```

---

## 12. 移动速度与服务端「moved too quickly」校验

**证据**：`MC\net\minecraft\server\network\ServerGamePacketListenerImpl.java` L896-923

```java
double d6 = d0 - this.firstGoodX;
double d7 = d1 - this.firstGoodY;
double d8 = d2 - this.firstGoodZ;
double d9  = this.player.getDeltaMovement().lengthSqr();          // L899
double d10 = d6 * d6 + d7 * d7 + d8 * d8;                         // L900
...
if (!this.player.isChangingDimension()
    && (!this.player.level().getGameRules().getBoolean(GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK) || !flag)) {
    float f2 = flag ? 300.0F : 100.0F;                            // ** 固定常量，不读 MOVEMENT_SPEED **
    if (d10 - d9 > (double)(f2 * (float)i) && !this.isSingleplayerOwner()) {
        LOGGER.warn("{} moved too quickly! {},{},{}", ...);
        this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), ...);   // ** 回拉，不是踢出 **
        return;
    }
}
```

另有 L946-953 的 `moved wrongly!` 检查：`d10 > 0.0625`（= 0.25 格）且碰撞箱与环境冲突时触发，同样是回拉。

**结论（重要）**
1. 1.21.1 **没有**基于 `MOVEMENT_SPEED` 的比例式速度上限（新版本才引入 `MAX_PLAYER_MOVE_SPEED`）。
2. 阈值是**固定 ~10 格/tick（水平）**，超出只触发**服务端回拉传送 + warn 日志**，**不会踢人**。
3. **单机房主（`isSingleplayerOwner()`）完全豁免。**
4. 原版玩家步行约 4.317 m/s，约 0.216 格/tick ⇒ 阈值约等于 **46x 原版速度** ⇒ 把 MOVEMENT_SPEED 有效上界压到 **1.5（= 15x 原版）** 有 3x 安全余量。
5. 副作用：速度极高时客户端会「先冲出去再被拉回」，手感崩坏 ⇒ 除数值阈值外还有**体验上限**。

---

## 13. 尚待编译 / 运行验证的项（诚实标注）

| # | 项 | 为什么没验证 | 验证方式 |
|---|---|---|---|
| V1 | `RangedAttribute` 的 descriptionId 与 lang key 的对应（如 `attribute.rpgleveling.health_regen`） | 需跑客户端看 tooltip | M1 启动客户端看属性 tooltip；未翻译则显示 key 原文 |
| V2 | 7 个自定义属性 `.setSyncable(true)` 后客户端 `getAttributeValue` 是否立刻有值 | 需运行时观察 | M2 客户端 debug 打印，与 `/rpg list` 服务端值比对 |
| V3 | `PlayerEvent.Clone` 与 `AttachmentType.copyOnDeath()` 的执行顺序（谁覆盖谁） | 需运行时观察 | M2 死亡后检查数据是否保留；若丢失则在 Clone 里手工复制 |
| V4 | `CriticalHitEvent` 双端 fire 时只改服务端是否导致暴击粒子不同步 | 需运行时观察 | M4 双端对比；若不同步改为双端同种子 |
| V5 | `PlayerLoggedInEvent` 时附件是否已从磁盘加载完毕 | 需运行时观察 | M2 登录打印 `totalXp`；若为 0 则延后一 tick |
| V6 | `BlockDropsEvent` 对创造模式玩家是否也 fire（要不要排除创造） | 源码未找到 `isCreative` 判断 | M3 创造模式挖矿看是否给 XP |
| V7 | `ItemCraftedEvent` 是否只在服务端 fire | 未追 `EventHooks.firePlayerCraftingEvent` 调用点 | 代码统一加 `!level.isClientSide()` 守卫即可，不依赖结论 |
| V8 | `LivingDropsEvent.getDrops()` 返回集合是否可变 | javadoc 未声明 | **不依赖**：改为 `setCount` + `level.addFreshEntity()` 处理溢出 |
| V9 | 原版 MAX_HEALTH 上限 1024 是否会在 UI 上渲染异常 | 需运行时观察 | M2 用 `/rpg admin setpoints` 拉满后看血条 |
| V10 | `neoforge.mods.toml` 必填字段与 `pack.mcmeta` 的 `pack_format` | 未读 moddev metadata spec | 参考 `星空之刃模组-stellersword-1.0.0.jar` 内的 mods.toml（用户已确认可加载） |