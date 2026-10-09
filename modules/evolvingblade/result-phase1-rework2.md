# PHASE 1 返工第 2 轮报告（最终自动轮）— 死亡边界、Boss 延迟死亡阶段与 GameTest 可重复性

> 触发原因：返工第 1 轮仍未被接受。`../_agent-team/phase1-death-edge.md` 给出了独立复核结论：
> 1) **原版飞行末影龙的击杀会被丢掉**（DYING 阶段把血量恢复成 1，随后死亡阶段才归零）；
> 2) 取消契约必须保留**原始 `LivingDeathEvent` 引用**并在 tick 末复核 `isCanceled()`；
> 3) 不能要求模组 Boss 采用原版 `Pose.DYING`；4) 立即以 `KILLED` 移除的合法死亡必须可计数；
> 5) 去重必须按"实体这一条命"永久生效，不能被 TTL/容量清理掉。
>
> 同时 Codex 指出第二次 `runGameTestServer` 可复现地**挂起**（进度条长期空白），要求先诊断再交付。
>
> **本轮结论：上述全部修正完成；JUnit 73/73；GameTest 全绿且连续 9 次完整通过（含全新世界、
> 真实飞行末影龙、凋灵、立即 KILLED 移除、延迟取消等场景），最终验证运行为 19/19
> （Codex 在复核期间持续补充 QA 用例：16 → 18 → 19，每次补充后都重新全绿）；
> `gradlew build` 只跑一次套件；本报告同时保留"挂起"这一真实历史事实与诊断证据。**
> GUI / 战斗能力仍按计划留给 PHASE 2。

---

## 1. 修正清单（逐条对应审查）

### 1.1 原版飞行末影龙的击杀不再丢失（最关键）

**缺陷机制（一手源码）**：
`EnderDragon.hurt(EnderDragonPart, DamageSource, float)`（EnderDragon.java:520-552）在**非坐姿**阶段
受到致命玩家伤害时：先 `reallyHurt(...)` → `LivingEntity.die()`（血量归零、事件发出），
紧接着 `if (isDeadOrDying() && !phase.isSitting()) { setHealth(1.0F); setPhase(EnderDragonPhase.DYING); }`
——**把血量恢复成 1**。之后 `DragonDeathPhase.doServerTick()` 才在满足距离条件时 `setHealth(0.0F)`，
`tickDeath()` 在 200 tick 后 `remove(RemovalReason.KILLED)`。
第 1 轮的实现只按"血量 0"判定终局，因此在 tick 末看到血量 1 就**丢弃候选**，龙永远不会入账。

**修正**：把候选死亡改成显式状态机（`DeathDecision` + `PendingDeathQueue`），tick 末的决策为：

| 条件（按优先级） | 决策 |
|---|---|
| 原始事件在本 tick 末仍被取消 | `DISCARD_CANCELLED` |
| 该实体这条命已入账 | `DISCARD_ALREADY_CREDITED` |
| 已从世界移除：`KILLED` | **`CREDIT`** |
| 已从世界移除：其它原因（DISCARDED / UNLOADED / 换维度） | `DISCARD_REMOVED_NOT_KILLED` |
| 血量已归零 | **`CREDIT`** |
| 仍未终局且未超保留上限 | **`KEEP_WAITING`（保留候选，下个 tick 再查）** |
| 超过保留上限 | `DISCARD_RETENTION_EXPIRED` |

于是末影龙的流程变成：tick 0 致命一击 → 血量 1 + DYING → tick 0 末 **KEEP_WAITING**（不提前入账、
也不丢弃）→ tick 1 死亡阶段把血量归零 → tick 1 末 **CREDIT（100 点 / 1 Boss / 0 普通击杀）**
→ 200 tick 后 `KILLED` 移除，全程**恰好一次**。

**绝不做的事**：不设置姿态、不设置血量、不切换阶段、不覆写 Boss 的阶段逻辑——只观察。

### 1.2 最终取消契约（保留原始事件引用）

`PendingDeath` 现在保存**原始 `LivingDeathEvent` 对象**，并在 `ServerTickEvent.Post`（LOWEST）
重新读取 `event.isCanceled()`。因此：

- HIGH/HIGHEST/NORMAL/LOW 取消 → 本模组处理器根本不会被调用（NeoForge 对已取消事件默认不派发）；
- **同优先级（LOWEST）更晚取消、且血量已经归零** → 事件引用显示已取消 → `DISCARD_CANCELLED`，
  绝不入账。

对应 GameTest：`lateLowestCancelWithZeroHealthIsNotCredited`
（断言：血量确实为 0、候选确实已被本模组先接受、随后被同优先级取消、最终 0 击杀且候选被撤销）。
另有 `cancelledDeathAtLowPriorityIsNotCredited` 覆盖 LOW 取消。

### 1.3 不再要求原版 `Pose.DYING`

第 1 轮用 `getPose() == Pose.DYING` 作为"死亡已提交"的信号，这只对走原版 `die()` 的实体成立。
本轮**完全移除姿态判定**：只要事件未被取消，并且出现"终局血量"或"被 KILLED 移出世界"这两个
可接受事实之一，就入账。
对应 GameTest：`customBossWithoutDyingPoseIsCredited`（自定义 LivingEntity fixture 在 `die()` 后
把姿态重置为 `Pose.STANDING`，仍然必须入账）。

### 1.4 立即 `KILLED` 移除的合法模组 Boss 可计数

`immediateKilledRemovalBossIsCredited`：自定义 fixture 在 `super.die()` 之后立刻
`this.remove(Entity.RemovalReason.KILLED)`；断言实体确实已移除、移除原因确实是 KILLED、并恰好入账 1 次。
反向区别由决策表覆盖：非 KILLED 的移除（例如被 `discard()`）一律丢弃候选
（`liveEntitySyntheticDeathIsNotCredited` 的第二段断言）。

### 1.5 去重按"实体这一条命"永久生效（不再有 TTL/容量上限）

第 1 轮用全局 `Map<UUID, Long>` + 时间窗口 + 4096 条上限：既有"20 tick 后重复尸体通知"
的漏洞，也可能被大量其它击杀挤掉。

**本轮改为把标记写在实体自身**：
`EBAttachments.KILL_CREDITED`（`AttachmentType.builder(() -> Boolean.FALSE).build()`，**刻意不做序列化**）。
- 生命周期严格绑定实体对象：**只要这具尸体还在（哪怕几百 tick），就永远不会二次入账**；
- 没有全局缓存、没有清理窗口、没有容量淘汰，标记不可能被"别的击杀"挤掉；
- 非持久 attachment 不写入存档（`AttachmentHolder.saveAttachments` 只序列化带 serializer 的类型，
  已核实），实体被移除即随对象消失，因此不会泄漏也不会跨实体串味。

全局待核实队列只保留"已接受但未终局"的候选，且有 2400 tick 保留上限（覆盖末影龙 200 tick 死亡动画），
保证内存有界。

对应 GameTest：`duplicateCorpseAfterManyKillsIsNotCredited`
（先击杀目标 → 等 25 tick（远超 20 tick，尸体早已移除）→ 期间再击杀 **30** 个其它僵尸 →
再对第一具尸体补发死亡通知 → 总数仍恰好 31）。

### 1.6 时钟统一与服务器停止清理（第 1 轮修正保留）

所有时间戳统一取**主世界** `server.overworld().getGameTime()`；`ServerStoppedEvent` 时
`STATES.remove(server)`，状态不跨服务器残留。

### 1.7 Codex 独立新增的两条回归所抓到的**真实缺陷**（本轮一并修正）

Codex 在 `src/gametest` 中新增了 `CodexDeathRegressionGameTests`，抓到两个我遗漏的缺陷：

1. `syntheticAliveEventCannotClaimLaterEnvironmentalDeath`：
   先对**活体**合成一次带玩家归属的死亡事件（产生候选），随后该实体死于**无归属**的环境伤害。
   由于新死亡事件没有归属，旧实现直接 `return`，**陈旧的合成候选**就把这次无归属死亡"认领"了。
   **修正**：当新死亡事件无法解析出归属玩家时，**显式作废该实体的既有候选**
   （`state.pending.remove(victimId)`），避免旧候选认领新死亡。
2. `readingFalseCreditMarkerCannotSuppressRealKill`：
   `IAttachmentHolder.getData` 在缺省时会把默认值写进 holder，所以**仅仅读取一次**默认值就会让
   `hasData(...)` 变成 `true`。第 1 轮按"是否存在"判定已入账，会被一次无关读取永久压制正常击杀。
   **修正**：改为按**值**判定 `Boolean.TRUE.equals(victim.getData(KILL_CREDITED))`。

### 1.8 测试运行器必须对"零发现 / 中止"失败

`EBTestRunner.exitCode(...)` 现在是纯函数并单独测试：`testsFound<=0`、`containersFound<=0`、
`testsSucceeded<=0`（全部跳过/中止）、任何用例或容器失败/中止 → 返回 1。
对应 `EBTestRunnerExitCodeTest`（6 个用例，含零发现、全跳过、容器中止）。

### 1.9 入口语义：`gradlew test` 真的跑套件，`build` 只跑一次

`test` 任务在本机（非 ASCII 路径）自身被禁用，但**它 `dependsOn ebTest`**，
因此 `gradlew test` 会真正执行 JUnit 套件；`check.dependsOn ebTest` 让 `gradlew build` 也执行一次。
实测见 §3。

### 1.10 关闭原生文件监视，消除重复告警

`gradle.properties` 增加 `org.gradle.vfs.watch=false`（本机无法创建原生文件监视线程，
会反复打印 `File watcher server ... NativeException: Couldn't open current thread, error = 5`）。
实测构建日志中该告警出现 **0** 次。

---

## 2. GameTestServer 挂起：事实、诊断与修复

### 2.1 保留的真实事实（不粉饰）

| 运行 | 事实 |
|---|---|
| 第 1 次（14:01-14:02） | **完整通过**：`8 tests are now running` → `8 GAME TESTS COMPLETE IN 1.497 s` → `All 8 required tests passed` |
| 第 2 次（14:04:37 起，被 Codex 取消） | **挂起**：`8 tests are now running at position -5141743, -59, -5691104` 之后**一个测试都没开始**，日志被无尽的空白进度条 `[        ]` 填满，直到 14:07:52 仍在自动存档（日志 181,414 行 / 13 MB） |
| 第 3 次（14:21:32 起） | **另一种挂起**：三个批次都开始过，进度条停在 **15/16**，从 14:21:34 一直到 14:25:49（4 分多钟）都没有完成（日志 11 MB） |
| 诊断运行（14:30） | 抓到失败断言：`对照僵尸（同一结构/区块）必须 tick，实际=0, tickingAtControl=false, tickingAtDragon=false, tickingAtOrigin=true` |

**没有**声称"重跑就通过了"——上述三次都是真实历史。

### 2.2 诊断

1. **复用的世界**：`gameTestServer` 原本与普通 `run/` 共用 `run/world`。GameTestServer 会把测试结构
   放在**随机位置**（±14.9M），并依赖 `StructureUtils.forceLoadChunks` → `ServerLevel#setChunkForced`
   让结构覆盖的区块进入 entity-ticking。复用旧世界时，新建/重新加载的区块在部分随机位置下
   永远不进入 ticking，而 `GameTestInfo#tick` 的等待
   （`...allMatch(p -> level.isPositionEntityTicking(...))`）**在测试开始前没有任何超时**，
   于是"一个测试都不开始"，服务器以极高速度空转并不断自动存档 → 表现为无尽空白进度条。
2. **结构跨区块边界**：结构网格间距（X 方向 `size+5`、Z 方向 `size+6`）会让不同结构落在
   相邻区块里。诊断运行证明：结构**原点所在区块** `isPositionEntityTicking=true`，
   而结构**跨过去的那半个区块** `isPositionEntityTicking=false`。
   在 false 的区块里 spawn 的实体 `tickCount` 恒为 0 → 所有依赖实体 tick 的断言随机失败
   （末影龙死亡阶段不推进、僵尸尸体 20 tick 后不消失）。
3. **批次切换会取消全部强制加载**：`GameTestRunner` 在每个批次结束时执行
   `level.getForcedChunks()` → 逐个 `setChunkForced(..., false)`，再开始下一批。
   多批次布局因此引入了额外的区块卸载/重载窗口。

### 2.3 修复（全部落在项目内，不触碰系统设置）

| 措施 | 位置 | 作用 |
|---|---|---|
| 独立的测试游戏目录 `run-gametest` | `build.gradle`（`gameDirectory = project.file('run-gametest')`） | 与普通 `run/` 隔离 |
| 每次运行前删除该目录 | 新增 `cleanGameTestWorld` 任务，`runGameTestServer` 依赖它 | **每次都是全新世界**，消除"复用旧世界"这一挂起原因 |
| 硬性超时 15 分钟 | `tasks.matching { it.name == 'runGameTestServer' }.configureEach { timeout = Duration.ofMinutes(15) }` | 万一再出现框架级挂起，不会无限等待 |
| 单批次布局 | 移除 cancelLow/cancelLowest 的独立批次（全 18 个测试同批） | 去掉批次切换引发的"全部取消强制加载"窗口 |
| `ensureTestChunksTicking(helper)` | `KillCreditGameTests` 每个测试开头 | 显式强制加载**结构实际覆盖的区块（外扩 1 圈）**；区块一旦进入 ticking，`PersistentEntitySectionManager#updateChunkStatus` 会把已在区块内的实体补登记进 tick 列表，实体随后正常被 tick |
| `boss_arena`（48×32×48）结构 | Codex QA 提供（含几何自检测试） | 16 格宽的末影龙在 9×9 的 `empty` 里会挤到相邻结构的清理区；专用竞技场提供足够空间 |
| 有界轮询 + 完整诊断 | 龙测试 `awaitDragonCredit`（最多 240 tick，失败时打印 health/phase/removed/tickCount/noAi/位置/候选状态） | 任何残余问题都会**明确失败并给出状态**，而不是挂起 |

### 2.4 修复后的可重复性证据

**连续 9 次完整运行全部通过**（当时用例数随 Codex 补充 QA 从 16 增至 18；末次为 18）：

```
RUN1 : All 16 required tests passed :)      （14:32:46）
RUN2 : All 18 required tests passed :)      （14:33:02）
RUN3 : All 18 required tests passed :)      （14:33:18）
RUN4 : All 18 required tests passed :)      （14:33:33）
RUN1 : All 18 required tests passed :)      （14:34:35）
RUN2 : All 18 required tests passed :)      （14:34:50）
RUN3 : All 18 required tests passed :)      （14:35:06）
RUN4 : All 18 required tests passed :)      （14:35:22）
RUN5 : All 18 required tests passed :)      （14:35:38）
```

Codex 随后又补了第 3 个死亡回归用例（`revivedNonDragonCannotRetainAnOldClaimForUnownedDeath`），
**最终验证运行：`All 19 required tests passed :)`（14:38:48，BUILD SUCCESSFUL）**。

每次都是全新世界，完整耗时约 2 秒（对比挂起时的 3-4 分钟以上）。
通过运行的日志保存在 `docs/logs/r2-gt1..5.log`；**两次挂起的原始日志**（gzip）保存在
`docs/logs/r2-gametest-run1.log.gz` 与 `docs/logs/rework-gametest-run.log.gz`，作为真实证据保留。

> 诚实说明：挂起的**框架层根因**是"测试结构所在区块没有进入 entity-ticking 且框架在测试开始前
> 没有超时"。本轮通过"全新世界 + 单批次 + 显式强制加载覆盖区块 + 竞技场 + 任务级超时"消除了
> 可复现路径并连续验证 9 次；但这是**测试夹具/框架**问题，不是模组逻辑问题。
> 若在别的机器上再次出现，15 分钟任务超时会兜底，日志会停在某个未完成的用例上。

---

## 3. 实际执行的命令与真实结果

| # | 命令 | 结果 |
|---|---|---|
| 1 | `.\gradlew.bat clean build --console=plain` | **BUILD SUCCESSFUL**；`EBTEST tests=73 succeeded=73 failed=0 aborted=0 skipped=0 containersFailed=0 containersAborted=0 -> exitCode=0`；`> Task :test SKIPPED`；`> Task :ebTest` 出现次数 = **1**；`File watcher`/`NativeException` = **0** |
| 2 | `.\gradlew.bat test --console=plain` | **BUILD SUCCESSFUL**；先 `> Task :ebTest`（73/73）再 `> Task :test SKIPPED` —— 证明被禁用的 `test` 任务仍会执行其依赖，套件确实运行 |
| 3 | `.\gradlew.bat runGameTestServer --console=plain`（连续 9 次 + 最终 1 次） | 每次 **BUILD SUCCESSFUL**；`N tests are now running` → `N GAME TESTS COMPLETE IN ~2 s` → `All N required tests passed :)`；最终为 `All 19 required tests passed :)` |
| 4 | `jar tf build/libs/evolvingblade-1.0.0.jar` | 67 个条目，**不含任何 gametest 类或结构**（`gametest` / `structure` / `boss_arena` / `empty.nbt` 均无匹配）；jar 86,744 字节 |

产物指纹（内容哈希，按条目名+字节排序后 SHA-256）：
`19742BB1DDE3D2996CFE1B0A4AE9111DC9099ED4734EFE520E77CE4511D75AD3`

---

## 4. 测试总览

### 4.1 JUnit（纯 JVM）：73 个用例，全部通过

| 测试类 | 用例 | 覆盖 |
|---|---|---|
| `growth.StageCalculatorTest` | 9 | 24 个阶段边界、双门槛联合、溢出饱和、畸形输入 |
| `growth.DeathDecisionTest` | 7 | **决策表**：最终取消优先（含血量 0）、已入账、KILLED 移除、延迟死亡阶段保留、保留上限 |
| `growth.PendingDeathQueueTest` | 6 | 候选接受/刷新（保留最初时间）、撤销、保留上限、遍历安全 |
| `growth.GrowthSettingsTest` | 7 | 配置净化、阶段数值、能力档位 |
| `growth.GrowthDataTest` | 7 | Codec round-trip、畸形输入拒绝、未选模式阶段 0、饱和 |
| `growth.GrowthModeTest` | 4 | ID 解析、Codec |
| `growth.GiftDeliveryTest` | 4 | 空背包/全满/边界 |
| `growth.GrowthServiceBoundaryTest` | 5 | **null 模式拒绝**、负计数拒绝、`withMode(null)` 拒绝 |
| `boss.BossClassifierCoreTest` | 10 | 排除优先、包含、标签、生命门槛、类名兜底、典狱长非 Boss |
| `boss.BossEventProbeTest` | 8 | 继承字段、类型过滤、**深度敏感缓存**、子类型可赋值、有界日志 |
| `test.EBTestRunnerExitCodeTest` | 6 | **零发现/中止/全跳过 → 非零退出** |
| **合计** | **73** | |

### 4.2 GameTest（真实无头服务器）：19 个用例全部通过（最终验证运行）

| 用例 | 覆盖 |
|---|---|
| `flyingDragonLethalAttackCreditsOnce` | **飞行/非坐姿末影龙**：玩家打头部致命一击 → DYING 阶段血量 1 **不提前入账** → 死亡阶段归零入账 100 点/1 Boss/0 普通 → 200 tick 动画结束 KILLED 移除后仍恰好一次 |
| `defaultWitherIsBossAndCreditsExactly100Once` | 默认凋灵：分类为 Boss（理由=默认包含名单）→ 100 点/1 Boss/0 普通（Boss 排他计数） |
| `lateLowestCancelWithZeroHealthIsNotCredited` | **同优先级更晚取消 + 血量 0** → 先接受候选、再撤销、0 入账 |
| `cancelledDeathAtLowPriorityIsNotCredited` | LOW 优先级取消 → 无候选、0 入账 |
| `customBossWithoutDyingPoseIsCredited` | 模组 Boss **不采用原版 DYING 姿态** → 仍入账 |
| `immediateKilledRemovalBossIsCredited` | **立即 KILLED 移除** → 入账 |
| `duplicateCorpseAfterManyKillsIsNotCredited` | 尸体移除后 >20 tick、且期间 30 次其它击杀 → 重复通知不二次入账 |
| `liveEntitySyntheticDeathIsNotCredited` | 活体合成事件不入账；被 `discard()` 后候选按"非 KILLED 移除"撤销 |
| `environmentalDeathIsNotCredited` | 无归属环境死亡不入账 |
| `terminalDeathCreditsExactlyOnce` | 普通终局死亡恰好一次；在场尸体重复通知不二次入账 |
| `syntheticAliveEventCannotClaimLaterEnvironmentalDeath` | **Codex QA**：陈旧合成候选不得认领之后的无归属死亡 |
| `revivedNonDragonCannotRetainAnOldClaimForUnownedDeath` | **Codex QA**：非龙实体被复活后，旧候选不得认领之后的无归属死亡 |
| `readingFalseCreditMarkerCannotSuppressRealKill` | **Codex QA**：读取一次默认 false 标记不得压制正常击杀 |
| `fullSurvivalInventoryKeepsGiftPending` | 生存满背包 → PENDING、无掉落；腾位后恰好一把；重复调用不多发 |
| `fullCreativeInventoryKeepsGiftPending` | **创造满背包 → 仍 PENDING**（`Inventory.add()` 陷阱）；腾位后恰好一把 |
| `nullModeIsRejected` | `selectMode/forceMode(..., null)` 抛 NPE 且不改变状态 |
| `growthAttachmentRoundTrip` | attachment 读写 + 阶段计算 |
| `emptyHasActualNineByFiveByNineBounds` / `bossArenaHasActualFortyEightByThirtyTwoByFortyEightBounds` | **Codex QA**：测试结构几何自检 |

测试用结构（`empty.nbt` 9×5×9、`boss_arena.nbt` 48×32×48）与全部 GameTest 类都在独立源集
`src/gametest/`，**不进入发布 jar**（已用 `jar tf` 核实）。

---

## 5. 未验证 / 残余事项（诚实清单）

1. **多人真机未验证**：GameTestServer 覆盖了服务器权威行为，但没有真实客户端连接；
   客户端渲染、GUI、网络同步属 PHASE 2，尚未实现。
2. **其它模组 Boss 的阶段语义**：本轮只能对"原版末影龙（延迟死亡阶段）"与
   "自定义 fixture（无 DYING 姿态 / 立即 KILLED 移除）"做真实验证。任何未安装的第三方 Boss
   只能声明为"通过公开契约（事件未取消 + 终局血量或 KILLED 移除）兼容"，**不能声称已实测**。
3. **`makeMockServerPlayerInLevel()` 是 deprecated-for-removal 的官方测试辅助**：已在测试中
   `@SuppressWarnings("removal")`；若未来版本移除，需要自行构造 `ServerPlayer` 并
   `PlayerList.placeNewPlayer`（源码已取证）。
4. **GameTest 框架的区块 ticking 属于框架/原版行为**：本轮的缓解措施全部在测试夹具层，
   若换机器重现，15 分钟任务超时会兜底并留下明确日志。
5. PHASE 2 未实现的内容（保持与 SPEC 一致）：选择/仪表盘 GUI、键位与网络包、
   8 张正式贴图、全部战斗能力（剑气/吸血/爆发/斩 Boss）、阶段化动态属性、共享冷却。

**没有任何已知的成长 / 赠剑 / 死亡入账缺陷被留待后续处理。**

## 6. PHASE 2 待办（不变）

1. 网络包（C2S 模式选择意图、S2C 权威快照/配置值）与服务器侧校验。
2. 首次登录 3 卡选择 GUI + 二次确认；`G` 键成长仪表盘（含双门槛展示、冷却显示）。
3. 阶段化动态属性与卸下/交换语义（交换武器不转移强度）。
4. 战斗能力：击杀回血、剑气（视线阻挡）、满蓄力吸血（按受击前生命截断）、
   Shift+右键爆发（排除玩家/友方/宠物、上限 32）、斩 Boss 额外伤害（只绕过护甲与无敌帧）。
5. 共享冷却（跨剑副本、跨重连与死亡、服务器 tick 计时）。
6. 8 张正式 32×32 贴图与美术。
7. 客户端同步与专服隔离复核；854×480 / 1920×1080 真机验证。
8. 阶段性措辞清理：PHASE 2 完成后移除"PHASE 2 才会实现"类注释/文档，收敛为最终行为描述。

## 7. 未修改的边界

- 未修改任何兄弟项目、Minecraft 实例、既有存档、启动器设置、凭据或共享 Harness 文件。
- 未改动系统编码/安全设置，未添加全局路径，未使用 `sandbox_permissions` 提权。
- 未使用 mixin 或访问转换器；所有修正都基于原版公开 API 与一手源码。
- 测试世界只创建在项目内的 `run-gametest/`，且每次运行前由 `cleanGameTestWorld` 清空。

## 8. 证据文件

| 文件 | 内容 |
|---|---|
| `docs/logs/r2-build.log` | `clean build` 完整输出（含 EBTEST 73/73、ebTest 只跑一次） |
| `docs/logs/r2-gradlew-test.log` | `gradlew test` 输出（ebTest 真实执行 + test SKIPPED） |
| `docs/logs/r2-gt1..5.log` | 连续 5 次 GameTest 全绿（各 18/18） |
| `docs/logs/final-build.log` | 最终 `clean build`（JUnit 73/73） |
| `docs/logs/final-gametest.log` | 最终 GameTest 运行：`All 19 required tests passed :)` |
| `docs/logs/r2-gametest-run1.log.gz` | **挂起证据 A**：15/16 用例永远不完成（11 MB 原始日志压缩） |
| `docs/logs/rework-gametest-run.log.gz` | **挂起证据 B**：一个测试都没开始、无尽空白进度条（13 MB 原始日志压缩） |
| `build/test-results/ebTest/ebTest-summary.txt` | 73 个 JUnit 用例的逐条 PASS 记录 |
