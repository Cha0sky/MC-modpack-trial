# 给接续 AI 的开发进度与规划说明

更新时间：2026-10-16，Asia/Shanghai。用途：让新对话了解当前整合包状态并协助用户规划下一项开发。**本文件不是新任务、推送、启动游戏或修改存档的自动授权。**

## 1. 先读这一页的结论

- 项目已经进入实作阶段，不能再当成“尚未建包/尚未开发核心”的空白项目。
- 初始聚落、六处建筑、新版主角住宅、默认标准路线持剑、史莱姆王前后邪恶地形门槛均已实现。农田生成检查导致的崩溃已修复，用户随后反馈“目前实现很好，可以推送远程仓库了”。该聚落版本已推送。
- **最新一项**是工作台史莱姆皇冠配方、叙事占位框架、王后两种指南针合成任务：已实现、已打包、已获退出/更新确认并部署；**尚无这项新增内容的用户实机反馈，尚未推送远端。**
- 用户最新决定：**文字叙述暂缓，先搭可玩的框架并留占位符。** 不要接着写完整剧情、NPC对白、前言长文或新增开局补给。
- 当前接续应先询问最新配方/指南针的实测结果，再提出小范围下一步方案。不要重做已经接受的聚落，或再次要求统一地坪。

推荐阅读顺序：本文件 → [AGENTS.md](AGENTS.md) → [STATE.md](STATE.md) 顶部最新记录 → [PLAN.md](PLAN.md) 的设计方向 → 用户选定任务对应源码。`PLAN.md` 中部分进度表/勾选项和旧 `HANDOFF.md` 已过时，不能据此否定后续实现；实际状态以本说明、最新 STATE、源码与部署回执交叉判断。

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

本次交接只更新文档，不改代码或实例，不启动测试，不自动推送。

## 4. Git 与运行包：区分已推送、已部署、已验收

仓库：`https://github.com/Cha0sky/MC-modpack-trial`，远端名 `MC`，分支 `main`。

编写本文前，本地 HEAD 为 `386d341`；本轮实际 `git ls-remote --heads MC main` 核对远端为 `efd212f`。本地领先2个功能/部署提交；本文提交还会追加文档提交，接手时重新看 Git，不要把这里的 HEAD 当成永不变化的值。

| 提交 | 内容 | 状态 |
|---|---|---|
| `04c3fc9` | MCP 建筑资产和离线导入 | 已推送；其最初接入方式后来被修正 |
| `d69fe97` | 统一地坪整村方案 | 已推送的历史失败方案，**不是当前策略** |
| `1c37fc6` | 独立地坪且六种必需，赠剑解耦 | 已推送；其农田严格检查后来修正 |
| `6b963f8` | 作物最后放置，修复农田检查崩溃 | 用户确认当前表现良好，已推送 |
| `efd212f` | 记录用户确认 | 当前已核对的远端 main |
| `dabc805` | 工作台皇冠＋两种指南针任务＋占位 | 本地已提交、已部署，待本项实机反馈；未推送 |
| `386d341` | 记录该次获批部署 | 本地已提交，未推送 |

开发实例当前核心为 `world-progression-core-0.1.0.jar`，本轮只读核对SHA-256：

`1f3333cefa2b335425d65134445feccfe0e61f12da90a7f0c1144aa59c3516cb`

版本号仍为0.1.0，历史多次构建同名；必须用回执/哈希辨认，不能仅凭文件名认定是旧版本。当前核心、第一章配置及两份语言与 `evidence/chapter1-crown-compasses-deployment.json` 四个文件的哈希均一致。

最新备份：`backups/chapter1-crown-compasses-20261016-161449`。它保存本次四个目标文件的上一版本。无需为最新任务/配方更新重建世界；旧11节点ID保留，未重置存档进度。

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
| `modules/world-progression-core` | 0.1.0；初始聚落、开局赠剑、第一章独立Boss记录/邪恶地形门槛、最新配方和合成进度数据 |
| `modules/evolvingblade` | 1.1.0；成长剑、成长路线与幂等赠剑API。核心调用现有API，不另塞一把剑 |
| `modules/rpgleveling` | 1.0.0；角色成长与属性加点。旧构建/数值检查是历史证据，不是本轮新测试 |
| `modules/infinitecore` | 1.1.2；根据遗失源码模组重新实现，**不是恢复的原始源码**。直接模板、八阶生成器、主动IO、拆除掉落修正见模块README |
| `vendor/infinitecore` | 保留原1.0.0二进制作为来源基准，不替换成重制版冒充原件 |
| `integration` / `sanctuary` | 既有整合配方/庇护石原型，保留待评估，不等于新阶段系统已完成 |

无限之核不再使用空白模板流程；保留直接材料合成模板。模块README明确尚无对外ItemHandler能力及世界阶段资源黑白名单，不要在规划中假定这些能力已经存在。它有主动IO，可据实际实现另行设计自动化衔接。

当前只读核对开发实例：82个启用JAR、2个停用JAR。重要第三方版本：Confluence 1.2.4（文件带260226，内嵌Terra Entity 1.2.1）、Mowzie's Mobs 1.8.2、Lootr 1.21.1-1.11.38.127、Explorer's Compass与Nature's Compass均1.21.1-3.4.0-neoforge。完整历史快照在 `evidence/development-mods-2026-10-16.json`；此后自制核心哈希已更新，以最新回执为准。

两种Compass曾发现内部Minecraft依赖范围标签与文件名冲突，记录在 `docs/mods/2026-10-16-instance-changes.md`。这是待确认的静态发现，不是已证明的当前启动故障；不要无报告就改JAR、换版本或绕过加载器检查。

## 6. 已有的第一章玩法框架

### 开局与聚落

- 玩家为村民收留的失忆旅人，身边原有一把成长剑；这是用户给定开场。完整文字暂不创作。
- 新章节世界自动选择 `GrowthMode.STANDARD`（用户称标准/中等路线）并通过成长剑API赠剑。**房屋生成失败也不能让默认路线重新可选。** 已有路线、计数与赠剑状态保留，不强制重置旧玩家。
- 只有真实房屋存在才传送进屋、设床和记录到达。无标记的旧普通世界不自动改造。
- 六处必须齐全：新版玩家住宅、两栋村民住宅、农田、水井、扩建空地。用正式MCP模板，禁止旧木屋后备、禁止跳过农田。
- 用户已允许各栋不同地坪和有限位置调整，以道路连接。当前先规划齐六栋和通路再写方块；不是把整个43×40场景统一整平。
- 源为 `assets/settlement/emberstead/emberstead.schem`，Sponge v2、DataVersion3955；操作源、layout与两个真实MCP预览同目录。场景美术工作名“余烬驿”，不等于新增正典地名。
- 运行时读取离线转换后的 `modules.json`，无需WorldEdit；原JSON内为工具兼容保留的小型后备数据，运行时已过滤，不代表仍会用旧小屋。

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

- 探索：离家至少96格；下矿：在低于聚落地面至少12格处实际用镐开采，并持有32圆石、8煤炭、6粗铁。
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

## 7. 最新改动：已部署、尚待实测

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
| 漂亮预览落地却是旧木屋、农田离散/缺失 | 只用正式六模块；不使用compact主屋、不允许缺件；用户允许独立地坪，不再强求整片统一 |
| 找不到地块后路线重新可选 | HIGHEST登录事件先做模式/赠剑，再判断建筑；通过GrowthService幂等交付，不手动重复塞剑 |
| `Incomplete MCP component: farm` 崩溃 | 农田结构253块原本正确，缺的是30株作物。先完成结构/道路，作物最后写入，只有作物用flags18；结构检查保留，作物变化记录警告而非致命异常 |
| 任务书中间断线或提前计数 | ID用正long、显式线性依赖；新指南针额外检查首领解锁进度。不要重生成旧ID或把全书宽松默认当作本章策略 |

旧崩溃/暂停文档是历史，不代表当前仍暂停或问题未处理。旧失败存档可能已有放置但未登记完成的结构，不擅自清理或反复重试制造重复村落。建筑修改通常用新测试世界；最新仅配方/任务更新则可以沿用正常已有世界。

## 9. 代码与工具入口

相对正式仓库根目录：

| 入口 | 用途 |
|---|---|
| `modules/world-progression-core/src/main/java/local/lostworld/ChapterEvents.java` | 世界标记、启动生成、登录/赠剑、任务进度、Boss死亡及边界事件 |
| 同目录 `ModularSettlementBuilder.java` | 六种强制部件、分栋选址、地基/路、结构后种植；12个主屋实际候选/320次总地块检查，有具体拒绝日志 |
| 同目录 `ChapterData.java` / `ChapterState.java` / `EvilBoundary.java` | 持久化、当前单Boss状态与邪恶区域限制 |
| `tools/build_chapter1_quests.py` | 生成13节点、两份语言、配方及进度条件；修改后需同步生成，不只手工改产物 |
| `staging/config/ftbquests/quests/chapters/00_lostworld_chapter1.snbt` | 当前第一章；JSON语法作为SNBT输入有效，游戏也可能写回标准SNBT |
| `modules/world-progression-core/src/main/resources/data/world_progression_core` | recipe、advancement、标签、settlement生产资源 |
| `tools/import_settlement_schem.py` | Sponge v2/v3离线转模板、锚点/空箱/床检查，写路径限制仓库 |
| `tools/build_chapter1_settlement.py` | 已接MCP源优先导入；部分旧整片fixture仅供历史测试钩子，不是生产聚落 |
| `tools/deploy_chapter1.py` | 定向备份、哈希、部署；`--core-only`仅核心，`--skip-chapter`不写任务定义 |
| `tools/audit_mod_changes.py` | 只读实例模组，写日期清单；不是自动安装器 |

只在获准任务需要时运行生成/构建。资源数据变更可选 `jar`，Java逻辑变更用必要构建/相关检查；不顺手全模组重编译或跑GameTest。已用构建环境：

```powershell
# 在正式仓库根目录，且已获本项任务批准后按需使用
$env:JAVA_HOME = 'C:\Gradle\jdk-21-temurin'
$env:GRADLE_USER_HOME = Join-Path (Get-Location) 'work\build-baseline\gradle-user-home'
Push-Location '.\modules\world-progression-core'
.\gradlew.bat --offline --console=plain jar
Pop-Location
```

离线依赖缓存已存在才能用 `--offline`；核心已配置Eclipse编译路径。不要擅自升级依赖来绕过工具链问题。

部署语言更新时 `--language-base-ref` 应对应**当前实例语言的旧来源版本**。最新已部署内容对应 `dabc805` / `386d341`；`efd212f` 是这次更新前的基线，不应永远固定照抄。遇自定义语言冲突要保留和定位，不强行覆盖。先检查脚本实际写入范围，获确认再 `--apply`。

## 10. 设计方向与尚未做的事

长期定位：固定基地生活/女仆/农夫乐事等生活内容＋Create生产建设＋原创世界观中的少量关键Boss硬门槛＋大量可选冒险。航空学保留为小型载具/玩具，**完整移动基地暂不开发**；不要按旧交接恢复飞行基地架构。

叙事唯一作者基准：`docs/lore/失落世界_世界观设定_v1.0.md`。保留【已确定】【暂定】【建议】【待定】，不能把建议变正典；世界没有原初意志，不能改成世界意志选中勇者的故事。“薪火”来自人类文明积累。史莱姆王和Mowzie钢铁守护者都不自动等于设定中的封印守护者。当前用户要求文字暂缓，更不应借新任务补写设定。

尚未实施/未定：第二章正式框架与第二Boss、完整多阶段映射与配方阶段门槛、旧区块补矿、后续资源/科技平衡、Mowzie任务实装、正式前言物资/教程剧情、可导入安装包。不要把这份列表视为全部立刻开工。

### 下一步规划候选，需用户选择

1. **先收最新一项实测反馈**：工作台双冠皇冠合成、王后两个节点解锁及真实合成检测。若没有问题，记录验收；不要因此要求重测聚落全流程。
2. **邪恶地形开放后的第二章框架**：讨论猩红/腐化的探索目标、资源用途和下一阶段候选，只搭功能节点与占位，不先定新的封印身份/第二Boss。
3. **一条可选Mowzie遗迹支线**：已有候选文档 `docs/design/mowziesmobs-quest-candidates.md`，例如史莱姆王后的钢铁守护者。尚无任务、奖励或世界门槛实施；先明确小闭环、核对实体/原生生成与完成判定。雕刻家原生试炼不能直接用击杀替代通过。
4. **资源生成与Create的小型自动化闭环**：在现有IO能力基础上选一个具体产物/输出衔接，不先做所有阶段或全模组重平衡。
5. **PCL测试安装包与GitHub Release**：适合用户想发给朋友测试时单独开展。

目前没有本对话制作的可导入安装包；前次已查Releases无发布，编写本文也未创建任何发布。Git保存源码、配置、建筑资产、版本/哈希；大多数第三方JAR和构建产物被忽略。GitHub Download ZIP不是PCL安装包，单独clone不能获得完整运行包，不强行把全部下载JAR塞进Git。

接续对话建议先回复用户：已理解聚落版本已获确认、最新皇冠/指南针已部署待反馈且未推送；叙事暂缓。然后给两三个小范围下一步选项，等用户选择后再实施。
