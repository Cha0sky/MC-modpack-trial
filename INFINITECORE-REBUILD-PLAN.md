# 无限之核：原模组功能核查与重建改版方案

核查日期：2026-10-10（Asia/Shanghai）。任务起点：`main` / `0b91a20`，工作区与暂存区均干净。本文是第一项任务的产物，第二项重新实现尚未获准。

## 1. 结论与证据边界

可以以现存 JAR 为行为基线重新编写可维护源码，并保留 `infinitecore` 和全部已核实注册 ID。**这属于重新实现，不是恢复原始源码；保留 ID 也不等于已经证明存档兼容。**

主要核查结果：

- 八阶基础间隔完整确认为 **360、180、72、36、24、12、6、1 tick**；每次成功生成 **1 个物品**。模板阶级还会改变实际间隔，不能只复刻基础数值。
- 生成模板通过 `infinitecore:target_item` 保存一个数量为 1 的完整 `ItemStack`，并非只保存资源 ID；生成时复制其数据组件。没有发现资源白名单、Boss 限制或世界阶段检查。
- 机器生成时不消耗模板、不耗燃料或能量。制作模板、机器和定制模板仍涉及合成材料消耗。
- 机器有 9 个资源槽和 1 个模板槽，六面默认关闭；每 4 tick 主动输出资源、吸入模板。没有发现本机对外注册物品能力的实现，不能把主动输出说成“已经支持漏斗/Create 主动抽取”。
- 八阶外观使用 16×16 的同构图案、六面相同的立方体模型；主 GUI 缺少面板层次，进度显示未考虑实际模板倍率，IO 方向依赖悬停提示。这些为具体改善依据。
- 保存字段和读取路径已经明确；实际退出重载、旧世界加载、机器拆除掉落、IO 首次打开同步及自动化兼容均未进行游戏实测。

本文使用三种证据口径，并单独标记改版候选：

| 标记 | 含义 |
|---|---|
| 已核实（静态） | 从基准 JAR 资源、字节码直接读出；继承行为使用本机既有的对应版本源码交叉检查 |
| 推断 | 静态证据支持的运行结果或问题，尚未用游戏复现；不视为实测通过 |
| 待实测 | 涉及运行、客户端/服务端同步、整包影响、存档兼容或视觉效果，必须另行批准测试 |
| 候选 | 改版建议，需用户确认；不自动成为实施授权 |

遵循当前 `AGENTS.md`、`PLAN.md`、`STATE.md`、`SOURCE-ARCHIVE.md`。固定基地、Create 自动化、休闲生活与 RPG 冒险是背景；**本次及拟议重建不自行加入世界阶段联动、资源黑名单或新的成长曲线**。旧 HANDOFF 和历史设计没有覆盖 PLAN。

## 2. 二进制与检查方法

基准：`vendor/infinitecore/infinitecore-1.0.0.jar`。

| 项目 | 已核实值 |
|---|---|
| 文件大小 | 79,264 字节 |
| SHA-256 | `10EC43204404817C8738088670571D5044BBF4B992A9247AB8D67B986AA2E400` |
| ZIP 内容 | 121 个文件；24 个 class；69 个 JSON；25 个 PNG；ZIP CRC 检查通过 |
| class major version | 全部为 65，即 Java 21 字节码 |
| 元数据 | `META-INF/neoforge.mods.toml`：mod ID `infinitecore`，版本 `1.0.0`，名称 `Infinite Core`，作者 `Infinite Core Team`，声明 MIT |
| 依赖声明 | `javafml` / loader `[4,)`；NeoForge `[21.1.0,)`；Minecraft `[1.21.1,1.22)`；两依赖均 required / BOTH |
| 资源包 | `pack.mcmeta`：`pack_format=34`，`Infinite Core resources` |

JAR 依赖范围不是实际开发基线；新工程继续锁定 Minecraft 1.21.1、NeoForge 21.1.248、Java 21、Wrapper 8.14.2、ModDevGradle 2.0.81。JAR 本身没有证明原源码使用的 Gradle 工具链。

检查使用现有 Python `zipfile` / Pillow 和本机 Java 21 的 `javap -c -p`、`javap -v -p`，没有安装反编译器或其它分析工具。逐类检查了 JAR 的全部 24 个 class，包括匿名类、网络包和注册类。资源在内存中解码；八阶机器、空白/生成模板和 GUI 底图以最近邻放大进行视觉检查，没有修改或导出到实例。

涉及继承行为时，只读引用以下现有缓存，未运行 Gradle、下载依赖或构建：

- `C:\Users\俞皓天\.gradle\caches\modules-2\files-2.1\net.neoforged\neoforge\21.1.248\f0378c9b210dfc258170b6412a85fdffe4aea7fe\neoforge-21.1.248-sources.jar` 中的 `ItemStackHandler.java`、`SlotItemHandler.java`。
- `C:\Users\俞皓天\.gradle\caches\neoformruntime\intermediate_results\sourcesAndCompiledWithNeoForge_64ecec9b14996f3e2a8fd6b3b6c73f92fa632a08_output.jar` 中的 Minecraft `Recipe`、`CustomRecipe`、`ResultSlot`、`Direction`、`Item`、`Block`、`BlockBehaviour`、`BlockEntity`。此为辅助解释对应 API 的本机缓存，不是无限之核源码。

初次终端输出有中文解码乱码；随后对 JSON 按 UTF-8 解码、对本机 javap 标准输出按 GB18030 解码重新核对。**实际语言资源正常，不将终端乱码误报为模组文本损坏。**

## 3. 注册对象清单

以下均在 `infinitecore` 命名空间。八阶顺序固定为 `dirt, wood, stone, copper, iron, gold, diamond, netherite`，对应 ordinal 0–7。它参与倍率公式，不能随意调整枚举顺序。

| 阶级 | 空白模板物品 | 生成模板物品 | 机器方块及同名方块物品 |
|---|---|---|---|
| 泥土 | `blank_template_dirt` | `template_dirt` | `dirt_generator` |
| 木头 | `blank_template_wood` | `template_wood` | `wood_generator` |
| 石头 | `blank_template_stone` | `template_stone` | `stone_generator` |
| 铜 | `blank_template_copper` | `template_copper` | `copper_generator` |
| 铁 | `blank_template_iron` | `template_iron` | `iron_generator` |
| 金 | `blank_template_gold` | `template_gold` | `gold_generator` |
| 钻石 | `blank_template_diamond` | `template_diamond` | `diamond_generator` |
| 下界合金 | `blank_template_netherite` | `template_netherite` | `netherite_generator` |

合计：24 个物品注册（8 空白 + 8 生成模板 + 8 BlockItem），8 个方块。

| 注册类别 | 完整 ID | 说明 / 证据 |
|---|---|---|
| 方块实体类型 | `infinitecore:generator` | 单一类型支持全部八阶；`ModBlockEntities` |
| 菜单类型 | `infinitecore:generator` | 全部八阶共用；`ModMenus` |
| 数据组件 | `infinitecore:target_item` | `DataComponentType<ItemStack>`；`ModDataComponents` |
| 配方序列化器 | `infinitecore:template_customize` | `SimpleCraftingRecipeSerializer<TemplateCustomizeRecipe>` |
| 创造标签页 | `infinitecore:infinite_core` | 图标为下界合金生成器；先列空白模板，再生成模板，再机器 |
| C2S payload | `infinitecore:update_io_config` | 位置、面序号、模式 |
| S2C payload | `infinitecore:sync_io_config` | 位置、模式 byte 数组；网络 registrar 版本 `1` |

注册证据为 `ModItems`、`ModBlocks` 的循环和字符串拼接常量，以及上述注册类和网络类的静态初始化。JAR 没有发现额外配置文件、独立资源规则标签、阶段接口、Mixin 或能量/流体注册实现。

方块属性：强度 4.0、爆炸抗性 8.0、石头音效、要求正确工具掉落；全部位于 `minecraft:mineable/pickaxe` 和 `minecraft:needs_stone_tool` 标签。`GeneratorBlock` 没有自定义 blockstate 属性，没有朝向、运行状态或端口状态属性。右键的 `useWithoutItem` 在服务端通过 MenuProvider 打开菜单；服务端 ticker 调用 `tickServer`，客户端不运行生成 ticker。

## 4. 八阶配方、默认产物与基础速率

全部 24 个普通配方是 `minecraft:crafting_shaped`，产物数量均为 1，材料使用精确 item ID，而非 tag。另有 1 个定制配方，总计 25 个配方。

统一图形：

```text
空白模板       生成模板         机器（前七阶）   下界合金机器
XXX            BBB              TTT              TTT
XXX            BCB              TCT              T T
XXX            BBB              TTT              TTT
```

X：表中的空白材料；B：同阶空白模板；T：同阶生成模板；C：相应核心材料。下界合金机器中心确实为空，不擅自补入核心。

| 阶级 | 空白模板：9×X | 生成模板：8×B + C | 机器：8×T + C | 默认产物 | 基础间隔 tick | 同阶模板时理论个/秒 |
|---|---|---|---|---|---:|---:|
| 泥土 | `minecraft:dirt` | `minecraft:dirt` | `minecraft:oak_planks` | `minecraft:dirt` | 360 | 0.05556 |
| 木头 | `minecraft:oak_log` | `minecraft:oak_log` | `minecraft:stone` | `minecraft:oak_log` | 180 | 0.11111 |
| 石头 | `minecraft:smooth_stone` | `minecraft:stone` | `minecraft:copper_ingot` | `minecraft:stone` | 72 | 0.27778 |
| 铜 | `minecraft:copper_block` | `minecraft:copper_ingot` | `minecraft:iron_ingot` | `minecraft:copper_ingot` | 36 | 0.55556 |
| 铁 | `minecraft:iron_block` | `minecraft:iron_ingot` | `minecraft:gold_ingot` | `minecraft:iron_ingot` | 24 | 0.83333 |
| 金 | `minecraft:gold_block` | `minecraft:gold_ingot` | `minecraft:diamond` | `minecraft:gold_ingot` | 12 | 1.66667 |
| 钻石 | `minecraft:diamond_block` | `minecraft:diamond` | `minecraft:netherite_ingot` | `minecraft:diamond` | 6 | 3.33333 |
| 下界合金 | `minecraft:netherite_block` | `minecraft:netherite_ingot` | 无中心材料 | `minecraft:netherite_ingot` | 1 | 20 |

理论速率按 20 TPS、空间充足、持续加载计算；不是游戏测量值。生成无目标组件时，默认产物由**机器阶级**决定，而不是由插入的模板阶级决定。

资源证据：`data/infinitecore/recipe/blank_template_<tier>.json`、`template_<tier>.json`、`<tier>_generator.json`；数值和默认产物证据：`gen.GeneratorLevel` 静态初始化与 `block.GeneratorBlockEntity.tryGenerate`。

普通配方的 ingredient 只匹配模板物品种类，没有要求模板未定制，也没有要求八张模板的目标一致。因此带目标组件的同阶模板也符合机器材料条件；合成机器不会继承它们的复制目标。该材料匹配结果仍应在游戏配方/整包覆盖环境核对。

## 5. 模板记录、允许资源与消耗

### 5.1 空白模板与生成模板不是同一种可用模板

`ModItems.isTemplate` 和 `levelOfTemplate` 仅遍历八种 `template_<tier>`；**不包含 `blank_template_<tier>`**。生成模板使用 `TemplateItem`，空白模板是普通 Item。三类物品均使用默认 Item.Properties，没有自定义耐久或剩余合成物；默认堆叠为 64。

`TemplateCustomizeRecipe.matches` 要求恰好两个非空格子：一个生成模板、一个非生成模板物品。第二个生成模板会使匹配失败；目标数量按占用格子检查，不按堆叠总数检查。任意位置可放，工作网格面积至少为 2，因此允许 2×2 和 3×3 网格。

“非生成模板”包括普通材料、装备、容器、模组物品、空白模板甚至机器方块物品。静态代码没有黑名单、标签限制、阶段判定或按阶级限制资源。目标堆叠所携带的数据组件会一并复制；附魔、名称、耐久、容器内容等应按其实际组件验证，不得将它改写成只存物品 ID。

### 5.2 写入与生成

`assemble`：复制输入生成模板 → 数量设为 1；复制目标 ItemStack → 数量设为 1；将后者写入输出模板的 `TARGET_ITEM`。既有已定制模板也可以再次定制；目标组件被新目标覆盖，其它模板组件随模板 copy 保留。

`ModDataComponents` 明确使用 `ItemStack.OPTIONAL_CODEC` 持久化、`ItemStack.OPTIONAL_STREAM_CODEC` 网络同步，注册 ID 为 `infinitecore:target_item`。这不是旧式自行定义的 `targetItemId` NBT 字符串。

`tryGenerate`：模板槽为空则退出；非空且目标组件存在、非空时，复制目标栈并设数量为 1；否则新建机器阶级的默认产物。逐个尝试资源槽 0–8，成功插入一件就返回。**此路径没有 shrink 模板、损坏模板或移除模板组件。**

正常 GUI 与吸入路径只放入生成模板，但 `tryGenerate` 本身只检查模板槽非空，没有再验证物品身份。异常写入非模板物品时的行为属于边界情况，不应悄悄改动后再声称与原版完全一致。

### 5.3 “不消耗”的准确范围

| 情境 | 静态核查结果 |
|---|---|
| 持续生成 | 模板数量与目标保持，生成过程无消耗 |
| 定制模板合成 | 输入模板消耗一张，输出一张带目标的同 ID 模板；不是输入模板原地保持 |
| 用于定制的目标 | 通常消耗一个；本模组没有返还目标的 override，遵循目标物品的标准 crafting remainder，如某些容器可能返还空容器 |
| 制作生成模板/机器 | 消耗配方材料；不会返还八张模板 |
| 自动吸入模板 | 从邻接库存抽出一张移到模板槽；是移动，不是额外复制 |

消耗判断交叉检查了现有 Minecraft `Recipe.getRemainingItems` 和 `ResultSlot.onTake`。本模组 `TemplateCustomizeRecipe` 未覆盖 remainder；`TemplateItem` 未设置 craftingRemainingItem。不同模组目标的特殊 remainder 需实测。

## 6. 实际间隔、库存、输出与运行细节

### 6.1 跨阶倍率必须保留

`GeneratorLevel.multiplier(machineOrdinal, templateOrdinal)`：

```text
m = max(0.1, min(2.0, 1.0 + (templateOrdinal - machineOrdinal) * 0.2))
actualInterval = max(1, Math.round(baseRateTicks / m))
```

高阶模板装进低阶机器加快；低阶模板装进高阶机器降低倍率。**八阶生成模板均可装入八阶机器**，没有同阶门槛。倍率限于 0.1–2.0，最终间隔是整数 tick，产量仍为每次 1 个。保留 Java double 和 `Math.round` 的语义，不能使用银行家舍入替代。

按该公式计算的完整间隔表（行=机器，列=模板；属于公式推导而非实测）：

| 机器 / 模板 | 泥土 | 木头 | 石头 | 铜 | 铁 | 金 | 钻石 | 下界合金 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 泥土 | 360 | 300 | 257 | 225 | 200 | 180 | 180 | 180 |
| 木头 | 225 | 180 | 150 | 129 | 113 | 100 | 90 | 90 |
| 石头 | 120 | 90 | 72 | 60 | 51 | 45 | 40 | 36 |
| 铜 | 90 | 60 | 45 | 36 | 30 | 26 | 23 | 20 |
| 铁 | 120 | 60 | 40 | 30 | 24 | 20 | 17 | 15 |
| 金 | 120 | 60 | 30 | 20 | 15 | 12 | 10 | 9 |
| 钻石 | 60 | 60 | 30 | 15 | 10 | 8 | 6 | 5 |
| 下界合金 | 10 | 10 | 10 | 5 | 3 | 2 | 1 | 1 |

`tickServer` 每服务端 tick 增加 progress，达到实际间隔时先清零再尝试生成。没有模板时 progress 仍按基础间隔循环；满库存时失败的这次生成不积攒、不排队。插入/替换模板没有重置 progress 的代码，因此第一件延迟受原进度和新间隔影响。没有红石暂停、能量、离线补算或跨区块强制加载逻辑。

### 6.2 库存

- `ItemStackHandler(10)`：资源槽 0–8，模板槽 9。九资源槽为 3×3。普通 64 堆叠资源最多 576 个；不可堆叠物最多 9 个；不能将容量一律写成 576。
- 内部 Handler 匿名类仅覆盖 `onContentsChanged`，调用方块实体 `setChanged`；没有覆盖 `isItemValid` 或槽位上限。对应 NeoForge 源码上限为 `min(99, itemStack.maxStackSize)`，插入/合并要求物品及组件相同。
- GUI 模板槽单独限制 `ModItems.isTemplate`，但没有限制只能放 1 张；堆叠模板不会增加产量。资源槽是普通 `SlotItemHandler`，允许手工存入任意物品，**并非仅取出的输出槽**。
- 切换目标不会清空旧产物；库存可以混放多种物品。满库存导致本次产出失败，GUI 原实现仍可能显示“生成中”。

### 6.3 六面 IO

`IoMode` 固定编码和循环：`NONE=0 → INPUT=1 → OUTPUT=2 → BOTH=3 → NONE`。新机器六面 NONE。保存数组按 Direction 3D 编号：0 下、1 上、2 北、3 南、4 西、5 东。

每 4 tick 遍历六面；BOTH 在该面先 push 再 pull。操作前检查邻接位置 loaded；通过 `Capabilities.ItemHandler.BLOCK` 查询邻接方块**朝向本机的一面**的 IItemHandler。

| 操作 | 具体行为 |
|---|---|
| OUTPUT / pushTo | 遍历资源槽 0–8，每槽抽取最多 64 个，顺序插入邻接 Handler 所有槽；剩余回插原资源槽；不输出模板槽 |
| INPUT / pullTemplate | 仅在模板槽为空时遍历邻接槽；找到首个八种生成模板之一并成功抽出 1 张后，写入模板槽并返回；不吸入普通资源，不替换已有模板 |
| NONE | 本机不对该面执行主动 IO |
| 没有邻接能力/邻接未加载 | 跳过该面，不强行加载 |

“每 4 tick”是 IO 调度，不是所有阶级的生成周期；每面每轮也不是只输出一个物品或只处理一个槽。pushTo 抽出再回插会触发内容变更；未做能力缓存，规模性能需后续实际测量，不据此虚构已发生卡顿。

全部 24 类检查没有 `RegisterCapabilitiesEvent`、`registerBlockEntity` 或本机 Handler 注册回调。`GeneratorBlockEntity` 也不实现原版 Container。因此**核实的是本机主动访问邻居的接口；外部漏斗、管道、Create 能否主动访问本机未验证**，新增对外能力属于候选功能补全，需确认行为、面过滤和模板保护后实施。

## 7. GUI 实现与原视觉检查

### 7.1 机器和模板资源

已打开并查看 `assets/infinitecore/textures/block/<tier>_generator.png` 全八张及对应物品贴图，模型/方块状态 JSON 全部读取。

- 八张机器贴图均为 16×16 RGBA：灰暗外框、四角近黑固定点、内层阶级材质框、中间大块纯白核心。构图完全同类，亮度集中在纯白中心，缺少侧面机械结构与端口线索。
- 泥土/木头使用相近棕色，石头/铁使用相近灰色，正常缩小观看很难分辨。像素比较确认它们并不是相同文件或完全相同像素，不能把“相近”写成“重复贴图”。铜为铜橙、金为黄、钻石为青、下界合金为暗紫。
- 全八阶模型为 `minecraft:block/cube_all`，all 指向对应单张贴图；blockstate 只有空 variant。前、后、左、右、上、下都显示同一正面式图案，没有朝向变化或几何结构。
- 空白模板为材质色方片和四分结构；生成模板为带明亮中心的边框方片。均为 16×16，`minecraft:item/generated`，仅 layer0。没有按 target_item 动态显示目标图标的模型规则。

依据：`assets/infinitecore/models/block/`、`models/item/`、`blockstates/`、`textures/block/`、`textures/item/`。没有动画 `.mcmeta` 或方块实体自定义渲染器。

### 7.2 主界面实际布局

GUI 底图 `assets/infinitecore/textures/gui/generator.png` 是 256×256 RGBA，内容主要是平灰背景和一条黑色竖槽；代码只使用左上 176×166 区域。不能把这张底图当成最终游戏截图：槽位、文字、按钮、物品和进度由代码另行绘制。

坐标以主面板左上角为原点：

| 元素 | 代码位置 / 方式 |
|---|---|
| 标题 | (8,6)，颜色 `0x404040` |
| 配置按钮 | (124,4)，44×18，标准 Button |
| 模板槽（索引 9） | (8,17)，只有一个槽，无常驻用途标签 |
| 九资源槽（索引 0–8） | (30+18×列,17+18×行) |
| 槽位背景 | 遍历全部 46 槽，在 x−1,y−1 绘制原版 `minecraft:container/slot` 18×18 sprite |
| 竖向进度 | (89,18)，4×52，绿色填充，自下而上 |
| 玩家背包标题 | (8,70) |
| 状态文字 | (30,75)，目标名称与倍率在一行，没有截断/换行处理 |
| 玩家背包 | (8+18×列,84+18×行)，27 槽 |
| 快捷栏 | (8+18×列,138)，9 槽 |

已核实显示逻辑：无模板显示“未放置模板”；有模板显示“生成中（×倍率）”或“生成中：目标名称（×倍率）”，模板 tooltip 显示复制目标或定制提示。中英两套语言 JSON 均存在且可正常解析。

已核实实现问题：进度条分母是机器 `rateTicks()`，没有用 `actualInterval`。例如泥土机器配木模板实际 300 tick，条却按 360 tick 显示，约 83% 就复位；配更低阶模板时可能提前满条后等待。高阶 1 tick 周期每次先清零，界面也可能难以看到进度。

视觉/交互推断：平灰面板没有明确框架和信息区，4 像素细条可读性低，模板与库存缺少区分；状态 y=75 与背包标题 y=70 太近，长目标名称没有宽度约束，存在碰撞或越界风险。满库存仍称“生成中”不能说明停产原因。最终字体边界和缩放效果须游戏验证。

Shift 点击：机器前十槽 → 玩家槽 10–45；玩家生成模板先尝试槽 9，之后剩余仍可能进入资源槽 0–8；普通物品直接进入资源槽。模板成功移动后仍尝试资源区的路径可能造成返回值/剩余栈表现异常，列入待实测。菜单 `stillValid` 只检查该位置仍是相同 BE，没有玩家距离判断。

### 7.3 IO 页面

`IoConfigScreen` 是单独的普通 Screen，150×104；没有专用 PNG。代码 fill 绘制深灰面板、彩色面按钮和返回区域，使用原菜单返回主界面；ESC 也返回主界面。左键点击六个约 20×20 热区循环模式。

| 图示位置 | 面名（硬编码中文） | Direction ID / 世界方向 | 面按钮局部坐标 |
|---|---|---|---|
| 上方 | 上 | 1 / UP | (66,22) |
| 左侧 | 左 | 4 / WEST | (46,42) |
| 中间 | 前 | 3 / SOUTH | (66,42) |
| 右侧 | 右 | 5 / EAST | (86,42) |
| 下方 | 下 | 0 / DOWN | (66,62) |
| 右下 | 后 | 2 / NORTH | (86,62) |

颜色：输入 `#B05050`，输出 `#5080C8`，双向 `#C070C0`，关闭 `#6E6E6E`。存在邻接非空气方块时渲染其物品图标；悬停显示面名、模式与“连接”的邻接方块名。**这里“连接”只是邻居方块存在，并未检查它有可用物品能力。** 世界方向固定，不随机器或玩家朝向旋转；前/后并非玩家当时面前/身后。

界面缺少常驻方向与模式文字，主要靠颜色和 tooltip；邻居图标还会遮挡色块。“点击行循环切换模式”的语言键虽存在，实际绘制的是面图按钮，没有行列表或显示该说明的调用。方向中文硬编码使英文界面也可能显示中文。

## 8. 数据字段、同步、重载与兼容性

### 8.1 持久化和运行字段

| 所属 | 字段 / NBT 键 | 类型与语义 | 读取/缺省 |
|---|---|---|---|
| 模板 ItemStack | 组件 `infinitecore:target_item` | 完整目标 ItemStack，定制时 count=1；OPTIONAL_CODEC | 无组件/空组件时生成机器默认产物 |
| BE | `inventory` | ItemStackHandler 序列化 CompoundTag；10 槽 | 存在该键时 deserializeNBT |
| inventory 子结构 | `Size`、`Items`、每条 `Slot` | Size=10；非空物品列表、索引与标准 ItemStack 内容 | 对应 NeoForge Handler 默认格式；只载入合法索引 |
| BE | `io_modes` | byte[]，6 个 Direction 编号所对应的模式 ID | 缺失保持构造时全 NONE；只读取前 min(6,length) 个，未知 mode ID 映射 NONE |
| BE | `progress` | int，生成计数器 | 直接 getInt；缺失为 0 |
| 仅运行 | `ioCounter` | int，每 4 tick 清零 | 不保存；新加载 BE 初始 0 |
| 阶级来源 | block ID / `GeneratorBlock.level` | 从世界方块取得 GeneratorLevel | 非预期情境回退 DIRT；没有独立 NBT tier 字段 |
| 菜单 | 1 个 ContainerData 值 | progress；构造与 broadcastChanges 更新 | 常规菜单数据同步 |

BE `saveAdditional/loadAdditional` 已直接核查。Handler 的内容变更及 `setIoMode` 调用 setChanged；**progress 单独递增和清零没有逐次 setChanged**。所以“有 progress 保存键”不能保证任意卸载时最后一刻的计数器精确保留，特别是满库存或无模板时没有其它内容变更；需实测保存时机。没有时间戳或离线产量补算。

### 8.2 网络与初次同步边界

`UpdateIoConfigPacket` 在服务端主线程取玩家当前维度对应位置的 BE，设置该面模式后，将全六面 `SyncIoConfigPacket` 发给该维度所有玩家。面下标由 setIoMode 限定在 0–5，mode 解码使用 fromId。

`SyncIoConfigPacket` 在客户端更新已经加载的对应 BE 的模式。静态检查只发现修改模式时的发送路径；打开主菜单、打开 IO 页及 BE 初次加载没有主动发送现存模式的代码。BE 未覆盖 `getUpdateTag/getUpdatePacket`，对应默认实现分别为空 Tag / null。因此服务器已存 IO 设置、客户端首次载入后显示 NONE 的情况有明确静态依据，但尚未游戏复现；连续点击在服务器回包前还可能反复提交相同模式。

服务端包处理没有确认发送者正在操作对应菜单，也没有距离或权限检查；菜单 stillValid 也没有距离条件。这属于运行边界问题，建议作为单独确认的修复项，不当作配方/速率功能改动偷偷带入。

### 8.3 拆除与掉落

JAR 没有 `data/infinitecore/loot_table/blocks/` 资源；`GeneratorBlock` 没有 onRemove、playerDestroy、getDrops 或库存掉落覆盖，BE 也不是 Container。原版默认 onRemove 仅移除 BE。推断基准 JAR 单独运行时，拆除可能不掉落机器及内部物品；整包数据包或其它模组可能补充行为，**本次未检查或更改实例，不能宣称原整包实际一定如此**。这是迁移和功能验收的重要待实测项。

### 8.4 保留 ID 的可行性与条件

保留全部注册 ID 在新源码中可行；Java 包名不必决定存档 ID，但命名与结构应清晰可维护。必须同时保留数据组件类型/Codec、BE 类型所支持的全部八个方块、槽位顺序、NBT 字段名和含义、IO 编号、旧缺省规则，才能建立兼容候选。

第一版建议保持无朝向 blockstate，只通过模型区分上下和四侧，避免视觉升级同时引入旧 blockstate 转换。正面朝向、动态端口外观或运行属性需单独确认并验证旧状态缺省；IO 仍按世界方向保存。不得把 GUI 移动槽位的屏幕坐标误当成可以重排存储索引。

最低迁移验收必须在另行批准的**原版生成的测试世界副本**上完成：八阶已放置机器、背包/箱子里的 24 种物品、带复杂组件的定制模板、混合库存、全部 IO 模式、不同 progress 值；新 JAR 加载后检查缺失注册日志、方块和物品、目标组件与库存，再保存退出重进检查。新旧 JAR 不得同时加载；原世界保留。客户端与服务端网络版本应配套更新，不能假设新旧客户端可混用。

本轮没有打开存档、读取世界 NBT、验证迁移，也没有证明独立服务器或整包兼容。

## 9. 具体视觉改版候选（需确认）

### 9.1 推荐候选：统一机械核心，克制使用阶级色

保留单方块、完整立方体占地和统一“核心”符号。候选 A 使用暗灰机械外壳、有限铆钉、侧面散热格栅、上方核心盖板；将纯白中心改为较小的核心窗，以亮暗层次表达结构。四侧共用侧面设计，上/下单独设计，不依靠一张“正面图”铺满六面。

先做 16×16 像素风候选，保持原版尺度；若细节不够，用户可选 32×32 全八阶统一升级。首次不使用逐 tick 动画、粒子或重型 BER；是否需要动态发光与端口标记在预览后决定。机械外观不意味着新增能量需求。

| 阶级 | 候选主色 | 除颜色外的辨识线索 |
|---|---|---|
| 泥土 | 陶土棕 `#876044` | 粗糙陶土面、一级铭牌 |
| 木头 | 暖木色 `#BA8852` | 木纹嵌板、二级铭牌，亮度与泥土明显分开 |
| 石头 | 石板灰 `#727F89` | 石材拼缝、三级铭牌 |
| 铜 | 铜橙 `#C87546` | 铜边、四级铭牌 |
| 铁 | 银灰 `#C8D2DA` | 拉丝金属亮边、五级铭牌，与石头形成亮度差 |
| 金 | 金黄 `#D9B447` | 金边、六级铭牌 |
| 钻石 | 青蓝 `#43C8CB` | 菱形核心、七级铭牌 |
| 下界合金 | 紫灰 `#65596D` | 暗紫装甲与少量紫色核心线、八级铭牌 |

模板物品同样保留统一尺寸；空白用空心/未刻录结构，生成模板用刻录纹路；通过铭牌/结构区别八阶，降低棕色和灰色阶级混淆。复制目标主要由 GUI 物品预览和 tooltip 表达，不先引入复杂动态物品模型。

候选 B 为“材质工坊”：保留土、木、石、金属的主壳体材质，但使用相同核心和端口框架，更适合田园基地；仍需确保泥土/木头、石头/铁有清晰结构区别。**A/B、阶级配色与 16/32 分辨率均未定案，本轮没有生成新贴图。**

### 9.2 主 GUI 推荐候选

建议 176×216 内容面板，底图仍可使用 256×256 图集；加高为信息留出空间，保留九资源槽、模板槽、玩家库存及其存储索引。快捷栏物品位于 y=192–207，槽框延伸至 y=208，面板底部仍有余量。候选区域分配如下，坐标/视觉需下一项预览确认：

```text
y  6     标题 + 阶级铭牌                         [IO]
y 24     模板区：槽位 + 模板阶级 + 复制目标图标/名称
y 54     [库存][库存][库存]       当前生成目标
         [库存][库存][库存]       实际间隔 / 每次 1 个
         [库存][库存][库存]       倍率 + 9槽占用状态
y112     横向进度条 + 等待模板 / 生成 / 库存已满
y123     玩家背包标题
y134     玩家背包三行
y192     快捷栏
```

实际制作时以 16×16 物品图标、18×18 槽位、字体行高和宽度测量为准。长目标名称截断并悬停显示完整名称，不压背包标题。模板区可放入真实物品、目标预览只渲染 ghost icon，不新增可取出复制目标的槽位。资源区标“资源库存”以符合原版允许手工存放的行为。

进度条使用同一个实际间隔计算函数与服务端同步值，显示“每 N tick 生成 1 个”及理论速率；低 TPS 情境提示这是理论值。倍率同时展示阶级来源，避免把 ×倍率误认成每批产量。1 tick 阶级主要显示运行状态，不依赖肉眼观察循环闪动。

状态文案依据真实可插入能力判断库存是否阻塞；不把混合库存中尚可接收当前目标的机器误报为满库存。移除模板后明确显示等待模板，不改变服务器原有 progress 循环。由同一逻辑处理图标、tooltip 和状态，减少界面猜测。

### 9.3 IO 推荐候选

改成带常驻文字的六行列表，或“六面图 + 文字列表”组合：方向（上/下/北/南/西/东）、邻接方块名、当前模式、切换按钮。输入/输出/双向/关闭同时用文字与箭头，颜色只作辅助；用中文翻译键和英文翻译键替代硬编码前后左右。

说明“输入仅吸入生成模板，输出仅资源槽”；邻接方块名与“可传输物品”状态分开，能力由服务器确认后显示。保留原模式顺序、默认 NONE 与 4 tick 调度。首次进入发送权威配置快照，修改后等待确认再更新显示或处理待确认状态；不要新增未批准的批量清空、复制配置或世界阶段按钮。

## 10. 重建源码组织与实施顺序（第二项候选）

所有新增生产源码、资源和构建入口位于 `modules/infinitecore`。建议结构：

| 目录 / 组件 | 职责 |
|---|---|
| `src/main/java/.../registry` | 明确登记旧 ID、组件、菜单和网络；不通过零散字符串重复定义 |
| `gen/GeneratorTier` 与共享计算 | 八阶稳定顺序、基础速率、默认输出；一处计算模板倍率和整数间隔，客户端和服务端共用 |
| `item` / `recipe` | 模板 tooltip、全组件目标定制；明确 remainder 与匹配规则 |
| `block` / BE | 保留 10 槽、索引、生成节奏、主动 IO 和旧保存格式；读取与正常逻辑分开 |
| `menu` / `client` | 真槽位与目标预览区分、可靠菜单同步、主 GUI 和 IO 页面；客户端代码隔离 |
| `network` | 初次/变更配置同步；与确认后的操作校验规则一致 |
| `src/main/resources` | 旧注册资源路径、原数值配方、统一模型/贴图/语言；美术候选确认后制作 |
| 模块内说明与必要验证 | 数值/字段/兼容性约定及最小有价值的验证记录，不堆积模拟实现的形式化测试 |

不得复制旧模块的历史授权或运行目录；借用现有工程构建配置时逐项核对本模块依赖，保留适当许可/来源说明。基准 JAR 一直保留作对照。重建版本号需在第二项确认，不能覆盖基准 1.0.0 JAR 或让两个同 mod ID 的 JAR 同时部署。

建议小步顺序，每次开始前说明操作和验收范围，获得对应批准：

1. **最小可验证版本**：创建构建入口和可维护结构，打通泥土机器、空白/生成模板、任意目标定制、10 槽、GUI、主动 IO、保存读取。为保留 ID 方向做全八阶注册清单，但其它阶级没有完整验收前，产物只用于新隔离世界，不部署旧存档。编译、启动和测试世界必须纳入第二项明确计划。
2. **扩展八阶**：共享逻辑驱动全部阶级，逐项复核本报告配方表、基础速率和跨阶倍率；统一外观与 GUI，并保留全部字段/缺省。不要在扩展时重新设计成本或生成曲线。
3. **迁移与自动化验收**：另行指定原版测试世界副本、备份、JAR 切换和日志位置；实测旧数据、退出重载、Create/漏斗/管道与必要服务器行为。对外能力和掉落规则等新增/修复需先确认。

最小版不能因为只支持泥土，就移除另外七阶的旧注册或被当成可安全替换旧世界的正式版。通用阶级架构可以提前建立，但“已注册”不能代替八阶功能验收。

## 11. 行为保留与待确认修复

| 分类 | 处理原则 |
|---|---|
| 必须保留的已核实功能 | 全注册 ID、完整目标组件、任意非生成模板目标规则、生成无消耗、25 配方及数值、每次 1 个、跨阶公式、10 槽、混合可手工库存、主动 IO、旧保存结构 |
| 本次要求的视觉/可读性方向 | 统一八阶外观、清晰模板/目标/库存区、正确实际间隔显示、长文本处理、IO 常驻方向/模式文字；具体美术候选待确认 |
| 建议确认的同步修复 | 打开菜单/IO 页时提供权威 IO 快照；防连续点击旧状态；进度保存时机与卸载边界 |
| 建议确认的操作边界修复 | 菜单距离和服务器 IO 包的对应菜单/距离校验；Shift 模板路由异常核对后修复 |
| 需要明确选择的功能补全 | 本机对外 ItemHandler 能力及每面过滤；拆除机器与库存的掉落规则；不能以“恢复原功能”为由自行新增 |
| 保持待定、此轮不加入 | 世界阶段/资源黑白名单、Boss 限制、机器能量/红石暂停、离线补产、配方/产量/成长曲线调整、自动部署/旧存档迁移 |

原实现的缺陷不能冒充应保留的设计意图，也不能在未确认时悄悄改变。第二项开始前需确认美术方向和上述修复边界；有待实测问题先复现，再决定修复方式。

## 12. 后续实际验收清单（本轮未执行）

采用少量有代表性的实际操作和视觉检查，避免用大量形式化测试代替游戏验收：

| 验收主题 | 最小实际检查 |
|---|---|
| 配方 | 八阶各核对空白、生成模板、机器；石阶 smooth_stone/stone 区别、下界合金空中心；定制在 2×2/3×3 可用、两个生成模板不可互作目标 |
| 目标完整性与消耗 | 普通资源、带名称/附魔/耐久的装备、带内容容器、一个模组目标、空白模板/机器目标；检查输出完整组件、制作消耗/remainder、生成模板不减 |
| 生成数值 | 八阶同阶间隔及每次 1 个；选高/低跨阶与舍入案例；无模板、换模板、目标缺失、满库存恢复、堆叠模板不增产 |
| 库存与移动 | 九资源槽容量、不可堆叠目标、混合库存、普通拖拽、Shift 模板/资源、模板已有内容时路由；确认无复制/丢物 |
| IO | 六方向四模式、默认关闭、4 tick、主动输出/模板吸入、BOTH 顺序、相邻能力面限制、满邻居回插、无能力邻居；再单测外部抽取与 Create |
| 重载与同步 | 关闭/重开菜单、IO 页切换、退出重进、区块卸载重载、服务器重启（若批准）、第二玩家加入；比较库存、目标、IO、progress |
| 拆除 | 正确镐、其它工具、创造拆除及确认的爆炸场景，检查机器/库存/模板掉落；只在测试世界 |
| 视觉 | 八阶并排近/远观看、手持图标、GUI 常用缩放及窄屏、长中文/英文物品名、各状态/IO 模式、1 tick 机器显示 |
| 旧数据 | 原版生成世界副本切换新 JAR；全部注册、组件、BE/槽位/IO 格式读取和再次保存；查缺失注册/异常日志 |

如批准编译，最低验证为模块 Wrapper 构建、产物资源/注册/数值检查；实际游戏验收须单独包含启动/部署/测试世界权限。有限的公式和序列化检查可补充关键边界，但不替代上述运行验证。

## 13. 证据定位与本轮验证

以下方法偏移来自此 SHA-256 基准 JAR 的 javap 输出，用于复核关键结论，不是源码行号：

| 结论 | 精确定位 |
|---|---|
| 八阶速率 | `GeneratorLevel.static {}`：DIRT offset 9=360；WOOD 39=180；STONE 69=72；COPPER 98=36；IRON 127=24；GOLD 156=12；DIAMOND 186=6；NETHERITE 216=1 |
| 倍率公式 | `GeneratorLevel.multiplier` offset 0–22：0.1 / 2.0 / (template−machine)×0.2 |
| 实际间隔与 IO 频率 | `GeneratorBlockEntity.tickServer` offset 56–83：除倍率、Math.round、下限 1；offset 123：IO 阈值 4 |
| 完整目标复制/数量 1 | `TemplateCustomizeRecipe.assemble` offset 83–127；`GeneratorBlockEntity.tryGenerate` offset 18–74 |
| 九资源槽 + 模板槽 | BE 构造 offset 21=10；tryGenerate offset 4=9 / offset 79=9；`GeneratorMenu` 槽位初始化 |
| 进度分母问题 | `GeneratorScreen.renderBg` offset 174–184：progress / GeneratorLevel.rateTicks，而非实际间隔 |
| 保存格式 | `GeneratorBlockEntity.saveAdditional/loadAdditional`；`ModDataComponents.lambda$static$0`；对应 NeoForge Handler serializeNBT/deserializeNBT |
| IO 首次同步不足 | BE 未覆盖 getUpdateTag/getUpdatePacket；`GeneratorBlock`、两 Screen、`ModNetwork` 和 Update/Sync 包的全部调用路径 |
| 掉落缺失 | ZIP 没有 loot_table；GeneratorBlock 没有拆除/掉落 override；对应 Minecraft 默认 BlockBehaviour.onRemove/getDrops |

本轮完成：仓库规则阅读、全部 JSON 解析、全部 PNG 解码和视觉查看、JAR CRC/哈希与 class 版本检查、24 类静态检查、字段/配方/数值/注册与视觉方案审查。只新增本报告，提交前执行差异与空白检查；基准 JAR 内容保持原哈希。

**没有执行**：生产源码重建、编译、测试套件、游戏启动、工具安装、实例修改、部署、存档读写/迁移或 GitHub 推送。当前结论不代表可重建产物已存在、游戏测试通过或旧存档已兼容。
