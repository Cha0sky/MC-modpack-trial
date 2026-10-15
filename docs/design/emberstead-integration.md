# 建筑 MCP 聚落实装：余烬驿

最新实测修正：统一地坪方案选址失败，已按用户允许改为六栋独立地坪、道路连接，但六种正式 MCP 部件必须齐全；旧屋后备与可选跳过仍禁用。原始 schematic/预览不变，标准路线赠剑已与建筑成功解耦，最新实现见 `docs/fixes/emberstead-terraced-start-2026-10-16.md`。下文保留最初接入记录。

2026-10-16。用户已批准重构初始聚落、使用 subagent、查看预览后实装并提交 Git。回滚起点 `5741baf`。一个子代理负责建筑/MCP，一个负责离线导入器，主代理完成整合和部署。

## 从 schematic 到初始聚落

MCP 实际导出后缀为 `.schem`。本次源文件是 Sponge v2，DataVersion3955（Minecraft 1.21.1），43×14×40、2096 个非空气方块。原件在 `assets/settlement/emberstead/emberstead.schem`，同目录保留操作 JSON、Python 源、模块边界和两张实际 PNG；中文提示词为 `docs/design/emberstead-build-prompt.md`。“余烬驿”是美术工作名，不扩展世界观正典。

构建时离线解析 gzip NBT / Palette / VarInt，按 layout 拆为玩家住宅、两栋村民住宅、水井、农田、扩建空地，写入核心模组的 `settlement/modules.json`。游戏直接读取模板，不在世界加载时转换 schematic，也不需要 WorldEdit。

继续沿用已修复的加载时机和分栋选址/道路：各模块独立适应地形，完整场景只用于预览，不将43×40整片区域强制整平。原有三个紧凑后备模板保留，极端地形无安全普通地块时可能仍出现旧小屋。旧存档不自动重建，避免覆盖玩家建设。

主屋使用深木骨架、浅色墙、深色坡屋顶、风化铜门廊、窗台与烟囱。保留红床、两个空箱、工作台和屋内出生点，标准路线赠剑不改。木门可开启，任务描述改为通用门廊出入口说明。任务定义、依赖、世界阶段与首领门槛保持现状。

## 重建入口

```powershell
python tools/build_chapter1_settlement.py
python tools/build_chapter1_quests.py
```

原生成命令现在优先导入已有 MCP 资产，避免重跑覆盖新版建筑；历史整片蓝图只用于旧隔离测试钩子。独立导入可运行：

```powershell
python tools/import_settlement_schem.py assets/settlement/emberstead/emberstead.schem assets/settlement/emberstead/layout.json --report work/emberstead-import.json
```

修改操作源后须经 MCP 重新导出 schematic 再导入。只修改 JSON 操作不会自动更新归档的二进制源。源/布局哈希记录在生产资源和 `evidence/emberstead-schematic-import.json` 中。

## 检查、部署与回滚

实际查看两轮 MCP 预览，只追加主屋长侧面的木柱；图片已向用户展示。预览器对作物显示为紫灰色块，源为原版小麦/胡萝卜，不能把它当作游戏截图。

实际 schematic 导入通过：六模块覆盖、空箱、红床双格、出生/村民锚点均有效，无版本警告；三栋住宅在木门可开启时到入口有地板和两格净空通路。Java 21 离线 `gradlew.bat jar` 成功，打包模板与生产资源逐字节一致。未改 Java 逻辑，没有扩展测试或启动客户端、服务器、GameTest；实际落地由用户检验。

确认没有 Java/Javaw 进程后，已备份并更新开发实例的核心 JAR 与两份语言文件，逐文件哈希通过。任务定义及其他模组未替换。部署脚本新增 `--skip-chapter`；`--language-base-ref 5741baf` 仅允许更新与旧提交值一致的语言条目，自定义值仍受保护。

回执：`evidence/emberstead-deployment.json`。备份：`backups/emberstead-20261016-020305`。新 JAR SHA-256：`2ca44b0e8acf1deb0dbd12501fe7be7dd7ff2dec60a1b9d8d9e5fcbeba75bb9d`。退出游戏后恢复备份的三个文件可回滚运行包，源码可反向提交本项；这不会撤回游戏随后保存的建筑。

用户新建世界查看外观、屋内出生/持剑、门廊通行与村民住宅/农田/水井即可，无需为这次外观更新重跑完整第一章。
