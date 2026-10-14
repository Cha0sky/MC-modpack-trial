# 失落世界 · 启程（第一章试作）

原创模块 `world_progression_core` 0.1.0，Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21。
本项按用户批准实现起始聚落和第一章，不添加“世界意志”“天选之人”或把史莱姆王绑定为古代守护者。

## 内容与源码

- `SettlementBuilder` + `data/world_progression_core/settlement/starter.json`：45 × 39 的原版方块聚落，玩家住宅、两个村民住宅、3 名村民、公共农田、水井、路径、照明与扩建空地。
- 玩家住宅包含床、两个空箱和工作台。铁门两侧有按钮，村民不能自行开门。无免费生成器、机械、工具或箱子战利品；共享农田是聚落环境内容。
- `ChapterEvents`：首次到达家中、床重生点、标准成长路线、持剑、外出与下矿记录、史莱姆王死亡后的共享解锁。使用成长武器原有服务交付，不手动复制剑、不重置已有成长路线和计数。
- `ChapterData`：独立存入主世界 `data/lostworld_chapter1.dat`，保存聚落/房屋位置、到达过的玩家 UUID 和首领完成事实。复用存档时不自动重建聚落。
- `EvilBoundary`：在首领完成前，检测邪恶生物群系，退出骑乘并退回安全位置，拒绝边界另一侧的常规方块破坏、放置和交互。聊天提示限频。
- `staging/config/ftbquests/quests/chapters/00_lostworld_chapter1.snbt`：第一章 11 个节点。新章节排在旧原型章节前，旧 ID 和进度保留。

## 当前流程

醒来 → 离开起点至少 96 格 → 石镐 → 聚落地面以下至少 12 格的下矿记录与基础材料 → 泥土模板 / 安山合金两条分支 → 真实合成泥土生成器 / 收集机械动力基础部件 → 领取 1 个维度网络发生器 → 史莱姆王 → 猩红与腐化开放。

泥土生成器通过 `minecraft:recipe_crafted` 记录真正的合成；其余物品节点为不消耗材料的持有检测。机械动力节点为传动杆、齿轮、手摇曲柄、安山机壳各 1 个。前言补给、详细新手教学、复杂生产指标和第二章留待后续批准。

世界首次初始化出生点时仅记录待生成标记，让原版正常选择出生点、准备出生区；聚落在 `ServerStartedEvent`、原版加载进度监听停止后才建造。选址以原版实际出生点为中心，优先平原/向日葵平原/热带草原，实际地形检查每类最多 4 处、合计最多 8 处。避开邪恶群系、水面、陡坡、相交原生结构和方块实体；适度放宽自然地面起伏并同步扩展基础和保护检查范围。如果没有安全地块，保留原生出生点并记录原因，不强制覆盖，也不继续无限搜索。待生成标记独立保存；已有聚落不会重建，没有此标记的旧世界不会自动补建。首次登录才安置，日常重登不把正在远征的玩家送回家。

2026-10-14 的用户复测已确认可以进入世界，但原来的 8 个候选全部被拒绝（6 个水体、1 个起伏、1 个结构）。现增加九点地面预筛：用群系与 `OCEAN_FLOOR_WG` 基础高度预测整块地面是否为干燥陆地，再请求实际区块检查。每类最多预筛 64 处，实际地块上限仍为 8；预筛不会申请区块，也不能代替最终结构/方块实体和完整地面检查。失败世界保留待生成标记，可在用户重进时重试；不替用户修改存档或启动游戏。本次只通过编译与六个水面/起伏边界检查，落地效果由用户验收。

邪恶区域保持原生生成：`confluence:the_corruption`、`the_corruption_desert`、`the_corruption_tundra`、`the_crimson`、`the_crimson_desert`、`the_crimson_tundra`。不包含原版下界绯红森林。根实体为 `terra_entity:king_slime`，不是普通史莱姆或仆从；死亡事件被取消时不解锁。完成记录存档共享、幂等；后来加入的玩家也获得对应任务进度。

这是玩家入口与常规交互门槛，不是修改区块/地形的永久物理墙。管理员旁观者可检查区域；爆炸、其他模组的远程机器或自定义交互不能据此宣称全部受控。没有调用汇流来世阶段 setter、困难模式转换、矿物显现或补矿接口。

## 构建

先从仓库的 `modules/evolvingblade` 构建 `evolvingblade-1.1.0.jar`，本模块编译依赖它。不要修改保留的外部工程。环境与缓存设置见根目录 `BUILD-BASELINE.md`。

```powershell
# 在本模块目录；使用 Java 21 和模块自己的 Wrapper
.\gradlew.bat build --no-daemon --console=plain
```

`gradle.properties` 明确启用本机已验证的 NeoForm Eclipse 编译路径，工具链不升级。
产物 `build/libs/world-progression-core-0.1.0.jar` 只包含主源码/资源；测试类、测试地形标签和测试结构不会进入发布包。
Wrapper 与模板许可证复制自仓库已有模板，原许可证另存 `TEMPLATE_LICENSE.txt`。

可复现内容生成：

```powershell
# 在仓库根目录，仅写仓库；不会写实例或存档
python tools/build_chapter1_settlement.py
python tools/build_chapter1_quests.py
python tools/build_chapter1_test_arena.py
```

## 验证与部署

完整事实与限制见根目录 `CHAPTER1-PROTOTYPE.md`、`evidence/chapter1-verification.json`。
2026-10-13 的后续世界加载修正见 `docs/fixes/chapter1-world-loading-2026-10-13.md`：当前生成流程只做编译和关键状态检查，之前的独立服务器结论属于历史版本；最终游戏启动与实机验收由用户完成。
纯 Java 状态检查由 `build` 自动调用；服务端闭环由 `runGameTestServer` 使用每次独立的 `-PchapterTestDir` 目录执行。
GameTest 的沙漠标签仅用于测试真实生物群系拦截。未加载汇流来世时只能证明独立核心流程，不能冒充真实首领实测。
`-PchapterIntegrationMods` 读取忽略目录 `work/chapter1/integration-mods` 中的只读依赖副本；不会下载或打包第三方模组。
`-PchapterWorldGenSmoke -PchapterServerDir=...` 为测试专用普通世界生成检查，限定到隔离目录，成功后正常保存退出。以明确 PASS 日志和无崩溃报告为验收标准，不能只看 Gradle 退出码。

```powershell
# 先预览；外部部署需要任务授权并退出游戏
python tools/deploy_chapter1.py
python tools/deploy_chapter1.py --apply
```

部署工具只写新的章节模组、新章节及其语言键；先备份、验证源/目标哈希，保留实例独有语言键和旧章节。备份与回退清单在 `backups/chapter1-*/receipt.json`。要回退，在游戏关闭时按清单恢复已有文件，移出本项新建 JAR/章节；不要删除或迁移现有存档。已有世界不自动补建聚落或导入阶段；体验本次开局应新建世界。
