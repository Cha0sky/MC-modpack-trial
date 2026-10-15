# 无限之核 1.1.1-compat

这是根据用户自制 `vendor/infinitecore/infinitecore-1.0.0.jar` 的资源与字节码核查结果重新编写的工程，**不是恢复的原始源码**。原 JAR 的 SHA-256 为 `10EC43204404817C8738088670571D5044BBF4B992A9247AB8D67B986AA2E400`，保留原件。

## 当前改动

- 删除空白模板的获取流程：九个原空白模板材料直接合成一张对应生成模板。泥土、橡木原木、平滑石、铜块、铁块、金块、钻石块、下界合金块分别对应八阶。
- 八种旧空白模板 ID 只作隐藏兼容占位，保留旧图标和名称；没有配方，不进入创造栏，不被机器当作生成模板，没有自动转换。
- 八阶机器配方保持基准 JAR 的原字节；生成每次 1 个，基础间隔仍为 360/180/72/36/24/12/6/1 tick，完整跨阶倍率保持。
- 生成模板保存一个完整目标 ItemStack；允许原来的非生成模板目标，复制组件，不消耗运行模板；定制合成仍消耗模板输入和目标材料并遵循标准合成剩余物。
- 保留资源槽 0–8、模板槽 9、可手工存放的混合资源库存、4 tick 主动 IO、六面初始关闭和旧保存键 `inventory` / `io_modes` / `progress`。
- 八阶统一 16×16 像素机器，独立顶面/底面，阶级色与八级灯标；模板为统一卡片图案。方块仍无朝向属性，IO 使用世界方向。
- 主界面 176×216：真实模板槽、只显示的目标图标、资源库存、实际生成间隔、理论速率、倍率、进度和阻塞原因。长文本截断，目标与说明可悬停查看。
- IO 页保持同一个服务端菜单，隐藏主页面槽位；六个世界方向有常驻文字、邻居名、服务端物品接口检测与模式按钮。首次菜单数据和后续模式由服务端同步，连续点击等待确认。
- Shift 移动结果按是否实际移动返回，避免“模板已移动却返回 EMPTY”；IO 修改要求当前对应菜单和八格内操作。网络 ID 保留，协议改为 `2`，新旧客户端不能混用。

**未加入**：对外 ItemHandler 能力、拆除掉落规则、世界阶段或资源黑白名单、燃料/能量、红石暂停、离线补产。progress 仍保留原计数/保存时机；更精确的保存修复需要后续运行验证与确认。

## NeoForge 兼容范围（2026-10-15）

当前兼容声明为 Minecraft **1.21.1**、NeoForge **`[21.1.1,21.2)`**。官方发布列表中最早的 21.1.x 是 21.1.1；本模块已在 21.1.1 和 21.1.248 两套完整 SDK 上构建通过，既有 64 组数值检查通过。两套构建的 18 个生产 class 与原 1.1.0-rebuild 逐字节一致；解包内容仅 `META-INF/neoforge.mods.toml` 变化。

`neo_version` 是开发 SDK，保留 21.1.248；`neo_version_range` 是允许的运行范围，独立设置为 `[21.1.1,21.2)`，不再把玩家运行下限硬写成 21.1.248。最低版本仍是**静态/构建兼容候选**，没有启动游戏验证旧加载器的实际运行；整合包其它模组也可能要求更高版本。

详情、工具链问题与核查证据见 [docs/NEOFORGE-COMPATIBILITY.md](docs/NEOFORGE-COMPATIBILITY.md)。本次兼容检查没有部署或修改实例、配置、存档，原 1.1.0-rebuild JAR 保留。

## 工程与构建

开发默认使用 Minecraft 1.21.1 / NeoForge SDK 21.1.248 / Java 21 / Gradle Wrapper 8.14.2 / ModDevGradle 2.0.81。没有复制其它模块的游戏运行目录、删除任务、配置或历史测试。

在本模块目录、Java 21 可用时执行：

```powershell
.\gradlew.bat --offline --no-daemon build
python tools/verify_resources.py --write-report
```

检查最低 SDK 时，使用 Java 21 并对 PowerShell 中的完整参数加引号：

```powershell
.\gradlew.bat --no-daemon '-Pneo_version=21.1.1' '-PneoForge.neoFormRuntime.useEclipseCompiler=false' build
```

首次离线构建要求现有 Gradle/NeoForge 缓存；缺失时不能保证离线可构建。通过进程内 JAVA_HOME 指定 Java 21，不需要更改用户的永久环境变量。项目未配置自动启动、部署或存档迁移任务。

`verifyLogic` 是 JavaExec 的纯 JVM 验证：使用独立基准间隔表检查 64 种阶级组合和上下限，不引导 Minecraft，不创建世界。标准 `test` 本轮没有 JUnit 用例；“test 任务运行”不应被算作游戏或序列化功能测试。

产物：`build/libs/infinitecore-1.1.1-compat.jar`。本次交付选用最低 SDK 21.1.1 构建；构建缓存和产物不提交 Git，生产源码、Wrapper 和资源提交。不要与 1.0.0 或 1.1.0 同时安装。**本轮未启动游戏、部署或验证旧存档兼容，当前产物是待低版本游戏验收的兼容包。**

## 资源维护与视觉预览

`tools/build_assets.py` 以程序化像素绘制方式生成新机器/模板/GUI 图，并从只读基准复制机器配方、工具标签和旧空白模板图标。只写 `src/main/resources`，不会修改实例、存档或 vendor。重新运行会覆盖该脚本负责的模块资源，手工编辑后请先审查差异。

```powershell
python tools/build_assets.py
python tools/preview_assets.py
```

预览文件：[docs/visual-preview.png](docs/visual-preview.png)。它是静态贴图投影与界面示意，使用系统字体、示例目标与邻接方块；不是游戏截图，也不证明实际游戏字体、槽位操作或渲染已验收。预览脚本可只读使用本机已缓存的原版钻石图标，路径不可用时目标图示为空；这些辅助图标不进入模组生产资源。

1.1.0 历史资源记录：[docs/resource-verification.json](docs/resource-verification.json)，历史构建记录：[docs/VERIFICATION.md](docs/VERIFICATION.md)。本次 1.1.1 兼容检查见 [docs/neoforge-compatibility-2026-10-15.json](docs/neoforge-compatibility-2026-10-15.json)。历史功能基线和批准后的配方变更见仓库根 `INFINITECORE-REBUILD-PLAN.md`。

许可：重新实现的源码与新美术按 MIT；沿用基准中的 MIT 声明。复制的 Gradle Wrapper 保留 Apache-2.0 头部；MDK 模板许可保留于 `TEMPLATE_LICENSE.txt`。模块没有包含 Minecraft/NeoForge 的依赖源码或运行 JAR。
