# PHASE 3 交付报告 —— 噬星之刃

> 执行方：DeepSeek Harness（唯一实现者，无委派）。范围：PHASE 3 任务书（GUI 真实缺陷修复与内容补全、
> 时钟/同步复查、有意义的服务器集成测试、中文交付文档、发布产物）。
> 本报告只写**实际执行并看到结果**的内容；未验证项集中在 §7，且**不声称 UI 已验收**。

## 1. 实测结论速览

| 项目 | 命令 | 结果 |
|---|---|---|
| 生产构建 + 单元测试 | `gradlew clean build` | **BUILD SUCCESSFUL** |
| JUnit 5 | （`clean build` 内 `ebTest`） | `EBTEST tests=83 succeeded=83 failed=0 aborted=0 skipped=0 containersFailed=0 -> exitCode=0` |
| 无头服务器集成测试 | `gradlew cleanGameTestWorld runGameTestServer` | **`All 33 required tests passed :)`**（BUILD SUCCESSFUL；单批次、全新世界） |
| 发布 jar | — | `D:\PCL 正式版 2.10.6 (1)\codex\evolvingblade\build\libs\evolvingblade-1.0.0.jar`，**148,647 字节**，**SHA256 = `DCDF9CCA7AA2B04B11B3591307C1C15AC8F13052EC566678A3EC9FB37833D6ED`**，109 条目 |
| 发布 jar 内容 | ZipFile 逐条扫描 | **无** GameTest 类 / `.nbt` 夹具 / 测试源码 / tools 生成器 / 旧占位贴图 |

日志：`verification/phase3-build.log`、`verification/phase3-gametest.log`。

## 2. 修复的真实 GUI 缺陷（Codex 在隔离客户端确认过）

**根因（一手映射源码）**：`Screen.render()` 会先调用 `renderBackground(...)`；原版实现会对世界做
**高斯模糊**。此前子类在 `render()` 里先画文字、最后才 `super.render(...)`，于是模糊把已经画好的文字糊掉，
而按钮在之后绘制、盖住了卡片描述。

**修法**（`ChoiceScreen` 与 `DashboardScreen` 都改）：

1. **覆写 `renderBackground(...)`**：只画纯色遮罩，不调用原版背景 → 不会再有模糊。
2. **层序反转**：`render()` 先 `super.render(...)`（背景 + 按钮），**之后**再画标题/卡片/文字 →
   文字永远在按钮之上、且不在模糊之后绘制。
3. **卡片不再使用原版大按钮**：改为自绘矩形 + `mouseClicked` 命中检测（原版按钮会盖住描述）；
   确认/取消仍是原版按钮，放在卡片下方，不重叠。
4. **长文本按宽度换行**：中英文都用 `font.split(component, width)` 换行（不只是纵向滚动），
   854×480（GUI scale 2，逻辑 427×240）下也能读全；卡片与仪表盘都可用滚轮翻页。

## 3. 界面内容补全（全部数据来自服务器快照）

- **三选一卡片**：每个候选模式显示该模式**全部 8 个阶段**的成长点/Boss 门槛
  （`StateSnapshot` 新增 3×8 点门槛 + 3×8 Boss 门槛数组，服务器配置推导，客户端不硬编码）。
- **仪表盘能力描述**：不再只有"名称 + 就绪"，而是显示真实数值——
  剑气（伤害/射程/冷却/终阶冷却/最多命中）、吸血（比例/上限/强化比例/上限）、
  爆发（伤害/当前半径/终阶半径/冷却/终阶冷却/目标上限）、斩 Boss（比例/上限/终阶比例/上限）、击杀回血数值。
  这些值随快照下发（`StateSnapshot.ability`，索引常量集中在 `EBPayloads`）。
- 网络仍然只有 **C2S 选择意图（枚举）+ S2C 权威状态快照**，没有新增客户端权威接口。
- 移除了产出 debug/占位文本：`ChoiceScreen` 里的旧"终局数字 + 模糊路线"文案、`ability_ready_value`
  等未用键已删除（双语键集合保持一致）。

## 4. 时钟与同步（PHASE 2 返工 2 已修，本阶段复核）

- `EBPayloads.snapshotFor()` 已改为 `StarEaterAbilities.clock(player)`（主世界 `gameTime`）。
  复核全部使用点：`EBCombatHandler`（Post 用 `clock`；tick 清理用主世界；`pruneRateTable` 用 `clock`）、
  `EBKillCreditHandler`（主世界）、`StarEaterAbilities`（唯一入口）。
- `GrowthService.syncAfterChange(...)`：**任何计数/模式/阶段变化**都立刻"协调持有者属性 + 下发快照"
  （不再只在升阶时同步，也不等 20 tick 的周期协调）。
- 回归测试：`snapshotCooldownUsesOverworldClockAcrossDimensions`（含传送到下界）、
  `counterChangeReconcilesHeldSwordStatsImmediately`、`ordinaryKillUpdatesSnapshotCountersImmediately`。

## 5. 本阶段新增的集成测试（3 条，全部通过）

| 测试 | 覆盖 |
|---|---|
| `waveStopsAtWallAndNeverHitsWhatIsBehindIt` | 真实放置 5×3 石墙：墙前目标被剑气命中、**墙后目标血量不变**、墙体未被破坏 |
| `sameStackUsesHolderStageAndUnequipRemovesOnlyOurModifier` | 同一把剑在终阶持有者手上**真实属性值** = 1500 / 2.4（不是只看数学数组）；卸下后只剩下"玩家基础 1.0 + 第三方 3.0"，第三方修饰符保留 |
| （加上 PHASE 2 返工 2 的 5 条：计数器/快照/时钟/夹具自检×2） | 见 `result-phase2-rework2.md` |

夹具约束遵守：**单一 `defaultBatch`**、结构 NBT 仍为 LIST&lt;INT&gt;、`empty` 9×5×9 与
`boss_arena` 48×32×48（中心 (24,16,24)）已加载且实体可 tick、监听器按 UUID 作用域、
事件/配置在测试内自行复位、**不**做龙的手动 tick/阶段覆盖。

本阶段修过的测试自身问题（保留记录）：`sameStackUsesHolderStage...` 第一次失败，
原因是原版**在实体 tick 里**才把物品自带的属性修饰符挂到装备槽上，测试没有推进 tick 就读属性
（读到的 1494 = 少了物品基线 +6）。改成真实推进一次玩家 tick（`doTick()`）后再断言真实值，**没有放宽断言**。

## 6. 文档

- 新增中文 `README.md`：玩法与操作、三模式八阶段门槛表、能力默认数值表、合成配方、
  配置位置（单人 `saves/<世界>/serverconfig/evolvingblade-server.toml`、专服 `config/evolvingblade-server.toml`）、
  管理员命令、Boss/敌对判定规则与 `bosscheck` 原因查询、第三方 Boss 模组"机制支持但未实测"、
  后期安装不追溯历史击杀、源码构建（含中文路径 Gradle `@argfile` 变通说明）、实测与未测边界、
  开发客户端复验指引（`gradlew runClient`，工程自己的 `run/` 目录，建议 zh_cn + GUI scale 2 + 允许作弊的新世界）。
- 生产元数据/注释中的 PHASE 占位说明已清理（`EBItems`、`ChoiceScreen` 注释等；`README` 中不含实现术语）。

## 7. 未验证 / 未完成（诚实边界）

- **GUI 观感仍未验收**：本阶段只做了**源码层**修复（层序、换行、自绘卡片、快照数据补全），
  **没有再次真机运行客户端**，因此"文字不再模糊/按钮不再压字/854×480 与 1920×1080 与中英文下无重叠"
  仍需 Codex 真机复验；`verification/ui-phase2-blur.png` 是修复前的证据。
- **没有启动 `runClient`**（用户当时在用聊天，且任务要求不自动化桌面）。开发客户端已**准备好**（见 README §10），
  但"客户端能加载全部注册/配置界面"仍属未验证。
- **专服 Dist 不加载客户端类**：仍为代码级结论（`@Mod(dist = CLIENT)` + 客户端事件 + `FMLEnvironment` 守卫），
  未跑 `runServer`；GameTestServer 已多次成功启动（服务器 Dist）。
- **持久化跨重启**：只有编解码单测（JsonOps），**没有**真实存档往返/重启场景脚本。
- **任务书列出的其余集成覆盖尚未实现**（明确未完成，不声称）：
  三模式八阶段**真实边界**与双门槛组合、多人独立 attachment、凋灵/末影龙的**真实死亡入账**、
  标签夹具、自定义 Boss 类夹具、死亡取消/复活/重复通知的更多组合、背包满/创造模式赠剑矩阵、
  重连与管理员的赠剑重复性、技能伤害的护甲/无敌/抗性/事件取消矩阵、剑气多目标上限与视线细节、
  爆发的 32 目标上限与地形不变性、`ServerBossEvent`/反射失败安全等。
  现有 33 条 GameTest 覆盖的是其中的一部分（击杀入账与取消、Boss 分类基础、赠剑待发/发放、
  来源事实与回血/吸血、时钟与同步、阶段属性与第三方修饰符保留、剑气墙体阻挡、夹具几何与区块自检）。
- **第三方 Boss 模组**：未实际安装验证。
- 因此：**不声称模组已完成验收**；UI 与上述集成覆盖需要后续轮次/Codex 独立验证。
