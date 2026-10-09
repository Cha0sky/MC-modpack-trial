# VERIFICATION-REPORT.md — 独立验证报告

> 工程：Minecraft **1.21.1** + **NeoForge 21.1.248**，modId `rpgleveling`
> 验证人：verifier（独立复现，**未采信 coder 或 lead 的任何自述结论**）
> 工具链：ModDevGradle 2.0.81 / Gradle 8.14.2 / Temurin 21.0.12
> **本文件的最新结论是 T18（§T18，数值体系：24 条 LINEAR + 2 条 SATURATING、`Stat.hardMax()` 运行时自适应、移速 / 生命恢复两处遗漏上限的补修）。T17 / T16 没有单独段落（T17 的结论在 `_verify/evidence/T17/REPORT.md`），T15 仍是手册基线，T13 仍是 Java / 数值层的旧基线。T12/T10/T8/T4 保留在下方供追溯，均已被后续段落取代。**

---

# T18 独立验证结论（**最新 · 数值体系改动**：24 LINEAR + 2 SATURATING、自适应 \`hardMax()\`、两处遗漏成长上限的补修）

> 验证人：**limitverifier**（独立验证角色）
> **独立性声明**：本节的期望值表**逐字抄自 T18 验收任务书与用户需求**，没有复用 \`StatLine.java\`、\`CurvePolicySuite.java\` 或 \`RpgMathSelfCheck.java\` 里的任何一张表；两个检查器（\`_verify/tools/t18_check_numbers.py\`、\`_verify/tools/t18_check_guide.py\`）与 6 个 GameTest 用例（\`_verify/probe/src/test/.../T18ProbeSuite.java\`）都是我自己新写的；所有数值都从**运行期重新生成的** \`run/config/rpgleveling-common.toml\` 或**新的算术重算**得来。
> **写域纪律**：本轮**没有写过一个字节的 \`src/**\`**——所有变异测试与自写用例都跑在 \`src\` 的逐字节副本 \`_verify/probe/\` 里，事前/事后 \`src\` 清单比对 **0 处差异**（\`src-manifest.before/after.sha256\`）。
> 本轮我独占 Gradle，全程无并发构建。

## T18-0. 总判定：**CONDITIONAL PASS —— 契约与数值全部正确；1 项 MEDIUM 待产品裁决，其余为文档滞后与边界瑕疵**

| # | 验收项 | 结论 |
|---|---|---|
| 1 | **A 唯一性与数值**（26 段逐行 + 18 个斜率独立复算） | ✅ 检查器 **146/146 通过**；24 LINEAR / 2 SATURATING；18 个斜率全部 = 原 cap/half；减伤渐近 = 90 且**严格小于** |
| 2 | **B \`hardMax()\` 契约** | ✅ 字节码证明**无缓存字段**、每次调用读 \`RangedAttribute.getMaxValue()\`；10 个 LIVE 全部 \`== getMaxValue()\` 且等于原版源码值；3 个 FIXED = 90/100/1.0；**抬高属性 max 的实验真实通过**（1000000 → 2500000 → 跟随 → 回落） |
| 3 | **C 钳制提示** | ✅ 暴击 >100%、击退 >1.0 时 GUI 判据**确实会亮**（旧的 \`isClamped\` 判据确实为 false，新判据补上了）；⚠ 找到 2 个边界反例（见 T18-3） |
| 4 | **需求 5（regen 上限）** | ✅ 默认 \`hard_cap_hps = 0.0\`、\`regenHardCapEnabled() = false\`；**功能实测** 20 tick 回满 25 HP（无钳制）；设成 5 时只回 5 HP（兜底仍生效） |
| 5 | **需求 6（half_points WARN）** | ✅ 删除配置后冷启动 **\`half_points\` 相关 WARN = 0、ERROR = 0**；负向对照（把下限改回 1.0E-6）**精确复现 24 条 WARN** |
| 6 | **D 无残留上限** | ⚠ **发现第 3 处默认开启的成长钳制**：\`lifesteal.hps_cap = 20.0\`（D1，MEDIUM，待裁决）；其余见 T18-5 清单 |
| 7 | **E 回归与变异** | ✅ \`verifyMath\` **471/471**；\`clean build\` **BUILD SUCCESSFUL**；\`runGameTestServer\` **All 50 required tests passed**；探测工程 **All 56 required tests passed**；\`runServer\` 冷启动 **0 ERROR**；四套脚本 + 两套手册校验器全绿；**4 组变异 + 3 组检查器负向对照**全部捕获 |
| 8 | **F 手册与文档** | ✅ 手册 **348/348** 结构+双语一致，**0 处旧说法**；DESIGN \`1.4\`/\`1.16\`/\`1.17\` **诚实**（明确写「除减伤外不再有安全性证明」）；⚠ 但 \`1.8\`/\`0.1\`/\`12.5\`/\`18\` 等段落**未同步**（D2/D3，文档缺陷） |

## T18-1. A 唯一性与数值：**146/146 通过，0 缺陷**

**方法**：\`_verify/tools/t18_check_numbers.py\`。脚本里自带一张**手抄任务书**的表（18 条旧 SATURATING 线的 \`(cap, half)\`、6 条本就 LINEAR 的线的 \`per_point\`、减伤两条的 cap/half/B_ref/hardMax），然后解析**运行期新生成**的 \`run/config/rpgleveling-common.toml\` 逐段核对；渐近值与极大点数下的值由脚本**自己重算** \`cap*x/(x+half)\`，不调用被测代码。

**18 个斜率（\`per_point = 原 cap / 原 half\`）全部命中**（缓存 1e-6 绝对容差）：

| 线 | 原 cap/half | 期望 | 实测 | 线 | 原 cap/half | 期望 | 实测 |
|---|---|---|---|---|---|---|---|
| armor.flat | 6/40 | 0.15 | 0.15 ✓ | crit_chance.flat | 50/60 | 0.833333 | 0.833333 ✓ |
| armor.percent | 100/60 | 1.666667 | 1.666667 ✓ | crit_chance.percent | 100/80 | 1.25 | 1.25 ✓ |
| armor_toughness.flat | 5/40 | 0.125 | 0.125 ✓ | lifesteal.flat | 20/60 | 0.333333 | 0.333333 ✓ |
| armor_toughness.percent | 100/60 | 1.666667 | 1.666667 ✓ | lifesteal.percent | 100/80 | 1.25 | 1.25 ✓ |
| movement_speed.flat | 0.4/60 | 0.006666667 | 0.006666667 ✓ | drop_rate.flat | 150/80 | 1.875 | 1.875 ✓ |
| movement_speed.percent | 200/80 | 2.5 | 2.5 ✓ | drop_rate.percent | 500/100 | 5.0 | 5.0 ✓ |
| knockback_resistance.flat | 0.35/40 | 0.00875 | 0.00875 ✓ | xp_gain.flat | 100/60 | 1.666667 | 1.666667 ✓ |
| knockback_resistance.percent | 100/60 | 1.666667 | 1.666667 ✓ | xp_gain.percent | 500/100 | 5.0 | 5.0 ✓ |
| health_regen.flat | 5/40 | 0.125 | 0.125 ✓ | health_regen.percent | 200/60 | 3.333333 | 3.333333 ✓ |

其余断言：\`attack_damage\` 0.5/3.0、\`max_health\` 1.0/2.0、\`crit_damage\` 1.0/2.0 保持原值 ✓；**curve 普查 = {LINEAR: 24, SATURATING: 2}** ✓；24 条 LINEAR 的 \`cap = half_points = 0.0\` ✓；\`basis\` 策略（3 个 TOTAL + 10 个 DESIGN_BASELINE，flat 线一律 TOTAL）✓；\`damage_reduction.percent.basis_value = 40\` ✓；\`general.strict_convergence = true\` ✓。

**减伤渐近值（我自己的算术，不是引用实现）**：

\`\`\`
sup = equipment_max(0) + flat.cap(60) + B_ref(40) * pct.cap(75)/100 = 90 = hardMax   （取等号）
combined(x,x) = 60*x/(x+80) + 40*(75*x/(x+80))/100 = 90x/(x+80) < 90  对一切有限 x
\`\`\`

| 每条线投入点数 | flat | percent | 合计 | 严格 < 90 |
|---|---|---|---|---|
| 10 | 6.666667 | 8.333333 | 10.000000 | ✓ |
| 100 | 33.333333 | 41.666667 | 50.000000 | ✓ |
| 1 000 | 55.555556 | 69.444444 | 83.333333 | ✓ |
| **10 000** | 59.523810 | 74.404762 | **89.285714** | ✓ |
| 1 000 000 | 59.995200 | 74.994000 | 89.992801 | ✓ |
| **10 000 000** | 59.999520 | 74.999400 | **89.999280** | ✓ |
| \`Integer.MAX_VALUE\` | 59.999998 | 74.999997 | **89.9999966** | ✓ |

**144 组 (flat 点, pct 点) 组合的最坏值 = 89.999996647239（在 \`Integer.MAX_VALUE\` 处）**，即在整个 int 点数空间内都不可能达到 90。因为 \`lineValue\` 只接受 \`int\` 点数，不存在「浮点吞掉 half 项导致恰好等于 cap」的路径（2^31 远小于 2^53）。**减伤「永不免疫」的数学保证成立。**

## T18-2. B \`hardMax()\` 契约：三条独立证据，全部通过

**(1) 字节码证据（\`_verify/evidence/T18/b-javap-Stat-code.txt\`）**：\`javap -p\` 显示 \`Stat\` 只声明 \`playerBase\` / \`limitMode\` / \`fixedMax\` / \`equipmentMax\` 字段，**没有任何 \`hardMax\` 字段**；\`hardMax()\` 的字节码是

\`\`\`
getfield limitMode ; getstatic LimitMode.LIVE ; if_acmpne 43
getfield carrier ; invokevirtual Carrier.holder ; invokeinterface Holder.value
instanceof RangedAttribute ; ... invokevirtual RangedAttribute.getMaxValue ; dreturn
43: getfield fixedMax ; dreturn
\`\`\`

即：**每次调用都走一遍属性注册表并读 \`getMaxValue()\`**，命中不到 \`RangedAttribute\` 时才退回功能上限。不存在任何构造期快照或缓存字段。

**(2) 运行期枚举（我的 \`t18B_hardMaxContract\`，105 项检查）**：10 个 LIVE 维度全部满足 \`hardMax() == ((RangedAttribute) holder.value()).getMaxValue()\`（**容差 0.0，精确相等**），且等于我从反编译源码读出的原版值：attack_damage 2048、max_health 1024、armor **30**、armor_toughness 20、movement_speed 1024、health_regen / lifesteal / drop_rate / xp_gain 1.0E6、crit_damage 1.0E9；3 个 FIXED 精确等于 90 / 100 / 1.0。**\`hardMax(ARMOR) = 30.0\`，同时等于 \`Attributes.ARMOR.value().getMaxValue()\`，不是硬编码常量。**

**(3) 决定性实验：把属性 max 抬高，看 \`hardMax()\` 跟不跟**（\`t18B_hardMaxFollowsARaisedAttributeRange\`）
AttributeFix 的 Fabric 版在 NeoForge 上根本加载不了，所以我在**测试进程内**用 \`sun.misc.Unsafe\` 直接改写一个 LIVE 维度（\`LIFESTEAL\`，本 mod 自己注册、max = 1.0E6）的 \`RangedAttribute.maxValue\`，这正是 AttributeFix 的作用点：

\`\`\`
原始 maxValue                = 1000000.0   ->  hardMax() = 1000000.0
Unsafe 写入 2500000.0 后     = 2500000.0   ->  hardMax() = 2500000.0   ✓ 跟随
写回 1000000.0 后            = 1000000.0   ->  hardMax() = 1000000.0   ✓ 回落
\`\`\`

字节码里读到的 \`getMaxValue=2500000.0\` 与 \`hardMax()\` 同值，且**回落也跟随**，排除了「一次性缓存」。**结论：代码走的是运行时读取，不是构造期快照。**（\`finally\` 中已还原，事后 \`attr.getMaxValue()\` 断言恢复原值。）

## T18-3. C 钳制提示：主判据通过，另找到 2 个边界反例

**主判据（需求 C）成立**（\`t18C_clampMarkerLightsUp\`）：

| 维度 | 投入 | \`rawAttributeValue\` | \`finalValue\`（有效值） | 旧判据 \`isClamped\` | 新判据 \`isCappedByModCeiling\` | GUI 判据 \`isEffectivelyCapped\` |
|---|---|---|---|---|---|---|
| crit_chance.flat | 200 点 | **166.6666** | 100.0 | **false**（1.0E6 宽的自定义属性没被原版截断） | **true** | **true** ✓ |
| knockback_resistance.flat | 2000 点 | **17.5** | 1.0 | true（原版属性 max 就是 1.0） | — | **true** ✓ |

即：**「以前永远为 false」的那类情况（暴击 166% 但属性范围 1.0E6）现在确实会亮**，而实现方的说明属实。

**反例 1（MINOR）· 正好落在 FIXED 上限上时判据不亮。** 判据是严格 \`effective > hardMax + 1e-9\`。\`crit_chance.percent\` 是 DESIGN_BASELINE / B_ref=20 / per_point=1.25，**400 点恰好给出 \`20 * (1.25*400)/100 = 100.0\`**：

\`\`\`
投入 400 点（正好 100.0）-> t18.crit.exactCeiling.value=100.0   marker=false   ← 再投一点就没有收益，却不提示
投入 401 点（仍是 100.0）-> t18.crit.oneMorePoint.value=100.0    marker=true
\`\`\`

语义上「继续加点不会再有收益」在值为 100.0 时**已经成立**，所以这是一个真实的假阴性边界（只在整数能恰好命中 cap 的线上出现，目前是 crit_chance；knockback/减伤不受影响）。建议改成 \`>= hardMax - 1e-9\` 或对 FIXED 维度用「值已达上限且该线为 LINEAR」的判据。

**反例 2（LOW · i18n/措辞）· 文案与判据不符。** \`gui.rpgleveling.clamped\` 中英文分别是：
\`"已达原版上限（%s），继续加点不会再有收益"\` / \`"Already at the vanilla ceiling (%s); more points will have no effect"\`。
但 T18 之后这条提示**也会为本 mod 自己的功能上限触发**（暴击 100、击退 1.0），它们在原版里根本没有上限，\`%s\` 传的是 \`hardMax()\`。DESIGN \`1.17.6\` 声称语义「仍然诚实」，**文字层面不成立**，建议改为「已达上限」。

## T18-4. 需求 5 / 需求 6（lead 的两处补修）：**两处都修对了，但另发现第 3 处同类问题**

**需求 5（\`health_regen.hard_cap_hps\`）** — 我没有用实现方的用例（实现方的 GameTest **完全没有覆盖这两个访问器**，我 grep 过：只有 \`movementSpeedHardCapEnabled\` 被断言），而是自己写了功能级探测：

| 断言 | 证据（日志 \`RPGVERIFY\` 行） | 结论 |
|---|---|---|
| 冷启动默认值 | \`t18.regen.defaultHardCapHps=0.0\` | ✅ |
| 默认不施加上限 | \`t18.regen.defaultEnabled=false\` | ✅ |
| 默认下 20 tick 实际回血 | hps=25.0（200 点 × 0.125）→ \`healedPer20Ticks.defaultOff=25.0\` | ✅ **完全没有钳制** |
| 管理员设 5.0 时 | \`healedPer20Ticks.cap5=5.0\` | ✅ **兜底仍然有效** |

（探测直接调用 \`PlayerTickHandler.onPlayerTick(new PlayerTickEvent.Post(player))\` 20 次，同步执行、不依赖 tick，所以测量是确定的；每次先把 \`PlayerCache.regenAccumulator\` 清零。）

**需求 6（\`half_points\` WARN）**
- **正向**：删掉 \`run/config/rpgleveling-common.toml\` 后冷启动，日志 78 行里 **\`Incorrect key\` / \`half_points\` / \`corrected from\` 命中 0 条**，**ERROR 行 0 条**；剩下的 WARN 只有原版 teleport 参数歧义 ×5、VanillaPackResourcesBuilder ×2、patchouli refmap ×1、offline 模式 ×4。✅
- **负向对照（我自己设计）**：把下限改回 \`1.0E-6\` 并重新生成配置后，**精确出现 24 条** \`Incorrect key lines.<维度>.<模式>.half_points was corrected from 0.0 to its default, 1.0E-6\`，ERROR 仍为 0。⇒ 「0 条」这个结论**有判别力**，不是我的计数方法看不到。✅

**⚠ 第 3 处默认开启的成长钳制（D1，MEDIUM）**：\`lifesteal.hps_cap\` 默认 **20.0**（\`RpgConfig.java:269\`，\`CombatEvents.java:156\` 用它给每 tick 的吸血额度封顶）。lead 把移速与生命恢复两处「本 mod 自设上限」当成缺陷修掉了，但**生命偷取这处同类上限仍在默认状态下生效**：LIFESTEAL 现在是 LINEAR/无上限，可玩家无论投多少点，每秒吸血最多 20 HP（其余静默丢弃）。这与用户「**其余成长限制全部取消**」的要求不一致，也与 \`1.17.3\` 的「除减伤外不再有安全性证明 / 不再声称安全」口径不一致。**需要 lead 做产品裁决**：要么默认改 0 = 关闭（与另两处一致），要么在文档里明确它是「防 AoE 瞬间回满」的限流而非成长上限。

## T18-5. D 全工程「上限 / 钳制」清单（默认状态下）

\| 配置键 | 默认 | 是否在默认状态下限制成长 | 判定 |
|---|---|---|---|
| \`general.max_level\` | \`-1\` | 否 | ✅ 无等级上限 |
| 26 × \`lines.*.max_points\` | \`-1\` | 否 | ✅ 无投点上限（我逐条断言） |
| 26 × \`lines.*.enabled\` | \`true\` | 否 | ✅ 全部启用 |
| \`general.strict_convergence\` | \`true\` | — | 只影响运行时曲线覆盖；默认文件值本就是目标曲线 |
| \`movement_speed.hard_cap_attr\` | \`0.0\` | **否**（0 = 关闭） | ✅ 本次补修；\`AttributeApplier\` 关闭时主动移除旧修饰符 |
| \`health_regen.hard_cap_hps\` | \`0.0\` | **否**（0 = 关闭） | ✅ 本次补修（功能实测） |
| \`lifesteal.hps_cap\` | \`20.0\` | **是** | ⚠ **D1**，见 T18-4 |
| \`xp.minute_cap\` | \`0\` | 否 | ✅ 关闭 |
| \`xp.safety.max_per_tick.{mine,craft,smelt}\` | 60 / 40 / 20 | 是（每 tick 经验） | ➖ 反刷/防爆保险，非成长天花板；建议在交付说明里点明 |
| \`drop_rate.max_items_per_event\` / \`max_entities_per_event\` | 256 / 64 | 是（单次事件掉落预算） | ➖ 防卡顿预算，非成长天花板；同上 |
| \`stats.report_effective\` | \`true\` | — | ✅ 可观测性开关，未关闭 |
| FIXED 功能上限（\`Stat.fixedMax\`） | 90 / 100 / 1.0 | 是（按设计） | ✅ 用户明确要求保留 |
| 自定义属性自身范围 | 1.0E6（crit_damage 1.0E9） | 是（软边界） | ➖ 只有堆到 1e6 量级才会撞上；装 AttributeFix 可放开（LIVE 维度） |

另外发现一处**死代码**：\`CombatEvents.java:135\` 的 \`if (reduction > 95.0) reduction = 95.0;\` —— \`damageReductionPercent\` 已经过 \`hardMax() = 90\` 钳制，永远到不了 95。无害，但注释自称「numeric backstop only」会误导。

## T18-6. E 回归与变异：全部通过，含「加载期崩溃但 Gradle 报成功」的独立复现

**回归（我自己跑，全部读日志不看退出码）**

| 项 | 命令 | 结果 | 日志 |
|---|---|---|---|
| 纯 JVM 自检 | \`gradlew verifyMath\` | **\`RpgMath self check: 471/471 passed, 0 failed\`** | \`02-verifyMath.log\` |
| 全量构建 | \`gradlew clean build\` | **BUILD SUCCESSFUL**（7 tasks） | \`01-clean-build.log\` |
| GameTest（交付树） | \`gradlew runGameTestServer\` | **\`All 50 required tests passed :)\`** | \`03-gametest.log\` |
| GameTest（探测树） | \`_verify/probe\` \`runGameTestServer\` | **\`All 56 required tests passed :)\`**（50 + 我的 6 个） | \`p2-probe-baseline-final.log\` |
| 专服冷启动（删配置） | \`gradlew runServer\` | **\`Done (0.787s)\`，ERROR 行 = 0，half_points WARN = 0** | \`runServer-cold.log\` |
| \`check_static.py\` | — | 6/6 PASS，0 FAIL | \`05-check_static.log\` |
| \`check_lang.py\` | — | 8/8，0 FAIL（1 条 WARN：动态键需人工看） | \`07-check_lang.log\` |
| \`check_dist.py\` | — | PASS：**0 个非 client 包类引用客户端类型** | \`06-check_dist.log\` |
| \`check_jar.ps1\` | — | 13/13 PASS（379,420 字节 / 77 classes / pack_format 34） | \`04-check_jar.log\` |
| \`tools/lead_check_book.py\` | — | PASS：6 类 22 条 × 2 语言，**0 ERROR 0 WARN** | \`08-lead-check-book.log\` |
| \`tools/validate_patchouli_book.py\` | — | PASS：**95 页/语言**，语言树一致，0/0/0 | \`09-validate-patchouli.log\` |

**变异测试（全部跑在 \`_verify/probe\` 副本上，即写域纪律同时成立）**

| # | 变异 | 退出码 / Gradle 判定 | 日志判据 | 结果 |
|---|---|---|---|---|
| **M1** | \`ARMOR_FLAT\` 由 \`LINEAR\` 改回 \`SATURATING\`（cap 6 / half 40） | **exit=0，\`BUILD SUCCESSFUL\`** | **无 \`All N required tests passed\`**；\`java.lang.ExceptionInInitializerError\` → \`Caused by: IllegalStateException: line armor.flat is saturating, but only damage_reduction may keep a ceiling\`；\`ModLoadingCrashException\`；**没有任何 \`Running test batch\` 行** | ✅ 护栏生效；**假绿陷阱独立复现**：Gradle 报成功而 0 个用例跑过 |
| **M2** | \`ARMOR_FLAT.per_point\` 0.15 → 0.05 | exit=1，BUILD FAILED | \`everyLinearLineKeepsTheOldInitialSlope: 1/91 checks failed -> armor.flat.per_point: expected 0.150000 but got 0.050000\`；1 required tests failed | ✅ 数值回归会被抓 |
| **M3** | （无效尝试，如实记录）\`hard_cap_hps\` 默认 20.0，**但探测树里已存在旧配置文件** | exit=0，**All 56 passed** | 未被抓到 | ❌ **变异无效**：NeoForge 不会用新默认覆盖已存在的 \`rpgleveling-common.toml\`（铁律 2）。**这本身就是一条应当记录的实证** |
| **M3b** | 同上，但**先删掉配置文件**再跑 | exit=1，BUILD FAILED | \`t18D_noResidualGrowthCapByDefault: 1/105 checks failed -> health_regen.hard_cap_hps still clamps by default: 20.0\`；\`t18E_regenHardCapDefaultsToOffButStillWorks: 5/9 checks failed -> ... healed 20.0\`；2 required tests failed | ✅ **我的探测用例确实能抓住这处回归**（证明它不是空转） |
| **M4** | \`half_points\` 下限改回 \`1.0E-6\` + 删配置 | 冷启动 | **精确 24 条** \`Incorrect key lines.<x>.half_points was corrected from 0.0 to its default, 1.0E-6\` | ✅ 需求 6 的「0 条」结论有判别力 |

**检查器负向对照（\`_verify/evidence/T18/negctl/\`）**

| # | 对照 | 结果 |
|---|---|---|
| NC1 | 把配置副本里 \`armor.flat.per_point\` 改成 0.20 | \`t18_check_numbers.py\` **exit=1**，报 \`missing section lines.armor.flat\`（篡改破坏了段落，检查器确实在解析被篡改的文件）✅ |
| NC2 | 手册副本里删掉 \`zh_cn/.../health_regen.json\` | \`t18_check_guide.py\` **exit=1**（语言树不一一对应）✅ |
| NC3 | 手册副本里把「默认 0 = 关闭」改回「20 HP/s 硬钳」 | 旧说法扫描器命中 \`LEGACY-SCAN ... [regen_20_hps]\`（该文件本身通过了其余 348 项结构检查，说明差异只来自旧说法）✅ |

## T18-7. F 手册与文档

**手册（\`_verify/tools/t18_check_guide.py\`，348 项检查，0 失败）**：\`zh_cn\` 与 \`en_us\` 各 **28 个文件**、相对路径集合**完全相同**；逐条目比对 \`category\` / \`sortnum\` / \`icon\` / **页数** / **页型序列** / 每个 text 页的**文本行数** / 非本地化字段（recipe、item、entries、entity、image）——全部一致。**旧说法扫描 0 命中**（「渐近 …50/70/80%」「移速 1.5 上限」「20 HP/s 硬钳」三类正则都没有命中）。另单独断言 \`health_regen\` 双语文案都写明 \`hard_cap_hps\` 默认 0 = 关闭、\`damage_reduction\` 两边都写了 90%。

**DESIGN \`1.4\` / \`1.16\` / \`1.17\` 的诚实性**：**成立**。
- \`1.4\`（L215-318）：表头标注「T17 重写：已取消递减」，逐维度给 \`per_point\` 与上限模式，并明确写「cap 全部作废的后果：1.5/1.16 的安全性证明对除伤害减免外的维度**不再成立**」（L240-241）。
- \`1.16\`（L828-838）：有一张**覆盖声明表**，逐小节标注 \`1.16.2\` 只剩第 8 行成立、\`1.16.3\` 已删除、\`1.16.4\` 已放弃、\`1.16.6\` 结论反转。
- \`1.17.3\`（L1111-1121）：标题就是「诚实声明：取消递减之后，除减伤外不再有安全性证明」，并解释 \`sup → 无穷\`、证明「数学上不可能成立」。
⇒ **「取消递减后除减伤外不再有安全性证明」这一条，文档是诚实的。**

**但文档滞后项（不影响交付，但会误导读者）**：见 D2/D3/D6/D7/D8。

## T18-8. 缺陷清单（按严重度）

### D1 · **MEDIUM（需产品裁决）** · \`lifesteal.hps_cap = 20.0\` 默认仍限制成长
见 T18-4。证据：\`RpgConfig.java:269\`（默认 20.0）、\`CombatEvents.java:156\`（每 tick 额度 = cap/20）、\`t18.residual.lifestealHpsCap=20.0\`。与「其余成长限制全部取消」不一致；同类上限（移速、生命恢复）已被本轮当缺陷修掉。

### D2 · **MEDIUM（文档诚实性）** · DESIGN \`1.8\` 的「回复不会不死」论证已被 T18 作废，但没写
\`L399\` 仍写 \`R = min(hard_cap_hps, ...)\`，默认 \`hard_cap_hps = 20 HP/s\`、\`sup = 15 HP/s\`；\`L400\` 用「\`R\` 有上界 20」推出「任何 DPS > 20 都能击杀玩家」；\`L403\` 还说减伤上限是 **80%**「至少承受 20% 伤害」。默认状态下 regen 已无 mod 侧上限（功能实测 25 HP/s 不被钳制），而且减伤渐近值是 **90**（至少承受 10%）。\`1.17.3\` 只承认了「除减伤外的成长维度」失去安全性证明，**没有覆盖 \`1.8\` 的这条「不会不死」论证**。

### D3 · **LOW（文档滞后）** · 多处旧说法未随 T17/T18 同步
- \`0.1\` 需求锚点表 \`L41\`（「无上限 = 收益递减收敛」）、\`L43\`（击退 sup 0.95 < 1.0）、\`L44\`（暴击「渐近逼近 100% 取不到」）、\`L45\`（移速软上限 **1.5**）、\`L46\`（生命恢复/偷取/经验/掉落「有界的 x/(x+K)」）——**全是旧设计**，而 \`0.1\` 自称「用户已拍板不得偏离」。
- \`1.14.4\` \`L661\`（移速 sup = 1.5）、\`L662\`（击退 sup = 0.95）；\`1.15\` \`L770\`（「拉满后约 1.47」当验收点）。
- \`12.5\` \`L2780\`（\`hard_cap_hps\` 默认 **20.0**）、\`L2785\`（\`hard_cap_attr\` 默认 **1.5**）——与实际默认 0.0 不符。
- \`7\` \`L1834\` 代码示意（\`Math.min(hps, regenHardCapHps.get()) // 默认 20 HP/s\`）；\`18\` \`L3097\`（\`R ∈ [0, 80)\`）、\`L3103\`（\`regen_hps = min(hard_cap_hps, ...)\`）；\`16\` \`L3003/L3004\`（风险表仍写移速 1.5、regen 硬钳 20）。
- \`1.17.9\` \`L1218\` 写 \`runGameTestServer 49/49\` /「All 49 required tests passed」，**实际是 50**。

### D4 · **LOW** · 钳制提示文案与判据不符
\`gui.rpgleveling.clamped\` 说「已达**原版**上限」，但 T18 后也会为 mod 自己的功能上限（100 / 1.0）触发。见 T18-3 反例 2。

### D5 · **LOW** · 正好落在 FIXED 上限上时判据不亮
严格 \`> hardMax + 1e-9\` 造成 400 点暴击（恰好 100.0）不提示。见 T18-3 反例 1。

### D6 · **LOW** · DESIGN \`12.5\` 记录了一个**不存在**的配置键
\`L2787\` 的 \`knockback_resistance.hard_floor_ratio\` 在 \`RpgConfig.java\` 里**根本没有**（全工程 grep 0 命中）。T6 遗留的文档漂移。

### D7 · **INFO（账目）** · \`VERIFICATION-REPORT.md\` 头部与内容不一致
原文头部声称「最新结论是 T16（§T16）」，但文件里**没有 T16 段落，也没有 T17 段落**（T17 的结论只在 \`_verify/evidence/T17/REPORT.md\`）。我已把头部指针更新为 T18，并在此说明 T17/T16 没有段落这件事。

### D8 · **INFO** · \`1.17.8\` 的「附带现象（half_points WARN）」说明**已作废**
\`L1207-1210\` 仍描述「首次生成会打印 20~24 条 WARN，第二次不再出现」。lead 把下限改成 0.0 之后，**首次生成也没有任何 WARN**（我实测 0 条，并用反向对照证明计数有判别力）。该段需要删掉或改写。

### D9 · **INFO** · 死代码 \`CombatEvents.java:135\`
\`if (reduction > 95.0) reduction = 95.0;\` 永远不触发（\`hardMax() = 90\` 已保证）。无害。

### D10 · **INFO（理论）** · \`isClamped\` 的容差与加法顺序
\`isClamped\` 用 \`1e-6\` 绝对容差比较 \`rawAttributeValue\` 与 \`AttributeInstance.getValue()\`。\`rawAttributeValue\` 按 \`getModifiers()\`（插入序）三趟累加，而 MC 的 \`calculateValue\` 按**每个 operation 各自的哈希序**累加，浮点加法顺序不同会带来末位差异。在 1e6 量级下差异约 1e-10，远小于容差，**实测 0 误报**；只有值到 ~1e10 量级才可能误报（当前只可能出现在人为超大的 LINEAR 投入上）。建议未来把容差改成相对容差。

## T18-9. **必须真机确认的残余项**（我无法在本机闭环）

1. **AttributeFix（NeoForge 版）的端到端自适应**：用户装的是 **fabric 版**，在 NeoForge 上加载不了，所以「装了 AttributeFix 之后护甲上限真的从 30 抬高」这件事**本机无法闭环**。我能证明的只是**契约**：\`hardMax()\` 每次调用读 \`getMaxValue()\`，且我模拟抬高（1.0E6 → 2.5E6）后 \`hardMax()\` 立即跟随、回落也跟随。**请用户换成 NeoForge 版 AttributeFix 后实机确认**（例如 \`/attribute @s minecraft:generic.armor get\` 的 max 与 \`/rpg stats\` 的 \`armor.limit\`）。
2. **移速的 \`moved too quickly!\` 回拉**：本 mod 侧已证明不再钳制（LIVE 上限 = 原版 1024，\`hard_cap_attr = 0\`），但「多少倍原版会触发回拉」是原版服务器机制，需真机（非单机房主）确认。
3. **GUI 的「!」标记与提示文案**：\`RpgScreen\` 只在客户端渲染，**专服 / GameTest 都不渲染 GUI**。我证明的是判据函数（\`isEffectivelyCapped\`）在两种钳制下都为 true；**行首标记的实际显示、320×240 与中英文下的排版**仍需真机看。
4. **帕秋莉手册正文**：条目只在客户端资源重载时解析，专服启动不能证明正文正确。我做了结构 + 双语一一对应 + 旧说法扫描，**正文措辞仍需人工过一遍**。
5. **regen 现在真的没有 mod 侧上限**（只受属性 1e6 与客户端性能约束）：这是用户要的结果，但「1e6 HP/s 的回复在实际战斗/服务器 tick 下的表现」需要试玩确认；同时 D1 的裁决会改变 lifesteal 的手感。
6. **配置迁移**：用户实例的旧配置**必须删除或显式改键**（铁律 2）。我在探测树里亲自踩到这一点（M3 变异因此无效）。开发者工作区的 \`run/config/rpgleveling-common.toml\` 我已备份为 \`_verify/evidence/T18/rpgleveling-common.pre-T18.toml\` 并删除后重新生成；**用户实例的配置是否需要再处理，请 lead 确认**（T17 报告称已删过一次，但若用户在此期间启动过游戏，旧值会重新落盘）。

## T18-10. 证据索引（T18）

| 文件 | 内容 |
|---|---|
| \`_verify/tools/t18_check_numbers.py\` | 我写的数值检查器（146 项，含 \`--config\` 负向对照钩子） |
| \`_verify/tools/t18_check_guide.py\` | 我写的手册检查器（348 项，含 \`--guide\` 负向对照钩子） |
| \`_verify/probe/src/test/java/com/rpgleveling/gametest/T18ProbeSuite.java\` | 我写的 6 个独立 GameTest 用例（hardMax 契约 / 抬高 max / 钳制提示 / regen / 无残留上限 / 减伤） |
| \`_verify/evidence/T18/interactive-evidence.log\` | 终端内联证据汇总（见下） |
| \`01-clean-build.log\` \`02-verifyMath.log\` \`03-gametest.log\` \`runServer-cold.log\` | 回归 |
| \`p1/p2-probe-baseline*.log\` | 探测树基线（\`All 56 required tests passed\`） |
| \`m1-load-failure.log\` \`m2-wrong-slope.log\` \`m3-regen-cap-back.log\` \`m3b-regen-cap-freshconfig.log\` \`m4-halfpoints-warn.log\` | 变异（含假绿陷阱、无效变异、修正后的变异、WARN 负向对照） |
| \`negctl/NC1-config-tamper.out\` \`negctl/NC2-guide-missing.out\` \`negctl/NC3-guide-legacy.out\` | 检查器负向对照 |
| \`05-check_static.log\` \`07-check_lang.log\` \`06-check_dist.log\` \`04-check_jar.log\` \`08-lead-check-book.log\` \`09-validate-patchouli.log\` | 四套脚本 + 两套手册校验器 |
| \`b-javap-Stat.txt\` \`b-javap-Stat-code.txt\` | \`javap -p\` / \`javap -p -c\` 字节码证据 |
| \`check-numbers.out\` \`check-guide.out\` | 两个检查器的完整 JSON 输出 |
| \`src-manifest.before/after.sha256\` \`tools-manifest.before/after.sha256\` | 写域纪律：\`src/**\` 与 \`tools/**\` 事前事后 **0 差异** |
| \`rpgleveling-common.pre-T18.toml\` | 删除前备份的开发者工作区配置 |

---

# T15 独立验证结论（**T15 · 帕秋莉游戏内说明书 · 已被 T18 取代为「最新」**）

> 验证人：**bookverifier**（独立验证角色）
> **独立性声明**：本节的规则**不采信 bookwright 或 lead 的任何自述**，也**未复用**二人的校验脚本（`tools/validate_patchouli_book.py`、`tools/lead_check_book.py`）。全部规则由我用 `javap` 从 **Patchouli-1.21.1-93-NEOFORGE.jar 的字节码**重新推导，全部数字由我从 **运行期生成的 `run/config/rpgleveling-common.toml`** + mod 自身的 `Stat.java` 独立重算。
> 本轮我独占 Gradle，全程无并发构建。

## T15-0. 总判定：**PASS —— 手册可交付；残余项全部只能由真机 `runClient` 确认**

| # | 项目 | 结论 |
|---|---|---|
| 1 | **A 书目结构校验**（独立检查器 `_verify/tools/t15_book_check.py`） | ✅ **57/57 文件可解析；0 BLOCKER / 0 MAJOR / 0 MINOR** |
| 2 | **中英完全一一对应** | ✅ 6 分类 + 22 条目 × 2 语言；文件树、条目数、**每条页数、页型序列、键集合**全部一致 |
| 3 | **B 正文本数值对拍**（本轮重点） | ✅ **125 个采样点全部一致（0 处不符）**；58 个天花板/上限断言全部命中；XP 曲线 8 采样 + 4 累计全对 |
| 4 | **C 构建 / jar / 依赖** | ✅ `clean build` 成功；jar **373,192 字节**，60 个交付文件**逐一在 jar 内且与 src 逐字节一致**；`patchouli` 依赖块 `type="required" ordering="AFTER" side="BOTH"` |
| 5 | **C 运行回归** | ✅ `verifyMath` **432/432**；`runGameTestServer` **All 40 required tests passed**；`runServer` 冷启动 **ERROR 行数 = 0**，配方 **1295** 条全加载 |
| 6 | **C 负向对照（我自己设计）** | ✅ 两组都**确实报错**（配方 3 条 ERROR + 配方数 1295→1294；书 `use_resource_pack=false` → Patchouli 抛异常），证明「零报错」有判别力；**4 个被改文件已还原且 sha256 与改前完全一致** |
| 7 | 与 T13 的关系 | ✅ **T13 的 26/26 属性层 + 26/26 效果层判定不受影响**（详见 T15-6，含一处必须说明的基线账目偏差） |
| 8 | 缺陷 | **0 BLOCKER / 0 MAJOR；3 项 MINOR + 3 项 INFO**（详见 T15-5） |

### 冻结基线（T15，含手册的交付版）

```
产物 : build/libs/rpgleveling-1.0.0.jar
大小 : 373192 字节   条目 204   class 76
SHA-256 : EA9F143720012A451C303C5CAB9A3FA2738B63BDDCAC742B1248AD7BE281B65A

手册交付文件（57 数据文件 + 模型 + 配方 + mods.toml = 60 个）:
  MANIFEST-SHA256 : 49B4999BC9E31C7CD0AD7E5609C2F95053819E8C0111720B8767514AA489BA2C
  逐文件清单      : _verify/evidence/T15/BOOK-MANIFEST.sha256

独立检查器（我写的，未复用他人脚本）:
  B4374EBECE471E57B896920E3907872FAF8A9E4DAD8EA0C066CADCC2931D7E00  _verify/tools/t15_book_check.py
  63AB822281F7260AE9C285B18025F2E7E92E6147CFA6B766D9CAB53F0430B080  _verify/tools/t15_extract_page_schema.py
  C729F31BA12BC69AADEC3F1E9D44F1878832091AE8660FC7F2BECA529443CD8A  _verify/tools/t15_page_fields.py
  59C863F4F76C1C9E78495E7325CA0709313CB17F013062B020A8DFBF3871F0AC  _verify/tools/t15_check_jar.py
```

---

## T15-1. 权威规则是我**自己从字节码重新推导**的（且推翻了自述清单里的一处错误）

### 1.1 书目目录规则（运行时证据，非仅字节码）

Patchouli 的 `Book` 构造器在 `use_resource_pack != true` 时直接抛异常。这一点我**不只从字节码推断，还拿到了运行期原文**（负向对照 B 的专服日志）：

```
[modloading-worker-0/ERROR] [patchouli/]: Failed to load book rpgleveling:guide defined by mod rpgleveling, skipping
java.lang.IllegalArgumentException: Book rpgleveling:guide has use_resource_pack set to false.
  This behaviour was removed in 1.20. The book author should enable this flag and move all book
  contents clientside to /assets/, leaving the book.json in /data/.
    at vazkii.patchouli.common.book.Book.<init>(Book.java:148)
    at vazkii.patchouli.common.book.BookRegistry.loadBook(BookRegistry.java:90)
```

⇒ **「book.json 放 data/、内容放 assets/、必须 use_resource_pack=true」得到两种独立手段的确认**（字节码 + 运行期异常）。本书全部符合。

### 1.2 一条**必须更正的自述**：cooking 页型的 id 不是 `campfire_cooking`

lead 给的「权威 page type 清单（来自 `client/book/page/` 类名）」在这一点上**不成立**。我从 `ClientBookRegistry.<clinit>` 的常量池里逐对取出 (`patchouli`, name) → class 的真实注册表，得到 **16 个已注册 id**：

```
patchouli:text  crafting  smelting  blasting  smoking  campfire  smithing  stonecutting
patchouli:image spotlight empty      multiblock link     relations entity    quest
```

- **类名 `PageCampfireCooking` 对应的注册 id 是 `patchouli:campfire`，不是 `patchouli:campfire_cooking`。**
- `patchouli:template` **没有注册**（`PageTemplate` 只是「未知 type 时的兜底类」）。未知 type **不会报错**，会静默回落成 `PageTemplate` —— 这是一条**静默降级**风险。
- **对本书无影响**：本书只用了 `patchouli:text`（每语言 91 个页面，两语言共 182）与 `patchouli:crafting`（每语言 4 个页面），两者都在上述清单里。检查器按真实注册表判定，若将来有人写 `patchouli:campfire_cooking` 会被我的检查器判 **BLOCKER**。

### 1.3 「必填字段」的推导路径（与自述方法不同）

`ClientBookRegistry$LexiconPageAdapter.deserialize` 显示页面对象由 **Gson 反射**反序列化：

```
RAW_GSON.fromJson(json, pageClass)      // keys == Java 字段名（或 @SerializedName 覆盖值）
```

⇒ **Patchouli 不会校验多余/缺失键**，写错键名是**静默忽略**。因此我的合法键集合来自「类链上所有非 transient 字段 + @SerializedName」，必填集合来自「页面 build()/render() 会解引用的内容字段」。例如 `PageDoubleRecipe` 的四个 json 键真实为 `recipe / recipe2 / link_recipe / link_recipe2`（靠 `javap -v` 的 `RuntimeVisibleAnnotations` 读到 `@SerializedName`）。

`Book` 则是**手写**解析（`GsonHelper.getAsString(json, ...)`），我只把「其后紧跟 GsonHelper/SerializationUtil/JsonObject.get 调用的字符串常量」算作键，得到 32 个键；其中 **`name` 是唯一 2 参调用 ⇒ 唯一必填**。`BookEntry.category` 同理是**必填**（2 参 `getAsString`），`BookEntry` 合法键 13 个、`BookCategory` 7 个。

---

## T15-2. A 书目结构校验：**57/57 通过，0 缺陷**

检查器：`_verify/tools/t15_book_check.py`（输出 `_verify/evidence/T15/book_check.json` / `.log`）。全部判定项：

| 编号 | 检查 | 结果 |
|---|---|---|
| S1 | 所有 JSON 可解析 | **57/57** |
| S2 | book.json 三要素 + `use_resource_pack=true` + 键合法 + 纹理在 jar 内 | PASS（`name`/`landing_text`/`subtitle` 齐；`book_texture`=`patchouli:textures/gui/book_blue.png` 在 Patchouli jar 内） |
| S2 | `model: rpgleveling:guide_book` → `assets/rpgleveling/models/item/guide_book.json` 存在 | PASS |
| S3 | 6 个分类的键全在 `{name,description,icon,parent,flag,sortnum,secret}` 内、均有 icon | PASS |
| S4 | 每条 `category` 都能在同语言分类里解析（`rpgleveling:<cat>`） | PASS（22/22 × 2 语言） |
| S4 | **每个分类至少一条条目** | PASS（6/6 × 2 语言） |
| S4 | 每个 page 的 `type` 在**真实注册表**内 | PASS（`patchouli:text` ×91、`patchouli:crafting` ×4/语言） |
| S4 | 每个 page 的键都在该页型的合法键集合内 | PASS（0 处会被静默忽略的键） |
| S4 | 每个 page 的必填键齐全（`text`/`recipe`） | PASS |
| S5 | **zh_cn 与 en_us 完全一一对应** | PASS：文件树、分类 6/6、条目 22/22、**每条页数、页型序列、键集合、sortnum、icon** 全部一致 |
| S6 | 所有 `icon`/`item`/`recipe` 引用可解析 | PASS（30 个去重引用：`minecraft:*` 用**原版 en_us.lang 的 `item.minecraft.*`/`block.minecraft.*` 键**核实，`rpgleveling:*` 用源码注册项核实；`rpgleveling:guide_book` 等 4 个配方**文件真实存在**） |
| S7 | `$(...)` 宏配对 + **「零个裸美元符」核实** | PASS：**全书美元符出现次数 = 0** ⇒ 作者的「完全不用宏」属实，宏配对问题不存在 |

---

## T15-3. B 正文本数值对拍：**125/125 一致，0 处不符（本轮最重要的独立检查）**

### 方法（为什么这条结论可信）

作者的数字是**它自己复算的（= 自证）**。我的做法是**从零独立重算**，路径与作者无关：

1. 从 **`run/config/rpgleveling-common.toml`（运行期真实生成的 26 条线默认值）** 读出 `curve/per_point/cap/half_points/gamma/basis/basis_value`；
2. 从 **mod 自身 `Stat.java`** 读出 13 个维度的 `playerBase / hardMax / equipmentMax`；
3. 独立实现 `V(x)`：`LINEAR → per_point*x`；`SATURATING → cap*x/(x+half)`；
4. 独立实现组装：`TOTAL → (base+equip+V_flat)*(1+V_pct/100)`；`DESIGN_BASELINE → base+equip+V_flat+B_ref*V_pct/100`；
5. 用正则从 **正文** 抽取「投入 X 点 → Y」（中文）/「X points -> Y」（英文）配对，**逐条**与重算值比（容差 `max(0.006, 6e-4·|值|)`，覆盖正文的 2/4 位取整）。

### 结果

```
S8 numeric sample rows: 125 (mismatches: 0)
S8 ceiling claims     : 58  (missing: 0)
```

**13 个维度 × 2 条线 × 4 个采样点（20/50/100/200）× 2 语言 = 208 组数据中，实际命中的 125 组（其余是「每点收益」这类非表格表述）全部一致。** 抽几个非平凡的核对：

| 维度·模式 | 我的重算 20 / 50 / 100 / 200 | 正文 |
|---|---|---|
| armor · percent（B_ref=4.0） | 1.0000 / 1.8182 / 2.5000 / 3.0769 | 同 |
| damage_reduction · flat（60, 80） | 12.0000 / 23.0769 / 33.3333 / 42.8571 | 12.00 / 23.08 / 33.33 / 42.86 |
| crit_chance · percent（B_ref=20） | 4.0000 / 7.6923 / 11.1111 / 14.2857 | 4.00 / 7.69 / 11.11 / 14.29 |
| drop_rate · percent（B_ref=50, cap500, half100） | 41.6667 / 83.3333 / 125.0000 / 166.6667 | 41.67 / 83.33 / 125.00 / 166.67 |
| xp_gain · flat（100, 60） | 25.0000 / 45.4545 / 62.5000 / 76.9231 | 25.00 / 45.45 / 62.50 / 76.92 |
| max_health · percent（TOTAL, 2%/点） | 40 / 100 / 200 / 400 | 同 |
| attack_damage · flat（LINEAR 0.5） | 10 / 25 / 50 / 100 | 同 |

**渐近天花板（58 条断言全部命中）**：我把每条 SAT 线的 `cap`、每条 DESIGN_BASELINE 线的 `B_ref·capPct/100`、以及每维 `sup` 全部独立算出，再要求正文出现该数值。**13 维 sup 全部与 DESIGN §1.16 的 T6 修正版一致**：

```
armor 30 | armor_toughness 20 | movement_speed 1.5 | knockback 0.95 | health_regen 9 HP/s
damage_reduction 80% | crit_chance 70% | lifesteal 30% | drop_rate 400% | xp_gain 350%
attack_damage / max_health / crit_damage = 无（LINEAR，sup = ∞）
```

**组合示例与二次数值也全部正确**：「护甲 20+20 裸装 = 3.0000」、「护甲 200+200 全套下界合金 = 28.0769」、「韧性 200+200 全套 = 18.4744」、「击退 200+200 全套 = 0.8455（保留 15.45%）」、「经验倍率 100+100 = 187.50%（2.875×）」、「200+200 = 243.59%」、「裸装 200+200 护甲 = 8.08」、「移动速度 20+20 → (0.1+0.1)×1.4 = 0.28」。

**原版公式类断言我也独立代入验算**（不是看一眼就算过）：
- 护甲减伤公式 `clamp(clamp(A − D/(2+T/4), 0.2A, 20), 0, 25)/25` 与 1.21.1 `CombatRules` 一致；A=20,T=0：D=20 → **40%**；D≥40 → 落到底钳 **16%** —— 与正文一致。
- A=30：T=0 时 D=40→**40%**、D=80→**24%**；T=20 时同一档 **80% / 74.3%** —— 与正文一致。
- 移动速度「1.5 = 15 倍原版 ≈ 3.24 格/tick」：1.5/0.1=15，4.317×15/20=**3.238** ✔；「服务端 moved too quickly 阈值 ≈ 46 倍原版」：1.21.1 该检查是固定阈值 `distance² > 100`，即 10 格/tick = 200 格/s，200/4.317 = **46.3** ✔，1.5 留 3 倍余量 ✔。

**XP 曲线（`basics/leveling.json`）也独立重算**：8 个单级采样 + 4 个累计全部一致：

```
L1 100 | L10 280 | L50 1080 | L100 2080 | L200 4080 | L201 4504.21 | L300 6904.45 | L500 7572.75
累计 L100 109000 | L200 418000 | L300 1051432.95（正文 1,051,430）| L500 2511415.13（正文 2,511,411）
```

并且我核对了**实现公式**（`RpgMath.java:182-185`：`anchor*(1+logScale*ln(level-until+1))`），与正文写的 `4080×(1+0.15×ln(L−199))` 同式。

**LINEAR 维度是否如实写成「原版 RangedAttribute 硬钳」（lead 特别点名）**：✅ **如实**。
- `max_health` 正文：「唯一边界是原版 RangedAttribute 的硬钳 1024（= 512 颗心）：纯数值线约 **1004** 点，纯百分比线约 **2510** 点」——**我重算 (1024−20)/1.0 = 1004、(1024/20−1)/0.02 = 2510，完全正确**，且明确写成「硬钳」而非设计上限。
- `attack_damage` 正文：同样明确写成 2048 硬钳（数值见 T15-5 的 N-3 观察）。

**其它配置类断言（S10，19 项全对）**：首次奖励 10/15/8/20/10、`per_level=1`、`advancement.enabled=false`、`count_spawner_mobs=false`、结晶 100 / 卷轴 1、`minute_cap=0`、`hps_cap=20`、`hard_cap_hps=20`、`hard_cap_attr=1.5`、`rounding=STOCHASTIC`、`apply_to_blocks=true`、掉落上限 256/64、`include_thorns=false`、`heal_mode=ADD_DELTA`、`only_when_hurt=true`、`require_food_level=0`、`rpgleveling-client.toml` 确实存在（`CLIENT_SPEC` 已注册）、**`lines.*` 恰好 26 段且每段 9 项**。

---

## T15-4. C 构建 / 运行 / 依赖 / 负向对照

### 4.1 构建与 jar

| 步骤 | 结果 |
|---|---|
| `gradlew clean build` | **BUILD SUCCESSFUL**，EXIT=0 |
| jar 存在性 | `build/libs/rpgleveling-1.0.0.jar` **373,192 字节**，204 条目，**76 class** |
| **60 个交付文件是否都进 jar** | **缺 0 个**（57 手册数据文件 + `models/item/guide_book.json` + `recipe/guide_book.json` + `neoforge.mods.toml`），且**jar 内副本与 src 逐字节相同** |
| 书本贴图 | `assets/rpgleveling/textures/item/guide_book.png` 在 jar 内 |
| `[[dependencies.rpgleveling]] modId="patchouli"` | **PASS**：`type="required"`、`ordering="AFTER"`、`side="BOTH"`、`versionRange="[1.21,)"`（解析的是 **jar 内已展开占位符的 toml**，源模板同样含该块） |
| `check_jar.ps1`（T13 旧脚本复跑） | **13 checks, 0 FAIL**（B-03b 占位符已全展开、B-08 无测试类打入、B-10 = 76 class） |

### 4.2 回归（全部我自己复跑）

| 检查 | 结果 |
|---|---|
| `gradlew verifyMath` | **432/432 passed, 0 failed**（含 `layout self check: 178/178`）—— **无回归** |
| `gradlew runGameTestServer` | **`All 40 required tests passed :)`**（10 个 batch 全绿；Patchouli 已装入 mod 列表）—— **无回归** |
| `gradlew runServer` 冷启动 | **ERROR 行数 = 0**；`Loaded 1295 recipes`、`Loaded 1400 advancements`、`Done (1.293s)`；mod list = Minecraft / NeoForge / **Patchouli** / RPG Leveling |

> 配方数 **1294 → 1295**：正是 T14 新增的 `rpgleveling:guide_book`。**手册的配方进入了服务端配方管理器**。
> （WARN 13 条全部是原版/mixin 的既有噪声：argument ambiguity、`patchouli.refmap.json` 开发环境提示等，与本次改动无关。）

### 4.3 负向对照（**我自己设计，证明「零报错」是有判别力的**）

**对照 A —— 破坏配方**：把 `guide_book.json` 的一个原料改成不存在的 `rpgleveling:definitely_not_an_item`。

```
[main/ERROR] [minecraft/RecipeManager]: Parsing error loading recipe rpgleveling:guide_book
com.google.gson.JsonParseException: ... Unknown registry key ... rpgleveling:definitely_not_an_item
Loaded 1294 recipes          <- 从 1295 掉到 1294
```
⇒ **ERROR 3 条 + 配方计数下降**。所以基线的「1295 recipes / 0 ERROR」不是空转。

**对照 B —— 破坏书目规则**：`book.json` 改成 `use_resource_pack: false`，并删掉 `zh_cn/.../basics/overview.json` 的必填 `category`。

```
[modloading-worker-0/ERROR] [patchouli/]: Failed to load book rpgleveling:guide defined by mod rpgleveling, skipping
java.lang.IllegalArgumentException: Book rpgleveling:guide has use_resource_pack set to false.
    at vazkii.patchouli.common.book.Book.<init>(Book.java:148)
```
⇒ **规则确实被强制执行**，且在**专服**上就能看到（Patchouli 在 `NeoForgeModInitializer.onInitialize` 里 `BookRegistry.init` 加载 book.json）。
⇒ **同时暴露一条重要边界**：删掉 entry 的必填 `category` **在专服上没有任何报错**（配方仍 1295、服务器照常 `Done`）。因为**条目/页面内容在 `assets/` 下，只在客户端资源重载时加载**。**这正是「必须真机 runClient 才能确认」的根据**（见 T15-7）。

**还原**：两个对照共改动 4 个文件，全部用备份还原，sha256 **与改前逐字节一致**：

```
802D541B...F8BB0   book.json                 同改前
68FE0923...0FEC4   zh_cn/.../overview.json   同改前
F9C33EDA...93E265E en_us/.../overview.json   同改前
3196A5FA...10F291  data/.../recipe/guide_book.json 同改前
```
还原后我又重跑了一次 `gradlew build`，并校验 **jar 内 60 个文件与 src 逐字节相同**（防止 `processResources` 把对照期间的坏副本留在 `build/resources`）。

### 4.4 同时复跑 T13 的全部独立检查器（限定「Java 侧无回归」）

因为 `Stat.java` 之外的字节码没有专门比对手段，我用**旧检查器全量复跑**来限定回归面：

```
verify_math_independent.py     -> RESULT: 0 failure(s)（26 段配置与源码转写逐条一致）
verify_layout_independent.py   -> RESULT: PASS
check_static.py                -> 6 checks, 0 FAIL
check_dist.py                  -> S-08a PASS（0 个客户端混入类）
check_lang.py                  -> 8 checks, 0 FAIL（zh/en 键集互不缺失）
check_jar.ps1                  -> 13 checks, 0 FAIL
```

---

## T15-5. 缺陷 / 观察清单

**0 BLOCKER / 0 MAJOR。** 以下 3 项 MINOR + 3 项 INFO 均**不阻塞交付**。

### N-1 · MINOR · `health_regen` 正文声称「从不调用原版的 FoodData」，实现里被调用了
- 位置：`zh_cn/entries/utility/health_regen.json:13`、`en_us/.../health_regen.json:13`
- 正文：「它与原版饱食度自然回血是叠加关系、互不干扰：**我们从不调用原版的 FoodData**。」/ "the mod never calls the vanilla FoodData."
- 实测：`PlayerTickHandler.java:37` → `sp.getFoodData().getFoodLevel() < neededFood`，即 **`getFoodData()` 确实被调用**（用于 `require_food_level` 门槛；默认 0 故门槛不生效）。
- **判定**：语义上「不触发原版饱食度回血」是对的，但字面陈述错误。建议改为「不调用原版饱食度**回血逻辑**（仅在 `require_food_level > 0` 时读取饥饿值做门槛）」。

### N-2 · MINOR · 「前 20 点拿到渐近上限的约 20%~30%」区间不准
- 位置：`zh_cn/entries/basics/leveling.json:33`、`en_us/.../leveling.json:33`（EN: "about 20%..30% of the asymptote"）
- 实测（`20/(20+half)`）：half=40 → **33.3%**、60 → **25%**、80 → **20%**、100 → **16.7%** ⇒ 真值区间是 **16.7%~33.3%**，不是 20%~30%。极端两侧都越界（half=100 的 `drop_rate.percent`/`xp_gain.percent` 只有 16.7%；half=40 的四条 flat 线是 33.3%）。
- 来源：`docs/DESIGN.md §1.4` 的同一句话（同样偏差）。**这是「照抄设计文档」而非新错**，建议改成「约 17%~33%」。

### N-3 · INFO · `attack_damage` 的钳制点数字依赖「手持下界合金斧」，但该句未复述假设
- 位置：`zh_cn/entries/offense/attack_damage.json:40`、`en_us/.../attack_damage.json:40`
- 正文：「纯数值线要达到需要约 **4076** 点，纯百分比线需要约 **6793** 点」
- 我的重算：
  - **带 +9 下界合金斧**（该条目 page1/page2 自己建立的上下文）：`(1+9+0.5x)=2048 → x=4076` ✔；`(1+9)(1+0.03x)=2048 → x=6793.3` ✔ —— **完全正确**。
  - **不带武器**：`4094` / `68233`（百分比侧差 **10 倍**）。
- **判定**：句子本身与条目其它页自洽（前面明确写了「+9 斧头」），所以我**不判为数值错误**；但作为「纯线」的一般性陈述，建议补一句「（以手持下界合金斧为例）」。对比：`max_health` 的 1004/2510 无需假设即正确（equipmentMax=0），措辞没有这个问题。

### N-4 · INFO · 自述的 page type 清单一处不准，且揭示一条静默降级路径
- `patchouli:campfire_cooking` **不存在**（真实 id 为 `patchouli:campfire`）；`patchouli:template` **未注册**。
- 未注册的 type 会被 `LexiconPageAdapter` 静默替换为 `PageTemplate`（不报错、不警告）⇒ 任何拼错的页型都会变成「空白页」而不是加载失败。
- **本书未使用**这两个 id，故无实际影响；已由我的检查器覆盖（S4-type 判定 BLOCKER）。

### N-5 · INFO · `PlayerRpgData` 字段 javadoc 仍写着被 T13-3 证伪的来源
- `src/main/java/com/rpgleveling/data/PlayerRpgData.java:31-32`：`bonusPoints` 注释 "(the skill scroll item, **admin commands**)"，而 T13-3 已证明**没有任何 admin 路径**调用 `addBonusPoints`。仅注释，无功能影响。

### N-6 · INFO · T13 的「冻结基线」哈希与实际源码树不再一致（账目偏差，非功能缺陷）
- 见 T15-6.2。

---

## T15-6. 与 T13 的关系：**26/26 属性层 + 26/26 效果层判定不受影响**

### 6.1 结论

**不受影响，且我重新做了行为层复跑。** 依据：

1. **T14 没有改任何 Java 文件**：`src/` 下 2026-10-04（T14 全部活动时段）被修改的文件**全部是手册资源**（`patchouli_books/**`、`models/item/guide_book.json`、`recipe/guide_book.json`、`textures/item/guide_book.png`）与 `META-INF/neoforge.mods.toml`；**没有任何 `.java`**。手册条目的正文数字是对 `DESIGN.md`/`PlayerRpgData` 等现状的**描述**，不构成对 Java 的改动。
2. **曲线/上限/点数语义未被触碰**：`run/config/rpgleveling-common.toml` 的 26 段与 T13 一致（我的独立检查器 `verify_math_independent.py` 逐段与源码转写比对，**0 failure**）；T6 修正后的 cap（armor 6 / toughness 5 / knockback 0.35 …）与 `Stat.java` 的 `hardMax/equipmentMax` 均未变。
3. **行为层证据**：`verifyMath` **432/432**（与 T13 完全同值）、`runGameTestServer` **All 40 required tests passed**（与 T13 完全同值）、`runServer` **0 ERROR**、`check_dist` **0 客户端混入类**、`check_lang` **8/8**、`check_jar` **13/13**、`check_static` **6/6**。
4. ⇒ **`§T13-6` 的「26/26 属性层 ✅ ｜ 26/26 效果层 ✅」继续成立。**

### 6.2 但我发现一处**必须写进报告的基线账目偏差**（诚实说明）

我用 T13 的 `SOURCE-MANIFEST.sha256`（92 文件）逐个复算当前 `src/`：

```
identical : 90 / 92
changed   : 2
  * src/main/java/com/rpgleveling/data/PlayerRpgData.java
  * src/main/resources/META-INF/neoforge.mods.toml   <- T14 的写域，预期内
new files : 60（全部是手册资源 + guide_book 贴图）
```

- `neoforge.mods.toml` 的改动是 T14 本任务（追加 patchouli 依赖），**预期内**。
- `PlayerRpgData.java` 的哈希与 T13 清单不符，且其 **mtime = 2026-10-03 16:22:21，比 T13 清单的写入时间 16:21:15 晚 66 秒**（T13 的 jar/清单已生成、但该文件随后被改）。逐版哈希轨迹：T10 `79F6…` → T12 `D779…` → T13 `8091…` → 现状 `2A09…`。
- **改动内容与 T13-3 的建议完全对应**：现状 L127-131 的 javadoc 是「the item path (skill scroll) only」，即 T13-3 要求的「把 'and the admin path' 改成 'the item path (skill scroll) only'」——**一处纯 javadoc 修改**。
- **我无法做 T13 jar ↔ 现状的逐字节 class 比对**：T13 只保留了 jar 的 SHA-256（`096B56…`）与字节数（306,055），**没有留存 jar 副本**；Gradle build-cache 里也没有 T13 期的条目（仅两个 2026-08-13 的无关条目）。
- **因此我用可用的最强手段把它钉住**：(a) 该文件是**唯一**与 T13 清单不符的 Java 文件，且其 mtime 早于 T14 全部活动；(b) 改动文本与 T13-3 建议逐字对应；(c) 修订后 **class 总数仍为 76**、`verifyMath` 432/432、GameTest 40/40、以及 T13 的 5 个独立检查器全绿。
- **判定**：**不构成对 26/26 判定的影响**；但 **T13 冻结基线里 `JAR_SHA256`/`SOURCE_TREE_SHA256` 已不再等于当前树**，这是 T13 的**账目/流程缺口**（先冻结、后改文件、未重生成清单），**建议 lead 知悉**：以后冻结需「最后一个写入者之后再生成清单/重建 jar」。

---

## T15-7. **必须由真机 `runClient` 才能确认的残余项**（我无法在专服/无头环境闭环）

我的负向对照 B 已**实证**：删掉 entry 的必填 `category`，专服**毫无反应**。原因是书内容（`assets/**/patchouli_books/**`）由 Patchouli 的**客户端资源监听器**加载。因此下列各项**只能真机 runClient**：

| # | 步骤 | 通过标准 | ✓ |
|---|---|---|---|
| B-01 | 进世界按 **K** 打开手册（或手持指南书右键） | 书能打开，**不出现 "Failed to load book"**，目录页正常 | ☐ |
| B-02 | 浏览 6 个分类 | 入门指南 / 进攻 / 防御 / 实用 / 物品 / 参考 都在，**每个分类非空** | ☐ |
| B-03 | 逐个打开 22 个条目 | 每个语言 91 个 `text` 页 + 4 个 `crafting` 页全部渲染；**无空白页**（空白页 = 页型或键被静默忽略的信号） | ☐ |
| B-04 | 看 4 个 `patchouli:crafting` 页 | 配方网格与产物图标正确显示（`guide_book`/`experience_crystal`/`respec_token`/`skill_scroll`）；**若配方 id 写错，此页会显示「配方缺失」而不报错** | ☐ |
| B-05 | 切中文 / 英文后再看同一批条目 | 两份内容都对；**不出现原始 lang 键** | ☐ |
| B-06 | 看 `book.json` 的 `i18n: false` 效果 | 书本标题/副标题/落地文本是**中英混排的字面量**（书名 "RPG 加点手册 / RPG Leveling Guide"）——确认这是可接受的观感 | ☐ |
| B-07 | 看创造模式物品栏 | 指南书出现在 `rpgleveling:main` 标签页（`creative_tab` 指向的标签确实存在）；书皮用 `book_blue` 纹理、进度条开启 | ☐ |
| B-08 | 用 **JEI/REI** 查 `guide_book` 配方 | 无序合成：书 + 金锭 + 经验结晶；产物指向 `patchouli:guide_book` 且带 `patchouli:book=rpgleveling:guide` 组件 | ☐ |
| B-09 | 手册里 `icon`（20 种物品）逐个看 | 图标不显示为「紫黑缺失方块」（我已在静态层核实 id 全部真实，但贴图/模型存在性只有客户端能看） | ☐ |
| B-10 | 打开手册后再打开 RPG 加点界面 | 两者互不冲突；宏系统未被触发（本书 0 个美元符） | ☐ |

---

## T15-8. 证据索引（T15）

| 文件 | 内容 |
|---|---|
| `_verify/tools/t15_page_fields.py` | **本轮新增**：从 Patchouli jar 字节码提取 page type 注册表 + 每型合法键集 + book/category/entry 的 json 键 |
| `_verify/tools/t15_extract_page_schema.py` | 早期版本：解析器/字段提取的推导过程留档 |
| `_verify/tools/t15_book_check.py` | **本轮新增**：书目结构 + 双语对等 + 引用 + 宏 + **数值对拍** 一体化独立检查器 |
| `_verify/tools/t15_check_jar.py` | **本轮新增**：jar 内容 / 逐字节一致 / 依赖块解析 |
| `_verify/work/page_fields.json` | 上面前两个脚本的原始输出（规则推导证据） |
| `_verify/evidence/T15/BOOK-MANIFEST.sha256` | 60 个交付文件逐文件 sha256 |
| `_verify/evidence/T15/book_check.json` / `.log` | 结构 + 数值对拍完整结果（含 125 行采样明细与 58 条天花板断言） |
| `_verify/evidence/T15/01-clean-build.log` | `clean build` |
| `_verify/evidence/T15/02-verifyMath.log` | 432/432 |
| `_verify/evidence/T15/03-gametest.log` | `All 40 required tests passed :)` |
| `_verify/evidence/T15/04-check_jar.log` / `04b-check_jar.log` | 我的 jar 检查器 + T13 的 check_jar.ps1（13/13） |
| `_verify/evidence/T15/05-postrestore-build.log` | 还原后重建（确认对照无残留） |
| `_verify/evidence/T15/06-check_static.log` / `07-check_dist.log` / `08-check_lang.log` | T13 旧脚本复跑（6/6、PASS、8/8） |
| `_verify/evidence/T15/09-runServer-cold.log` | **专服冷启动：0 ERROR，1295 recipes，Done (1.293s)** |
| `_verify/evidence/T15/10-independent-math.log` / `11-layout-independent.log` | 0 failure / PASS |
| `_verify/evidence/T15/X1-negctl-bad-recipe.log` | **负向对照 A**（3 ERROR，1295→1294） |
| `_verify/evidence/T15/X2-negctl-bad-book.log` | **负向对照 B**（Book.java:148 异常；entry 校验专服不可见） |
| `_verify/evidence/T15/negctl/*.bak` | 对照前的 4 个文件备份（还原依据） |

---

# T13 最终结论（**已被 T15 取代为最新；但仍是 Java / 数值层的冻结基线**）

## T13-0. 最终判定：**PASS —— 可交付**

T12 唯一必修项 **T12-D1 已验证修复**；T12-D2 的 javadoc 亦已修正（新引入一处措辞不准确，见 T13-3，属 INFO，不阻塞）。全部自动化检查独立复跑通过。

| # | 项目 | 结论 |
|---|---|---|
| 1 | **T12-D1 表头重叠修复** | ✅ **VERIFIED** —— 修复后**可达违例 0**（两种字宽模型）；且检查器**仍能抓出修复前的 3518 例**，证明它不是被调成"通过"的 |
| 2 | clean build / verifyMath / GameTest / runServer / 四套脚本 | ✅ 全部 **PASS** |
| 3 | T12-D2 javadoc | ⚠️ 已改对，但新增一处措辞不准确（T13-D1，INFO） |
| 4 | T12-D3/D4/D5（INFO，lead 接受不改） | ✅ 我**同意接受**，理由见 T13-4 |

### 冻结基线（最终交付版）

```
产物 : build/libs/rpgleveling-1.0.0.jar
大小 : 306055 字节      条目 122   class 76
SHA-256 : 096B560B553B0B3C05F582EE7ABFCEF6EE53F14CE6A5C9D7CE89469342A3C986

源码树 : src/ 共 92 个文件
SOURCE-TREE-SHA256 :
         2B942EF3E4613A20932B2CF46B8455BF40B20DBAD683AC736E356BF998771AF3
逐文件清单 : _verify/evidence/T13/SOURCE-MANIFEST.sha256
```

---

## T13-1. T12-D1 复验：**VERIFIED**

### 方法（先说清楚为什么这个结论可信）
我把检查器改成**忠实建模新代码的截断语义**，而不是简单"把期望改小"：
```java
// StatRow(L90-93) 与修复后的 RpgScreen(L195-200) 用的是同一套：
if (font.width(s) > room) s = font.plainSubstrByWidth(s, room - 6) + "…";
```
`plainSubstrByWidth(s, w)` 返回宽度 **≤ w** 的前缀，之后**再追加省略号**。而 `…` 不在 `ascii.png` 里，会回退到 **unicode 字体 ≈ 9px** ⇒ 截断后实际宽度可达 `room + 3`。**我按这个真实上界建模**（而不是乐观地假设它 ≤ room），因此该检查是**对抗性**的。

同时保留 `header=old`（修复前：表头按自然宽度绘制）作为**对照**。

### 结果
```
header=new ellipsis=6px  ->  reachable=   0  unreachable= 10   PASS
header=new ellipsis=9px  ->  reachable=   0  unreachable= 10   PASS
header=old ellipsis=6px  ->  reachable=3518  unreachable= 15   FAIL
header=old ellipsis=9px  ->  reachable=3518  unreachable= 15   FAIL

fix removes every reachable violation : YES
checker still detects the pre-fix bug  : YES
RESULT: PASS
```

**结论**：
- 修复后，**在所有 ≥320×240 的可达逻辑尺寸、中英文、两种字宽模型下，任意两段绘制区间均不相交** ⇒ 唯一必修项关闭。
- 检查器在修复前的代码上**仍然报 3518 例** ⇒ 它没有被调成"永远通过"；这条 PASS 是有判别力的。
- 400px 的 `COL_GAP` 余量（10px）**足以吸收** `room+3` 的截断溢出，所以即便按 unicode 省略号宽度建模也不会越界。
- 剩余 `unreachable=10` 全部位于**逻辑尺寸 < 320×240**（MC 的 `calculateScale` 保证逻辑分辨率不低于 320×240），属 T12-D5，**不可达**。

---

## T13-2. 回归（全部独立复跑，未引用任何自述）

| 检查 | 结果 |
|---|---|
| `gradlew clean build` | **BUILD SUCCESSFUL in 3s**，EXIT=0；jar **306,055 字节** |
| `gradlew verifyMath` | **432/432 passed, 0 failed**（含 `layout self check: 178/178`） |
| `gradlew runGameTestServer` | **`All 40 required tests passed :)`** |
| `gradlew runServer`（S-08b） | **`Done`**，无 ERROR/Exception（见 T13-5） |
| `check_jar` | **13/13**（B-08 0 FAIL；B-10 = **76 classes**） |
| `check_static` | **6/6** |
| `check_dist` | **S-08a PASS**（0 危险类） |
| `check_lang` | **8 checks, 0 FAIL** |
| INV-1 字节码扫描 | `PlayerXpEvent` / `giveExperiencePoints` / `giveExperienceLevels` / `addExperienceLevels` 全部 **0 命中**（76 class） |

---

## T13-3. T12-D2 修正复核 —— 改对了，但引入一处措辞不准确（**T13-D1，INFO**）

**已改对**：`PlayerRpgData.addBonusPoints` 的 javadoc 现为
```
Grants (or removes) unspent points without touching totalXp.
Persisted - this is the item path (skill scroll) and the admin path, so a
granted point must survive a relog. Debug scaffolding uses addDebugPoints.
```
"Persisted" 与 "Debug scaffolding uses addDebugPoints" 都**准确** ✓。

**⚠️ 但 "and the admin path" 不成立**：我核查了全部调用点
```
addBonusPoints  <- 只被 RpgService.grantPoints (L219) 调用
grantPoints     <- 只被 SkillScrollItem (L28) 调用
/rpg admin setpoints -> RpgCommand.adminSetPoints (L388-399) -> RpgService.setPoints(L397)
                                   -> data.setPointsExact(line, points)   ← 写的是【该行的已投点数】
```
⇒ **没有任何 admin 路径会调用 `addBonusPoints`。** `/rpg admin setpoints` 设的是"某条线已投多少点"，与 `bonusPoints` 无关；调试加点走的是 `addDebugPoints`。
**影响**：纯文档。会误导后续维护者以为"admin 发的点数会以 bonus 形式持久化"。**级别 INFO，不阻塞交付**；建议把那半句改为 "the item path (skill scroll) only"。

---

## T13-4. 对 T12-D3/D4/D5 的处理意见：**同意接受，不要求改动**

- **D3（`MIN_PANEL_W` 生产代码不用）**：我原本标 INFO，**同意保持**。它是离线检查器的锚点常量（`LayoutSelfCheck` 用它取"首选宽度"做对照），不是生产约束。
  **唯一提醒**：因为没有最小面板宽度，`nameMaxW` 在 320 宽时只有 6px，表头/名称会退化成 "…"。这是**设计上的降级**而非缺陷 —— 而且经 T12-D1 修复后**不再重叠**。若将来想让窄窗口更可读，可在 `panel()` 里真正启用 `MIN_PANEL_W` 或至少保证 `nameMaxW ≥ 24`；**当前不作为交付条件**。
- **D4（`MODE_W=42` vs `"percent"` 40px，余量 2px）**：**同意保持**。它只在**换更长的翻译或更宽字体**时才会越界，而 mod 只内置 en_us / zh_cn 两套；且我已在 T13-1 的两种字宽模型下验证当前两套语言**均不越界**（含对抗模型）。列为**已知余量**即可。
- **D5（<320×240 时 level 文本重叠）**：**同意保持**。MC 的 `calculateScale` 保证逻辑分辨率 ≥ 320×240，该区间**正常不可达**；我的检查器也把它单列为 unreachable。

---

## T13-5. 证据索引（T13）

| 文件 | 内容 |
|---|---|
| `_verify/evidence/T13/10-layout-independent.log` | **四情景布局复验（old/new × 省略号 6/9px）** |
| `_verify/evidence/T13/01-clean-build.log` | clean build |
| `_verify/evidence/T13/02-verifyMath.log` | 432/432 |
| `_verify/evidence/T13/03-gametest.log` | `All 40 required tests passed :)` |
| `_verify/evidence/T13/09-runServer.log` | 专服冷启动 |
| `_verify/evidence/T13/{04-check_jar,05-check_static,06-check_dist,07-check_lang}` | 四套脚本输出 |
| `_verify/evidence/T13/FROZEN-BASELINE.txt` | **最终冻结基线** |
| `_verify/evidence/T13/SOURCE-MANIFEST.sha256` | 92 个源文件逐文件哈希 |
| `_verify/tools/verify_layout_independent.py` | 独立布局证伪器（支持 old/new × 省略号宽度情景） |

---

## T13-6. 未闭环项（累计，全部不阻塞交付）

**阻塞交付项：无。**

| 类别 | 项 |
|---|---|
| **需人工确认** | M-1 GUI 实际观感（**建议顺带看一眼 320×331 逻辑宽度下表头**，我的结论来自离线几何重算，未见真机）；M-2 双客户端/20 人压力；M-3 与 stellersword 共存；M-4 真人穿全套下界合金（原版 tick 装配路径）；M-5 三个新物品的手感与配方平衡 |
| **已知 INFO（接受）** | T10-D4（armor 余量 ~5e-5）、T12-D3/D4/D5、T13-D1（javadoc 措辞） |
| **待产品确认** | T4-D5：击杀时同时给 mod 经验与原版经验球（设计选择，非缺陷） |

**§6 的 26 行判定表**：**26/26 属性层 ✅ ｜ 26/26 效果层 ✅** —— 自 T10 起未再变化（T11/T13 未触及任何维度、曲线、上限或点数语义）。

---

# T12 独立验证结论（**已被 T13 取代**）

## T12-0. 总判定：**CONDITIONAL PASS —— 1 项必修（一行修复）**

> **审查姿态**：T11 的代码由 coder 在其上下文超限、auto-review 拒绝全部工具调用的情况下，用 program 内 `node:fs` 直接落盘，**未经它自己的审查**。我按"未经审查的新代码"对待，并**证伪**了它的核心自证（见 T12-2）。

| 审查项 | 结论 | 依据 |
|---|---|---|
| 1. 「错位不可能」 | ❌ **FAIL（被证伪）** | 独立重算：**3518 组可达尺寸存在区间相交**；`Stat` 表头与 `Mode` 表头在逻辑宽度 **320–331** 重叠 8–12px，**中英文都中招** |
| 2. `panel.png` 九宫格 | ✅ **PASS** | 7 参 `blit` 硬编码 256×256 属实；`panel.png` 192×192、四角 1:1 不拉伸；`backdrop` 512×288 匹配 |
| 3. `bonus_points`/`debug_points` 契约 | ✅ **PASS** | debug **transient 不落盘**、物品点 **落盘**、洗点**保留** bonus、旧存档**不炸** |
| 4. 新物品服务端权威性 | ✅ **PASS** | 客户端分支不写；失败**不消耗**；经验走 `XpService`，**`xp_gain` 只乘一次** |
| 回归（构建/静态/GameTest/专服） | ✅ **PASS** | 见 T12-7 |

**唯一必修项**：`RpgScreen.drawHeader` 的 `Stat` 表头未按 `nameMaxW` 截断。**根因单一、修复为一行**；影响面是窄窗口下两段表头文字视觉重叠，**不影响功能、数据或服务端**。故不判 FAIL 交付，但 lead 设定的验收线（"任何一例相交即判 FAIL"）**未达成**。

---

## T12-1. 冻结基线

```
产物 : build/libs/rpgleveling-1.0.0.jar
大小 : 305895 字节      条目 122   class 76
SHA-256 : 541C97F160685679EFF22A3773907A958434A36E34407FAD8CEA469E2702F9AD

源码树 : src/ 共 92 个文件
SOURCE-TREE-SHA256 :
         3F50C52A7A06CF235E56E6C33581C36576AD5D2F92144F6ACEB231E84399D8AD
逐文件清单 : _verify/evidence/T12/SOURCE-MANIFEST.sha256
```

---

## T12-2. 审查项 1：「错位」是否真的不可能 —— **证伪成功，判 FAIL**

### 方法
新增 **独立**检查器 `_verify/tools/verify_layout_independent.py`（**不复用** `LayoutSelfCheck`）：
1. 按 `RpgLayout.java` 的常量与级联规则**手工重新实现** `panel/frame/columns`；
2. 不只算"列盒"，而是建模**实际绘制区间**——包括**右对齐文本**（`pointsRight - font.width(t)`、`valueRight - font.width(t)`、`buttonsRight - font.width(t)`）与**左对齐但未截断的表头**；
3. 两种字宽模型：**A** 保守（ASCII 6px / 非 ASCII 9px）、**B** 对抗（7px / 10px，模拟更宽字体或强制 Unicode）；
4. 扫描 **59,264 组** (尺寸 × 语言 × 字宽模型)，断言同一 band 内任意两区间**不相交**；
5. 按 MC 的 `calculateScale`（逻辑分辨率 ≥ 320×240）**区分可达/不可达**。

### 结果：**FAIL**
```
reachable  (>= 320x240): 3518 violation(s)
unreachable(<  320x240):   11 violation(s)

e.g. 320x240 lang=en model=A band=caption_band
     hdr.stat[52,76)  vs  hdr.mode[68,92)   overlap=8 px
e.g. 320x240 lang=en model=B band=caption_band
     hdr.stat[52,80)  vs  hdr.mode[68,96)   overlap=12 px
caption_band collides for every reachable width in: 320..331
reachable affected: langs=['en','zh']  models=['A','B']  bands=['caption_band']
```

### 根因（精确到行）
- `RpgScreen.java:192-193`：`Stat` 表头**左对齐画在 `columns.nameX()`，宽度不受任何约束**；
- 而 `StatRow.java:90-93` 对**行内名称**做了 `nameMaxW` 截断；
- ⇒ 窄窗口下 `columns()` 把 `nameMaxW` 压到 6px（320 宽时 `nameX=52, modeX=68, nameMaxW=6`），**行内名称被正确截断，表头却按 24px 原宽画出去**，压到 `Mode` 表头上。
- 对照：`points/value/action` 三段表头都是**右对齐**、天然不越界；只有 `Stat` 与 `Mode` 是左对齐且无约束。

### 修复建议（一行）
在 `drawHeader` 里把 `Stat` 表头按 `nameMaxW` 截断，与行内名称一致：
```java
String statCaption = Component.translatable("gui.rpgleveling.column.stat").getString();
int room = Math.max(0, columns.nameMaxW());
if (font.width(statCaption) > room) statCaption = font.plainSubstrByWidth(statCaption, Math.max(0, room - 6)) + "\u2026";
graphics.drawString(font, statCaption, columns.nameX(), captionY, RpgTheme.TEXT_MUTED);
```
（或把 `Mode` 表头改为右对齐到 `modeX+MODE_W`，使两者都向右生长。）

### 对 coder 的公平说明
- **行 band 无任何违例** —— 26 行的名称截断、模式/点数/数值列都正确，说明 T11 的级联逻辑本身是好的；
- 违例只出现在`caption_band`。即"列不会重叠"基本成立，**唯独两段表头文字漏了约束**。
- coder 的 178 条不变量之所以没抓到：它们（据其命名与 `LayoutSelfCheck` 的调用方式）校验的是**列盒几何**，而表头文字宽度**不在 `RpgLayout` 里**（在 `RpgScreen` 用 `font.width` 现算），属于**自证覆盖不到的区域**。

---

## T12-3. 审查项 2：`panel.png` 九宫格 —— **PASS**

### (a) 「7 参 `blit` 硬编码 256×256」——**属实，已从反编译源码核实**
`net/minecraft/client/gui/GuiGraphics.java:490-491`：
```java
public void blit(ResourceLocation tex, int x, int y, int u, int v, int w, int h) {
    this.blit(tex, x, y, 0, (float)u, (float)v, w, h, 256, 256);   // ← 写死 256
}
```
⇒ 对 192×192 的贴图用 7 参重载，UV 会按 256×256 换算 ⇒ **采样错位**。coder 的判断正确。

### (b) 实际 9 次 blit —— **全部使用 11 参重载并显式传 192×192**（`RpgScreen.java:147-158`）

| # | 目标尺寸 | 源区域 | 判定 |
|---|---|---|---|
| 左上/右上/左下/右下 | `64×64` | `64×64`（四角） | ✅ **1:1，不拉伸** |
| 上/下边 | `midW×64` | `64×64` | ✅ 仅沿边方向平铺 |
| 左/右边 | `64×midH` | `64×64` | ✅ |
| 中心 | `midW×midH` | `64×64` | ✅ |

### (c) 贴图实际尺寸（独立读 PNG 头）
| 文件 | 实际 | 代码期望 | 判定 |
|---|---|---|---|
| `panel.png` | **192×192** | `PANEL_TEX_W/H=192`，`SLICE=64`（192=3×64） | ✅ |
| `backdrop.png` | **512×288** | `BACKDROP_TEX_W/H=512×288` | ✅ |

### (d) 虚化：`RpgScreen.java:92-100` 的 `renderBackground` **明确不调用 `super`**，改为"不透明底色 → backdrop 铺满 → 压暗层"三步 ⇒ **不会再触发 `renderBlurredBackground` 整屏高斯模糊** ✓
（`panel.width()<128` 时回退为整图 blit，`RpgScreen.java:130-133`，也不会崩。）

---

## T12-4. 审查项 3：`bonus_points` / `debug_points` 契约 —— **PASS**

| 契约 | 实现 | 判定 |
|---|---|---|
| `/rpg debug addpoints` **不落盘** | `RpgCommand.java:299` → `RpgService.addPoints`(L203) → `data.addDebugPoints`(L205)；`debugPoints` 声明为 **`transient`**（`PlayerRpgData.java:40`）⇒ 永不进 NBT | ✅ |
| 物品发的点**落盘** | `SkillScrollItem` → `RpgService.grantPoints`(L216) → `data.addBonusPoints`(L219)；`serializeNBT` **写入** `"bonus_points"`(L196)、`deserializeNBT` 读回(L215) | ✅ |
| 两者都计入可用点数 | `earnedPoints() = cachedEarnedPoints + bonusPoints + debugPoints`(L57-59) | ✅ |
| **洗点保留 bonus** | `respecAll()`(L161-164) **只** `Arrays.fill(points,0)` + `respecCount++`，**不动** bonus/debug | ✅ 我最初误读到 `resetAll()`(L166-172)，已更正：那是 `/rpg admin reset`，清空全部是合理的 |
| 旧存档向后兼容 | 缺 `bonus_points` → `tag.getLong` 返回 0 → `Math.max(0,0)`(L215) **不炸**；`points` 数组长度不符时有守卫 + 告警(L204-213) | ✅ |

---

## T12-5. 审查项 4：新物品服务端权威性 —— **PASS**

| 要求 | 实现 | 判定 |
|---|---|---|
| 客户端分支不写数据 | `RpgConsumableItem.use` L43-46：`level.isClientSide()` **直接 return**，注释明示"never mutates player data" | ✅ |
| 失败**不消耗** | L51-54：`apply` 返回 false → 提示 + `InteractionResultHolder.fail(stack)`；**`stack.shrink(1)` 只在成功分支 L56** | ✅ |
| 洗点令牌：无可洗点则失败 | `RespecTokenItem` L24-27：`spentPoints()<=0` 先判 false，否则 `RpgService.respec` | ✅ |
| 技能卷轴：走唯一写入口 | `SkillScrollItem` → `RpgService.grantPoints` | ✅ |
| 经验水晶：`xp_gain` **只乘一次**（INV-2） | `ExperienceCrystalItem` L29 → `RpgService.grantXp`(L233-236) → **`XpService.grant(player, XpSource.OTHER, amount)`**，即唯一的倍率应用点 | ✅ |
| 注册/模型/配方/lang | `RpgItems` 注册 3 项；3 个 model + 3 个 recipe 齐备；**12 个物品 lang 键在 en_us 与 zh_cn 全部存在**（0 缺失） | ✅ |
| INV-1（不得监听 `PlayerXpEvent`）| 字节码扫描 76 个 class：`PlayerXpEvent` **0 命中** | ✅ |

---

## T12-6. 缺陷清单

| ID | 级别 | 问题 | 建议 |
|---|---|---|---|
| **T12-D1** | **MEDIUM（必修）** | 表头 `Stat` 未截断，逻辑宽度 320–331 时与 `Mode` 表头重叠 8–12px（中英文皆然） | 见 T12-2 的一行修复 |
| **T12-D2** | LOW | `PlayerRpgData.addBonusPoints` 的 javadoc(L127-130) 仍写「Debug/test helper … **Transient by construction**」，但该方法已是**物品的持久化**路径（`bonus_points` 会落盘）。T11 拆分时把旧注释留在了错误的方法上，会误导后续维护者 | 改注释为「item-granted, persisted」 |
| **T12-D3** | INFO | `RpgLayout.MIN_PANEL_W = 480` 只被 `LayoutSelfCheck` 使用，**生产 `panel()` 完全不引用**（只用 `MAX_PANEL_W`）⇒ 没有最小面板宽度保证，这正是 320 宽时 `nameMaxW` 被压到 6px 的原因 | 要么在 `panel()` 里真正生效，要么删除以免误导 |
| **T12-D4** | INFO | `MODE_W = 42` 而英文 `"percent"` 实测约 40px，余量仅 2px；换更长的翻译或更宽字体会立刻越界 | 表头改右对齐，或给模式列留余量 |
| **T12-D5** | INFO | 逻辑尺寸 < 320×240 时 `level` 与 `Available points` 文本重叠（54–63px）。正常 GUI 缩放不可达（MC 保证 ≥320×240），仅当 OS 窗口被拖到极小才可能出现 | 可选：两段文本也做宽度约束 |

---

## T12-7. 我独立复跑的回归（未引用任何自述）

| 检查 | 结果 |
|---|---|
| `gradlew clean build` | **BUILD SUCCESSFUL in 3s**；jar **305,895 字节** |
| `gradlew verifyMath` | **432/432 passed, 0 failed**（含 `layout self check: 178/178`） |
| `gradlew runGameTestServer` | **`All 40 required tests passed :)`** |
| `gradlew runServer`（S-08b） | **`Done (0.589s)!`**，无 ERROR/Exception |
| `check_jar` | **13/13**（B-08 0 FAIL；B-10 = **76 classes**） |
| `check_static` | **6/6** |
| `check_dist` | **S-08a PASS**（0 危险类） |
| `check_lang` | **8 checks, 0 FAIL** |
| INV-1 字节码 | `PlayerXpEvent` 等 **0 命中**（76 class） |
| 物品 lang 键 | 12/12 在两种语言均存在 |

---

## T12-8. 与前几轮的关系 / §6 判定表

- **§6 的 26 行判定表不受 T11 影响**：T11 未改动任何属性维度、曲线、上限或 `RpgService` 的点数语义；`verifyMath` 由 254→432（新增的是**布局**不变量，不是数值维度）。故 §6 的 **26/26 属性层 ✅ ｜ 26/26 效果层 ✅** 结论**继续有效**。
- T10 的 3 条非阻塞缺陷（T10-D1/D2/D3）状态不变。
- **T12 新增未闭环项**：仅 T12-D1（必修）。

### 需人工确认（不阻塞，累计）
M-1 GUI 实际观感（**并请特别确认 320–331 逻辑宽度下表头是否视觉重叠** —— 我的结论来自离线几何重算，未见真机）、M-2 双客户端/20 人压力、M-3 与 stellersword 共存、M-4 真人穿全套下界合金、M-5 三个新物品的实际手感与配方平衡。

---

# T10 终验结论（**已被 T12 取代**）

## T10-0. 最终判定：**PASS —— 可冻结交付**

| 验收层 | T8 | **T10** | 依据 |
|---|---|---|---|
| L0 构建 | PASS | ✅ **PASS** | jar **136,858 字节**、65 class；13/13 |
| L1 静态 | PASS | ✅ **PASS** | check_static 6/6、check_dist 0 危险、check_lang 8/0、INV-1 字节码 0 命中 |
| L2 数学/安全证明 | PASS | ✅ **PASS** | `verifyMath` **254/254** |
| L3 专服 S-08b | PASS | ✅ **PASS** | **`Done (0.561s)!`**，无 `ERROR`/`Exception` |
| L3b GameTest | 22/22 | ✅ **PASS** | **`All 33 required tests passed :)`**（9 批次） |
| L4 属性接入层 | 26/26 | ✅ **PASS** | `lines` 批次 26/26 |
| **L4 玩法效果层** | ❌ 6 个维度无测试 | ✅ **PASS（已闭环）** | 新增 `CombatEffectSuite` 10 用例，6 个派生访问器**全部被执行并观测** |
| L5 客户端/联机回归 | NOT VERIFIED | ⚠️ **需人工**（非阻塞） | 见 T10-7 |

**无阻塞交付项。** 发现的 3 个问题全部为 LOW/INFO 级、已量化、不阻塞发布（T10-5）。

---

## T10-1. 冻结基线（交付修订版）

```
产物 : build/libs/rpgleveling-1.0.0.jar
大小 : 136858 字节      条目 91   class 65
SHA-256 : 4E4CA1ED9589B88D7E0EB3D92D98DE3A0BA93D109DA80E26FC766499D5005E9B

源码树 : src/ 共 69 个文件（main 58 / test 11）
SOURCE-TREE-SHA256 (文件名+内容，排序后聚合) :
         2DFBA081FF43CA4372C8A903D239288D867C54687A582AACFD78A6CF0708AF39
逐文件清单 : _verify/evidence/T10/SOURCE-MANIFEST.sha256
```

> 变异实验全部在隔离副本 `_verify/mutant/` 内完成并已删除。**实验前后 jar 的 SHA-256 完全一致**（`4E4CA1ED…`），证明 `src/**` 与产物全程未被改动。

---

## T10-2. lifesteal 过度击杀修复 —— **核心修复正确，但"防泄漏"注释与实现不符**

### (a) 截断是否正确：**是**（源码 + 实测 + 变异三重证实）

`CombatEvents.java:162-164`：
```java
double damageDealt = event.getNewDamage();
Float healthBefore = PRE_HIT_HEALTH.remove(victim.getId());
if (healthBefore != null) damageDealt = Math.min(damageDealt, healthBefore);
```
实测证据（33/33 通过的那次运行）：`lifesteal.healedFromOverkill=2.0` —— 20 血目标被 100 伤害打死，吸血 **2.0**（= 20 × 10%），而非旧行为的 10.0。**与 DESIGN §1.10 一致。**

### (b) 内存上限是否生效：**是**

`CombatEvents.java:120-121`：每次 `put` 前 `if (PRE_HIT_HEALTH.size() > 4096) PRE_HIT_HEALTH.clear();`。
⇒ 上限 **4097 条目**，最坏约 260 KB。**上限真实生效，不会无界增长。**

### (c) ⚠️ 但注释「a value this high only happens under a bug」**是错的** —— 泄漏是常态

`remove` **只**发生在 `onLivingDamagePost:163`，而该行之前有 5 个提前 `return`。逐条列出 `put` 后不会被消费的路径：

| # | 路径 | 触发条件 | 是否常见 |
|---|---|---|---|
| 1 | L147 `victim == attacker` | 玩家被自己造成的伤害（自己的箭/爆炸/三叉戟） | 偶发 |
| 2 | L148 thorns 且未开启 | 荆棘反伤 | 偶发 |
| 3 | **L151 `percent <= 0`** | **攻击者没投生命偷取点** | **常态（绝大多数玩家）** |
| 4 | L158 单 tick 预算耗尽 | 高频命中 | 常见 |
| 5 | Post 根本不触发 | Pre 之后伤害被取消 / 实体提前死亡 | 偶发 |

⇒ **路径 3 意味着：只要玩家没投偷取点，每一次命中都会永久留下一个条目。** 因此 4096 的清空**不是"只在 bug 时发生"的兜底，而是日常会走到的路径**。
**影响面**：内存有界（约 260 KB），**无功能性危害**，属 LOW。但它会让"顺带清空"丢掉一条合法待消费条目（后果仅是那一次少截断，方向安全）。

### (d) ⚠️ 并发竞态（多玩家同 tick 打同一目标时会退化为修复前的行为）

映射的 key **只有 `victim.getId()`**，不含攻击者。两个玩家同 tick 打同一目标时：
A 的 Pre 写入 (V,h1) → B 的 Pre 覆写为 (V,h2) → A 的 Post `remove` 拿到 h2（对 A 是错的）→ B 的 Post 拿到 `null` ⇒ **不做截断，直接用原始 overkill 伤害吸血**，正是 T9 修掉的那个缺陷。
**影响面**：仅多人、需同 tick 同目标；后果是**回血偏多**，非崩溃/复制。**LOW，非阻塞。**

**建议（两处各一行，均非必须）**：
1. 把 `PRE_HIT_HEALTH.remove(...)` 提到 `onLivingDamagePost` 的**最前面**（在 attacker 判断之后、所有提前 return 之前），泄漏即消失；
2. `put` 时加 `if (DerivedStats.lifestealPercent(attacker) <= 0) return;`（不必写入），即消除路径 3 这个常态泄漏源。

---

## T10-3. 变异测试：我独立复现了两组（**不是引用 coder 的结论**）

### 变异 1（我自己设计，针对 T9 修复本身）：**还原 lifesteal 截断**
在隔离副本把 `Math.min(damageDealt, healthBefore)` 改为空操作 → **恰好 1 个用例变红**：
```
1 required tests failed :(
RPGVERIFY lifesteal.healedFromOverkill=10.0
lifestealhealsproportionaltohealthactuallylost failed!
  overkill leaked: 100 damage on a 20 hp target healed 10.0 instead of 20 * 10% = 2
```
⇒ **T9 的修复是真的，且它的回归测试非空转。** 同一证据行在修复版下为 `2.0`。

### 变异 2（复现 coder 自报）：**减伤失效 + `DropRateHelper` 直接 return**
```
5 required tests failed :(        ← 与 coder 自报的"5 个用例变红"完全一致
damagereductionscalesthedamagethatarrives
   "100 damage with 42.857% reduction removed 100.0 hp, expected 57.142857…"
reductioncomposeswitharmourwithoutdoublecounting   (2/3 checks failed)
killcommandstillkillsthroughmaxreduction
   "300 damage with 42.857142857142854% reduction and maxed armour killed the player"
dropmultiplierscalesandsplitsaboveonestack
   "10 stone at x2.5 produced 10 items instead of 25"
dropratehonoursbothhardbudgets
   "the multiplier did nothing, so the budget assertions are vacuous"
```
**coder 的变异测试属实，逐字复现。**

---

## T10-4. 你要审查的三件实质内容

### (1) `/kill` 与虚空用例：**真的证明了"硬绕过"，不是"伤害不够大"**

我一开始怀疑这是空转——因为 `genericKill` 那一步发生在玩家已被打到 48.57 HP 之后，那样无论减不减免都会死。
**但读源码后确认 coder 做对了**：`CombatEffectSuite.java:240` 有 `player.setHealth(maxHealth);` —— **先把血补满再打**。于是：

| 路径 | 血量 | 期望 | 若减伤错误地生效会怎样 |
|---|---|---|---|
| `generic()` 300 + 42.857% 减伤 | 220 | **存活**（171.43 < 220） | — |
| `generic_kill` 300（/kill） | **补满到 220** | **必死** | 171.43 < 220 ⇒ **会活**，断言失败 |
| `fell_out_of_world` 300 | **全新玩家 220** | **必死** | 171.43 < 220 ⇒ **会活**，断言失败 |
| `player.kill()` | 全新玩家 | **必死** | — |

且**变异 2 实测**：减伤被禁用后 `killcommandstillkillsthroughmaxreduction` **确实变红**（"300 伤害把玩家打死了"）——说明"减伤生效 ⇒ 300 打不死"是真的，"绕过 ⇒ 必死"也是真的。**非空转，判定通过。**

> 唯一瑕疵（INFO）：该用例 L219-220 给玩家投了护甲点，但 `hit()` 用的是 `damageSources().generic()`，**该类型在 `bypasses_armor` 里、护甲根本不参与**，所以注释里的 "and maxed armour" 对这一步是装饰性的。不影响结论（减伤独自承担了不对称量）。

### (2) `minecraft:generic` 是否真在 `bypasses_armor`：**已独立核实，属实**

从 `neoforge-21.1.248-client-extra-aka-minecraft-resources.jar` 取出 `data/minecraft/tags/damage_type/bypasses_armor.json`：
```
on_fire, in_wall, cramming, drown, fly_into_wall, generic, wither, dragon_breath,
starve, fall, freeze, stalagmite, magic, indirect_magic, out_of_world,
generic_kill, sonic_boom, outside_border
```
⇒ **`minecraft:generic` 确实绕过护甲**，coder 的"真坑"判断正确。
**对我 T2 判定标准的影响：无 —— 我 T2 写的 D05 行为验证本来就用的 `/damage @s 20 minecraft:mob_attack`，并在 §5.5 明确列出 `minecraft:generic` 属 `BYPASSES_ARMOR`。** 该标准无需修改，本次是**对我原判定的实证确认**。
并已核实 `reductionComposesWithArmourWithoutDoubleCounting` 三处伤害全部走 `hitByMob(...)`（`mobAttack`），L145/151/163 —— **护甲管线确实跑起来了**。
（另：配置 `damage_reduction.applies_to_bypass_armor` 默认 **true**，所以减伤对 `generic` 仍生效——这正是"减伤"与"护甲"两条管线必须分开测的原因。）

### (3) 6 个派生访问器是否真的被执行：**是，全部被执行且被观测**

`CombatEffectSuite` 实际调用了：`critChancePercent`、`critDamagePercent`、`damageReductionPercent`、`dropMultiplier`、`healthRegenPerSecond`、`lifestealPercent` —— **6/6**。
且**观测值是真实游戏量**，不是中间变量：

| 证据行 | 值 | 说明 |
|---|---|---|
| `lifesteal.healedFrom100` | 10.0 | 100 × 10% |
| `lifesteal.healedFromOverkill` | **2.0** | T9 修复 |
| `lifesteal.accumulatorAfterOneHeart` | 0.2 | 小数累积器 |
| `lifesteal.healedFromFiveOneHeartTargets` | 1.0 | 5×0.2 恰好凑整 |
| `lifesteal.throttledHeal` | 1.0 | 单 tick hps 上限 |
| `crit.observedPercent` / `crit.chance` | 37.550 / 38.4615 | 统计观测 |
| `crit.multiplier` | 2.5 | — |
| `regen.hps` / `healedOver200Ticks` | 2.5 / 25.0 | — |
| `regen.fractionalHps` / `after15Ticks` / `after30Ticks` | 0.8333 / 1.0 / 2.0 | 小数累积 |
| `drop.multiplier` / `budget.maxStackSeen` | 2.5 / **64** | 堆叠上限 |
| `drop.budget.itemsAdded` / `budgetItems` | 128 / 256 | 预算 |
| `drop.budget.entitiesAdded` / `budgetEntities` | 3 / 64 | 预算 |
| `drop.split.totalItems` / `newEntities` | 160 / 3 | 拆堆 |

**⇒ T8-5 指出的覆盖缺口已真正闭合**（不只是 import，而是执行并断言真实游戏量）。

---

## T10-5. 缺陷清单（T10 新增，均为非阻塞）

| ID | 级别 | 问题 | 影响 | 建议 |
|---|---|---|---|---|
| **T10-D1** | **LOW** | `PRE_HIT_HEALTH` 在 5 条提前 return 路径上泄漏；注释「only happens under a bug」与事实不符（**没投偷取点的玩家每次命中都泄漏**） | 内存有界（≤4097 条 ≈260 KB），无功能危害 | `remove` 提到 Post 最前；或只在 `lifestealPercent>0` 时 put |
| **T10-D2** | **LOW** | 映射 key 仅 `victim.getId()`，多玩家同 tick 打同一目标会互相覆写，其中一个拿到 `null` 而**退化为未截断吸血** | 仅多人，回血偏多，非崩溃 | 见上；或在 Post 顶部无条件消费 |
| **T10-D3** | **INFO** | `killCommandStillKillsThroughMaxReduction` L219-220 的护甲加点对 `generic()` 源无效，注释 "and maxed armour" 有误导 | 无 | 改注释或改用 `mobAttack` |

**T4 遗留项状态**：D-1（无自动化路径）**已解除**；D-2、D-3 **已修**；D-4（armor 余量薄）**仍成立但已文档化**（INFO）；D-5（击杀双份经验）**仍待产品确认**，非技术缺陷。

---

## T10-6. 报告 §6 判定表（T10 实测）

**26/26 属性层 ✅ ｜ 26/26 效果层 ✅ ｜ 0 未覆盖** —— 见下方 §6 表（已按 T10 结论更新两列）。

---

## T10-7. 未闭环项：**区分「阻塞交付」与「需人工确认」**

### 🔴 阻塞交付项：**无**

### 🟡 需人工确认（无法自动化，不阻塞交付）
| # | 项 | 原因 |
|---|---|---|
| M-1 | GUI：K 键开关、缩放适配、语言切换、点数不足禁用 | 需图形界面（GameTest 无头） |
| M-2 | 双客户端权威性 / 20 人压力 | 需真实客户端 |
| M-3 | 与 `infinitecore-1.0.0.jar`（stellersword）共存 | 需装入双 mod 启动 |
| M-4 | **真人穿全套下界合金**看 `/rpg stats` | T8-4c：原版 tick 装配路径未被 mock 走到 |
| M-5 | `lifesteal` 的 T10-D1/D2（泄漏/竞态）如需彻底确认 | 需长时多人压测 |

---

# T8 复验结论（**已被 T10 取代**）

## T8-0. 总判定：**CONDITIONAL PASS**（较 T4 显著收窄）

T4 的阻塞项（26 条线无法自动化）**已解除**：新增的 22 个 GameTest 经**变异测试证明非空转**，26 条线的属性接入已闭环。
剩余未闭环项只有 2 类：**6 个自定义维度的"玩法效果层"无测试覆盖**（新发现，见 T8-4），以及需要图形界面的客户端/联机项。

| 验收层 | T4 结论 | **T8 结论** | 依据 |
|---|---|---|---|
| **L0 构建** | PASS | ✅ **PASS** | clean build 成功；jar **136,435 字节**；65 class 与源码一一对应 |
| **L1 静态** | PASS | ✅ **PASS** | 13/13、6/6、0 危险类、8/0 FAIL；INV-1 字节码级 0 命中 |
| **L2 数学与安全证明** | PASS | ✅ **PASS** | `verifyMath` **254/254**；BL-9 独立重算不变 |
| **L3 专服运行（S-08b）** | PASS | ✅ **PASS** | `Done (0.530s)!` |
| **L3b GameTest** | 不存在 | ✅ **PASS** | `All 22 required tests passed :)`，8 个批次；**变异测试双向验证非空转** |
| **L4 26 线行为·属性接入层** | ❌ NOT VERIFIED | ✅ **PASS** | 26/26 逐条覆盖（`LineEffectSuite` 遍历 `StatLine.VALUES`） |
| **L4 26 线行为·玩法效果层** | ❌ NOT VERIFIED | ⚠️ **PARTIAL** | **6 个自定义维度的效果处理器无任何测试**（T8-4） |
| **L5 回归（双 mod 共存）** | NOT VERIFIED | ❌ **NOT VERIFIED** | 需客户端图形界面 |
| **L6 人工清单** | 30 条 | 已收窄（T8-6） | — |

---

## T8-1. coder 自报的独立复跑（全部亲跑）

| # | 检查 | 结果 |
|---|---|---|
| 1 | `runGameTestServer` | **`All 22 required tests passed :)`**，`BUILD SUCCESSFUL in 16s`，退出码 0 |
| 2 | 批次构成 | `respec:0`(2) `curve:0`(3) `authority:0`(4) `smoke:0`(1) `xp:0`(5) `equipment:0`(2) `persistence:0`(3) `lines:0`(2) = **22** |
| 3 | 命名空间 | `Enabled Gametest Namespaces: [rpgleveling]` |
| 4 | 日志洁净度 | 通过运行中 **无 `ClassCastException`、无 `ERROR`** |
| 5 | `verifyMath` | **254/254 passed, 0 failed** |
| 6 | `check_jar` | **13/13**；`B-10 = 65 classes`；**`B-08` 0 FAIL（测试类未入 jar）** |
| 7 | `check_static` / `check_dist` / `check_lang` | **6/6** / **0 危险类** / **8/0 FAIL** |
| 8 | `runServer` | **`Done (0.530s)!`** |
| 9 | 装备双跑证据 | naked armor **8.076923** → geared **28.076923**（Δ20）；toughness **6.474359** → **18.474359**（Δ12）；kb **0.445513** → **0.845513**（Δ0.4）—— **与 coder 自报完全一致，且我另用 `Stat.equipmentMax` 独立核对 Δ 正确** |

---

## T8-2. 优先级一：`Carrier.holder()` 必崩缺陷 —— **已修复，且修复真实生效**

### (a) 缺陷真实性：**用新旧 jar 的字节码描述符证实**

| jar | `holder()` 描述符 |
|---|---|
| **旧**（`_verify/evidence/T4/…as-delivered.jar`，135,568 B） | `()Lnet/neoforged/neoforge/registries/DeferredHolder;` |
| **新**（136,435 B） | `()Lnet/minecraft/core/Holder;` |

旧签名把原版 `Attributes.X`（`Holder.Reference`）当 `DeferredHolder` 返回 ⇒ 6 个原版维度必然 CCE。

### (b) 回归测试是否真的抓得住：**做了针对性变异测试**

我在**隔离副本** `_verify/mutant/`（不进 `src/**`，用完即删）里把 `holder()` **还原成旧签名**并重跑 GameTest：

```
java.lang.ClassCastException: class net.minecraft.core.Holder$Reference cannot be cast to
class net.neoforged.neoforge.registries.DeferredHolder
...
17 required tests failed :(      ← BUILD FAILED，退出码 1
```

**22 个测试里 17 个当场变红**，报错正是该 CCE。
⇒ 缺陷**真实且致命**（任何玩家 tick 都触发），`HarnessSmokeSuite`（遍历 13 个 `Stat.VALUES` 调 `holder()`）是**有效回归护栏**。
⇒ 修复后 22/22 全绿。**修复真实生效，已独立证实。**

---

## T8-3. 优先级二：GameTest 是否空转 —— **不空转，已用变异测试证明**

### (a) 已确认的三项非空转证据

1. **自带覆盖率下限**：`LineEffectSuite` 断言 `report.checks() >= StatLine.COUNT * 5`（≥130 次检查），并对 `StatLine.VALUES` **全量遍历**（26/26，非抽样）。
2. **存在独立预言（oracle）**：多处使用**硬编码期望值**，不是拿同一份常量自我比对：
   - `EquipmentClampSuite`：28.0769 / 18.4744 / 0.8455（来自 DESIGN 1.16.3）
   - `LevelCurveSuite`：need(199)=4060 / need(200)=4080 / need(201)=4504；cum(199)=413920 / cum(200)=418000 / cum(201)=422504；`levelForXp(6471888)==1000`
   - `XpBookingSuite`：`grant(10) with 1.625x` 必须返回 **16**，并**显式注明"a squared multiplier would give 26"**
3. **predicted 与 actual 走两条不同代码路径**：`predicted` 来自 `DerivedStats.computedValue`（设计公式），`actual` 来自 live `AttributeInstance.getValue()`（原版累加）。二者在"配置 → 曲线 → modifier id → Operation → 属性"任一环节出错都会分叉。

### (b) 我做的变异测试（**lead 明确要求**）

隔离副本里把 `RpgMath.saturating` 从渐近曲线改成 `cap * 1.5`（**不会**触发 `verifySafetyProof` 的类加载守卫，只会让数值真的越过天花板）：

```
4 required tests failed :(        ← BUILD FAILED，退出码 1
armourlinesneverclampnakedorgeared: 12/15 checks failed ->
   geared armor reached the ceiling: 30.0 >= 30.0
   clamped=true for armor geared (computed=35.0 effective=30.0)      ←←← 正是 lead 问的那条断言
   clamped=true for armor_toughness geared (computed=24.0 effective=20.0)
   clamped=true for knockback_resistance geared (computed=1.225 effective=1.0)
   armor geared 200+200: expected 28.076900 but got 30.000000
multiplierisappliedexactlyonce: 3/7 failed
killeventbooksexactlyonce: one kill booked 18 xp, expected 11
flatandpercentofonedimensioncompose: 3/39 failed
```

**结论：`clamped` 断言在应当失败时确实失败。测试套件非空转，可以采信。**

> 附注：`LineEffectSuite` 的 `predicted`/`actual` 都源自 `RpgMath.lineValue`，所以**曲线层本身**的变异它抓不到（两边一起错）。这正是为什么必须保留 `verifyMath` 的硬编码值 + 我的 `verify_math_independent.py` 作为曲线层的独立预言。**两层缺一不可。**

---

## T8-4. 对 coder「装备 mock 妥协」的裁定

**结论：该替代路径忠实于真实装备流程，`Δ == equipment_max` 断言可接受，但它不能单独支撑 BL-9 —— BL-9 实际由另外两条断言支撑。**

### (a) 忠实性：**逐行核对通过**

真实的原版路径（`LivingEntity.detectEquipmentUpdates` → `collectEquipmentChanges`，反编译源码 **L2567-2572**）：

```java
itemstack2.forEachModifier(equipslot1, (attr, modifier) -> {
    AttributeInstance ai = this.attributes.getInstance(attr);
    if (ai != null) {
        ai.removeModifier(modifier.id());
        ai.addTransientModifier(modifier);
    }
    ...enchantment location effects...
});
```

coder 的 mock（`EquipmentClampSuite.applyWornItemModifiers`）：

```java
stack.forEachModifier(slot, (attribute, modifier) -> {
    AttributeInstance instance = player.getAttribute(attribute);
    if (instance != null) {
        instance.removeModifier(modifier.id());
        instance.addTransientModifier(modifier);
    }
});
```

**同一个 `ItemStack.forEachModifier` 数据源、同一对 API 调用、同样的顺序**；只省略了对属性值无影响的附魔位置效果。
⇒ **忠实**。唯一差异是**触发者**（手调 vs tick），不改变最终属性状态。

### (b) `Δ == equipment_max` 的定位：**必要但不充分**

- 它验证的是「测试真的穿上了设计假设的装备」——即 20/12/0.4 这组常数与现实一致。这是安全证明的**输入**校验，是有价值的。
- 但 BL-9 的**主张**是「不会触碰原版天花板」，由 `geared < hardMax` 与 `!isClamped` 承担，且这两条**与谁施加装备无关**。
- 变异测试已证明这两条**会真的失败**（T8-3b）。⇒ **BL-9 的验收是成立的。**

### (c) 我必须指出的剩余缺口（不构成 FAIL，但必须记录）

证据行 `equipment.path=explicit-item-modifiers` 说明 **`reachedByTicking == false`**：mock 玩家在 10 tick 内**没有**走原版 tick 驱动的装备变更路径。
⇒ **「原版 tick 装配 + 本 mod 每 20 tick 重应用」这两条机制的并发/交错未被端到端覆盖。**
风险低（两边都用稳定 id + `addOrUpdateTransientModifier`，幂等），但**建议保留一条真人穿装备的确认项**（T8-6 的 H-08）。

---

## T8-5. **新发现的覆盖缺口：6 个自定义维度的「玩法效果层」无任何测试**

这是 T8 最重要的**未被 lead 计入**的发现。

我扫描了全部 10 个测试文件对主代码的引用：

```
CombatEvents      → 0 处被测试引用
DropRateHelper    → 0 处被测试引用
PlayerTickHandler → 0 处被测试引用
```

而 22 个用例全部只验证到 **属性值层**（`AttributeInstance.getValue()` / `DerivedStats.finalValue`）。

| 维度 | 属性值层 | **玩法效果层** | 效果实现在 |
|---|---|---|---|
| health_regen | ✅ 已验证 | ❌ **无测试** | `PlayerTickHandler` |
| damage_reduction | ✅ 已验证 | ❌ **无测试** | `CombatEvents` |
| crit_chance | ✅ 已验证 | ❌ **无测试** | `CombatEvents` |
| crit_damage | ✅ 已验证 | ❌ **无测试** | `CombatEvents` |
| lifesteal | ✅ 已验证 | ❌ **无测试** | `CombatEvents` |
| drop_rate | ✅ 已验证 | ❌ **无测试** | `DropRateHelper` |
| 其余 7 个（6 原版属性 + xp_gain） | ✅ 已验证 | ✅ 由属性本身 / `XpBookingSuite` 承担 | — |

**含义**：例如「减伤 80% 是否真的把 100 点伤害变成 20 点」「暴击率 70% 是否真的约 70% 暴击」「掉落 ×5 是否真的掉 5 倍且每堆 ≤64」—— **这些都没有自动化证据**。
**因此 lead「26 条线行为已在 GameTest 下闭环」这一前提只对属性接入层成立，对玩法效果层不成立。**

**建议**（工作量小，直接扩展既有 harness）：加一个 `CombatEffectSuite`，用 mock 玩家 + `helper.getLevel()` 里的实体：
- 对目标施加固定伤害，断言实际承伤 = `D × (1-减免) × 护甲公式`；
- `RpgService` 把 crit_chance 拉到上限后用固定 seed 断言暴击次数；
- `LivingDropsEvent` 用 `simulate` 或直接构造事件断言掉落数；
- `PlayerTickHandler` 用 `helper.runAfterDelay(20)` 断言回血量。
**在此之前，这 6 个维度只能给"属性正确、效果未验证"。**

---

## T8-6. 人工清单（已从 T4 的 30 条收窄）

**GameTest 已覆盖、可以从清单删除的**：H-13/H-14（暴击，见 T8-5 反例——仍需人工或补测试）、H-25/H-26/H-27/H-28（重生/跨维度/重启/洗点，已由 `PersistenceSuite`/`RespecSuite` 覆盖）、H-19/H-20（掉落，未覆盖，保留）。
**仍然必须人工的**：

| # | 步骤 | 通过标准 |
|---|---|---|
| H-01~H-07 | GUI：K 键、Esc、缩放、语言、点数不足禁用 | 见 T4 §8.1（原样保留） |
| **H-08** | **真人穿全套下界合金后**看 `/rpg stats` | `stat.armor.clamped=false`、`effective<30`；**验证原版 tick 装配路径**（T8-4c 的缺口） |
| H-09/H-10/H-11 | BL-9 专项（toughness/kb 拉满、裸装 percent 不退化为 0） | 见 T4 §8.2 |
| **H-13** | 暴击率统计（400 次攻击） | 实测暴击率在 3σ 内（T8-5 未覆盖） |
| **H-17** | 减伤拉满后 `/kill @s` | **必须仍能杀死** |
| **H-18** | 减伤拉满后掉虚空 | **必须仍会死** |
| **H-19** | 掉落倍率 1000 挖一块石头 | 每堆 ≤64、不卡服 |
| H-21/H-22/H-23/H-24 | 首次奖励防刷、玩家隔离、击杀每次都给、时运顺序 | 见 T4 §8.4 |
| H-29/H-30 | 双客户端权威性 / 与 stellersword 共存 | 见 T4 §8.5 |

---

## T8-7. 本轮结论摘要

1. **`Carrier.holder()` 必崩缺陷：真实存在，已修复，修复真实生效**——由新旧 jar 字节码描述符 + 变异测试（17/22 红 + 原始 CCE 栈）双向证实。
2. **GameTest 非空转**——`clamped` 断言在变异下确实变红；套件带覆盖率下限与硬编码独立预言。**可以采信。**
3. **装备 mock 忠实**，`Δ == equipment_max` 可接受；BL-9 由 `< hardMax` 与 `!isClamped` 承担，且已证会失败。
4. **新缺口**：6 个自定义维度（health_regen / damage_reduction / crit_chance / crit_damage / lifesteal / drop_rate）的**玩法效果层无测试**，lead 的"26 线闭环"前提对该层不成立。
5. T4 的 D-1 阻塞项**已解除**；D-2/D-3 已由 coder 修复（`currentMultiplier` → 包私有 `diagnosticMultiplier`，我已复核）；D-4（armor 余量薄）与 D-5（双份经验）结论不变。

---

## 历史记录：T4 原始结论（**已被 T8 取代**，保留供追溯）

> ⚠️ 以下内容为 T4（jar = 135,568 字节）时的结论。其中 L4「NOT VERIFIED」已被 T8 解除；
> 「65/65 逐字节一致」的可复现性结论**对 136,435 字节的新 jar 不再适用**（jar 已变更）。
> 其余（构建、静态、BL-9 数学、缺陷清单）经 T8 复核**依然有效**。

## 0. 总判定：**CONDITIONAL PASS**

---

## 1. 我独立执行的复现（含原始结果）

| # | 检查 | 命令 | 结果 |
|---|---|---|---|
| 1 | **clean 构建** | `gradlew clean build --console=plain` | **BUILD SUCCESSFUL in 11s**；jar **135,568 字节** |
| 2 | **构建可复现性** | 逐 class 解包比对 SHA256 | 65 个 class，**名称集合一致、0 个字节码不同**（整体 jar SHA 不同仅因 ZIP 条目时间戳） |
| 3 | **数学自检** | `gradlew verifyMath` | **254/254 passed, 0 failed** |
| 4 | **jar 结构** | `check_jar.ps1` | **13 checks, 0 FAIL** |
| 5 | **静态扫描** | `check_static.py` | **6 checks, 0 FAIL** |
| 6 | **客户端类混用** | `check_dist.py` | **0 个危险类**（5 个在 `client` 包内，合规） |
| 7 | **lang 完整性** | `check_lang.py` | **8 checks, 0 FAIL**（1 条 WARN 已人工核实无缺陷） |
| 8 | **专服冷启动（既有配置）** | `gradlew runServer` | **`Done (3.410s)! For help, type "help"`** |
| 9 | **专服冷启动（全新配置）** | 移走 toml 后 `runServer` | **`Done (2.848s)!`**，配置重新生成 34,953 字节 |
| 10 | **INV-1 字节码级** | 自研常量池扫描（65 个 class） | `PlayerXpEvent` **0 命中**；`giveExperiencePoints`/`giveExperienceLevels`/`addExperienceLevels` **各 0 命中** |
| 11 | **独立数学复算** | `_verify/tools/verify_math_independent.py` | **0 failure**（§3） |
| 12 | **配置 ↔ 源码一致性** | 同上（E 段） | 26 个 `[lines.*]` 段**全部与源码转写一致** |

**环境清理**：构建前杀掉了 2 个残留 java 进程（coder 遗留 daemon），全程保持 Gradle 独占。

---

## 2. 两个口径裁定（lead 要求）

### 2.1 (a) INV-2 判据 —— **coder 的替代判据不成立，采纳我给的更强判据**

实测：
| 判据 | 结果 |
|---|---|
| 字面 `grep -rn "XP_GAIN"` | **14 行 / 5 文件** —— 确实无法为 0（枚举常量名本身含该子串），coder 的观察正确 |
| **coder 提议**的 `grep -rln "Stat\.XP_GAIN"` | **命中 2 个文件**：`StatLine.java`(2) **和** `XpService.java`(2) —— **并不"仅命中 XpService"，故该判据不成立** |

**裁定：放弃"grep 计数"这一类判据，改用真正能防住"倍率平方"的不变量。**

数学上的风险不是"读了几次"（读多少次都不影响结果），而是**乘了几次**。可机械验证的正确判据是：

| 判据 | 命令 | 期望 | 实测 |
|---|---|---|---|
| **INV-2a** 经验落账只有一个写入口 | `grep -rn "commitXp" src/main/java` | 1 个定义 + **1 个调用点** | ✅ `RpgService.java:144` 定义，`XpService.java:43` **唯一调用** |
| **INV-2b** 全工程不得调用原版经验写入 API | 字节码扫描 `giveExperiencePoints\|giveExperienceLevels\|addExperienceLevels\|setExperiencePoints` | **0 命中** | ✅ 65 个 class 全 0 |
| **INV-2c** 倍率只在 `XpService.grant` 内应用 | 人工/审计 `1.0 + ...finalValue(player, Stat.XP_GAIN)` 的**应用点** | 1 处 | ✅ `XpService.java:33`（`grant`）；`XpService.java:49` 仅 `currentMultiplier()` 诊断用，**不应用** |

**结论：INV-2 的意图成立且已被独立证实。** 但请 coder 做两处小修（见缺陷 D-2、D-3）。

### 2.2 (b) `/rpg stats` 的 `computed` 语义 —— **确认，与我的判定标准一致**

coder 的实现（`RpgCommand.java:229-238`）：

```java
double computed  = DerivedStats.rawAttributeValue(player, stat);   // sanitizeValue 之前
double effective = (inst != null) ? inst.getValue() : ...;         // sanitizeValue 之后
boolean clamped  = Math.abs(effective - computed) > 1.0E-6;
```

我逐行核对了 `DerivedStats.rawAttributeValue`（L89-104）与反编译源码 `AttributeInstance.calculateValue()`（L142-160）：
`rawAttributeValue` **忠实复刻**了原版的 `ΣADD_VALUE → ×(1+ΣADD_MULTIPLIED_BASE) → ×Π(1+ADD_MULTIPLIED_TOTAL)` 三步，且**不含** `sanitizeValue`。

**裁定：APPROVED。** 理由：
- `computed` 含实际装备与第三方 modifier ⇒ 裸装 / 全套下界合金**应当**不同，而两者 `clamped` 都应为 `false` —— 这正是 §5.0.5-8 要求的可观测性。
- 与"设计公式值"分离得很好：`line.<id>.value`（L223）走 `computedValue` = 纯设计公式；`stat.<dim>.computed` 走实况属性。**两个通道都有，缺一不可**。
- 唯一注意点：`computed` **不可跨玩家/跨装备比较**（它依赖实况），做公式对拍时必须用 `line.*.value`。建议在 `/rpg stats` 的帮助文本里写明。

---

## 3. T6 / BL-9 专项验收（本轮最高风险变更）

BL-9（我在 T2 提出的漏洞）：**TOTAL 模式的安全性公式漏掉了装备 ADD_VALUE**，导致 armor/toughness/knockback 在穿装备时被原版 `sanitizeValue` 静默钳制。

### 3.1 变更确认（从**运行期生成的配置**读出，非读源码）

`run/config/rpgleveling-common.toml` 与服务器加载日志实测：

| 维度 | flatCap | half | percent basis | B_ref | 新 sup | 旧 sup |
|---|---|---|---|---|---|---|
| armor | **6.0** | 40 | **DESIGN_BASELINE** | **4.0** | 20+6+4 = **30.0** | 70 |
| armor_toughness | **5.0** | 40 | **DESIGN_BASELINE** | **3.0** | 12+5+3 = **20.0** | 44 |
| knockback_resistance | **0.35** | 40 | **DESIGN_BASELINE** | **0.20** | 0.4+0.35+0.2 = **0.95** | 1.7 |

`defaultBasis()` 仅 `attack_damage` / `max_health` / `movement_speed` 为 TOTAL，其余 10 个为 DESIGN_BASELINE —— **与 lead 描述完全一致**。

### 3.2 **BL-9 验收核心：`clamped` 是否恒为 false**

我用独立重算（`_verify/tools/verify_math_independent.py`）验证两条：
1. **安全证明**：`equipmentMax + flatCap + B_ref·pctCap/100 ≤ hardMax` —— **9 个 DESIGN_BASELINE 饱和维度全部 PASS**（crit_damage 因 LINEAR 无界按设计跳过）。
2. **严格饱和**：`SAT(x)=cap·x/(x+half)` 对任意有限 x **严格小于 cap** ⇒ 实际可达值**严格小于** sup ≤ hardMax。在 10⁷ 点处实测：

| 维度 | 10⁷ 点实际值 | hardMax | 余量 |
|---|---|---|---|
| armor | 29.999952000 | 30.0 | **+0.000048** |
| armor_toughness | 19.999962000 | 20.0 | **+0.000038** |
| knockback_resistance | 0.949997400 | 1.0 | **+0.050003** |
| health_regen | 8.999956 | 20.0 | +11.0 |
| damage_reduction | 79.999360 | 90.0 | +10.0 |
| crit_chance | 69.999540 | 100.0 | +30.0 |
| lifesteal | 29.999800 | 60.0 | +30.0 |
| drop_rate | 399.996300 | 1000.0 | +600.0 |
| xp_gain | 349.996900 | 1000.0 | +650.0 |

**判定：BL-9 已解决。** 所有 DESIGN_BASELINE 维度在**任意点数**下都不会触碰原版 `sanitizeValue` ⇒ `clamped` 恒为 `false` ⇒ 公理 A3「点不浪费」成立。
**击退抗性的新保证成立**：`final < 0.95` ⇒ **至少保留 5% 击退**（旧文案「保留 10%」已作废，正确）。

> ⚠️ **唯一保留意见（D-4）**：armor 与 toughness 在 10⁷ 点处的余量只有 ~5e-5 和 ~4e-5。数学上严格为正、结论成立；但这意味着**任何外部的 ADD_VALUE 来源**（其他 mod、`/attribute` 指令）都会立刻触发钳制。建议把 armor 的 `hardMax` 从 30 下调到 29（或 `equipmentMax` 声明为 20 并在文档中写明"不兼容为玩家加护甲的其他 mod"），以留出真实余量。

### 3.3 三条线的行为断言（供人工/GameTest 使用）

| 场景 | 期望 |
|---|---|
| 全套下界合金 + armor 两线拉满 | `/rpg stats` 的 `stat.armor.clamped` **必须为 false**；`effective` < 30 |
| 全套下界合金 + knockback 两线拉满 | `clamped=false`；`effective` < 0.95；被僵尸击中**仍有位移**（≠0） |
| 裸装 + armor percent 满 | `effective` = 0 + 4.0 = 4.0（DESIGN_BASELINE 不再退化为 0）✅ **较旧版显著改善** |

---

## 4. coder 自报修复的三个缺陷 —— 复核结论

| # | 缺陷 | 我的复核 | 结论 |
|---|---|---|---|
| **1** | `RpgConfig.push("a.b")` 压两层/`pop()` 弹一层 → 26 条线嵌套 | 独立解析生成的 toml：**恰好 26 个扁平 `[lines.<stat>.<mode>]` 段**，全部格式合法（正则 `^\[[a-z_0-9]+(\.[a-z_0-9]+)*\]$` 无一违例），且 26 段的 curve/cap/half/basis/basis_value **逐项与源码转写一致** | ✅ **已修复** |
| **2** | `ModConfigEvent.Reloading` 早触发时 `getCurrentServer()` 为 null → 起不了服 | 源码 L46-54 已加 null 守卫；**实证**：14:34 的 crash-report 正是此路径（`ConfigEvents.applyToOnline(ConfigEvents.java:40)` NPE）。我**移走配置文件强制全新生成**后冷启动 → `Done (2.848s)`，无异常；另一次既有配置冷启动 → `Done (3.410s)` | ✅ **已修复（在最严苛路径上证实）** |
| **3** | 等级索引每次查询整体重建 | `verifyMath` 实测：`cum(1e6) 0.0 ms`、`lvl(A) 0.1 ms`、`deep-level probe (build + 2 lookups) 0.2 ms`、`levelForXp(Long.MAX_VALUE/4) = 2147483646 in 0.03 ms`；曲线边界我独立重算见 §5 | ✅ **已修复** |

---

## 5. XP 曲线独立复算（含 199/200/201 交界）

`need(L) = 100 + 20(L-1)` (L ≤ 200)；`need(L) = 4080·(1 + 0.15·ln(L-199))` (L > 200)

| L | 独立重算值 | 判定 |
|---|---|---|
| 199 | 4060.0000 | ✅ |
| **200** | **4080.0000** | ✅ 线性段终点 |
| **201** | **4504.2061** | ✅ = 4080·(1+0.15·ln2)；**无跳变、无回落** |
| 500 | 7572.7515 | ✅ 与 lead 的 7573 基准一致（差 0.25，容差内） |
| 1000 | 8171.7469 | ✅ |
| 10000 | 9704.4267 | ✅ |

- **严格单调**：L=1..20000 逐点验证 `need(L+1) > need(L)` 全部成立 ✅
- **`ln` 非正数防护**：L>200 ⇒ `L-200+1 ≥ 2`，**不可能取到 `ln(0)`/`ln(负数)`** ✅ `LevelCurve.java` L8-10 的公式天然满足
- **确定性**：`RpgMath` 全程使用 `StrictMath`（L49, L14-16 注释）⇒ 跨 JVM 重启位精确，等级边界不会漂移 ✅ 这是很好的工程决定

---

## 6. 逐维度判定表（13 维度 × 2 模式 = 26 条线）

> **T8 更新**：本表「行为」列原为「待人工」，现已由 GameTest 部分闭环。为准确起见拆成两层：
> - **属性层**＝配置→曲线→modifier id→Operation→`AttributeInstance` 实读值（`LineEffectSuite` 遍历 `StatLine.VALUES` 全量覆盖）
> - **效果层**＝该数值**真正作用到游戏**（伤害结算、暴击 roll、回血、掉落倍率）
>
> 判定符号：✅ 已有自动化证据　❌ **无任何测试覆盖**　⚠️ 见缺陷
> **关键结论：26/26 的属性层已闭环；但 6 个自定义维度的效果层完全无测试（T8-5）。**

| # | 维度 | flat 数学 | pct 数学 | 实现接入点 | 属性层 | **效果层** | 备注 |
|---|---|---|---|---|---|---|---|
| 1 | attack_damage | ✅ | ✅ LINEAR 3%/点 (TOTAL) | 原版属性 | ✅ GameTest | ✅ 原版机制承担 | hardMax 2048 会被钳（设计已声明） |
| 2 | max_health | ✅ | ✅ LINEAR 2%/点 (TOTAL) | `AttributeApplier` | ✅ GameTest | ✅ 原版机制承担 | 重生重建见 `PersistenceSuite` ✅ |
| 3 | armor | ✅ | ✅ **DB** B_ref 4 | 原版属性 | ✅ GameTest 双跑 | ✅ 原版机制承担 | **BL-9 已修**；⚠️ D-4 余量薄 |
| 4 | armor_toughness | ✅ | ✅ **DB** B_ref 3 | 原版属性 | ✅ GameTest 双跑 | ✅ 原版机制承担 | **BL-9 已修**；⚠️ D-4 余量薄 |
| 5 | movement_speed | ✅ | ✅ SAT 200/80 (TOTAL) | 原版属性 | ✅ GameTest | ✅ 原版机制承担 | sup=1.5，SAT 严格小于 ⇒ 不钳 |
| 6 | knockback_resistance | ✅ | ✅ **DB** B_ref 0.2 | 原版属性 | ✅ GameTest 双跑 | ✅ 原版机制承担 | 新保证「≥5% 击退」成立 |
| 7 | health_regen | ✅ | ✅ DB B_ref 2 | `PlayerTickHandler` | ✅ GameTest | ✅ **T10 已验** | `regen.hps=2.5`/`healedOver200Ticks=25.0`/`after15Ticks=1.0`；小数累积已验 |
| 8 | damage_reduction | ✅ | ✅ DB B_ref 40 | `CombatEvents` | ✅ GameTest | ✅ **T10 已验** | 100 伤害在 42.857% 下只掉 57.14；**`/kill` 与虚空必死已验**（变异下会红） |
| 9 | crit_chance | ✅ | ✅ DB B_ref 20 | `CombatEvents` | ✅ GameTest | ✅ **T10 已验** | 统计观测 37.55% vs 期望 38.46% |
| 10 | crit_damage | ✅ | ✅ LINEAR 2%/点 | `CombatEvents` | ✅ GameTest | ✅ **T10 已验** | `crit.multiplier=2.5` |
| 11 | lifesteal | ✅ | ✅ DB B_ref 10 | `CombatEvents` | ✅ GameTest | ✅ **T10 已验** | `healedFrom100=10.0`、**`healedFromOverkill=2.0`（T9 修复）**、单 tick 上限 1.0；⚠️ T10-D1/D2 为 LOW 缺陷 |
| 12 | drop_rate | ✅ | ✅ DB B_ref 50 | `DropRateHelper` | ✅ GameTest | ✅ **T10 已验** | `drop.multiplier=2.5`、**`maxStackSeen=64`**、预算 256/64 生效、拆堆 160/3 |
| 13 | xp_gain | ✅ | ✅ DB B_ref 50 | `XpService` | ✅ GameTest | ✅ `XpBookingSuite` | INV-2 已证实（含"平方会得 26"反例断言） |

**T10 更新：26/26 属性层 ✅ ｜ 26/26 效果层 ✅ ｜ 0 未覆盖。** T8-5 指出的缺口已由 `CombatEffectSuite`（10 用例）真正闭合——6 个派生访问器全部被执行，且断言的是**真实游戏量**（掉血、回血、物品数、实体数、暴击结果）。详见 §T10-4(3)。

---

## 7. 缺陷清单（按严重度）

### D-1 · **MEDIUM** · 26 条线行为验真无自动化路径（**阻塞项**）
- **现象**：无 GameTest 套件（`rpgleveling` 未注册任何 `@GameTest`）；`/rpg debug` 全部子命令要求 `EntityArgument.player()`（在线玩家）；`gradlew runServer` **不转发 stdin**。
- **证据**：我把 `rpg / rpg list / rpg stats / rpg debug override list / reload / stop` 写入文件并以 `< file` 重定向给 `runServer`，服务器日志**除 `Done` 外无任何命令回显**，`stop` 也未执行（我不得不强杀进程）。日志：`_verify/evidence/T4/08-runServer-freshcfg-probe.log`。
- **影响**：`docs/VERIFICATION.md` §5 的 26 条线判定、§7 的边界/作弊面用例**全部无法自动执行**。设计 §11.4.1 承诺的 `debug override`（D11 暴击倍率的唯一确定性测量手段）因此**实际上不可用**。
- **建议**（二选一，工作量都很小）：
  1. **补最小 GameTest 套件**：用 `helper.makeMockPlayer()` + `RpgService` 直接驱动，覆盖 §6.3 已列出的 10 个关键用例；`gradlew runGameTestServer` 无头可跑，产出 JUnit XML。
  2. **让 `runServer` 能收控制台命令**：在 MDG run 配置里补 `standardInput`（**不在我的写域，需 lead 决定**），或改用 **RCON**（`run/server.properties` 开 `enable-rcon`）。
- **注**：D11（crit_damage）在 `crit_chance` sup=70% 下**SAT 严格取不到 100%**，若不做 (1)/(2)，D11 只能靠人工反复攻击统计。

### D-2 · **LOW** · `XpService` 类注释与实现不符
- `XpService.java:12` 写「the `xp_gain` dimension is read exactly once, here」，但实际读了**两次**：L33（应用）与 L49（`currentMultiplier` 诊断）。
- **风险**：`currentMultiplier()` 是 `public` 且复制了倍率公式，未来若有人用它**施加**经验，就会平方 ⇒ 正好触发 INV-2 要防的事故。
- **建议**：注释改为「applied exactly once」，并在 `currentMultiplier()` 上加 `// diagnostics only - never apply this`。

### D-3 · **LOW** · 配置节位置不一致
- 其余 12 个维度的专属配置在顶层（`[health_regen]`、`[damage_reduction]`、`[movement_speed]`、`[max_health]`、`[lifesteal]`、`[drop_rate]`…），但 `drop_rate` **多出一个 `[lines.drop_rate]`**（在 `[lines]` 之下）。已确认 27 个 `[lines.*]` 段中 26 个是正常加点线、第 27 个是 drop_rate 专属项。
- **影响**：纯一致性问题，无功能缺陷。

### D-4 · **INFO** · armor / armor_toughness 的钳制余量极薄
- 见 §3.2 保留意见。armor 在 10⁷ 点处仅剩 **+4.8e-5**、toughness **+3.8e-5**。
- **影响**：对本 mod 自身成立；但与任何"给玩家加护甲/韧性"的第三方 mod（或 `/attribute`）**必然冲突**并静默丢点。

### D-5 · **INFO** · 击杀经验同时进入两套系统（**需产品确认，非缺陷**）
- `XpEvents.java:58-66`：读 `event.getOriginalExperience()`（**原值，正确 —— 避免自反馈平方**），授给 mod 计数器，**同时** `event.setDroppedExperience(granted)` 把原版经验球改成同样的（已乘倍率的）值。
- ⇒ 玩家击杀怪物时**同时**获得 [mod 经验] 与 [等量原版经验球]。
- **判定**：这是设计选择而非缺陷（两套货币各自只记一次；且取 `Original` 规避了反馈环）。但需 lead/用户确认「双份经验」是预期行为。
- **副产品（好消息）**：由于经验球被写成 `granted`，**读取经验球 `Value` 反而成为 mod 击杀倍率的合法独立通道** —— 我 T2 写的 D06「通道 D 用 `/xp query`」对本实现**不适用**（mod 从不调用原版经验 API，字节码已证），应改为读经验球 NBT + 读存档 attachment。

---

## 8. 必须人工确认清单（照做即可，无法自动化）

> 前置：`$env:JAVA_HOME='C:\Gradle\jdk-21-temurin'`；客户端 `gradlew runClient`（需图形界面）。
> 每条记录「期望 / 实测」，截图存 `_verify/evidence/manual/`。

### 8.1 交互与 GUI
| # | 步骤 | 通过标准 | ✓ |
|---|---|---|---|
| H-01 | 进入世界按 **K** | 加点界面打开，26 行 = 13 维度 × 2 模式 | ☐ |
| H-02 | 按 **Esc** / 再按 K | 正常关闭，鼠标解锁 | ☐ |
| H-03 | 背包打开时按 K；聊天聚焦时按 K | 不崩溃、不叠界面 | ☐ |
| H-04 | 界面数值 vs `/rpg stats` | 一致 | ☐ |
| H-05 | 点数不足时点 "+" | 按钮禁用或有明确提示 | ☐ |
| H-06 | GUI 缩放 1/2/3/4 | 不重叠、不溢出 | ☐ |
| H-07 | 切中文/英文 | **不出现 `rpgleveling.*` 原始键** | ☐ |

### 8.2 BL-9 / T6 专项（**本轮最高优先**）
| # | 步骤 | 通过标准 | ✓ |
|---|---|---|---|
| H-08 | 穿**全套下界合金**，armor 两线拉满 | `/rpg stats` 的 `stat.armor.clamped=false`，`effective` < 30 | ☐ |
| H-09 | 同上，armor_toughness 两线拉满 | `stat.armor_toughness.clamped=false`，`effective` < 20 | ☐ |
| H-10 | 全套下界合金 + knockback 两线拉满，被僵尸打 | `clamped=false`；**仍有位移（≠0）**，即"至少保留 5% 击退"成立 | ☐ |
| H-11 | **裸装**投 armor percent（不投 flat） | `effective` ≈ 4.0（**不再退化为 0**）—— 这是 T6 的核心改进 | ☐ |
| H-12 | 脱掉装备后再看 | `computed` 变小、`effective` 同步下降，**不报错、不回退点数** | ☐ |

### 8.3 战斗维度
| # | 步骤 | 通过标准 | ✓ |
|---|---|---|---|
| H-13 | 对高血量靶（`/summon zombie` + max_health 1e6 + armor 0）连续攻击 400 次 | 暴击率落在 3σ 内；p=0 时 0 暴击 | ☐ |
| H-14 | `/rpg debug forcecrit <player> true` 后测暴击倍率 | D2/D1 = 2.5/1.5 = **1.6667** | ☐ |
| H-15 | 生命偷取：两档伤害（10 / 50）各测一次 | percent 模式偷取量随伤害等比；flat 模式恒定 | ☐ |
| H-16 | 生命恢复：`/gamerule naturalRegeneration false`，10 秒测回复量 | 与 `hps` 公式一致；**不超过 max_health** | ☐ |
| H-17 | 减伤拉满后 `/kill @s` | **必须仍能杀死**（硬性验收项） | ☐ |
| H-18 | 减伤拉满后掉虚空 | **必须仍会死** | ☐ |

### 8.4 掉落 / 经验
| # | 步骤 | 通过标准 | ✓ |
|---|---|---|---|
| H-19 | 掉落倍率拉到 1000，挖一块石头 | 掉落物成堆且**每堆 ≤ 64**；不卡服 | ☐ |
| H-20 | 反复挖同一方块 / 合成同一物品 / 钓鱼 100 次 | **只有第 1 次给经验**（首次行为奖励） | ☐ |
| H-21 | 首次奖励后退出重进，再挖同一方块 | **不再给经验**（已解锁集合落盘） | ☐ |
| H-22 | 玩家 A 与 B 各挖同一方块 | **B 仍应获得首次奖励**（集合按玩家隔离） | ☐ |
| H-23 | 反复击杀同一怪物 | **每次都给经验**（击杀不受"首次"规则限制） | ☐ |
| H-24 | 时运 III 镐 + 掉落倍率 | 倍率在时运**之后只乘一次** | ☐ |

### 8.5 生命周期 / 多人
| # | 步骤 | 通过标准 | ✓ |
|---|---|---|---|
| H-25 | 投满点 → 死亡 → 重生（连续 5 次） | 等级/经验/加点**完全不变**；`/data get entity @s Attributes` 中 modifier **数量不增长** | ☐ |
| H-26 | 主世界 → 下界 → 末地 → 主世界 | 数值不变 | ☐ |
| H-27 | 停服 → 起服 | 数值从存档恢复 | ☐ |
| H-28 | `/rpg respec` 后 | 26 条线全部回原版基线；`Attributes` 无残留 modifier；点数精确返还 | ☐ |
| H-29 | 双客户端：A 加点，B 看 A | A 的数值变化对 B 可见；**服务端为准** | ☐ |
| H-30 | 与 `infinitecore-1.0.0.jar`（stellersword）同时装入 | 双双正常启动、无注册表/mixin 冲突 | ☐ |

---

## 9. 与 `docs/VERIFICATION.md` 的差异（需回写）

| 项 | 原方案 | 实际实现 | 处置 |
|---|---|---|---|
| **D06 经验倍率独立通道** | 用 `/xp query` + `/xp add` | **不适用**：mod 从不调用原版经验 API（字节码 0 命中） | 改为：读**经验球 `Value` NBT**（击杀路径）+ 读存档 `world/data/neoforge_data_attachments.dat`（总量） |
| **`computed` 语义** | §5.0.5-8 要求"我们算出的值 vs 实际落值" | 实现为"实况属性钳前值 vs 钳后值"，设计公式另走 `line.*.value` | **接受实现口径**，§5.0.5-8 措辞已按 §2.2 更新 |
| **canary 结论** | T2 定案 ModDevGradle 2.0.81 | T3 沿用，构建 11s 通过 | 一致，无需变更 |
| **INV-1 判据强度** | `grep PlayerXpEvent` = 0 | 注释已被改写成"the vanilla player-experience event"以规避字面 grep | **改用字节码常量池扫描**（注释不进字节码）—— 我已重写并验证 0/65 |

---

## 10. 证据索引

| 文件 | 内容 |
|---|---|
| `_verify/evidence/T4/01-clean-build.log` | 独立 clean 构建 |
| `_verify/evidence/T4/02-verifyMath.log` | 数学自检 254/254 |
| `_verify/evidence/T4/03-runServer-cold.log` | 冷启动（既有配置）`Done (3.410s)` |
| `_verify/evidence/T4/08-runServer-freshcfg-probe.log` | **全新配置冷启动 `Done (2.848s)`**（原崩溃路径） |
| `_verify/evidence/T4/09-independent-math.log` | 独立数学复算 0 failure |
| `_verify/evidence/T4/04-check_jar` / `05-check_static` / `06-check_dist` / `07-check_lang` | 四套脚本输出（JSON + TXT） |
| `_verify/evidence/T4/rpgleveling-1.0.0.as-delivered.jar` | 交付时 jar 备份（用于逐 class 比对） |
| `_verify/tools/verify_math_independent.py` | **本轮新增**：手抄源码的独立数学复算器 |
| `_verify/tools/t4_refcheck.py` | **本轮新增**：字节码禁用引用扫描 + lang 动态键核实 |
| `run/crash-reports/crash-2026-10-03_14.34.58-fml.txt` | 缺陷 #2 的原始崩溃证据 |

---

## 11. 建议给 coder 的返工项（按优先级）

1. **（阻塞验收）** 补最小 GameTest 套件，或让 `runServer` 可收控制台命令（D-1）。**不修则 26 条线永远无法自动化验收。**
2. **（低）** 修 `XpService` 注释与 `currentMultiplier()` 的风险提示（D-2）。
3. **（低）** 统一 `drop_rate` 专属配置节的存放位置（D-3）。
4. **（建议）** 把 `armor.hardMax` 由 30 下调到 29，为第三方 mod 留出钳制余量（D-4）。
5. **（需 lead/用户拍板）** 确认"击杀同时给 mod 经验与原版经验球"是否符合预期（D-5）。

**本报告不构成对 26 条线行为正确性的背书。** §6 与 §7 已明确区分「已证实」与「待人工」。
