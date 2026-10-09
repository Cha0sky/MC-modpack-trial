# 两个归档工程的构建核查

核查日期：2026-10-10（Asia/Shanghai）。用户明确选择继续整合包主线，批准验证成长武器和角色加点两个归档工程。本项已完成；**M0 的游戏功能、重载及整包兼容验证仍未完成**。

本项开始时 HEAD 为 `5c547c2`。期间另一个对话完成了无限之核重制提交 `7d9f47c`；本项没有修改、编译或验收该工程，没有将其暂存文件混入本项提交。

## 结果

| 项目 | 成长武器 `evolvingblade` 1.1.0 | 角色加点 `rpgleveling` 1.0.0 |
|---|---|---|
| 新位置构建 | `modules/evolvingblade`，通过 | `modules/rpgleveling`，通过 |
| 本轮 Java 验证 | JUnit 112/112，失败/跳过/中止均为 0 | 数学与布局自检 471/471，失败为 0；包含布局检查 178 项 |
| 编译类版本 | Java 21，class major 65 | Java 21，class major 65 |
| 生产类数 | 61 | 77 |
| JSON 资源解析 | 18 个 | 67 个 |
| PNG 文件头与尺寸检查 | 8 个 | 8 个 |
| 源生产资源存在且内容对应 | 32 个 | 78 个 |
| 测试类进入发布 JAR | 没有 | 没有 |
| 与旧构建逐条目内容对比 | 条目集合及每个条目内容一致 | 条目集合及每个条目内容一致 |

角色加点的 GameTest 源码编译成功，但没有启动 GameTest 服务器。其标准 `test` 任务不能代替 Minecraft GameTest，本轮明确执行的纯 Java 检查是 `verifyMath`。

成长武器的标准 `test` 在中文路径中按原工程设置被跳过，实际通过的是 `build → check → ebTest` 的 112 个 JUnit 用例；不能把 `test SKIPPED` 本身当作通过。

两个工程的源代码、资源和构建输入在执行前后逐文件核对：成长武器 128 个、角色加点 160 个输入文件均保持原样。没有修改功能、配方或构建版本。

## 产物

- `modules/evolvingblade/build/libs/evolvingblade-1.1.0.jar`：185,362 字节，SHA-256 `1C307442C3E95C401BC0847C7F900420E0D7115409C08C92C38069B44703A539`。
- `modules/rpgleveling/build/libs/rpgleveling-1.0.0.jar`：379,569 字节，SHA-256 `0B475581512A643A6D126BE165AEF54B1BEFD1220D5F67C7DA1BAD30A170761D`。

JAR 的整体哈希与旧构建不同，但解包条目内容完全一致；不能用 ZIP 容器整体哈希不同推断功能改变。上述构建产物按 `.gitignore` 留在本地，不提交到源码仓库；原工程 JAR、原始实例和开发实例均未替换。

## 本轮实际工具链与构建方式

- 完整 Temurin JDK **21.0.12+8**：`C:\Gradle\jdk-21-temurin`，已核实 `java`、`javac` 和 `jmods/java.base.jmod`。
- Minecraft **1.21.1**；NeoForge **21.1.248**；ModDevGradle **2.0.81**；各模块自己的 Gradle Wrapper **8.14.2**。
- Wrapper 通过原 JAR 的 `org.gradle.wrapper.GradleWrapperMain` 入口启动，不使用全局 Gradle。角色加点的旧 Wrapper 没有可供 `java -jar` 使用的主清单，必须采用其原脚本的 classpath 启动方式。
- Gradle 用户缓存在仓库内 `work/build-baseline/gradle-user-home`，从已有缓存只读复制所需内容；没有复制用户的全局配置或初始化脚本，没有让本轮构建写入原工程缓存。
- `JAVA_HOME` 和 `GRADLE_USER_HOME` 只在构建子进程环境中设置，没有修改用户级/系统级环境变量。
- 使用 `--no-daemon --console=plain --no-configuration-cache --max-workers=2 -Dorg.gradle.vfs.watch=false`，显式指定 Java 21 工具链路径并禁用自动发现和自动下载工具链。
- 使用 `-PneoForge.neoFormRuntime.useEclipseCompiler=true` 生成 Minecraft/NeoForge 开发依赖；模组本身仍由 Java 21 编译。该配置已核实存在于本机 **ModDevGradle 2.0.81** 的插件类中，也有[官方配置说明](https://github.com/neoforged/ModDevGradle#global-settings-for-nfrt)。本项没有将该选项写入两个模块的构建文件。

本轮复现命令的精确参数、工作目录、退出码、耗时及环境路径保存在 `evidence/build-baseline/build-summary.json`。从仓库根目录使用已有 Wrapper 的示例：

```powershell
$env:JAVA_HOME = 'C:\Gradle\jdk-21-temurin'
$env:GRADLE_USER_HOME = Join-Path (Get-Location) 'work\build-baseline\gradle-user-home'
$taskBuildOptions = @(
    '--no-daemon', '--console=plain', '--no-configuration-cache', '--max-workers=2',
    '-Dorg.gradle.vfs.watch=false',
    '-Porg.gradle.java.installations.auto-detect=false',
    '-Porg.gradle.java.installations.auto-download=false',
    "-Porg.gradle.java.installations.paths=$env:JAVA_HOME",
    '-PneoForge.neoFormRuntime.useEclipseCompiler=true'
)
Push-Location '.\modules\evolvingblade'
.\gradlew.bat @taskBuildOptions build
Pop-Location
Push-Location '.\modules\rpgleveling'
.\gradlew.bat @taskBuildOptions build verifyMath
Pop-Location
```

以上是等价的 Wrapper 脚本复现入口；本轮实际执行的是记录中的 Java/classpath 调用。新机器仍需完整 Java 21 JDK、Gradle 发行包和依赖；任务缓存不在 Git 中。再次执行构建按 AGENTS.md 取得对应任务授权。

## 已处理的环境问题与剩余限制

首次离线缺少 Foojay 1.0.0 / JUnit 5.9.2 缓存，联网解析后补齐，版本没有升级。NeoForm Runtime **1.0.13** 的默认 JDK 编译路径在 Microsoft Java 21.0.7 与完整 Temurin 21.0.12 中均失败：

```text
ERROR Line: 1, ..net.minecraft.world.ticks.package-info...: null in /net/minecraft/world/ticks/package-info.java
NodeExecutionException: Node action for recompile failed
```

切换到上述官方支持的 Eclipse 编译选项后，Minecraft 开发依赖和两个模组构建成功。这里只证明该选项能绕过本轮故障，没有确认默认路径失败的根本原因，也不声称默认构建已被永久修复。首次角色加点 Wrapper 的启动方式错误也已更正，失败尝试完整保留。

本轮没有复制 Gradle 的任务输出缓存，模块的生产代码实际从新位置编译；部分 Minecraft 工具链中间结果使用本机缓存，缺失部分实际生成。成长武器最终成功命令中的生产编译显示 `UP-TO-DATE`，因为同一任务前一尝试已经完成生产编译，随后成功命令补齐并运行 JUnit；不是直接拿旧工程 JAR 当新产物。

保留了原代码的 API 弃用/unchecked 警告以及 Gradle 9 兼容性提示。本项保持 Gradle 8.14.2，不为了消除警告升级版本或改写功能。

没有运行客户端、Minecraft GameTest 服务器、实际 Boss 战、死亡/重载/跨维度、多人、性能或存档迁移测试。静态 PNG 检查不等于 GUI 的游戏内视觉验收。本轮不推送 GitHub。

## 证据

- `evidence/build-baseline/build-summary.json`：8 次构建尝试、最终结果及完整命令。
- `evidence/build-baseline/*-console.txt`：对应控制台输出，统一保存为 UTF-8/LF、清理行尾空白并保留一个结尾换行；原始日志及其哈希保留于任务工作目录/摘要中。
- `evidence/build-baseline/evolvingblade-junit.xml`、`evolvingblade-junit-summary.txt`：本轮 JUnit 证据。
- `evidence/build-baseline/artifact-audit.json`：资源、生产类及旧构建内容对比。
- `evidence/build-baseline/source-before.json`：构建输入快照，执行后已逐文件比对。
- `evidence/build-baseline/preparation.json`：任务缓存的来源与范围；临时缓存约 727 MB 的初始复制量，保留在被忽略的 `work` 中，未写入 Git。

下一项建议是 **汇流来世内建进度兼容调查**：只读核实当前版本、世界阶段持久化、关键 Boss 触发、困难模式与矿物解锁接口，再提出与原创世界进度核心的衔接方案。它是新任务，必须先告知用户规划并询问，不能自动执行。
