# 第二项重建验证记录

日期：2026-10-10（Asia/Shanghai）。任务起点：`5c547c2`。验证仅包含编译、纯 JVM 数值检查、静态资源核对和静态预览；没有启动游戏、Server 或 GameTest，没有部署或读写存档。

## 构建与数值

本机 Microsoft OpenJDK 21.0.7+6-LTS；Gradle Wrapper 8.14.2；ModDevGradle 2.0.81；NeoForge 21.1.248。JAVA_HOME 只在执行命令的进程中指定，未修改永久环境变量。Gradle 使用既有用户缓存并以 `--offline` 构建，没有安装分析工具。

第一次完整构建成功，包含所有生产源码、八阶资源和 `verifyLogic`。最后的英文方向标签、tooltip 换行修改后再次执行：

```text
gradlew.bat --offline --no-daemon -Dorg.gradle.java.installations.paths=<本机 Java 21 目录> build
PASS: 64 baseline interval pairs, 0.1/2.0 clamps, Java rounding and one-tick floor.
BUILD SUCCESSFUL in 9s
7 actionable tasks: 5 executed, 2 up-to-date
```

完整最后日志保留在被 Git 忽略的 `build/verification/build-final.log`。Gradle 提示未来 Gradle 9 不兼容的弃用功能；当前锁定的 8.14.2 构建成功，本任务未升级任何版本。

`GenerationMathCheck` 用第一项核查独立列出的完整间隔表检查实际生产计算方法，覆盖 64 种模板/机器组合、倍率 0.1/2.0 限制、整数舍入与 1 tick 下限。检查不加载 Minecraft、mod 注册器或世界。标准 `test` 没有 JUnit 用例，本轮没有据此声称附加测试通过。

这些结果没有验证放置、生成、目标组件持久化、IO 传输、菜单操作、实际游戏字体/渲染或退出重载；这些仍需下一项游戏验收。

## 打包资源核查

执行 `python tools/verify_resources.py --write-report`，结果为通过。对照的是最终构建 JAR，详细记录见 [resource-verification.json](resource-verification.json)：

- 八个机器配方与基准逐字节一致；八个直接模板配方准确符合九材料规则；17 个总配方，0 个空白模板配方。
- 保留八个隐藏旧空白模板的物品模型；源代码保留对应注册，但没有通过游戏注册日志验证所有对象已实际加载。
- 61 个 JSON 正常解析，42 个 PNG 解码与尺寸检查通过，本地模型/纹理引用完整；中英语言键一致，源码中的固定翻译键存在。
- ZIP CRC、Java 21 class 版本、元数据版本展开通过；验证用类没有进入生产 JAR。
- 没有新增掉落表或世界阶段资源；这不能证明运行中所有整包外部行为，当前实例未参与检查。

最终产物：`build/libs/infinitecore-1.1.0-rebuild.jar`，81,511 字节。

SHA-256：`AFEFC4BBF0D1C3EB41A9DB82E58A35D882494C1F3A9DE1C8E2A586372366C338`。

基准 JAR 仍为 79,264 字节，SHA-256 `10EC43204404817C8738088670571D5044BBF4B992A9247AB8D67B986AA2E400`。

## 静态视觉检查

已打开 [visual-preview.png](visual-preview.png) 检查全八阶机器与卡片、主界面、六面 IO 页。机器侧面/顶面结构统一，材质色与灯标区分阶级；模板/目标、资源区、信息区、状态和玩家库存分开。主界面 176×216，快捷栏槽框底边在 y=208 内；IO 列表没有与主界面槽位同时显示。

这是资源投影与系统字体示意，邻居、目标和库存为示例，没有启动 Minecraft。实际游戏窗口缩放、字体边界、点击命中、物品渲染与多人同步不能用这张图代替验收。

## 差异边界与后续

本项只纳入 `modules/infinitecore` 和根 `INFINITECORE-REBUILD-PLAN.md`。构建 JAR、Gradle 缓存、日志、pycache 不纳入提交；Wrapper JAR 是仓库既有规则允许的构建入口。原 vendor、其它模块、原始实例和存档保持原样。

暂存审查发现 Git 的文本换行转换会改变基准机器配方字节；模块 `.gitattributes` 仅将八个保留机器配方与复制工具标签设置为 `-text`，并为它们的原 CRLF 启用 `cr-at-eol`，保留其它默认空白检查。随后重新暂存并对 Git blob 与基准再次核对。这样“逐字节保留”在提交/重新检出后仍成立，不改变原资源或其它文本文件。

未加入对外 ItemHandler 能力或机器/库存拆除掉落，也未改动原 progress 的计数/保存触发方式。旧字段读取与 ID 保留只是兼容候选，不能宣布旧存档兼容。下一项需先明确测试实例/世界、备份、部署与启动范围，按用户规则获得批准后，再实测泥土最小循环、八阶、IO 与重载。
