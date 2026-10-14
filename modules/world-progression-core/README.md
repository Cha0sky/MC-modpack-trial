# 失落世界 · 启程（第一章试作）

原创模块 `world_progression_core` 0.1.0，Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21。
本项按用户批准实现起始聚落和第一章，不添加“世界意志”“天选之人”或把史莱姆王绑定为古代守护者。

## 内容与源码

- `ModularSettlementBuilder` + `data/world_progression_core/settlement/modules.json`：逐栋定高的玩家住宅、两个村民住宅、公共农田、水井和扩建空地；安全落地完整时有 3 名村民。`SettlementGeometry` 计算地基和连接路的高差。旧 `starter.json` 和平地 `place` 方法仅保留为历史模板/测试入口，不再作为生产选址方案。
- 玩家住宅包含床、两个空箱和工作台。铁门两侧有按钮，村民不能自行开门。无免费生成器、机械、工具或箱子战利品；共享农田是聚落环境内容。
- `ChapterEvents`：首次到达家中、床重生点、标准成长路线、持剑、外出与下矿记录、史莱姆王死亡后的共享解锁。使用成长武器原有服务交付，不手动复制剑、不重置已有成长路线和计数。
- `ChapterData`：独立存入主世界 `data/lostworld_chapter1.dat`，保存聚落/房屋位置、到达过的玩家 UUID 和首领完成事实。复用存档时不自动重建聚落。
- `EvilBoundary`：在首领完成前，检测邪恶生物群系，退出骑乘并退回安全位置，拒绝边界另一侧的常规方块破坏、放置和交互。聊天提示限频。
- `staging/config/ftbquests/quests/chapters/00_lostworld_chapter1.snbt`：第一章 11 个节点。新章节排在旧原型章节前，旧 ID 和进度保留。

## 当前流程

醒来 → 离开起点至少 96 格 → 石镐 → 聚落地面以下至少 12 格的下矿记录与基础材料 → 泥土模板 / 安山合金两条分支 → 真实合成泥土生成器 / 收集机械动力基础部件 → 领取 1 个维度网络发生器 → 史莱姆王 → 猩红与腐化开放。

泥土生成器通过 `minecraft:recipe_crafted` 记录真正的合成；其余物品节点为不消耗材料的持有检测。机械动力节点为传动杆、齿轮、手摇曲柄、安山机壳各 1 个。前言补给、详细新手教学、复杂生产指标和第二章留待后续批准。

世界首次初始化出生点时仅记录待生成标记，让原版正常选择出生点、准备出生区；聚落在 `ServerStartedEvent`、原版加载进度监听停止后才建造。先寻找 13×13 玩家房屋的独立地块；若不适合，改用仍有床、两只空箱和工作台的 7×9 小屋。屋檐占地分别为 15×15、9×11，其他部件分别选址并定高，不要求 45×39 的整片平地。每处地基最多削高 6 格、垫高 18 格；已存在的结构构件、方块实体和不在天然地形白名单中的材料受保护。完整计划检查通过后才放置。

玩家房屋必须有连接外部地面的出口路。其他部件与房屋之间采用有限路线选项，以台阶/半砖适应高差，单步高度差最多一格；道路不穿过建筑，共用路口保持既有高度。小范围切坡、垫基用于适应山地，不大面积推平。玩家房屋最多检查 16 处（普通屋和小屋各 8），其余每个部件最多 6 处；无安全地块/路线的非核心部件记录警告并跳过，玩家房屋仍优先落地。没有安全玩家房屋时保留原生出生点并记录原因，不强制覆盖。

待生成标记独立保存；失败测试世界可由用户重进重试，已有聚落不重建，没有标记的旧世界不补建。实际床位保存于数据版本 3，并维持旧版床偏移关系以便回滚。首次登录才安置，日常重登不把远征中的玩家送回家。下矿深度以玩家房屋地面为基准。部署更新不直接读写存档；用户进入已标记世界后才按正常游戏流程建造和保存。

此前整片选址方案在用户实测中仍失败。2026-10-15 已获用户批准改为上述分栋方案，详见 `docs/design/chapter1-modular-settlement.md`。只做编译、模板坐标和关键地基/台阶检查；最终落地、通行与视觉由用户验收。

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
