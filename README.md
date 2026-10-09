# Minecraft 整合包开发项目

本仓库是正式开发目录：`D:\PCL 正式版 2.10.6 (1)\codex\modpack-project`。

最新设计以 [PLAN.md](PLAN.md) 为准：Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21，围绕固定基地的休闲生活、女仆与 Create 自动化，结合原创剧情、少数关键 Boss 推动的世界共享阶段及大量可选冒险。航空学保留为小型载具与玩具，完整移动基地暂不开发。

项目已进入按任务批准的正式开发流程。**已完成仓库整理与两个源码工程归档；M0 尚未完成。** 归档不代表重新编译或整包兼容验证已经通过。无限之核源码已由用户确认丢失，仓库仅保存其现有运行 JAR。

## 目录

| 位置 | 内容 |
|---|---|
| `AGENTS.md` | 用户可编辑的开发与 Git 工作规则，适用于整个仓库 |
| `PLAN.md` | 当前设计与里程碑 |
| `STATE.md` | 当前进度、验收边界和下一项候选任务 |
| `modules/evolvingblade` | 成长武器源码副本，含资源、构建入口、测试、设计与美术资料 |
| `modules/rpgleveling` | 角色加点源码副本，含资源、构建入口、测试与设计资料 |
| `vendor/infinitecore` | 无限之核 1.0.0 的运行 JAR 与来源说明；没有源码 |
| `integration/src` | 旧版配方整合原型，保留待评估 |
| `sanctuary/src/main` | 庇护石原型源码，保留待评估 |
| `staging/config/ftbquests` | 旧版任务书原型，尚未按当前 PLAN 改造 |
| `tools` | 现有构建、清单、部署和启动工具；执行前必须检查写入范围 |
| `evidence` | 版本、部署和验证记录；完整运行日志留在本机 |
| `SOURCE-ARCHIVE.md` | 本次复制范围、原工程位置、校验和限制 |
| `BASELINE-AUDIT.md`、`HANDOFF.md` 等 | 历史核查、交接与草案；不覆盖当前 PLAN 或执行授权 |

## 开发与 Git

每项新任务先向用户说明计划并询问是否开始，获准后先检查 Git，再在批准范围内执行；完成后审查差异、验证并提交本地 Git。推送 GitHub 需要明确指示。详细规则见 [AGENTS.md](AGENTS.md)，用户可以直接编辑它。

远端名为 `MC`，仓库为 [MC-modpack-trial](https://github.com/Cha0sky/MC-modpack-trial)。

`.gitignore` 排除构建产物、下载 JAR、缓存、运行目录、存档、备份和临时文件。Gradle Wrapper JAR 与丢失源码的无限之核 JAR 是明确保留的例外。取消跟踪不删除本地文件，也不重写首次提交历史。

正式修改应在 `modules` 的源码副本中进行；原始两个工程继续保留。历史模块文档和 `docs/archive/original-agent-instructions.md` 是归档资料，不是当前操作指令。

## 构建入口与尚未验证的内容

两个工程均保留了 `gradlew.bat`、`build.gradle`、`settings.gradle`、`gradle.properties` 和 Wrapper。版本锁定为 Gradle 8.14.2 / ModDevGradle 2.0.81 / Java 21 / NeoForge 21.1.248。

后续获准编译时，分别在以下目录使用各自的 Wrapper：

```powershell
Set-Location '.\modules\evolvingblade'
.\gradlew.bat build
```

```powershell
Set-Location '.\modules\rpgleveling'
.\gradlew.bat build
```

以上是后续构建入口，**本次未执行**。首次构建可能下载 Gradle、依赖和工具链；未随源码复制本机缓存。历史测试结果见各模块归档资料，不等于从新位置重跑通过。

- 成长武器有 `ebTest` 和 GameTest 入口；GameTest 清理任务会删除隔离运行目录，运行前需要检查。
- 角色加点有 `verifyMath` 和 GameTest 入口；运行元数据要求 Patchouli。其美术辅助脚本仍含原作者机器的外部图片/字体路径，本次未改造。
- 无限之核无法从本仓库源码重建；今后恢复、反编译研究或重写须作为单独批准的任务。
- 庇护石原型的 Python 构建脚本仍依赖仓库外 `../evolvingblade/build` 和本机 PCL 路径；本次未改造。

## 实例与存档

原始实例 `D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\mod test` 保持只读。

已存在的开发实例为 `D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\星庭远征-开发版`。它是部署和测试目标，名称是旧阶段的工作名；不需要重新克隆或覆盖。批准仓库修改不等于批准修改实例或存档。

旧任务、配方与庇护石属于原型。世界进度核心和旧区块补矿尚未实现，汇流来世内建进度尚未完成兼容调查。
