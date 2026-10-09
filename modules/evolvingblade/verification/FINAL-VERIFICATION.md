# 噬星之刃 1.0.0：Codex 独立验收与交付记录

日期：2026-10-05，Asia/Shanghai。结论：已完成源码、构建、独立测试、隔离客户端验收，并安装到 `mod test`。Harness 的回执没有替代独立验收。

## 成品与运行环境

- Minecraft 1.21.1、NeoForge 21.1.248、Java 21.0.7；工程采用官方 ModDevGradle 模板，ModDevGradle 2.0.81、Gradle 8.14.2。
- 成品：`build/libs/evolvingblade-1.0.0.jar`，151792 字节。
- SHA-256：`8244fd6281b071d38f6818d8efe45db6f689fff0437a5c909422eb62f1803cd5`。
- 安装：`D:/PCL 正式版 2.10.6 (1)/.minecraft/versions/mod test/mods/evolvingblade-1.0.0.jar`，与验收成品哈希相同。
- `delivery/` 提供相同 JAR、完整源码 ZIP、验证记录 ZIP 及各文件哈希清单。源码包含 Gradle wrapper、生产资源、测试和开发夹具，不含缓存、运行世界或凭据。

## 独立自动化检查

最终执行日志为 `release-final-independent.log`：83 项 JUnit 成功，失败、跳过、终止均为 0；全部 97 项 required GameTest 通过，构建成功。测试运行于实际 SERVER 发行版 `forgeserverdev` / GameTestServer，已验证客户端类隔离。未另外启动常规生产 dedicated server。

| 范围 | 验证结论 |
|---|---|
| 三种节奏、八阶段 | 24 个阶段边界、成长点与 Boss 双条件、重复同种 Boss、普通/Boss 独占计数及实际服务端属性通过 |
| 保存与玩家隔离 | 实际死亡克隆、末地返回附件复制、多人独立数据、模式/赠剑/技能冷却/追加伤害限速保存通过；客户端冷重启另有 NBT 证据 |
| 选择与赠剑 | 模式锁定、重复意图包、管理员改模式不额外赠送、背包满保持待领取并重试通过 |
| 击杀归属 | 玩家、投射物、归属明确的宠物；环境/玩家死亡、取消、重复通知、复活、长死亡动画、过期来源通过；末影龙和凋灵有实际实体测试 |
| 当前持有者属性 | 不同成长玩家交换同一把剑、首次攻击及时更新、显示数据不能决定强度、保留其他属性加成通过 |
| Boss 识别 | 默认龙/凋灵、名单、模组标签和 c:bosses、ServerBossEvent/继承类名、200 血阈值、强制排除优先、监守者普通敌对通过 |
| 吸血 | 满蓄力、真实损血、护甲/吸收/取消、5%/10% 与上限、非主要近战和技能排除通过 |
| 弑王 | 实际最大生命 1%/3%、200/1000 上限、穿甲但保留抗性/无敌/取消、19/20 tick 边界、玩家-目标隔离、不递归通过 |
| 剑气 | 实际伤害、24 格射程、墙体、前后方排序、单体/最终 3 实体、友军过滤、冷却及即时快照通过 |
| 爆发 | 6/8 格半径、150% 实际伤害、32 有效目标上限、墙体/玩家/队友/驯服生物排除、方块不变及冷却通过 |
| JAR 审计 | 110 个条目、Java class major 65、八张不同的 32×32 剑纹理、语言与元数据、正确无序配方；没有开发 GameTest/夹具/NBT 结构泄漏 |

对应方法与限制详见 `../docs/TEST-MAPPING.md`，审计机器记录为 `artifact-audit.json`。Codex 修正了独立死亡回归夹具的日晒干扰：401 tick 的复活测试从僵尸改用 Husk，原断言保持完整，生产 JAR 不受此夹具修改影响。

## 实际客户端验收

Codex 使用获授权的原生 Minecraft 窗口操作和实际截图完成验收。Harness 没有运行这些 GUI 验收。

- 首次选择快速/中等/长期、全部八阶段门槛、二次确认、取消及一次赠剑；G 面板、八阶段剑身纹理和当前持有者提示均已实际检查。
- 中文最终 JAR 在 1920×1080 和 854×480、GUI 缩放 2 下检查。英文相同 GUI 源码在两种尺寸检查，最终仅提示文案更改。两种小窗口都可滚动看完路线和能力，未逐一检查其他分辨率。
- 实际灵锋剑气击杀 8 格外 Husk：普通击杀和成长点均 30 → 31；证据 `ui-wave-kill-world.png`、`ui-wave-kill-growth.png`。
- 实际爆发粒子效果：`ui-burst-ring-sky.jpg`。技能具体伤害、友军和目标上限由服务端 GameTest 验证。
- 最终中文面板：`ui-final-1920-zh-panel.png`、`ui-final-854-zh-panel.png`；英文选择/滚动/面板：`ui-final-854-en-selection.png`、`ui-final-854-en-route-end.png`、`ui-final-854-en-panel.png`。历史缺陷截图仍保留为返工证据，名称带 `early`、`defect`、`blur` 的文件不是最终效果。

### 冷重启与冷却保存

隔离客户端进程 17980 正常保存退出，再用最终 JAR 启动进程 50736。成长 NBT 的模式 quick、成长点 140、普通击杀 40、Boss 1、gift GRANTED、剑气/爆发截止时刻完全相等，剑数量 1 → 1。

保存前世界时刻 16002，爆发截止 16216，剩余 214 tick（10.7 秒）；再次读取世界时刻 16332，保存/加载/UI 操作期间累计推进了 330 tick。因此重开 G 面板时爆发已经正常就绪，本记录不声称重启后仍显示倒计时。见 `client-restart-verification.json` 和 `qa-cooldown-before/after-*.json`。

## 安装与原文件保护

验收后在游戏进程关闭时安装唯一的本模组 JAR。原 27 个模组 JAR 保留，没有旧版本本模组需要备份。实际安装实例成功加载 35 个模组，并创建全新测试世界 `EvolvingBlade Install QA`。

同一离线 QA 玩家在该新世界选择并锁定中等模式，领取一把剑；G 面板显示 0 成长点、0 普通击杀、0 Boss、基础伤害 7、速度 1.6、下一门槛 50。这同时与隔离世界 quick/140 的记录构成实际每世界隔离证据。见 `installation.json`、`qa-installed-player-nbt.json`、`ui-installed-standard-panel.png`。

没有打开原来的两个生存世界。安装测试结束后客户端已正常关闭，原 `options.txt` 已逐字节恢复；最终原模组及存档基线 115 个文件 SHA-256 全部一致，差异 0。注意这里是 **115 个文件**，不是 115 个模组。见 `instance-originals-verification.json`、`options-backup.json`。新 QA 世界保留供复验。

## Harness 派单与闭环证据

本机 CLI 0.1.5-rc.2、专用 profile `codex-evolvingblade`；运行时目录和执行记录确认沿用 `deepseek-official/deepseek-flash`（DeepSeek-V41-Flash），没有更换模型或输出凭据。单执行者主会话 `7d84d667-1a13-4e03-8828-a182897c9d7a`。ACP 真实握手、文件 revision 1/2、取消、关闭/恢复/读回已通过，详见 `ACP-ACCEPTANCE.md`。

| 派单任务 | 实际验收结果 |
|---|---|
| 014-phase1、015/017 返工 | 工程、成长存储和基础服务端行为；独立 73 JUnit / 19 GameTest |
| 018-phase2、019/020 返工 | GUI、网络、持有者属性、纹理及能力；独立 83 / 31 |
| 021-phase3、022/023 返工 | Boss 兼容、说明及部分验收；实现验收 83 / 52，剩余测试覆盖不足明确保留 |
| 024-combat-acceptance、025/026 两轮返工 | 收紧真实损血/护甲/抗性/限速断言，独立最终 83 / 65 |
| 027-wave-burst-acceptance、028-wave-burst-rework1 一轮返工 | 修复剑气近后方抢占和排序、补完整技能证明，独立 83 / 80 |
| 029-persistence-acceptance | 保存、死亡/末地生命周期、末影龙部位归一化；独立 83 / 97 |
| 030 文案、031 交付说明 | 最终中文提示及说明；031 仅改文档，未改变验收 JAR |

第三阶段初期短任务结束而覆盖不足，随后按原计划剩余范围拆分战斗、技能、保存验收；各独立任务最多两轮自动返工，没有把空闲或 `end_turn` 当作验收。每次回执和事件均保留在工作区 `../../_agent-team/results/`、`runs/`、`events.jsonl`；默认派单上限 45 分钟。初次空模型路由失败也保留在 ACP 验收中。最终主会话关闭及桥接停止回执为 `032-main-close`、`033-bridge-shutdown`，并另记 `agent-team-closeout.json`。

## 未实测范围

第三方 Boss 模组没有逐个安装并战斗实测，只确认名单、标签、反射字段/类名接入和伤害取消机制。真实联网多人会话未实测，玩家隔离来自同一真实服务端内多个 ServerPlayer。常规生产 dedicated server 未另开，实际 SERVER 发行版 GameTest 已成功启动。ACP 桌面会话可见性、SDK、MCP 注册及并行执行者不在本次已验证范围。
