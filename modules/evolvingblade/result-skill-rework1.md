# 技能验收返工 1/2 报告（剑气 / 爆发）

> 本轮含**一处真实生产缺陷修复**（剑气可以命中玩家背后的目标，并因此抢占唯一命中位）与一处**真实 UI 缺陷修复**
> （仪表盘滚动提示与标题挤在同一行、超出窗口）。其余是测试补强与既有测试的修正。
> 日志：`verification/skill-rework1-run1..5.log`（失败与修复全过程保留）、`skill-rework1-build.log`、`skill-rework1-final.log`。

## 1. 生产修复（真实缺陷）

### 1.1 剑气：背后目标不得命中，且命中顺序必须按"沿射线距离"

**缺陷**：旧实现逐个 0.5 格采样球收集候选，阶段 2（上限 1 个目标）时，**玩家背后 1 格**的目标会落进第一个
采样球（半径 1.6）并抢占唯一命中位，导致前方真正的目标完全没被打到——这是产品缺陷，不是测试问题。

**修复**（`StarEaterAbilities.tryWave`）：
1. 先用**一次** `level.clip(eye → eye + dir*range)` 求出被墙截断的 `reach`；
2. 在整条被截断射线上**一次性**收集合法候选，按"沿射线的投影距离"升序稳定排序；
3. 过滤条件：`along >= 0`（**排除背后**）、`along <= reach`（墙与射程）、
   横向容差 `WAVE_HIT_RADIUS + max(bbWidth, bbHeight)/2`（保留原采样球的横向语义）、`hasLineOfSight`、UUID 去重；
4. 依次结算，最多 `waveMaxTargets` 个（阶段 2 = 1，终阶 = 3），**不再**触碰 `invulnerableTime`。

回归：`waveMidStageBehindTargetRangeBoundaryAndCooldown` 恢复"背后 1 格"目标并断言 0 伤害、前方 6 格目标先中；
`waveFinalHitsExactlyThreeNearestRegardlessOfSpawnOrder` **故意先远后近**生成 5 个目标，断言最近 3 个中、最远 1 个不中、背后不中。
旧的 `StageAndWaveGameTests.waveStopsAtWallAndNeverHitsWhatIsBehindIt` 也一并通过。

### 1.2 仪表盘滚动提示（Codex 实机截图缺陷）

`DashboardScreen` 原先把滚动提示画在 `width-110` 的同一行（标题行），长中英文会越界。
现在**滚动提示独占一条底带**（`height-36`，Done 按钮之上），并按 `font.width` 真实居中 + 按宽度换行；
内容区底边让出提示带与按钮带，三者互不重叠。**未运行客户端**，视觉复验仍由 Codex 执行。

## 2. 测试补强（逐条对应返工要求）

| # | 要求 | 实现与实测 |
|---|---|---|
| 2 | 射程 24 内/外边界 | `boss_arena` 内目标放在 **23 格（射程内，会被命中）** 与 **25 格（射程外，0 伤害）**，同一测试还断言 6 格目标先中 |
| 3 | 真实伤害的生命差 | 新增 `noArmorBoss`（`LargeHealthFixtureHusk`，max/current **10000**，`ARMOR=0` 且断言 `getArmorValue()==0`，不覆写 hurt/setHealth）：**阶段 2 生命差 = 24.0、终阶 = 1500.0**；**阶段 4 爆发 = 180.0、终阶 = 2250.0**；目标全程**存活**（无击杀回血混淆）且玩家**不回血** |
| 4 | 宠物/玩家/队友排除要"本来合法" | 新增 dev-only `FixtureBossWolf`（非空 bar 字段 + 最大生命 500 → **本来合法目标**）：**未驯服无主对照组真的受伤**；随后"自己的宠物 / 他人宠物 / `isTame()` 且 owner=null"三者 0 伤害。玩家排除用**非创造**真实 `ServerPlayer`（对照组：同次爆发的开放 Husk 受伤）；队友用**原本合法**的敌对 Husk 放进同一计分板队伍 → 0 伤害；测试结束移除队伍 |
| 5 | 冷却真实世界时钟边界 | `waveCooldownDeadlineUsesRealWorldTicks`：`waveReadyAt == start+160`，**+159 tick 拒绝、+160 tick 接受**，并断言时钟差值；`burstCooldownDeadlineUsesRealWorldTicks`：**+399 拒绝、+400 接受**。终阶 80 / 200 的数值仍由 `waveRemaining/burstRemaining == 80/200` 断言（同一入口/同一时钟） |
| 6 | 龙必须真实受伤 | 龙生成在射程内（rel 24,12,24）并对准其眼睛：`sword_ability` 事件**恰好 1 次**且龙**真实掉血**（走原版 hurt 路径，未 setHealth/未改阶段） |
| 7 | UI 缺陷 | 见 §1.2 |

保留的既有断言：墙体阻挡（含墙角）、爆炸/方块不变、≥33 目标恰好 32 个受伤、半径 6/8 外安全、
阶段 0..3 未解锁不消耗冷却、取消技能伤害、`invulnerableTime` 不被清零、配置数值对照。

## 3. 过程记录（不把测试规避当作修复）

- 本轮**没有**再通过"挪动测试位置"绕过背后目标缺陷：背后目标已恢复到**1 格**，并且断言它不受伤；
  规避行为（原先把它挪到 5 格）已删除。
- 过程中修掉的**测试自身**问题：`face(...)` 只对到方块中心导致射线被地板提前截断（改为对到眼睛高度 +1.6）；
  横向容差过严（改为 `1.6 + max(宽,高)/2`，与原采样球语义一致）；爆发墙体用例把墙放在目标之后（已放到玩家与目标之间）；
  狼夹具默认 8 点生命达不到自动探测阈值（抬到 500 使其"本来合法"）。
- `revivedNondragonCannotRetainAnOldClaimForUnownedDeath`（Codex 既有测试）在 run4 失败、run5 **同源码全绿**：
  属于该用例自身的时间敏感抖动（+401 tick 断言），本轮**未改**它，也没有把它当作已修复。

## 4. 最终实测

| 项目 | 结果 |
|---|---|
| `gradlew clean build` | **BUILD SUCCESSFUL**（JUnit 83/83，见日志） |
| `gradlew cleanGameTestWorld runGameTestServer` | **`All 80 required tests passed :)`**（上一轮 77；本轮新增到 80） |
| 冻结 jar | `build/libs/evolvingblade-1.0.0.jar` — **151,608 字节 / 110 条目 / SHA256 `62A09F4555DD159130C5D8A8B92FBF6BDEB38923432A526C4BDEDED041485985`**；扫描 `unexpected=[]`（无 GameTest/夹具/NBT） |

## 5. 未验证 / 边界

- **GUI 视觉未验证**：仪表盘提示条带修复只做了源码层保证（独立底带 + `font.width` 居中/换行），
  实机 854×480 / 1920×1080、中英文观感仍由 Codex 复验（截图 `ui-english-dashboard-header-defect.png` 为本轮修复对象）。
- 未安装任何真实第三方 Boss 模组；`FixtureBossWolf` / `LargeHealthFixtureHusk` 都是 dev-only 夹具，
  **不代表**第三方兼容已实测。
- 龙用例只断言"父实体恰好一次真实伤害"，未对头/身伤害倍率做数值断言。
- `snapshotFor` 相关断言只证明**服务器下发数据正确**，不声称 mock 客户端真的收到网络包。
