# PHASE 2 返工 2（最终自动轮）报告 —— 噬星之刃

> 执行方：DeepSeek Harness（唯一实现者，无委派）。范围：Codex 独立验收失败项（`abilityLethalHitGivesKillHeal`
> 偶发 `kills=50 expected 51`）+ 返工 2 追加要求（统一时钟、计数同步与立即协调、夹具自检、3 次独立复跑）。
> 本文件只记录**实际执行并看到结果**的内容；未验证项集中列在 §7。

## 1. 结论速览

| 项目 | 结果 |
|---|---|
| 失败原因 | **测试夹具缺陷（1 HP 亡灵受害者可能在"我的致命一击"之前/同时被环境伤害杀死）**，不是产品逻辑缺陷；详见 §2 |
| 产品代码改动 | 只改了**时钟一致性**与**计数变化时的同步/立即属性协调**（Codex 指出的真实缺陷），没有为过测试而放宽任何语义 |
| `gradlew clean build` | **BUILD SUCCESSFUL**；`EBTEST tests=83 succeeded=83 failed=0 aborted=0 skipped=0 containersFailed=0 -> exitCode=0` |
| 3 次独立全新世界 GameTest | **`All 31 required tests passed :)` × 3**（日志见 §5） |
| release jar | `build/libs/evolvingblade-1.0.0.jar`，**144,998 字节**，**SHA256（jar 实际字节）= `3C9067F74AC889F193DC6FD41355795BAB331CE4F5CB9FDA249AD721C4DC3FB1`** |
| jar 内容 | 109 条目；扫描确认**不含** GameTest 类 / `.nbt` / 测试源码 / tools 生成器 / 旧占位贴图 |

## 2. 偶发失败的真正原因（含复现证据）

### 2.1 复现

按"3 次独立全新世界"复跑（`cleanGameTestWorld` + `runGameTestServer`）时，**第 1 次就复现了同一签名**：

```
[18:07:25] swordlethalhitgiveskillheal failed at 4383824, -60, -12044549! kill is credited (kills=50)
[18:07:26] 1 required tests failed :(
```

日志已保留：`verification/phase2-rework2-repro-failure.log`（99,414 字节）。

关键观察：**同一个失败签名先后出现在两个不同的测试上**——
Codex 独立运行里是 `abilityLethalHitGivesKillHeal`（技能伤害），我复跑时是 `swordLethalHitGivesKillHeal`（真实
`player.attack`）；而在**同一次运行**里其它击杀测试（弹射物击杀、PHASE 1 的击杀入账测试）都正常入账
（日志中多次 `has made the advancement [Monster Hunter]`）。也就是说：**失败与"伤害类型/来源解析"无关**，
而是这两个测试共有的东西——**"1 HP 的亡灵受害者 + 2 tick 等待窗口"**。

### 2.2 机制

这两个测试（以及 FakePlayer 测试）此前的夹具是：`spawn(ZOMBIE)` → `setNoAi(true)` → `setHealth(1.0F)`，
**然后等 2 tick 才施加"致命一击"**。在这 2 tick 窗口里，1 HP 的僵尸只要吃到**任何一点环境伤害**
（白天阳光点燃亡灵是典型来源；GameTest 的 `empty` 平台露天、服务器世界时间从白天开始），它就会先死。
之后我的"致命一击"打在已经死亡/已移除的实体上 → **不会再产生 `LivingDeathEvent`** →
`EBKillCreditHandler` 没有候选 → 击杀数不涨。而 `assertFalse(victim.isAlive())` 依旧为真，
于是表现为 **"死了但没入账"** —— 与观测到的 `kills=50 expected 51` 完全一致。
这是一次**夹具竞争**，不是产品缺陷：僵尸被环境杀死本来就不该给玩家记击杀。

### 2.3 修法（不改产品语义、不删断言）

1. **换受害者类型**：`EntityType.ZOMBIE` → `EntityType.HUSK`（尸壳）。
   尸壳同属亡灵敌对生物（分类仍走普通敌对、不是 Boss），但**不会在白天被阳光点燃**，
   从根上消除该环境伤害窗口。
2. **消除血量窗口**：血量不再在测试开始时设为 1，而是在**施加致命一击的同一个回调里、出手之前**才设。
3. **把前提写成断言**（失败的夹具会立刻变成精确诊断，而不是偶发的入账失败）：
   `assertOneHealthTarget(...)` 断言"出手前活着、正好 1 HP、没有着火"，失败信息里带上
   `alive / hp / onFire / fireTicks / seeSky / day`；
   技能测试额外断言"受害者被分类为普通敌对、且不是 Boss"（Boss 会走 `creditBossKill`，击杀数本来就不涨）。
4. **失败信息加全量诊断**：击杀断言现在带 `kills / expected / bossKills / points / creditedForLife /
   pending / removal / hp`，下一次任何异常都能一眼定位是"没入账"、"记到 Boss 计数"还是"目标已死"。

### 2.4 关于"模拟玩家共享 UUID / 监听器越界"的核实（结论：不成立，但已确认安全）

- 一手源码核实：1.21.1 的 `GameTestHelper.makeMockServerPlayerInLevel()`
  （`.reference-mcsrc/net/minecraft/gametest/framework/GameTestHelper.java:291-310`）用的是
  `new GameProfile(UUID.randomUUID(), "test-mock-player")` —— **UUID 每次都是随机的，只有名字相同**。
  所以"模拟玩家共享 UUID 导致 `PRIMARY` 互相干扰"在本版本不成立（日志里同名只是显示名）。
- 即便如此，本项目的跨测试状态仍然全部**按 UUID 作用域**且在测试内复位：
  `PRIMARY`/`HEALTH_BEFORE` 以玩家 UUID 为键并在处理完该玩家伤害后 `remove`；
  新增的 `OneShotDamageCanceller` 只取消"指定受害者 UUID"的伤害，并在同一个同步代码块里设置与清除。

## 3. 返工 2 的产品改动（Codex 指出的真实缺陷）

### 3.1 统一时钟

- `EBPayloads.snapshotFor()` 原先用 `player.level().getGameTime()` 计算冷却剩余，而冷却的创建/比较/清理
  一律用主世界 `gameTime` ——**换维度后界面剩余时间会跳变**。现已改为
  `StarEaterAbilities.clock(player)`（主世界 `gameTime`）。
- 复查全部时钟使用点：`EBKillCreditHandler`（主世界 ✓）、`EBCombatHandler`（伤害 Post 用 `clock(player)`；
  服务器 tick 清理用主世界 ✓；`pruneRateTable()` 已改 `clock(player)`）、`StarEaterAbilities`（单一入口 ✓）、
  `EBPayloads`（本次修复 ✓）。

### 3.2 计数变化立即同步 + 立即协调属性

- 新增单点出口 `GrowthService.syncAfterChange(ServerPlayer)`：**协调持有者属性 + 下发权威快照**。
- `creditHostileKill` / `creditBossKill` 现在**任何计数变化**都会调用它（此前只在"阶段变化"时才发，
  用别的武器击杀后按 G 会看到过期计数）；`forceMode` / `setCounters` / `chooseMode` 同样走这个出口
  （管理员改动也立即生效，不再等 20 tick 的周期协调）。

### 3.3 新增测试（共 +5，全部通过）

| 测试 | 覆盖 |
|---|---|
| `counterChangeReconcilesHeldSwordStatsImmediately` | 计数改变（升阶）后**不等 tick**，持有者攻击力/攻速差值立刻等于配置的阶段差值 |
| `ordinaryKillUpdatesSnapshotCountersImmediately` | 一次**不升阶**的普通击杀后，权威快照里的击杀数/成长点立刻更新 |
| `snapshotCooldownUsesOverworldClockAcrossDimensions` | 冷却剩余只用主世界时钟；**传送到下界后仍然一致**（失败信息同时打印 overworldNow/netherNow 便于判定） |
| `emptyFixtureCentreIsLoadedAndEntityTicking` | `empty` 实际 9×5×9、中心点在实际边界内、区块**已加载且实体可 tick** |
| `bossArenaFixtureCentreIsLoadedAndEntityTicking` | `boss_arena` 实际 48×32×48、中心(24,16,24) 已加载且实体可 tick |

夹具约束保持：**单一 `defaultBatch`**（31 个测试全部在一个批次里跑完）、结构 NBT 仍为 LIST&lt;INT&gt;、
未新增/重建任何 NBT、`empty`/`boss_arena` 原样使用。

## 4. 未改动 / 未触碰

- 没有为了过测试而删除或放宽任何断言，也没有修改击杀入账/回血/吸血的语义。
- 未触碰 UI（用户正在用聊天，且 GUI 由 PHASE 3 处理）；未触碰任何 Minecraft 实例、共享状态、凭据或全局设置。
- 未运行 `verify-dev.ps1`（它在 `../_agent-team/` 下写文件，超出本项目允许的写域），改为直接运行等价的 Gradle 任务并保留日志。

## 5. 证据文件（可复核）

| 文件 | 内容 |
|---|---|
| `verification/phase2-rework2-repro-failure.log` | **复现失败**的完整日志（99,414 字节，`swordlethalhitgiveskillheal ... kills=50`） |
| `verification/phase2-rework2-pass1.log` | 修复后第 1 次全新世界运行：`All 31 required tests passed :)` |
| `verification/phase2-rework2-pass2.log` | 第 2 次：`All 31 required tests passed :)` |
| `verification/phase2-rework2-pass3.log` | 第 3 次：`All 31 required tests passed :)` |

三次运行都用 `gradlew cleanGameTestWorld runGameTestServer`（每次都是全新世界），均无 0 测试/中止；
`EBTEST` 83/83 在同一冻结版本上通过。

## 6. 产物与哈希

- `build/libs/evolvingblade-1.0.0.jar`
- 字节数：**144,998**
- **SHA256（jar 文件实际字节，`Get-FileHash -Algorithm SHA256`）= `3C9067F74AC889F193DC6FD41355795BAB331CE4F5CB9FDA249AD721C4DC3FB1`**
- 条目数 109；jar 内**无** GameTest 类 / `.nbt` / 测试源码 / tools 生成器 / 旧占位贴图。
- 双语 lang 键集合一致：zh 137 = en 137。

## 7. 未验证 / 未完成（诚实边界）

- **GUI 仍未验收**：Codex 已在隔离客户端确认存在真实渲染缺陷（背景模糊把已绘制文本糊掉、卡片按钮压住描述），
  这属于 **PHASE 3** 范围，本轮**未改 UI**。因此**不声称 UI 可用**。
- **客户端隔离**：Codex 已用 release jar 在隔离客户端成功启动（`verification/isolated-client-console.log`），
  但"专用服务器不加载客户端类"这一点在本轮仍只是代码级结论（`@Mod(dist = CLIENT)` + 客户端事件）；
  我没有再跑 `runServer`。
- **持久化**：`BOSS_RATE`/冷却的编解码有纯 JVM 测试，但**没有**真实存档跨重启往返（PHASE 3 要求单独覆盖）。
- **战斗其余部分**：剑气（墙/射程/穿透上限）、爆发（上限/视线/半径）、斩 Boss 加成（护甲/冷却/事件取消/递归）
  仍**没有**实体级测试；本轮只加固了"来源事实 + 击杀回血 + 吸血 + 时钟 + 同步"这条线。
- 关于复现：我**复现了失败签名并定位到共有的夹具竞争**（1 HP 亡灵 + 等待窗口），但**没有**逐帧抓到
  具体是哪一种环境伤害（阳光点燃最常见）；因此修法是"换用不会阳光点燃的尸壳 + 出手前才设 1 HP + 把前提写成断言"，
  而不是保留原夹具并声称已根治。若这类偶发再次出现，新的断言会直接打印 `onFire/seeSky/day/hp` 现场状态。
- PHASE 3 的范围已阅读（`../_agent-team/phase3.md`），**本轮未启动**。
