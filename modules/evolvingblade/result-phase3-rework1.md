# PHASE 3 返工 1 报告（清单式）

> 45 分钟预算内的工作记录。**包含一个我引入的真实回归**（见 §2），未完成项逐条列出（§3）。
> 冻结产物：`build/libs/evolvingblade-1.0.0.jar`，148,647 字节 / 109 条目 /
> SHA256 `C8877F67AC563062C3432671CA573A624FA96E1F4FC7414472E73B7D17B1F485`（`clean build` 后、测试前的源码状态）。
> 日志：`verification/phase3r1-build.log`（83/83 JUnit，BUILD SUCCESSFUL）、
> `verification/phase3r1-gametest.log`（**39 个测试，2 个失败**）。

## 1. 已完成（有实测证据）

### 1.1 立即换剑后第一次攻击的属性正确性（生产修复，Codex 第 1 项）

**问题**：`StageAttributes` 按"差值"施加（阶段总值 − 玩家基础 − 物品基线），而物品基线与第三方修饰符
是原版在**装备 tick** 里才挂上的。同一 tick 内"快捷栏切到本剑 + 攻击"时基线还不存在 →
实测 1494 而不是 1500；之后的装备 tick 又叠一次，攻速甚至掉到 0（攻击条永远不满，实测 `attackSpeed=0.0`）。

**修复**（`StageAttributes`，只改动 4 个 id）：
1. 先把**保留的原版基线槽位**（`minecraft:base_attack_damage` / `base_attack_speed`）设为配置的阶段 0 基线
   —— 缺就补、残留就覆盖；原版装备 tick 对同一 id 也是"先 remove 再 add"，双方幂等；
2. 再移除本模组自己的修饰符，读出"玩家基础 + 基线 + 第三方"的当前总值；
3. 补足到 **阶段总值 + 第三方 ADD_VALUE 之和**（第三方叠加在阶段总值之上，第三方 id 一律不碰）；
4. 空手时清掉我们替原版补过的基线（`INJECTED_BASELINE`，仅有武器时不清理，基线归原版管）；
5. 非有限值防御（NaN/Infinity 直接放弃本次补足，避免 `sanitizeValue` 把攻速静默变成 0）。

**新增 4 条 GameTest（全部通过，见 37/37 那次运行）**：
`immediateAttackAfterSwapToSwordUsesFullStageTotal`（无装备 tick 时攻击读取到的攻击力 =
1500 + 第三方 7 = **1507**，第三方修饰符保留）、`sameStackUsesEachHolderStageWithoutDoubleCounting`
（同一物品栈：阶段 7 持有者 1500/2.4，交给阶段 0 持有者立刻变成 7/1.6，装备 tick 后仍然是 7/1.6）、
`forgedStageComponentNeverChangesStrength`（伪造 `stage=7` 组件，阶段 0 持有者仍然只有 7）、
`freshCraftAdoptsCurrentOwnerStage`（新造的剑跟随持有者阶段 → 1500/2.4）。

### 1.2 成长数据真实持久化路径（Codex 第 2 项，部分）

新增 `GrowthPersistenceGameTests`：
- `attachmentNbtRoundTripPreservesCountersCooldownsAndRateLimit`：`saveWithoutId` → 新 ServerPlayer
  → `load`，断言成长点/双计数/模式/**两个冷却绝对 deadline**/`BOSS_RATE` 表都保留（**真实实体 NBT 路径，不是 JsonOps**）→ 通过；
- `dimensionChangeRetainsGrowthData`：传送到下界后数据与阶段不变 → 通过；
- `chooseLocksAndGiftIsDeliveredExactlyOnce`：第一次选择成功、第二次被拒（锁定）、背包里
  恰好 1 把剑、`giftState=GRANTED`；管理员 `setCounters` + `forceMode` 保留计数且**不会多发一把剑** → 通过。

### 1.3 文档修正（Codex 第 6 项）

`README.md` 的配置路径**改为经本项目运行目录核对的事实**：SERVER 配置始终在**实例/服务器根目录的 `config/`**
（证据：`run/config/evolvingblade-server.toml` 与 `run-isolated-client/config/evolvingblade-server.toml` 都存在），
`<世界>/serverconfig/` 只是可选的"按世界覆盖"位置（那三个目录里只有 NeoForge 的 `readme.txt`）。
Codex 原话"sempre world/serverconfig"与实测不符，已按证据写清。

## 2. 我引入的回归（必须修，未在预算内修完）

`verification/phase3r1-gametest.log`：

```
waveStopsAtWallAndNeverHitsWhatIsBehindIt failed ... a wall must stop the wave:
    the target behind it must be untouched (hp=499.0, before=500.0)
wardenisordinarybuttwohighhealthinstancescountastwobosses failed ...
    sanity: a 500 HP instance must be auto-detected as a boss
```

- 墙体测试在**本次属性改动之前是通过的**（33/33 那次）。改动后墙后目标掉 1 点血，说明
  1.1 的属性改动影响到了这条测试的实体（最可能是 **Husk 与"自动探测 Boss"的交互**：
  第二条失败显示"500 生命的 Husk 没有被判定成 Boss"，而设置 `MAX_HEALTH=500` 的写法在本批次的
  并发环境下不可靠——两条失败指向同一处：**我对 `AttributeInstance` 的读写与其它测试并发修改同一实体类型属性时的相互作用**）。
- 结论：**这是回归，不是"未实现"**；我没有为了让它变绿而放宽断言，也没有把失败藏起来。
  预算内没有完成定位与修复。

## 3. 未完成（Codex 逐条要求中尚未做到的）

- **两进程持久化重启场景**（第 2 项后半）：没有实现 `write`/`read` 两次真实进程 + 共享世界目录的脚本，
  只做到"真实 NBT 往返 + 维度保持"。
- **三模式八阶段真实边界与双门槛组合**（第 2 项）：仅依赖已有 JUnit 门槛测试，没有新增实体级断言。
- **多人两个持有者独立 attachment**：未做（已有"两个持有者同一物品栈"的属性测试）。
- **Boss 兼容矩阵**（第 3 项）：只加了"监守者=普通敌对 + 两个高生命实例各计一次且不占普通击杀"这一条
  （当前因 §2 的并发问题失败，且 §2 第二条失败说明"500 HP 自动探测"的写法本身需要修）；
  **未做**：自定义 `ServerBossEvent` 字段（继承/子类）、类名包含 Boss、null bar、强制排除优先于包含/标签/自动、
  dev-only 标签夹具、反射失败的单元夹具、同一 Boss 两次击杀=2（本条已部分覆盖）。
- **吸血/斩 Boss 的实际战斗矩阵**（第 4 项）：未新增；仍是 PHASE 2 的 5 条（满蓄力吸血、未满蓄力不复用、
  取消的一击不留来源等）。
- **剑气/爆发的完整数值矩阵**（第 5 项）：**未新增**（已有墙体阻挡测试，但当前因 §2 失败）；
  最大 3 目标、4 号目标不受影响、超射程不受影响、爆发 32 上限/半径/排除玩家友军宠物/方块不变/无吸血均未实现。
- **专服 `forgeserverdev` 启动作为"专用 Dist"证据**：未运行（本轮只跑了 GameTestServer）。

## 4. 已有的基础覆盖（供参考，本次未改动）

83 条 JUnit（门槛/成长/冷却/速率限制/编解码/死亡决策/来源窗口）+ 33 条通过的 GameTest
（击杀入账与取消、Boss 分类基础、赠剑待发/发放、来源事实与回血/吸血、时钟与同步、阶段属性、
夹具几何与区块自检、剑气墙体阻挡）——详见 `result-phase3.md` 与各阶段报告。

## 5. 结论

预算内完成了 Codex 第 1 项（生产修复 + 4 条实测通过）与第 2 项的一部分、第 6 项的文档修正；
第 3/4/5 项的大部分与两进程重启场景**未完成**，并且我引入了一个**已知回归**（§2）。
当前 HEAD 的 GameTest 是 **39 跑 37 过**，不是全绿，**不能作为验收版本**。
