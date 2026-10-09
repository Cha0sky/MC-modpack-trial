# PHASE 1 交付报告 — 噬星之刃 / Evolving Blade

> **本文件已被 `result-phase1-rework2.md` 取代。** 首轮提交经 Codex 独立审查后指出 6 项缺陷，
> 已在返工第 1 轮修正；返工第 2 轮又修正了原版飞行末影龙击杀丢失、最终取消契约、立即 KILLED
> 移除、按实体生命周期的永久去重等边界问题。本文件保留为历史记录，其中的实现细节
> **不再代表当前源码**；请以 `result-phase1-rework2.md` 为准。

**结论（首轮）：PHASE 1 完成。** 项目可编译、可打包、JUnit 测试套件全绿（48/48），
产物为 `build/libs/evolvingblade-1.0.0.jar`。

---

## 1. 实际路径

| 项目 | 路径 |
|---|---|
| 项目根目录 | `D:\PCL 正式版 2.10.6 (1)\codex\evolvingblade` |
| 模板输入（只读） | `D:\PCL 正式版 2.10.6 (1)\codex\_agent-team\inputs\mdk\MDK-1.21.1-ModDevGradle-main` |
| 规格（只读） | `D:\PCL 正式版 2.10.6 (1)\codex\_agent-team\SPEC.md` |
| 主源码 | `src/main/java/com/evolvingblade/`（21 个类） |
| 测试源码 | `src/test/java/com/evolvingblade/`（7 个测试类 + 1 个运行器） |
| 资源 | `src/main/resources/{assets,data}/evolvingblade/` |
| 产物 | `build/libs/evolvingblade-1.0.0.jar`（78,654 字节） |
| 测试报告 | `build/test-results/ebTest/ebTest-summary.txt`、`ebTest-results.xml` |
| 构建/测试日志 | `docs/logs/*.log` |
| API 取证 | `docs/API-NOTES-phase1.md` |

反编译参考源（Minecraft + NeoForge 合并源码）来自本机 Gradle 缓存：
`.gradle-user-home/caches/ng_execute/5d0290f4.../output/`（取证时用目录联接 `.reference/mcsrc` 访问，
交付前已删除该联接）。

产物指纹（用于比对部署物是否一致）：

- 内容哈希（按条目名+字节排序后 SHA-256）：`B1FB1ED83DB804DE4B3DC0379B9F582ACD34C1657076CEAD390FF14F47ED0709`
- 文件 SHA-256（含时间戳，不可复现，仅供参考）：见 `Get-FileHash build/libs/evolvingblade-1.0.0.jar`

## 2. 环境与版本（实际生效）

| 项 | 值 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | **21.1.248**（`gradle.properties: neo_version`） |
| Java | 21.0.7（`JAVA_HOME` = 监督者设置的 `java-runtime-delta`，是含 `javac` 的完整 JDK） |
| Gradle | 8.14.2（wrapper；本机缓存自带，未联网下载） |
| ModDevGradle | **2.0.81**（见 §6.1 说明） |
| mod_id / version | `evolvingblade` / `1.0.0` |
| 包名 / 组 | `com.evolvingblade` |
| JUnit | 5.9.2（本机缓存版本） |

## 3. 已实现的行为（逐条对应任务清单）

### 3.1 主注册
- `EvolvingBlade`（`@Mod("evolvingblade")`）：注册物品、创造页签、玩家数据 attachment、
  服务器配置，并显式注册配置热重载监听。
- `EvolvingBladeClient`（`@Mod(dist = CLIENT)`）：仅客户端，挂载 NeoForge 自动配置界面；
  专服不会加载任何客户端类。
- 注册项：`evolvingblade:star_eater`、创造页签 `evolvingblade:main`、
  attachment `evolvingblade:growth`、标签 `evolvingblade:bosses` / `evolvingblade:hostile`。

### 3.2 基础武器
- `StarEaterItem extends SwordItem`（下界合金 Tier 的工具规则），
  **不可破坏**（`unbreakable` 组件 + 不设最大耐久）且**抗火**（`fire_resistant`），史诗稀有度。
- 属性按配置默认值的**阶段 0** 烘焙：总攻击 7、总攻速 1.6。
  `stageDamageModifier = 总攻击 − 玩家基础 1.0 = 6.0`，**不会多加一次 +1**（有单测覆盖）。
- 鼠标悬停显示两条固定说明（不可破坏 / 抗火，双语语言键）。
- 物品上**不存任何权威成长数据**；复制/新造的剑自行跟随持有者阶段（PHASE 2 落地动态属性）。

### 3.3 服务器配置（`EBConfig`，`ModConfig.Type.SERVER`）
包含**全部**门槛、数值与旋钮：
- `[growth]`：`hostile_point_weight`、`boss_point_weight`、三模式 `*_points` / `*_bosses`（各 8 项）；
- `[stages]`：`attack_damage`、`attack_speed`（各 8 项）；
- `[abilities]`：回血、剑气（6 项）、吸血（6 项）、爆发（7 项）、斩 Boss（6 项）——共 27 个旋钮；
- `[boss]`：`auto_detect`、`auto_detect_health_threshold`、`include`、`exclude`；
- `[hostile]`：`include`、`exclude`。

**校验**：长度必须为 8、非负、单调不减、有限、在范围内；非法项回落到默认值并**有界**记录
（最多 32 条）。`EBConfig.settings()` 永远返回自洽的纯 Java `GrowthSettings` 快照，
畸形配置不会让 tick 崩溃，也不会产生 NaN/无穷伤害。

### 3.4 成长模式与阶段计算（纯 Java，可单测）
- `GrowthMode`：quick / standard / long 及 SPEC 的默认门槛数组，含严格/宽松 ID 解析与 Codec。
- `GrowthThresholds`：双门槛 + `validate()`（长度/非负/单调）+ `sanitized()` 回落。
- `StageCalculator`：`stageFor(points, bossKills, thresholds)` 取**同时满足双门槛**的最大阶段；
  饱和加法/乘法；`computePoints` 按权重重算；负输入防御性归零、显式边界抛异常。
- 未选择模式时 `GrowthData.stage()` 恒为 0（有单测）。

### 3.5 玩家持久化与赠剑状态
- `GrowthData`（不可变 record）：`hostileKills` / `bossKills` / `growthPoints` / `mode?(可空)` /
  `giftState`；Codec 拒绝负数、未知模式、未知赠剑状态、错误类型。
- `GiftState`：`NOT_GRANTED` / `PENDING` / `GRANTED`。
- `EBAttachments.GROWTH`：`AttachmentType.builder(...).serialize(CODEC).copyOnDeath().build()`。
- **末地返回不会复制/累加两次**：死亡重生与末地返回是互斥的两条 `copyAttachmentsFrom` 路径，
  写入方式是 `put`（覆盖）且目标为全新实体实例（源码证据见 `docs/API-NOTES-phase1.md` §4）；
  `EBPlayerLifecycleHandler.onClone` 只做校验与日志，**绝不**在 Clone 里加计数。
- `GrowthService.requestGift/tryDeliverGift`：只有成功放进背包才写 `GRANTED`；
  背包满时保持 `PENDING` 并周期性重试，**不掉落、不复制**（GUI 触发在 PHASE 2）。

### 3.6 服务器成长/击杀入账服务
- `GrowthService` 是**唯一**写入玩家成长数据的入口，全部在服务器主线程执行。
- `EBKillCreditHandler`：`LivingDeathEvent` 入账；拒绝取消的死亡、PvP 受害者、
  `FakePlayer`、无归属环境死亡、重复通知（首轮为 `KillCreditLedger` 20 tick 去重窗口；
  **返工第 1 轮已改为 `KillCreditState` 的"按实体一条命"标记 + 两阶段提交**，见 `result-phase1-rework1.md`）。
- 归属链：直接来源 → 弹射物主人 → `OwnableEntity` 主人（最多 4 层），
  再回退到原版 `victim.getKillCredit()`；只有唯一的真实玩家拿到成长。
- 末影龙多部位伤害转发给父实体，因此只为父实体入账一次。
- 计数一律饱和加法（`Long.MAX_VALUE` 封顶，永不回绕）。

### 3.7 Boss 分类器
- 纯函数内核 `BossClassifierCore` + MC 胶水 `BossClassifier` + 反射 `BossEventProbe`。
- 优先级：排除名单 > 包含名单（默认末影龙、凋灵）> `evolvingblade:bosses` / `c:bosses` 标签 >
  自动探测兜底。
- 自动探测 = 最大生命 ≥ 阈值（默认 200）**且**（非空 `ServerBossEvent` 字段（含继承链）
  **或**继承链类名含 `Boss`）。**绝不只看生命值**：典狱长（500 血）默认非 Boss。
- 只缓存反射访问器（`ConcurrentHashMap<(类,字段类型), List<Field>>`），不缓存实体的可变状态；
  访问失败安全跳过并有界记录（上限 16 条）。
- **对第三方 Boss 类零硬引用**。
- `bosscheck` 回显真实实体 ID、结论、获胜理由与是否普通敌对。

### 3.8 管理员命令（`/evolvingblade`）
`info [player]` / `mode <player> <mode>` / `set <player> <hostile> <boss>` / `bosscheck <entity>`。
权限 2（查询自己除外）；`set` 用 `longArg(0)` 拒绝负数；按当前权重重算成长点；
`mode`/`set` 均保留计数与赠剑标记；`bosscheck` 只接受单个生物实体选择器。
所有回显走语言键，中英双语齐全。

### 3.9 配方与资源
- 无序配方 `data/evolvingblade/recipe/star_eater.json`（单数 `recipe` 目录，1.21.1 格式已核对）：
  铁剑 ×1 + 铁锭 ×2 + 紫水晶碎片 ×1 + 红石 ×1 → 噬星之刃 ×1。
- `data/evolvingblade/tags/entity_type/{bosses,hostile}.json`（bosses 默认含末影龙与凋灵）。
- `assets/evolvingblade/`：物品模型（`item/handheld` + `layer0`）、
  `lang/zh_cn.json` 与 `lang/en_us.json`（键完全对称：物品/页签/tooltip/命令/配置界面全量）。
- **占位贴图**：`textures/item/star_eater.png` 是 `tools/PlaceholderTexture.java` 程序化生成的
  32×32 像素图（保证有图可显示，不是紫黑格）。**这不是 SPEC 要求的 8 张正式美术**，
  PHASE 2 会替换。

## 4. 明确**未**实现（PHASE 2）

- 首次登录 3 选 1 选择 GUI、二次确认、`G` 键打开成长仪表盘、键位与全部网络包。
- 选择模式下发的服务器侧触发（`requestGift` 已就绪，等待 GUI 调用）。
- 8 张正式 32×32 贴图与美术资源。
- 全部战斗能力：阶段化动态属性、剑气（右键）、吸血（满蓄力主手近战）、
  爆发（Shift+右键）、斩 Boss 额外伤害、共享冷却与跨死亡/重连持久化。
- 客户端同步快照（S2C 权威阶段/配置派生门槛与数值）。
- GameTest 与真机验证；PHASE 1 **没有**任何假装已实现的上述能力代码。

## 5. 运行过的命令与真实结果

全部命令在项目根目录执行（`JAVA_HOME` / `GRADLE_USER_HOME` 使用监督者设置的值，未改动）：

| # | 命令 | 结果 |
|---|---|---|
| 1 | `.\gradlew.bat compileJava --console=plain` | 首次失败：`BossRules.java:31` 类型不匹配（`List.of` 传给 `Set<String>`）→ 修复后 **BUILD SUCCESSFUL** |
| 2 | 同上（修复后） | **BUILD SUCCESSFUL**，0 error / 0 warning |
| 3 | `.\gradlew.bat test --console=plain` | **FAILED**：`ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`（详见 §6.2，与代码无关） |
| 4 | `.\gradlew.bat test --console=plain --info` | 同上，并取得 worker 命令行（`java ... @<argfile>`）用于定位根因 |
| 5 | `.\gradlew.bat ebTest --console=plain` | 第一次 47 用例中 2 个失败（**测试期望写错**，已修正）→ 之后 **48/48 通过** |
| 6 | `.\gradlew.bat clean build --console=plain` | **BUILD SUCCESSFUL in 14s**，含 `ebTest` 48/48 通过，产物 `build/libs/evolvingblade-1.0.0.jar` |
| 7 | 收尾润色（物品 tooltip、移除死代码/无用注解）后再次 `.\gradlew.bat clean build --console=plain` | **BUILD SUCCESSFUL in 13s**，`ebTest` 仍为 48/48，0 error / 0 warning |

最终 `build` 的关键输出（见 `docs/logs/build-phase1-build.log`、`docs/logs/build-final.log`）：

```
> Task :test SKIPPED            (非 ASCII 路径下按设计禁用)
> Task :ebTest
[        48 tests successful      ]
[         0 tests failed          ]
EBTEST tests=48 succeeded=48 failed=0 skipped=0 containers=8
> Task :check
> Task :build
BUILD SUCCESSFUL in 14s
```

对产物的核验（非命令输出，为实际检查）：

- `jar tf` 内容：`META-INF/neoforge.mods.toml`、`com/evolvingblade/**`（21 个类，
  **不含测试类**）、`assets/evolvingblade/{lang,models,textures}`、
  `data/evolvingblade/{recipe,tags/entity_type}` —— 与预期完全一致。
- 生成的 `neoforge.mods.toml`：`modId="evolvingblade"`、`version="1.0.0"`、`license="MIT"`、
  `versionRange="[21.1.248,)"`、`minecraft` 范围 `[1.21.1]`，中文描述按 UTF-8 正确生成。

## 6. 构建环境问题（已解决 / 已定位，不属于本模组代码缺陷）

### 6.1 ModDevGradle 版本与 Gradle 版本
官方模板 `MDK-1.21.1-ModDevGradle-main` 使用 ModDevGradle **2.0.148** + Gradle **9.2.1**。
本机 Gradle 缓存（由监督者预置）只包含 ModDevGradle **2.0.81** + Gradle **8.14.2** 发行版，
且 curl/.NET 的 TLS 在本机不可用（只有 JVM 的 TLS 可用，网络下载存在不确定性）。
为**可复现、可离线**构建，已把 `build.gradle` 的插件版本固定为 `2.0.81`、
`gradle-wrapper.properties` 的 `distributionUrl` 指向 `gradle-8.14.2-bin.zip`。
NeoForge 仍严格为 **21.1.248**、Minecraft **1.21.1**。
> 如果希望严格复刻模板的 2.0.148 + Gradle 9.2.1，请在有网络的环境把这两处改回即可
> （本机实测 2.0.81 + 8.14.2 已能正确产出 `neoforge-21.1.248` 的开发工件）。

### 6.2 `gradlew test` 在本机必然失败（Gradle/JVM 缺陷，非本项目代码问题）

**现象**：

```
ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain
Process 'Gradle Test Executor N' finished with non-zero exit value 1
```

**根因（已用对照实验定位）**：

1. Gradle 对每个 worker 进程（含 Test Executor）都会把 classpath 写进一个临时
   `@argfile`；反汇编确认该逻辑位于 `gradle-core-8.14.2.jar` 的
   `ApplicationClassesInSystemClassLoaderWorkerImplementationFactory`（worker JVM ≥ Java 9 时一律使用）。
2. Gradle 以 **UTF-8** 写这个文件。
3. Windows 上的 JVM 用**平台 ANSI 代码页（本机 GBK）**解析 `-cp` 值。
4. 本项目路径含非 ASCII 字符（`D:\PCL 正式版 2.10.6 (1)\...`），classpath 被解码坏。

对照实验（同一份 argfile 内容，只改编码）：

| argfile 编码 | `-cp "<含中文的路径>"` 的结果 |
|---|---|
| UTF-8（Gradle 的做法） | `ClassNotFoundException`；`-XshowSettings` 显示 `java.class.path` 里的中文已损坏 |
| GBK | 正常加载类 |

补充实验：`-cp` 内联在命令行时（不经 argfile）UTF-8/中文路径**完全正常**；整个 Gradle 发行版中
只有上述 worker 工厂引用 `ArgWriter`，**`JavaExec` 不使用 argfile**。

**处理方式**：新增 `ebTest` 任务（`JavaExec` + `com.evolvingblade.test.EBTestRunner`），
用内联 classpath 跑同一套 JUnit 5 用例；`build.gradle` 检测项目路径：
非 ASCII 时禁用 `test`、让 `check`/`build` 依赖 `ebTest`（并打印中文提示）；
纯 ASCII 路径时 `test` 与 `ebTest` 都会正常。

> 若 Codex 在纯 ASCII 路径下检出，可直接用标准 `gradlew test` 得到同样结果。
> 无论哪种情况，`gradlew clean build` 都会完整跑完 48 个用例。

## 7. 测试覆盖（48 个用例，全部通过）

| 测试类 | 用例数 | 覆盖内容 |
|---|---|---|
| `growth.StageCalculatorTest` | 9 | SPEC 默认门槛逐项核对；**全部 24 个阶段边界**；双门槛联合判定（含门槛为 0 不构成约束）；恰好低于门槛；溢出饱和；畸形输入；下一阶段差值；门槛对象校验与回落 |
| `growth.GrowthSettingsTest` | 7 | 默认快照与 SPEC 一致；**阶段总攻击减 1.0（不多加一次）**；阶段数组净化（长度/NaN/∞/0）；标量净化（权重/整型/浮点越界与 NaN）；能力旋钮按阶段切换；默认 Boss 规则；非法阈值上报 |
| `growth.GrowthDataTest` | 7 | JSON round-trip（空/完整）；缺省字段；**畸形输入拒绝**（负数/未知模式/未知赠剑状态/错误类型）；未选模式阶段恒 0；击杀入账与饱和；管理员重算保留模式与赠剑标记；构造器钳制 |
| `growth.GrowthModeTest` | 4 | ID 解析（大小写/空白/未知）；`require` 抛异常；模式 Codec；赠剑状态 Codec |
| `boss.BossClassifierCoreTest` | 10 | 决策表：非生物、**排除优先级最高**、包含、标签、自动探测关闭、**生命值是硬门槛**（阈值含等号）、Boss 事件字段优先、类名兜底、**典狱长不是 Boss**、描述文本 |
| `boss.BossEventProbeTest` | 6 | 非空字段判定（直接/继承/空）、类型过滤、深度限制、**只缓存访问器**、类名兜底（大小写不敏感）、有界日志 |
| `util.KillCreditLedgerTest`（**已删除**，被 `growth.KillCreditStateTest` 取代） | 5 | 窗口内去重、不同实体互不影响、null 拒绝、prune 清理、有界日志上限 |

测试报告：`build/test-results/ebTest/ebTest-summary.txt`（含每个用例的 PASS/FAIL 与唯一 ID）、
`ebTest-results.xml`。日志：`docs/logs/`。

## 8. 未验证 / 残余风险（诚实清单）

1. **未做真机/服务器运行验证**。dev server 需要先接受 Mojang EULA，本次没有代为接受，
   因此注册流程、配置生成、击杀入账、Boss 判定与命令的**运行时**行为尚未实测。
   —— 代码层面全部按反编译源码核对，静态检查（注册表齐、事件订阅齐、无客户端类混入专服路径）
   已通过，但这**不等于**运行时验证通过。
2. **GameTest 未编写/未运行**（SPEC 把 GameTest 列为验收内容；PHASE 1 无对应玩法）。
3. `ServerBossEvent` 反射探测只用测试自带标记类型验证了机制，**未对真实凋灵实体**实测。
4. 物品模型/贴图/创造页签的观感**只能在客户端真机确认**。
5. 配置文件的"用户实例迁移"问题：本模组尚无历史版本，暂不适用；但 README 已写明
   NeoForge 不会覆盖既有配置键这一事实。
6. 抗火/不可破坏组件、配方能否成功合成，均需真机确认。

## 9. PHASE 2 待办（按 SPEC 顺序）

1. 网络包（C2S 模式选择意图、S2C 权威快照/配置值）与服务器侧校验（拒绝畸形/重复提交）。
2. 首次登录自动打开 3 卡选择 GUI + 二次确认（明确"锁定"警告）；`G` 键成长仪表盘
   （8 阶段路线、当前阶段/模式、累计成长点、两类击杀、**双门槛**、能力解锁与冷却）。
   要求 320×240 / 854×480（GUI scale 2）/ 1920×1080 下中英文均可用且无重叠。
3. 阶段化动态属性：按玩家当前阶段施加本模组唯一的主手修饰符，卸下立即移除，
   交换武器不转移强度；阶段快照数据组件仅用于图标/tooltip，永不参与战斗结算。
4. 战斗能力：击杀回血、剑气（射线/弹射物 + 客户端视觉音效 + 视线阻挡）、
   满蓄力主手吸血（按受害者受击前生命截断、上限）、Shift+右键爆发
   （排除玩家/友方/宠物、最多 32 目标、不破坏地形）、斩 Boss 额外伤害
   （自定义伤害类型只绕过护甲与无敌帧、每秒限一次、不覆盖阶段逻辑、防递归）。
5. 共享冷却（跨剑副本、跨重连与死亡、服务器 tick 计时）。
6. 8 张正式 32×32 贴图与美术，替换当前占位贴图。
7. 客户端同步与专服隔离复核；GameTest（真实死亡入账、凋灵/末影龙、多部位/重复/取消、
   宠物主人、存读档与克隆、赠剑一次性/背包满、交换与装备效果、冷却/实际治疗/递归/
   视线/友方排除/上限）；在 854×480 与 1920×1080 下的 GUI 真机验证。
8. README/CHANGELOG 增补 PHASE 2 内容与兼容性说明。

## 10. 未修改的边界

- 未修改任何兄弟项目、Minecraft 实例、存档、启动器设置、凭据、共享 Harness 文件或系统设置。
- 未改 `JAVA_HOME` / `GRADLE_USER_HOME`（仅使用监督者提供的值）。
- 未使用 mixin；未删除用户机器上任何无关文件。
- 只读模板与 SPEC 仅被读取；模板许可证已保留（`TEMPLATE_LICENSE.txt`）。
