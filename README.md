# Minecraft 整合包开发项目

本仓库是正式开发目录：`D:\PCL 正式版 2.10.6 (1)\codex\modpack-project`。

最新设计以 [PLAN.md](PLAN.md) 为准：Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21，围绕固定基地的休闲生活、女仆与 Create 自动化，结合原创剧情、少数关键 Boss 推动的世界共享阶段及大量可选冒险。航空学保留为小型载具与玩具，完整移动基地暂不开发。

叙事依据为作者的 [《失落世界：世界观与主线叙事设定集》V1.0](docs/lore/失落世界_世界观设定_v1.0.md)，原文及状态标记完整归档。[开发落地规划 V0.1](docs/design/失落世界_开发落地规划_v0.1.md)是衔接现有技术的提案；五幕、守护者名单、具体解锁与新玩法仍按作者状态标记讨论，不因保存文档自动批准实施。

项目已进入按任务批准的正式开发流程。**两个归档工程的构建与 Java 检查已通过；M0 的游戏内及整包验证尚未完成。** 成长武器 JUnit 112/112，角色加点数学与布局自检 471/471，记录见 [BUILD-BASELINE.md](BUILD-BASELINE.md)。无限之核原始源码丢失，基准 JAR 保留；另一个对话已新增重制工程，本轮不验收其功能。

2026-10-13 的[第一章试作](CHAPTER1-PROTOTYPE.md)已实现并定向部署：起始聚落与玩家住宅、默认标准路线持剑、11 个任务节点、史莱姆王后的邪恶地形门槛。独立服务器和普通地形生成/重载验证通过；真实首领、FTB 界面/领奖和修正版整包新世界仍待验收，不能把源码/部署完成当作整包玩法全部通过。

2026-10-16 更新：用户已确认此前村民住宅、任务书断点/解锁和启动问题全部修复，见[用户验收](docs/feedback/2026-10-16-chapter1-accepted.md)。开发实例新装模组的[版本与改动说明](docs/mods/2026-10-16-instance-changes.md)、[完整哈希清单](evidence/development-mods-2026-10-16.json)已归档；Mowzie 生物的[任务候选](docs/design/mowziesmobs-quest-candidates.md)待确认，建筑外观按用户要求暂不处理。新增模组的兼容性与全部玩法未因此自动验收。

## 目录

| 位置 | 内容 |
|---|---|
| `AGENTS.md` | 用户可编辑的开发与 Git 工作规则，适用于整个仓库 |
| `PLAN.md` | 当前设计与里程碑 |
| `STATE.md` | 当前进度、验收边界和下一项候选任务 |
| `docs/lore` | 作者世界观原文；具体版本的确认、建议与待定状态保持原样 |
| `docs/design` | 从世界观派生的开发提案，尚未作为实施清单批准 |
| `modules/evolvingblade` | 成长武器源码副本，含资源、构建入口、测试、设计与美术资料 |
| `modules/rpgleveling` | 角色加点源码副本，含资源、构建入口、测试与设计资料 |
| `modules/infinitecore` | 另一对话提交的无限之核重制工程，验收状态见其模块文档 |
| `modules/world-progression-core` | 第一章聚落、开局持剑、独立共享首领记录及邪恶地形入口门槛 |
| `CHAPTER1-PROTOTYPE.md` | 第一章实现、世界生成修正、验证限制与部署回退 |
| `vendor/infinitecore` | 无限之核 1.0.0 的运行 JAR 与来源说明；没有源码 |
| `integration/src` | 旧版配方整合原型，保留待评估 |
| `sanctuary/src/main` | 庇护石原型源码，保留待评估 |
| `staging/config/ftbquests` | 旧版任务书原型，尚未按当前 PLAN 改造 |
| `tools` | 现有构建、清单、部署和启动工具；执行前必须检查写入范围 |
| `evidence` | 版本、部署和验证记录；完整运行日志留在本机 |
| `SOURCE-ARCHIVE.md` | 本次复制范围、原工程位置、校验和限制 |
| `BUILD-BASELINE.md` | 两个归档工程的实际构建、Java 检查、工具链问题和复现命令 |
| `CONFLUENCE-PROGRESSION-AUDIT.md` | 当前汇流来世进度接口、存储、困难模式与矿物机制的调查及衔接建议 |
| `BASELINE-AUDIT.md`、`HANDOFF.md` 等 | 历史核查、交接与草案；不覆盖当前 PLAN 或执行授权 |

## 开发与 Git

每项新任务先向用户说明计划并询问是否开始，获准后先检查 Git，再在批准范围内执行；完成后审查差异、验证并提交本地 Git。推送 GitHub 需要明确指示。详细规则见 [AGENTS.md](AGENTS.md)，用户可以直接编辑它。

远端名为 `MC`，仓库为 [MC-modpack-trial](https://github.com/Cha0sky/MC-modpack-trial)。

`.gitignore` 排除构建产物、下载 JAR、缓存、运行目录、存档、备份和临时文件。Gradle Wrapper JAR 与丢失源码的无限之核 JAR 是明确保留的例外。取消跟踪不删除本地文件，也不重写首次提交历史。

正式修改应在 `modules` 的源码副本中进行；原始两个工程继续保留。历史模块文档和 `docs/archive/original-agent-instructions.md` 是归档资料，不是当前操作指令。

## 构建入口与尚未验证的内容

两个工程均保留了 `gradlew.bat`、`build.gradle`、`settings.gradle`、`gradle.properties` 和 Wrapper。版本锁定为 Gradle 8.14.2 / ModDevGradle 2.0.81 / Java 21 / NeoForge 21.1.248。

本轮使用完整 Temurin JDK 21.0.12+8（`C:\Gradle\jdk-21-temurin`）及 `-PneoForge.neoFormRuntime.useEclipseCompiler=true` 构建通过。后续获准编译时，从仓库根目录设置当前进程环境，再分别使用各自的 Wrapper；完整参数见 BUILD-BASELINE.md：

```powershell
$env:JAVA_HOME = 'C:\Gradle\jdk-21-temurin'
$env:GRADLE_USER_HOME = Join-Path (Get-Location) 'work\build-baseline\gradle-user-home'
```

```powershell
Push-Location '.\modules\evolvingblade'
.\gradlew.bat build -PneoForge.neoFormRuntime.useEclipseCompiler=true --no-daemon --no-configuration-cache
Pop-Location
```

```powershell
Push-Location '.\modules\rpgleveling'
.\gradlew.bat build verifyMath -PneoForge.neoFormRuntime.useEclipseCompiler=true --no-daemon --no-configuration-cache -Dorg.gradle.vfs.watch=false
Pop-Location
```

以上是已有 Wrapper 的复现入口。本轮实际调用的入口、命令和结果保存在 `evidence/build-baseline`；没有启动游戏或部署。缓存不在 Git 中，新机器仍可能需要下载 Gradle 和依赖。默认 NeoForm JDK 编译路径的故障没有永久修复，本轮使用 Eclipse 编译选项绕过；历史验收不能替代后续游戏测试。

- 成长武器有 `ebTest` 和 GameTest 入口；GameTest 清理任务会删除隔离运行目录，运行前需要检查。
- 角色加点有 `verifyMath` 和 GameTest 入口；运行元数据要求 Patchouli。其美术辅助脚本仍含原作者机器的外部图片/字体路径，本次未改造。
- 无限之核原始源码没有恢复；另一对话新增了重制工程，不能据此宣称与原 JAR 已完成存档或整包兼容验收。
- 庇护石原型的 Python 构建脚本仍依赖仓库外 `../evolvingblade/build` 和本机 PCL 路径；本次未改造。

## 实例与存档

原始实例 `D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\mod test` 保持只读。

已存在的开发实例为 `D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\星庭远征-开发版`。它是部署和测试目标，名称是旧阶段的工作名；不需要重新克隆或覆盖。批准仓库修改不等于批准修改实例或存档。

旧任务、配方与庇护石属于原型。第一章独立进度核心已实现，通用阶段框架和旧区块补矿尚未实现。汇流来世 1.2.4 的静态调查见 CONFLUENCE-PROGRESSION-AUDIT.md；第一章保留原生进度，不接管困难模式或矿物转换。后续衔接设计需另行批准。
