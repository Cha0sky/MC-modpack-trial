# PHASE 2 返工 1 报告 —— 噬星之刃（Evolving Blade）

> 执行方：DeepSeek Harness（唯一实现者）。范围：`_agent-team/live-review.md` 的 5 条返工要求 + 上一份报告里
> 自己承认的 tooltip 缺陷。独立验收仍由 Codex 负责；本文件只记录**实际执行过并看到结果**的内容，
> 并明确列出**尚未验证**的部分（UI / 持久化 / 全部战斗路径**都还没有完成验收**）。

## 1. 本轮实际运行的命令与结果

| 命令 | 结果 |
|---|---|
| `gradlew compileJava compileTestJava compileGametestJava` | 全部通过（仅 `makeMockServerPlayerInLevel` 的固有 deprecation 警告） |
| `gradlew ebTest` | `EBTEST tests=83 succeeded=83 failed=0 aborted=0 skipped=0 containersFailed=0 -> exitCode=0`（返工前 79） |
| `gradlew clean build` | **BUILD SUCCESSFUL**，含 83/83 单测 |
| `gradlew runGameTestServer` | `All 26 required tests passed :)`（返工前 19；新增 7 条来源事实回归） |
| release jar 扫描 | 见 §6 |

返工过程中的一次真实失败（保留记录）：新增的 7 条 GameTest 第一次运行 **6 条失败**、
第二次（修好测试自身缺陷后）**1 条失败**、第三次 **26/26 全绿**。失败原因全部是**我测试代码的缺陷**，
不是产品代码：把 `setCounters(points, …)` 的 `hostileKills` 当成了 0（`setCounters` 会同时设置击杀数）、
误以为 `invulnerableTime>10` 会拒绝伤害（实际只按 `lastHurt` 差值结算）、
以及用 `ServerPlayer.tick()` 推进攻击条（它不会走到 `Player.tick()`，必须用 `doTick()`）。

## 2. 逐条返工结果

### 2.1 tooltip 权威化（live-review 第 4 条，已完成）

- `StarEaterItem.attributesFor(...)` 现在 `.build().withTooltip(false)`：物品自带的**阶段 0 固定属性行**
  （7 / 1.6）**不再显示**——采用组件级 `showInTooltip=false`，而不是脆弱的本地化文本剥离，
  因此任何语言、任何第三方 tooltip 追加都不受影响。
- `StarEaterItem.appendHoverText` 不再读物品上的 `evolvingblade:stage` 组件（通用端不可能把显示组件当强度）。
- 新增客户端事件 `RenderTooltipEvent.GatherComponents` 处理器（在 `@Mod(dist = CLIENT)` 的 `EBClient` 内），
  对噬星之刃追加三行**服务器权威**数值：阶段名、攻击力、攻击速度，全部来自 `EBClientState.snapshot()`
  （即服务器为该**当前玩家**推导的值）。未选择路线时显示"暂无服务器权威数值"。
- 因此背包里"别的玩家造的 / 旧持有者的"剑，预览显示的是**当前玩家**拿到它之后的真实强度；
  物品上伪造的 stage 组件既不改变强度也不改变显示。
- 阶段显示**不再做 `+1`**：tooltip 与仪表盘都直接用 `evolvingblade.stage.<stage>`（0..7 名字），
  仪表盘的 `%s/8` 也改成阶段名（`screen.evolvingblade.dashboard.mode`）。
- 双语键新增：`tooltip.evolvingblade.star_eater.stage_authoritative` / `.damage` / `.speed` / `.awaiting`。

> **仍未验证**：tooltip 的实际渲染效果（需要真机 `runClient`）。本轮只保证编译通过与数据来源正确。

### 2.2 来源事实与击杀回血（live-review 第 9/10 条，已完成）

`EBCombatHandler.onDamagePost` 现在对**每一个被接受的伤害**（`getNewDamage() > 0`）更新来源事实：

- 本模组技能伤害（`evolvingblade:sword_ability` / `boss_bonus`）→ 写入 `SwordHit(player, tick)`；
- 玩家主手近战直击（同一 tick 的 `PRIMARY` 上下文 + `minecraft:is_player_attack` + 直击实体就是玩家本人 +
  主手确实是本剑）→ 写入 `SwordHit(player, tick)`；
- **其它一切被接受的伤害**（弓/弩/三叉戟、宠物、环境、别人打的）→ 写入 `SwordHit.NONE`，即**清除**旧来源。
  这正是"用剑砍一下再用弓射杀不再回血"的修复点。
- 零伤害/被取消的合成 Post **不改变**任何来源事实（也不会因为"没伤害"而误清除）。

主手上下文收紧：只接受**同一 tick**（`melee.gameTime() == tick`，不再是 `<= 1`）、
`is_player_attack` 标签、`getDirectEntity() == player`、且主手是本剑；处理完该玩家的伤害后立即
`PRIMARY.remove(...)`，因此被取消的攻击、上一 tick 的攻击、同 tick 的无关伤害都无法复用满蓄力/吸血上下文。
吸血另需 `melee.charged()`（满蓄力 ≥ 0.9）。

**延迟死亡（末影龙）**：`PendingDeath` 现在在**死亡事件阶段**快照 `SwordHit` 与死亡 tick
（`deathGameTime`），结算时使用这份快照而不是实体的可变 attachment——致命伤之后 200 tick 内的
其它命中不会改写这次击杀的来源，窗口判断也按"命中 → 死亡"的间隔进行。

### 2.3 目标合法性（live-review 第 6 条，已完成）

`isValidTarget` 同时排除：玩家、`TamableAnimal.isTame()`（正确类名，见 §4）、
`OwnableEntity.getOwnerUUID() != null`、友军。新增 GameTest
`tamedTargetsAreExcludedEvenWithoutOwner` 覆盖"已驯服但 owner 为 null"与"别人的宠物"两种情形。

### 2.4 `BOSS_RATE` 持久化与严格有界（live-review 第 1 条 + 本轮要求，已完成）

- 新增纯逻辑 `combat/BossRateCodec`：
  - UUID 解析失败 → **`DataResult` 错误**（不再是 `UUID::fromString` 的未检查异常）；
  - 负的截止时间 → 丢弃（不写入任何可被解释为"早已就绪"的状态）；
  - 条目数超过 256 → 只保留**截止时间最晚**的 256 条（保守方向：宁可多限速，绝不提前放行）。
- `AbilityState.tryConsumeRate` **严格有界**：调用返回后表大小恒为 `<= maxEntries`；
  表满且没有过期条目时**拒绝**新目标，**绝不淘汰仍生效的条目**（淘汰会让该目标在 20 tick 内被再次加成）。
  `pruneExpired` 只清"已经可以再次触发"的条目。
- 单测：`BossRateCodecTest`（往返、畸形 UUID 变 DataResult 错误、负值丢弃、超限保守裁剪）
  与更新后的 `CombatPhase2Test.rateLimitIsPerTargetAndBounded`（表满拒绝 + 生效条目不被淘汰 + 过期后回收）。

> **仍未验证**：真实存档的写入/读取往返（需要真机或专门的持久化测试，本轮未做）。

### 2.5 其它（本轮要求 5，已完成）

- 删除 `EBClient` 里每 40 tick 打印一次翻译标题的调试日志（属于"周期性 debug 刷屏"）。
- 统一时钟复查：`EBCombatHandler.pruneRateTable()` 原先用 `level.getGameTime()`，现在改用
  `StarEaterAbilities.clock(player)`（主世界 `gameTime`），与创建/比较/清理/同步全部一致。

## 3. 新增/修改的文件

**新增**

- `src/main/java/com/evolvingblade/combat/BossRateCodec.java`（纯 JVM 编解码 + 保守裁剪）
- `src/test/java/com/evolvingblade/combat/BossRateCodecTest.java`（4 条）
- `src/gametest/java/com/evolvingblade/gametest/SourceFactGameTests.java`（7 条实体级回归）

**修改**

- `item/StarEaterItem.java`（`showInTooltip=false`；tooltip 不再读 stage 组件）
- `client/EBClient.java`（新增 tooltip 权威行；删除调试刷屏；`List` 导入）
- `client/ModeCardText.java`（阶段显示去掉 `+1`，改为阶段名）
- `event/EBCombatHandler.java`（来源事实清除/写入规则、同 tick 上下文、`is_player_attack`、统一时钟）
- `event/EBKillCreditHandler.java`（`PendingDeath` 快照来源事实与死亡 tick；`applySwordKillHeal` 新签名）
- `combat/StarEaterAbilities.java`（补 `TamableAnimal.isTame()`；时钟说明）
- `combat/AbilityState.java`（严格有界、不淘汰生效条目）
- `registry/EBAttachments.java`（`BOSS_RATE` 使用 `BossRateCodec.CODEC`）
- `test/.../CombatPhase2Test.java`（速率表契约更新）
- `lang/{zh_cn,en_us}.json`（新增 4 个 tooltip 键；仪表盘阶段行改为阶段名）
- `docs/API-NOTES-phase1.md`、`result-phase2.md`（更正 `TamableAnimal` 说明）

## 4. 更正上一份报告的错误说明

上一份报告 §10 声称"编译类路径无法解析 `TamableAnimal`"——**这是我自己的拼写错误**
（写成了 `TameableAnimal`）。正确类是 `net.minecraft.world.entity.TamableAnimal`，
源码位于 `.reference-mcsrc/net/minecraft/world/entity/TamableAnimal.java`，
`AbstractHorse` 也实现 `OwnableEntity`。现已使用正确类名并加了 GameTest 锁定。
`result-phase2.md` 的 §10 已同步更正。

## 5. 本轮新增的 7 条实体级 GameTest（全部通过）

| 测试 | 断言要点 |
|---|---|
| `swordNonlethalThenBowKillGivesNoKillHeal` | 剑非致命一击 → 换下剑 → 真实箭矢致命一击：击杀仍归属该玩家，但**回血为 0** |
| `swordLethalHitGivesKillHeal` | 真实 `player.attack` 致命一击 → 击杀入账 1、**回血 +2** |
| `abilityLethalHitGivesKillHeal` | 本模组技能伤害致命 → 击杀入账 1、**回血 +2** |
| `cancelledSwordHitLeavesNoSourceFact` | 被 `LivingIncomingDamageEvent` 取消的一击不掉血、不留来源事实，之后用弓射杀**不回血** |
| `underchargedFollowUpGivesNoStaleLifesteal` | 满蓄力一击吸血（stage 3）；同 tick 未满蓄力追击**不再吸血**（旧上下文未被复用） |
| `tamedTargetsAreExcludedEvenWithoutOwner` | `isTame()` 为真且 owner 为 null、以及"别人的宠物"都不是合法目标 |
| `fakePlayerSwordKillGivesNoGrowthOrHeal` | FakePlayer 击杀不入账（`isCreditedForLife == false`）、零成长、零回血 |

夹具遵循既定约束：单一 `defaultBatch`、只用 `empty` (9×5×9) 结构、无新 NBT、
事件监听器 `OneShotDamageCanceller` 按目标 UUID 限定作用域（不会影响其它测试）。

## 6. 产物

- `build/libs/evolvingblade-1.0.0.jar` — **145,032 字节 / 109 条目 / 内容哈希
  `1A33377469D94D0F428BC3F52FBDE7E35ABBABBEA7F21EC72110F75C2180638E`**（在 `clean build` 之后、
  `runGameTestServer` 之前冻结；`runGameTestServer` 不修改 jar）。
- jar 内容扫描（ZipFile 逐条）：**无** GameTest 类、**无** `.nbt` fixture、**无** tools 生成器、
  **无**旧占位贴图 `star_eater.png`。
- 资源一致性：双语 lang 键集合一致（zh 137 / en 137，无缺失、无空值）；
  `src/main/resources` 下所有 JSON 可解析（沿用上一轮脚本核对口径）。

## 7. 运行记录（按时间顺序，含失败）

1. 新增 7 条 GameTest 后第一次运行：**6 条失败**（全部为测试代码自身缺陷：
   `setCounters` 同时设置了击杀数导致断言基数错、`invulnerableTime>10` 并不会拒绝伤害、
   `ServerPlayer.tick()` 不走 `Player.tick()` 所以攻击条不增长）。
2. 修正测试后第二次运行：**1 条失败**（攻击条仍为 0，同样原因）。
3. 把推进攻击条的方式从 `player.tick()` 改成 **`player.doTick()`**
   （`ServerPlayer.tick()` 不会走到 `Player.tick()`，攻击条在那里自增）后第三次运行：
   `All 26 required tests passed :)`。
4. `gradlew ebTest`：83/83 通过。
5. `gradlew clean build`：BUILD SUCCESSFUL（含 83/83）。
6. 冻结后 `gradlew runGameTestServer`：`All 26 required tests passed :)`，**BUILD SUCCESSFUL**。
7. jar 内容哈希与条目核对：见 §6。

## 8. 未完成 / 未验证（诚实清单）

- **UI 全部未做真机验证**：tooltip 渲染、三选一界面、第二次确认、仪表盘滚动与 854×480 / 1920×1080 排版，
  本轮**只保证编译与数据来源**；`runClient` 未运行。
- **持久化未验证**：`BOSS_RATE` 的编解码只有纯 JVM 单测（JsonOps），**没有**真实存档读写往返；
  冷却的跨重启同样未验证。
- **战斗仍未全部验收**：剑气（墙/射程/上限）、爆发（上限/视线）、斩 Boss 加成（护甲/冷却/事件取消）、
  剑气/爆发的实际视觉效果都**没有**实体级测试；本轮只补齐了"来源事实/击杀回血/吸血"这一条线。
- **多人**：没有真实多人会话；赠剑、锁定、界面在多人下的表现未验证。
- **专服**：未运行 `runServer`；"客户端类不进服务端"仍只是代码级结论（`@Mod(dist = CLIENT)` + 客户端事件）。
- 因此：**不声称 UI、持久化或战斗已经完成**，等待下一轮任务与 Codex 独立验收。
