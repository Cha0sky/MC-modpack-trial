# 开发实例模组改动（2026-10-16）

用户要求检查本地模组改动、提交 Git 并与此前修正一起推送至 `MC`。目标为 `星庭远征-开发版`，Minecraft 1.21.1 / NeoForge 21.1.248。本次只读实例；不启动游戏、不安装/替换 JAR、不写配置或存档。

## 本次发现的新增模组

| 模组 ID | JAR 元数据版本 | 用途/当前结论 |
|---|---|---|
| `mowziesmobs` | 1.8.2 | 用户明确新增的冒险生物来源；剧情候选另见下述设计文档 |
| `lootr` | 1.21.1-1.11.38.127 | 元数据描述为每位玩家生成独立战利品；当前文件已归档至清单，未做多人实测 |
| `explorerscompass` | 1.21.1-3.4.0-neoforge | 结构搜索工具；Minecraft 依赖范围存在下述异常 |
| `naturescompass` | 1.21.1-3.4.0-neoforge | 群系搜索工具；Minecraft 依赖范围存在下述异常 |

本地共 84 个模组目录文件，82 个 `.jar` 启用候选、2 个 `.jar.disabled`；没有重复的启用顶层模组 ID。停用的旧 Sodium Extra 0.9.3 与 Starsword 仍保持停用。目录状态和元数据不代替本次新增模组的实际加载验收。

Mowzie 的元数据声明 NeoForge `[21.1.1,21.2)`、Minecraft `[1.21,1.21.2)`、GeckoLib `[4.8.3,)`。现有 NeoForge 21.1.248、Minecraft 1.21.1 和 GeckoLib 4.9.3 满足这些声明范围；没有据此宣称与其他模组的全部交互已通过。

## 与已有快照比较

采用第一章实施前的本地 `work/chapter1/instance-before.json` 为较近参考。为便于远端复查，报告中嵌入其模组子集及原始文件哈希，不公开其中与模组无关的运行/玩家文件。

- 新增：上表四个第三方模组、`infinitecore-1.1.2.jar`、`world-progression-core-0.1.0.jar`。
- 移除：`infinitecore-1.1.0-rebuild.jar`，由 1.1.2 替代；它是先前工程更新，不把它误归因为用户本次新增 Mowzie。
- 其余相同文件名 JAR 的哈希与该快照一致。世界进度核心在这个较早快照中尚不存在；本次哈希与已部署的第一章修正版一致。
- 无限之核 1.1.2 和世界进度核心 0.1.0 均与对应模块当前本地构建 JAR 的 SHA-256 一致。没有重新编译。
- 另外保留相对仓库旧 `evidence/inventory.json` 的文件差异，避免混淆 2026-10-09 的原始清单与当前状态。旧证据不覆盖。

在全局 `config` 与 `defaultconfigs` 未发现 Mowzie、Lootr 或两种 Compass 的独立配置文件，本次没有相关配置内容可同步。没有从存档抓取 serverconfig；不将此结论扩大成“所有配置没有变化”。

## 两种罗盘的依赖声明待确认

两份当前 JAR 的 `META-INF/neoforge.mods.toml` 均声明 Minecraft 范围 `[1.21,1.21.1)`，右端为开区间，按字面不包含目标版本 1.21.1；这与它们的文件名和模组版本标签冲突。本次保存该原始声明，标记为待确认，不擅自修改 JAR、放宽加载器检查或换版本。新增模组本轮未进行启动验证，不能说它们必定能在当前实例加载。

## Git 归档范围

完整文件名、启停状态、文件大小、SHA-256、顶层/内嵌元数据、差异和关联配置摘要保存在 `evidence/development-mods-2026-10-16.json`。重跑工具：

```powershell
python tools/audit_mod_changes.py --baseline work/chapter1/instance-before.json --date 2026-10-16
```

本地比较快照位于被忽略的 `work` 中；远端已有报告内的模组子集可用于后续对比。本工具只读实例并只写仓库内指定日期的清单，不执行旧 `tools/inventory.py` 的主程序或覆盖历史证据。

按现有 `.gitignore` 和 README 约定，第三方 JAR 本体不强制加入 Git，仍留在本地实例；Git 保存清单和哈希。仅克隆 Git 不会自动获得全部第三方模组二进制，复原时需取得清单对应版本并校验哈希。自制模组的源码与构建入口已在仓库中。

Mowzie 主线/支线候选见 `docs/design/mowziesmobs-quest-candidates.md`。建筑外观由用户寻找工具后再讨论，本轮不处理。用户已确认此前第一章问题全部修复，验收见 `docs/feedback/2026-10-16-chapter1-accepted.md`。
