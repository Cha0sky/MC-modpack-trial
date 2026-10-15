# NeoForge 最低兼容候选检查

日期：2026-10-15（Asia/Shanghai）。任务起点 `83d45dc`。用户批准直接检查最低可能兼容版本；范围为无限之核依赖/API 检查、必要配置调整与编译，不部署、不启动游戏或 GameTest，不读写存档。开始时已有三份未提交的崩溃排查记录及一个未跟踪的其它任务文件，均保留，不纳入本次提交。

## 结论

**最低可能兼容版本可以下调到 NeoForge 21.1.1，Minecraft 保持 1.21.1。** 官方 Maven 发布列表中，21.1.x 从 21.1.1 开始；不存在更早的已发布 21.1.0 可供本轮检查。21.1.1 和原开发 SDK 21.1.248 均完成完整构建和已有数值检查。

这是 API、编译、二进制与资源层面的结论，不是游戏启动、操作、渲染或旧存档兼容已经实测通过。也不代表整个整合包中的其它模组都能降到 21.1.1。

## 已实施的声明调整

| 设置 | 原值 | 当前值 |
|---|---|---|
| 模组版本 | `1.1.0-rebuild` | `1.1.1-compat` |
| 默认开发 SDK `neo_version` | `21.1.248` | 保留 `21.1.248` |
| 允许的 NeoForge 运行范围 | 硬写 `[21.1.248,)` | 独立属性 `neo_version_range=[21.1.1,21.2)` |
| 允许的 Minecraft 版本 | `[1.21.1,1.22)` | `minecraft_version_range=[1.21.1]` |
| FML 语言加载器声明 | `[4,)` | 保留 `[4,)` |

`build.gradle` 将三个元数据属性一起展开并跟踪为资源任务输入。编译 SDK 与运行依赖范围不再混用；Minecraft 范围明确为用户指定的同一游戏版本。未修改任何生产 Java 源码，也未改配方、产量、模板、GUI、美术、注册 ID、网络协议或保存字段。

官方区分 `neo_version` 和 `neo_version_range`，依赖使用 Maven 版本区间；`[21.1.1,21.2)` 包含 21.1.1，排除 21.2。[NeoForged 1.21.1 模组文件说明](https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles/)、[版本范围说明](https://docs.neoforged.net/docs/1.21.1/gettingstarted/versioning/)。

## 检查证据

1. 下载并读取官方 [NeoForge Maven 元数据](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)、[21.1.1 POM](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.1/neoforge-21.1.1.pom)、21.1.1 sources/universal JAR 和其 FML 4.0.24 loader JAR。文件哈希见同目录结构化记录。第三方依赖只留在忽略的工作/Gradle 缓存目录，没有放入发布模组或 Git。
2. 核对 21.1.1 中已有本模块使用的菜单注册、IMenuTypeExtension、DeferredRegister/Holder/Item、ItemStackHandler、SlotItemHandler、PayloadRegistrar、PacketDistributor、RegisterPayloadHandlersEvent、Capabilities 等 API；FML 4.0.24 的 `@Mod` 已有 `dist()`。
3. 用完整 Temurin 21.0.12+8、Wrapper 8.14.2、ModDevGradle 2.0.81，在独立输出目录对**完整生产源码**编译，而非只验证几个 API 名字存在。
4. 对最终兼容声明，在 21.1.1 和 21.1.248 分别执行 `build`，都显示 `BUILD SUCCESSFUL`；两次 `verifyLogic` 均实际执行，64 种阶级间隔组合通过。标准 `test` 没有新增 JUnit 用例，不把它算作额外功能测试。
5. 两个 SDK 构建的 18 个生产 class 与原 1.1.0-rebuild JAR **全部逐字节相同**。对比解包后的全部路径，唯一内容变化为 `META-INF/neoforge.mods.toml`；原有 17 配方、42 PNG、61 JSON、模型/贴图引用、语言和 Java 21 class 版本检查通过。
6. 在复制到模块构建目录前，核对目标不存在或内容一致，保留原 1.1.0-rebuild JAR。未写入实例 mods，没有调整任何加载器或 Java 启动设置。

## 构建方法与工具链问题

实际输出隔离在 `work/infinitecore-neoforge-compat-2026-10-15/build-21.1.1` 与 `build-21.1.248`。通过临时 Gradle init script 改输出目录，`--project-cache-dir` 也各自独立；没有把这些临时机器路径写入模块工程。最终日志是工作目录中的 `compat-final-21.1.1.log` 和 `compat-final-21.1.248.log`。

最终两次均离线构建。核心参数如下，`<version>` 分别为 21.1.1 与 21.1.248；PowerShell 必须将带点的 `-P` 参数整体加引号：

```powershell
.\gradlew.bat --offline --no-daemon --console plain `
    '-Pneo_version=<version>' `
    '-PneoForge.neoFormRuntime.useEclipseCompiler=false' build
```

最早版本 SDK 的第一次生成使用了先前工程的 Eclipse 编译器规避选项，生成的开发 class 有无效 `RuntimeInvisibleParameterAnnotations`，使 Java 编译器拒绝读取。改用标准 javac 生成 21.1.1 SDK 后，源码和构建通过，**没有修改或剥离 SDK class 来伪造兼容**。这是本轮遇到的开发工具链问题，不据此把 21.1.1 判为 API 不支持。失败和成功日志分别保留在工作目录 `sdk-probe-21.1.1.log`、`sdk-probe-21.1.1-javac.log`。

首次命令还遇到 PowerShell 将未整体加引号的版本参数拆成 `21` 与任务 `.1.1`；该次未进入有效的 21.1.1 编译，不作为兼容性失败。未升级构建插件、Java 主版本或全局环境变量。

## 交付与边界

- 兼容包：`build/libs/infinitecore-1.1.1-compat.jar`，81,507 字节。
- 本次交付选择 21.1.1 SDK 构建，SHA-256：`B1E8095C98B6810614DD02E76334EE016798625FA397D6720D664F2AB4E9F9A6`。
- 原包 `build/libs/infinitecore-1.1.0-rebuild.jar` 保留，SHA-256 仍为 `AFEFC4BBF0D1C3EB41A9DB82E58A35D882494C1F3A9DE1C8E2A586372366C338`。
- 结构化证据：[neoforge-compatibility-2026-10-15.json](neoforge-compatibility-2026-10-15.json)；旧构建、部署与崩溃调查记录作为历史保留，没有覆盖。

后续低版本实测应由用户确认后，在 Minecraft 1.21.1 的目标加载器环境中确认模组成功加载，再检查直接模板配方、机器 GUI/产出、主动 IO 和退出重载。测试不应直接使用重要旧存档；本轮未创建或进入任何世界。仅修改依赖声明不代表这些运行项目已经通过。
