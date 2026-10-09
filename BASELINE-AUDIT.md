# 开发基线核查

本项由用户明确授权开始。核查完成，**M0 尚未完成**：三个自制模组还没有全部纳入整合包仓库，本轮未重新编译或进行游戏测试。

## 执行规则与范围

- 每项新任务先说明目标、文件范围、实施步骤和验收方法，获得用户同意后开始。
- 获准后的第一步检查 Git 分支、提交、工作区与暂存区，保留用户已有改动。
- 完成任务后审查差异、进行适合该任务的验证，并提交本地 Git；推送 GitHub 按用户明确指示执行。
- 扩大任务范围需要重新询问。本项仅核查并保存报告，不归档源码、不清理已跟踪文件、不修改模组功能、不安装模组、不启动游戏。
- 以 PLAN.md 的最新设计为准：固定基地、生活与 Create 自动化、原创世界阶段；旧文档和任务为历史原型。

## 1. Git 基线

| 项目 | 核查结果 |
|---|---|
| 仓库根目录 | `D:\PCL 正式版 2.10.6 (1)\codex\modpack-project` |
| 当前分支 | `main` |
| 开始时本地提交 | `e1e98a63d5fbab1f395eab101be9f28ad115101f`，`initial AI developing resources` |
| 远端 | `MC`，`https://github.com/Cha0sky/MC-modpack-trial` |
| 远端核查 | 实际执行 `git ls-remote --heads MC main`，返回同一提交；没有推送 |
| 已跟踪改动 / 暂存改动 | 开始时均为空 |
| 用户已有未跟踪文件 | `PLAN.md`，本项保持原样，不自动加入提交 |
| 项目 `.gitignore` / `.gitattributes` | 均不存在 |

PLAN.md 核查时的 SHA-256：

`9A7B8A1086703F81337E602BC1DF7E078714EE663CF6E1BCC23CAEB66E0409EE`

首次提交已跟踪 13 个二进制或缓存文件：6 个 `sanctuary/build/classes` 下的 `.class`、2 个 `tools/__pycache__` 下的 `.pyc`、5 个 `staging/mods` 下的 `.jar`。还跟踪了旧任务备份与历史日志。

后续添加 `.gitignore` 不会自动取消已有文件的跟踪。清理时应审查清单，再仅从 Git 索引移除不应跟踪的文件，保留本地原件；本项未做清理或历史重写。

## 2. 三个自制模组

### 成长武器：evolvingblade

- 源码：`D:\PCL 正式版 2.10.6 (1)\codex\evolvingblade`。
- 位于整合包仓库之外；父级 `codex` 自身另有 Git 仓库，其中跟踪了该项目 `src/main` 的 82 个文件，最近相关提交为 `7fe2e11`（`the leavings`）。这不能代替本整合包仓库的源码覆盖。
- 项目包含 `build.gradle`、`settings.gradle`、`gradle.properties`、Gradle Wrapper、生产源码和测试源码。
- 配置版本：模组 `1.1.0`；Minecraft `1.21.1`；NeoForge `21.1.248`；Java toolchain `21`；ModDevGradle `2.0.81`；Gradle Wrapper `8.14.2`；Parchment `2024.11.17`；JUnit BOM `5.9.2`。
- 现有测试入口包含 `ebTest` 和 `runGameTestServer`。构建脚本的 GameTest 清理任务会删除隔离的 `run-gametest` 目录，后续运行前必须确认该目录用途；本项未执行。
- 现有 `build/libs/evolvingblade-1.1.0.jar` 与开发实例对应 JAR 的 SHA-256 一致：

`2D9BE326E8310189DA15E36CDBFD29C8B9C42BD4681D7143DDA12481589BF7C0`

- 历史 `verification/v3/acceptance.json` 记录 112 个 JUnit、117 个服务器 GameTest 通过，客户端人工视觉检查仍待完成。这是历史记录，本轮没有重跑，也不证明当前源码重新构建必定产生相同 JAR。

### 角色加点：rpgleveling

- 源码：`D:\Agent_workspaces\Deepseek Harness\MC_mod`。
- 位于整合包仓库之外，本项只读访问。
- 项目包含 Gradle 构建文件、Wrapper、生产源码、测试源码和设计/验证文档。
- 配置版本：模组 `1.0.0`；Minecraft `1.21.1`；NeoForge `21.1.248`；Java toolchain `21`；ModDevGradle `2.0.81`；Gradle Wrapper `8.14.2`。
- 元数据声明 Patchouli 必需依赖，范围 `[1.21,)`；开发实例中存在 `Patchouli-1.21.1-93-NEOFORGE.jar`。文件存在不等于本轮验证了加载兼容性。
- 已定义 `verifyMath` 和 `runGameTestServer` 等验证入口；本轮未执行。
- 现有 `build/libs/rpgleveling-1.0.0.jar` 与开发实例对应 JAR 的 SHA-256 一致：

`C80A959783B33FA65D75FFFAB14B43FDD21C70A97F3634BB02EA3AD58B043BB0`

- 历史 `docs/VERIFICATION-REPORT.md` 的最新 T18 结论是有条件通过，包含待裁决问题和文档滞后；归档时需要保留这些限制，不能记作无条件稳定版本。

### 资源生成：infinitecore

- 已确认开发实例存在 `无限之核模组-infinitecore-1.0.0.jar`，79,264 字节。
- SHA-256：

`10EC43204404817C8738088670571D5044BBF4B992A9247AB8D67B986AA2E400`

- **在本轮检索范围内未定位到源码及构建项目**，不能认定源码已丢失，也不能将运行 JAR 当作源码基线。
- 已检索当前 `codex` 工作区，以及 `D:\MCmods`、`C:\Users\俞皓天\Documents\ChatGPT\New project`、`D:\DSH_workspace`、`D:\Agent_workspaces`、`D:\Codex杂项`、`D:\WB工作区`、`D:\repo`、`D:\Hermes Workspace` 中的常见构建/模组元数据文件；检索排除了依赖、缓存和构建输出，没有全盘扫描。
- 下一步需要用户提供原始源码工程位置；Java 构建版本、依赖和许可证仍待核实。

## 3. 仓库内已有原型与构建边界

| 内容 | 当前情况 |
|---|---|
| `integration/src` | 资源型配方原型，使用 `lowcodefml`；由 `tools/build_integration.py` 生成并打包 |
| `sanctuary/src/main` | 庇护石 Java 原型；由 `tools/build_sanctuary.py` 编译并打包 |
| `staging/config/ftbquests` | 已跟踪的五章任务原型，尚未按最新 PLAN 重新设计 |
| `tools` | 构建、清单、复制、部署与启动工具；各脚本有不同写入范围，不能笼统当作只读工具 |

`build_sanctuary.py` 依赖仓库外的 `../evolvingblade/build/moddev/artifacts/neoforge-21.1.248.jar`、PCL 库目录、开发实例版本 JSON 和硬编码的本机 JDK 路径；它还会重写部分资源文件。因此，当前 GitHub 仓库**尚不能在新机器上独立重建全部原型**。

`build_integration.py` 也会生成或覆盖资源并更新构建证据，而不只是打包现有资源。本轮未运行这两个脚本。

## 4. 开发、部署与测试边界

- 开发与 Git 根目录：`modpack-project`。
- 原始输入基线：`D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\mod test`，保持只读。
- 已有开发实例：`D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\星庭远征-开发版`。不需要重新克隆或覆盖。
- 开发实例已有多个存档，包括 `EvolvingBlade Install QA`；本项仅列出目录，没有打开或修改存档。已有名字不能证明它是可随意删除的测试世界。
- 现有 `launch_development.py` 使用离线 QA 身份启动到标题界面，并写入项目证据；它不会自动创建或打开测试存档。
- 后续首次功能测试应另建明确的测试存档并定义日志归档方式。部署到仓库外实例之前，仍需检查当时的沙盒权限与授权范围。
- `README.md`、`STATE.md` 等保留了旧架构或旧状态描述，后续需与 PLAN 同步；本项未改写历史文档。

## 5. 建议下一项任务：整理仓库与归档源码

以下仅为建议，**未获本项授权，不能直接执行**：

1. 保持 PLAN 内容不变，将其正式纳入 Git；增加项目级 AGENTS.md，保存用户要求的逐任务批准和 Git 工作流程。
2. 增加 `.gitignore`，审查并取消跟踪编译产物、缓存和第三方下载 JAR，保留本地文件。Gradle Wrapper JAR 应作为必要构建文件保留，不能被通用 `*.jar` 规则误排除；旧备份和简短验证证据需逐项分类。
3. 将两个已定位模组的源码、资源、Wrapper、构建文件、必要测试和许可/设计资料复制归档到 `modules/evolvingblade` 与 `modules/rpgleveling`，保留原工程，不复制缓存、运行存档和大型构建输出，并校验复制前后的文件哈希。
4. 用户给出 infinitecore 工程位置后，按同一方式纳入 `modules/infinitecore`；未提供时明确缺口。
5. 更新项目入口文档，说明最新 PLAN、目录、构建入口与尚未完成的 M0 验证。庇护石构建依赖改造及真正的编译/游戏测试应另列任务。
6. 检查 Git 差异，完成本地提交；不重写首次提交，不自动推送远端。

完成上述源码归档后，才能继续批准并执行三个模组的独立构建和功能验证，以及汇流来世内建进度的兼容调查。本轮只确认其开发实例 JAR 为 `ConfluenceOtherworld-1.2.4-260226.jar`，未调查其进度 API 或源码。

## 6. 本轮验收

- 已执行本地 Git 状态、提交、跟踪文件清单和远端分支只读查询。
- 已核实两个源码工程的构建配置和必要文件存在，并对比本地产物与实例 JAR 哈希。
- 已明确 infinitecore 源码缺口、仓库覆盖缺口、已跟踪产物问题和外部构建依赖。
- 唯一新增交付文件为本报告；PLAN.md 的内容与未跟踪状态保持不变。
- 未执行编译、单元测试、GameTest、客户端启动或世界内功能测试；未修改源码、实例、配置或存档；未安装或移除模组；未推送 GitHub。
