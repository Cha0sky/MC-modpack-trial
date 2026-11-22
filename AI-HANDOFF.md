# 星庭远征整合包：开发交接文档

更新时间：2026-11-23，Asia/Shanghai。用途：让新对话了解当前整合包状态并协助用户规划下一项开发。**本文件不是新任务、推送、启动游戏或修改存档的自动授权。**

## 1. 接手时先看这里

装备手册持有检测与救赎之戒兼容已部署，实现 `0d700cd`。1,272 个具体物品页按队伍实际持有登记，92 个目录页改为手动已读，旧空任务完成记录在下次进档时校正。序章七选一仍要求真实穿戴。救赎之戒从零阶获得七咒正向资格并按七条固有诅咒计算。已用 Java 21 编译、对本机发行件做离线织入并读回实例任务配置。游戏内验收由用户进行。[玩法与构建入口](docs/mods/饰品扩展与维度选择.md) · [部署回执](evidence/equipment-collection-deployment.json)。

神秘遗物、Relics、星月遗物与 Baubley Heart Canisters 已装入开发版。主线装备手册 → 全饰品目录可进入四条路线，新开 14 册。总计 691 件饰品、450 件盔甲，材料与手持物另有 57 页。实现 `fb00fb6`，实例离线读回通过，实机待用户验收。[版本、玩法与维度候选](docs/mods/饰品扩展与维度选择.md) · [部署回执](evidence/gear-expansion-deployment.json)。

生命水晶、六档首领与恶意成长的第一轮平衡已部署到开发版，核心 `0.9.0-balance`。生命水晶上限 30 颗，旧档继续原计数。首档对玩家单击上限分别为史莱姆王 10、克苏鲁之眼 7、育母蜘蛛 8。莱特兰击杀、距离和维度增长按原来的四分之一计算。已读回配置与实际安装件，游戏体验待用户复验。[逐只平衡表](docs/design/首战数值平衡.md) · [部署记录](evidence/encounter-balance-deployment.json) · [饰品候选](docs/mods/饰品与装备扩展候选.md)。

本轮实测反馈修复已部署。维度网络发生器在序章开局自动发给每位玩家，沿用原领奖编号。按 G 或输入 `/bladefolio` 打开成长页即可登记原剑谱任务，两种剑技冷却减半、距离翻倍，可命中范围内全部有效目标。探险者指南针支持中文搜索“废弃工坊”和“出生集镇”。当前版本待用户实机复验。[部署回执](evidence/starter-sword-compass-deployment.json)。

出生集镇与自然生成工坊的地形优化（`bfc1c32`）已部署到「星庭远征-开发版」，该次部署读回版本为 `0.8.1-terrain`。[部署回执](evidence/structure-terrain-deployment.json)。集镇按全部 13 块地的起伏及街区落差选址，住宅空院角保留原地形。工坊最多试四个朝向，按主、侧门落脚处和楼体轮廓定高，只在实际底板下短距补基。新版本实机待用户验证。改造前的 13 地块集镇生成已有用户验收。[集镇选址](docs/design/starter-town.md) · [工坊生成](assets/workshop-rekindled/README.md)。

开发版序章至第六章共 123 项任务（含支线），旅人手记有 11 页，随身手册含 14 页首领战册、27 页装备资料。史莱姆王开放边境，血肉墙或血肉山开启困难模式。进度核心为 `0.9.0-balance`，成长剑为 `1.1.6-phaseblade`，加点手册为 `1.0.1-fieldguide`。

2026-11-23 的科技线从工坊送料、供水试装，接到困难矿的精炼与施工订单，前线可收机械动力货运回执。实现为 `eb17a3e`，已备份部署。旧任务与奖励 ID、各路线可达性及新配方已对实例检查，机器现场动作和服务器收件待用户实机验证。第七章草稿单独保存在工作区。[玩法指引](docs/design/campaign-field-guide.md) · [部署回执](evidence/production-campaign-deployment.json)。

原有 27 页装备手册和主线的 7 处入口继续可用。目录新增「护甲图鉴」与「饰品合成图」入口，收录 432 件盔甲和 500 件饰品。盔甲按单件护甲分七档，饰品按 261 组直接用料关系排图。实现 `567abe3` 已部署，已用实例中的 FTB Library 读回章节、翻页与分级，实机交互待用户验收。[部署回执](evidence/equipment-atlas-deployment.json)。

任务图滚轮修复已部署。FTB Quests 与 FTB Library 均为 `2101.1.35`，可在章节任务图上用 Ctrl＋滚轮缩放。游戏内手感待用户确认。[部署回执](evidence/ftb-quest-zoom-deployment.json)。

噬星之刃已换成玩家确认的 16×16 直身长剑。八种形态共用第一人称持握模型，左右手镜像露出刃面。已备份并更新开发实例，手持观感待用户实测。[成长剑部署回执](evidence/blade-pixels-deployment.json)。

用户认为**序章的玩法骨架基本完成**：玩家在聚落持剑醒来，探索、下矿，完成两条起步制作任务，击败史莱姆王后开放猩红与腐化。旧任务文件和代码中的 Chapter 1 指这一段序章；界面已改称“序章”，任务 ID、存档键和文件名保持。新的第一章是“重启第一座工坊”。

| 内容 | 当前状态 | 接手时怎么处理 |
|---|---|---|
| 原六地块聚落、标准路线成长剑 | 原版修复经过用户验收，已推送 | 只作已知能工作的基线；不能把旧验收套到新集镇 |
| 工作台史莱姆皇冠、王后两种指南针 | 已部署，用户说「已经试过」 | 不把重测当成前置；暂无单独的逐项验收记录 |
| 80×65 格、13 地块新集镇 | `207b45a` 修复后，用户明确反馈“已经能正常生成了” | **集镇生成已验收**；不扩大为新工坊或白名单验收 |
| 无限之核首版白名单 | `a03ba0c` 已实现，用户本轮明确要求部署，现已安装 | **已部署、未实机验收**；安装文件仍名为 1.1.2，包内版本为 `1.1.2-whitelist` |
| 第一章“重启第一座工坊” | 19 项，三种机器操作，任选一种送料，现场读数或摄影后校准三处节点 | 已部署，待实测 |
| 第二章“遗产调查” | 25 项，工程、奥术、远征任选二，工程增加供水、试装和带载调速 | 已部署，待实测 |
| 第三章世界转折 | 15 项，战前工兵或先锋，战后精炼、熔坯、锯切 | 已部署，待实测 |
| 第四章困难模式整备 | 11 项，用边料制模，采掘铺轨或护卫布灯，再实地检验装备 | 已部署，待实测 |
| 第五章遗址远征 | 10 项，手记、现场摄影或首领战果支持回访制图 | 已部署，待实测 |
| 第六章前线补给站 | 19 项，三项业务任选二，真货到站可取回执，凭纸领用库存并拆包交班 | 已部署，待实测 |
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
3. 用户明确要求使用 Stop That Shit 减少无意义检查：只做必要编译和相关逻辑/数据检查，哈希不再作为例行步骤，不重复全目录核对，不为收集证据扩展测试矩阵。
4. **最终游戏实机由用户操作。** 不自行启动客户端、服务器、创建/进入测试世界或运行 GameTest；另有明确授权时仅执行所授权项目。交付时给短验收清单。
5. 本地任务完成要提交 Git；推送远端需用户明确指示。不要把此前某次推送授权延伸到所有未来提交。
6. 新任务的仓库修改与部署按实际授权执行；第一章及两个新模组已明确授权安装；本轮按用户“继续推进，执行规划”推进第二章。部署确认游戏退出，先备份再定向替换并保留简短回执，不改原实例或自动处理用户存档。
7. 不提交、覆盖、撤销其他对话已有改动。只 stage 明确路径；已有别的暂存内容时，用限定本任务路径的提交方式，避免混入。
8. 用户曾在本对话的建筑重构任务明确允许 subagent 降低主对话上下文压力。接续时按当前任务许可使用，清楚划分文件所有权，主代理整合；不能把历史授权当作任意无关任务的委派授权。
9. 用户允许围绕玩法从模组社区选择并加入合适模组，不限既有清单；第一章已确认纳入 Explorify 与 Exposure。后续仍围绕获准任务推进，对已授权范围不逐项重复索批，优化留待需要时处理。

交接文档本身不授权修改、测试或推送；本轮第二章已部署，第一、二章均保留待实测状态，实机由用户负责。

## 4. Git 与运行包：区分已推送、已部署、已验收

仓库：`https://github.com/Cha0sky/MC-modpack-trial`，远端名 `MC`，分支 `main`。

当前核心为 **0.9.0-balance**，安装文件名为 `world-progression-core-0.1.0.jar`。第三章扩充实现提交为 `b8779bc`。

| 内容 | 实现/记录 | 当前状态 |
|---|---|---|
| 原六地块与开局修复 | `6b963f8` / `efd212f` | 已验收、已推送的历史基线 |
| 皇冠与指南针 | `dabc805` / `386d341` | 已部署，用户已试过 |
| 无限之核白名单 | `a03ba0c` / `50276e3` | 已部署，未另获实机验收 |
| 13 地块集镇及生成修复 | `8b70bc7` / `207b45a` | 用户已确认正常生成 |
| 工坊第一章 | `526a5ab` | 已部署，待实机验收 |
| 遗产调查第二章 | `9685e01` / `e96e9c2` | 已部署，待实机验收 |
| 任务书清理、奖励扩充、工坊与奥术修复 | `22d2df8` | 已部署，待实机验收 |
| 第三章世界转折、第二章骷髅领主选项 | `9f22763` | 已部署，待实机验收 |

本轮备份并更新三个模块、七份章节及两份语言文件。[部署回执](evidence/campaign-refresh-deployment.json)。

首次部署预检发现 FTB 已把语言自动写回标准无逗号 SNBT，旧JSON读取器停止且未替换文件。`tools/quest_language.py` 现在同时读取JSON与FTB语言SNBT，第一、二章部署工具共用；当前语言键及转义字符串已定向检查并保留，不覆盖其他章节内容。

本地产物（均未跟踪）：

- `modules/world-progression-core/build/libs/world-progression-core-0.7.1-campaign.jar`：当前核心。
- `staging/mods/confluence-otherworld-1.2.4-260226.jar`：从开发版复制的汇流来世编译依赖，Gradle 同时读取它内嵌的 Confluence Magic Lib。
- `staging/mods/Explorify v1.6.6.mod.jar`、`staging/mods/exposure-neoforge-1.21.1-1.9.21.jar`：新增模组，官方来源和依赖见 `staging/mods/downloads-workshop.json`。
- `modules/infinitecore/build/libs/infinitecore-1.1.2-whitelist.jar`：上一轮已部署的白名单，本轮未替换。

历史集镇修复/白名单备份和哈希仍见 `evidence/starter-town-fix-whitelist-deployment.json`。这些运行包及备份只在本机，不随 GitHub 源码克隆提供；不能用旧文件名推断实际包内版本。

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
| `modules/world-progression-core` | 当前部署构建参数为 0.9.0-balance。第三章运行时读取汇流来世 1.2.4。其余依赖继续使用 Create 6.0.10、Exposure 1.9.21 和 Iron’s Spellbooks 1.21.1-3.16.3 |
| `modules/evolvingblade` | 1.1.4-longsword；成长剑、成长路线与幂等赠剑API。核心调用现有API，不另塞一把剑 |
| `modules/rpgleveling` | 1.0.1-fieldguide；角色成长与属性加点。旧构建/数值检查是历史证据，不是本轮新测试 |
| `modules/infinitecore` | 1.1.2；根据遗失源码模组重新实现，**不是恢复的原始源码**。直接模板、八阶生成器、主动IO、拆除掉落修正见模块README |
| `vendor/infinitecore` | 保留原1.0.0二进制作为来源基准，不替换成重制版冒充原件 |
| `integration` / `sanctuary` | 既有整合配方/庇护石原型，保留待评估，不等于新阶段系统已完成 |

无限之核保留直接材料合成模板。白名单包中，`GenerationTargets` 让模板绑定和机器产出共用一份[物品白名单](modules/infinitecore/src/main/resources/data/infinitecore/tags/item/replicable_resources.json)。22 项原料为泥土、原版主世界原木、常见石材、煤炭及粗铁、粗铜、粗金；不含矿石方块、模组矿物、金属锭和加工品。泥土、木、石级保留默认产出，铜级以上须绑定目标。手动、自动输入和旧模板都走同一产出判断；旧的非法目标停产并提示，模板及库存保留，可用合法材料重新绑定。没有按世界阶段放行资源的机制，也没有对外 ItemHandler 能力；已有主动 IO，可用于后续配合机械加工。

第一章新增 Explorify 1.6.6、Exposure 1.9.21；其余关键版本沿用：Create 6.0.10、Create Aeronautics 1.3.2、Confluence 1.2.4（文件带 260226，历史审计确认内嵌 Terra Entity 1.2.1）、Mowzie's Mobs 1.8.2、Lootr 1.11.38.127，两种 Compass 均为 1.21.1-3.4.0-neoforge。完整历史快照见 [2026-10-16 模组清单](evidence/development-mods-2026-10-16.json)；世界进度核心以最新部署回执为准。

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
- 初版新档无集镇的修复见[生成修复说明](docs/fixes/starter-town-generation-2026-11-16.md)：重叠提前过滤；各栋在作者位置周围搜索 33 个候选；预览全部 13 处，每站点最多 104 次、全局仍最多 384 次地形探测；只放行已核实的太阳花和松树藤蔓，保留结构和其他方块实体保护。修复版已部署，用户已确认正常生成。

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
- 醒来自动收到维度网络发生器，可在任务书领取面包、火把与原木。两制作分支汇合后可领取批量物资。王后道路节点直接接到第一章，两种指南针可选。
- 全章显式 `linear` / `all_completed`。任务ID由固定种子hash并限制为正有符号long；过去未屏蔽高位会使FTB重分配ID、丢依赖，不能再直接用任意16位hex。
- 旧五章原型已移出任务书，定义保存在 `docs/archive/legacy-quests`。

### 史莱姆王、世界门槛

- 根Boss实体：`terra_entity:king_slime`。普通史莱姆、仆从和取消的死亡不算；死亡结束时提交世界共享记录，避免取消事件/重复击杀问题。
- 邪恶地形原本就生成，首领之前禁止进入与常规交互；击败后开放。标签为 `data/world_progression_core/tags/worldgen/biome/locked_evil_biomes.json`，包含Confluence的腐化/猩红及其沙漠、冻土共六种，不是下界原版猩红森林。
- `ChapterData` 保存键为 `lostworld_chapter1`，schema3包含实际床坐标并兼容旧偏移；玩家首次到达记录与Boss世界记录独立。
- 不反复调用原生 `setGamePhase`，不改Confluence困难模式/矿物转换；当前不是完整多阶段框架，旧区块补矿未实施。

## 6.1 新第一章：重启第一座工坊

10 个主线＋5 个可选支线，详细目标和奖励见[玩家说明](docs/design/workshop-rekindled.md)。4 区域原木经磨石得到 8 浸染纤维，不加热混合得到 4 共振胶；1 胶制作粗制调谐器，3 胶修复同一座工坊的三节点。

- 调谐器在主世界对空气右键领取/补领地图；对设备长按 3 秒校准。三个节点都读正面邻接 Create 轴端的有效转速，至少 8 RPM，方向节点须正转。成功才扣胶。
- 工坊为原生单片 jigsaw 结构，只在新生成区块出现，支持继续旧档；普通建筑可拆改，四个关键设备固定。三节点校准且有动力后操作中枢，解锁世界共享里程碑和维修簿，工程奖励在 FTB 领取。
- 玩家进度独立持久保存并在死亡复制；材料峰值结合已消费量和确定配方产物记录，允许提前准备和协作。摄影记录本人拍摄的三个不同已打印照片，支持叠放；Explorify 同类变体归一，探索三类完成。钢铁守护者是可选支线，不是正典封印守护者。
- Java 21 编译与离线检查通过。未运行客户端、服务器或 GameTest，现场生成、传动、校准、任务界面与奖励均待用户实测。

## 6.2 第二章：遗产调查

[玩家说明](docs/design/heritage-survey.md)含14节点及奖励。第一章世界工坊恢复后开启；三条路线任意两条完成即可汇合，第三条可选。路线事实保存在 `lostworld_heritage`，按世界共享，同步每条路线的两个步骤与记录节点。

- 工程：装有烈焰人的燃烧室＋8黄铜；实际使用机械手和注液器＋取得精密构件。原生精密构件仍为5轮机械手、80%结果，注液是独立实践，不改其配方。
- 奥术：Iron铭文台／铁质书／实际装备＋法术书释放一次，或Goety黑暗魔杖＋召唤僵尸仆从＋仆从击杀。两种方法只计同一奥术路线，不能交叉混凑。监听Iron非取消的实际释放事件，不声称命中证明。
- 远征：钢铁守护者室、附魔剑冢、沙漠遗址、古代工厂任选两类。原有五位自选首领加上诡厄的骷髅领主，仍是任选一位。前章钢铁守护者记录和骷髅领主的原生击败成就均可接续。
- 档案台用普通书、玻璃、铜板、安山机壳、木板和共振胶制作，无黄铜门槛。两线后右键整理／更新书，潜行右键选地点，主手空地图右键制图。只存每类已发现遗迹的首个坐标，不凭空定位未知结构；数据独立于可拆改的档案台。
- 当前没有新增大结构、模组或困难模式改写。Java 21 build／verifyLogic及相关数据检查通过，实际界面、施法/战斗判定、档案与地图由用户测试。

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
| `staging/config/ftbquests/quests/chapters/00_lostworld_chapter1.snbt` | 现称序章，ID保留；JSON语法作为SNBT输入有效 |
| `tools/build_workshop_quests.py` / `01_workshop_rekindled.snbt` | 新第一章15节点、配方、进度占位和外观资源 |
| 同 Java 目录 `Workshop*` | 玩家任务、世界里程碑、设备注册、调谐器、地图与中枢交互 |
| `tools/build_workshop_structure.py` / `assets/workshop-rekindled` | 工坊MCP资产与标准NBT/worldgen转换 |
| `tools/deploy_workshop.py` | 第一章六文件定向备份部署、语言合并，无文件哈希 |
| `tools/build_heritage_quests.py` / `02_heritage_survey.snbt` | 第二章14节点、档案台配方与外观 |
| 同Java目录 `Heritage*` | 世界两线汇合、玩家事实、原生动作、档案书与地图 |
| `tools/deploy_heritage.py` / `tools/quest_language.py` | 第二章四文件部署；兼容JSON和FTB语言SNBT |
| `tools/deploy_quest_refresh.py` | 本轮备份、更新三章任务书并移出五个旧原型 |
| `tools/build_relic_expeditions.py` / `tools/deploy_relic_expeditions.py` | 生成第五章与两位首领的历史战果判定；部署时只替换核心和任务书相关文件 |
| `tools/build_world_turn_quests.py` / `tools/deploy_world_turn.py` | 生成第三章和状态进度文件，并单独部署核心、新章与语言 |
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
.\gradlew.bat --offline --no-daemon --console plain '-Pmod_version=0.7.1-campaign' jar verifyLogic -x createMinecraftArtifacts
Pop-Location
```

离线依赖缓存已存在才能用 `--offline`。核心还依赖仓库内 `modules/evolvingblade/build/libs/evolvingblade-1.1.0.jar`，以及 `staging/mods` 中 Create 6.0.10、Exposure 1.9.21、irons_spellbooks-1.21.1-3.16.3.jar 作为编译类路径；Ponder直接复用Create内嵌包，不另安装。集镇构建用本机已有 NeoForge 21.1.248 开发产物；同 SDK 产物已核实时，可用 `-x createMinecraftArtifacts` 跳过重建，缺失时不可照抄。无限之核在自己的模块目录运行构建，白名单测试包的版本参数为 `'-Pmod_version=1.1.2-whitelist'`。这两个模块的 `verifyLogic` 都不启动游戏。不开新世界、不为了绕过缓存问题擅自升级工具链。

本次编译发现旧的本地开发 SDK 在 javac 读取 ChatFormatting 时有无效的参数注解。已备份该构建缓存，复用同仓库 `modules/infinitecore/build/moddev/artifacts/neoforge-21.1.248.jar` 的同版本开发 SDK，编译及离线逻辑检查通过。

部署前确认版本、目标和游戏退出，备份后定向复制即可；不例行运行哈希。当前语言包含第一、二章键，游戏可自动重写为标准SNBT；使用共享 `read_language`，不能假定外部语言仍是JSON。各章部署只合并对应键，保留其他键；不照抄旧皇冠/指南针语言基线。旧 `deploy_chapter1.py` 是历史工具，不用于当前章节。

## 10. 设计方向与尚未做的事

长期定位：固定基地生活/女仆/农夫乐事等生活内容＋Create生产建设＋原创世界观中的少量关键Boss硬门槛＋大量可选冒险。航空学保留为小型载具/玩具，**完整移动基地暂不开发**；不要按旧交接恢复飞行基地架构。

2026-10-16 用户确认[机械动力与资源分工](docs/design/create-resource-progression.md)：基础原料供应与实际加工分开，保留泥土/木材生产和普通熔炉，部分关键道具必须经过机械动力。首版白名单已在源码落地，原矿限粗铁、粗铜和粗金；首个机械动力关键道具已确定并实现为粗制调谐器；第二章工程路线已采用原生精密构件及设备实践；动力合成器等仍属后续候选。

叙事唯一作者基准：`docs/lore/失落世界_世界观设定_v1.0.md`。保留【已确定】【暂定】【建议】【待定】，不能把建议变正典；世界没有原初意志，不能改成世界意志选中勇者的故事。“薪火”来自人类文明积累。史莱姆王和Mowzie钢铁守护者都不自动等于设定中的封印守护者。当前用户要求文字暂缓，更不应借新任务补写设定。

困难模式的首轮整备已部署。后续仍待确定下一阶段的远征节奏、阶段配方、旧区块补矿以及高阶首领的推荐时机。

### 接手后的建议顺序

1. **接收第一、二章实测反馈。** 用户继续当前存档；先完成工坊，再看三线任选二、第三线可选、档案书和已发现地点地图。若有阻断，按具体反馈读相关日志并修复，不扩大测试矩阵。
2. **保留已有验收边界。** 新集镇生成已通过，皇冠和指南针用户已试过。白名单已部署但未另获实机验收，不要求为本章重测全部历史内容。
3. **接收第三、四章实测反馈。** 第三章仍由汇流来世控制世界转折，用户使用备份存档实测。第四章在取得钴锭或钯金锭后开放，先检查两种整备路线任选其一及选修砧的体验，第五章已实现双线远征。先驱者、焰魔和使徒留作后期。
4. **发布另开任务。** 当前没有制作PCL可导入安装包或发布Release；GitHub源码ZIP不是整合包安装包。

接续摘要：集镇生成已验收；第一至第六章已部署待实测。本次更新优化序章至第六章，第七章草稿保留在工作区，部署构建已排除草稿。
