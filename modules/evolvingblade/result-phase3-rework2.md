# PHASE 3 返工 2 报告（清单式，全部 1–5 已完成，测试全绿）

> 冻结产物：`D:\PCL 正式版 2.10.6 (1)\codex\evolvingblade\build\libs\evolvingblade-1.0.0.jar`
> **149,652 字节 / 109 条目 / SHA256 `881B4B63CF3E52854535F895AB68904ADE5DCAAECB681CA961CD41ABCC375D8A`**
> （`clean build` 后冻结；扫描确认 jar 内无 GameTest 类/夹具/NBT/测试源码）。
> 日志：`verification/phase3r2-build.log`、`verification/phase3r2-gametest.log`（保留上一轮失败日志 `verification/phase3-rework1-failing.log`）。

## 最终实测

| 项目 | 结果 |
|---|---|
| `gradlew clean build` | **BUILD SUCCESSFUL** |
| JUnit 5 | `EBTEST tests=83 succeeded=83 failed=0 aborted=0 skipped=0 containersFailed=0 -> exitCode=0` |
| `gradlew cleanGameTestWorld runGameTestServer`（全新世界、单 `defaultBatch`） | **`All 52 required tests passed :)`**，无 0 测试/中止 |

## 1. StageAttributes 第三方乘法缺陷（已修 + 5 条新测试）

**问题确认**（Codex 指出的真实缺陷）：上一版本用
`needed = stageTotal + thirdPartyAdd − instance.getValue()` 反解，`instance.getValue()` 已经包含第三方乘法与钳制，
减法把乘法效果吃掉、再放进我们的 ADD，里外里重复乘除 → 阶段 0 基线 7 在 1.5× 下变成 5.25。

**修复**：回到**代数差值** `delta = settings.stageDamageModifier(stage) − StarEaterItem.BASE_DAMAGE_MODIFIER`
（攻速同理），并先 `ensureBaseline(...)` 确保保留的原版基线槽位
（`minecraft:base_attack_damage` / `base_attack_speed`）存在且等于静态物品默认值（缺失则补、残留自其它武器则覆盖）。
不再读 `getValue()`；原版 base 改动与第三方 ADD/乘法都**叠加**在阶段曲线之上。
- 只改动 4 个 id（本模组 2 个 + 原版保留 2 个），第三方 id 一律不碰；
- 阶段 0 差值 0 → 只移除本模组修饰符（保留基线）；
- 空手时才清理"我们补过"的基线记录，手上有别的武器**绝不清除**其原版基线；
- `INJECTED_BASELINE` 有界（>1024 自动清空）且 `EBCombatHandler.onServerStopped` 调用
  `StageAttributes.clearInjectionRegistry()`。

**新增 `AttributeMathGameTests`（5 条，全绿）**：
阶段 7 基线 1500；+7 ADD → 1507；第三方 1.5× TOTAL → （阶段 3 上）(48+7)×1.5 = 82.5
（阶段 7 会撞 `ATTACK_DAMAGE` 的 2048 上限，故用阶段 3 断言乘法语义）；
重复 5 次 reconcile + 装备 tick 不累加（1500/2.4）；`setBaseValue(10)` 不被重置且阶段仍相对（1509 = 1500+9）；
阶段 0 恰好 7/1.6 且不留我们的修饰符、保留原版基线；攻速 1.2× → 2.4×1.2。
上一轮的 4 条"立即换剑（无装备 tick）"测试全部保留并通过。

## 2. Boss 分类测试改为符合 SPEC（不再声称"高生命=自动 Boss"）

- 确认 SPEC 内核（`BossClassifierCore`）：自动探测 = **生命 ≥ 阈值 且（非空 ServerBossEvent 字段 或 类名含 Boss）**，
  **绝不**只凭生命值 —— 典狱长（500 血）必须留在普通敌对。
- 我上一轮把"500 血 Husk 自动是 Boss"当作 sanity **是错的**，已删除该断言。
- 新增 `src/gametest/.../FixtureEntities.java`（dev-only，**不进入发布 jar**）：
  以**已注册的原版 `minecraft:husk` 类型**构造的自定义子类 —— 自身 bar 字段、**继承的 bar 字段**、
  只有 bar（类名无 Boss）、类名含 Boss 而无 bar、无 bar 无 Boss 名。**没有**为了测试去改 BossClassifier。
- 新增 `BossCompatGameTests`（6 条，全绿）：500 血真实 Husk 仍是普通；bar（自身/继承/无 Boss 名）都被识别为 Boss；
  类名含 Boss（500 血）被识别、无 bar 无 Boss 名 500 血仍普通；强制排除优先于包含/自动、关闭自动后高生命仍普通；
  **dev-only `c:bosses` 标签夹具**（`src/gametest/resources/data/c/tags/entity_type/bosses.json` 只加 `minecraft:stray`）
  → Stray 判定 Boss、Husk 不受影响；两个真实 Boss 实例分别入账（bossKills=2）、普通击杀=0、
  成长点 = 2×配置权重（Boss 独占、每实例各一次）。
- 另在 `GrowthPersistenceGameTests` 中补 `wardenAndHighHealthHusksAreOrdinary`（典狱长 / 500 血 Husk / 199 血均普通）。
- 反射失败的纯单元覆盖沿用已有的 `BossClassifierCore` 测试（未改动）。

## 3. 墙后目标掉 1 点血（已定位为阳光，已修）

- 根因：该墙体测试的 `behindWall` 仍是 `EntityType.ZOMBIE`（亡灵），在等待窗口里被**阳光点燃**掉血。
  与 `AttributeInstance` 并发无关（属性实例是每实体独立的）。
- 修复：墙前/墙后目标都改为 **Husk**（亡灵但不会阳光点燃），并新增断言：
  施法前**两个目标都满血、未着火**（证明没有环境伤害先发生）；墙前目标受到的**唯一伤害来源必须是
  `evolvingblade:sword_ability`**（新增按 UUID 作用域的 `SourceTrace` 监听器记录来源与数值）；
  墙后目标 500 → 500 不变；墙体方块未被修改。未削弱任何墙体断言。
- 附注：`EntityType.HUSK` 的导入与用法已核对映射源码（Husk 继承 Zombie，属亡灵、不受阳光伤害）。

## 4. GUI（按 live-review 第 5 条修复，未运行客户端）

- `ChoiceScreen` 底部改为**三条独立保留带**：按钮 30px、红色锁定警告 24px（最多 2 行）、灰色提示 12px，
  卡片底部让出全部三条带 → 红/灰不再重叠（live-review 指出的 4px vs 9px 重叠已消除）；
  红色警告在**确认之前**就显示（未隐藏），并中英文按宽度换行。
- 卡片内的"滚轮查看"提示改到**卡片顶部的保留带**（内容行从 `top+4+10` 开始），不再压住最后一行内容。
- 中文模式名 `evolvingblade.mode.standard` 改为**「中等」**（id 仍是 `standard`，不影响存档/命令）。
- 卡片内容仍是每个候选模式**全部 8 个阶段**门槛（服务器配置推导）；阶段显示用**阶段名**而非 0/7 索引；
  854×480（逻辑 427×240）下卡片可见约 14 行 + 滚轮，仪表盘按宽度换行。
- **未运行 GUI**（按任务要求不启动客户端、不碰 run-isolated-client）；观感由 Codex 用冻结 jar 复验。

## 5. 服务器配置路径（按实际文件核实）

- 复核结论：NeoForge 21.1 的 SERVER 配置**默认在实例/服务器根目录的 `config/`**，
  `<世界>/serverconfig/` 是可选的按世界覆盖位置。
- 证据（实际落盘，不是记忆）：`run/config/evolvingblade-server.toml` 与
  `run-isolated-client/config/evolvingblade-server.toml` 均存在；而
  `run/world/serverconfig/`、`run-isolated-client/saves/<世界>/serverconfig/` 里**只有 NeoForge 的 `readme.txt`**。
  （本机反编译快照不含 FML 的 `ConfigTracker`，因此以实际文件为准。）
- `README.md` §5 已按此改写；成长数据仍是**每玩家 + 每存档**的 attachment，未改动。

## 边界（不声称整份计划已被接受）

- 本轮**未**运行客户端 `runClient`、未安装到任何实例、未触碰 `run-isolated-client` 与用户原有实例。
- **未**实现 Codex 计划中列出、但明确安排为"后续独立小测试任务"的项：
  两进程持久化重启、死亡克隆/末地返回、剑气与爆发的完整数值矩阵（最多 3 目标/超射程/爆发 32 上限/半径/无吸血等）、
  吸血与斩 Boss 的完整实战矩阵（护甲/吸收后的真实生命损失、取消、20 tick 限速、两玩家独立、终阶 3%/1000）。
  这些是**独立的测试任务**，不是本实现的遗漏谎报。
- 本轮交付的整合测试总数：**52 条 GameTest 全绿** + 83 条 JUnit 全绿；冻结 jar 哈希见文首。
