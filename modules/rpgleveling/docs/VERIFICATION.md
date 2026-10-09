# VERIFICATION.md — RPG Leveling 独立验证方案

> 项目：Minecraft **1.21.1** + **NeoForge 21.1.x**（modId `rpgleveling`，包名 `com.rpgleveling`）
> 角色：verify（独立验证，不参与实现）
> 目标：**独立判定每个加点维度是否真的生效**，而不是只看"编译通过"。
> 本文档的每一条判定标准都必须是：可由第三方复现、有客观观测值、能区分"实现正确 / 实现错误 / 没实现"。

---

## 0. 核心原则

| 原则 | 含义 |
|---|---|
| **不信自报** | 不采信 mod 自己 GUI/HUD 显示的数值作为唯一证据。每个维度必须有**至少一条绕开 mod 自身的独立观测通道**（原版 `/attribute`、`/data`、`/xp query`、伤害差、掉落计数、位置差）。 |
| **可判别** | 判定标准要能区分"正确"与"看起来对"。必须给出**精确期望值**（含公式与容差），不是"数值应该变大"。 |
| **双向** | 每个维度都要做 **加点点 → 观测** 与 **洗点 → 观测** 两个方向。只增不减、洗点留残留，都判 FAIL。 |
| **幂等** | 重登、跨维度传送、死亡重生、服务器重启后数值必须**完全不变**。这是本类 mod 最高频的缺陷。 |
| **权威** | 所有可影响战斗/经济的数值以**服务端**为准。客户端只能显示。 |
| **可自动化优先** | 能写成 GameTest / 命令脚本的一律自动化；只有真正需要人眼/手感判断的才进人工清单（§9）。 |

**证据留档要求**：每条判定都要保留原始输出（命令回显截图 / 日志行 / GameTest XML 报告），落在 `_verify/evidence/<阶段>/<用例ID>.*`。

---

## 1. 工具链基线（阶段一已实测，非推测）

### 1.1 已确认结论

| 项 | 值 | 证据 |
|---|---|---|
| JDK | **Temurin 21.0.12**（完整 JDK，含 jmods）`C:\Gradle\jdk-21-temurin` | `javac -version` / `gradlew --version` 输出 `Launcher JVM: 21.0.12` |
| 备选 JDK | `%APPDATA%\.minecraft\runtime\java-runtime-delta` = 21.0.7 | `javac -version` |
| 系统 PATH | **无 java、无 gradle** | 必须显式设置 `JAVA_HOME`，否则 `gradlew` 直接报 `ERROR: JAVA_HOME is not set` |
| Gradle | **8.14.2**（wrapper 指定，已播种离线可用） | `gradlew --version` |
| 构建插件 | **ModDevGradle `net.neoforged.moddev` 2.0.81** | §1.5 实测 |
| Gradle 发行版来源 | `C:\Gradle\gradle-8.14.2`（本机已有完整发行版）+ `C:\Gradle\gradle-8.10.2` | 目录存在 |
| 本地 Maven 缓存 | `C:\Users\俞皓天\.gradle\caches\modules-2` | 含 `net.neoforged:neoforge:21.1.248`、`net.neoforged:moddev-gradle:2.0.81`、完整 NFRT 中间产物 |
| NeoForge | **21.1.248** | 缓存 + maven 200 |
| 磁盘 | D 盘剩余 280GB | — |

### 1.2 网络实测（**重要纠正**）

> 侦察阶段记录的"NeoForged maven SSL 失败"是 **误报**：那是 PowerShell `Invoke-WebRequest` 的 TLS/代理问题，不是网络不通。
> 用 `curl.exe` 实测（curl 8.21.0 / Schannel）：

| 端点 | 结果 |
|---|---|
| `maven.neoforged.net/releases/.../neoforge-21.1.248.pom` | **HTTP 200** (0.73s) |
| `.../net.neoforged.gradle.userdev.gradle.plugin/7.0.170/...pom` | **HTTP 200** |
| `.../net.neoforged.moddev.gradle.plugin/2.0.81/...pom` | **HTTP 200** |
| `repo1.maven.org/maven2/` | **HTTP 200** |
| `plugins.gradle.org/m2/` | **HTTP 200** |
| `maven.parchmentmc.org/` | **HTTP 302**（正常） |
| `services.gradle.org/distributions/gradle-8.14.2-bin.zip` | HTTP 307 → 跳转 **GitHub release-assets CDN**，实测吞吐极低（≈265 B/s），130MB 包**实际拉不动** |

**结论：不需要离线/镜像绕行方案来获取 Maven 依赖（Gradle 用 OkHttp，不经过 PowerShell 的 TLS 栈）。唯一的网络坑是 Gradle 发行版本身走 GitHub CDN 极慢——已用本机 `C:\Gradle\gradle-8.14.2` 播种 wrapper 缓存解决（见 §1.4）。**

### 1.3 标准构建命令（所有角色统一）

```powershell
$env:JAVA_HOME = "C:\Gradle\jdk-21-temurin"
$env:PATH      = "$env:JAVA_HOME\bin;$env:PATH"
Set-Location "<工程根>"
.\gradlew.bat build --console=plain --stacktrace
```

> **纪律：同一时刻只允许一个 Gradle 构建进程**。多角色共享 `~/.gradle`，并发会争抢 `modules-2` / `neoformruntime` 的文件锁。构建前先确认无残留 java 进程。

### 1.4 已排掉的坑（复现用）

| # | 坑 | 现象 | 解法 |
|---|---|---|---|
| P1 | `JAVA_HOME` 未设置 | `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH` | 每次调用前显式设置（见 §1.3） |
| P2 | `~/.gradle/wrapper/dists` 为空 | `gradlew` 卡在下载 `gradle-8.14.2-bin.zip`，`.part` 文件长期 0 字节，无任何输出 | 见下方"播种步骤" |
| P3 | PowerShell 里用 `Tee-Object` 记录日志到不存在的目录 | 整个管线**在 gradle 启动前**就失败，却返回 `$LASTEXITCODE=0`，**假成功** | 先 `New-Item -ItemType Directory -Force` 建目录；判定构建结果要用日志内容而不是退出码 |
| P4 | `Invoke-WebRequest` 访问 maven.neoforged.net 报 SSL | 误判为网络不通 | 改用 `curl.exe`；Gradle 本身不受影响 |
| P5 | 被中断的构建残留 java 进程持有 `.lck` | 后续构建报锁冲突 | `taskkill /PID <id> /F` 后再继续 |
| P6 | **NeoGradle 7.0.170 的 `neoFormRecompile` worker 起不来** | `ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain` → `Failed to run Gradle Worker Daemon` → `BUILD FAILED`。Gradle 8.14.2 与 8.10.2 同样失败 | **改用 ModDevGradle 2.0.81**（§1.5） |

**播种 Gradle wrapper 缓存（离线可用，已执行成功）**

```powershell
$dist = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.14.2-bin\2pb3mgt1p815evrl3weanttgr"
Remove-Item "$dist\gradle-8.14.2-bin.zip.part","$dist\gradle-8.14.2-bin.zip.lck" -Force -ErrorAction SilentlyContinue
Copy-Item "C:\Gradle\gradle-8.14.2" "$dist\gradle-8.14.2" -Recurse -Force
New-Item -ItemType File -Force -Path "$dist\gradle-8.14.2-bin.zip.ok" | Out-Null
```

> 哈希目录名 `2pb3mgt1p815evrl3weanttgr` 由 `distributionUrl` 决定；只要不改 wrapper properties 就稳定。`gradlew --version` 能打印 `Gradle 8.14.2` 即表示播种成功。

### 1.5 插件组合（**已实测定案**）

**结论：使用 ModDevGradle。NeoGradle 在本机不可用。**

| 项 | 最终值 |
|---|---|
| 构建插件 | **`net.neoforged.moddev` 2.0.81（ModDevGradle）** |
| NeoForge | **21.1.248** |
| Gradle | **8.14.2**（wrapper，已播种离线可用） |
| JDK | **Temurin 21.0.12** @ `C:\Gradle\jdk-21-temurin` |

**实测证据**：`gradlew build` → `BUILD SUCCESSFUL in 3m` → 产出 `build/libs/canary-1.0.0.jar`（6,078 字节），`check_jar.ps1` 13/13 PASS。详见 `_verify/CANARY_RESULT.md`。

**被否决的候选**：
- ❌ **`net.neoforged.gradle.userdev` 7.0.170（NeoGradle）** —— 参考工程 InfiniteCore-Mod 确实用它产出过 jar，但**当前环境无法复现**：`:neoFormRecompile` 失败于
  `java.lang.ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`（Gradle Worker Daemon 起不来）。
  **Gradle 8.14.2 与 8.10.2 均同样失败**，且 Gradle 发行版已证明逐字节完好 → 属 NeoGradle 自身实现路径问题，与 Gradle 版本无关。
- ✅ **根因差异**：ModDevGradle 把 NeoForm 全流程交给 **NFRT 外部 Java 进程**（日志 `Running external tool installertools / mergetool / AutoRenamingTool / vineflower / jst-cli-bundle / DiffPatch`），不走 Gradle Worker API → 天然规避该故障。

> **硬性要求：code 角色必须与本 §1.5 的最终组合完全一致**（插件 id + 版本 + NeoForge 版本 + Gradle 版本 + JDK）。
> 逐字照抄的 `build.gradle` / `settings.gradle` / `gradle.properties` 模板见 `_verify/CANARY_RESULT.md` §5.3–5.5。

---

## 1.6 原版机制取证（**已从反编译源码逐行核对，不是推测**）

> 证据来源：`_verify/canary-mdg/build/moddev/artifacts/neoforge-21.1.248-sources.jar`
> 抽取件留存于 `_verify/evidence/mcsrc/`。下列结论可直接作为判定基准。

### 1.6.1 `AttributeInstance.calculateValue()` 的结算顺序（L142-160）

```java
double d0 = this.getBaseValue();
for (ADD_VALUE)            d0 += amount;         // ① base + Σ ADD_VALUE（含装备、含我们的 flat）
double d1 = d0;
for (ADD_MULTIPLIED_BASE)  d1 += d0 * amount;    // ② d0 * (1 + Σ AMB)  ← percent 线在这里
for (ADD_MULTIPLIED_TOTAL) d1 *= 1.0 + amount;   // ③
return this.attribute.value().sanitizeValue(d1);// ④ 最后钳制
```

**三条硬结论**：
- **① 先加后乘**：`(base + Σ ADD_VALUE) × (1 + Σ AMB)` ⇒ flat 与 percent 的顺序是 **`(B+flat)×(1+pct)`**。
- **② `ADD_MULTIPLIED_BASE` 会把装备一起放大**：装备提供的护甲/韧性/击退抗性本身就是 `ADD_VALUE`（见 1.6.2），所以 percent 的基数 = `base + 装备 + flat`。
- **③ 多个 ADD_MULTIPLIED_BASE 先求和再乘** ⇒ percent 是**线性加算，不是复利**。

### 1.6.2 `ArmorItem` 用 `ADD_VALUE`（L77-90）

```java
builder.add(Attributes.ARMOR,           new AttributeModifier(id, defense,   Operation.ADD_VALUE), group);
builder.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, toughness, Operation.ADD_VALUE), group);
if (kbRes > 0) builder.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, kbRes, Operation.ADD_VALUE), group);
```

### 1.6.3 属性区间（`Attributes` 静态构造，已逐条核对）

| 属性 | 默认值 | min | max | 类型 |
|---|---|---|---|---|
| `generic.armor` | 0.0 | 0.0 | **30.0** | `RangedAttribute` |
| `generic.armor_toughness` | 0.0 | 0.0 | **20.0** | `RangedAttribute` |
| `generic.knockback_resistance` | 0.0 | 0.0 | **1.0** | ⚠️ **NeoForge `PercentageAttribute`**，不是 vanilla `RangedAttribute` |
| `generic.attack_damage` | **2.0**（属性默认） | 0.0 | 2048.0 | `RangedAttribute` |
| `generic.max_health` | 20.0 | **1.0** | 1024.0 | `RangedAttribute` |
| `generic.movement_speed` | **0.7**（属性默认） | 0.0 | 1024.0 | ⚠️ NeoForge `PercentageAttribute(..., 1000)` |

> ⚠️ **测试陷阱**：`attack_damage` 的属性默认是 **2.0**，`movement_speed` 的属性默认是 **0.7**；
> **玩家实例的 base 分别被 `Player.createAttributes()` 设为 1.0 与 0.1**。
> 若 mod 用「属性默认值」而不是「玩家 base」做百分比基数，会分别差 **2×** 与 **7×**。
> **验证必测**：`/attribute @s minecraft:generic.attack_damage base get` 在玩家身上必须返回 **1.0**（不是 2.0）。
> 设计文档 §1.4 用的 base = 1.0 / 0.1 是**正确的**（玩家口径）。

### 1.6.4 `RangedAttribute.sanitizeValue` —— NaN 会被吞掉（重要）

```java
public double sanitizeValue(double v) {
    return Double.isNaN(v) ? this.minValue : Mth.clamp(v, this.minValue, this.maxValue);
}
```

- **NaN 被映射为 `minValue`**（不是保留 NaN）。⇒ 6 个原版属性维度**不可能**出现 NaN 血条/NaN 属性；一旦算错，症状是**数值突然掉到 0**而不是 NaN。
- 超限值被 `Mth.clamp` **静默丢弃，无日志、无异常**。⇒ 靠 sanitizeValue 兜底会让 bug 完全不可见，**必须由 `/rpg stats` 显式回报「期望值 vs 实际落值」**，否则验证无从发现。
- ⚠️ **但 7 个自定义维度不是 attribute**（`health_regen`/`damage_reduction`/`crit_chance`/`crit_damage`/`lifesteal`/`drop_rate`/`xp_gain`），**没有 sanitizeValue 保护**，NaN 会直接传播。§7.7 的 N-03 只对它们成立。

### 1.6.5 `CombatRules.getDamageAfterAbsorb`（L16-30）与「负伤害」陷阱

```java
float f  = 2.0F + armorToughness / 4.0F;
float f1 = Mth.clamp(armorValue - damage / f, armorValue * 0.2F, 20.0F);
float f2 = f1 / 25.0F;
if (weaponItem != null && level instanceof ServerLevel)
    f3 = Mth.clamp(EnchantmentHelper.modifyArmorEffectiveness(...), 0.0F, 1.0F);
else
    f3 = f2;                       // ← 无武器时【不钳制】
return damage * (1.0F - f3);
```

- 减伤率上限 **80%**（`f1 ≤ 20 ⇒ f2 ≤ 0.8`），**与护甲属性高低无关**。
- `f1` 的**下限**是 `armorValue * 0.2`：**护甲 20 → 30 并非无效**，它对高伤害的有效护甲下限从 4 提到 6（24% vs 16%）。
- ⚠️ **负伤害陷阱**：若 `armorValue > 100`，则 `armorValue*0.2 > 20`，`Mth.clamp(v, min>max, max)` 会返回 `min`，使 `f1 > 25`、`f2 > 1` ⇒ **`damage * (1 - f2)` 变成负数 = 攻击给玩家回血**；且无武器路径 `f3 = f2` 不受 [0,1] 钳制，危险成立。
  **本 mod 护甲 cap = 15（SAT 渐近，属性 ≤ 30）⇒ 不会触及**。但**必须有一条守卫测试**：`/attribute @s minecraft:generic.armor base set 200` + `/damage` 检查伤害不为负（这测的是原版行为，用于确认我们从未把装甲推到该区间）。

### 1.6.6 装备提供的最大 ADD_VALUE（BL-9 的核心输入）

由 `ArmorMaterials.NETHERITE`（L70-76）与 `ArmorItem`：整套下界合金（头/胸/腿/靴）提供

| 属性 | 每件 | 四件合计 |
|---|---|---|
| `ARMOR` | 3 / 8 / 6 / 3 | **+20** |
| `ARMOR_TOUGHNESS` | 3.0 | **+12** |
| `KNOCKBACK_RESISTANCE` | 0.1 | **+0.4** |

---

## 2. 验证分层总览

| 层 | 名称 | 手段 | 自动化 | 覆盖 |
|---|---|---|---|---|
| **L0** | 构建验证 | `gradlew build` + jar 结构解析 | ✅ 全自动 | §3 |
| **L1** | 静态验证 | 源码/字节码/资源扫描脚本 | ✅ 全自动 | §4 |
| **L2** | 逻辑验证 | NeoForge **GameTest**（`gradlew runGameTestServer`）| ✅ 全自动 | §5 大部分 |
| **L3** | 行为验证矩阵 | 服务端命令脚本 + GameTest | ✅ 大部分自动 | §6 |
| **L4** | 边界与作弊面 | 命令脚本 + 数据包 + 代码审计 | ⚠️ 部分自动 | §7 |
| **L5** | 回归/兼容 | 双 mod 共存启动 + 冒烟 | ⚠️ 部分自动 | §8 |
| **L6** | 人工确认 | 游戏内照做清单 | ❌ 人工 | §9 |

**L2 是本方案的关键杠杆**：NeoForge 自带 `runGameTestServer`（参考工程 build.gradle 已配置 `forge.enabledGameTestNamespaces`，且存在 `runs/junit/`）。26 条加点线中的绝大多数可以在无头环境里用 `FakePlayer` + 真实伤害/经验/掉落路径断言，**不需要人进游戏**。因此：

> **对 architect/code 的硬性要求**：为了让 26 条线可独立验证，mod 必须提供**服务端指令测试面**，至少包含：
> - `/rpg stats [<player>]` —— 以聊天/结构化形式回显：等级、总经验、可用点数、已投点数（按维度×模式）、以及**每个维度的最终计算值**。
> - `/rpg debug addpoints <n>`、`/rpg debug setlevel <n>` —— 仅 OP/单人开启，用于构造测试前置状态。
> - `/rpg respec` —— 洗点。
> 没有这一层，"点数超发""洗点残留""上限收敛"等用例只能靠人肉点击，无法形成可信证据链。

---

## 3. L0 — 构建验证

### 3.1 验收项

| ID | 检查 | 方法 | 判定 |
|---|---|---|---|
| B-01 | `gradlew build` 成功 | `.\gradlew.bat build --console=plain` | 退出码 0 **且**日志含 `BUILD SUCCESSFUL` |
| B-02 | 产出 jar | `build/libs/rpgleveling-<version>.jar` | 文件存在、大小 > 10KB |
| B-03 | jar 内 `META-INF/neoforge.mods.toml` 存在且占位符已替换 | 解压读取 | 无残留 `${...}`，`modId="rpgleveling"` |
| B-04 | `version` 正确 | 同上 | 与 `gradle.properties` 的 `mod_version` 一致 |
| B-05 | 依赖范围正确 | 同上 | `neoforge` → `[21.1.0,)`；`minecraft` → `[1.21.1,1.22)`；`loaderVersion` `[4,)`；两者 `type="required" side="BOTH"` |
| B-06 | 无重复 class | 解析 jar entry，统计重名 | 无重复 entry |
| B-07 | 无 `src/main/java` 之外的源码被误打包 | 扫描 entry 前缀 | 只含 `com/rpgleveling/**` + `assets/` + `data/` + `META-INF/` |
| B-08 | 未打包测试/开发类 | 扫描 | 无 `*Test.class`、无 `FakePlayer` 引用 |
| B-09 | `pack.mcmeta` 存在 | 读取 | `pack_format=34`（1.21.1 资源包） |
| B-10 | 构建可重复 | 连跑两次 `clean build` | 两次均成功；jar 内文件集合一致 |
| B-11 | 无编译告警中的错误类 | 日志 grep `warning:` | 允许 warning；不得有 `error:`、`Deprecated API used` 之外的阻断项 |

### 3.2 自动化脚本

`_verify/tools/check_jar.ps1 <jar路径>` —— 实现 B-02 ~ B-09，输出 PASS/FAIL 表。
用法：
```powershell
.\_verify\tools\check_jar.ps1 -Jar .\build\libs\rpgleveling-1.0.0.jar
```

### 3.3 判定

**L0 全 PASS 是进入 L1 的门槛。** L0 失败时不得开始 L1~L6。

---

## 4. L1 — 静态验证

### 4.1 注册表完整性（S-01 ~ S-04）

| ID | 检查 | 方法 | 判定 |
|---|---|---|---|
| S-01 | 所有 `DeferredRegister` 都调用了 `.register(modEventBus)` | 源码扫描：统计 `DeferredRegister.create*` 声明 vs `.register(...)` 调用 | 数量一致；**漏注册的注册表在运行期完全静默失效** |
| S-02 | 所有 `DeferredHolder`/`DeferredItem` 都被真实引用 | 扫描字段使用 | 无"注册了但从不使用"的项（提示设计冗余） |
| S-03 | 注册名与资源文件名一一对应 | 提取所有 `register("xxx")`，与 `assets/rpgleveling/models/item/*.json`、`blockstates/*.json`、`textures/**` 比对 | 每个 item 有 model + texture；每个 block 有 blockstate + model |
| S-04 | 无重复注册名 | 按注册表分组统计字符串常量 | 同表内无重名 |

### 4.2 事件订阅完整性（S-05 ~ S-07）

| ID | 检查 | 方法 | 判定 |
|---|---|---|---|
| S-05 | 每个 `@SubscribeEvent` 方法所在的类**确实会被注册** | 扫描：类上有 `@EventBusSubscriber`，**或**类在 `@Mod` 构造里被 `addListener`/`register` | **不允许存在"有 `@SubscribeEvent` 但类从未注册"的方法**——这是静默失效的头号来源 |
| S-06 | `@EventBusSubscriber` 的 `modid` 正确、总线正确 | 读取注解 | `modid="rpgleveling"`；游戏事件用默认 GAME 总线，注册类事件用 `bus = Bus.MOD` |
| S-07 | 处理函数签名合法 | 解析 | 方法为 `public static`（或已注册实例方法），**恰好 1 个参数**，参数类型是 `Event` 子类；返回 `void` |
| **S-17** | **经验倍率不得双重生效（硬不变量，architect §6.4 INV-1）** | `grep -rn "PlayerXpEvent" src/main/java/` | **必须 0 命中**。有命中 ⇒ 与 `LivingExperienceDropEvent` 叠加成「倍率²」⇒ 判 **FAIL** |
| **S-18** | **单一入账函数（architect §6.4 INV-2）** | `grep -rn "XP_GAIN\|xp_gain" src/main/java/ --include=*.java` | 只允许在 `XpService.java` 命中一次；多处命中 ⇒ 存在重复计数路径 ⇒ 判 **FAIL** |

> **反例**：`@EventBusSubscriber` 类放在 `com.rpgleveling.client` 包里没写 `value = Dist.CLIENT` → 服务端会尝试加载它 → DedicatedServer 崩溃。见 S-08。

### 4.3 客户端/服务端类混用（S-08，**专测 DedicatedServer 崩溃**）

这是"编译能过、服务端必崩"的唯一高发原因。**必须两层都做**：

**S-08a 字节码层（自动）**
`_verify/tools/check_dist.py`：
1. 遍历 `build/classes/java/main/**/*.class`，用 `javap -c -p` 或 ASM 读常量池，找出所有对以下包的引用：
   - `net/minecraft/client/`
   - `com/mojang/blaze3d/`
   - `net/neoforged/neoforge/client/`
   - `net/neoforged/neoforge/client/event/`（`RegisterKeyMappingsEvent`、`RegisterGuiLayersEvent` 等）
2. 输出"引用了客户端类的类清单及其引用点"。
3. **判定**：每个这样的类必须满足以下之一，否则 FAIL：
   - (a) 类上有 `@EventBusSubscriber(modid="rpgleveling", value = Dist.CLIENT)`，且**不**被任何 common 代码直接 import/实例化；
   - (b) 类位于 `com.rpgleveling.client` 包，且**只被**通过 `@EventBusSubscriber(value = Dist.CLIENT)` 或 `FMLEnvironment.dist == Dist.CLIENT` 守卫的代码触碰；
   - (c) 类整体包装在 `if (FMLEnvironment.dist.isClient())` 之后且方法体独立（不导致类加载）。
4. **特别检查**：`@Mod` 主类构造函数、`DeferredRegister` 的 lambda、静态初始化块**绝对不能**直接引用客户端类——静态初始化不会被 dist 守卫拦住。

**S-08b 运行期层（自动，权威）**
```powershell
$env:JAVA_HOME = "C:\Gradle\jdk-21-temurin"
.\gradlew.bat runServer --console=plain
```
- 判定：服务端完成启动，日志出现 `Done (Xs)! For help, type "help"`，且**无** `NoClassDefFoundError` / `ClassNotFoundException` / `Failed to create mod instance`。
- 追加：启动后依次执行 `/rpg stats`（控制台）、`/summon minecraft:zombie`、`/damage`、`/rpg respec` 均无异常。
- **S-08a 是静态筛查，S-08b 才是判定依据。两者不一致时以 S-08b 为准。**

### 4.4 资源 lang 键完整性（S-09 ~ S-11）

| ID | 检查 | 方法 | 判定 |
|---|---|---|---|
| S-09 | 所有翻译键都有 `en_us` 条目 | 提取源码中 `Component.translatable("...")` 的键 + 所有注册名推导出的键（`item.rpgleveling.X`、`block.rpgleveling.X`、`gui.rpgleveling.X`、`rpgleveling.config.X`），与 `en_us.json` 比对 | 差集为空 |
| S-10 | `zh_cn.json` 与 `en_us.json` 键集合**完全相同** | 解析两个 JSON 取 key set | 双向差集为空（缺翻译会显示 raw key） |
| S-11 | 无占位符错配 | 检查含 `%s`/`%1$s` 的键在两个语言文件中占位符个数一致 | 一致，否则运行期 `IllegalFormatException` |
| S-12 | 无空值/空串 | 解析 | 无 `""` 值 |

脚本：`_verify/tools/check_lang.py`。

### 4.5 其他静态检查

| ID | 检查 | 判定 |
|---|---|---|
| S-13 | 数值常量无魔法数散落 | 所有维度上限/系数集中在一处（如 `RpgConfig`），便于单点验证 |
| S-14 | 无 `System.out.println` | 改用 `Logger`（日志才可被自动化解析） |
| S-15 | 关键日志可在不重编译的情况下开 DEBUG | 配置项或 `log4j` 配置支持，用于 §6 的统计型用例 |
| S-16 | 玩家数据存储方式明确 | 必须查明：DataAttachment / Capability / SavedData / 自建 NBT。**写入位置必须记录**，用于 §7 持久化用例定位证据文件 |

---

## 5. L2/L3 — 逐维度行为验证矩阵

### 5.0 通用约定与测量工具

**被测对象**：13 个维度 × 2 种加点模式（flat 直接数值 / percent 百分比）= **26 条加点线**。

#### 5.0.1 五个"绕开 mod 自身"的独立观测通道

| 通道 | 命令 | 用途 |
|---|---|---|
| **A. 属性直读** | `/attribute <t> <attr> get` | 读取最终值（base + 全部 modifier 结算后） |
| **B. 属性原始 NBT** | `/data get entity <t> Attributes` | **最强通道**：直接看到 `Base` 与每个 `Modifiers[]{Name,Amount,Operation,UUID}`。可发现重复叠加、洗点残留、错误 Operation |
| **C. 固定伤害探针** | `/damage <t> <amount> <type> [by <src>]` | 确定性伤害输入，用于测减伤/护甲/韧性/生命偷取 |
| **D. 经验直读** | `/xp query <t> points` / `/xp add <t> <n> points` | 确定性经验输入与读取，用于测经验倍率 |
| **E. 实体状态差分** | `/data get entity <t> Health` / `Pos` / `/summon`+`/kill` 计数 | 生命、位移、掉落 |

> **纪律**：任何维度的判定都必须至少使用 A/B 中的一条 **加上** C/D/E 中的一条。只看 mod 的 `/rpg stats` 输出**不算验证通过**（那只是自报）。

#### 5.0.2 标准测试靶（一次性搭建，供全部维度复用）

```mcfunction
# 关闭一切会污染测量的原版机制
/gamerule naturalRegeneration false
/gamerule doMobLoot true
/gamerule doTileDrops true
/gamerule mobGriefing false
/gamerule randomTickSpeed 0

# 高血量、0 护甲、0 韧性的伤害靶（消除护甲曲线的干扰）
/summon minecraft:zombie 0 -60 0 {NoAI:1b,Silent:1b,NoGravity:1b,PersistenceRequired:1b,Tags:["rpg_probe"],Attributes:[{id:"minecraft:generic.max_health",base:1000000.0},{id:"minecraft:generic.armor",base:0.0},{id:"minecraft:generic.armor_toughness",base:0.0},{id:"minecraft:generic.knockback_resistance",base:0.0}],Health:1000000.0f}
```

测量前后各读一次 `Health`，取差值 = 实际承伤。

#### 5.0.3 必须由 architect 明确、否则无法判定的 3 个公式约定

**（这是当前设计的最大验证风险，必须在 DESIGN.md 中写死）**

1. **percent 的叠加方式**是三选一：
   - **约定 A（线性/加法）**：`V = B × (1 + k_p × u_p / 100)` —— 每点 +固定百分比，简单可预测
   - **约定 B（复利/指数）**：`V = B × (1 + u_p/100)^k_p` —— 高点数时爆炸
   - **约定 C（加算百分比）**：与 A 相同但 `u_p` 为"每点百分点"
2. **flat 与 percent 同时存在时的运算顺序**：
   - **顺序 I**：`V = (B + flat) × (1 + pct)`
   - **顺序 II**：`V = B × (1 + pct) + flat`
3. **`damage_reduction` 的 flat 语义**：该维度本身单位就是"百分比"，所以
   - **语义 α**：flat 模式 = 每点 +1 个百分点；percent 模式 = 在已有减免率上再乘
   - **语义 β**：flat 模式 = 每点减免固定点数伤害（平砍减免）；percent 模式 = 减免百分比
   > α 与 β 会导致**完全不同的验收结果**，architect 必须二选一。

4. **每个维度的收益曲线与渐近上限**（lead 已定调"无上限 = 收益递减渐近公式收敛"）：
   - 每个维度必须给出形如 `V(k) = Vmax - (Vmax - V0)·e^(-k/τ)` 或 `V(k) = V0 + a·k/(k+b)` 的**单调有界**公式；
   - verifier 必须实测 `V(k)` 在 k = 1, 10, 100, 1000, 100000 时的值，并断言：
     - 严格单调递增（或至少非递减）
     - `lim V(k) < Vsafe`，其中 `Vsafe` 是该维度的"安全渐近值"（见下表）
     - 不存在硬编码的 `if (k > MAX) k = MAX` 断崖（允许，但必须在设计文档声明并作为期望值）
   - **安全渐近值表（verifier 的独立判定基线，不依赖 architect 的数值）**：

     | 维度 | 绝对安全上界 | 超限后果 |
     |---|---|---|
     | attack_damage | float 有限，且不使目标单次伤害溢出为 Infinity | 溢出一击破坏游戏 |
     | max_health | ≤ 1024（原版血条渲染与 float 精度） | 血条溢出屏幕、UI 错乱 |
     | health_regen | ≤ 20 HP/s（低于 max_health） | 等效无敌 |
     | movement_speed | ≤ 1.0（原版 0.1 的 10 倍） | 触发服务端移动校验、被踢 |
     | armor | 属性 ≤ **30**；单次减伤率 ≤ **80%** | 超 30 被静默钳制；减伤率封顶 80% |
     | xp_gain | ≤ 1000× | 经验球/实体爆炸 |
     | knockback_resistance | ≤ 1.0（原版 `RangedAttribute` 硬钳制） | 超出被静默钳制或产生负击退 |
     | armor_toughness | ≤ 20（原版区间上限） | 超出被静默钳制 |
     | damage_reduction | ≤ 0.95（**不得达到 1.0**） | 100% = 无敌 = `/kill` 也可能失效 |
     | crit_chance | ≤ 1.0 | 超 100% 无意义，可能出现负非暴击率 |
     | crit_damage | ≤ 100× | 伤害溢出 Infinity |
     | lifesteal | ≤ 使 单次偷取 ≤ max_health | 血量超上限 / NaN |
     | drop_rate | ≤ 64（单次掉落不超过一组） | 非法 ItemStack、实体爆炸 |

     > 这些是**验证用的独立标尺**：即使 architect 选择不设上限，只要实测值超过上表且产生上表后果，即判 **FAIL**。

5. **percent 的作用基数必须逐维度确定，且必须连同「装备贡献」一起验证**（见 §1.6 与 BL-9）。

**判别性测试（一次实验同时区分上面 1/2）**：
```
1. 洗点，记录基线 B = /attribute ... get
2. 只投 flat：k_f 点 → 记录 V_f
3. 洗点，只投 percent：k_p 点 → 记录 V_p
4. 洗点，同时投 k_f 与 k_p → 记录 V_both
5. 判定：
   - 若 V_f - B = k_f·u_f（线性）→ flat 正确
   - V_p 与 B·(1+k_p·u_p/100) 或 B·(1+u_p/100)^k_p 哪个匹配 → 定 percent 约定
   - V_both 与 (B+k_f·u_f)(1+...) 或 B(1+...)+k_f·u_f 哪个匹配 → 定顺序
```
**证据**：4 组数值 + 计算过程，写入 `_verify/evidence/L3/order-of-operations.md`。

#### 5.0.4 通用容差

| 量 | 容差 | 理由 |
|---|---|---|
| 属性值（读 A/B） | `±0.001` | 双精度累加 |
| 生命值（`Health` 是 float） | `±0.05` | float 精度 |
| 整数计数（经验点、掉落数） | `0`（必须精确） | 整数运算无误差 |
| 百分比类派生值 | `±0.5 个百分点` | 取决于内部用 float 还是 double |

#### 5.0.5 每条线的 6 个必做动作

对 26 条线中的**每一条**，必须完成：
1. **归零**：`/rpg respec` + 确认 `/data get entity @s Attributes` 回到原版基线
2. **单点**：投 1 点 → 记录观测值 → 与公式预测比对
3. **多点线性**：投 k=2,5,10 → 验证随 k 的变化符合约定
4. **幂等**：退出重进 / 切换维度（下界↔主世界）/ 死亡重生 → 观测值**必须不变**
5. **撤销**：`/rpg respec` → 观测值必须**精确**回到第 1 步基线，且 `Attributes` 中**无残留 Modifier**
6. **叠加**：与另一条线同时投（同维度 flat + percent，或跨维度）→ 无重复叠加、无相互破坏
7. **装备状态（**必做，BL-9 的直接推论**）**：同一条线的判定必须在**两种装备状态**下各跑一遍并分别记录：
   - **裸装**（`/clear @s`，且 `/data get entity @s Attributes` 确认无装备 modifier）
   - **全套下界合金**（头/胸/腿/靴，提供 `+20 armor` / `+12 toughness` / `+0.4 knockback_resistance`）
   > 因为 `ADD_MULTIPLIED_BASE` 的基数是 `base + 装备 + flat`（§1.6.1），**同一加点方案在两种状态下会得出两个不同答案**。
   > 若两者差异超出公式预期 ⇒ 说明实现取错了基数。若**穿装备后数值被钳死不再增长** ⇒ 见 BL-9。
8. **钳制可见性**：投到高点数后，`/rpg stats` 的 `computed` 与 `effective`（`/attribute` 实读）必须**同时显示**。
   两者出现差异时必须能被观察者看见 —— 这是 `sanitizeValue` 静默丢弃（§1.6.4）唯一的暴露窗口。

---

### 5.1 D01 — `attack_damage` 基础攻击伤害

- **期望载体**：原版属性 `minecraft:generic.attack_damage`（NeoForge `Attributes.ATTACK_DAMAGE`），或 `LivingIncomingDamageEvent` 事件乘区。
- **原版基线 B**：空手 1.0（`/attribute @s minecraft:generic.attack_damage base get`）
- **独立观测**：通道 A + B；端到端用 C（`by @s` 观察事件型实现）。

| 模式 | 步骤 | 期望观测值 | 判定标准 |
|---|---|---|---|
| **flat** | 洗点 → 记录 B → 投 k 点 flat → `/attribute @s minecraft:generic.attack_damage get` | `B + k·u_f` | 若为属性型实现：实测值 = 期望值（±0.001）。**若** `/attribute` 不变但 `/damage … by @s` 造成的伤害增加 → 判定为事件型实现，改用伤害差判定：Δ伤害 = k·u_f |
| **percent** | 洗点 → 投 k 点 percent → 读取 | 按 §5.0.3 约定的公式 | 实测 = 期望（±0.001）；**并验证是对"最终值"还是"基础值"取百分比**——对已含装备加成的值取百分比是常见错误 |

**端到端确定性验证**：对靶 `rpg_probe` 施加一次固定伤害 `/damage @e[tag=rpg_probe,limit=1] 10 minecraft:player_attack by @s` → 读 Health 差值。因靶护甲=0、无附魔、无抗性，承伤应精确等于 10 × (攻击方乘区)。有攻击加成的实现此处应 > 10。

**边界/坑**：
- 增伤是否影响**空手**、**弓箭**（`minecraft:arrow`）、**三叉戟**、**横扫**（sweeping）？设计需明确范围。至少测试：空手、铁剑近战、弓。
- 扫击伤害 `Attributes.SWEEPING_DAMAGE_RATIO` 是否被误乘。
- 是否错误地也加成到 `/damage` 指令、摔落伤害、仙人掌伤害（这些无攻击者，不该加成）。

---

### 5.2 D02 — `max_health` 基础生命值

- **期望载体**：`minecraft:generic.max_health`（默认 20.0）
- **独立观测**：通道 A + B；行为用 `/data get entity @s Health` 与 `/damage` 致死测试。

| 模式 | 步骤 | 期望观测值 | 判定标准 |
|---|---|---|---|
| **flat** | 投 k 点 → `/attribute @s minecraft:generic.max_health get` | `20 + k·u_f` | 精确匹配 |
| **percent** | 投 k 点 → 读取 | `20 × (1 + …)` | 精确匹配；**注意是否错误地对"装备后的最大值"取百分比** |

**必测子项**：
1. **满血 vs 残血**：增加最大生命**不应**自动回血（原版语义）。若设计为回满，需在设计文档声明并作为期望值。
2. **致死边界**：把最大生命投到极低（若允许），检查当前生命是否正确被钳制、不出现负血/NaN。
3. **死亡重生**（**最高危**）：投满点 → `/kill @s` → 重生 → `/attribute @s minecraft:generic.max_health get`。**必须等于死亡前的值**。NeoForge 玩家属性在重生时会被重建，modifier 不会自动保留——这是本类 mod 的头号线上事故。
4. **跨维度**：主世界 → 下界 → 回主世界，值不变。
5. **重登**：退出存档再进，值不变且 `/data get entity @s Attributes` 中 modifier **不重复**。
6. **无敌帧**：生命上限提高后 `/damage` 大于上限的伤害应正常死亡（见 D09 减免是否使其免疫）。

---

### 5.3 D03 — `health_regen` 生命恢复

- **期望载体**：自定义（非原版属性）。通常为 tick 事件或 `LivingHealEvent` 驱动。
- **独立观测**：`/data get entity @s Health` 的**时间差分**。

**测量协议（必须严格遵守，否则不可复现）**：
```mcfunction
/gamerule naturalRegeneration false          # 隔离原版回血
/effect clear @s                             # 清除再生/中毒等效果
/data merge entity @s {foodLevel:6,Health:6.0f}   # 低血量起点，且食物不足以触发原版回复
# t=0：记录 H0 = /data get entity @s Health
# 等待恰好 200 tick（10 秒）。用命令方块或 /schedule 精确计时，不要用秒表
# t=200：记录 H1
# 恢复速率 = (H1 - H0) / 10   （HP/秒）
```

| 模式 | 步骤 | 期望观测值 | 判定标准 |
|---|---|---|---|
| **flat** | 投 k 点 → 测 10 秒回复量 | `u_f · k · 10` HP（若 u_f 单位是 HP/秒） | 实测速率 = 期望（±0.05） |
| **percent** | 投 k 点 → 测 10 秒回复量 | 基线速率 × 百分比因子 | 实测 = 期望；**必须同时测"基线速率"作为分母** |

**边界/坑**：
1. **不得超过最大生命**：H1 必须 ≤ max_health（float 溢出会出现 >20 的红心或 NaN）。
2. **死亡后不得继续回复**：`/kill` 后检查不会"诈尸回血"。
3. **不得与饱食度回复叠加成双倍**：`/data merge entity @s {foodLevel:20,saturation:20.0f}` 时应表现为两者取其一或明确叠加，需设计声明。
4. **不得在创造/旁观模式生效**（若设计如此）。
5. **性能**：26 条线全满时，检查 TPS 不下降（`/forge tps` 或 `/neoforge tps`）。每 tick 每玩家一次属性重算会导致明显卡顿——**这是本维度最容易踩的性能坑**。

---

### 5.4 D04 — `movement_speed` 基础移动速度

- **期望载体**：`minecraft:generic.movement_speed`（默认 **0.1**）
- **独立观测**：通道 A（精确公式核对）+ 位移差分（体感/行为）。

| 模式 | 步骤 | 期望观测值 | 判定标准 |
|---|---|---|---|
| **flat** | 投 k 点 → `/attribute @s minecraft:generic.movement_speed get` | `0.1 + k·u_f` | 精确匹配 |
| **percent** | 投 k 点 → 读取 | `0.1 × (1 + …)` | 精确匹配 |

**行为验证（位移差分，可半自动）**：
```mcfunction
# 在超平坦、无障碍、无冰面区域，沿 +X 直线
/tp @s 0 100 0 90 0
# 记录 P0 = /data get entity @s Pos
# 按住 W 前进恰好 10 秒（用命令方块 /schedule 在 10 秒后 tp 回并读 P1）
# 速度 ≈ (P1.x - P0.x) / 10   （方块/秒）
```
- 判定：实测速度相对提升比例 ≈ 属性提升比例（±5%）。属性对但位移不对 → 说明 modifier 的 **Operation 用错**（`ADD_VALUE` vs `ADD_MULTIPLIED_BASE` vs `ADD_MULTIPLIED_TOTAL`）。
- **Operation 必须显式验证**：用通道 B 读 `Modifiers[].Operation`，与设计文档对照。百分比加点的正确 Operation 通常是 `add_multiplied_base`，用成 `add_multiplied_total` 会在乘算链上产生非线性差异。

**边界/坑**：
- **服务器移动校验**：速度过高时服务端会刷 `moved too quickly!` 警告，某些反作弊会踢人。必须给出上限或说明"单人/可信服"。
- 移速是否影响**骑马/坐船/鞘翅**？设计需明确。
- 移速提升是否影响**跳跃距离**（正常副作用，非 bug）。
- 极端值（如 10.0）下是否会穿墙/掉落虚空——需实测并给出上限。

---

### 5.5 D05 — `armor` 基础防御力

- **期望载体**：`minecraft:generic.armor`（默认 0）
- **独立观测**：通道 A/B + **通道 C 固定伤害探针**（最可靠）。

| 模式 | 步骤 | 期望观测值 | 判定标准 |
|---|---|---|---|
| **flat** | 投 k 点 → `/attribute @s minecraft:generic.armor get` | `0 + k·u_f` | 精确匹配 |
| **percent** | 投 k 点 → 读取 | `0 × (1+…) = 0` ⚠️ | **重要边界**：护甲原版基线为 0，对它取百分比**恒为 0**。必须确认 percent 模式的作用基数是"玩家基础护甲"还是"装备后总护甲"。若为后者，必须穿装备测；若设计未定义 → 判 **FAIL/设计缺陷** |

**行为验证（原版护甲公式必须被尊重）**：
```
1. 记录 H0；/damage @s 20 minecraft:mob_attack；记录 H1 → 承伤 = H0 - H1
2. 改变 armor 加点，重复
原版公式（1.21.1 CombatRules）：
  f = 2 + armorToughness/4
  g = clamp(armor - damage/f, armor*0.2, 20)
  实际 = damage * (1 - g/25)
判定：实测承伤 = 公式预测（±0.5）→ 说明护甲是通过原版属性生效的
```

**边界/坑（设计必须知道的硬事实）**：
- **原版护甲有效上限是 20 点**（公式里 `clamp(..., 20.0)`），即最多减伤 **80%**。护甲堆到 100 点和堆到 20 点，对普通攻击的减伤**完全相同**。
  → **若设计宣称"护甲无上限、越高越硬"，这就是错的**。验证时必须把护甲堆到 30/100 并证明承伤不再下降，然后要求 architect 明确这是"已知并接受"还是"需要用 D09 减免补足"。
- 护甲对 `minecraft:generic`、`minecraft:out_of_world`（虚空）、`minecraft:starve` 等不生效（`DamageTypeTags.BYPASSES_ARMOR`）。必须验证 mod 没有错误地绕开这个 tag。
- **护甲耐久**：原版护甲会掉耐久。若 mod 的护甲是"虚拟护甲"（属性型），则不掉耐久——需在设计中说明，否则玩家会觉得"白嫖"。

---

### 5.6 D06 — `xp_gain` 经验获取倍率

- **期望载体**：`PlayerXpEvent.XpChange`（`setAmount`）、`LivingExperienceDropEvent`（`setDroppedExperience`）、或 `PlayerXpEvent.PickupXp`。
- **独立观测**：**通道 D（最干净）**：
```
/xp query @s points          → P0
/xp add @s 100 points        → 触发 Player#giveExperiencePoints(100) → 触发 PlayerXpEvent.XpChange
/xp query @s points          → P1
倍率 = (P1 - P0) / 100
```

| 模式 | 步骤 | 期望观测值 | 判定标准 |
|---|---|---|---|
| **flat** | 投 k 点 → 上述三步 | `100·(1 + flat 效果)` 且 P1-P0 为**整数** | 精确整数匹配；出现小数/取整误差需在设计声明舍入规则 |
| **percent** | 投 k 点 → 上述三步 | `100 × (1 + …)` | 精确匹配 |

**双通道交叉验证（必做，防"只实现了一半"）**：
1. **指令路径**：`/xp add` → 测 `XpChange`
2. **怪物掉落路径**：`/summon minecraft:zombie` → 用玩家击杀 → 读经验球：`/data get entity @e[type=minecraft:experience_orb,sort=nearest,limit=1] Value` → 求和，与未加点基线对比
3. **矿石/熔炉路径**：挖煤/烧铁得到的经验是否也乘倍率？
- **判定**：三条路径的倍率**必须一致**。若只有 `/xp add` 生效、怪物掉落不生效 → 说明只挂了 `XpChange` 而没挂 `LivingExperienceDropEvent`（或反之），判 **FAIL**。

**边界/坑**：
- **双倍叠加**：若同时挂了 `LivingExperienceDropEvent`（放大经验球）**和** `XpChange`（放大吸收量），玩家实际获得 `mult²` 倍 —— 经典 bug。**必须专门测**：击杀僵尸，经验球 `Value` 之和 vs 玩家实际获得的点数，两者应只乘一次。验证方法：经验球 Value 已被放大，则吸收时不应再放大。
- **经验球单次上限**：经验球 `Value` 过大时会被原版拆分；检查极端倍率下不会造成一次刷出上千实体导致卡顿。
- **附魔之瓶**、`/xp add` 是否为"作弊路径"应被排除？设计需明确。
- **获得经验是否也放大等级**、是否与经验曲线交互（高等级时倍率导致瞬间满级）。
- **溢出**：倍率极大时总经验 `int` 溢出 → 变负数。必须测（见 §7 边界）。

#### D06.1 经验曲线（lead 已锁定，可直接断言）

```
XP(L) = 100 + 20*(L-1)                      , L <= 200      （线性段）
XP(L) = 4080 * (1 + 0.15*ln(L-199))         , L >  200      （对数段）
每级获得 1 点
```

**可精确断言的采样点**（verifier 必须逐点核对，容差 ±0.5）：

| L | 期望 XP(L) | 备注 |
|---|---|---|
| 1 | 100 | 线性段起点 |
| 2 | 120 | 步进 +20 |
| 100 | 2080 | — |
| 199 | 4060 | — |
| **200** | **4080** | 线性段终点；必须与对数段在 200→201 处**无跳变** |
| 201 | 4080·(1+0.15·ln2) ≈ **4504.21** | 对数段起点 |
| 500 | 4080·(1+0.15·ln301) ≈ **7572.75** | lead 的 7573 基准；精确值 7572.75，落在 ±0.5 容差内 |
| 1000 | 4080·(1+0.15·ln801) ≈ **8171.75** | — |
| 10000 | 4080·(1+0.15·ln9801) ≈ **9704.43** | 注意 ln(9801)=2·ln(99)=9.1902397 |

> **基准值以本表为准（已逐点复算）**。lead 提到的 500 级基准 7573 与精确值 7572.75 相差 0.25，在容差内；若验收需要严格整数，请以 `round(XP(L))` 为期望并统一舍入规则。

**判定**：
- 每级差值恒 > 0（严格单调），且 200↔201 处不出现**负增量或突降**（分段函数最常见的 bug 是 `ln(L-199)` 在 L=200 时 `ln(1)=0` 导致 XP 从 4080 掉回 4080 或更低——必须实测交界）。
- `L-199 <= 0` 时不得调用 `ln(0)` 或 `ln(负数)`（→ `-Infinity`/NaN）。**必测 L=199、200、201 三点。**
- 对数段增长极慢 ⇒ **高等级升级速度不会自然下降**。这是设计选择，但必须确认不会导致"挂机刷怪几小时就满级"的体验问题；验证时给出 L=10000 所需的累计经验量。

#### D06.2 非击杀经验 = 首次行为奖励（**反刷重点**）

设计：每种方块/物品**首次**触发给一次经验，之后不再给；按玩家持久化"已解锁 ID 集合"。

| ID | 用例 | 步骤 | 判定 |
|---|---|---|---|
| F-01 | 重复挖同一方块 | 挖同一坐标的石头 100 次（放置+挖循环） | 只有**第 1 次**给经验；第 2..100 次经验增量为 **0** |
| F-02 | 重复合成同一物品 | 合成同一种物品 100 次 | 只有第 1 次给经验 |
| F-03 | 重复钓鱼 | 钓 100 次 | 只有第 1 次给经验（或按"首次钓到某鱼类"计） |
| F-04 | 变体是否算新 ID | 挖 `stone` 之后挖 `deepslate`/`granite` | 按设计：不同方块 ID 应各自首次给经验；同一 ID 的不同状态（如 `oak_log` 的轴向）**不应**被当作新 ID |
| F-05 | 已解锁集合持久化 | 挖 `stone` → 退出 → 重进 → 再挖 `stone` | **不再给经验**（集合已落盘）。这是最容易漏的：集合只存内存 → 重登即可重刷 |
| F-06 | 跨维度持久化 | 主世界挖 `stone` → 去下界 → 回主世界挖 `stone` | 不再给经验 |
| F-07 | 多人集合隔离 | A 挖 `stone` 后，B 第一次挖 `stone` | B **应**给经验（集合按玩家独立） |
| F-08 | 集合体积 | 长期游玩后集合增长 | 集合必须有界（用 `BitSet`/数字 ID 而非字符串全量存储）；存档大小不爆炸 |
| F-09 | 与经验倍率的交互 | 首次奖励 + xp_gain 倍率 | 首次奖励**也应**被倍率放大，且只放大一次 |
| F-10 | 刷取绕过 | 用不同工具/不同维度/不同世界挖同一 ID | 不得绕过"首次"判定 |
| F-11 | 击杀经验不适用首次规则 | 反复击杀同一怪物 | 击杀经验**应每次都给**（首次规则只针对非击杀行为）。若误用首次规则 → 判 FAIL |

> **F-05 / F-11 是最容易出错的两条**：前者决定"重登即无限刷经验"，后者决定"打怪不给经验"。两者都必须有独立用例。

---

### 5.7 D07 — `knockback_resistance` 击退抗性

- **期望载体**：`minecraft:generic.knockback_resistance`（默认 **0.0**）
- **⚠️ 原版硬约束**：该属性由 `RangedAttribute` 定义，**区间为 [0.0, 1.0]**，赋值会被自动钳制。

| 模式 | 步骤 | 期望观测值 | 判定标准 |
|---|---|---|---|
| **flat** | 投 k 点 → `/attribute @s minecraft:generic.knockback_resistance get` | `min(1.0, 0 + k·u_f)` | 值**永不超过 1.0**；超限部分被钳制应被设计明确承认 |
| **percent** | 投 k 点 → 读取 | `0 × (1+…) = 0` ⚠️ | **基线为 0，百分比恒为 0** —— 同 D05 问题。必须确认基数定义，否则判设计缺陷 |

**行为验证（位移差分）**：
```mcfunction
# 站定，记录 P0 = /data get entity @s Pos
/summon minecraft:zombie ~2 ~ ~ {NoAI:0b,Tags:["kb"],Attributes:[{id:"minecraft:generic.attack_damage",base:1.0}]}
# 让僵尸攻击一次，或直接用 /damage 的击退来源
# 记录 P1；位移 = |P1 - P0|
```
- 抗性 1.0 时位移应为 **0**（或仅剩极小值）；抗性 0 时位移显著。
- 判定：位移随抗性单调下降，1.0 时归零。

**边界/坑**：
- 值 > 1.0 若未被钳制而是直接参与 `1 - r` 计算，会得到**负击退 = 被吸向攻击者**。必须测 `/attribute @s minecraft:generic.knockback_resistance base set 2.0` 观察是否有异常反向位移。
- 是否影响**爆炸击退**、**钓鱼竿拉扯**、**活塞**？设计需明确。
- 是否影响**自己受到的击退**还是**造成的击退**（别搞反方向）。

---

### 5.8 D08 — `armor_toughness` 基础盔甲韧性

- **期望载体**：`minecraft:generic.armor_toughness`（默认 0）
- **独立观测**：通道 A/B，**行为必须用通道 C 配合固定护甲值**。

**关键：韧性单独无法观测效果，必须与护甲联合测试。**
```
固定 /attribute @s minecraft:generic.armor base set 20
设定 toughness = T
/damage @s 100 minecraft:mob_attack → 读 Health 差
原版预测：f = 2 + T/4 ; g = clamp(20 - 100/f, 4.0, 20) ; 承伤 = 100 * (1 - g/25)
对比 T = 0 / 4 / 20 / 100 四组
```
- 判定：实测承伤序列 = 公式预测序列（±0.5）。若 T 增大而承伤完全不变 → 韧性没生效。

| 模式 | 步骤 | 期望观测值 | 判定标准 |
|---|---|---|---|
| **flat** | 投 k 点 → `/attribute @s minecraft:generic.armor_toughness get` | `0 + k·u_f` | 精确匹配 + 承伤序列符合公式 |
| **percent** | 投 k 点 → 读取 | 基线 0 → 恒为 0 ⚠️ | 同 D05/D07 的"零基线百分比"问题，必须澄清 |

**边界/坑**：
- 原版该属性同样有 `RangedAttribute` 上限（原版护甲韧性区间上限为 **20.0**）。超限被钳制必须被设计承认。
- 韧性对小伤害几乎无感、对大伤害显著。**测试必须用大伤害（≥40）**，否则观测不到差异而误判"未生效"。
- 单位误解：原版韧性的收益是 `toughness/4` 进入公式，1 点韧性 ≈ 0.25 点"抗削减"。设计若声称"1 点韧性 = 1 点护甲"是错的。

---

### 5.9 D09 — `damage_reduction` 基础伤害减免

- **期望载体**：自定义。最可能是 `LivingIncomingDamageEvent#setAmount` 或 `LivingDamageEvent.Pre`。
- **⚠️ 语义歧义（§5.0.3 第 3 条）**：flat 到底是"百分点"还是"减免固定点数"，**必须先由 architect 定死**。

- **独立观测**：**通道 C（唯一可靠手段）**
```
H0 = /data get entity @s Health
/damage @s 100 minecraft:mob_attack
H1 = /data get entity @s Health
实际承伤 = H0 - H1
减免率 = 1 - 实际承伤 / 100
```

| 模式 | 步骤 | 期望观测值 | 判定标准 |
|---|---|---|---|
| **flat** | 投 k 点 → 用 **伤害量 100 和 10 两组**分别测 | 语义 α：减免率 = k·u_f；语义 β：减免量 = k·u_f（与伤害量无关） | 用"两档伤害"**区分 α/β**：若减免量随伤害量等比变化 → α；若减免量恒定 → β |
| **percent** | 投 k 点 → 测 | 减免率 = 基线减免率 × (1+…) | 与基线对比 |

**必须覆盖的伤害类型（遍历 `DamageTypeTags`）**：

| 伤害类型 | 是否应被减免 | 验证 |
|---|---|---|
| `minecraft:mob_attack` / `player_attack` | ✅ 应 | 核心用例 |
| `minecraft:fall` 摔落 | 设计声明 | 测有无 |
| `minecraft:explosion` 爆炸 | ✅ 应 | — |
| `minecraft:on_fire` / `lava` | 设计声明 | — |
| `minecraft:magic` 药水伤害 | 设计声明 | — |
| `minecraft:starve` 饥饿 | 设计声明 | — |
| `minecraft:out_of_world` 虚空 | ✅ **应绕过** | 否则掉虚空不死 = 卡死 |
| `minecraft:generic_kill`（`/kill`） | ✅ **必须绕过** | **`/kill @s` 必须仍然生效**，否则管理员/自动化无法处置玩家 |
| `minecraft:falling_block` | 设计声明 | — |

> **`/kill` 必须仍能杀死玩家** —— 这是硬性验收项，不是可选项。

**边界/坑（本维度是"作弊面"最集中的地方）**：
1. **减免 100% = 无敌**。必须给出**软上限**（如 90%）或明确"允许无敌"并说明后果。测试：把减免堆到设计上限再超投，检查是否出现"永不死亡"。
2. **除零 / NaN**：减免公式若写成 `dmg * (1 - r)`，`r` 为 NaN 时整个伤害变 NaN，玩家血条变 NaN 且**永远无法死亡**（NaN 比较恒 false）。必须测：投负数点、投 `Integer.MAX_VALUE` 点，检查是否出现 NaN。
3. **绕过无敌帧**：`invulnerableTime` 与减免的交互。
4. **创造模式**：不应受影响。
5. **与 D05 护甲的乘算顺序**：先护甲后减免 vs 先减免后护甲结果不同。需设计声明并给出组合测试。
6. **不应对"伤害来源是自己"生效**（如自杀、摔落）——除非设计如此。

---

### 5.10 D10 — `crit_chance` 暴击率

- **期望载体**：自定义。最可能 `CriticalHitEvent` 或 `Player#attack` 的后处理。原版**没有**暴击率属性。
- **独立观测难点**：暴击是**概率性**的，单次观测无意义，**必须统计**。

**测量协议（统计法，唯一可信）**：
```
1. 对高血量靶（rpg_probe，max_health=1e6）
2. 使暴击率 = 设计值 p（通过加点）
3. 玩家执行 N=200 次普通攻击（站在地面、不跳跃、不疾跑 → 排除原版跳跃/疾跑暴击）
4. 每次记录承伤；暴击 = 承伤 ≈ 非暴击承伤的 1.5 倍以上（或读 mod 的 DEBUG 日志）
5. 观测暴击次数 k；实测暴击率 p̂ = k/N
```
- **判定**：`|p̂ - p| ≤ 3·sqrt(p(1-p)/N)`（3σ，N=200 时约 ±0.09@50%）。超出 → FAIL。
- **N 的选取**：p=10% 时需 N≥400 才能分辨；建议 **N=400**，且必须做**基线对照**（p=0 时暴击次数应为 0）。

**自动化优先方案**：要求 mod 在 DEBUG 级别**每次暴击打一行日志**（如 `[rpgleveling] crit roll=0.42 chance=0.35 result=true dmg=…`）。verifier 用 `_verify/tools/parse_crit_log.ps1` 统计。这比人肉数伤害可靠得多。

| 模式 | 步骤 | 期望观测值 | 判定标准 |
|---|---|---|---|
| **flat** | 投 k 点 → 统计 N 次 | `p = k·u_f` | 实测在 3σ 内；且 p=0 时 0 次暴击 |
| **percent** | 投 k 点 → 统计 N 次 | 基线 p 的百分比放大 | 同上。**基线 p=0 时百分比恒为 0** —— 需确认基数是否含原版跳跃暴击 |

**边界/坑**：
1. **p > 100%**：必须钳制到 100%。测试：投到 150% → 统计应 100% 暴击，**且不出现"负的非暴击概率"导致每次都暴击 + 额外增幅**。
2. **p < 0%**：负数应钳制到 0。
3. **与原版暴击的关系**：跳跃暴击是原版 1.5×。mod 的 crit 与之叠加吗？同时命中时是 1.5×1.5=2.25 还是 1.5？**必须测**：边跳边打，统计伤害分布应为**双峰**（普通/暴击）还是**三峰**。
4. **客户端伪造**：暴击判定必须在服务端。客户端只能播粒子。测：如果客户端可以影响 roll，则存在作弊面（见 §7）。
5. **随机源**：不得使用 `Math.random()` 之外的不可复现源导致测试无法复现；建议使用可传 seed 的 `RandomSource`，或在 DEBUG 日志中打印 roll 值。

---

### 5.11 D11 — `crit_damage` 暴击伤害

- **期望载体**：自定义，通常与 D10 同一处。
- **独立观测**：需要**暴击必定发生**才能确定性测量。

**测量协议**：
```
步骤 A（基线）：洗点，crit_chance=0，攻击靶 10 次，记录承伤 D_base（非暴击）
步骤 B（暴击）：把 crit_chance 投到 100%（若非 100%，则用 D10 的统计法取"承伤高的一组"的中位数）
              攻击靶 10 次，记录承伤 D_crit
暴击倍率 = D_crit / D_base
判定：暴击倍率 = 设计值（原版 1.5 + mod 加成），±0.05
```
- 若无法通过加点把暴击率拉到 100%，则必须提供 `/rpg debug setcrit 1.0` 之类的测试钩子——**这是可测性的硬需求**。

| 模式 | 步骤 | 期望观测值 | 判定标准 |
|---|---|---|---|
| **flat** | 投 k 点 → 测 D_crit/D_base | `1.5 + k·u_f`（若 u_f 是倍率增量）或 `+k·u_f` 伤害 | 与公式匹配；**必须区分"乘算倍率"与"加算伤害"语义** |
| **percent** | 投 k 点 → 测 | `1.5 × (1+…)` | 与公式匹配 |

**边界/坑**：
1. **乘算顺序**：`(基础 + 攻击加成) × 暴击 × 减免` 还是 `基础 × 暴击 - 减免`？结果差很多。必须与 D01/D09 联合测。
2. **整数取整**：伤害取整发生几次？`(int)` 截断在乘算链中间会累计误差。测：小伤害（1.0）暴击后期望 1.5，实际可能被截断成 1。**必须用大伤害测倍率，用小伤害单独测取整规则**。
3. **NaN/溢出**：倍率极大时 float 溢出 → Infinity → 伤害变 Infinity → 目标瞬间死亡且可能崩服。测上限。
4. **暴击是否影响生命偷取**（D12）：偷取按暴击前还是暴击后伤害计算？设计需明确并测试一致性。

---

### 5.12 D12 — `lifesteal` 生命偷取

- **期望载体**：自定义，通常在伤害结算后回血。
- **独立观测**：**通道 E（生命差分）**，且必须关闭一切其他回复源（见 D03 协议）。

**测量协议**：
```mcfunction
/gamerule naturalRegeneration false
/effect clear @s
/data merge entity @s {foodLevel:6,Health:6.0f}     # 低血量起点
# H0 = Health
# 对靶施加一次已知伤害：/damage @e[tag=rpg_probe,limit=1] 10 minecraft:player_attack by @s
# H1 = Health（同一 tick 后立即读，避免混入自然回复）
实际伤害 D = 靶承伤；偷取量 = H1 - H0
```

| 模式 | 步骤 | 期望观测值 | 判定标准 |
|---|---|---|---|
| **flat** | 投 k 点 → 上述协议 | `H1 - H0 = k·u_f`（固定 HP/次）| 精确匹配（±0.05）；**且与实际伤害 D 无关** |
| **percent** | 投 k 点 → 上述协议 | `H1 - H0 = D × 比例` | 用**两档伤害（10 和 50）**分别测：percent 模式下偷取量应随 D 等比变化；flat 模式下恒定 |

> **用"两档伤害"是区分 flat / percent 的判别性实验**，必做。

**边界与作弊面（本维度作弊风险最高）**：
1. **不得超过最大生命**：`H1 ≤ max_health`。否则出现"血量 30/20"或 NaN。
2. **不得从 0 伤害偷取**：对免疫目标、已死目标、创造模式目标攻击时不应回血。
3. **不得对"无敌实体"生效刷血**：`/summon minecraft:armor_stand`、无敌帧内的实体、`invulnerable` 标签实体、已死亡实体 —— 全部不得回血。**测法**：召唤盔甲架，攻击 100 次，血量不应上升。
4. **不得对"伤害被取消"的情况生效**：盾牌格挡、`LivingIncomingDamageEvent` 被 cancel、伤害为 0。
5. **不得触发治疗环**：偷取 → `LivingHealEvent` → 又被某逻辑当伤害 → 再偷取。测：连续攻击 1000 次，血量不应发散。
6. **多人**：A 攻击 B（PvP）时是否偷取？设计需明确。若允许，需检查不会导致双方都回血。
7. **服务端权威**：偷取必须由服务端结算，客户端不得影响。
8. **死亡瞬间**：致死一击是否偷取（尸体回血）？设计需明确，且不得导致"死后仍存活"的矛盾状态。

---

### 5.13 D13 — `drop_rate` 掉落倍率

- **期望载体**：自定义，通常 `LivingDropsEvent` / `BlockDropsEvent`（NeoForge 1.21.1 为 `BlockDropsEvent`）。
- **独立观测**：**掉落物计数**（通道 E）。

**测量协议（掉落本身随机，必须统计）**：
```mcfunction
/gamerule doMobLoot true
/gamerule doTileDrops true
# 清场
/kill @e[type=item]
# 基线：不投点，击杀 N=100 只牛（/summon 循环 + 玩家击杀）
# 记录：/execute if entity @e[type=item] ... 或用 /data get entity @e[type=item] Item 统计
# 加点：投 k 点，重复 N=100
# 倍率 = 加点后总掉落数 / 基线总掉落数
```
- **判定**：倍率 = 设计值（±10%，N=100 的统计噪声）。
- **更干净的确定性方案**：选用**掉落恒定**的方块——`minecraft:stone`（正确工具恒掉 1 个圆石）、`minecraft:coal_ore`（恒掉 1 个煤）。此时 N 可降到 10，甚至单次即可判定（1 → ceil(1×m)）。**推荐优先用方块掉落做精确判定，怪物掉落做补充验证。**

| 模式 | 步骤 | 期望观测值 | 判定标准 |
|---|---|---|---|
| **flat** | 投 k 点 → 挖 10 个石头，计掉落 | 每块 `1 + k·u_f` 个 | **整数**；必须明确取整规则（floor/ceil/round）与"不足 1 个"时的概率补偿 |
| **percent** | 投 k 点 → 挖 10 个石头 | 每块 `1 × (1+…)` | 同上；小数部分的处理必须由设计明确（累积概率补偿 vs 直接截断） |

**边界/坑（掉落倍率是破坏性最强的维度）**：
1. **超过堆叠上限**：一次掉落 6400 个物品**必须拆成 100 个 64 的堆叠**，否则会生成非法 ItemStack（数量 > 64）→ 客户端崩溃/物品复制漏洞。**必测：倍率拉到 1000，挖一块石头。**
2. **实体数量爆炸**：一次生成上千 ItemEntity 会严重卡服。必须设上限或合并策略。测：TPS 与实体数。
3. **不得作用于玩家死亡掉落**（`keepInventory=false` 时玩家掉落不该被放大成 100 倍，否则灾难）。**必测。**
4. **不得作用于 `/give`**、`/loot` 指令。
5. **必须尊重 gamerule**：`doTileDrops=false` 时倍率不应复活掉落；`doMobLoot=false` 同理。**必测。**
6. **不得造成事件递归**：`LivingDropsEvent` 中再次触发掉落事件 → 栈溢出/无限循环。测：连挖 200 个方块，看日志有无 `StackOverflowError`。
7. **与时运/精准采集的关系**：倍率应在时运**之后**只乘一次。测：时运 III 镐 + 加成，实际应为 `(1~4) × m` 而非 `(1~4) × m²`。**必测。**
8. **无掉落方块**：玻璃（无精准采集）、`minecraft:barrier` —— 倍率不应凭空造出物品。
9. **不能复制"非物品"**：经验球、掉落物以外的效果（如生物生成）不在此列。
10. **与 stellersword 共存**：若 stellersword 也改掉落，需检查是否有事件顺序/倍率叠加问题（见 §8）。

---

### 5.14 26 条线判定汇总表

> 每条线的判定列必须由 verifier 逐条填写 PASS / FAIL / BLOCKED（BLOCKED = 缺少设计定义或缺少测试钩子）。

| # | 维度 | 载体 | flat 判定 | percent 判定 | 备注 |
|---|---|---|---|:--:|---|
| 1 | attack_damage | `generic.attack_damage` | | | 需区分属性型/事件型 |
| 2 | max_health | `generic.max_health` | | | **重生后必须保留** |
| 3 | health_regen | 自定义 | | | 需 tick 精确计时 |
| 4 | movement_speed | `generic.movement_speed` | | | 验证 Operation 类型 |
| 5 | armor | `generic.armor` | | | **原版上限 20 点** |
| 6 | xp_gain | `XpChange`/`LivingExperienceDropEvent` | | | **防双倍叠加** |
| 7 | knockback_resistance | `generic.knockback_resistance` | | | **原版钳制 [0,1]** |
| 8 | armor_toughness | `generic.armor_toughness` | | | 需大伤害才可观测 |
| 9 | damage_reduction | 自定义 | | | **语义待定；/kill 必须仍生效** |
| 10 | crit_chance | 自定义 | | | **统计法 N≥400** |
| 11 | crit_damage | 自定义 | | | 需强制 100% 暴击钩子 |
| 12 | lifesteal | 自定义 | | | **两档伤害区分 flat/percent** |
| 13 | drop_rate | `LivingDropsEvent`/`BlockDropsEvent` | | | **堆叠上限/递归/时运顺序** |

---

## 6. 自动化验证资产（需随工程一起维护）

> 这些资产由 **verify 角色拥有**，写在 `_verify/` 下，**不进入正式发布 jar**。

### 6.1 脚本清单

| 脚本 | 层 | 作用 | 调用 | 状态 |
|---|---|---|---|---|
| `_verify/tools/check_jar.ps1` | L0 | jar 结构、mods.toml 占位符、重复 class、打包根目录 | `-Jar <path> -ModId <id>` | ✅ **已实现并冒烟通过** |
| `_verify/tools/check_lang.py` | L1 | lang 键完整性（S-09~S-12），区分静态/动态键 | `--assets <dir> --modid <id> --src <dir>` | ✅ **已实现并冒烟通过** |
| `_verify/tools/check_dist.py` | L1 | 客户端类混用扫描（S-08a），自带 class 常量池解析 | `--jar <path>` 或 `--classes <dir>` | ✅ **已实现并冒烟通过** |
| `_verify/tools/check_static.py` | L1 | 注册表/事件订阅扫描（S-01~S-07） | `--src src/main/java` | ⬜ 待实现 |
| `_verify/tools/parse_crit_log.ps1` | L3 | 暴击率统计（D10） | `-Log <log> -Chance <p>` | ⬜ 待实现 |
| `_verify/tools/run_all.ps1` | 全部 | 一键跑 L0+L1 | — | ⬜ 待实现 |

**冒烟测试记录**（对参考工程 `infinitecore-1.0.0.jar` 运行，证明脚本本身可用）：

| 脚本 | 结果 |
|---|---|
| `check_jar.ps1` | **13 checks, 0 FAIL**（B-02/03/03b/03c/04/05a/05b/05c/06/07/08/09/10 全 PASS） |
| `check_lang.py` | **8 checks, 0 FAIL**（另有 1 条 S-09d WARN：`"block." + id` 之类的动态键，需人工复核） |
| `check_dist.py` | 扫出 3 个引用客户端类型的类：2 个在 `com/infinite/core/client/`（约定内），**1 个 `com/infinite/core/registry/ModClient` 不在 client 包内 → REVIEW**。这正好印证 S-08a 的价值：该类若不写 `@EventBusSubscriber(value = Dist.CLIENT)` 就会在专用服务端崩溃。 |

> 三个脚本均为**自包含**（check_dist.py 自己解析 JVM 常量池，无第三方依赖），用捆绑 Python 运行即可：
> `$env:PYTHONIOENCODING="utf-8"; python _verify\tools\check_dist.py --jar <jar>`

### 6.2 游戏内测试数据包 `_verify/datapack/rpg_verify/`

提供**可复现**的 mcfunction 用例，避免手工敲命令出错：
```
rpg_verify/
  pack.mcmeta
  data/rpg_verify/function/
    setup_probe.mcfunction      # §5.0.2 标准靶 + gamerule
    d01_attack_flat.mcfunction
    d01_attack_percent.mcfunction
    ...
    d13_drop_rate.mcfunction
    teardown.mcfunction          # 清场、恢复 gamerule
```
用法：把 `_verify/datapack/rpg_verify` 放进存档的 `datapacks/`，然后 `/reload` + `/function rpg_verify:setup_probe`。
> 每个 function 内部用 `/tellraw` 输出带用例 ID 的结构化结果行，便于 `Select-String` 断言。

### 6.3 GameTest 套件（**最高优先级自动化手段**）

NeoForge 1.21.1 自带 GameTest 框架；参考工程 `build.gradle` 已配置：
```groovy
runs {
    gameTestServer {
        systemProperty 'forge.enabledGameTestNamespaces', project.mod_id
    }
}
```
运行：`.\gradlew.bat runGameTestServer --console=plain`
报告：`build/gametest-report/*.xml`（JUnit XML，可被 CI 解析）

**可在无头环境自动断言的用例**（覆盖 26 条线中的大部分）：

| 用例 | 断言内容 | 覆盖 |
|---|---|---|
| `AttackDamageFlatTest` | 给 FakePlayer 投 k 点 → `getAttributeValue(ATTACK_DAMAGE)` == 期望 | D01 flat |
| `AttackDamagePercentTest` | 同上，百分比约定 | D01 percent |
| `MaxHealthRespawnTest` | 投点 → `player.die()` → 重生 → 值不变 | D02 关键 |
| `XpMultiplierSingleApplicationTest` | 击杀实体后经验球总值与玩家实得点数**只乘一次** | D06 关键 |
| `DamageReductionCapTest` | 减免投到极限 → `hurt()` 仍能致死 | D09 关键 |
| `KillCommandStillWorksTest` | `player.kill()` 必死 | D09 关键 |
| `DropRateStackLimitTest` | 倍率 1000 时掉落 ItemStack `count <= 64` | D13 关键 |
| `RespecLeavesNoModifierTest` | 洗点后 `Attributes` modifier 列表为空 | 全维度 |
| `EquipmentBasisTest` | 裸装 vs 全套下界合金，同点数下 percent 贡献符合 `base+装备+flat` 基数（§1.6.1） | D05/D07/D08 percent |
| `PlayerBaseNotAttributeDefaultTest` | 玩家 `ATTACK_DAMAGE` base == **1.0**（非属性默认 2.0）、`MOVEMENT_SPEED` base == **0.1**（非 0.7） | D01/D04 基数 |
| `ForceCritOverrideTest` | `/rpg debug forcecrit` 后 D2/D1 == 2.5/1.5 == 1.6667 | D11 |
| `NegativeDamageGuardTest` | `armor base set 200` 后 `/damage` 伤害不为负（原版守卫） | §1.6.5 |
| `DoubleApplyOnReloadTest` | 反复登录/重载 → 数值不叠加 | 全维度 |
| `NegativePointsRejectedTest` | 负点数/超大点数被拒 | §7 |

样例骨架：
```java
@GameTestHolder("rpgleveling")
public class StatGameTests {
    @GameTest(template = "rpgleveling:empty")
    public static void maxHealthFlat(GameTestHelper helper) {
        Player p = helper.makeMockPlayer(GameType.SURVIVAL);
        // ... 投点
        double actual = p.getAttributeValue(Attributes.MAX_HEALTH);
        helper.assertTrue(Math.abs(actual - 25.0) < 1e-3, "expected 25.0 but got " + actual);
        helper.succeed();
    }
}
```
> `template = "rpgleveling:empty"` 需要一个空结构 `data/rpgleveling/structure/empty.nbt`，由 verify 提供。

**准入要求**：只要 mod 提供了 §2 所述的 `/rpg` 测试指令面，上述用例就能把 26 条线的判定从"人肉点击"降到"一条命令"。**这是本方案能否全自动化的唯一前提。**

---

## 7. L4 — 边界与作弊面

> 这一节是**独立于功能对错**的破坏性测试。每条都必须给出明确判定。

### 7.1 等级与经验

| ID | 用例 | 步骤 | 判定 |
|---|---|---|---|
| E-01 | 无等级上限 | `/rpg debug setlevel 1000000` 或 `/xp add @s 1000000 levels` | 不崩、不卡；`/rpg stats` 回显正确 |
| E-02 | 经验曲线在 200 级切换 | 采样 L=198..203 每级所需经验，对照 §D06.1 的精确值 | 线性段 `100+20(L-1)`、对数段 `4080(1+0.15·ln(L-199))`；**L=200→201 处无跳变、无负增量**；L≤200 时不得对 `ln()` 传非正数 |
| E-02b | 点数发放速度 | 每升 1 级 | 恰好 +1 点；连升多级时点数累计正确（用 `/xp add @s 100 levels` 一次跨多级测试） |
| E-03 | 曲线单调性 | 采样 L=1..10000 | `xpNeeded(L+1) >= xpNeeded(L)` 恒成立；**对数段不得出现"升级所需经验反而减少"** |
| E-04 | 总经验 int 溢出 | 把总经验推到接近 `Integer.MAX_VALUE` (2147483647) | 不得超过或回绕成负数；必须用 `long` 或在接近上限时钳制 |
| E-05 | 高等级初始化耗时 | L=1e6 时进服/升级 | 单 tick 不得耗时 > 50ms；曲线计算不得是 O(L) 循环 |

### 7.2 点数超发与负数

| ID | 用例 | 步骤 | 判定 |
|---|---|---|---|
| P-01 | 花费超过可用点数 | 有 1 点，投 10 点 | **拒绝**，点数不变，返回错误提示 |
| P-02 | 负数投点 | `/rpg debug addpoints -100` 或投 `-1` 点 | 拒绝；等级/点数不得增加；属性不得下降 |
| P-03 | 超大投点 | 投 `Integer.MAX_VALUE` 点 | 拒绝或安全钳制；**不得溢出成负点数**（负点数 + 后续扣除 = 无限刷点） |
| P-04 | 点数计数溢出 | 反复投点使总量逼近 int 上限 | 使用 `long` 或钳制；不得回绕 |
| P-05 | 浮点精度 | 投 10000 点 percent | 累加不得因 `float` 精度丢失而偏离公式 > 容差 |
| P-06 | 并发投点 | 同一 tick 连发多个投点包 | **每次必须重新校验余额**（先校验后扣减的 TOCTOU 竞态会导致超发） |

### 7.3 客户端伪造（**服务端权威性核心**）

| ID | 用例 | 方法 | 判定 |
|---|---|---|---|
| C-01 | 伪造投点包 | 用修改过的客户端 / 网络注入工具，发送"投 100 点"的 payload，而服务端记录只有 1 点 | **服务端拒绝**；点数与属性均不变化；日志记录可疑请求 |
| C-02 | 伪造等级包 | 发送"设置等级 9999"的 payload | 服务端**不采信客户端等级**，以服务端数据为准 |
| C-03 | 重放攻击 | 录制合法投点包，重复发送 N 次 | 只有余额允许的次数生效；**不得因重放而超发** |
| C-04 | 伪造 UUID/目标 | payload 中指定其他玩家的 UUID | 服务端必须从连接上下文取玩家身份，**忽略 payload 中的身份字段** |
| C-05 | 未登录时发包 | 在 configuration 阶段就发 play 阶段包 | 被正确丢弃，不抛异常刷屏 |
| C-06 | 恶意大包 | 发送超长/畸形的 payload 字段 | 不崩溃、不 OOM；被 `StreamCodec` 拒绝并断开 |
| C-07 | 客户端改属性显示 | 客户端侧改显示数值 | **只影响本地显示**，服务端 `/attribute` 读值不变 |
| C-08 | 单人作弊→多人 | 单人存档里改的数据带到服务器 | 无影响（服务器用服务端存档） |

**可执行的验证方式（不需要写破解客户端）**：
1. **代码审计**：检查每个 `CustomPacketPayload` 的 handler 是否**只从 `ctx.player()` 取身份**，是否**重新校验**所有数值。
2. **单元测试**：直接构造 payload 对象，用 `IPayloadContext` 的 mock 调用 handler，传入越界数值，断言状态不变。→ 放进 §6.3 的 GameTest 套件。
3. **双客户端法**：开两个客户端，A 用正常 UI 投点，B 观察 A 的属性——验证是服务端广播而非 A 自报。

> **判定基线：任何由客户端发送的数值都必须被视为"不可信输入"**，等价于命令参数校验。缺少校验 = FAIL。

### 7.4 洗点

| ID | 用例 | 步骤 | 判定 |
|---|---|---|---|
| R-01 | 洗点归零 | 投满 26 条线 → `/rpg respec` | `/data get entity @s Attributes` 中 **mod 添加的 modifier 全部消失**，回到原版基线（±0.001） |
| R-02 | 点数返还 | 洗点前后 | 可用点数 = 洗点前可用 + 已投总数；**不多不少** |
| R-03 | 重复洗点 | 连续 `/rpg respec` ×5 | 幂等；不报错；点数不被重复返还 |
| R-04 | 空洗点 | 0 点已投时洗点 | 不报错，无副作用 |
| R-05 | 高压下洗点 | 战斗中/受伤时洗点 | 不崩；最大生命下降时当前生命被正确钳制（不得出现 `Health > max_health`） |
| R-06 | 洗点即时生效 | 洗点后立即读属性 | 无需重登/重载；客户端显示同步更新 |
| R-07 | 洗点后重登 | 洗点 → 退出 → 进入 | **不得"复活"已洗掉的加点**（说明洗点没落盘） |
| R-08 | 洗点与经验 | `/rpg respec` | 等级与经验**不受影响**（只洗加点） |

### 7.5 死亡与持久化

| ID | 用例 | 步骤 | 判定 |
|---|---|---|---|
| D-01 | 死亡无惩罚 | 记录 L/经验/点数 → `/kill @s` → 重生 | 三者**完全不变** |
| D-02 | 重生后属性保留 | 同上，读 `/attribute` | 26 条线的属性值不变（**最高危项**） |
| D-03 | 重生后无重复叠加 | 死亡重生 ×5，读 `/data get entity @s Attributes` | modifier **数量不增长**（每次重生加一次 = 数值越死越高） |
| D-04 | keepInventory 无关 | `/gamerule keepInventory` 两种取值各测一次 | 数据都保留（mod 数据不应挂在物品上） |
| D-05 | 跨维度 | 主世界→下界→末地→主世界 | 数据不变（**不得按维度分库存放**） |
| D-06 | 服务器重启 | 停服 → 起服 | 数据从存档恢复；对照 S-16 记录的文件（如 `world/data/neoforge_data_attachments.dat`）确认已写入 |
| D-07 | 强制杀进程 | `taskkill` 杀服务端（模拟崩溃） | 最多丢失最后一次存档周期（自动保存间隔）内的数据；**不得整个玩家数据损坏** |
| D-08 | 新玩家初始化 | 全新玩家进服 | 等级 0、点数 0、无 modifier；不报 NPE |
| D-09 | 硬核死亡 | 硬核模式死亡 | 世界删除，数据随之消失（预期行为，不算 bug） |
| D-10 | 离线玩家 | 玩家离线时被指令改数据 | 下次上线正确加载；不覆盖为默认值 |

### 7.6 多人服务器

| ID | 用例 | 判定 |
|---|---|---|
| M-01 | 数据隔离 | A 的加点不影响 B 的属性（`/attribute B` 独立） |
| M-02 | 各自结算 | A 的伤害加成只在 A 攻击时生效，B 攻击不受影响 |
| M-03 | 经验隔离 | A 获得经验不影响 B |
| M-04 | 广播正确性 | A 的加点同步到所有能看到 A 的客户端（外观/粒子），但**数值以服务端为准** |
| M-05 | 并发投点 | A、B 同时投点，互不干扰 |
| M-06 | 指令权限 | `/rpg debug *` 仅 OP 可用；普通玩家执行被拒 |
| M-07 | 掉线重连 | 战斗中掉线重连，数据一致、无重复 modifier |
| M-08 | 玩家名重复/大小写 | 数据按 UUID 而非用户名索引 |

### 7.7 数学稳定性（发散边界）

> 用户已拍板"单维度数值无上限"，因此**必须**证明体系在极端值下不发散。

| ID | 用例 | 判定 |
|---|---|---|
| N-01 | 减伤 100% | 若设计允许：明确声明；`/damage @s 1000` 后血量 > 0（无敌）——**但 `/kill @s` 必须仍能杀死** |
| N-02 | 减伤 > 100% | 不得出现**负伤害**（负伤害 = 治疗！会让玩家被攻击时回血，严重漏洞） |
| N-03 | NaN 传播 | 任何维度出现 NaN/Infinity → 该玩家**可能永远无法死亡**（NaN 比较恒 false）。测：投极端点数后 `/damage @s 1000`，血量必须正常减少 |
| N-04 | 属性值 Infinity | `/attribute` 读数不得为 `Infinity`/NaN |
| N-05 | 移速极小值 | 若允许负数加点导致移速 ≈ 0，玩家不应被永久卡住（提供下限钳制） |
| N-06 | 移速极大值 | 不得穿墙、不得被服务端踢出、不得导致客户端移动预测崩溃 |
| N-07 | 暴击率 > 100% | 钳制到 1.0；100% 时**每次**都暴击且不叠加额外倍率 |
| N-08 | 掉落倍率极大 | ItemStack count ≤ 64；实体总数有上限；不卡服 |
| N-09 | 生命恢复极大 | 不超过 max_health；不出现 `Health` 为 NaN |
| N-10 | 攻击伤害极大 | float 溢出为 Infinity 时不得一击秒杀创造模式/无敌实体，不得崩服 |
| N-11 | 除零 | 公式中所有分母（如 `maxHealth`、`baseValue`）在极端情况下不为 0 |

**统一的收敛验收标准**：对每个维度，把该维度投到"设计上限 × 10"，然后：
1. 服务器不崩、不卡（TPS ≥ 19）
2. 无非有限值（NaN/Infinity）
3. `/kill @s` 仍能杀死玩家
4. 玩家不能被永久卡死（可移动、可交互、可退出重进）

*以上 4 条任一不满足 → 该维度判 FAIL，必须加软上限。*

### 7.8 性能

| ID | 用例 | 判定 |
|---|---|---|
| F-01 | 26 条线全满 | TPS ≥ 19（`/neoforge tps`）；无 tick 超时警告 |
| F-02 | 10 名玩家全满 | 服务端 MSPT < 50 |
| F-03 | 不掉落实体爆炸 | 掉落倍率满点时连挖 100 方块，实体数 < 1000 |
| F-04 | 无每 tick 重算 | 属性应在投点/装备变化时重算，**而非每 tick**（用 Spark/JFR 或代码审计确认） |
| F-05 | 经验加成无每 tick 开销 | 经验结算只在事件触发时 |

---

## 8. L5 — 回归与兼容

### 8.1 与 stellersword 共存

> ⚠️ **命名澄清**：用户说的 **stellersword** 就是参考工程目录 `C:\Users\俞皓天\Desktop\InfiniteCore-Mod`。
> 其 `settings.gradle` 的 `rootProject.name = 'stellersword'`，但**产物 jar 与 modId 是 `infinitecore`**：`build/libs/infinitecore-1.0.0.jar`。
> 找 jar / 查日志时请认 `infinitecore`，不要找 `stellersword.jar`。

| ID | 用例 | 步骤 | 判定 |
|---|---|---|---|
| X-01 | 双 mod 启动 | `mods/` 放 `rpgleveling-*.jar` + `infinitecore-1.0.0.jar`，启动客户端与服务端 | 正常进入主界面 / 服务端 `Done`；无 `ModResolutionException` |
| X-02 | 无注册表冲突 | 启动日志 | 无 `Duplicate registration` / `already registered` |
| X-03 | 无 mixin 冲突 | 启动日志 | 无 `Mixin apply failed` / `Mixin transformation failed` |
| X-04 | 无配置文件冲突 | `config/` 目录 | 两个 mod 各自生成配置，文件名不撞（stellersword 用 `infinitecore-common.toml` 类命名） |
| X-05 | NeoForge 版本范围兼容 | 读两个 jar 的 `neoforge.mods.toml` | 两个范围都与实际 NeoForge 版本（21.1.248）相容 |
| X-06 | 事件处理顺序 | 两者都监听伤害类事件 | 无异常；伤害结果符合各自声明的叠加规则；**顺序变化不应导致数值跳变或异常** |
| X-07 | 功能各自可用 | stellersword 的发电机开始工作 + rpgleveling 的加点生效 | 两者同时正常 |
| X-08 | 卸载 rpgleveling | 只留 stellersword | stellersword 正常（rpgleveling 不得污染存档/注册表） |
| X-09 | 卸载 stellersword | 只留 rpgleveling | rpgleveling 正常；玩家数据不丢 |
| X-10 | 存档前后兼容 | 旧存档（无 rpgleveling）加载 → 加 mod → 再移除 | 无崩溃；数据按设计处理 |

### 8.2 原版机制回归

| ID | 用例 | 判定 |
|---|---|---|
| V-01 | 成就/进度正常 | 击杀、挖矿等进度仍能完成 |
| V-02 | 附魔正常 | 锋利/保护等附魔效果不被破坏 |
| V-03 | 药水效果正常 | 力量/抗性提升等仍生效，不与属性加点重复计算错误 |
| V-04 | 死亡不掉落 | 玩家死亡掉落正常 |
| V-05 | 村民交易/合成 | 不受掉落倍率影响 |
| V-06 | 刷怪/生成 | 不受影响 |
| V-07 | 单人/局域网/专服 三种模式 | 都能正常启动与结算 |

---

## 9. L6 — 必须人工在游戏内确认的检查清单

> 以下项目**无法**由脚本可靠判定（涉及渲染、手感、UI 布局、主观体验）。
> 每条都写成**可照做**的步骤；执行者勾选并附截图。

### 9.1 交互与 UI

| # | 检查项 | 操作步骤 | 通过标准 | ✓ |
|---|---|---|---|---|
| H-01 | K 键打开界面 | 进入世界，按 **K** | 加点界面正常打开，无延迟/闪烁 | ☐ |
| H-02 | 界面可关闭 | 按 **Esc** / 再按 **K** | 正常关闭，鼠标解锁，不死锁 | ☐ |
| H-03 | 界面不可在错误状态打开 | 打开背包时按 K；死亡界面按 K；聊天框聚焦时按 K | 不崩溃、不叠加两个界面、不丢失输入焦点 | ☐ |
| H-04 | 数值显示正确 | 打开界面，与 `/rpg stats` 的控制台输出对照 | 界面显示的等级/点数/各维度数值与指令一致 | ☐ |
| H-05 | 加/减按钮 | 点击各维度的 flat/percent 加点按钮 | 数值即时更新；点数即时扣减；按钮在点数不足时禁用/提示 | ☐ |
| H-06 | 界面缩放适配 | GUI 缩放设为 1/2/3/4（自动）分别查看 | 布局不重叠、文字不溢出 | ☐ |
| H-07 | 语言切换 | 切到简体中文再切回英文 | 所有文字都有翻译，**不出现 `rpgleveling.xxx` 原始键** | ☐ |
| H-08 | 洗点按钮 | 点击界面中的洗点 | 有二次确认（或明确提示）；执行后数值归零 | ☐ |
| H-09 | 多人下界面 | 两个客户端同时开界面 | 各自显示自己的数据；一方加点后另一方看到同步 | ☐ |

### 9.2 手感与视觉反馈

| # | 检查项 | 操作步骤 | 通过标准 | ✓ |
|---|---|---|---|---|
| H-10 | 移速手感 | 分别投 0 / 满 移速，在同一条直道上跑 10 秒 | 明显更快但**不失控**（不滑、不穿墙、不被踢） | ☐ |
| H-11 | 生命上限视觉 | 投满 max_health | 血条心数正确增加；血量数字与 `/attribute` 一致；**不超出屏幕** | ☐ |
| H-12 | 受伤反馈 | 用 mob 攻击自己 | 血条下降幅度与 `/damage` 计算一致 | ☐ |
| H-13 | 暴击粒子 | 投满暴击率后攻击 | 出现暴击粒子/音效；视觉与伤害数值一致 | ☐ |
| H-14 | 生命偷取反馈 | 投满偷取后攻击 | 血条上升平滑，无闪烁/抖动 | ☐ |
| H-15 | 升级提示 | 击杀怪物升级 | 有升级提示音/文字（若设计要求）；等级数字即时更新 | ☐ |
| H-16 | 击退手感 | 投满击退抗性后被僵尸打 | 几乎不被推动 | ☐ |
| H-17 | 掉落视觉 | 满掉落倍率挖一块石头 | 掉落物**成堆**出现且可正常拾取；不卡顿、不消失 | ☐ |
| H-18 | 客户端预测无回弹 | 高移速下移动 | 不出现位置回拉/橡皮筋 | ☐ |

### 9.3 多人主观项

| # | 检查项 | 通过标准 | ✓ |
|---|---|---|---|
| H-19 | 双人 PvP | A 攻击 B，偷取/暴击/减伤按设计生效，无异常 | ☐ |
| H-20 | 旁观者视角 | 旁观者看到的效果与当事者一致 | ☐ |
| H-21 | 20 人压力 | 20 个账号（可用假人）在线，服不卡 | ☐ |

### 9.4 人工执行记录模板

```
执行人：            日期：            版本/commit：
MC 版本：1.21.1   NeoForge：21.1.248   其他 mod：
存档/服务器：       
截图目录：_verify/evidence/manual/<日期>/
未通过项与现象描述：
```

---

## 10. 证据规范、执行顺序与最终判定

### 10.1 证据目录结构

```
_verify/
  CANARY_RESULT.md              # 工具链去风险结论（§1.5）
  VERIFICATION_REPORT.md        # 最终验证报告（verifier 产出）
  evidence/
    L0_build/                   # 构建日志、jar 结构输出
    L1_static/                  # 各脚本输出
    L2_gametest/                # JUnit XML 报告
    L3_matrix/D01..D13/         # 每个维度的命令回显、读数、计算过程
    L4_boundary/                # 边界用例证据
    L5_regression/              # 双 mod 共存日志
    L6_manual/                  # 截图 + 手工记录
  tools/                        # §6.1 脚本
  datapack/rpg_verify/          # §6.2 数据包
  logs/                         # 构建与服务端日志
```

**每个 FAIL 必须包含**：复现步骤、原始输出、期望值、实际值、最小复现（能缩到一条命令最好）。

### 10.2 执行顺序与门槛

```
L0 构建  ──FAIL──▶ 停止，报告 code 角色
   │ PASS
L1 静态  ──FAIL──▶ 若 S-08（客户端类混用）失败 → 停止（服务端必崩）；其余可并行修
   │ PASS
L2 GameTest ──▶ 覆盖大部分行为断言
   │
L3 行为矩阵（13×2=26 条线）──▶ 逐条 PASS/FAIL/BLOCKED
   │
L4 边界与作弊面 ──▶ 任一条 N-xx 失败 → 阻塞发布
   │
L5 回归（双 mod 共存）
   │
L6 人工清单（交用户执行）
   │
最终判定
```

### 10.3 最终判定规则

**发布通过（PASS）必须同时满足**：
1. L0 全 PASS（B-01 ~ B-11）
2. L1 全 PASS（S-01 ~ S-16），其中 **S-08b（DedicatedServer 实际启动）必须实测通过**
3. L3：26 条线全部 PASS，**无 BLOCKED**
4. L4：**E/P/C/R/D/M/N/F 各组无 FAIL**；特别是
   - `/kill` 仍能杀死玩家
   - 死亡重生后属性与数据不丢、不重复叠加
   - 客户端伪造包被拒
   - 洗点无残留
   - 无 NaN/Infinity 导致的永久不死
5. L5：双 mod 共存启动正常
6. L6：人工清单全部勾选（或未勾选项有明确书面豁免）

**任一项 FAIL → 整体 FAIL**，并在报告中给出：最小复现 + 期望/实际 + 影响面 + 建议修法。

### 10.4 当前已知的 BLOCKED 风险（需 architect/code 补齐后才能判定）

| # | 阻塞项 | 影响 | 需要的动作 |
|---|---|---|---|
| BL-1 | `percent` 的叠加公式未定义（线性/复利） | D01~D13 全部 percent 线无法判定 | architect 在 DESIGN.md 写死公式 |
| BL-2 | flat 与 percent 同时投时的运算顺序未定义 | 26 条线的组合场景无法判定 | 同上 |
| BL-3 | `damage_reduction` 的 flat 语义（百分点 vs 固定点数）未定义 | D09 无法判定 | architect 二选一 |
| ~~BL-4~~ | ~~percent 基数未定义~~ | **已裁决（verifier 复核后部分撤回）** | 见下方 §A.1 |
| **BL-9** | **TOTAL 模式的 `sup` 证明忽略了「装备 ADD_VALUE」，导致静默丢点与击退免疫被突破** | D03/D05/D07/D09 的满分构建下失效；`knockback_resistance` 的「永远保留 10% 击退」保证**为假** | **architect 必须复核并二选一**（降低 cap 或改 DESIGN_BASELINE）|
| BL-5 | 缺少 `/rpg` 测试指令面（stats / debug addpoints / debug setlevel） | 26 条线只能靠人肉点击，无法形成可信证据链 | code 提供测试指令（仅 OP） |
| BL-6 | 各维度的**渐近公式与取整规则**未写死（lead 已定调"收益递减渐近收敛"，但未给具体公式） | N-01~N-11、D13 无法给出精确期望值 | architect 给出每个维度的收敛公式（形如 `Vmax-(Vmax-V0)e^(-k/τ)`）与取整规则 |
| BL-7 | 掉落倍率的**小数处理**与单次掉落上限策略未定义 | D13 的 flat/percent 判定标准不确定 | architect 明确 floor/ceil/概率补偿 + 单次不超过一组的拆分规则 |
| BL-8 | 非击杀经验的"首次行为"**粒度**未定义（按方块 ID？按物品 ID？按鱼类？） | F-04 无法判定 | architect 明确 ID 粒度与已解锁集合的存储形式（需有界） |

> **这些必须在 code 角色动手前由 architect 落实，否则验证无法开始。** 见 §5.0.3。

---

## 10.5 BL-9 详述：TOTAL 模式的 sup 证明为何不成立（**verifier 独立复核结论**）

### 10.5.1 缺陷

设计 §1.5 的安全性公式写作 `sup = (base + sup_flat) * (1 + sup_pct/100)`，**只代入了 `base`**。
但由 §1.6.1/§1.6.2（源码已核对），`ADD_MULTIPLIED_BASE` 的基数是 `base + Σ ADD_VALUE`，而**装备正是在 ADD_VALUE 里**。
⇒ 对 3 个「有装备来源」的维度，正确公式应为：

```
sup_real = (base + maxEquipment + sup_flat) * (1 + sup_pct/100)
```

代入整套**下界合金**（+20 护甲 / +12 韧性 / +0.4 击退抗性，见 §1.6.6）：

| 维度 | 设计 §1.5 的 sup（裸装） | `sup_real`（全套下界合金） | HARD_MAX | 后果 |
|---|---|---|---|---|
| `armor` | (0 + 15) × 2 = **30** | (20 + 15) × 2 = **70** | 30 | 被 `sanitizeValue` 钳到 30 ⇒ **理论值 57% 被静默丢弃** |
| `armor_toughness` | (0 + 10) × 2 = **20** | (12 + 10) × 2 = **44** | 20 | 被钳到 20 ⇒ **理论值 55% 被静默丢弃** |
| `knockback_resistance` | (0 + 0.45) × 2 = **0.9**<br>设计结论：「0.9 < 1.0 ⇒ **永远保留 10% 击退**」 | (0.4 + 0.45) × 2 = **1.7** | 1.0 | 被钳到 **1.0 = 完全免疫击退** ⇒ **该安全保证为假** |

### 10.5.2 「点不浪费」（公理 A3）在这 3 条 percent 线上被破坏

用 §1.4 的 SAT 采样（`V_pct(x) = cap·x/(x+half)`）解出**开始完全丢点的点数**（穿全套下界合金、flat=0）：

| 维度 | 钳制开始的判据 | 解出的点数阈值 |
|---|---|---|
| `armor` | `(20 + 0)·(1 + V_pct/100) > 30` ⇒ `V_pct > 50` | **约 60 点之后，每一点 100% 无效** |
| `armor_toughness` | `(12 + 0)·(1 + V_pct/100) > 20` ⇒ `V_pct > 66.67` | **约 120 点之后 100% 无效** |
| `knockback_resistance` | `(0.4 + V_flat)·(1 + V_pct/100) ≥ 1.0` | **约 (flat 100 点, pct 53 点) 即达完全免疫** |

> 复算依据：`armor.percent` cap=100 half=60；`armor_toughness.percent` cap=100 half=60；`knockback.flat` cap=0.45 half=60，`knockback.percent` cap=100 half=80，均为 §1.4 表值。

### 10.5.3 为什么这不是「玩家不该穿装备」能糊弄过去的

- 下界合金是**终局标准装备**，满分构建必然穿着它 ⇒ 这 3 条线的上限场景**必然**触发。
- 玩家的体感是：**投了几十点在护甲 percent 上，数值一点没变**（因为早被钳死了），且**没有任何提示**（§1.6.4：sanitizeValue 静默丢弃、无日志）。
- 这与设计宣称的核心卖点「无上限 + 点不浪费」**直接冲突**。

### 10.5.4 verifier 建议的三条修法（architect 二选一即可）

| 方案 | 做法 | 代价 |
|---|---|---|
| **A（推荐）** | `armor` / `armor_toughness` **改用 `DESIGN_BASELINE`**。理由：下界合金已占掉 20/30 与 12/20 的天花板，TOTAL 模式下留给玩家的绝对没有余量。 | 失去「+50% 护甲把装备也放大」的直觉（但那个直觉本来就会撞天花板） |
| **B** | 保留 TOTAL，但把 `sup` 按 `(base + 装备最大值 + sup_flat)(1 + sup_pct/100) ≤ HARD_MAX` **反解并下调 cap**。 | `armor` 无解（`(20+15)(1+p) ≤ 30` 要求 `p < 0`）⇒ 对 armor 不可行 |
| **C** | `knockback_resistance` 若选择接受「可完全免疫击退」，**必须删掉 §1.5 的「永远保留 10% 击退」结论**，并把它改成一条**显式验收项**（拉满 + 下界合金 ⇒ 位移为 0）。 | 与「不被原版钳制」的设计叙事冲突 |

**无论选哪条都追加一条硬要求**：
`/rpg stats` 必须同时显示 **`computed`（我们算出的值）与 `effective`（sanitizeValue 之后属性的实际落值）**。
否则「静默丢点」在验证与线上都**不可观测**——这是 §1.6.4 的直接推论。

---

## 附录 A — 单维度独立测量通道速查

| 维度 | 首选独立通道 | 命令 |
|---|---|---|
| D01 attack_damage | 属性直读 + 伤害探针 | `/attribute @s minecraft:generic.attack_damage get` |
| D02 max_health | 属性直读 + NBT | `/attribute @s minecraft:generic.max_health get` |
| D03 health_regen | 生命时间差分 | `/data get entity @s Health` × 2（相隔 200 tick） |
| D04 movement_speed | 属性直读 + 位移差分 | `/attribute @s minecraft:generic.movement_speed get` |
| D05 armor | 属性直读 + 固定伤害 | `/damage @s 20 minecraft:mob_attack` → Health 差 |
| D06 xp_gain | 经验直读 | `/xp query @s points` + `/xp add @s 100 points` |
| D07 knockback_resistance | 属性直读 + 位移差分 | `/attribute @s minecraft:generic.knockback_resistance get` |
| D08 armor_toughness | 属性直读 + 大伤害探针 | 固定 armor=20，`/damage @s 100 minecraft:mob_attack` |
| D09 damage_reduction | 固定伤害探针（两档） | `/damage @s 100` 与 `/damage @s 10` 对比 |
| D10 crit_chance | 统计（N≥400） + DEBUG 日志 | 攻击 400 次，统计暴击比例 |
| D11 crit_damage | 100% 暴击下的伤害比 | `D_crit / D_base` |
| D12 lifesteal | 生命差分（两档伤害） | 攻击前后 `/data get entity @s Health` |
| D13 drop_rate | 恒定掉落方块计数 | 挖 N 个石头，统计掉落总数 |
| **通用** | **原始属性 NBT** | `/data get entity @s Attributes`（查重复/残留 modifier） |

## 附录 B — 版本与 ID 速查（1.21.1）

| 项 | 值 |
|---|---|
| 属性注册名 | 带 `generic.` 前缀（1.21.2+ 才去掉）：`minecraft:generic.attack_damage` 等 |
| 资源包 `pack_format` | **34** |
| 数据包 `pack_format` | **48** |
| 关键事件 | `LivingIncomingDamageEvent`、`LivingDamageEvent.Pre/Post`、`LivingExperienceDropEvent`、`PlayerXpEvent.XpChange`、`CriticalHitEvent`、`LivingDropsEvent`、`BlockDropsEvent`、`ItemAttributeModifierEvent`、`PlayerEvent.Clone`（重生） |
| NeoForge 玩家附件存档 | `world/data/neoforge_data_attachments.dat`（已在参考工程存档中确认存在） |
| 服务器独立启动 | `.\gradlew.bat runServer` |
| GameTest 启动 | `.\gradlew.bat runGameTestServer` |

> 事件类名已在 `.research/neoforge-sources` 的 NeoForge 21.1.248 源码中逐一核对（`net/neoforged/neoforge/event/...`）。
