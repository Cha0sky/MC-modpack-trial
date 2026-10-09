# WAVE-BURST-ACCEPTANCE 报告（剑气 / 爆发真实实体验收）

> 生产改动：**仅 1 处真实缺陷修复** —— `tryWave` / `tryBurst` 在占用冷却后**立刻下发服务器权威快照**
> （`GrowthService.sendSnapshot`）。这样"施法后立刻打开仪表盘"不会再因为要等 20 tick 周期同步而显示"就绪"。
> 客户端仍然只显示服务器快照，没有新增任何网络意图。
> 其余全部是 `src/gametest` 的新增测试。

## 最终实测

| 项目 | 命令 | 结果 |
|---|---|---|
| 单元测试 | `gradlew clean build` | **BUILD SUCCESSFUL**（`EBTEST tests=83 succeeded=83 failed=0 aborted=0 skipped=0 containersFailed=0 -> exitCode=0`） |
| 集成测试 | `gradlew cleanGameTestWorld runGameTestServer`（全新世界、单 `defaultBatch`） | **`All 77 required tests passed :)`**（上一轮 65；本轮 +12 条 wave/burst 验收） |
| 冻结 jar | — | 见 §5（`build/libs/evolvingblade-1.0.0.jar`，扫描无 GameTest/夹具/NBT） |

日志：`verification/wave-burst-build.log`、`wave-burst-final.log`；中间失败与修复过程保留
`wave-burst-run1..5.log`（**未隐藏任何失败**）。

## 1. 逐条实际结果（新增 `WaveBurstAcceptanceGameTests`，12 条）

### 剑气
| 断言 | 方式 | 实测 |
|---|---|---|
| 阶段 2 伤害 = 24 | 真实 `tryWave` + 按 UUID/类型观察 `sword_ability` 的 Incoming 原始值 | **raw = 24.0**，且只有 1 次事件 |
| 射程 24（内/外边界） | `boss_arena`：6 格内目标被命中；**28 格外目标 0 伤害** | 通过（另有用例断言 24 的数学来源） |
| 背后目标不受伤 | 背后 5 格目标血量不变 | 通过 |
| 冷却 160 tick + 立刻同步 | `AbilityState.remaining == 160`；**施法后立刻取快照 `waveRemaining`（±1）** | 通过（这是本轮生产修复的回归断言） |
| 冷却期间再次施法被拒 | `tryWave` 第二次返回 false | 通过 |
| 墙体阻挡（含墙角） | 5×3 石墙：墙后目标与贴墙角目标都 0 伤害；墙块未被修改 | 通过 |
| 终阶 = 1500 且恰好 3 个最近目标 | **4 个目标（6/9/12/15 格）**：前 3 个受伤（最近的），**第 4 个 0 伤害**；raw = 1500；冷却 80 | 通过 |
| 阶段 0/1 未解锁 | `tryWave` false、0 伤害、`waveReadyAt` 不变 | 通过 |
| 换另一把同物品的剑不能重置冷却 | 换栈后仍被拒且 deadline 不变 | 通过 |
| 龙多部位只算父实体一次 | 真实末影龙：`sword_ability` 事件 ≤ 1（`EnderDragonPart` 不是 `LivingEntity`，查询无法直接命中部位） | 通过 |

### 爆发
| 断言 | 方式 | 实测 |
|---|---|---|
| 阶段 4 伤害 180 / 半径 6 / 冷却 400 | 5 格内目标 raw = **180.0**；10 格外 0 伤害；`burstRemaining == 400`；快照立刻显示 | 通过 |
| 终阶 2250 / 半径 8 / 冷却 200 | 最近目标 raw = **2250.0**（500 HP 目标被击杀，生命差只到 500） | 通过 |
| ≥33 个目标 → 恰好 32 个受伤 | `boss_arena` 内 **34 个 8 格内目标**：**damaged == 32**；半径外（10/13 格）0 伤害 | 通过 |
| 不破坏地形、不爆炸 | 施法前后指定石块的 `BlockState` 相等（相对与绝对坐标各一处） | 通过 |
| 排除玩家 / 队友 / 自己的宠物 / 他人宠物 / `isTame()` 且 owner=null | 5 类实体血量全不变；**开放处的敌对目标受伤**；**墙后目标 0 伤害**（LOS） | 通过 |
| 阶段 4 非致命不吸血 | 目标真实掉血且玩家血量不变 | 通过 |
| 阶段 0..3 未解锁 | `tryBurst` false、0 伤害、`burstReadyAt` 不变 | 通过 |
| 技能取消 + 不写 `invulnerableTime` | 取消 `sword_ability` Incoming → 0 真实伤害；另一次真实命中后 `invulnerableTime > 0` | 通过 |
| 配置数值对照 | `waveDamage/burstDamage/cooldown/radius/maxTargets` 直接对照配置（24/1500/180/2250、160/80/400/200、6/8、1/3） | 通过 |

## 2. 本轮修掉的**测试自身**问题（保留真实记录）

1. 最初在 `empty`（9×5×9）里测 24 格射程 → 射线在结构边缘被挡，只有近处目标能被命中 → 长射程/多目标用例改用
   `boss_arena`（48×32×48），并用 `helper.absolutePos` + `player.lookAt(绝对坐标)` 消除结构旋转影响。
2. "背后目标"放在玩家身后 1 格，被第一个采样球的 1.6 半径包住并抢占了唯一的命中位（阶段 2 上限 1） →
   玩家后移到靠后位置，背后目标改为 5 格。
3. 500 HP 目标的"生命差"最多 500，用它断言 1500/2250 的原始伤害是错的 →
   改为用按 UUID/类型观察的 **Incoming 原始值**断言 1500 / 2250，生命差只用来断言"确实被击杀"。
4. 墙体最初只盖住 3 格高，实体眼睛在墙顶之上导致"墙后目标仍被命中"（爆发 LOS 与剑气射线）→
   墙体改为覆盖实体眼睛所在高度带。

## 3. 未验证 / 边界（诚实说明）

- **GUI 观感未验证**：本任务未运行客户端；"施法后立刻打开仪表盘显示冷却"只验证了**服务器立刻下发快照**
  这一数据链路，视觉确认仍属 Codex 的 UI 复验范围。
- **第三方模组**：未安装任何真实第三方 Boss/技能模组；`LargeHealthFixtureHusk` 是 dev-only 夹具（只覆写
  `getAttributeValue` 的 MAX_HEALTH 分支，模拟"模组给生物加大最大生命"），**不代表**第三方兼容已实测。
- 末影龙用例只断言"父实体最多被命中一次"，没有对龙的头部/身体伤害倍率做数值断言（避免与技能数值混淆）。
- 本任务不包含重启/持久化与 UI 视觉验收（后续独立任务）。

## 4. 生产修复的最小回归

- `GrowthService.sendSnapshot` 走既有的 `EBPayloads.sendState`，内部保留 `player.connection.hasChannel(...)`
  守卫，因此 GameTest 的 mock 玩家（无该频道）不会因为立刻发包而抛异常——77 条测试全绿即为该路径的回归证据。
- 未改动属性/成长/UI/网络意图；`src/main` 只有 `StarEaterAbilities` 的两行新增。

## 5. 冻结产物

- `build/libs/evolvingblade-1.0.0.jar` — **149,693 字节 / 109 条目 /
  SHA256 `60508B1B580D37C2134612145E7CF3D6450CBDFF8D1A1FD617B703AAE2CCE358`**
  （`clean build` 后冻结；此时源码含本轮唯一的 2 行生产修复）。
- jar 内容扫描：**无** GameTest 类 / `.nbt` 夹具 / dev 实体夹具（`unexpected=[]`）。
