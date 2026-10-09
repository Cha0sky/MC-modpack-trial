# COMBAT-ACCEPTANCE 验证记录（返工 1 后）

> 本文件是**验收证据清单**：每条都区分「真实 `Player.attack` 集成」与「直接入口隔离」。
> 产物：`build/libs/evolvingblade-1.0.0.jar`（见文末冻结哈希）。
> 原始日志：`combat-rework1-run1.log` / `combat-rework1-run2.log`（失败与修复过程全保留）、
> `combat-rework1-build.log`（JUnit）、`combat-rework1-final.log`（最终全绿）。

## 夹具与隔离规则（本轮强化）

| 规则 | 实现 |
|---|---|
| 位置 | `livingHusk` / `livingBoss` 全部用 `helper.absolutePos(new BlockPos(relX,1,relZ))` 定位，并断言**在 bounds 内** + **区块已加载且实体可 tick**；实体只留在本测试结构内 |
| 监听器作用域 | 单一 `Scoped` 监听器，按 **(目标 UUID, 伤害类型包含串)** 过滤；每个测试开头 `reset()`、结束再 `reset()` |
| 记录内容 | Incoming 原始值 `lastAmount`、Pre 的 `preHealth`、Post 的真实生命差 `lastPostDelta` |
| 不回绕免疫 | 从不修改 `invulnerableTime` 来绕过受击冷却；只在"两次明确不同的攻击之间"把它清零（说明性） |
| 时间推进 | 限速测试用 `helper.runAfterDelay(...)` 真实推进世界时钟，**不用** `player.doTick()` 假装推进 |
| 装备护甲 | 目标 `setItemSlot(...)` 后调用 `entity.tick()`（原版在实体 tick 里才生效），并断言 `getArmorValue() > 0` |

## 逐项结果

| # | 要求 | 测试 | 方式 | 关键实测 |
|---|---|---|---|---|
| 1 | 夹具定位/bounds/chunk | 所有 | — | `centreOf()` 断言 bounds 内含 + `isLoaded` + `isPositionEntityTicking`；实体用绝对坐标生成 |
| 2 | 护甲/吸收按真实生命差 | `lifestealUsesRealPostArmorAndPostAbsorptionLoss` | 真实 `Player.attack`（阶段 3） | 无护甲损失 47.23 → 回血 2.36；**有钻石胸甲损失更小 → 回血按更小的实际损失**（未触上限 4）；部分吸收（20 点）损失更小、回血更小；**全吸收 → 生命差 0 → 回血 0**。目标 500 HP 全程存活 |
| 3 | 1%/3% 未达上限 + 上限 + 穿甲 | `bossBonusStage5IsOnePercentBelowAndAtTheCap`、`bossBonusFinalStageIsThreePercentBelowAndAtTheCap`、`bossBonusBypassesArmorButMainDamageDoesNot` | 真实 `Player.attack` + 来源隔离 Post | 10000 HP：阶段 5 加成 **raw=100**（1%）、终阶 **raw=300**（3%），且 **Post 真实生命差与 raw 之差 < 0.5 / < 1.0（无甲无抗性活目标，精确相等）**；100000 HP：**200 / 1000**（上限）；**穿甲用 bonus 自身的 Post 生命差证明**：无甲与有甲两次第一击的 bonus 生命差**都等于 100 且彼此相等**，而第二击（同 tick 限速隔离出的主伤）**因护甲变小**，两个目标全程存活 |
| 7 | 防递归 + bonus 不吸血 | `lifestealIgnoresBonusDamageAndNeverRecurses` | 真实 `Player.attack`（阶段 5） | 单一按 UUID 的观察器**同时观察全部伤害类型**：主伤被 Pre 压到 **40**（记录到 40 且主 Post 恰好 1 次）、bonus **incoming==1 且 post==1、Post 生命差 ==100**；一次攻击总真实损失 **==140**；**回血恰好 2.0（40×5%）**；随后同 tick 再次入口 → **新增 bonus 事件 0**（防递归有真实观察支撑，不是"没触发也通过"） |
| 4 | 限速真实时钟 | `bossBonusRateLimitUsesRealWorldTicksAndIsPerPlayer` | 直接入口（隔离限速） | 记录 `StarEaterAbilities.clock` 起点后：同 tick 第二次 `false`；**世界时钟 +19 tick，断言 `now-start==19` 且仍 `false`（tick 19）**；**再 +1 tick，断言 `later-start==20` 且 `true`（tick 20）**；另一玩家同目标独立 `true`；目标全程存活 |
| 5 | 抗性（消除限速混淆） | `bossBonusObeysResistanceWithIndependentPlayerAndTarget` | 真实 `Player.attack`（不同玩家 + 不同目标） | 无抗性：raw=300、**生命差 = 300**；抗性 II：raw=300、**生命差 ≈ 180（raw×0.6）**；两者都确实触发 bonus 且目标存活 |
| 6 | bonus 取消/清零/无敌/主伤取消 | `bossBonusCancelPreZeroAndInvulnerabilityAreHonoured` | 真实 `Player.attack`（主伤取消、bonus 取消）+ 直接入口（无敌） | bonus Incoming 取消 → **Post 数=0**、主伤害仍独立掉血；bonus Pre 清零 → **真实生命差 0**；主伤 Incoming 取消 → **无伤害且 0 个 bonus 事件**；非创造玩家下**无敌目标 0 个 bonus 事件、生命不变** |
| 7 | 防递归 + bonus 不吸血 | `lifestealIgnoresBonusDamageAndNeverRecurses` | 真实 `Player.attack`（阶段 5） | 单一按 UUID 的观察器**同时观察该目标的全部伤害类型**：主伤被 Pre 压到 **40**（记录到 40、主 Post 恰好 1 次），bonus **incoming==1 且 post==1、Post 生命差 ==100**；一次攻击总真实损失 **==140**；**回血恰好 2.0（40×5%）**，而不是 (40+100)×5%=7 或 cap 4；随后同 tick 再次入口 → **新增 bonus 事件 0**（不是"没触发也算通过"） |
| 8 | 未满蓄力/次要目标/技能 | `underchargedSecondaryAndSkillNeverLifesteal` | 真实 `Player.attack` + 真实伤害 | 未满蓄力：**造成伤害但 0 回血**；次要目标（非 PRIMARY）：**真实 30 点伤害、且主攻击后与次要命中后的玩家血量完全相等（0 额外回血）**（明确命名"次要目标模拟"，不声称真实横扫）；技能伤害：**真实生效（10 点）但不吸血** |
| — | 不清 `invulnerableTime` | `mainAndBonusNeverClearInvulnerableTime` | 真实 `Player.attack` | 主伤后字段 >0，bonus 调用后**不减小** |

## 本轮（返工 2）修掉的测试自身问题

1. 限速测试原先用 18+2 却写成 19/20 → 改为 **19+1**，并记录 `StarEaterAbilities.clock` 起点，**断言时钟真实走了 19 与 20 tick**。
2. 低于上限的 bonus 只断言 `0 < postDelta <= raw` → 改为 **`|postDelta - raw| < 0.5`（阶段 5）/ `< 1.0`（终阶）**（无甲无抗性活目标应精确相等）。
3. 穿甲只比较 raw → 改为比较 **bonus 自身的 Post 生命差**（无甲/有甲都 ==100 且彼此相等），同时保留"主伤因护甲变小"（第二击限速隔离出主伤）。
4. 防递归原先 reset 后观察同 tick 限速（即使 bonus 完全没触发也会通过）→ 改为**一次攻击内按 UUID 同时观察主伤与 bonus**：主伤 40（Post 1 次）、bonus incoming/post 各 1 次、bonus Post 生命差 100、总损失 140、回血 2.0；随后同 tick 再入口断言**新增事件 0**。
5. 次要目标原先只断言"回血 ≤ 上限" → 改为直接对比**主攻击后与次要命中后的玩家血量严格相等**，并断言主目标非致命存活、次要目标受到真实伤害。
6. 校验夹具上限：`MAX_HEALTH` 是 RangedAttribute（上限 **1024**），原先把目标设成 5000 实际只有 1024 → 改用 500/1000 量级的真实存活目标，避免"目标被一击打死"污染断言。

1. 护甲没有生效：`setItemSlot` 后缺少实体 tick → 已补 `entity.tick()` + `getArmorValue()>0` sanity。
2. Pre 清零的 bonus：Post 在 0 伤害时**仍会触发**（原版语义），断言应检查"真实生命差为 0"，而不是"没有 Post"。
3. 原先 `final1500` 打 5000 HP 的目标其实会被原版**生命上限 1024** 主导，护甲结论不成立 → 改用阶段 3（48 伤害）与 500 HP 存活目标做对照。
4. 原先限速测试只比较同 tick 两次调用 → 改为真实世界时钟推进。
5. 原先抗性测试复用同一玩家+目标，被 20 tick 限速污染 → 改为不同玩家 + 不同目标。
6. 原先"技能不吸血"时目标仍在受击冷却、技能可能根本没造成伤害 → 改为新活目标并断言**技能确实造成真实伤害**。

## 冻结产物

- `build/libs/evolvingblade-1.0.0.jar`：字节数与 SHA256 见 `result-combat-acceptance-rework1.md`（本次 `clean build` 后冻结，且 jar 内无任何 GameTest/夹具）。

## 诚实边界

- 超大生命是 **dev-only** 夹具（仅覆写 `getAttributeValue` 的 MAX_HEALTH 分支），**不代表**与真实第三方 Boss 模组集成过；未安装任何第三方 Boss 模组。
- 抗性期望 60% 来自原版 float 数学（Resistance II = 40% 减伤）；不同目标/玩家的生命差用容差 1.0 判定。
- "次要目标"是**模拟**（同 tick 真实伤害、非 PRIMARY 目标），不是原版真实横扫动画路径。
- 本任务仍**不包含** wave/burst 完整矩阵与重启/持久化（后续独立任务）。
