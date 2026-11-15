# 星庭远征整合包：开发交接文档

更新时间：2026-11-16，Asia/Shanghai。用途：让新对话了解当前整合包状态并协助用户规划下一项开发。**本文件不是新任务、推送、启动游戏或修改存档的自动授权。**

## 1. 接手时先看这里

用户认为**序章的玩法骨架基本完成**：玩家在聚落持剑醒来，探索、下矿，完成两条起步制作任务，击败史莱姆王后开放猩红与腐化。任务文件和早期文档称这一段为「第一章」或 Chapter 1，与这里的「序章」是同一段既有内容；暂不为改称序章而重命名任务 ID、存档键或代码。

| 内容 | 当前状态 | 接手时怎么处理 |
|---|---|---|
| 原六地块聚落、标准路线成长剑 | 原版修复经过用户验收，已推送 | 只作已知能工作的基线；不能把旧验收套到新集镇 |
| 工作台史莱姆皇冠、王后两种指南针 | 已部署，用户说「已经试过」 | 不把重测当成前置；暂无单独的逐项验收记录 |
| 80×65 格、13 地块新集镇 | 初版用户反馈新世界有剑但没有集镇；`207b45a` 已修复选址/植物兼容并部署 | **修复版待用户实机验收**。旧集镇存档不会自动换成新版 |
| 无限之核首版白名单 | `a03ba0c` 已实现，用户本轮明确要求部署，现已安装 | **已部署、未实机验收**；安装文件仍名为 1.1.2，包内版本为 `1.1.2-whitelist` |
| 王后探索与机械动力主线、Mowzie 支线 | 设计方向或候选 | 未选定首个机械动力关键配方、下一 Boss 或支线任务 |
| 正式前言、补给和镇内服务机制 | 暂缓 | 只保留任务占位和公共建筑内装，不追加剧情、对白、专属交易或治疗 |

推荐阅读顺序：本文件与 [AGENTS.md](AGENTS.md) → [STATE.md](STATE.md) 最新记录 → 对应部署回执和源码。玩法方向看 [PLAN.md](PLAN.md)；部分早期状态表和旧 `HANDOFF.md` 属于历史。叙事依据始终是作者世界观原文。

## 2. 工作位置与环境

| 项目 | 当前约定 |
|---|---|
| 正式 Git 仓库 | `D:\PCL 正式版 2.10.6 (1)\codex\modpack-project` |
| 常见工具初始 cwd | 上一级 `D:\PCL 正式版 2.10.6 (1)\codex`，它另有 Git 上下文；不要在这里误提交整合包改动 |
| 可部署开发实例 | `D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\星庭远征-开发版` |
| 原始只读实例 | 同级 `mod test`，禁止作为默认修改目标 |
| Minecraft / 加载器 | Java Edition 1.21.1 / NeoForge 21.1.248 |
| 开发 JDK | Java 21；本机已用于构建的完整 JDK 为 `C:\Gradle\jdk-21-temurin` |
| 构建 | 模块各自的 Gradle Wrapper 8.14.2，ModDevGradle 2.0.81 |
| 语言/编码 | 玩家文本中文，文件 UTF-8，Windows PowerShell |

原成长武器工程 `D:\PCL 正式版 2.10.6 (1)\codex\evolvingblade` 与原角色加点工程 `D:\Agent_workspaces\Deepseek Harness\MC_mod` 是保留原件；默认开发必须使用仓库内 `modules` 副本。

历史崩溃报告中出现过 Microsoft Java 25.0.1；编译目标仍是21。这不是已经证明的崩溃原因，不要因此擅自改启动器 Java、全局环境变量或升级加载器。

若工具默认 PowerShell 路径失效，可明确使用 `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe`，`login:false`。中文文件读取指定 UTF-8，路径用 `-LiteralPath`；Python使用 `python`，不要使用 Bash heredoc 或假定 `py` 可用。

## 3. 用户工作规则：要执行得果断，也要守住范围

1. 新任务先说明目标、改动范围、实施步骤、关键验收方式，取得用户同意；已同意范围内自主完成，不反复逐项索要许可。交接文件里的候选不是用户已批准的实施任务。
2. 获准后先在正式仓库检查 `git status --short --branch`、最近提交、工作区和暂存区；修改/构建/部署前仍检查 Git。
3. 用户强调大胆一些，减少多阶段验证和反复复查。只做必要定位、相关关键检查、必要编译及部署文件核对，不为收集证据扩展测试矩阵。
4. **最终游戏实机由用户操作。** 不自行启动客户端、服务器、创建/进入测试世界或运行 GameTest；另有明确授权时仅执行所授权项目。交付时给短验收清单。
5. 本地任务完成要提交 Git；推送远端需用户明确指示。不要把此前某次推送授权延伸到所有未来提交。
6. 仓库修改和外部实例更新分别确认；部署须确认游戏退出，先备份再替换，保留回执/哈希，不改原实例或自动处理用户存档。
7. 不提交、覆盖、撤销其他对话已有改动。只 stage 明确路径；已有别的暂存内容时，用限定本任务路径的提交方式，避免混入。
8. 用户曾在本对话的建筑重构任务明确允许 subagent 降低主对话上下文压力。接续时按当前任务许可使用，清楚划分文件所有权，主代理整合；不能把历史授权当作任意无关任务的委派授权。

交接文档本身不授权修改、测试或推送；本轮另获用户明确授权的生成修复与双 JAR 部署已完成，实机仍由用户负责。

## 4. Git 与运行包：区分已推送、已部署、已验收

仓库：`https://github.com/Cha0sky/MC-modpack-trial`，远端名 `MC`，分支 `main`。

本轮生成修复已本地提交为 `207b45a`，随后完成修复核心与白名单部署，回执见下方。本机远端跟踪引用 `MC/main` 仍为 `efd212f`；本轮未查询服务器实时状态或推送。当前 HEAD 和领先数量以接手时 Git 检查为准，不沿用旧交接的提交计数。

| 提交 | 内容 | 状态 |
|---|---|---|
| `04c3fc9` | MCP 建筑资产和离线导入 | 已推送；其最初接入方式后来被修正 |
| `d69fe97` | 统一地坪整村方案 | 已推送的历史失败方案，**不是当前策略** |
| `1c37fc6` | 独立地坪且六种必需，赠剑解耦 | 已推送；其农田严格检查后来修正 |
| `6b963f8` | 作物最后放置，修复农田检查崩溃 | 用户确认当前表现良好，已推送 |
| `efd212f` | 记录用户确认 | 本机远端跟踪引用 `MC/main` |
| `dabc805` | 工作台皇冠＋两种指南针任务＋占位 | 本地已提交、已部署；用户已说明试过 |
| `386d341` | 记录该次获批部署 | 本地已提交，未推送 |
| `13f4ac0` / `db2c740` | 旧 AI 交接快照、机械动力与资源分工规划 | 本地已提交 |
| `a03ba0c` | 无限之核首版资源白名单 | 本地已提交，现已部署；待用户验收 |
| `8b70bc7` | 初始集镇、模板与运行时生成 | 初版已部署，用户反馈新世界没有集镇，后续修复见下行 |
| `cbf83cd` | 集镇核心备份与部署记录 | 本地已提交 |
| `207b45a` | 集镇植物兼容、局部候选与检查预算修复 | 本地已提交、已部署；待用户实机验收 |

开发实例当前核心文件仍为 `world-progression-core-0.1.0.jar`，内含集镇修复版 `0.1.0-starter-town-fix`（源码 `207b45a`）。部署后核对 SHA-256：

`d48c0b3543906c87c98d78ece28ca349b2d9e5595adde97df17a8ae145ccacd9`

本轮只替换核心和无限之核两个 JAR。第一章任务和两份语言文件沿用皇冠与指南针那次部署，其他模组与存档未改。详见[最新部署回执](evidence/starter-town-fix-whitelist-deployment.json)；两个旧包已备份到 `backups/town-fix-whitelist-20261116-040350-555295/mods` 并核对哈希。上一版核心哈希为 `fcd474f16dab3a7c217766cc85f8a3c6a500e0026289f7655ac550ddbff53a6f`，上一版无限之核哈希为 `99074a8242313ca813abb452484236adb9ab088ebb1b56fbd63a101240de2ae0`。回滚只在获准且游戏退出后恢复对应 JAR；更早的集镇部署仍见原[历史回执](evidence/starter-town-deployment.json)。

| 本地产物（均未跟踪） | 当前用途 |
|---|---|
| `modules/world-progression-core/build/libs/world-progression-core-0.1.0-starter-town-fix.jar` | 集镇修复包，已部署且与安装件哈希一致 |
| `modules/infinitecore/build/libs/infinitecore-1.1.2-whitelist.jar` | 白名单包，已部署；SHA-256 `67f0b84a5918adac451f61a4cd15f5b4ba0ec28f56cd836bcc1ca05ad2c288ff` |

开发实例 `infinitecore-1.1.2.jar` 文件名保留，内容已替换为白名单包，哈希与上表一致。不能通过文件名或 Git 源码版本推断安装内容。两个测试包和备份只在本机，不在 GitHub 克隆中。

### 必须保留的非本任务改动

编写本文时仍有以下历史文件，**不要顺手清理或加入本任务提交**：

- `modules/infinitecore/docs/fixes/2026-10-10-root-error.txt`：同时有暂存与未暂存差异。
- `modules/infinitecore/docs/fixes/2026-10-10-startup-diagnosis.md`：已暂存。
- `modules/infinitecore/docs/fixes/2026-10-10-startup-evidence.json`：已暂存。
- `evidence/chapter1-client-final-launch.json`：未跟踪。

这些文件留着不代表当前功能仍故障。不要 `git add .`、全局重置暂存区或为了“干净状态”丢弃它们；差异检查和提交限定本项路径。

## 5. 当前模块和实际模组

| 目录 / 模块 | 版本与实现边界 |
|---|---|
| `modules/world-progression-core` | 源码默认 0.1.0，当前实例构建标识 0.1.0-starter-town-fix；出生集镇、开局赠剑、序章 Boss 记录、邪恶地形门槛、皇冠配方和指南针合成进度 |
| `modules/evolvingblade` | 1.1.0；成长剑、成长路线与幂等赠剑API。核心调用现有API，不另塞一把剑 |
| `modules/rpgleveling` | 1.0.0；角色成长与属性加点。旧构建/数值检查是历史证据，不是本轮新测试 |
| `modules/infinitecore` | 1.1.2；根据遗失源码模组重新实现，**不是恢复的原始源码**。直接模板、八阶生成器、主动IO、拆除掉落修正见模块README |
| `vendor/infinitecore` | 保留原1.0.0二进制作为来源基准，不替换成重制版冒充原件 |
| `integration` / `sanctuary` | 既有整合配方/庇护石原型，保留待评估，不等于新阶段系统已完成 |

无限之核保留直接材料合成模板。白名单包中，`GenerationTargets` 让模板绑定和机器产出共用一份[物品白名单](modules/infinitecore/src/main/resources/data/infinitecore/tags/item/replicable_resources.json)。22 项原料为泥土、原版主世界原木、常见石材、煤炭及粗铁、粗铜、粗金；不含矿石方块、模组矿物、金属锭和加工品。泥土、木、石级保留默认产出，铜级以上须绑定目标。手动、自动输入和旧模板都走同一产出判断；旧的非法目标停产并提示，模板及库存保留，可用合法材料重新绑定。没有按世界阶段放行资源的机制，也没有对外 ItemHandler 能力；已有主动 IO，可用于后续配合机械加工。

本轮只读核对开发实例：82 个启用 JAR、2 个停用 JAR。关键版本：Create 6.0.10、Create Aeronautics 1.3.2、Confluence 1.2.4（文件带 260226，历史审计确认内嵌 Terra Entity 1.2.1）、Mowzie's Mobs 1.8.2、Lootr 1.11.38.127，两种 Compass 均为 1.21.1-3.4.0-neoforge。完整历史快照见 [2026-10-16 模组清单](evidence/development-mods-2026-10-16.json)；世界进度核心以最新部署回执为准。

两种Compass曾发现内部Minecraft依赖范围标签与文件名冲突，记录在 `docs/mods/2026-10-16-instance-changes.md`。这是待确认的静态发现，不是已证明的当前启动故障；不要无报告就改JAR、换版本或绕过加载器检查。

## 6. 已有的序章玩法框架（代码中的 Chapter 1）

### 开局与聚落

- 玩家为村民收留的失忆旅人，身边原有一把成长剑；这是用户给定开场。完整文字暂不创作。
- 新章节世界自动选择 `GrowthMode.STANDARD`（用户称标准/中等路线）并通过成长剑API赠剑。**房屋生成失败也不能让默认路线重新可选。** 已有路线、计数与赠剑状态保留，不强制重置旧玩家。
- 只有真实房屋存在才传送进屋、设床和记录到达。无标记的旧普通世界不自动改造。
- 集镇保留玩家住宅、两栋村民住宅、农田、水井、扩建空地，新增议事厅、餐厅、诊所、商店、粮仓、工匠铺、镇口，共 13 处，用正式 MCP 模板。
- 继续采用独立地坪；先规划齐 13 处，再规划入口路网与镇口出口，才开始写方块。地势允许时主街加宽，不铺整镇统一地坪。
- 新源为 `assets/settlement/starter-town/starter-town.schem`，Sponge v2、DataVersion3955。操作源、布局和两张预览同目录；历史六地块资源 `assets/settlement/emberstead` 保留。
- 运行时读取离线转换后的 `modules.json`，无需WorldEdit；原JSON内为工具兼容保留的小型后备数据，运行时已过滤，不代表仍会用旧小屋。

### 新集镇如何接着改

- 用户指定继续用 **Minecraft Builder MCP**（工具名 `mcp__mc_builder_d_test`）。平地预览为 80×65 格，导出尺寸 80×21×65；预览底板和示意路不进运行时模板。建筑相互朝向，游戏道路由地形决定，不要求复制平地预览的路形。保留两版原件，别拿旧 43×40 的 `emberstead` 当当前生产模板。
- 输出链：`assets/settlement/starter-town/generate_operations.py` → `operations.json` 和 `layout.json` → Minecraft Builder 以 Java 1.21.1 导出 `.schem` → `tools/import_settlement_schem.py` → 核心的 `settlement/modules.json`。布局 schema 2 的锚点允许四向入口；原 schema 1 仍供旧资产重导。
- 命名与入口来自作者布局和 `SettlementLayout.TOWN_PARTS`；公共建筑先做能走进的内装，容器留空。13 处都要选址并连道路，镇口要能穿向野外。不要恢复静默缺件或旧小屋后备。地形允许时主街加宽；农田最后放作物。
- [集镇说明及两张预览](docs/design/starter-town.md)和[导入回执](assets/settlement/starter-town/build-report.json)随仓库保存。Java 21 离线构建与 `verifyLogic` 检查通过；还验证了打包内的 13 处地块、平地门口连通、公共内装可达、镇口和 110 株作物的数据。这些不是实际世界生成结果。
- 初版新档无集镇的修复见[生成修复说明](docs/fixes/starter-town-generation-2026-11-16.md)：重叠提前过滤；各栋在作者位置周围搜索 33 个候选；预览全部 13 处，每站点最多 104 次、全局仍最多 384 次地形探测；只放行已核实的太阳花和松树藤蔓，保留结构和其他方块实体保护。修复版已部署，待实机验收。

### 任务图：当前13节点

```text
在小屋里醒来 → 走出聚落 → 准备下矿 → 第一趟矿洞
                                        ├─ 材料的另一种用法 → 第一个泥土生成器 ─┐
                                        └─ 机械动力的起点 → 一套基础机械方块 ─┴─ 为旅途建立后方
                                                                                 ↓
                                                               第一次首领挑战：史莱姆王
                                                                                 ↓
                                                               通往猩红与腐化的道路
                                                                  ├─ 合成自然罗盘
                                                                  └─ 合成探险者指南针
```

- 探索：与家相距超过 96 格；下矿：在低于聚落地面至少 12 格处实际用镐开采，并持有 32 圆石、8 煤炭、6 粗铁。
- 泥土模板为直接材料合成；泥土生成器检测真实合成记录，当前为8张泥土模板围绕橡木木板。
- Create分支要求4安山合金，以及传动杆、齿轮、手摇曲柄、安山机壳各1个，当前主要检测持有。
- 两制作分支汇合后，手动确认节点领取1个 `beyonddimensions:net_creater`。这是**本章**唯一物品奖励；没有新增前言食物/火把礼包。
- 全章显式 `linear` / `all_completed`。任务ID由固定种子hash并限制为正有符号long；过去未屏蔽高位会使FTB重分配ID、丢依赖，不能再直接用任意16位hex。
- 旧五章原型仍保留，不应当作当前主线或新世界阶段已实现。

### 史莱姆王、世界门槛

- 根Boss实体：`terra_entity:king_slime`。普通史莱姆、仆从和取消的死亡不算；死亡结束时提交世界共享记录，避免取消事件/重复击杀问题。
- 邪恶地形原本就生成，首领之前禁止进入与常规交互；击败后开放。标签为 `data/world_progression_core/tags/worldgen/biome/locked_evil_biomes.json`，包含Confluence的腐化/猩红及其沙漠、冻土共六种，不是下界原版猩红森林。
- `ChapterData` 保存键为 `lostworld_chapter1`，schema3包含实际床坐标并兼容旧偏移；玩家首次到达记录与Boss世界记录独立。
- 不反复调用原生 `setGamePhase`，不改Confluence困难模式/矿物转换；当前不是完整多阶段框架，旧区块补矿未实施。

## 7. 已部署的皇冠配方与合成任务

### 工作台史莱姆皇冠

新增 `world_progression_core:slime_crown_workbench`，8个 `confluence:gel` 围绕1个 `confluence:gold_crown` 或 `confluence:platinum_crown`，得到1个 `confluence:slime_crown`。金冠/铂金冠已有原生工作台配方，不需要先进入被封锁的祭坛区域。

原祭坛20凝胶＋皇冠配方保留。工作台8凝胶是新增成本，不要描述成仍消耗20；也不要在普通原版配方里加堆叠数量条件，却实际只扣1个材料。原版配方书获得凝胶后可解锁新条目，JEI可查看。

### 两种指南针

| 任务 | 物品ID | 必须实际制作的原始配方ID |
|---|---|---|
| 合成自然罗盘 | `naturescompass:naturescompass` | `naturescompass:natures_compass` |
| 合成探险者指南针 | `explorerscompass:explorerscompass` | `explorerscompass:explorers_compass` |

两节点各依赖王后的“通往猩红与腐化的道路”，用 `minecraft:recipe_crafted` 并检查玩家已有 `world_progression_core:unlock_evil` 进度。**王前合成、只持有/拾取、修复原有罗盘不计入。** 王后合成、稍后才在任务书解锁时，记录可保留。没有全局禁止玩家提前合成/使用罗盘，锁的是任务和该任务的合成记录。

每个节点加入叙事占位，保留实用操作要求，不写新剧情。最新说明与核对记录：`docs/design/chapter1-crown-compasses.md`、`evidence/chapter1-crown-compasses.json`、对应deployment回执。

## 8. 已解决的坑：避免重新引入

| 历史问题 | 当前处理 / 不要退回的做法 |
|---|---|
| 世界进度100%长时间无法进入 | `CreateSpawnPosition` 只请求标记；实际放置在 `ServerStartedEvent`，不要把大量同步取块放回早期生成事件 |
| 漂亮预览落地却是旧木屋、农田离散/缺失 | 用当前 13 处正式模板；不使用 compact 主屋、不允许缺件；继续独立地坪，不要求整镇统一整平 |
| 找不到地块后路线重新可选 | HIGHEST登录事件先做模式/赠剑，再判断建筑；通过GrowthService幂等交付，不手动重复塞剑 |
| `Incomplete MCP component: farm` 崩溃 | 农田结构253块原本正确，缺的是30株作物。先完成结构/道路，作物最后写入，只有作物用flags18；结构检查保留，作物变化记录警告而非致命异常 |
| 任务书中间断线或提前计数 | ID用正long、显式线性依赖；新指南针额外检查首领解锁进度。不要重生成旧ID或把全书宽松默认当作本章策略 |

集镇世界如果没有生成，先问是否为**部署后创建的新世界**、是否仍持剑，再看该实例 `logs/latest.log` 有无 `Terraced starter town 13/13` 或 `Terraced settlement pending`。后者含候选次数与拒绝原因（如 `missing-...`、`street-...`、`town-exit`），按实际失败点改。旧崩溃/暂停记录是历史；已放置部分建筑但未登记的失败存档不擅自清理或重复生成。仅配方/任务更新一般可沿用正常存档。

## 9. 代码与工具入口

相对正式仓库根目录：

| 入口 | 用途 |
|---|---|
| `modules/world-progression-core/src/main/java/local/lostworld/ChapterEvents.java` | 世界标记、启动生成、登录/赠剑、任务进度、Boss死亡及边界事件 |
| 同目录 `ModularSettlementBuilder.java` | 13 处必需地块、分栋选址、广场路网和镇口、结构后种植；最多 12 个主屋候选和 384 次总地块检查 |
| 同目录 `ChapterData.java` / `ChapterState.java` / `EvilBoundary.java` | 持久化、当前单Boss状态与邪恶区域限制 |
| `tools/build_chapter1_quests.py` | 生成13节点、两份语言、配方及进度条件；修改后需同步生成，不只手工改产物 |
| `staging/config/ftbquests/quests/chapters/00_lostworld_chapter1.snbt` | 当前第一章；JSON语法作为SNBT输入有效，游戏也可能写回标准SNBT |
| `modules/world-progression-core/src/main/resources/data/world_progression_core` | recipe、advancement、标签、settlement生产资源 |
| `tools/import_settlement_schem.py` | Sponge v2/v3离线转模板、锚点/空箱/床检查，写路径限制仓库 |
| `tools/build_chapter1_settlement.py` | 当前优先导入 `starter-town` 资产；旧整片样板仅用于历史工具兼容 |
| `tools/deploy_chapter1.py` | 定向备份、哈希、部署；`--core-only` 仅核心，`--skip-chapter` 不写任务定义。脚本源 JAR 路径写死为普通 0.1.0，不会自动选择带后缀的测试包 |
| `tools/audit_mod_changes.py` | 只读实例模组，写日期清单；不是自动安装器 |

只在获准任务需要时运行生成/构建。资源数据变更可选 `jar`，Java逻辑变更用必要构建/相关检查；不顺手全模组重编译或跑GameTest。已用构建环境：

```powershell
# 在正式仓库根目录，且已获本项任务批准后按需使用
$env:JAVA_HOME = 'C:\Gradle\jdk-21-temurin'
$env:GRADLE_USER_HOME = Join-Path (Get-Location) 'work\build-baseline\gradle-user-home'
Push-Location '.\modules\world-progression-core'
.\gradlew.bat --offline --no-daemon --console plain '-Pmod_version=0.1.0-starter-town-fix' build
Pop-Location
```

离线依赖缓存已存在才能用 `--offline`。核心还依赖仓库内 `modules/evolvingblade/build/libs/evolvingblade-1.1.0.jar`。集镇构建用本机已有 NeoForge 21.1.248 开发产物；同 SDK 产物已核实时，可用 `-x createMinecraftArtifacts` 跳过重建，缺失时不可照抄。无限之核在自己的模块目录运行构建，白名单测试包的版本参数为 `'-Pmod_version=1.1.2-whitelist'`。这两个模块的 `verifyLogic` 都不启动游戏。不开新世界、不为了绕过缓存问题擅自升级工具链。

部署前检查具体 JAR 的版本和哈希，从实例备份旧文件并确认该模组仅有一个启用 JAR；不同文件名不能证明是不同模组。部署语言更新时 `--language-base-ref` 应对应**当前实例语言的旧来源版本**：本轮双 JAR 部署未改语言，最近一次语言更新对应 `dabc805` / `386d341`，不要照抄更早的基线。遇自定义语言冲突保留并定位。先看脚本写入范围，获授权再执行；本轮用户已明确授权修复与白名单部署，不将本轮授权延伸到未来任务。

## 10. 设计方向与尚未做的事

长期定位：固定基地生活/女仆/农夫乐事等生活内容＋Create生产建设＋原创世界观中的少量关键Boss硬门槛＋大量可选冒险。航空学保留为小型载具/玩具，**完整移动基地暂不开发**；不要按旧交接恢复飞行基地架构。

2026-10-16 用户确认[机械动力与资源分工](docs/design/create-resource-progression.md)：基础原料供应与实际加工分开，保留泥土/木材生产和普通熔炉，部分关键道具必须经过机械动力。首版白名单已在源码落地，原矿限粗铁、粗铜和粗金；首个机械动力关键道具（如精密构件或动力合成器）的用途及配方仍待选定。

叙事唯一作者基准：`docs/lore/失落世界_世界观设定_v1.0.md`。保留【已确定】【暂定】【建议】【待定】，不能把建议变正典；世界没有原初意志，不能改成世界意志选中勇者的故事。“薪火”来自人类文明积累。史莱姆王和Mowzie钢铁守护者都不自动等于设定中的封印守护者。当前用户要求文字暂缓，更不应借新任务补写设定。

尚未实施或未定：序章之后的主线框架和下一位 Boss、完整多阶段映射与配方阶段门槛、旧区块补矿、后续资源与科技平衡、Mowzie 任务、正式前言物资和文字、诊所治疗与自定义商店、可导入安装包。

### 接手后的建议顺序

1. **先接收集镇修复版验收反馈。** 初版新世界有剑但无集镇，`207b45a` 已修复并部署。用户确认自宅持剑醒来，广场与公共建筑齐全并可走入，农田有作物、路能穿过镇口。若再次失败，先按第 8 节读最新日志，再提有针对性的修复；不重做已认可的建筑。
2. **验收已部署白名单。** 用户实机看泥土默认产出、旧铁锭模板停产且库存/模板保留、重新绑定粗铁恢复。皇冠和指南针已试过，无需因此重测。
3. **设计王后第一个探索和生产小闭环。** 从猩红或腐化取得一种有用途的资源，回到基地完成一次真正的机械加工，做出一个推进主线的关键道具。先选资源、加工设备及产物，再决定任务依赖；配方不循环，生活用普通熔炉始终可用。不先定封印身份、下一 Boss 或完整阶段表。
4. **可选支线或发布另开任务。** [Mowzie 候选](docs/design/mowziesmobs-quest-candidates.md)中可考虑王后的钢铁守护者；雕刻家试炼不能直接以击杀视作完成。若用户想发给朋友测试，可另做 PCL 可导入安装包与 GitHub Release。目前没有本对话制作的安装包；未在本轮核查发布页。GitHub Download ZIP 含源码和配置，不是可直接导入的整合包。

新对话可先用一句话复述：「序章骨架已成，集镇生成修复与白名单均已部署待用户验收，王后机械动力主线待设计。」然后根据用户反馈提出一项小任务，等待批准。
