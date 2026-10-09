# 自制模组源码归档

归档任务获用户明确批准；整理日期：2026-10-10（Asia/Shanghai）。基线提交为 `0dabe4c`。逐文件来源、大小和 SHA-256 见 [evidence/source-archive.json](evidence/source-archive.json)。该清单记录首次复制时的快照，后续开发修改源码后，应使用归档提交核对快照，而不是把所有正常开发差异当成损坏。

## 已保存的两个工程

| 模组 | 原工程，保持原样 | 本仓库副本 | 文件数 | 总字节 |
|---|---|---|---|---|
| 成长武器 1.1.0 | `D:\PCL 正式版 2.10.6 (1)\codex\evolvingblade` | `modules/evolvingblade` | 211 | 7,562,952 |
| 角色加点 1.0.0 | `D:\Agent_workspaces\Deepseek Harness\MC_mod` | `modules/rpgleveling` | 173 | 1,293,409 |

复制包含 `src`、Gradle Wrapper、构建配置、相关设计/测试文档和辅助工具；成长武器还包含美术设计与源图、许可证、模板许可和少量历史验收摘要。没有复制原工程的 Gradle 缓存、构建输出、运行实例、存档、大型日志或引用源码链接。

所有复制文件均通过复制前后的源文件哈希和目标哈希比对。两个源工程没有被移动或修改。源码和生产资源未重新生成或格式化。

成长武器原 AGENTS.md 的内容保存为 `modules/evolvingblade/docs/archive/original-agent-instructions.md`，字节保持一致。它属于历史文档；新仓库中的有效规则是根目录 AGENTS.md。角色加点原工程没有对应 AGENTS.md。

成长武器原 LICENSE 和 TEMPLATE_LICENSE.txt 均已保留。角色加点的 `gradle.properties` 声明 MIT，但本次可归档工程中未发现独立 LICENSE 文件；本次没有虚构或补写许可文本。

## 无限之核：二进制基线

用户明确确认无限之核源码已丢失。本次未继续搜索、反编译或重新实现。

为保留现有版本，将开发实例中的 `无限之核模组-infinitecore-1.0.0.jar` 复制为 `vendor/infinitecore/infinitecore-1.0.0.jar`；这是现有自制模组产物，不是恢复的源码。79,264 字节，SHA-256 为：

`10EC43204404817C8738088670571D5044BBF4B992A9247AB8D67B986AA2E400`

原运行 JAR 保留，复制前后哈希一致。该二进制是 `.gitignore` 的明确保留例外。任何内部功能修改、源码恢复或重写都需要新的任务批准；世界进度联动如何在仅有二进制的条件下实现尚未决定。

## Git 整理

- PLAN.md 保持原字节内容，SHA-256 为 `9A7B8A1086703F81337E602BC1DF7E078714EE663CF6E1BCC23CAEB66E0409EE`，本次正式纳入 Git。
- AGENTS.md 写入逐任务批准、操作前 Git 检查、保护用户改动、明确暂存范围、本地提交与推送授权等规则。
- `.gitignore` 排除构建输出、缓存、运行目录、备份、临时文件及普通 JAR；两个 Wrapper JAR 和无限之核 JAR 保留。
- 从当前 Git 跟踪中移除 19 个本地文件：6 个 `.class`、2 个 `.pyc`、5 个 staging JAR、5 个旧任务备份、1 个完整客户端运行日志。
- 上述文件仅使用 `git rm --cached` 取消跟踪，**全部本地保留**。247 字节的庇护石编译日志及现有 JSON/Markdown 证据仍跟踪。
- 不重写首次提交历史。旧提交中的文件仍可从 Git 历史取得；本次只整理当前工作树的跟踪范围。

## 仍需单独验证

本轮仅完成归档、差异/忽略规则及哈希检查，没有编译、运行测试套件或启动游戏。

新增项目文件的 `git diff --cached --check` 通过。全文检查发现 3 处原文件已有的空白格式警告：成长武器 `WaveBurstAcceptanceGameTests.java` 末尾空行、`ExportSwordArtwork.java` 第 106 行行尾空白、角色加点 `docs/VERIFICATION.md` 第 1211 行行尾空白。本次为保持复制文件的原字节哈希，未格式化它们。校验摘要另存为 `evidence/source-archive-validation.json`。

- 两个源码工程尚未从新位置重新编译，首次构建可能需要下载 Wrapper 发行包、工具链或依赖。
- 归档模块 README 和验证报告按原字节保存，包含历史版本/路径和过往测试结论；最新项目状态看根目录 STATE.md。
- 角色加点的美术辅助脚本依赖原机器的图片/字体路径；归档的生产资源已存在，但辅助脚本可移植性未验证。
- 庇护石 Python 构建脚本仍指向原来的仓库外成长武器构建缓存，本次没有改造。
- 现有开发实例与存档未改动，当前 GitHub 仓库仍不能据此宣称完整整合包可独立重建或已通过游戏验证。
