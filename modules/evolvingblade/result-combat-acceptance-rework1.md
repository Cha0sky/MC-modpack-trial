# COMBAT-ACCEPTANCE 返工 1 报告

> 范围：把 Codex 指出的 8 处薄弱断言改成真实服务端实体/事件/生命变化的证据。
> **只改测试（`src/gametest`）与验证文档；未改动任何生产实现**（本轮未发现生产缺陷）。

## 最终实测（冻结版本）

| 项目 | 命令 | 结果 |
|---|---|---|
| 单元测试 | `gradlew clean build` | **BUILD SUCCESSFUL**；`EBTEST tests=83 succeeded=83 failed=0 aborted=0 skipped=0 containersFailed=0 -> exitCode=0` |
| 集成测试 | `gradlew cleanGameTestWorld runGameTestServer`（全新世界、单 `defaultBatch`） | **`All 65 required tests passed :)`**（上一轮 63；本轮测试重组后 65） |
| 冻结 jar | — | `build/libs/evolvingblade-1.0.0.jar`，**149,652 字节 / 109 条目 /
  SHA256 `D277AA73619CC76156300307A653495FB93CDFDC14DE8BC149CCE92E136A4A17`**；扫描无 GameTest/夹具/NBT |

日志：`verification/combat-rework1-build.log`、`verification/combat-rework1-final.log`；
失败与修复过程保留在 `verification/combat-rework1-run1.log`、`combat-rework1-run2.log`。
逐条证据表见 **`verification/COMBAT-ACCEPTANCE.md`**（区分"真实 `Player.attack` 集成"与"直接入口隔离"）。

## 逐条完成情况

1. **夹具定位**：新增 `centreOf(helper, relX, relY, relZ)`，用 `helper.absolutePos(...)` 定位，并断言在
   `helper.getBounds()` 内、`isLoaded`、`isPositionEntityTicking`；所有目标/ Boss 只在本测试结构内生成。
2. **护甲/吸收按真实生命差**：改用**阶段 3（48）对 500 HP 存活目标**做对照——
   无甲损失 47.23 → 回血 2.36；有钻石胸甲（`entity.tick()` 后断言 `getArmorValue()>0`）**实际损失更小、
   回血按更小的实际损失且未触上限 4**；部分吸收（20 点）同样按更小的实际损失；**全吸收 → 生命差 0 → 回血 0**。
   不再使用会被原版 1024 上限主导的 final1500 场景。
3. **百分比与上限**：10000 HP 下阶段 5 加成 **raw=100**、终阶 **raw=300**（**Post 真实生命差 = raw**）；
   100000 HP 下上限 **200 / 1000** 保留；**穿甲**用"有/无钻石胸甲的两个 Boss、同一玩家、不同目标"证明：
   加成 raw **完全相同（100）**，而**主伤害因护甲变小**。
4. **限速真实时钟**：tick 0 允许、同 tick 拒绝、**世界时钟 +18 tick 仍拒绝（tick 19）**、**+2 tick 接受（tick 20）**、
   第二玩家对同目标独立允许、目标全程存活；未用 `player.doTick()` 假装推进。
5. **抗性**：用**不同玩家 + 不同目标**消除 20 tick 限速混淆；无抗性 **生命差 = raw(300)**，
   抗性 II **生命差 ≈ 180（raw×0.6）**，两者都确实产生 bonus 且目标存活。
6. **取消/清零/无敌/主伤取消**：bonus Incoming 取消 → **Post 数 0** 且主伤害独立掉血；
   bonus Pre 清零 → **真实生命差 0**；主伤 Incoming 取消 → 无伤害且 **0 个 bonus 事件**；
   无敌用**非创造真实 ServerPlayer** 验证（GameTest mock 玩家 `isCreative()` 恒为 true 会触发原版
   `!source.isCreativePlayer()` 绕过语义）→ 0 事件、生命不变。
7. **防递归/不吸血**：真实阶段 5 满蓄力 `Player.attack`，监听器把主伤 Pre 压到 **40**（模拟其它模组改伤），
   Boss 10000 HP 的 bonus raw=100 → **回血恰好 2.0（=40×5%）**，不是 (40+100)×5% 也不是 cap 4；
   同 tick 再次入口 **0 个额外 bonus**；总损失 > 回血。
8. **未满蓄力/次要目标/技能**：未满蓄力**造成伤害但 0 回血**；次要目标（非 PRIMARY）**真实 30 点伤害、0 额外回血**
   （明确命名为"次要目标模拟"，不声称真实横扫路径）；技能伤害**真实生效（10 点）但不吸血**；三者都用新活目标。

## 本轮修掉的测试自身问题（无生产缺陷，均已如实记录）

`setItemSlot` 后缺实体 tick 导致护甲没生效；Pre 清零后 Post 仍触发（原版语义）导致断言口径错误；
final1500 场景被原版生命上限主导；限速只用同 tick 两次调用；抗性被限速污染；
技能用例目标处于受击冷却导致"无伤害也通过"。**没有删除任何既有测试，也没有放宽断言。**

## 未完成 / 边界

- 超大生命仍是 **dev-only 夹具**（仅覆写 `getAttributeValue` 的 MAX_HEALTH 分支），**不代表**与真实第三方
  Boss 模组集成；未安装任何第三方 Boss 模组。
- 次要目标是**模拟**而非原版真实横扫动画路径。抗性期望 60% 为原版 float 数学（容差 1.0）。
- 本任务**不包含** wave/burst 完整矩阵与重启/持久化（后续独立任务）。
- 未运行 GUI、未安装、未触碰任何实例；release jar 内无任何 dev 夹具。
