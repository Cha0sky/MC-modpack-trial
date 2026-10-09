# PHASE 1 返工第 1 轮报告 — 六项审查缺陷的修正与回归结果

> **本文件已被 `result-phase1-rework2.md` 取代。** 第 1 轮交付经 `phase1-death-edge.md` 复核后
> 仍未被接受（原版飞行末影龙击杀会丢失、取消契约不够严格、去重依赖 TTL/容量、缺少"不使用原版
> DYING 姿态"与"立即 KILLED 移除"的覆盖），相关实现已在第 2 轮重做。本文件保留为历史记录，
> **其中的实现细节（例如 `KillCreditState`、`Pose.DYING` 判定、4096 条容量上限）不再代表当前源码**；
> 请以 `result-phase1-rework2.md` 为准。

> 触发原因：Codex 对首轮提交 `result-phase1.md` 的独立源码审查
> （`../_agent-team/phase1-review-notes.md`）指出 6 项违反 SPEC 的缺陷。
> 本文件记录每一项的**确切修正**与**真实回归结果**。
>
> **结论：6 项全部修正，且都有可复现的回归覆盖。**
> JUnit 67/67 通过；NeoForge GameTestServer 8/8 通过（真实无头服务器）；
> `gradlew build` 只执行一次套件；`gradlew test` 会真正执行套件。
> UI/战斗能力仍按计划留给 PHASE 2。

---

## 0. 本轮改动的文件

| 动作 | 文件 |
|---|---|
| 新增 | `src/main/java/com/evolvingblade/growth/KillCreditState.java`（纯 Java 入账状态机） |
| 新增 | `src/main/java/com/evolvingblade/growth/GiftDelivery.java`（纯 Java 空槽位查找） |
| 重写 | `src/main/java/com/evolvingblade/event/EBKillCreditHandler.java`（两阶段提交） |
| 重写 | `src/main/java/com/evolvingblade/growth/GrowthService.java`（赠剑 + null 模式） |
| 修改 | `src/main/java/com/evolvingblade/growth/GrowthData.java`（`withMode` 拒绝 null） |
| 修改 | `src/main/java/com/evolvingblade/boss/BossEventProbe.java`（缓存键含深度、只吞反射异常） |
| 修改 | `src/test/java/com/evolvingblade/test/EBTestRunner.java`（严格退出码） |
| 删除 | `src/main/java/com/evolvingblade/util/KillCreditLedger.java` 及其测试（被取代） |
| 新增 | `src/gametest/**`（GameTest 源集：8 个服务器集成测试 + 空结构 NBT） |
| 新增 | `tools/GenerateGameTestStructure.java` |
| 修改 | `build.gradle` / `gradle.properties`（gametest 源集、test→ebTest 依赖、关闭文件监视） |
| 文档 | `README.md`、`docs/API-NOTES-phase1.md`、`result-phase1.md`（标注被取代） |

---

## 1. 被取消 / 延后提交的真实死亡（缺陷 1）——已修正

### 修正

`EBKillCreditHandler` 改为**两阶段提交**：

1. **LOWEST 优先级捕获候选**（`@SubscribeEvent(priority = EventPriority.LOWEST)`）：
   等其它模组的 `LivingDeathEvent` 处理器全部跑完后，才把这次死亡记为候选；
   `event.isCanceled()` 为真则直接忽略。**归属事实在此刻固化**：伤害来源解析出的玩家 UUID、
   宠物/弹射物主人链、以及 Boss 分类结果，一并存入 `PendingDeath`。
2. **本 tick 末核实后入账**（`ServerTickEvent.Post`，同样 LOWEST）：只有实体
   **真的进入终局**才入账：

```java
!victim.isRemoved() && !victim.isAlive() && victim.isDeadOrDying() && victim.getPose() == Pose.DYING
```

### 为什么必须加 `Pose.DYING`（本轮最重要的技术发现）

1.21.1 的 `LivingEntity.die()`（`LivingEntity.java:1408-1410`）**第一件事**就是调用
`CommonHooks.onLivingDeath(...)`，被取消时**立即 return**——此时血量已经是 0，
但不置 `dead`、不掉落、不设置姿态。而 `isDeadOrDying()` 在 1.21.1 **只看血量**
（`LivingEntity.java:1134-1136`），所以**单靠血量无法区分"真死"与"死亡被取消"**。
`protected boolean dead` 没有公开读取方式，`deathTime` 也因为 `tickDeath()` 由血量触发而同样无效。

`Pose.DYING` 是唯一干净、公开、无需反射的终局信号：全代码库中它**只在**
`die()` 走完取消检查之后的 `this.setPose(Pose.DYING)`（`LivingEntity.java:1437`）被设置一次，
没有任何地方会改回去。取证详见 `docs/API-NOTES-phase1.md` §A。

### 回归覆盖

**GameTest（真实无头服务器）**：

| 用例 | 断言 |
|---|---|
| `terminalDeathCreditsExactlyOnce` | 真实死亡恰好入账 1 次；实体处于 `Pose.DYING`；对同一具仍在场的尸体**再补发一次**死亡通知后，击杀数仍为 1 |
| `cancelledDeathIsNotCredited` | 一个 **LOW 优先级**监听器取消死亡 → 实体不进入 `DYING` 终局姿态，击杀数 0 |
| `liveEntitySyntheticDeathIsNotCredited` | 对**活着的**实体手动 `EVENT_BUS.post(new LivingDeathEvent(...))` → 实体仍存活，击杀数 0（该用例同时走通了"候选排队 → tick 末核实拒绝"这条路径） |
| `environmentalDeathIsNotCredited` | 无归属伤害击杀 → 击杀数 0，但死亡本身正常发生 |

**JUnit（纯 Java）**：`KillCreditStateTest` 覆盖占位/入账/释放/重复拒绝。

---

## 2. 混合时钟与有界去重（缺陷 2）——已修正

### 修正

- **统一时钟**：所有时间戳都取 `server.overworld().getGameTime()`（与维度无关）。
  清理**不再接收时间参数**，从根本上消除了"记录用一个时钟、清理用另一个时钟"这一整类缺陷。
- **去重标记按"实体的一条命"保留**：`KillCreditState` 只在实体**真正离开世界**时移除标记
  （外加 4096 条的内存硬上限，按入账时间从旧到新丢弃）。
  旧的"固定 20 tick 窗口"已删除，因此长时间留存的尸体（末影龙长死亡动画）
  在 20 tick 之后重复收到通知**也不会**二次入账。
- **服务器停止清理**：`ServerStoppedEvent` 中 `STATES.remove(server)`，状态不跨服务器残留。

### 回归覆盖

| 层 | 用例 | 断言 |
|---|---|---|
| JUnit | `KillCreditStateTest.lingeringCorpseNeverDoubleCredits` | 实体持续"存在"并模拟每秒清理达 **1 小时**，标记仍在；重复 `tryReserve` / `markCreditedOnce` 全部返回 false |
| JUnit | `pruneWhenEntityGone` / `maxEntriesBound` / `clearRemovesEverything` / `nullSafety` | 实体消失即清理；超限收敛到上限；服务器停止后无残留 |
| GameTest | `terminalDeathCreditsExactlyOnce` | 真实服务器上"同一具尸体补发通知不二次入账" |

---

## 3. 创造模式满背包赠剑（缺陷 3）——已修正

### 修正

`GrowthService.tryDeliverGift` **不再使用 `Inventory.add()` 的返回值**。
取证：`Inventory.add(...)` 在 `player.hasInfiniteMaterials()`（创造模式）且放不下时，
会执行 `stack.setCount(0); return true;`，**实际什么都没放进背包**
（`Inventory.java:266-268` 与 `283-286`）。

新流程：

1. `GiftDelivery.findFreeSlot(Inventory.INVENTORY_SIZE /*36*/, i -> inv.getItem(i).isEmpty())`
   只在**主背包 36 格**里找真正为空的槽位（盔甲/副手不参与，与 `getFreeSlot()` 的语义一致）；
2. 没有空位 → 保持 `PENDING`，**不产生任何掉落**（生存与创造一视同仁）；
3. 写入前后各复核一次，并在写入后确认该槽位**确实放着噬星之刃**，才写 `GRANTED`。

### 回归覆盖

**GameTest（真实 ServerPlayer）**：

| 用例 | 断言 |
|---|---|
| `fullSurvivalInventoryKeepsGiftPending` | `instabuild=false` + 36 格全满 → 交付失败、状态 `PENDING`、背包 0 把剑、无掉落；腾出 1 格后重试 → `GRANTED` 且**恰好 1 把**；再调用两次仍为 1 把 |
| `fullCreativeInventoryKeepsGiftPending` | `instabuild=true` + 36 格全满 → 交付失败、`PENDING`、0 把剑、无掉落（**这正是 `add()==true` 的陷阱**）；腾出 1 格后重试 → `GRANTED` 且恰好 1 把 |

**JUnit**：`GiftDeliveryTest`（空背包/全满/首个空位/边界与非法入参）。

> 设计说明（有意为之，非缺陷）：交付只使用主背包 36 格，与 vanilla `Inventory.add`
> 的资源槽位语义一致；主背包满即视为"背包满"，保持 `PENDING` 并在玩家腾出空间后补发。

---

## 4. 服务边界的 null 模式（缺陷 4）——已修正

`GrowthService.selectMode` / `forceMode` 在触碰玩家之前 `Objects.requireNonNull(mode, "mode")`；
`GrowthData.withMode(null)` 同样抛 `NullPointerException`。
"未选择"只能作为初始状态存在，公开修改入口不能把已选模式改回 `null`。
`setCounters` 的数值边界校验被提到玩家对象校验**之前**，保证"负数一定被拒绝"与调用顺序无关。

回归：`GrowthServiceBoundaryTest`（5 个用例）+ GameTest `nullModeIsRejected`
（真实 ServerPlayer 上验证抛 NPE 且玩家仍处于"未选择"）。

---

## 5. 反射缓存契约（缺陷 5）——已修正

- 缓存键改为 `CacheKey(owner, fieldType, maxDepth)`：**先浅后深不会再命中不完整结果**。
- 只捕获**反射访问类异常**（`ReflectiveOperationException | RuntimeException`），
  不再 `catch (Throwable)`，因此不会吞掉 `OutOfMemoryError` 等 VM 致命错误。

回归（`BossEventProbeTest` 从 6 个用例增加到 8 个）：

| 用例 | 断言 |
|---|---|
| `cacheIsDepthSensitive` | 深度 0 → 空；深度 8 → 发现继承字段；深度 1 → 不含父类字段；**反向顺序（先深后浅）也各自正确** |
| `subclassFieldIsAssignable` | 字段声明为查询类型的**子类型**时必须命中；不相关类型不得误判 |
| 既有用例 | 非空字段、继承、类型过滤、类名兜底、有界日志 |

---

## 6. 构建 / 测试入口（缺陷 6）——已修正

| 要求 | 实现 | 实测证据 |
|---|---|---|
| 保留真正的 JUnit 引擎运行方式 | `ebTest`（`JavaExec` + `EBTestRunner`） | `EBTest tests=67 succeeded=67 failed=0` |
| `gradlew test` 在原生 worker 被禁用时**仍要跑套件** | `test` 保持 `enabled = false`，但 **`dependsOn ebTest`** | `gradlew test` 输出 `> Task :ebTest`（67/67）后 `> Task :test SKIPPED`，BUILD SUCCESSFUL |
| 运行器必须对**零发现 / 中止**失败 | `EBTestRunner.exitCode(...)` 纯函数：`testsFound<=0`、`testsSucceeded<=0`、`containersFound<=0`、任何 failed/aborted（用例或容器）都返回 1 | `EBTestRunnerExitCodeTest` 6 个用例（零发现、全跳过、有中止、容器中止/失败、有失败、全通过） |
| `build` 与 `test` 都恰好执行一次套件 | `check.dependsOn ebTest`，`test.dependsOn ebTest`（Gradle 任务图去重） | `clean build` 日志中 `> Task :ebTest` 出现次数 = **1** |
| 关闭本机不支持的文件系统监视 | `gradle.properties` 增加 `org.gradle.vfs.watch=false` | `clean build` 日志中 `File watcher` / `NativeException` 出现次数 = **0** |
| 保留 UTF-8 资源 | `generateModMetadata.filteringCharset = 'UTF-8'`；`JavaCompile.options.encoding = 'UTF-8'`；lang/结构文件均为 UTF-8 | 生成的 `neoforge.mods.toml` 中文描述正确，jar 内 lang 正确 |

---

## 7. 本轮新增：真实服务器集成测试（GameTestServer）

按建议使用 **NeoForge GameTestServer**（无头服务器 Dist）而不是需要 EULA 的专用服：
`GameTestServer extends MinecraftServer`（**不是** `DedicatedServer`），本机实测**无需**任何
`eula.txt` 配置即可启动。

- 独立源集 `src/gametest/`，通过 `neoForge.mods` 挂到同一个 mod id 下被 GameTest 框架发现；
  **`jar` 只打包 `sourceSets.main.output`，实测产物中不含任何 gametest 类或结构**
  （已用 `jar tf` 验证：无 `gametest` / `structure` / `empty.nbt` 条目）。
- GameTest 必须有结构模板，否则报 `Missing test structure`；因此提供
  `src/gametest/resources/data/evolvingblade/structure/empty.nbt`
  （9×5×9、一层基岩地板），由 `tools/GenerateGameTestStructure.java` 程序化生成，
  字段格式按 `StructureTemplate#load` 取证。
- 使用 `GameTestHelper.makeMockServerPlayerInLevel()`（真实 `ServerPlayer`，
  经 `PlayerList.placeNewPlayer` 入世界）——它不是 `FakePlayer`，因此不会被本模组的
  "拒绝 FakePlayer"规则挡住，可以端到端验证真实入账路径。
  该方法在 1.21.1 已被标记 deprecated-for-removal，测试中显式
  `@SuppressWarnings("removal")` 并记录在案。

**8 个 GameTest 全部通过**（真实服务器日志）：

```
[Server thread/INFO] [minecraft/GameTestServer]: 8 tests are now running at position -13711876, -59, -11688190!
[Server thread/INFO] [minecraft/GameTestServer]: ========= 8 GAME TESTS COMPLETE IN 1.497 s ======================
[Server thread/INFO] [minecraft/GameTestServer]: All 8 required tests passed :)
```

覆盖：4 个击杀入账（真死一次、LOW 取消、活体合成事件、环境死亡）、
2 个赠剑（生存满背包 / 创造满背包）、1 个 null 模式、1 个 attachment 读写与阶段计算。

---

## 8. 实际执行的命令与真实结果

所有命令在项目根目录执行；`JAVA_HOME` / `GRADLE_USER_HOME` 使用监督者设置的值，未改动。

| # | 命令 | 结果 |
|---|---|---|
| 1 | `.\gradlew.bat ebTest --console=plain` | **BUILD SUCCESSFUL**，`EBTEST tests=67 succeeded=67 failed=0 aborted=0 skipped=0 containersFailed=0 containersAborted=0 -> exitCode=0` |
| 2 | `.\gradlew.bat test --console=plain` | **BUILD SUCCESSFUL**；日志顺序为 `> Task :ebTest`（67/67）→ `> Task :test SKIPPED`，证明禁用的 test 任务仍会执行其依赖 |
| 3 | `.\gradlew.bat runGameTestServer --console=plain` | **BUILD SUCCESSFUL**；`8 tests are now running` → `All 8 required tests passed :)`（中途曾出现 1 个失败，见下） |
| 4 | `.\gradlew.bat clean build --console=plain` | **BUILD SUCCESSFUL**；`> Task :ebTest` 出现 **1** 次、67/67 通过；`File watcher`/`NativeException` 警告 **0** 次；产物 `build/libs/evolvingblade-1.0.0.jar` |
| 5 | `jar tf build/libs/evolvingblade-1.0.0.jar`（检查） | 65 个条目；无 `gametest` 类、无测试结构；含 `com/evolvingblade/growth/KillCreditState.class` 等新类 |

调试过程中的一次真实失败（已修正，保留记录以免"看起来一次就绿"）：
第一次 GameTest 运行报
`cancelleddeathisnotcredited failed ... 死亡被取消后实体必须仍然存活`。
失败原因是**测试断言写错**（1.21.1 中被取消的死亡血量已经是 0，`isAlive()` 必然为 false），
而这恰好暴露了"单靠血量无法区分取消与真死"这一实现缺陷；
两者都已按 §1 的方式修正（改为断言终局姿态 + 击杀数）。

产物指纹（用于比对部署物）：内容哈希（按条目名+字节排序后 SHA-256）
`361C52070888C1A67AF3636AA5F487301B58D1872D4F221A28A7A1EFDE222D52`，78,654 → 84,550 字节。

---

## 9. 测试总览（本轮）

| 测试类 | 用例数 |
|---|---|
| `growth.StageCalculatorTest` | 9 |
| `boss.BossClassifierCoreTest` | 10 |
| `boss.BossEventProbeTest` | 8（+2） |
| `growth.GrowthDataTest` | 7 |
| `growth.GrowthSettingsTest` | 7 |
| `growth.GrowthModeTest` | 4 |
| `growth.KillCreditStateTest` | 7（新增） |
| `growth.GiftDeliveryTest` | 4（新增） |
| `growth.GrowthServiceBoundaryTest` | 5（新增） |
| `test.EBTestRunnerExitCodeTest` | 6（新增） |
| **JUnit 合计** | **67** |
| **GameTest 合计** | **8（新增）** |

报告与日志：`build/test-results/ebTest/ebTest-summary.txt`、`docs/logs/`。

---

## 10. 关于"移除过期的 PHASE1/PHASE2 占位说明"

审查备注中的这一条附带条件为"**after later phases are implemented**"。
本轮 PHASE 2 尚未实现（GUI / 网络 / 战斗能力按用户指示仍留给 PHASE 2），
因此生产代码、配置注释与文档中关于"PHASE 2 才会实现"的说明目前**仍然准确**，
未做删除，以免出现"文档声称已实现、实际没有"的虚假说明。已核对：

- `EBConfig` 的 `[abilities]` 注释、`StarEaterItem` 的类注释、`GrowthService.requestGift`
  的注释都明确写着相应能力/触发在 PHASE 2；
- `result-phase1.md` 已在开头标注"已被本文件取代"，其中提到已删除的 `KillCreditLedger`
  的两处也加了明确说明；
- 客户端渲染/键位/网络包在源码中**不存在**任何实现或占位实现。

PHASE 2 完成后应回头清理这些阶段性措辞（已列入 §12）。

---

## 11. 未验证 / 残余风险（诚实清单）

1. **没有真人多人客户端验证**：GameTestServer 覆盖了服务器权威行为（入账、赠剑、attachment），
   但客户端渲染、GUI、网络同步属 PHASE 2，尚未实现也未验证。
2. **`makeMockServerPlayerInLevel()` 是 deprecated-for-removal 的官方测试辅助**：
   若未来升级到移除该方法的版本，GameTest 需要改为自行构造 `ServerPlayer` 并
   `PlayerList.placeNewPlayer`（源码已取证，可照抄）。
3. **真实凋灵/末影龙的击杀入账未实测**：它们走默认包含名单，不依赖反射；
   但"击杀真实 Boss 实体"这条路径仍未在服务器上跑过
   （GameTest 用普通敌对生物验证了同一套入账管线）。
4. **行为验证的边界**：GameTest 覆盖了"同一具在场尸体 10 tick 内重复通知"；
   "超过 20 tick 的重复通知"由等价的纯 JUnit 用例覆盖（实体持续存在 1 小时），
   因为原版僵尸尸体在 20 tick 后就会被移除、无法在服务器上构造长留存尸体。
5. 占位贴图仍是程序化生成的 32×32 像素图，**不是** SPEC 要求的 8 张正式美术（PHASE 2）。
6. `gradlew test` 会打印 `> Task :test SKIPPED`——这是刻意的（worker 在本机不可用），
   套件由它依赖的 `ebTest` 真正执行；若需要看到"test 通过"的字样，
   需在纯 ASCII 路径下检出（那时标准 `test` 任务本身可用）。

**没有任何已知的成长/赠剑逻辑缺陷被留待后续处理**；上表列出的都是验证范围与阶段性交付边界。

## 12. PHASE 2 待办（不变，另加一项清理）

1. 网络包（C2S 模式选择意图、S2C 权威快照/配置值）与服务器侧校验。
2. 首次登录 3 卡选择 GUI + 二次确认；`G` 键成长仪表盘（含**双门槛**展示）。
3. 阶段化动态属性与卸下/交换语义；交换武器不转移强度。
4. 战斗能力：击杀回血、剑气、满蓄力吸血、Shift+右键爆发、斩 Boss 额外伤害。
5. 共享冷却（跨剑副本、跨重连与死亡、服务器 tick 计时）。
6. 8 张正式 32×32 贴图与美术。
7. 客户端同步与专服隔离复核；GUI 真机验证（854×480 / 1920×1080、中英双语）。
8. 清理阶段性措辞：PHASE 2 完成后移除"PHASE 2 才会实现"类注释与文档说明，
   并把 `README`/`result-*` 收敛为最终行为描述。

## 13. 未修改的边界

- 未修改任何兄弟项目、Minecraft 实例、存档、启动器设置、凭据、共享 Harness 文件或系统设置。
- 未改 `JAVA_HOME` / `GRADLE_USER_HOME`；未使用 `sandbox_permissions` 升级（本轮无需任何提权）。
- 未使用 mixin、未使用访问转换器；`Pose.DYING` 方案完全基于公开 API。
- 只读模板与 SPEC 仅被读取；模板许可证保留。
