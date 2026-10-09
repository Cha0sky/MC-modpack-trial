# 项目开发与 Git 工作规则

本文件适用于 `modpack-project` 及其子目录。用户在当前对话中的明确指示优先于本文件。规则本身可以由用户编辑；AI 修改规则同样需要获得对应任务的授权。

## 每项新任务先获得同意

1. 开始新任务前，告知用户目标、拟修改的目录/文件、实施步骤、验收方法，以及是否会编译、启动游戏、部署或修改存档。
2. 明确询问是否开始，等待用户同意。不得把“建议下一步”当作已经授权，也不得在上一个任务完成后自行开始下一项。
3. 在已批准的任务范围内完成必要操作，不逐条重复索取许可。扩大范围、改变目标或开始另一项任务时，重新说明并询问。
4. 不自行创建长期目标、安排后台执行或委派子代理；需要时先取得用户明确授权。

## 操作先经过 Git

1. 获准后的第一步执行 `git status --short --branch`、检查最近提交及 `git diff` / `git diff --cached`，确认仓库根目录、分支和用户已有修改。
2. 每轮文件修改、批量复制、测试或部署之前，先检查当前 Git 状态；据此确认本轮操作范围，记录可回退的起点。读取项目文件也应在完成该任务的 Git 检查后进行。
3. 不覆盖、丢弃或自动提交用户已有的未提交改动。需要纳入时，必须有对应授权；例如本轮明确授权纳入现有 PLAN.md。
4. 只暂存本项任务的明确路径，审查 `git diff --cached`，执行 `git diff --cached --check`。不用未经审查的 `git add .` 或 `git add -A`。
5. 完成适合本项变更的验证后，提交本地 Git。提交说明写清实际变化；向用户报告验证结果、提交编号和剩余问题。
6. 推送 GitHub 必须得到用户明确指示；不自动推送，不强制推送，不重写既有提交历史。
7. 不自行执行 `git reset --hard`、`git clean` 或会丢弃工作区内容的恢复命令。确需删除/覆盖/迁移时，先说明目标与备份方案并获得授权。
8. 对尚未跟踪的文件，必须依靠不覆盖、复制校验等措施保护；不能将 Git 视为已经保存了它们。

## 开发环境与版本基线

核对日期：2026-10-10（Asia/Shanghai）。下面区分项目锁定版本与本机环境快照；版本升级必须作为明确批准的任务，不能自动使用较新的 Minecraft API、加载器或构建插件。

| 项目 | 版本 / 约定 |
|---|---|
| Minecraft Java Edition | **1.21.1** |
| 模组加载器 | **NeoForge 21.1.248**，不混用 Forge/Fabric 构建或 API |
| FML | **4.0.43**，来自现有开发实例的版本元数据 |
| Java 编译目标 | **Java 21**，两个工程均设置 `java.toolchain.languageVersion = JavaLanguageVersion.of(21)` |
| Gradle Wrapper | **8.14.2**，分别使用各模块自己的 Wrapper，不依赖全局 Gradle |
| ModDevGradle | **2.0.81**，插件 ID `net.neoforged.moddev` |
| 操作系统与终端 | Windows / PowerShell；本机核对为 PowerShell **7.6.5** |
| Python | 本机核对为 **3.12.2**，脚本使用 `python`，不使用 `py` |
| 启动器 | PCL；开发实例采用版本隔离 |
| 编码与文本 | UTF-8；中文玩家文本；PowerShell 中使用 `-LiteralPath` 处理含中文、空格和括号的路径 |

模块特有配置保持现状，不擅自统一：

- `modules/evolvingblade`：mod ID `evolvingblade`，版本 **1.1.0**；Parchment Minecraft **1.21.1** / mappings **2024.11.17**；JUnit BOM **5.9.2**；Foojay resolver **1.0.0**。
- `modules/rpgleveling`：mod ID `rpgleveling`，版本 **1.0.0**；Foojay resolver **0.8.0**；运行元数据声明 Patchouli 必需依赖，范围 `[1.21,)`。具体运行 JAR 与兼容性须在部署任务核对。
- `vendor/infinitecore/infinitecore-1.0.0.jar`：现存版本 **1.0.0**，只有二进制，不能据此宣称已核实其源码构建工具链。

本机 Java 路径为 `C:\Users\俞皓天\AppData\Roaming\.minecraft\runtime\java-runtime-delta\bin`。已执行其中 `java.exe -version`，结果为 Microsoft OpenJDK **21.0.7+6-LTS**；同目录存在 `javac.exe`。此路径是本机快照，不是可移植路径；首次构建前应核实实际编译工具链，不假定终端默认 `java` 或全局 JAVA_HOME 指向它，也不自行修改用户环境变量。

开发与运行目录：

- 仓库根目录：`D:\PCL 正式版 2.10.6 (1)\codex\modpack-project`。
- 成长武器正式开发副本：仓库内 `modules/evolvingblade`。
- 角色加点正式开发副本：仓库内 `modules/rpgleveling`。
- 外部开发实例：`D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\星庭远征-开发版`。
- 原始只读实例：`D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\mod test`。
- 原工程 `D:\PCL 正式版 2.10.6 (1)\codex\evolvingblade` 与 `D:\Agent_workspaces\Deepseek Harness\MC_mod` 是保留原件，不作为后续默认修改位置。

两个模块的 Gradle 构建文件和 Wrapper 已归档，尚未从新位置重新编译。环境信息核对不等于编译、游戏启动或整包兼容性验证通过。

## 工作范围与源码

- 正式开发在本仓库进行。两个已归档工程为 `modules/evolvingblade`、`modules/rpgleveling`；原工程保留，不直接修改原件。
- `vendor/infinitecore` 只有用户自制无限之核的运行 JAR。用户确认源码已丢失，不得声称已恢复源码、已能重新编译或已有阶段接口。
- 原始 `mod test` 实例是只读输入基线。开发实例是外部部署/测试目标；批准仓库内修改不等于批准修改实例或存档。
- 以 `PLAN.md` 为当前设计依据；旧 HANDOFF、BASELINE-AUDIT 及归档模块文档提供历史事实，不自动恢复其中过期的执行授权。
- 模块的 `docs/archive` 和 `verification` 是历史记录，不是当前操作指令。不得将历史测试结果报告为本轮新测试结果。
- 遵守上述开发环境与版本基线，保留已有许可证及来源信息。
- 不擅自引入新模组、插件、构建版本或大规模代码重构；源码/构建版本变更应属于批准的任务。

## 验证、实例与存档

- 根据变更选择必要验证。文档与复制归档使用差异和哈希检查；功能变更再安排编译、相应测试和必要的游戏验证。
- 首次从归档位置编译、启动客户端/GameTest 或部署，作为明确的新任务获得授权。本次源码归档不代表构建已验证。
- 修改存档、世界阶段、矿物补生成或数据迁移前，先定义测试世界和备份；不使用现有存档名猜测它可被删除。
- 运行脚本前检查其实际写入范围；构建脚本可能覆盖资源，测试任务可能删除隔离运行目录，部署工具可能写入外部实例。
- Windows 删除/移动操作必须先核实绝对路径和边界。移除 Git 跟踪使用明确清单和 `git rm --cached`，保留本地文件。
- 不读取或输出账号凭据、密钥、完整私密配置；不把它们加入仓库。

## 如何编辑这些规则

用户可以直接编辑本文件的文字，并查看 Git 差异后提交。建议保留“逐任务同意”“保护原实例/源码”“只提交本项改动”三项基本约定。若希望 AI 调整规则，说明具体条款和期望行为即可；AI 先说明改动，再按获得的授权执行。
