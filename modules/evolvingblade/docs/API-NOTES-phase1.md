# PHASE 1 API 取证记录（NeoForge 21.1.248 / Minecraft 1.21.1）

> 铁律：不凭记忆写 API。下面每一条都在本机可复现的一手信源里核对过，并给出**文件与行号**。
> 取证环境：
> - 反编译源码（Minecraft + NeoForge 合并）：`.gradle-user-home/caches/ng_execute/5d0290f4.../output/`
>   （`.reference/mcsrc` 是它的目录联接，仅本机使用，已加入 `.gitignore`）
> - Gradle 缓存中的 `neoforge-21.1.248-sources.jar` / `neoforge-21.1.248-universal.jar`
> - FML loader `loader-4.0.43.jar`（`javap -v` 读注解默认值）
> - 原版 1.21.1 服务端 jar 内的数据包路径与示例 JSON

## 1. 物品 / 武器

| 事实 | 证据 |
|---|---|
| `SwordItem` 构造是 `SwordItem(Tier, Item.Properties)`（1.21 已移除旧的 `(Tier,int,float,Properties)`） | `net/minecraft/world/item/SwordItem.java:20` |
| 属性构造用 `SwordItem.createAttributes(Tier, float damage, float speed)`，内部把 `tier.getAttackDamageBonus()` 加进 `ATTACK_DAMAGE` | `SwordItem.java:42-57` |
| 修饰符 ID 常量 `Item.BASE_ATTACK_DAMAGE_ID` / `BASE_ATTACK_SPEED_ID` | `net/minecraft/world/item/Item.java:62-63` |
| `ItemAttributeModifiers.builder()` 与 `Builder.add(Holder<Attribute>, AttributeModifier, EquipmentSlotGroup)` | `world/item/component/ItemAttributeModifiers.java:50, 107` |
| `Item.Properties` 可用方法：`stacksTo`(452) / `rarity`(468) / `fireResistant`(472) / `component`(490) / `attributes`(500) | `world/item/Item.java` |
| 不可破坏 = `DataComponents.UNBREAKABLE` + `new Unbreakable(boolean showInTooltip)`；`isDamageableItem()` 要求同时存在 `MAX_DAMAGE` | `world/item/component/Unbreakable.java:15`；`ItemStack.java:440-442` |
| 玩家自身基础攻击力 = **1.0**（因此阶段总攻击要减 1.0，不能加两次） | `world/entity/player/Player.java:230` |
| 玩家攻速属性使用注册默认值（4.0），原版铁剑 `-2.4` 得到 1.6 | `Player.java:232`；`world/item/Items.java:990-991` |
| `Tiers.IRON` 等常量 | `world/item/Tiers.java:14` |
| `Inventory.add(ItemStack)` 返回 false 表示"整叠都放不下"（用于背包满时保持 PENDING） | `world/entity/player/Inventory.java:245` |
| `ResourceLocation.fromNamespaceAndPath` / `tryParse` | `resources/ResourceLocation.java:61, 85` |

## 2. 击杀归属

| 事实 | 证据 |
|---|---|
| `LivingDeathEvent` 实现 `ICancellableEvent`（取消即"实体不会死"，必须跳过入账） | `net/neoforged/neoforge/event/entity/living/LivingDeathEvent.java:34-44` |
| 原版归属入口 `LivingEntity.getKillCredit()` → `lastHurtByPlayer` 优先，否则 `lastHurtByMob` | `world/entity/LivingEntity.java:1814-1820` |
| `Projectile.getOwner()` 给出射手的实体（玩家的弹射物归属） | `world/entity/projectile/Projectile.java:54` |
| `OwnableEntity.getOwner()`（宠物主人归属） | `world/entity/OwnableEntity.java:14` |
| `FakePlayer extends ServerPlayer`（必须显式拒绝） | `net/neoforged/neoforge/common/util/FakePlayer.java:97` |
| 末影龙多部位：`EnderDragonPart extends PartEntity<EnderDragon>`，`hurt()` 直接转发给父实体 | `world/entity/boss/EnderDragonPart.java:16, 59-60` |
| 凋灵持有 `ServerBossEvent bossEvent` 字段（反射兜底可命中） | `world/entity/boss/wither/WitherBoss.java:74` |
| **末影龙的 Boss 血条不在实体上**，而在 `EndDragonFight.dragonEvent` | `world/level/dimension/end/EndDragonFight.java:75` |
| `ServerBossEvent` 位于 `net.minecraft.server.level` | `net/minecraft/server/level/ServerBossEvent.java:15` |

> 结论：末影龙**必须**依赖包含名单/标签（默认包含名单已含 `minecraft:ender_dragon`），
> `ServerBossEvent` 字段兜底对它无效——这正是 SPEC 要求包含名单的原因。

## 3. 敌对 / 标签

| 事实 | 证据 |
|---|---|
| `MobCategory.MONSTER` | `world/entity/MobCategory.java:8` |
| `EntityType.getCategory()` | `world/entity/EntityType.java:1046` |
| `EntityType.is(TagKey<EntityType<?>>)`（用于 `evolvingblade:bosses` / `c:bosses` / `evolvingblade:hostile`） | `EntityType.java:1223` |
| `TagKey.create(ResourceKey<Registry<T>>, ResourceLocation)` | `tags/TagKey.java:35` |
| 1.21.1 数据包目录为**单数**：`data/<ns>/recipe/`、`data/<ns>/tags/entity_type/` | 原版 1.21.1 服务端 jar 内条目列举（`data/minecraft/recipe/ender_eye.json`、`data/minecraft/tags/entity_type/skeletons.json`） |

## 4. Data Attachment（玩家数据持久化）

| 事实 | 证据 |
|---|---|
| 可序列化的实体 attachment **默认不会**在死亡时复制；`copyOnDeath()` 才会 | `net/neoforged/neoforge/attachment/AttachmentType.java:43-44, 221-229` |
| 复制统一走 `copyAttachmentsFrom(original, isWasDeath)`：`isDeath=true` 只复制 `copyOnDeath` 类型，`false`（末地返回）复制全部可序列化类型 | `attachment/AttachmentInternals.java:52-53, 56-59`；`common/extensions/IEntityExtension.java`（`copyAttachmentsFrom` 默认实现） |
| 复制实现是 `to.getAttachmentMap().put(type, copy)` —— **put（覆盖）而非累加**，且目标永远是全新实体实例 | `attachment/AttachmentInternals.java:37-40` |
| `IAttachmentHolder.getData` 缺省时会写入默认值；`setData` 返回旧值 | `attachment/IAttachmentHolder.java:39, 90` |
| attachment 注册表键 `NeoForgeRegistries.Keys.ATTACHMENT_TYPES` | `net/neoforged/neoforge/registries/NeoForgeRegistries.java:58` |
| `AttachmentType.builder(Supplier).serialize(Codec).copyOnDeath().build()` | `AttachmentType.java:95, 184, 224, 294` |

> **"末地返回不会复制/累加两次"的结论**：死亡重生与末地返回是**两条互斥路径**，
> 各自只调用一次 `copyAttachmentsFrom`，且写入方式是覆盖。因此本模组**不需要**、也**绝不应该**
> 在 `PlayerEvent.Clone` 里再加任何计数；`EBPlayerLifecycleHandler.onClone` 只做校验与日志，
> 一旦发现新玩家计数大于旧玩家就打印警告（真实异常才触发）。

## 5. 配置

| 事实 | 证据 |
|---|---|
| `ModConfigSpec.Builder.defineList(String path, List default, Supplier newElement, Predicate elementValidator)` | `net/neoforged/neoforge/common/ModConfigSpec.java:408` |
| **没有** `String path` + `sizeRange` 的重载（只有 `List<String> path` 版本），且 `split(String)` 是 private | `ModConfigSpec.java:612, 1372` |
| 列表长度约束因此由本模组的 `sanitizeLongList` / `sanitizeStageValues` 负责（非法即回落默认值） | 本项目 `config/EBConfig.java`、`growth/GrowthSettings.java` |
| `defineInRange` 系列会在加载时按 Range 修正数值 | `ModConfigSpec.java:753-800` |
| `ModConfigSpec.Range.of(int,int)` | `ModConfigSpec.java:1005` |
| NeoForge 配置界面翻译键格式为 `<modid>.configuration.<key>` | `net/neoforged/neoforge/client/gui/ConfigurationScreen.java:280, 557` |

## 6. 事件总线

| 事实 | 证据 |
|---|---|
| `@EventBusSubscriber` 的 `bus()` 默认值是 **GAME**；`Bus` 枚举只有 `GAME`/`MOD`；FML 4.0.43 中 `bus()` 与 `Bus` **已标记待删除** | `loader-4.0.43.jar` → `javap -v net.neoforged.fml.common.EventBusSubscriber`（`AnnotationDefault: GAME`；常量池含 `Deprecated`） |
| 因此 `ModConfigEvent.Loading/Reloading`（MOD 总线）改为在主类中显式 `modEventBus.addListener(EBConfig::onLoading)`，避免弃用 API | 本项目 `EvolvingBlade.java` |
| `RegisterCommandsEvent.getDispatcher()` | `net/neoforged/neoforge/event/RegisterCommandsEvent.java:36` |
| `ServerTickEvent.Post` / `PlayerTickEvent.Post` | `net/neoforged/neoforge/event/tick/ServerTickEvent.java`、`.../PlayerTickEvent.java` |

## 7. 配方 / 资源格式

| 事实 | 证据 |
|---|---|
| 无序配方 JSON 字段：`type` / `group?` / `category?`(默认 misc) / `result`(ItemStack) / `ingredients` | `world/item/crafting/ShapelessRecipe.java:85-109` |
| 单个原料写法 `{"item": "..."}` 由 `Ingredient.ItemValue.MAP_CODEC` 的 `item` 字段解析 | `world/item/crafting/Ingredient.java:251-252` |
| 产物写法 `{"count": 1, "id": "..."}`（原版 `data/minecraft/recipe/ender_eye.json` 实测） | 原版 1.21.1 服务端 jar |
| 物品模型 `parent: minecraft:item/handheld` + `textures.layer0`（1.21.1 仍是 layer0） | 原版客户端资源与 `Items.java` 模型命名约定 |

## 8. 工具链事实（非 API，但影响能否构建/测试）

| 事实 | 证据 |
|---|---|
| 本机 `JAVA_HOME` = `java-runtime-delta` 21.0.7（含 `javac`，是完整 JDK） | `java -version`；`bin/javac.exe` 存在 |
| Gradle 8.14.2 + ModDevGradle 2.0.81 + NeoForge 21.1.248 均已在本机缓存中，可离线构建 | `.gradle-user-home/caches/modules-2/...`、`wrapper/dists/gradle-8.14.2-bin` |
| 模板原本要求 ModDevGradle 2.0.148 + Gradle 9.2.1，但本机缓存只有 2.0.81 + 8.14.2；已按可复现原则改用缓存版本 | 缓存目录列举 |
| **Gradle worker 的 `@argfile` 与非 ASCII 路径冲突**：`ApplicationClassesInSystemClassLoaderWorkerImplementationFactory` 在 worker JVM ≥ Java 9 时一律把 classpath 写入临时 `@argfile`，文件用 UTF-8 写；而 Windows JVM 按平台 ANSI 代码页（GBK）解析，导致含中文路径的 classpath 被解码坏 → `ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain` | 反汇编 `gradle-core-8.14.2.jar` 中该工厂类；实测对照：同内容 argfile 用 GBK 编写可正常加载，用 UTF-8 编写失败 |
| `JavaExec` **不使用** argfile（整个 Gradle 发行版中只有上述 worker 工厂引用 `ArgWriter`），classpath 内联在命令行，因此不受影响 | 在解包后的 gradle-core/process-services/worker-main/base-services 中检索 `ArgWriter` 引用 |

## 9. 未取证 / 未验证事项（诚实记录）

- 未在真实**多人**客户端里运行本模组（GameTestServer 是无头服务器 Dist，已覆盖服务器行为；
  客户端渲染、GUI 与网络包属 PHASE 2/最终验收）。
- `ServerBossEvent` 反射探测只用测试自带的标记类型验证了机制，未对真实凋灵实体实测
  （末影龙/凋灵走的是默认包含名单，不依赖反射）。
- ~~占位贴图是程序化生成的 32×32 像素图，**不是** SPEC 要求的 8 张正式美术。~~
  **（PHASE 2 已解决）** 占位贴图已删除；现在是 8 张**原创、程序化生成**的 32×32 透明像素剑
  （`textures/item/star_eater_stage0..7.png`，生成器 `tools/GenerateSwordTextures.java`，纯 JDK），
  配合 `evolvingblade:stage` 模型谓词展示当前持有者阶段。详见 `result-phase2.md`。

---

# 返工第 1 轮补充取证（PHASE 1 rework 1）

以下每条同样来自一手信源（反编译源码 / Gradle 缓存 jar），标注文件与行号。

## A. 死亡是否"真正提交"

| 事实 | 证据 |
|---|---|
| `LivingEntity.die()` **第一件事**就是调用 `CommonHooks.onLivingDeath(...)`，被取消则**立即 return**——不置 `dead`、不掉落、不设置姿态 | `world/entity/LivingEntity.java:1408-1410` |
| `die()` 走完检查后才 `this.dead = true;` 并最终 `this.setPose(Pose.DYING);` | `LivingEntity.java:1425, 1437` |
| `isDeadOrDying()` 在 1.21.1 **只看血量**（`getHealth() <= 0`），被取消死亡后血量同样是 0，所以**不能**用它判断"是否真的死了" | `LivingEntity.java:1134-1136` |
| `isAlive()` = `!isRemoved() && getHealth() > 0`（用于拒绝"被复活"的实体） | `LivingEntity.java:1631-1633` |
| `protected boolean dead;` 没有公开读取方式 | `LivingEntity.java:211` |
| **`Pose.DYING` 在整个代码库里只在 `LivingEntity.die()` 被设置一次**（另一处 `Player.java:150` 只是读取），没有任何地方会改回去 | 全库检索 `Pose.DYING` 仅 2 处 |
| `tickDeath()` 由 `isDeadOrDying()` 而非 `dead` 触发，所以被取消死亡的实体也会在 20 tick 后被移除——`deathTime` 因此**不能**用来区分取消与真死 | `LivingEntity.java:479-481, 566-567` |
| 末影龙与凋灵**都没有**覆写 `die()`，因此都走 `LivingEntity.die()`（会设置 DYING 姿态） | 全库检索 `EnderDragon`/`WitherBoss` 无 `public void die` |

> 结论：`isTerminalDeath` 使用
> `!isRemoved() && !isAlive() && isDeadOrDying() && getPose() == Pose.DYING`。
> 这是**公开 API、零反射**，且被 GameTest 实测锁定（真实死亡=DYING、被取消死亡≠DYING）。

## B. 背包交付

| 事实 | 证据 |
|---|---|
| `Inventory.add(...)` 在 `player.hasInfiniteMaterials()`（创造模式）且放不下时，会 `stack.setCount(0); return true;`——**什么都没放进背包** | `world/entity/player/Inventory.java:266-268`（受损物品分支）与 `283-286`（普通物品分支） |
| 主背包槽位数常量 `Inventory.INVENTORY_SIZE = 36`（盔甲 0..3、副手 40 属于其它 compartment） | `Inventory.java:26, 28` |
| `getFreeSlot()` 只扫描 36 个主背包槽位并返回首个空位，否则 −1 | `Inventory.java:59-67` |
| `setItem(int, ItemStack)` / `getItem(int)` 对 0..35 操作主背包 | `Inventory.java:385-400, 491` |
| `hasInfiniteMaterials()` = `abilities.instabuild` | `world/entity/player/Player.java`（`hasInfiniteMaterials()`） |

> 结论：交付判定改为"主背包存在真正的空槽位 → 写入 → 复核槽位内容"，与 `add()` 的返回值无关。

## C. GameTest 基础设施（本次新增）

| 事实 | 证据 |
|---|---|
| `GameTestServer extends MinecraftServer`（**不是** `DedicatedServer`），因此**不需要** EULA/`eula.txt` | `gametest/framework/GameTestServer.java:54`；`server/dedicated/DedicatedServer.java` 中不存在 EULA 逻辑 |
| GameTest 必须提供结构模板，否则抛 `Missing test structure: <name>` | `gametest/framework/StructureUtils.java:105-109` |
| 结构文件路径：`data/<ns>/structure/<name>.nbt`，由 `StructureTemplateManager` 从资源包加载 | `StructureUtils.java:106-107` |
| 结构 NBT 必需字段：`size`(IntArray 3)、`blocks`(List)、`palette`(List)、`entities`(List，可缺省) | `world/level/levelgen/structure/templatesystem/StructureTemplate.java:706-733` |
| `@GameTest(template=...)` 配合 `@PrefixGameTestTemplate(false)` 可用单一共享结构名 | `gametest/framework/GameTestRegistry.java:138-160`；`neoforged/neoforge/gametest/GameTestHooks.java`（`prefixGameTestTemplate`） |
| `@GameTestHolder` 类会以 `initialize=true` 加载（因此静态初始化块会在注册期执行） | `GameTestHooks.java`（`Class.forName(..., true, ...)`） |
| 无头服务器里可以拿到**真实 ServerPlayer**：`GameTestHelper.makeMockServerPlayerInLevel()` 会构造 `ServerPlayer` 并通过 `PlayerList.placeNewPlayer` 放入世界（方法已标记 deprecated-for-removal，测试里用 `@SuppressWarnings("removal")`） | `gametest/framework/GameTestHelper.java:288-310` |
| 断言/调度 API：`succeed()` / `fail(String)` / `assertTrue(boolean,String)` / `assertFalse` / `runAfterDelay(long,Runnable)` / `getBounds()` / `getLevel()` | `GameTestHelper.java:777-916` |

## D. Gradle 构建/测试入口

| 事实 | 证据 |
|---|---|
| worker 进程的 classpath 被写入 UTF-8 `@argfile`，JVM 按平台 ANSI 代码页解析 → 含中文路径时 classpath 损坏 | 见 §8（反汇编 `ApplicationClassesInSystemClassLoaderWorkerImplementationFactory`） |
| 关掉原生文件监视可消除 `File watcher server ... NativeException` 警告 | `org.gradle.vfs.watch=false`（Gradle 官方属性），本机实测警告数 0 |
| `TestExecutionSummary` 提供 `getTestsAbortedCount()` / `getContainersAbortedCount()` / `getContainersFailedCount()` / `getTestsSkippedCount()` 等计数，可用于严格退出码判定 | `junit-platform-launcher` 接口（本机 5.9.2，`javap` 核对） |
| 被 `enabled = false` 的 Gradle 任务，其 `dependsOn` 依赖**仍会执行** | 本机实测：`gradlew test` 输出 `> Task :ebTest` 后 `> Task :test SKIPPED`，套件真实运行 |

---

# 返工第 2 轮补充取证

## E. 原版末影龙的延迟死亡阶段（本轮最关键的一手证据）

| 事实 | 证据 |
|---|---|
| 非坐姿阶段受到**玩家**致命伤害时，先 `reallyHurt`（内部 `die()`、血量归零、发出 `LivingDeathEvent`），**紧接着把血量恢复成 1 并切到 `DYING` 阶段** | `EnderDragon.java:532-538` |
| 头部以外的部位伤害会被削弱（`damage / 4 + min(damage,1)`），且 `DYING` 阶段免疫后续伤害 | `EnderDragon.java:520-531` |
| `DYING` 阶段的 `doServerTick`：**距离目标 ≥10 且 ≤150 且无碰撞时把血量保持为 1**，否则归零 | `phases/DragonDeathPhase.java`（`setHealth(1.0F)` / `setHealth(0.0F)` 分支） |
| 死亡阶段只作用于"未死亡"分支：`aiStep` 中 `if (isDeadOrDying()) {仅粒子} else {…doServerTick()}` —— 血量归零后阶段不再推进 | `EnderDragon.java:216-266` |
| `tickDeath()` 累计 `dragonDeathTime`，**200 tick 时 `remove(RemovalReason.KILLED)`** | `EnderDragon.java:580-624` |
| `EnderDragon`/`WitherBoss` **都没有覆写 `die()`**（走 `LivingEntity.die()`） | 全库检索无 `public void die` |
| 新生成的龙默认阶段是 `HOVERING`，而 `DragonHoverPhase.isSitting()` 返回 **true**；要复现缺陷必须先置于非坐姿阶段（如 `HOLDING_PATTERN`） | `EnderDragonPhaseManager` 构造器 + `phases/DragonHoverPhase.java:23` |

> 结论：**必须保留候选并在血量 1 期间继续等待**；只在"终局血量"或"KILLED 移除"时入账，
> 且绝不能修改 Boss 的姿态/血量/阶段。

## F. "死亡是否成立"的可观察事实

| 事实 | 证据 |
|---|---|
| `die()` **先**调用 `CommonHooks.onLivingDeath(...)`，被取消则立即 return（此时血量已经是 0） | `LivingEntity.java:1408-1410` |
| `isDeadOrDying()` 只看血量；`isAlive()` = `!isRemoved() && getHealth() > 0` | `LivingEntity.java:1134-1136, 1631-1633` |
| 移除原因读取用 `Entity#getRemovalReason()`（不是 `removalReason()`） | `Entity.java:358, 3642` |
| `IAttachmentHolder#getData` 在缺省时**会把默认值写入 holder**，所以"存在性"`hasData` 不等于"值为真" | `attachment/IAttachmentHolder.java:39`；Codex 的 `readingFalseCreditMarkerCannotSuppressRealKill` 即此缺陷 |
| `AttachmentHolder#saveAttachments` 只序列化带 `serializer` 的类型 → 非持久 attachment 不会写档、不会跨实体串味 | `attachment/AttachmentHolder.java:130` |

## G. GameTest 结构与区块 ticking（挂起/flaky 的根因）

| 事实 | 证据 |
|---|---|
| 测试结构被放在**随机位置**（±14.9M），并靠 `level.setChunkForced(...)` 让覆盖区块进入 entity-ticking | `GameTestServer.java:240-250`；`StructureUtils.java:167-169` |
| 测试开始前 `GameTestInfo#tick` 会等待结构 bbox 的**每个**相交区块 `isPositionEntityTicking`；**该等待没有超时** → 区块永不 ticking 时"一个测试都不开始" | `GameTestInfo.java:95-110` |
| 每个批次结束时 `GameTestRunner` 会把**全部**强制加载区块取消（`getForcedChunks()` → `setChunkForced(..., false)`） | `GameTestRunner.java:102-103, 121-122` |
| 实体是否被 tick 取决于**所在 section 的 Visibility**；section 由 `chunkVisibility`（默认 `HIDDEN`）初始化 | `PersistentEntitySectionManager.java:47-48, 95-102, 314-318`；`EntitySectionStorage.java`（`createSection` 读同一张表） |
| 区块一旦进入 ticking，`updateChunkStatus` 会把该区块内已存在的实体补登记进 tick 列表（因此"先 spawn 后强制加载"也能补救） | `PersistentEntitySectionManager.java:152-165` |
| `EntityTickList` 按**实体 ID** 索引（`add`/`remove` 均为 `put`/`remove`） | `EntityTickList.java` |
| 实测诊断：结构**原点区块** `isPositionEntityTicking=true`，而结构**跨过去的相邻区块** `isPositionEntityTicking=false`，其中 spawn 的实体 `tickCount` 恒为 0 | 本项目诊断运行的失败断言（见 `result-phase1-rework2.md` §2.1） |

> 结论：测试夹具必须（a）每次从**全新世界**开始，（b）避免多批次布局带来的"取消全部强制加载"窗口，
> （c）显式强制加载结构**实际覆盖**的区块（外扩一圈），（d）给大尺寸 Boss 用足够大的结构，
> （e）给运行任务加硬性超时，并对依赖 tick 的断言做有界轮询 + 状态诊断。
