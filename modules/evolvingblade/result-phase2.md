# PHASE 2 交付报告 —— 噬星之刃（Evolving Blade）

> **注意**：本文件的 §8"未完成"清单中的 **第 4 条 tooltip 权威化**、以及来源事实/速率限制相关的
> 未完成项已在 **`result-phase2-rework1.md`** 中修复（含 7 条新的实体级 GameTest）。
> 本文件保留为 PHASE 2 首次交付的历史记录；两者冲突时以 rework 报告与当前源码为准。

> 执行方：DeepSeek Harness（唯一实现者）。独立验收方：Codex。
> 目标版本：Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21 / ModDevGradle 2.0.81。
> 产物：`build/libs/evolvingblade-1.0.0.jar`（141,829 字节，108 个条目，内容哈希 `088F1CFCD78CF29842C909009C312A74DA67CADBCABEEE1C09751BC084563BF4`）。
> 本报告只写**实际执行过并看到结果**的内容；未执行的项在"未测试"一节逐条列出，不做任何"应该没问题"的推断。

---

## 1. 本次实际运行的命令与结果

| 命令 | 结果 |
|---|---|
| `gradlew clean build --console=plain` | **BUILD SUCCESSFUL**（2 s，含 `ebTest`） |
| `gradlew ebTest --console=plain` | `EBTEST tests=79 succeeded=79 failed=0 aborted=0 skipped=0 containersFailed=0 -> exitCode=0` |
| `gradlew runGameTestServer`（第一次） | **失败**，退出码 17：`UnsupportedOperationException: Payload evolvingblade:state may not be sent to the client!`，16 个用例失败 |
| （修复后）`gradlew runGameTestServer` | `All 19 required tests passed :)`，**BUILD SUCCESSFUL** |
| （删除废弃占位贴图后）`gradlew clean build` + `gradlew runGameTestServer` | 79/79 JUnit 通过；`All 19 required tests passed :)`；**BUILD SUCCESSFUL** |
| release jar 内容扫描（ZipFile 列举） | 108 条目；**无** GameTest 类、**无** `.nbt` fixture、**无** tools/ 生成器、**无**旧的 `star_eater.png` 占位贴图 |

对照 PHASE 1 基线：JUnit 73 → **79**（新增 `CombatPhase2Test` 6 条纯 JVM 回归）。

## 2. GameTest 结果与它抓到的真实缺陷

最终结果：`All 19 required tests passed :)`（`gradlew runGameTestServer`，BUILD SUCCESSFUL）。

**第一次运行是失败的，并且抓到了一个真实缺陷（这正是它存在的意义）**：

```
[Server thread/ERROR] Exception caught during firing event: Payload evolvingblade:state may not be sent to the client!
java.lang.UnsupportedOperationException: Payload evolvingblade:state may not be sent to the client!
...
16 个用例失败（liveentitysyntheticdeathisnotcredited / nullmodeisrejected / terminaldeathcreditsexactlyonce ...）
```

根因（一手源码核实，`NetworkRegistry.java:435-447`）：NeoForge 在发送 `ClientboundCustomPayloadPacket` 前会调用
`NetworkRegistry.checkPacket`，只有该连接的**已协商频道集合**包含该 payload id 时才允许发送。
GameTest 用 `helper.makeMockServerPlayerInLevel()` 造出的模拟玩家**没有经过 NeoForge 握手**，
因此没有 `evolvingblade:state` 频道，盲发即抛异常；而这个异常发生在服务器 tick 的事件派发里，
把同 tick 的其它测试一起带崩（16 个失败）。

修复（`EBPayloads.sendState`）：

```java
if (player == null || player.connection == null) return;
if (!player.connection.hasChannel(StateSnapshot.TYPE)) return;   // ICommonPacketListener#hasChannel
PacketDistributor.sendToPlayer(player, snapshotFor(player, giftDelivered));
```

这同时也是**面向真实环境的正确行为**：尚未完成握手、或没有安装本模组的连接不应该被盲发自定义包。
修复后 19/19 全绿，并连续两次运行均通过（含最终冻结版本）。

> 说明：GameTest 套件本身是 PHASE 1 由 Codex 建立的击杀入账/Boss 分类回归，
> 它**没有**覆盖 PHASE 2 新增的剑气/爆发/吸血/斩 Boss 能力（按任务安排属于 PHASE 3）。
> 本次它之所以对 PHASE 2 有价值，是因为 PHASE 2 的同步代码被挂进了登录/重生/阶段变化路径。

## 3. 控制方式（玩家可见行为）

| 操作 | 行为 |
|---|---|
| 首次登录（尚未选择路线） | 自动弹出"三选一"修行卡界面，**每次登录最多自动弹出一次**；关闭后不再自动弹 |
| 点一张卡 → 「确认选择」 | 弹出**第二次确认**界面，明确写出"选择「X」后将永久锁定，之后无法更改"；确认才发送 C2S |
| 取消 / ESC | 回到三选一界面或关闭；不会发任何包，也不会锁定 |
| `G` 键（可改键） | 未选择时打开三选一界面；已选择时打开成长仪表盘；再按一次/ESC 关闭 |
| 右键（主手持剑） | 阶段 2+ 释放剑气 |
| Shift + 右键 | 阶段 4+ 释放裂空爆发 |
| 满蓄力主手近战命中 | 阶段 3+ 吸血；阶段 5+ 对 Boss 追加斩击加成；阶段 1+ 用剑击杀回血 |

## 4. 权威性模型（服务器唯一权威）

- **C2S 只有一条意图**：`evolvingblade:choose_mode`，载荷是一个受校验的枚举（`quick`/`standard`/`long`），
  通过 `GrowthMode.require()` 解析，非法值直接抛错丢弃。客户端**不能**提交计数、阶段、伤害、赠剑标记。
- **S2C 是服务器推导的快照**：`evolvingblade:state`，含模式、阶段、计数、双门槛、八阶段路线、
  各能力解锁阶段与**来自服务器配置**的数值、当前冷却剩余。客户端不计算任何成长/伤害数值。
- **原子选择**：`GrowthService.chooseMode(ServerPlayer, GrowthMode)` 在服务器主线程执行
  "拒绝重复 → 写模式 → 精确一次赠剑 → 回发权威快照"。背包满时保持 `PENDING` 并显示"待发放"提示，
  不会给客户端任何"成功"假象；已锁定的玩家再发包只回发真实状态，不改变任何数据。
- **冷却持久化**：剑气/爆发冷却保存为玩家数据里的**绝对到期 tick**（`wave_ready_at`/`burst_ready_at`，
  随玩家数据序列化并 `copyOnDeath`），换剑、换维度、重连、死亡都无法绕过。
  全模组统一使用**主世界 `gameTime`**（`StarEaterAbilities.clock()`）创建与比较截止时间。
- **阶段属性**：物品自带属性是阶段 0 基线；当前持有者的阶段差值以**玩家身上的 transient 修饰符**
  施加（`AttributeInstance.addOrUpdateTransientModifier`），卸载即移除；只移除本模组自己的修饰符 id，
  不触碰原版/其它模组的修饰符与附魔。`evolvingblade:stage` 数据组件只驱动模型与 tooltip 显示。
- **攻击时序**：`AttackEntityEvent`（HIGHEST）里先 `StageAttributes.reconcile(player)` 再
  `player.getAttackStrengthScale(0.5F)` 采样满蓄力——对应 `Player.attack()` 中 1223 行事件先于
  1226 行读取 ATTACK_DAMAGE、1230 行采样蓄力的顺序，因此不依赖周期性 tick 是否跑过。
- **来源事实（击杀回血）**：`SwordHit(player, gameTime)` 记录在**受害者实体**上，且只在伤害
  **真的由本剑造成**时写入：主手近战直击（同一 tick 的攻击上下文 + 直击实体就是玩家本人）或本模组技能伤害类型。
  弓/箭等其它武器命中不会冒充来源；用剑命中后换下剑也不会抹掉来源。空值用哨兵 `SwordHit.NONE`
  （attachment 默认值必须非空），`isStillValidFor` 对 NONE 与"未来时间戳"都返回 false。
- **吸血**：在 `LivingIncomingDamageEvent`（HIGHEST）记录主手目标的命中前生命值，
  在 `LivingDamageEvent.Post`（LOWEST）用"命中前 − 结算后"计算实际损失并取比例上限；
  只对满蓄力主手近战直击生效，技能伤害与其它来源不参与，因此不会递归。
- **斩 Boss 加成**：自定义伤害类型 `evolvingblade:boss_slayer`，只写进
  `minecraft:bypasses_armor` 与 `minecraft:bypasses_cooldown` 两个数据包标签（只绕过护甲与受击冷却），
  **不修改实体的 `invulnerableTime` 等可观察状态**，不绕过无敌/抗性/模组事件取消，不直接 setHealth/kill。
  每玩家+目标 20 tick 速率限制保存在 `evolvingblade:boss_rate` attachment 上，**带 codec 且 copyOnDeath**，
  有界（上限 256，超出淘汰最早就绪项），因此不能靠重生/换维度/重连绕过。
- **剑气**：逐 0.5 格步进做方块 clip（撞墙即停，不穿墙）；对采样球命中的候选**再验证真实视线与射程**
  （避免"墙后目标被采样球命中"）；最多命中父实体数改为**含计数语义**（`waveMaxTargets`：普通 1，终阶 = 配置值 3），
  以 `hits >= maxTargets` 停止；只接受敌对/Boss，排除玩家、任何有主人的生物（含他人宠物）、友军。
- **爆发**：视线过滤后按距离取最近 N 个（默认 32）施加伤害，不破坏地形、不产生爆炸，不触碰受击冷却。
- **同步时机**：登录、重生、换维度、管理员改模式/改计数、阶段变化、以及"手持本剑或冷却未走完"时
  每秒一次的有界冷却同步。登录包若早于客户端世界/玩家就绪，客户端**只排队**一次性弹窗，
  等 `mc.level`/`mc.player` 就绪且没有其它界面时才弹出；周期性的 `mode=null` 同步不会重复弹窗。

## 5. 新增与修改的文件

**新增（PHASE 2）**

- `src/main/java/com/evolvingblade/combat/CombatMath.java`（纯 JVM：满蓄力阈值、伤害、吸血、Boss 加成、
  冷却、`waveMaxTargets`、射线采样、最近目标）
- `src/main/java/com/evolvingblade/combat/AbilityState.java`（纯 JVM：就绪判定、到期 tick、每目标速率限制与清理）
- `src/main/java/com/evolvingblade/combat/SwordHit.java`（纯 JVM：来源事实 + 空值哨兵 + 窗口判定）
- `src/main/java/com/evolvingblade/combat/StageAttributes.java`（阶段差值修饰符的唯一写入口）
- `src/main/java/com/evolvingblade/combat/StarEaterAbilities.java`（剑气/爆发/斩 Boss 加成/目标合法性/统一时钟）
- `src/main/java/com/evolvingblade/event/EBCombatHandler.java`（攻击时序、来源、吸血、右键技能、周期协调与同步）
- `src/main/java/com/evolvingblade/network/EBPayloads.java`（C2S 枚举 + S2C 快照；1.21.1 载荷注册）
- `src/main/java/com/evolvingblade/registry/EBDataComponents.java`（`evolvingblade:stage` 显示组件）
- `src/main/java/com/evolvingblade/client/EBClient.java`（`@Mod(dist = CLIENT)`：G 键、模型属性、客户端 tick 调度）
- `src/main/java/com/evolvingblade/client/EBClientState.java`（快照缓存 + 一次性弹窗队列）
- `src/main/java/com/evolvingblade/client/ChoiceScreen.java`（三选一 + 第二次锁定确认）
- `src/main/java/com/evolvingblade/client/DashboardScreen.java`（可滚动仪表盘）
- `src/main/java/com/evolvingblade/client/ModeCardText.java`（全部文本行只读服务器快照）
- `src/test/java/com/evolvingblade/combat/CombatPhase2Test.java`（6 条纯 JVM 回归）
- `tools/GenerateSwordTextures.java`（程序化像素画生成器，纯 JDK，无第三方图片库）
- 资源：`assets/evolvingblade/textures/item/star_eater_stage0..7.png`（8 张 32×32 透明 PNG）、
  `assets/evolvingblade/models/item/star_eater_stage0..7.json`、
  `data/evolvingblade/damage_type/{sword_ability,boss_slayer}.json`、
  `data/minecraft/tags/damage_type/{bypasses_armor,bypasses_cooldown}.json`、
  `lang/{zh_cn,en_us}.json` 全量新增键。

**修改（PHASE 1 → PHASE 2）**

- `EvolvingBlade.java`：注册数据组件、注册网络载荷；删除"PHASE 2 才会实现"的过时说明。
- `growth/GrowthData.java`：记录追加持久化的 `waveReadyAt`/`burstReadyAt`（含旧构造器兼容）。
- `growth/GrowthService.java`：新增 `chooseMode`（原子选择 + 赠剑 + 快照）、`sendSnapshot`、
  阶段变化才补发；`forceMode`/`setCounters` 管理员改动后同样补发。
- `event/EBPlayerLifecycleHandler.java`：登录下发快照；重生、换维度延迟一 tick 补发。
- `event/EBKillCreditHandler.java`：击杀回血改为按"最后一次有效剑命中"判定（读 `SwordHit`）。
- `registry/EBAttachments.java`：`SWORD_HIT` 默认值改为非空哨兵；`BOSS_RATE` 增加 codec 与 `copyOnDeath`。
- `item/StarEaterItem.java`：写入 `stage` 显示组件、tooltip 增加"当前持有者阶段"。
- `config/EBConfig.java`、`growth/Abilities.java`：删除"PHASE 2 才生效"的过时注释。
- 删除 `EvolvingBladeClient.java`（PHASE 1 的客户端入口），由 `client/EBClient.java` 取代，避免重复注册。

## 6. 美术

- 8 张 **原创、程序化生成**的 32×32 透明像素剑（`star_eater_stage0..7.png`，164–271 字节），
  轮廓长度、剑刃宽度、配色（灰 → 钢蓝 → 灵青 → 冰蓝 → 紫 → 金 → 终焉粉金）与灵光/星屑逐级增长；
  生成器 `tools/GenerateSwordTextures.java` 只依赖 `java.base`，**不使用任何下载素材**。
- 模型：`star_eater.json` 以 `item/handheld` 为父，用 `evolvingblade:stage` 谓词做 7 级 `overrides`
  指向 8 个模型，因此**不同持有者看到各自真实阶段**（组件由服务器在协调属性时写入）。
- 物品保持不可破坏、抗火（PHASE 1 已实现并有 tooltip 文案），配方与创造页签未改动。

## 7. 实测通过的内容（明确列出）

1. `gradlew clean build` 成功；生产源、测试源、GameTest 源全部编译通过。
2. JUnit 79/79 通过（含新增 6 条：`waveMaxTargets` 含计数语义与停止规则、
   `SwordHit.NONE` 与窗口边界、每目标速率限制跨 tick/按目标/有界）。
3. release jar 内 **无** GameTest 类与 `.nbt` fixture（已用 ZipFile 逐条核对）。
4. 8 张贴图、8 个模型、2 个伤害类型、2 个数据包标签、双语 lang 均进入 jar（已逐条列出核对）。
5. `runGameTestServer`：`All 19 required tests passed :)`（PHASE 1 回归套件，在 PHASE 2 代码挂上同步路径后仍然全绿）；
   过程中的一次真实失败与修复见 §2 / §9。
6. 资源一致性（用脚本核对，不是"看起来对"）：
   - `src/main/resources` 下 18 个 JSON **全部可解析**；
   - 双语 lang **键集合完全一致**（zh 134 / en 134，无缺失、无空值）；
   - `star_eater.json` 的 7 条 `overrides` 全部指向**存在**的模型文件，基础层为 `evolvingblade:item/star_eater_stage0`。

## 8. 未测试 / 未完成（诚实清单）

**未测试（本阶段没有可无头执行的验证手段）**

- **客户端界面观感**：三选一界面、第二次确认、仪表盘滚动、854×480（guiScale 2）与 1920×1080 下的实际排版，
  只做了代码级尺寸推导，**没有真机渲染验证**，重叠/截断需人工确认。
- **真实多人 / 完整登录流程**：`G` 键、自动弹窗一次性、赠剑满背包提示、重复包的界面表现均未真机走查。
- **专服冷启动**：本阶段未运行 `runServer`，未验证专用服务器上"不加载客户端类"的实际日志
  （代码层面已用 `@Mod(dist = CLIENT)` 与 `FMLEnvironment.dist` 守卫，但这是**代码级**结论）。
- **持久化跨重启**：`BOSS_RATE` 的 codec/copyOnDeath、冷却跨重启未做实际存档往返测试。
- **新能力的 GameTest**：剑气（墙/射程/穿透上限）、爆发（上限/视线）、吸血（真实生命损失）、
  斩 Boss 加成（护甲/冷却/事件取消）、击杀回血的换武器场景，按任务安排属于 **PHASE 3**；
  本阶段只有纯 JVM 数学回归，**没有**实体级验证。
- **与其它 mod / 附魔共存**：未在装有多 mod 的实例中验证。
- **客户端入口的运行时行为**：`EBClient` 的 `@Mod(dist = CLIENT)` 构造、`G` 键注册、
  `evolvingblade:stage` 模型谓词、NeoForge 配置界面（`ConfigurationScreen`）注册都**只验证了能编译**，
  没有启动过 `runClient`，因此"客户端真的能加载这些注册"这一点属于**需真机确认**。

**其它已知细节（不是缺陷，但需要验收方知道）**

- `AbilityState.tryConsumeRate` 的 `maxEntries` 是**内存上限**而非"拒绝阈值"：调用开始时先把表裁剪到上限，
  本次新写入可能使其短暂达到 `maxEntries + 1`，下一次调用立刻回收；测试已按这个真实契约断言（`<= maxEntries + 1`）。
- 快速/标准/长期三种模式的**终局门槛不同**，但阶段数值曲线相同；界面上显示的终局门槛来自服务器快照，
  不硬编码。

**未完成（Codex `_agent-team/live-review.md` 第 4 条的 tooltip 权威化）**

- 物品 tooltip 目前仍会显示物品自带的**阶段 0 基线**属性行（7 / 1.6），旁边另有一行
  "当前持有者阶段：X"。也就是说：**没有**隐藏基线属性行、**没有**在 tooltip 里显示当前持有者的
  权威伤害/攻速。这是已知未完成项，未用"应该是这样"糊过去。

**与 Codex 观察的逐条对应**

| live-review 条目 | 处理 |
|---|---|
| 1 `BOSS_RATE` 未持久化 | **已修**：加 codec + `copyOnDeath`，有界并清理（未做实际存档往返测试） |
| 2 `SWORD_HIT` 默认值返回 null | **已修**：改为显式哨兵 `SwordHit.NONE` + `isPresent()` |
| 3 不得信任物品上的 stage/属性作为强度来源 | **已满足**：强度只由玩家数据推导的 transient 修饰符决定；只清理本模组 id |
| 4 tooltip 权威显示 | **未完成**（见上） |
| 5 `wavePierce` 语义与墙后命中 | **已修**：`waveMaxTargets` 含计数 + `hits >= max`；候选再验证视线与射程 |
| 6 应排除所有被驯服的动物 | **部分**：改为排除**任何 owner 非空**的 `OwnableEntity`（覆盖原版所有驯服宠物）；
  原版 `TamableAnimal.isTame()` 的显式判断在本次编译环境中**无法解析该类**，故未使用，详见 §10 |
| 7 不得清零 `invulnerableTime` | **已修**：剑气/爆发/斩 Boss 加成都不再修改该字段；改用 `bypasses_cooldown` 标签 |
| 8 统一时钟 | **已修**：`StarEaterAbilities.clock()` = 主世界 `gameTime`，创建/比较/清理全部统一 |
| 9 来源事实 | **已修**：只在"本剑真实造成伤害"时写入（主手直击同 tick 上下文，或本模组技能类型）；技能伤害也写来源 |
| 10 主手上下文过期 | **已修**：`PRIMARY` 上下文要求 `gameTime - melee.gameTime() <= 1`，过期即清除，取消的攻击不会沿用旧记录 |

## 9. 运行记录（按时间顺序，含失败）

1. `gradlew compileJava` / `compileTestJava` / `compileGametestJava`：逐步修完编译错误后全部通过。
2. `gradlew ebTest`：第一次 78/79（我自己的新测试对速率表上限的断言写错了），改正断言后 **79/79**。
3. `gradlew clean build`：BUILD SUCCESSFUL，产出第一个 jar（142,109 字节，hash `D22781FB…`）。
4. `gradlew runGameTestServer`：**失败（exit 17）**，抓到 `Payload evolvingblade:state may not be sent to the client!`
   （详见 §2），16 个用例失败。
5. 修复 `EBPayloads.sendState` 的频道守卫后重跑：`All 19 required tests passed :)`，BUILD SUCCESSFUL。
6. 删除 PHASE 1 遗留的未使用占位贴图 `textures/item/star_eater.png`（模型已改用 `star_eater_stage0..7`），
   重新 `gradlew clean build`（79/79）并再次 `runGameTestServer`：`All 19 required tests passed :)`，BUILD SUCCESSFUL。
7. 对**最终 jar**重新做内容哈希与条目核对：141,829 字节 / 108 条目 / `088F1CFCD78CF29842C909009C312A74DA67CADBCABEEE1C09751BC084563BF4`；
   扫描确认 jar 内不含任何 GameTest 类、`.nbt` fixture、生成器源码或占位贴图。

## 10. 关于 `TamableAnimal` 的更正（rework 1）

上一版报告里"编译类路径无法解析 `TameableAnimal`"的说法是**我自己的拼写错误**：
正确类是 `net.minecraft.world.entity.TamableAnimal`（我写成了 `TameableAnimal`），
源码位于 `.reference-mcsrc/net/minecraft/world/entity/TamableAnimal.java`，
且 `AbstractHorse` 同样实现了 `OwnableEntity`。
现已按正确类名加入 `TamableAnimal.isTame()` 判定，与"owner 非空"判定**同时**存在，
并被 GameTest `tamedTargetsAreExcludedEvenWithoutOwner` 锁定（含"已驯服但 owner 为 null"这一情形）。
详见 `result-phase2-rework1.md`。
