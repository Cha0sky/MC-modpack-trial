# COMBAT-ACCEPTANCE 返工 2/2 报告（最终限定返工）

> 只改了 `src/gametest/.../CombatAcceptanceGameTests.java` 与验证文档；**未改动任何生产实现**
> （`src/main` 无改动，因此本轮 jar 内容与上一轮一致，哈希差异只来自构建时间戳/打包顺序，见文末说明）。

## 最终实测

| 项目 | 命令 | 结果 |
|---|---|---|
| 单元测试 | `gradlew clean build` | **BUILD SUCCESSFUL**；`EBTEST tests=83 succeeded=83 failed=0 aborted=0 skipped=0 containersFailed=0 -> exitCode=0` |
| 集成测试 | `gradlew cleanGameTestWorld runGameTestServer`（全新世界、单 `defaultBatch`） | **`All 65 required tests passed :)`** |
| 冻结 jar | — | `build/libs/evolvingblade-1.0.0.jar`，**149,652 字节 / 109 条目 / SHA256
  `3DC9039753CCB5258012A661963D66C6EEC7E5F90762C3A130A2820C0DF2CC71`**；扫描无 GameTest/夹具/NBT |

日志：`verification/combat-rework2-build.log`、`combat-rework2-final.log`；
失败与修复过程保留在 `combat-rework2-run1..4.log`。证据表：`verification/COMBAT-ACCEPTANCE.md`。

## 逐条落实（4 处不足 + 次要目标）

1. **限速真实时钟**：记录 `StarEaterAbilities.clock(first)` 起点，`runAfterDelay(19)` 后断言
   **`now - start == 19` 且仍拒绝**，再 `runAfterDelay(1)` 断言 **`later - start == 20` 且接受**；
   同 tick 第二次仍拒绝、第二玩家独立允许、目标全程存活。**不再只是注释改名。**
2. **低于上限的精确百分比**：阶段 5 的 10000 HP Boss 断言 **`|postDelta - raw| < 0.5`**（raw=100），
   终阶断言 **`< 1.0`**（raw=300）；raw=100/300 与上限 200/1000 断言全部保留。
3. **穿甲证明**：改为比较**两次第一击各自的 bonus Post 生命差**——无甲与有甲都 **`==100` 且彼此相等**，
   同时保留"第二击（同 tick 限速隔离出的主伤）因护甲变小"，两个目标全程存活。
4. **防递归/不吸血**：观察器扩展为**按 UUID 同时观察该目标的全部伤害类型**（主伤 + bonus 分开计数）：
   主伤被 Pre 压到 **40**（主 Post 恰好 1 次）、bonus **incoming==1 / post==1 / Post 生命差==100**、
   一次攻击总真实损失 **==140**、**回血恰好 2.0（40×5%）**；随后同 tick 再次入口断言**新增事件 0**。
   不再存在"bonus 完全没触发也算通过"的空间。
5. **次要目标**：直接对比**主攻击后与次要命中后的玩家血量严格相等**（容差 0.01），
   并断言主目标非致命存活、次要目标受到真实伤害（30 点）。

## 本轮修掉的测试自身问题（保留真实记录）

- 限速用例 18+2 与报告的 19/20 不符 → 已改 19+1 并做真实时钟断言。
- `MAX_HEALTH` 是 RangedAttribute，**上限 1024**：原先把目标写成 5000 实际只有 1024，
  导致"非致命"前提被打破（一次 1500 伤害直接打死目标）→ 改用 500 HP 目标 + 阶段 3 玩家做次要目标用例。
- 顺手修正：观察器原先在 Pre 改写**之前**记录数值 → 现在记录**实际生效**的伤害；
  次要目标用例原先在攻击**之后**才取"攻击前血量" → 已修正取值顺序。

**未改动生产代码**：`src/main` 本轮零改动（因此本轮结论对生产行为无影响，纯测试与文档强化）。

## 剩余未完成 / 边界

- 超大生命仍是 **dev-only 夹具**（仅覆写 `getAttributeValue` 的 MAX_HEALTH 分支），**不代表**与真实第三方
  Boss 模组集成；未安装任何第三方 Boss 模组。
- 次要目标是**模拟**（同 tick 真实伤害、非 PRIMARY 目标），不是原版真实横扫动画路径。
- 抗性期望 60% 为原版 float 数学（容差 1.0）；限速断言依赖 `runAfterDelay` 的真实世界 tick。
- 本任务**不包含** wave/burst 完整矩阵与重启/持久化（后续独立任务）。
- 未运行 GUI、未安装、未触碰任何实例；release jar 内无任何 dev 夹具。
