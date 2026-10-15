# 八阶生成器拆除掉落修复

日期：2026-10-15（Asia/Shanghai）。版本：1.1.2；起点：fb00bec。用户报告生成器破坏后不掉落，批准修复、编译、提交本地 Git，再替换星庭远征-开发版和 Create+ 的旧 JAR，旧版删除、不备份。不启动游戏、不读写存档。

## 原因与修复

1.1.1-compat 和此前重制版本没有八阶方块的掉落表；`GeneratorBlock` 没有拆除回调，方块实体的 `ItemStackHandler` 也不会被原版自动当成容器释放。这对应此前核查中记录的缺口，本轮用户已提供实际不掉落反馈。

- 新增 `data/infinitecore/loot_table/blocks/<tier>_generator.json`，八阶各一份；每次正常采集掉落一个对应阶级的机器。使用标准 `minecraft:survives_explosion` 条件，不额外保证爆炸回收。
- 保留原有 `requiresCorrectToolForDrops`、镐采掘与石质工具等级标签。生存模式需要石镐或更高等级的正确镐；徒手、木镐和创造模式仍遵循原版机器物品掉落规则。
- `GeneratorBlock.onRemove` 在方块种类改变时，于服务端、移除方块实体之前提取并清空全部 10 个槽位，将资源与模板作为独立物品掉出。`ItemStackHandler.extractItem` 和原版 `Containers.dropItemStack` 保留物品组件，包括模板的完整目标栈；客户端不另行生成掉落物，同种方块状态更新不触发释放。
- 机器掉落表不复制库存或方块实体数据，避免与独立库存掉落叠加。重新放置时资源槽和模板槽为空，IO 和进度使用原有初始值；没有增加便携机器或保存 IO 的物品机制。库存释放采用容器式拆除处理，不取决于是否使用正确工具，也不为创造模式额外生成机器物品。
- 配方、产量、生成间隔、注册 ID、网络与保存字段不变，继续声明 Minecraft 1.21.1 / NeoForge `[21.1.1,21.2)`。

掉落表路径及默认方块关联方式依据 [NeoForged 1.21.1 掉落表文档](https://docs.neoforged.net/docs/1.21.1/resources/server/loottables/)。同时核对本机 21.1.1 SDK 中的 `BlockBehaviour.onRemove/getDrops`、`AbstractFurnaceBlock.onRemove`、`Containers.dropItemStack` 以及原版熔炉掉落表。

`tools/build_assets.py` 负责生成八阶掉落表；本轮仅调用其掉落表生成函数，没有重新生成美术或配方。`tools/verify_resources.py` 检查最终 JAR 中八个对应掉落表及原有工具标签，取代旧版“不允许掉落表”的限制；报告日期改为实际检查的上海日期，不覆盖历史报告。

## 已完成的离线验证

- Temurin Java 21.0.12+8、Gradle Wrapper 8.14.2、ModDevGradle 2.0.81；分别在 NeoForge 21.1.1 和 21.1.248 完整 SDK 上离线 `build` 成功，两次 `verifyLogic` 均实际执行，64 组生成间隔及原有边界检查通过。标准 `test` 不是游戏功能验证。
- 两套 SDK 的最终 JAR 解包内容完全相同。与 1.1.1-compat 比较，仅 `GeneratorBlock.class`、版本元数据变化，新增八个掉落表，无文件移除；其它生产 class、所有既有配方和美术字节保持一致。
- 最终资源检查通过：8 张机器掉落表、原有 17 个配方、8 张直接模板配方、2 份原字节工具标签、69 个 JSON、42 张 PNG、模型/贴图引用、语言和 Java 21 class 版本。
- 审查拆除路径：服务端限制、同种方块保护、10 槽提取清空、先释放后调用父类移除；掉落表不复制库存。

发布产物：`build/libs/infinitecore-1.1.2.jar`，85,121 字节，选用 21.1.1 SDK 构建。

SHA-256：`99074A8242313CA813ABB452484236ADB9AB088EBB1B56FBD63A101240DE2AE0`。

结构化构建证据：[2026-10-15-generator-drops-verification.json](2026-10-15-generator-drops-verification.json)。这些结论属于源码、构建和资源检查，没有实测游戏掉落或旧存档兼容。

## 部署范围与用户验收

用户指定部署到 `D:\PCL 正式版 2.10.6 (1)\.minecraft\versions` 下的 `星庭远征-开发版\mods` 和 `Create+\mods`。提交源码后执行替换，逐个核对新版哈希、仅一个 infinitecore、其他模组未改变；删除各自旧 JAR，不创建旧版备份，不改配置和存档。部署结果以实际操作回执为准，构建证据不代替部署证据。

用户启动游戏后可在测试场地检查：

1. 用石镐分别拆除八阶空机器，各回收一个对应机器；重新放置正常打开 GUI。
2. 放入已定制模板和带名称/组件的资源，记下拆除前数量；拆除后回收机器、原模板及库存，目标组件和数量保持一致，没有双份物品。注意运行中的机器可能在拆除前继续生成资源。
3. 重新放置后机器库存为空、六面 IO 关闭；再次拆除空机器，不应再次掉出上一台的库存。
4. 木镐/徒手不回收机器；创造拆除不生成机器物品；正常 GUI/IO 操作和区块退出重载不应意外释放库存。爆炸时遵循原版爆炸存活及物品实体受损规则，不作为保证无损回收的方式。

本次不自动启动客户端、服务器或 GameTest，也不修改既有存档。先前未提交的崩溃诊断文件和其它任务文件保持原状，不纳入修复提交。
