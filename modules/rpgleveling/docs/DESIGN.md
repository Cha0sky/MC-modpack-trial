# 《RPG 加点》Mod 技术设计文档

| 项 | 值 |
|---|---|
| Mod 名称 | RPG 加点 |
| modId | `rpgleveling` |
| Java 包名 | `com.rpgleveling` |
| 目标平台 | Minecraft **1.21.1** + NeoForge **21.1.x**（`loaderVersion [4,)`） |
| Java | 21（toolchain 21.0.7） |
| 权威 API 证据 | `docs/DESIGN-API-NOTES.md`（本文所有签名都在那里逐行取证） |
| 状态 | 待 code 阶段实现；标「待编译验证」的项见 API-NOTES 第 13 节 |
| 工具链基线 | **已定案（verifier canary 实测）**，见下方声明 |
| 验证方案 | **`docs/VERIFICATION.md` 是验收标准的唯一来源**；本文 `11.4` 是它对实现提出的接口要求在架构上的落地 |

> **工具链对齐声明（已定案，2025 由 verifier 的 `_verify/CANARY_RESULT.md` 实测拍板）**：
> - 插件：**ModDevGradle `net.neoforged.moddev` 2.0.81**（`net.neoforged.gradle.userdev` 7.0.170 / NeoGradle **在本机不可用，已排除**）
> - NeoForge：**21.1.248**　|　Gradle：**8.14.2**（wrapper 已播种）　|　JDK：**Temurin 21.0.12**（`C:\Gradle\jdk-21-temurin`，**必须显式设 `JAVA_HOME`**，系统 PATH 里没有 java）
> - 本文所有 API 结论只依赖 `net.neoforged.neoforge` 与 `net.minecraft` 的编译期 API 面，与构建插件无关，因此该变更**不影响本文任何结论**。
> - 唯一需要联动的是 `11.4` 的 GameTest 配置写法：ModDevGradle 用 `neoForge { }` 块而不是 `runs { }`，但 `forge.enabledGameTestNamespaces` 系统属性与 `runGameTestServer` 任务名一致，M5.11 的验收方式不变。

---

## 0. 需求锚点（用户已拍板，不得偏离）

1. 命名：mod 名《RPG 加点》/ modId `rpgleveling` / 包 `com.rpgleveling`。
2. 加点结构：**线性列表**，13 个维度直接加点，无前置依赖、无技能树。
3. 等级上限：**无上限**。经验曲线：**前 200 级线性增长，200 级后转对数增长**。
4. 单维度数值取向：**无上限**。
5. 洗点：**免费**，`/rpg respec`。
6. 交互：**图形加点界面，按 K 打开**；`/rpg` 指令保留（洗点与自动化验证需要）；HUD 常驻显示**不在需求内**（留配置项，默认关闭）。
7. 死亡：**完全无惩罚**（等级 / 加点 / 当前经验进度全保留）。
8. 单人与多人服务器都要；**服务端权威 + 防作弊**（数据在服务端，客户端只显示）。
9. 经验来源：**击杀怪物 + 挖矿 / 合成 / 熔炼 / 钓鱼 / 交易等原版行为**，不只击杀。
10. 维度共 **13 个**（原 9 + 新增 4）。
11. 每个维度都同时支持 **flat（直接数值提升）** 与 **percent（百分比提升）** 两种加点模式。

### 0.1 lead 于用户超时后锁定的默认值（**优先级高于本文其他任何描述**）

| 项 | 锁定值 | 本文对应章节 |
|---|---|---|
| 「无上限」落地方式 | **收益递减收敛**，不写死上限；渐近公式使数值可无限堆但收益自然收敛 | `1`（整章）、`1.4`、`1.5` |
| 基础伤害减免 | `DR = maxDR * x / (x + K)`，恒小于 `maxDR`，永不无敌 | `1.4` 第 8 行、`1.9` |
| 击退抗性 | 映射进原版 0..1 并收敛（**T6 修正后 sup = 0.95 < 1.0**，含全套下界合金的 0.4） | `1.4` 第 6 行、`1.16.4`（权威） |
| 暴击率 | 渐近逼近 100% 但取不到；**另提供**「溢出转暴击伤害」模式作为配置开关 | `1.4` 第 9 行、`12.5` |
| 移动速度 | 实用软上限 **1.5**（15 倍原版），避开服务端 moved too quickly | `1.7` |
| 生命恢复 / 生命偷取 / 经验倍率 / 掉落倍率 | 次线性增长避免爆炸（本设计默认用**有界**的 `x/(x+K)`，另提供 `x^gamma` 次线性曲线备选） | `1.2`、`1.4`、`12.4` |
| **等级 L 到 L+1 所需经验** | `XP(L) = 100 + 20 * (L - 1)`（L <= 200）；`XP(L) = 4080 * (1 + 0.15 * ln(L - 199))`（L > 200） | `8.4、`8.5 |
| **每级获得点数** | **每级 1 点**（所有等级一致） | `8.4、`8.5 |
| **非击杀经验** | 挖矿 / 合成 / 熔炼 / 钓鱼 / 交易 = **首次行为奖励**，每种 id 每玩家只给一次；需按玩家持久化「已解锁 id 集合」；可配置开关 | `8.7` |
| 配置可调性 | 以上**全部**走配置表，一处修改全局生效 | `12` |

> **与本文早前草案的差异（已按 lead 锁定值改写）**：
> - 经验曲线由 `20*L / 4000*(1+ln(L/200))` 改为 `100+20(L-1) / 4080*(1+0.15*ln(L-199))`。
> - 每级点数由 `3 / max(1, floor(600/L))` 改为**恒定 1 点**。
> - 因点数经济从「3 点/级」变成「1 点/级」，`1.4` 的 `half_points` 已整体下调（原区间 100..250 改为 40..100），使前 100 点的收益仍有体感。`1.5` 的安全性证明**不受影响**（只依赖 `cap`，与 `half_points` 无关）。
> - 非击杀经验由「每次给 + 每分钟上限」改为「**首次给一次**」，新增 `8.7` 一整节说明数据结构与性能设计。

### 0.2 本轮（T5 / T6）新增章节索引 —— 实现与验证前必读

| 新增章节 | 回答什么问题 | 谁必须读 |
|---|---|---|
| **`1.14` 百分比提升（percent）的作用基数** | percent 到底乘什么？flat=0 时 percent 会不会白投？BL-1/BL-2/BL-4/BL-5 的正式裁决；逐维度基数表；退化处置；取整规则；数值对拍样例 | **coder（必须按此实现 basis 模式）**、verifier（判定基准） |
| **`1.15` 护甲维度的真实收益边界** | 为什么「护甲属性 30」不等于「减伤 30/25」；减伤上限为什么是 80%；armor_toughness 为什么是核心维度 | coder（tooltip 要显示真实减伤）、verifier（B-1 判定基准） |
| **`6.3` 死亡重生后的属性保留与防重复叠加** | 本类 mod 的头号线上事故（verifier D-1） | coder、verifier |
| **`6.4` 经验倍率不得双重生效（硬不变量）** | verifier D-2；禁止注册 `PlayerXpEvent` 监听 | coder、verifier |
| **`1.9` 基础伤害减免（已改为纯百分点 α 口径）** | BL-3 裁决；`/kill` 必须仍能杀死玩家（硬性验收项） | coder、verifier |
| **`11.4` 验证测试面** | `/rpg stats` / `/rpg debug *` 的完整输出契约（含 `override` 强制满暴、`computed` 与 `effective` 双输出） | coder、verifier |
| **`1.16` 【T6 / BL-9】sup 必须含装备项（本轮最重要）** | `1.5` 漏了装备项的修正；13 维度 equipment_max 取证；修正后的 sup 逐维度代入验算；**armor / armor_toughness / knockback_resistance 三条线的新 cap 与新 basis**；击退抗性「永不免疫」保证的恢复；computed 与 effective 可观测性；NaN 防线；玩家 base 不等于 Attribute 默认值 | **coder（必须按新 cap 重写这三条线）**、verifier（判定基准） |

### 13 个维度

| # | 维度 id | 中文名 | 类别 |
|---|---|---|---|
| 1 | `attack_damage` | 基础攻击伤害 | 原 9 |
| 2 | `max_health` | 基础生命值 | 原 9 |
| 3 | `health_regen` | 生命恢复 | 原 9 |
| 4 | `movement_speed` | 基础移动速度 | 原 9 |
| 5 | `armor` | 基础防御力 | 原 9 |
| 6 | `xp_gain` | 经验获取倍率 | 原 9 |
| 7 | `knockback_resistance` | 击退抗性 | 原 9 |
| 8 | `armor_toughness` | 基础盔甲韧性 | 原 9 |
| 9 | `damage_reduction` | 基础伤害减免 | 原 9 |
| 10 | `crit_chance` | 暴击率 | 新增 |
| 11 | `crit_damage` | 暴击伤害 | 新增 |
| 12 | `lifesteal` | 生命偷取 | 新增 |
| 13 | `drop_rate` | 掉落倍率 | 新增 |

**13 × 2 = 26 条加点线**，每条线独立配置、独立加点。

### 玩法闭环

```
击杀怪物 / 挖矿 / 合成 / 熔炼 / 钓鱼 / 交易
        |
        v  (XpService.grant，经 xp_gain 维度修正)
   totalXp (只增不减, long, 存服务端 Attachment)
        |
        v  (RpgMath.levelForXp, 线性<=200 级 / 对数>200 级)
      level  ---> earnedPoints(level) ---> unspentPoints = earnedPoints - SUM(points[26])
        |                                         |
        |                                         v  按 K 打开界面 / /rpg spend
        |                                    points[26]（每条线独立点数）
        v                                         |
   经验条显示(派生)                                v
                                     AttributeApplier.apply(player)（幂等）
                                              |
                                              v
                           AttributeModifier(ADD_VALUE / ADD_MULTIPLIED_BASE)
```

---

## 1. 【核心章节】「无上限」与「功能性天花板」的冲突与收敛方案

> 这是本设计最关键的一节。用户要求「等级无上限、单维度数值取向无上限」，但同时要求不破坏游戏平衡（不能无敌、不能满暴溢出、不能被服务端踢、不能不死）。这两条在数学上**直接冲突**，必须正面解决。

### 1.1 冲突的形式化

设某条线的点数为 `x >= 0`，效果函数为 `V(x)`。
- 用户要求：`x` 无上界（可以一直加点）。
- 游戏要求：对某些维度存在**硬上限** `H`，`V(x) <= H` 对所有 `x` 成立，且 `V(x) < H` 应当**严格**成立（否则就是无敌 / 免疫 / 溢出废点）。

如果 `V` 是线性的（`V(x) = k*x`），则 `lim V(x) = +inf > H` ⇒ **必然击穿**。

### 1.2 解决方案：把「点的空间」与「效果的空间」解耦

**四条公理**（本 mod 的数值宪法）：

| 公理 | 内容 |
|---|---|
| **A1 点数无界** | 每条线的点数 `x` 是自然数，`max_points` 默认 `-1`（无上限）。玩家永远可以继续投点。 |
| **A2 效果收敛** | 对存在硬天花板的维度，效果函数取**饱和曲线** `SAT(x) = cap * x / (x + half)`，满足 `SAT(0)=0`、`SAT(half)=cap/2`、`lim SAT(x) = cap`（恒取不到）。 |
| **A3 点不浪费** | `SAT` 严格单调递增（导数 `cap*half/(x+half)^2 > 0` 对所有有限 `x` 成立）⇒ 任何额外 1 点都产生**严格正的**收益，只是边际递减。 |
| **A4 可证安全** | 每个有天花板的维度都给出 `sup(V) = (base + sup_flat) * (1 + sup_pct/100) <= HARD_MAX` 的算术证明（见 1.5 表）。 |

**三条曲线原语**（整个 mod 只有这三种；通过配置 `lines.<dim>.<mode>.curve` 选择）：

```java
/** 1) 线性：无硬天花板维度使用，真·无上限 */
static double linear(double points, double perPoint) {
    return perPoint * points;
}

/** 2) 饱和（默认）：有硬天花板维度使用，严格单调、恒小于 cap */
static double saturating(double points, double cap, double halfPoints) {
    if (points <= 0.0) return 0.0;
    return cap * points / (points + halfPoints);   // = cap * x/(x+K)
}

/** 3) 次线性幂（可选）：lead 建议的 sqrt/log 修正的等价实现；gamma = 0.5 即 sqrt */
static double sublinearPow(double points, double perPoint, double gamma) {
    if (points <= 0.0) return 0.0;
    return perPoint * StrictMath.pow(points, gamma);
}
```

**为什么默认用 `SAT` 而不是 `sqrt` / `log`（对 lead 建议 A 的正面回答）**

`sqrt(x)` 与 `log(x)` 确实是次线性，但**它们没有上界**，因此在「点数无上限」的前提下**不能单独保证不破坏游戏**：

| 曲线 | 100 点 | 10^4 点 | 10^6 点 | 有上界？ |
|---|---|---|---|---|
| `sqrt(x)`（gamma=0.5, per_point=1） | 10 | 100 | 1000 | **否** |
| `log(1+x)` | 4.6 | 9.2 | 13.8 | **否** |
| `cap*x/(x+half)`（cap=100, half=60） | 62.5 | 99.4 | 99.9994 | **是（< 100）** |

`SAT` 在低点数区间通过标定 `half` 就能获得与 `sqrt` 几乎一样的手感，但在高点数区间**渐近收敛**，所以能同时满足「点数无上限」与「A4 可证安全」。

**处置**：三条曲线全部实现并暴露到配置；**默认所有有天花板的线用 `SATURATING`**。需要 `sqrt` 手感的服务器可把 `curve` 改成 `SUBLINEAR_POW` 并把 `gamma` 设成 0.5（**此时 A4 的安全性证明失效，风险自负；配置项注释与本文均已明确标注**）。

**曲线原语的使用规则（唯一）**：`LINEAR` 只用于无功能天花板的维度（攻击伤害 / 生命值 / 暴击伤害）；其余全部用 `SATURATING`。

**为什么选 `x/(x+K)` 而不是 `cap*(1-exp(-x/K))`**：
1. 代数式可**反解** —— 给定「想达到 cap 的 r 倍」，所需点数 `x = half * r / (1-r)`。可直接写进 tooltip：「还需 N 点达到 90% 上限」。指数式无闭式反解。
2. 无超越函数依赖，浮点行为跨平台一致（Java `Math.exp` 允许 1 ulp 误差；除法则由 IEEE754 完全确定）。
3. 计算便宜（热路径每 tick 会被调用）。

**`SAT` 反解表（供 tooltip / 调试用）**

反解式：达到 `cap` 的 `r` 倍所需点数 `x = half * r / (1 - r)`（`0 <= r < 1`）。下表以 `half = 60`（本设计最常用的默认值）为例：

| 目标 | 所需点数公式 | half=60 时的值 |
|---|---|---|
| 25% of cap | `half/3` | 20 |
| 50% of cap | `half` | 60 |
| 75% of cap | `3*half` | 180 |
| 90% of cap | `9*half` | 540 |
| 99% of cap | `99*half` | 5940 |

> 结论：**90% 的 cap 只需要 `9*half` 点**（默认 half 在 40..100 之间 ⇒ 360..900 点）。这就是「无上限但不破坏游戏」的量化含义：想吃到九成收益要花掉数百点，剩下的一成永远吃不满。

**推论**：由于 `SAT` 恒取不到 `cap`，**「无上限」在点数维度上成立，在效果维度上收敛**。这就是用户两条要求的同时满足方式。

### 1.3 最终数值的组装规则（唯一，26 条线零特例）

```
d0     = attributeBase + V_flat(x_flat)          // ADD_VALUE，可累加
final  = d0 * (1 + V_pct(x_pct) / 100)           // ADD_MULTIPLIED_BASE，多来源求和后再乘
final' = clamp(final, 0, HARD_MAX)               // 仅作为数值兜底
```

- `attributeBase` 取**玩家实体的真实 base**：攻击伤害 `1.0`、移速 `0.1`、生命 `20.0`，其余 `0.0`（证据：`Player.createAttributes()` L228-231）。
- 因为 `ADD_MULTIPLIED_BASE` 的语义是 `(base + SUM ADD_VALUE) * (1 + SUM amt)`（证据：`AttributeInstance.calculateValue()` L145-163），**percent 线会自动放大 flat 线的成果，而多条 percent 之间是相加不是连乘** ⇒ 天然不会指数爆炸。
- **全面禁用 `ADD_MULTIPLIED_TOTAL`**（它会连乘，多个来源叠加会失控）。
- `clamp` 用 `Math.min` / `Math.max`，**永不用于实现「上限」**——上限由 `SAT` 的 `cap` 保证；`clamp` 只是防止配置文件被改坏。

**对 verifier BL-1 / BL-2 的一句话口径（详细裁决见 `1.14.7`）**：
- **BL-1**：percent 是**线性加算，绝不复利** ⇒ `final = B * (1 + SUM(pct_i)/100)`。本 mod 每维度只有一条 percent 线，所以就是 `B * (1 + V_pct/100)`。
- **BL-2**：**先加后乘** ⇒ `(B + flat) * (1 + pct/100)`，**不是** `B*(1+pct/100) + flat`。
  ⚠ **但这条只对 `TOTAL` 模式成立**。`DESIGN_BASELINE` 模式下 percent 是**加性**的：`final = B + flat + B_ref * pct / 100`。
  两种口径的完整对照见 `1.14.3` 与 `1.16.2`。verifier 写判定时必须先确认该维度的 basis 模式。
- **BL-3**：`damage_reduction` 的 flat **锁定为 α（每点 +1 个百分点）**，不采用 β（每点减免固定点数伤害）；见 `1.9`。
- **BL-4**：percent 的**作用基数**是逐维度定义的，7 个自定义维度改用 `DESIGN_BASELINE` 彻底消除退化；见 `1.14.3` 与 `1.14.4`。

### 1.4 逐维度的曲线与上限设计（13 × 2 = 26 条线）〔T17 重写：已取消递减〕

参数单位说明：`per_point` 只在 `linear` 曲线下生效；`cap` / `half_points` 只在 `saturating` 曲线下生效。
**T17 之后只有伤害减免两条线使用 `saturating`，其余 24 条全部 `linear`**（且 `per_point = 原 cap / 原 half_points`）。

| # | 维度 | 承载 | 玩家 base | **flat 线语义 / 曲线** | **percent 线语义 / 曲线** |
|---|---|---|---|---|---|
| 1 | 基础攻击伤害 | 原版 `Attributes.ATTACK_DAMAGE` | 1.0 | +0.5 伤害/点 · **linear** | 每点 +3% · **linear**（TOTAL） | **LIVE** 原版 2048 |
| 2 | 基础生命值 | 原版 `Attributes.MAX_HEALTH` | 20.0 | +1 HP/点 · **linear** | 每点 +2% · **linear**（TOTAL） | **LIVE** 原版 1024 |
| 3 | 基础防御力 | 原版 `Attributes.ARMOR` | 0.0 | 护甲点 · **linear** per_point=**0.15** | **DESIGN_BASELINE** B_ref=**4.0** · **linear** per_point=1.666667 | **LIVE** 原版 30 |
| 4 | 基础盔甲韧性 | 原版 `Attributes.ARMOR_TOUGHNESS` | 0.0 | 韧性点 · **linear** per_point=**0.125** | **DESIGN_BASELINE** B_ref=**3.0** · **linear** per_point=1.666667 | **LIVE** 原版 20 |
| 5 | 基础移动速度 | 原版 `Attributes.MOVEMENT_SPEED` | 0.1 | 速度单位 · **linear** per_point=**0.006666667** | +% · **linear** per_point=**2.5**（TOTAL） | **LIVE** 原版 1024（**原自设 1.5 已删除**） |
| 6 | 击退抗性 | 原版 `Attributes.KNOCKBACK_RESISTANCE` | 0.0 | 抗性比例 · **linear** per_point=**0.00875** | **DESIGN_BASELINE** B_ref=**0.20** · **linear** per_point=1.666667 | **FIXED = 1.0**（1.0 即完全免疫） |
| 7 | 生命恢复 | **自定义** `rpgleveling:health_regen` | 0.0 | HP/秒 · **linear** per_point=**0.125** | **DESIGN_BASELINE** B_ref=**2.0** · **linear** per_point=3.333333 | **LIVE** 属性 max = 1.0E6 |
| 8 | 基础伤害减免 | **自定义** `rpgleveling:damage_reduction` | 0.0 | 百分点 · **sat** cap=60 **half=80**（不变） | **DESIGN_BASELINE** B_ref=**40.0** · **sat** cap=**75** **half=80** | **FIXED = 90**（渐近，永不达到） |
| 9 | 暴击率 | **自定义** `rpgleveling:crit_chance` | 0.0 | 百分点 · **linear** per_point=**0.833333** | **DESIGN_BASELINE** B_ref=**20.0** · **linear** per_point=1.25 | **FIXED = 100**（>100% 无意义） |
| 10 | 暴击伤害 | **自定义** `rpgleveling:crit_damage` | 0.0 | 百分点 · **linear** per_point=1.0 | **DESIGN_BASELINE** B_ref=**50.0** · **linear** per_point=2.0 | **LIVE** 属性 max = 1.0E9 |
| 11 | 生命偷取 | **自定义** `rpgleveling:lifesteal` | 0.0 | 百分点 · **linear** per_point=**0.333333** | **DESIGN_BASELINE** B_ref=**10.0** · **linear** per_point=1.25 | **LIVE** 属性 max = 1.0E6 |
| 12 | 掉落倍率 | **自定义** `rpgleveling:drop_rate` | 0.0 | 百分点 · **linear** per_point=**1.875** | **DESIGN_BASELINE** B_ref=**50.0** · **linear** per_point=5.0 | **LIVE** 属性 max = 1.0E6 |
| 13 | 经验获取倍率 | **自定义** `rpgleveling:xp_gain` | 0.0 | 百分点 · **linear** per_point=**1.666667** | **DESIGN_BASELINE** B_ref=**50.0** · **linear** per_point=5.0 | **LIVE** 属性 max = 1.0E6 |

> 🔴 **本表已被 T17 重写（用户明确要求：取消成长上限、不再有收益递减）。**
> 除 **伤害减免两条仍是 SATURATING** 外，其余 **24 条线全部改为 LINEAR**，
> 且 **per_point = 原 cap / 原 half_points**（即旧饱和曲线在 x=0 处的切线斜率），以此「保持前期手感」。
> 完整裁决记录见 **1.17**；cap / half_points 现在只对伤害减免有意义。
> **cap 全部作废的后果**：1.5 / 1.16 的安全性证明对除伤害减免外的维度**不再成立**——
> 这不是疏漏，是用户的选择（见 1.17.3）。

> **per_point 的来历（T17）**：旧饱和曲线 `SAT(x) = cap*x/(x+half)` 在 `x=0` 处的导数是 `cap/half`，
> 所以把 `per_point` 设为 `cap/half` 就精确保持了「第 1 点的收益」不变 —— 用户要求的正是「取消递减但保住前期手感」。
> 例：`armor.flat` 原 cap=6 / half=40 ⇒ `per_point = 0.15`；`crit_chance.flat` 原 cap=50 / half=60 ⇒ `0.833333`。
> 这条规则由 `RpgMathSelfCheck`（纯 JVM）与 `CurvePolicySuite`（GameTest）两侧独立校验。
>
> ⚠ **但「前期斜率一致」不等于「长期曲线一致」**：LINEAR 没有上限，旧 SAT 有。
> 从第 1 点往后两者迅速分岔，从大约 `half` 点起 LINEAR 的值就已经是旧 SAT 的两倍以上。
> 这是用户明确选择的取舍，不是实现偏差。

**26 条线的收益预览（T17：LINEAR 生效后）**：值 = `per_point × 点数`；
percent 线若 basis 为 `DESIGN_BASELINE`，表内给的是**绝对贡献量** `B_ref × V_pct / 100`，否则给的是「增速百分点」。

| 维度 · 模式 | 曲线 | per_point | 10 点 | 20 点 | 50 点 | 100 点 | 200 点 | 500 点 |
|---|---|---|---|---|---|---|---|---|
| attack_damage · flat | linear | 0.5 | 5.0 | 10.0 | 25.0 | 50.0 | 100.0 | 250.0 |
| attack_damage · percent（增速 %） | linear | 3.0 | 30.0 | 60.0 | 150.0 | 300.0 | 600.0 | 1500.0 |
| max_health · flat | linear | 1.0 | 10.0 | 20.0 | 50.0 | 100.0 | 200.0 | 500.0 |
| max_health · percent（增速 %） | linear | 2.0 | 20.0 | 40.0 | 100.0 | 200.0 | 400.0 | 1000.0 |
| armor · flat | linear | 0.15 | 1.5000 | 3.0000 | 7.5000 | 15.0000 | 30.0000 | 75.0000 |
| armor · percent（**绝对贡献**） | linear | 1.666667 | 0.6667 | 1.3333 | 3.3333 | 6.6667 | 13.3333 | 33.3333 |
| armor_toughness · flat | linear | 0.125 | 1.2500 | 2.5000 | 6.2500 | 12.5000 | 25.0000 | 62.5000 |
| armor_toughness · percent（**绝对贡献**） | linear | 1.666667 | 0.5000 | 1.0000 | 2.5000 | 5.0000 | 10.0000 | 25.0000 |
| movement_speed · flat | linear | 0.006666667 | 0.0667 | 0.1333 | 0.3333 | 0.6667 | 1.3333 | 3.3333 |
| movement_speed · percent（增速 %） | linear | 2.5 | 25.0 | 50.0 | 125.0 | 250.0 | 500.0 | 1250.0 |
| knockback_resistance · flat | linear | 0.00875 | 0.0875 | 0.1750 | 0.4375 | 0.8750 | 1.7500 | 4.3750 |
| knockback_resistance · percent（**绝对贡献**） | linear | 1.666667 | 0.0333 | 0.0667 | 0.1667 | 0.3333 | 0.6667 | 1.6667 |
| health_regen · flat (HP/s) | linear | 0.125 | 1.2500 | 2.5000 | 6.2500 | 12.5000 | 25.0000 | 62.5000 |
| health_regen · percent（**绝对贡献**） | linear | 3.333333 | 0.6667 | 1.3333 | 3.3333 | 6.6667 | 13.3333 | 33.3333 |
| **damage_reduction · flat (%)** | **sat** cap=60 half=80 | n/a | 6.6667 | 12.0000 | 23.0769 | 33.3333 | 42.8571 | 51.7241 |
| **damage_reduction · percent（绝对贡献）** | **sat** cap=75 half=80 | n/a | 3.3333 | 6.0000 | 11.5385 | 16.6667 | 21.4286 | 25.8621 |
| crit_chance · flat (%) | linear | 0.833333 | 8.3333 | 16.6667 | 41.6667 | 83.3333 | 166.6667 | 416.6667 |
| crit_chance · percent（**绝对贡献**） | linear | 1.25 | 2.5000 | 5.0000 | 12.5000 | 25.0000 | 50.0000 | 125.0000 |
| crit_damage · flat (%) | linear | 1.0 | 10.0 | 20.0 | 50.0 | 100.0 | 200.0 | 500.0 |
| crit_damage · percent（**绝对贡献**） | linear | 2.0 | 10.0 | 20.0 | 50.0 | 100.0 | 200.0 | 500.0 |
| lifesteal · flat (%) | linear | 0.333333 | 3.3333 | 6.6667 | 16.6667 | 33.3333 | 66.6667 | 166.6667 |
| lifesteal · percent（**绝对贡献**） | linear | 1.25 | 1.2500 | 2.5000 | 6.2500 | 12.5000 | 25.0000 | 62.5000 |
| drop_rate · flat (%) | linear | 1.875 | 18.75 | 37.50 | 93.75 | 187.50 | 375.00 | 937.50 |
| drop_rate · percent（**绝对贡献**） | linear | 5.0 | 25.0 | 50.0 | 125.0 | 250.0 | 500.0 | 1250.0 |
| xp_gain · flat (%) | linear | 1.666667 | 16.6667 | 33.3333 | 83.3333 | 166.6667 | 333.3333 | 833.3333 |
| xp_gain · percent（**绝对贡献**） | linear | 5.0 | 25.0 | 50.0 | 125.0 | 250.0 | 500.0 | 1250.0 |

> ⚠ **只有伤害减免两行仍是渐近的**：cap=60 half=80 与 cap=75 half=80，两者相加渐近 `60 + 40*75/100 = 90`，
> 严格小于 90 但可无限接近（10 000 点时两条线一起为 89.2857）。
> 其余 24 行都是纯线性、**没有任何设计上限** —— 唯一的边界是属性自身的 `maxValue`（见上表「上限模式」列与 `1.17`）。

**组合后的维度最终值示例（T17）**：TOTAL 维度用 `(base + V_flat) * (1 + V_pct/100)`，
DESIGN_BASELINE 维度用 `base + V_flat + B_ref * V_pct / 100`。最后一列同时给出「理论上限」与「实际值」。

| 场景 | flat 点 | percent 点 | 装备 | 理论值 | 实际值（受上限模式约束） |
|---|---|---|---|---|---|
| 攻击伤害 | 100 | 100 | 手持下界合金斧 +9 | (1+50)·4 = **204** | **204.0000**（LIVE 2048，不钳） |
| 护甲 | 20 | 20 | 裸装 | 3.0 + 1.3333 = **4.3333** | **4.3333** |
| 护甲 | 100 | 100 | 裸装 | 15.0 + 6.6667 = **21.6667** | **21.6667** |
| 护甲 | 200 | 200 | 全套下界合金 | 30 + 13.3333 + 20 = **63.3333** | **30.0000**（LIVE 原版 30，**被钳**，行首出现「!」） |
| 盔甲韧性 | 200 | 200 | 全套下界合金 | 25 + 10 + 12 = **47** | **20.0000**（LIVE 原版 20，**被钳**） |
| 击退抗性 | 100 | 100 | 裸装 | 0.875 + 0.3333 = **1.2083** | **1.0000**（FIXED 1.0 = 完全免疫） |
| 击退抗性 | 100 | 100 | 全套下界合金 | +0.4 ⇒ **1.6083** | **1.0000**（同上，超出部分无意义） |
| 伤害减免 | 50 | 50 | — | SAT(50,60,80) 23.0769 + 40·SAT(50,75,80)/100 11.5385 = **34.6154** | **34.6154 %** |
| 伤害减免 | 1000 | 1000 | — | 55.5556 + 27.7778 = **83.3333** | **83.3333 %**（仍 < 90） |
| 暴击率 | 50 | 50 | — | 41.6667 + 12.5 = **54.1667** | **54.1667 %** |
| 暴击率 | 200 | 200 | — | 166.6667 + 50 = **216.6667** | **100.0000 %**（FIXED 100，**被钳**） |
| 经验倍率 | 100 | 100 | — | 166.6667 + 250 = **416.6667** | **416.6667 %**（= 5.1667 倍） |

> 「百分点」= percentage point。例如 `crit_chance` 属性值 `25.0` 表示 25% 暴击率；`damage_reduction` 属性值 `30.0` 表示减伤 30%；`drop_rate` 属性值 `150.0` 表示 +150%（即 2.5 倍掉落）。

**「点不浪费」的实现（T17 后的诚实版本）**

- **只有伤害减免仍然保证「没有任何一点是浪费」**：`SAT` 的边际收益 `d/dx SAT(x) = cap*half/(x+half)^2` 对所有有限的 `x` 都 `> 0`，
  且 `SAT` 恒小于 `cap` ⇒ `sup = 60 + 40·75/100 = 90 = hardMax`，**10 000 点时两条线合计 89.2857，永不达到 90**。
- **其余 24 条线已经不再满足公理 A3**。它们是 LINEAR，值会一直涨，直到撞上属性自身的 `maxValue`
  （LIVE 维度）或本 mod 的功能上限（FIXED 维度：暴击率 100、击退抗性 1.0）。
  越过之后继续加点**确实没有收益**，唯一的补偿是可观测性：`DerivedStats.isClamped` / `isCappedByModCeiling`
  会为真，GUI 行首出现「!」，`/rpg stats` 的 `<dim>.clamped` 为 `true`。
  这是**用户明确选择的取舍**（"取消成长上限、不再有收益递减"），不是实现缺陷。
- **装了属性上限 mod（AttributeFix 等）之后**，LIVE 维度的边界随属性 `maxValue` 一起抬高，
  于是「点被浪费」的起点自动后移，代码无需改动。

### 1.5 安全性证明表（A4）

> 🔴 **T17：本表（以及 `1.16` 的修正版）对除伤害减免外的所有维度都已作废。**
> 24 条线改成了 LINEAR，`sup` 不再有有限值（就是无穷），因此**「取消递减后，除伤害减免外不再有安全性证明」**——
> 这是用户的明确选择，见 `1.17.3`。**唯一仍然成立的证明是伤害减免**：`0 + 60 + 40*75/100 = 90 = hardMax`（渐近，永不达到）。
> 下表仅作为历史记录保留。

> ⚠ **本表已被 `1.16` 取代（BL-9 修正）**。本表的 `sup = (base + sup_flat) * (1 + sup_pct/100)` **漏掉了装备项**，
> 对 armor / armor_toughness / knockback_resistance 三个维度**结论错误**（真实 sup 分别是 70 / 44 / 1.7，见 `1.16.2`）。
> **权威版本请直接看 `1.16.2` 与 `1.16.3`**；本表保留作为「equipment_max = 0 的维度」的参考。

`sup = (base + sup_flat) * (1 + sup_pct/100)`，`sup_flat / sup_pct` 取 `cap`（虽然是极限取不到，用于放缩证明）。

| # | 维度 | base | sup_flat | sup_pct | **sup** | HARD_MAX | 天花板来源 | 结论 |
|---|---|---|---|---|---|---|---|---|
| 1 | 攻击伤害 | 1.0 | 无限 | 无限 | 无限 | **2048** | 原版 `RangedAttribute(0, 2048)` | 见 1.6 特别说明 |
| 2 | 生命值 | 20.0 | 无限 | 无限 | 无限 | **1024** | 原版 `RangedAttribute(1, 1024)` | 见 1.6 特别说明 |
| 3 | 防御力 | 0.0 | 15 | 100% | **30** | 30 | 原版 `ARMOR` max=30 | 恰好等于天花板，SAT 严格小于 ⇒ 永远差一点点 ✔ |
| 4 | 盔甲韧性 | 0.0 | 10 | 100% | **20** | 20 | 原版 `ARMOR_TOUGHNESS` max=20 | 同上 ✔ |
| 5 | 移动速度 | 0.1 | 0.4 | 200% | **1.5** | 1.5 | 本 mod 自设（见 1.7） | 15x 原版，远低于服务端回拉阈值 ✔ |
| 6 | 击退抗性 | 0.0 | 0.45→**0.35**（T6） | 100% | 0.9→**0.95**（T6，含装备 0.4） | 1.0 | 原版 `KNOCKBACK_RESISTANCE` max=1 | **0.95 < 1.0 ⇒ 永远保留至少 5% 击退** ✔ 见 `1.16.4` |
| 7 | 生命恢复 | 0.0 | 5 HP/s | 200% | **无上限（LINEAR）** | 属性范围 1.0E6 | 无 | **T19 起取消成长限制**，见 1.8 |
| 8 | 伤害减免 | 0.0 | 60 | 50% | **90%** | 90% | 本 mod 自设 | **严格小于 90%** ⇒ 永不免疫 ✔ 另见 1.9 |
| 9 | 暴击率 | 0.0 | 50 | 100% | **100%** | 100% | 本 mod 自设 | 严格小于 100%，`Math.min(100,·)` 是纯兜底 ✔ |
| 10 | 暴击伤害 | 0.0 | 无限 | 无限 | 无限 | 无 | 无 | **真无上限**（只放大伤害数值，不破坏游戏机制）✔ |
| 11 | 生命偷取 | 0.0 | 20 | 100% | **40%** | 60% | 本 mod 自设 | 40% < 60% ✔ 另见 1.10 |
| 12 | 掉落倍率 | 0.0 | 150 | 500% | **900%**（=10 倍） | 1000% | 本 mod 自设 | 900 < 1000 ✔ 另见 1.11 |
| 13 | 经验倍率 | 0.0 | 100 | 500% | **600%**（=7 倍） | 1000% | 本 mod 自设 | 600 < 1000 ✔ 另见 1.12 |

> **本表的两个重要注释（T5 补充）**：
> 1. **第 3 行（防御力）的 sup=30 是「属性上限」，不是「减伤上限」。** 原版 `CombatRules.getDamageAfterAbsorb` 还会把**有效护甲**钳到 20，因此护甲单独能提供的减伤上限恒为 `20/25 = 80%`。
>    「护甲属性 30」的真实收益边界见 **`1.15.2` 的实测表**（Armor=30 + Toughness=20 时，对 40 到 80 点伤害的减伤可达 80% / 74.3%，而不带韧性只有 40% / 24%）。
> 2. **第 7 到 13 行（7 个自定义属性）的 sup 已按 `DESIGN_BASELINE` 口径更新，以 `1.14.5` 的表为准：**
>    `health_regen 9.0 HP/s`、`damage_reduction 80%`、`crit_chance 70%`、`crit_damage 无上限`、`lifesteal 30%`、`drop_rate 400%`、`xp_gain 350%`。
>    本表保留的乘法口径（15 / 90% / 100% / 40% / 900% / 600%）是「管理员把该维度改回 `TOTAL` 模式」时的上界，两者都小于各自的 HARD_MAX。

### 1.6 特别说明：原版 Attribute 的硬天花板无法突破（诚实结论）

`RangedAttribute` 的 `min/max` 是构造期确定的 final 字段，`sanitizeValue` 在 `AttributeInstance.calculateValue()` 最后一步无条件钳制（API-NOTES `1.1`）。**在不写 Mixin 的前提下无法修改。**

| 维度 | 原版 max | 达到 max 需要的 flat 点数 | 现实可达性 | 处置 |
|---|---|---|---|---|
| 攻击伤害 | 2048 | `(2048-1)/0.5 = 4094` 点 | 极难（1000 级总共才 1500 点，还得分 26 条线） | **接受**。2048 伤害 = 1024 心/击，远超任何内容需求。文档明确告知。 |
| 生命值 | 1024 | `1024-20 = 1004` 点 | 很难 | **接受**。1024 HP = 512 心。 |
| 防御力 | 30（装了属性上限 mod 则更高） | LINEAR 0.15/点 ⇒ `(30-0)/0.15 = 200` 点 | 容易（200 点） | **LIVE**：上限 = 属性自身 max。达到后继续加点**确实无收益**（`clamped=true` + GUI 出现「!」），这是 T17 的选择。 |
| 盔甲韧性 | 20（装了属性上限 mod 则更高） | LINEAR 0.125/点 ⇒ `20/0.125 = 160` 点 | 容易（160 点） | 同上（LIVE）。 |
| 击退抗性 | 1.0 | LINEAR 0.00875/点 ⇒ 裸装约 115 点 | 容易 | **FIXED = 1.0**：设计上就是要「完全免疫」，超过 1.0 无意义。 |

> **verifier B-2 的确认（静默丢弃，不报错）**：`RangedAttribute.sanitizeValue` 是 `Mth.clamp(value, min, max)`，**超出的部分被静默丢弃，不会有任何日志或异常**。
>
> **T17 之后这段结论反过来了**：因为 `cap` 已全部作废、24 条线改成 LINEAR，**「数值被丢弃」现在是正常玩法的一部分**，
> 不再是需要刻意绕开的边界。本 mod 因此把丢弃做成了**可观测**的：
> `DerivedStats.isClamped`（原版截断）与 `DerivedStats.isCappedByModCeiling`（本 mod 的 FIXED 功能上限）
> 会让 `/rpg stats` 的 `<dim>.clamped` 报 `true`，GUI 行首出现「!」，让玩家知道「继续加点不会再涨」。
>
> **上限是自适应的**：`Stat.LimitMode.LIVE` 的维度在**每次调用时**读 `RangedAttribute.getMaxValue()`，
> 所以装了 AttributeFix 之类的属性上限 mod 之后，上面这张表的「原版 max」会被自动抬高，**代码与配置都不需要改**。
> 只有三个维度是 `FIXED`（伤害减免 90 / 暴击率 100 / 击退抗性 1.0），它们的上限是功能语义，故意不跟随属性。

### 1.7 特别说明：移动速度与服务端校验

**取证结论（API-NOTES 第 12 节，`ServerGamePacketListenerImpl.handleMovePlayer` L896-923）**：
1. 1.21.1 的 `moved too quickly!` 检查用**固定常量** `f2 = 100.0F`（平方距离阈值，约 10 格/tick 水平位移），**不读 `MOVEMENT_SPEED`**。
2. 触发后的动作是 **`this.teleport(玩家当前位置)` 回拉 + warn 日志，不是踢出**。
3. `isSingleplayerOwner()` **完全豁免**。
4. 原版玩家步行约 4.317 m/s ≈ 0.216 格/tick ⇒ 阈值约等于 **46 倍原版速度**。

**本 mod 的处置（T17 已改）**：~~把 `sup(MOVEMENT_SPEED)` 压在 1.5（15 倍原版）~~
> 🔴 **T17：这两个 1.5 都删掉了。** `Stat.MOVEMENT_SPEED` 改成 `LimitMode.LIVE`（上限 = 属性自身 max，原版 1024），
> 运行时硬钳 `movement_speed.hard_cap_attr` 的默认值也从 `1.5` 改成 **`0 = 关闭`**。
> 理由：用户要求「其余成长限制全部取消」，而移速上的这两个 1.5 正是本 mod 自己加的限制。
>
> **必须提醒玩家的一点**：删掉本 mod 的限制后，**原版**的 `moved too quickly!` 检查就成了实际上限。
> 它不读 `MOVEMENT_SPEED`、用固定阈值（约 10 格/tick，≈46 倍原版），触发后**只是把你回拉并记一条 warn，不会踢人**。
> **这是原版机制，不是本 mod 在限制你**；单机房主（`isSingleplayerOwner()`）完全豁免。
> 管理员若仍想要一道本地兜底，可以把 `movement_speed.hard_cap_attr` 设成任意正数重新启用。

### 1.8 特别说明：生命恢复为何不会「不死」

> **T19 更新：本节原有的「不会不死」论证已作废。** 原论证依赖 `health_regen.hard_cap_hps` 的默认 20 HP/s 上界；
> T19 按用户要求取消了全部成长限制，该硬钳默认改为 `0 = 关闭`（`regenHardCapEnabled() == false`），
> 因此回复速率**只受属性自身范围（1.0E6）限制**。
> **诚实结论：堆到很高之后，玩家在数值上确实可以做到「回复 ≥ 任何 DPS」，本 mod 不再提供这条数学保证。**

- 有效回复速率 `R = V_flat(x) * (1 + V_pct(p)/100)`，默认**没有任何 mod 侧钳制**。
- 管理员若仍想要一道兜底，把 `health_regen.hard_cap_hps` 设为正数即可重新启用。
- 另有两个保险：
  - `health_regen.only_when_hurt`（默认 `true`）：满血时不回血，与原版 `FoodData.tick` 的 `player.isHurt()` 语义一致（证据 API-NOTES 第 4 节）。
  - `damage_reduction` 的渐近上限 **90%**（见 `1.17）⇒ 玩家**至少承受 10% 伤害**。这是目前**唯一仍然成立**的「不会免疫」保证。
- **与原版自然回血的叠加关系**：我们的回血是**额外叠加**（`PlayerTickEvent.Post` 里 `player.heal(...)`），原版 `FoodData.tick` 的饱食度回血**照常生效**。两者互不干扰、不重复计算：原版逻辑完全在 `FoodData` 内，我们从不调用 `FoodData`。
  - 配置 `health_regen.require_food_level`（默认 `0` = 不要求）：设成 `18` 可让自定义回复也受饥饿约束，与原版手感一致。

### 1.9 特别说明：基础伤害减免为何不会无敌，且不与防御力重复计算

**完整伤害结算管线（逐行取证，API-NOTES 第 3 节）**：

```
 1. LivingEntity#hurt(source, amount)
 2.   Entity#isInvulnerableTo  ->  EntityInvulnerabilityCheckEvent
 3.   new DamageContainer(source, amount)
 4.   ** LivingIncomingDamageEvent **                  <- 可 setAmount / addReductionModifier
 5.   LivingShieldBlockEvent                           <- 盾牌格挡，从 newDamage 扣除 blockedDamage
 6.   LivingEntity#actuallyHurt
 7.     Reduction.ARMOR          = getDamageAfterArmorAbsorb(...)      <- 护甲 + 韧性 + modifyArmorEffectiveness
 8.     getDamageAfterMagicAbsorb(...) 内部依次:
 8a.        Reduction.MOB_EFFECTS    <- 抗性提升（DAMAGE_RESISTANCE 药水）
 8b.        Reduction.ENCHANTMENTS   <- 保护附魔（EnchantmentHelper.getDamageProtection）
 9.     ** LivingDamageEvent.Pre **                     <- 我们在这里应用「基础伤害减免」
10.     Reduction.ABSORPTION      <- 伤害吸收（金苹果）
11.     扣血 setHealth(getHealth() - newDamage)
12.     ** LivingDamageEvent.Post **                    <- 生命偷取在这里读取「实际生命损失」
13.   死亡 -> LivingDeathEvent -> LivingDropsEvent / LivingExperienceDropEvent
```

**结论 1：不与防御力重复计算。**
- 「基础防御力」维度挂 **`Attributes.ARMOR`**，走第 7 步的**护甲管线**（`CombatRules.getDamageAfterAbsorb`，有效护甲钳到 20 ⇒ 纯护甲最多 80% 减伤）。
- 「基础伤害减免」维度挂 **`LivingDamageEvent.Pre`**（第 9 步），此时护甲 / 抗性 / 保护附魔**已全部结算完毕**，我们作用在**剩余伤害**上。
- 两者数学上串联、互不重叠。✔ 正是用户要求的「确保不与防御力 armor 重复计算」。

**结论 2：为何不把减伤挂到 `DamageContainer.Reduction`？**
- `Reduction` 是**封闭枚举**（只有 6 项，API-NOTES 第 3 节证据 E），**无法新增成员**。
- `addReductionModifier(Reduction.ENCHANTMENTS, ...)` **不可靠**：只有 `f3 > 0`（玩家有保护附魔）时才触发（证据 C L1772）。
- `addReductionModifier(Reduction.ARMOR, ...)` 可行（L1787 无条件调用），但它会把自定义减伤**混进护甲统计**，导致 GUI/统计里「护甲减伤」虚高，且与防御力维度语义混淆。
- **⇒ 采用 `LivingDamageEvent.Pre` + `setNewDamage`，语义最清晰、时序最确定。**

**结论 3（BL-3 裁决）：基础伤害减免**只采用 α 口径（每点 +1 个百分点），**不采用 β 口径**（每点减免固定点数伤害）。**
⇒ 公式里**没有**「固定吸收量 A」这一项，这是与本文早前草案的唯一差异（早前草案同时写了 A 和 R，两者混用会让 verifier 无法判定；现统一为纯百分比）。

设进入第 9 步的伤害为 `D >= 0`，我们的减伤百分点为 `R`（`DESIGN_BASELINE` 口径下 `0 <= R < 80`，见 `1.14.5`）：

```
D' = D * (1 - R/100)
   >= 0                        // 永不产生负伤害（负伤害会变成治疗）
且 R < 80  =>  (1 - R/100) > 0.20
=> 对所有 D > 0，D' > 0.20 * D > 0      // 永远挨打，且至少承受 20%
```

**因为 R 有严格的 80% 上界，「完全免疫」在数学上不可能出现** ⇒ 早前草案里的「小伤害被完全吸收」漏洞与 `never_immune` 兜底配置**都不再需要，已从设计中删除**。

**结论 3.1（verifier B-3，硬性验收项）：`/kill` 必须仍然能杀死玩家。**
- 取证：`/kill` → `KillCommand` L28 `entity.kill()` → `Entity.kill()` L333；对 `LivingEntity` 走 `hurt(damageSources().genericKill(), Float.MAX_VALUE)`，
  伤害类型 id 为 `minecraft:generic_kill`（证据：`DamageTypes.java` L55）。
- ⇒ **`minecraft:generic_kill` 与 `minecraft:fell_out_of_world`（`DamageTypes.java` L22，实际 id 是 `minecraft:out_of_world`）必须永久绕过减伤，且不可被配置移除。**
- 实现：在 `CombatEvents.onLivingDamagePre` 里做**硬编码短路**，配置只能**追加**，不能删除：

```java
/** 硬编码的永久绕过集：配置只能往里加，不能删。 */
private static final Set<ResourceKey<DamageType>> ALWAYS_BYPASS = Set.of(
        DamageTypes.GENERIC_KILL,        // /kill —— 管理员必须永远能处置玩家
        DamageTypes.FELL_OUT_OF_WORLD);  // 虚空 —— 否则玩家能在虚空里活着
private static final TagKey<DamageType>[] ALWAYS_BYPASS_TAGS = new TagKey[]{
        DamageTypeTags.BYPASSES_INVULNERABILITY };   // DamageTypeTags.java L11

@SubscribeEvent
public static void onLivingDamagePre(LivingDamageEvent.Pre event) {                 // L1789 的钩子
    if (!(event.getEntity() instanceof ServerPlayer sp)) return;
    DamageSource src = event.getSource();
    if (ALWAYS_BYPASS.contains(src.typeHolder().unwrapKey().orElse(null))
            || Arrays.stream(ALWAYS_BYPASS_TAGS).anyMatch(src::is)) {
        return;                                                                     // 永久绕过，减伤完全不生效
    }
    double dr = DerivedStats.damageReductionPercent(sp);                             // 0 <= dr < 80
    if (dr <= 0.0) return;
    float d = event.getNewDamage();
    if (d <= 0.0F) return;
    event.setNewDamage((float) (d * (1.0 - dr / 100.0)));                            // L81 setNewDamage
}
```

- **验收点（verifier 必须测）**：把一个玩家所有维度拉满（`/rpg debug override` 到 sup），然后 `/kill` —— **必须死亡**；再把玩家放进虚空 —— **必须死亡**。
- 配置 `damage_reduction.extra_bypass_damage_types`（默认空列表）只用于**追加**，读取时会与 `ALWAYS_BYPASS` 取并集。

### 1.10 特别说明：生命偷取

```
实际回血 H = min( hps_cap 剩余额度,
                  D_actual * lifestealPct / 100 + lifestealFlatPercentPoints )
其中 D_actual = LivingDamageEvent.Post.getNewDamage()   // 目标真正损失的生命值，不是原始伤害
```

- 用 `getNewDamage()`（Post）而不是原始伤害 ⇒ **不过度击杀也吸血**（对 1 心僵尸打 100 伤害只吸 1 心的比例）。
- 只在 `source.getEntity() instanceof ServerPlayer` 且 `victim != attacker` 时生效。
- 排除 `DamageTypes.THORNS`（反伤不该吸血，可用 `lifesteal.include_thorns` 配置）。
- 全局 `lifesteal.hps_cap`（**T19 起默认 `0 = 关闭`**，`lifestealHpsCapEnabled() == false`）用「每 tick 剩余额度」限流，防止一次 AoE 回满。关闭时额度为 `Double.MAX_VALUE`（**不是 0** —— 0 会被下方的 `budget > 0` 守卫当成「跳过」，等于彻底禁用吸血而不是取消上限）。

### 1.11 特别说明：掉落倍率

- 语义：属性值 `v` ⇒ 数量倍率 `m = 1 + v/100`（`v` 有 sup 900 ⇒ `m` 有 sup 10 倍）。
- 作用点两个（都只在服务端）：
  - `LivingDropsEvent`（生物死亡掉落，`Collection<ItemEntity> getDrops()`）
  - `BlockDropsEvent`（方块掉落，`List<ItemEntity> getDrops()`，javadoc 明确「A mutable list」）
- **实现细节（BL-6 裁决：小数处理 + 超组拆分 + 服务端 RNG）**：

```java
// 1) 小数处理：随机取整（STOCHASTIC，默认），期望值无偏
double e   = oldCount * m;
int    base = (int) Math.floor(e);
double frac = e - base;
int newCount = base + (level.random.nextDouble() < frac ? 1 : 0);      // 只在服务端调用
// 备选：lines.drop_rate.rounding = CEIL（总是向上取整，利于玩家）/ FLOOR（总是向下，保守）

// 2) 写回原实体（最稳，不依赖集合可变性）
itemEntity.getItem().setCount(Math.min(newCount, maxStackSize));

// 3) 超出一组的余量：自己 new 实体并 addFreshEntity，**绝不依赖 getDrops() 的返回值可变**
int remain = newCount - Math.min(newCount, maxStackSize);
while (remain > 0) {
    int n = Math.min(remain, maxStackSize);
    ItemStack extra = itemEntity.getItem().copy();
    extra.setCount(n);
    ItemEntity ie = new ItemEntity(level, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), extra);
    ie.setDefaultPickUpDelay();
    level.addFreshEntity(ie);
    remain -= n;
    if (++spawned >= RpgConfig.dropMaxEntitiesPerEvent.get()) break;    // 4) 实体数硬钳
}
```

| 规则 | 值 | 理由 |
|---|---|---|
| 取整方式 | **STOCHASTIC 随机取整**（默认），备选 `CEIL` / `FLOOR`（配置 `lines.drop_rate.rounding`） | 期望值 = `oldCount * m` **无偏**；用 `ceil` 会让「+10% 掉落」变成「每次至少 +1」，在小数量上严重偏强 |
| 拆组阈值 | `itemEntity.getItem().getMaxStackSize()`（通常 64） | 超过一组的余量必须拆成多个 `ItemEntity`，否则 `setCount` 会被原版钳掉 |
| 单次事件实体硬钳 | `drop_rate.max_entities_per_event = 64` | 防 10 倍掉落 + 大量掉落物时一次刷上千实体卡服 |
| 单次事件物品硬钳 | `drop_rate.max_items_per_event = 256` | 同上，按物品总数再限一层 |
| **RNG 必须在服务端** | 用 `level.random` 或 `player.getRandom()`，且处理器开头就判 `level.isClientSide()` | 双端各自取随机数会导致客户端预测与服务端结果不一致（掉落实体由服务端生成，客户端只接收） |
| 与原版时运 / Looting 的关系 | **相乘**（它们已算进 `getDrops()` 的数量） | 符合直觉，且无需我们额外处理 |
- 原版 `Looting` 附魔与我们的倍率**相乘**（它们在事件之前就已算进 `getDrops()`），这是符合直觉的。

### 1.12 特别说明：经验获取倍率

- 语义：属性值 `v` ⇒ 倍率 `m = 1 + v/100`，`sup v = 600 ⇒ sup m = 7 倍`。
- **单点介入，杜绝重复计数**：只在 `XpService.grant(player, source, rawXp)` 这一个函数里乘 `m`；所有 XP 来源（击杀 / 挖矿 / 合成 / 熔炼 / 钓鱼 / 交易）都调用它。**绝不**同时挂 `LivingExperienceDropEvent` 和 `PlayerXpEvent.XpChange`（那会变成 `m²`）。
- 全局限流 `xp.minute_cap`（默认 `0` = 关闭；服务器推荐 `3000`）：滚动 60 秒窗口内计入的 XP 上限，超出部分不入账。见第 8.3 节。

### 1.13 「无上限」被满足的总结陈述

| 用户要求 | 实现方式 | 结论 |
|---|---|---|
| 等级无上限 | `RpgMath.levelForXp` 无上界，`general.max_level = -1` | ✔ 完全满足 |
| 每条线点数无上限 | `max_points = -1`，SAT 严格单调 ⇒ 点永远有用 | ✔ 完全满足 |
| 单维度数值无上限 | 3 个维度（攻击伤害 / 生命值 / 暴击伤害）走 `LINEAR`，`sup = +inf`，真无上限；其余 10 个维度走 `SATURATING`，`sup` 有限但**点数仍无上限**（A1/A3） | ✔ 满足（攻击伤害与生命值受原版 2048 / 1024 钳制，见 1.6，已明确告知） |
| 非击杀行为不可刷 | 挖矿 / 合成 / 熔炼 / 钓鱼 / 交易改为**首次行为奖励**（见 `8.7`），总量上界硬性封顶 | ✔ 满足（lead 决策 B） |
| percent 不退化 | 7 个自定义维度改用 `DESIGN_BASELINE` 基数，percent 从第 1 点起即有收益（见 `1.14`） | ✔ 满足（T5 裁决） |
| 护甲收益边界透明 | 明确「属性 sup=30 但伤害公式的有效护甲钳到 20 ⇒ 减伤上限 80%」，并给出实测表（见 `1.15`） | ✔ 满足（verifier B-1） |
| 不破坏游戏 | 9 个有功能天花板的维度全部用 SAT 收敛，A4 表逐条给出 `sup <= HARD_MAX` 证明 | ✔ 完全满足 |

---

## 1.14 【T5 裁决】百分比提升（percent）的作用基数 —— 逐维度定义与退化处置

> 本节是对 verifier BL-4 的正式裁决，也是对 lead T5 任务三项要求的正面回答。
> **结论先行：verifier 的「4 个维度 percent 恒为 0」部分不成立 —— armor / armor_toughness / knockback_resistance 的 percent 在玩家穿着装备时是有效的；真正会退化为 0 的是 7 个自定义属性维度，且只在「未投入任何 flat」时退化。**

### 1.14.1 原版语义取证（三条证据，逐条 grep）

**证据 1：`AttributeInstance.calculateValue()`（`MC\net\minecraft\world\entity\ai\attributes\AttributeInstance.java` L145-163）**

```java
private double calculateValue() {
    double d0 = this.getBaseValue();
    for (AttributeModifier m : getModifiersOrEmpty(Operation.ADD_VALUE))            d0 += m.amount();          // L148-150
    double d1 = d0;                                                                                            // L152
    for (AttributeModifier m : getModifiersOrEmpty(Operation.ADD_MULTIPLIED_BASE))  d1 += d0 * m.amount();     // L154-156
    for (AttributeModifier m : getModifiersOrEmpty(Operation.ADD_MULTIPLIED_TOTAL)) d1 *= 1.0 + m.amount();    // L158-160
    return this.attribute.value().sanitizeValue(d1);                                                           // L162
}
```

⇒ **`ADD_MULTIPLIED_BASE` 的乘数是 `d0 = base + SUM(该属性上所有 ADD_VALUE)`，不是 `base`。**
⇒ **`sanitizeValue` 在最后一步（L162）才调用，会把 `ADD_MULTIPLIED_BASE` 算出来的结果一起钳掉**（这是 lead 第 3 问的答案）。

**证据 2：装备给这三个属性加修饰符用的是 `ADD_VALUE`（`MC\net\minecraft\world\item\ArmorItem.java` L77-89）**

```java
itemattributemodifiers$builder.add(
    Attributes.ARMOR, new AttributeModifier(resourcelocation, (double)i, AttributeModifier.Operation.ADD_VALUE), equipmentslotgroup);              // L77-79
itemattributemodifiers$builder.add(
    Attributes.ARMOR_TOUGHNESS, new AttributeModifier(resourcelocation, (double)f, AttributeModifier.Operation.ADD_VALUE), equipmentslotgroup);     // L80-82
float f1 = material.value().knockbackResistance();
if (f1 > 0.0F) {
    itemattributemodifiers$builder.add(
        Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(resourcelocation, (double)f1, AttributeModifier.Operation.ADD_VALUE), equipmentslotgroup);  // L85-89
}
```

⇒ **护甲 / 韧性 / 击退抗性的装备来源全部是 `ADD_VALUE` ⇒ 它们会被 `ADD_MULTIPLIED_BASE` 一起放大。**

**证据 3：装备修饰符确实进入同一个 `AttributeInstance` 的 ADD_VALUE 池（`MC\net\minecraft\world\entity\LivingEntity.java` L2628-2633）**

```java
itemstack2.forEachModifier(equipmentslot1, (p_352705_, p_352706_) -> {
    AttributeInstance attributeinstance = this.attributes.getInstance(p_352705_);
    if (attributeinstance != null) {
        attributeinstance.removeModifier(p_352706_.id());     // L2631
        attributeinstance.addTransientModifier(p_352706_);    // L2632
    }
    ...
});
```

⇒ 装备的 modifier 与我们的 modifier 在**同一个 map** 里，`calculateValue` 的 ADD_VALUE 循环会一并求和。

### 1.14.2 裁决结论（对 verifier BL-4 逐条回应）

| verifier 的论断 | 裁决 | 依据 |
|---|---|---|
| 「armor 的 percent 恒为 0」 | **不成立**。穿任何护甲时 `d0 = 装备护甲 + SUMflat > 0`，percent 会放大装备护甲。只有**完全裸装且 flat=0** 时才为 0。 | 证据 2 + 3 |
| 「armor_toughness 的 percent 恒为 0」 | **不成立**，同上（钻石 / 下界合金护甲提供韧性 ADD_VALUE）。 | 证据 2 + 3 |
| 「knockback_resistance 的 percent 恒为 0」 | **不成立**，下界合金护甲每件提供 `+0.1` 的 ADD_VALUE 击退抗性；裸装时为 0。 | 证据 2 + 3 |
| 「crit_chance 的 percent 恒为 0」 | **成立**。自定义属性，无任何外部 ADD_VALUE 来源，base=0 ⇒ flat=0 时确实恒为 0。 | 证据 1（d0 = 0 + 0） |
| （verifier 未提，lead 已指出）其余 6 个自定义属性 | **同样成立**：`health_regen / damage_reduction / crit_damage / lifesteal / drop_rate / xp_gain` 在没有 flat 投入时 percent 贡献恒为 0。 | 证据 1 |

**修正后的准确表述**：会「percent 投了点却完全无收益」的维度是 **7 个自定义属性维度**，且**仅在该维度 flat 点数为 0 时**；
6 个原版属性维度中只有 `armor / armor_toughness / knockback_resistance` 在**裸装**时有 0 基数窗口，
`attack_damage`(base 1.0) / `max_health`(base 20.0) / `movement_speed`(base 0.1) **永不退化**。

### 1.14.3 两种 percent 基数模式（配置可选，默认值已定）

为彻底消除退化，`percent` 线支持两种明确的基数模式，由 `lines.<dim>.percent.basis` 选择：

| 模式 | 公式 | 实现方式 | 基数何时为 0 |
|---|---|---|---|
| **`TOTAL`**（原版语义） | `final = (base + SUM(所有 ADD_VALUE，含装备)) * (1 + V_pct/100)` | percent 线用 `ADD_MULTIPLIED_BASE`，amount = `V_pct/100` | 仅当该属性上**没有任何** ADD_VALUE 来源时 |
| **`DESIGN_BASELINE`**（设计参考基数） | `final = base + V_flat + B_ref * V_pct / 100` | percent 线改用**独立的 ADD_VALUE** modifier（id = `rpgleveling:<stat>_pctbasis`），**不注册** ADD_MULTIPLIED_BASE | **永不为 0**（只要 `B_ref > 0` 且 `V_pct > 0`） |

**默认分配（T5 首次裁决 → T6 修正后）**：

| 版本 | TOTAL 维度 | DESIGN_BASELINE 维度 |
|---|---|---|
| T5（`1.14.4` 原表） | 6 个原版属性维度 | 7 个自定义维度 |
| **T6（本版，权威）** | **3 个：attack_damage / max_health / movement_speed** | **10 个：armor / armor_toughness / knockback_resistance + 7 个自定义** |

> **为什么 T6 要把 armor / armor_toughness / knockback_resistance 从 TOTAL 改成 DESIGN_BASELINE**：
> 因为 TOTAL 的被乘数含装备，而下界合金已经吃掉 67% / 60% / 40% 的余量，TOTAL 在数学上**没有余量可分配**（见 `1.16.3`）。
> 判定依据是 `1.16.2` 的 `equipment_max` 列，不是「有没有外部来源」。

`DESIGN_BASELINE` 的语义是「百分比只是一个系数」：percent 的每个点把 `B_ref` 的 `V_pct/100` 绝对量加到该属性上。它的上限完全可证：`max contribution = B_ref * capPct / 100`。

### 1.14.4 逐维度「百分比作用基数」定义表（13 维度全表）

| # | 维度 | base | 外部 ADD_VALUE 来源（原版） | **默认 basis 模式** | **基数精确定义** | flat=0 且无装备时 percent 收益 |
|---|---|---|---|---|---|---|
| 1 | attack_damage | 1.0 | 武器 / 工具 / 剑（ItemAttributeModifiers） | **`TOTAL`** | `1.0 + 武器攻击力 + SUMflat` | 有效（base 1.0 > 0） |
| 2 | max_health | 20.0 | 生命提升物品 / `/attribute` | **`TOTAL`** | `20.0 + SUMflat` | 有效（base 20.0） |
| 3 | armor | 0.0 | **护甲装备（ArmorItem L77-79，ADD_VALUE）= 20** | **`DESIGN_BASELINE`**（T6 改） | `20 + SAT(x_flat,6,40) + 4.0 * SAT(x_pct,100,60)/100`，sup = 30 | **永不退化** |
| 4 | armor_toughness | 0.0 | **护甲装备（ArmorItem L80-82，ADD_VALUE）= 12** | **`DESIGN_BASELINE`**（T6 改） | `12 + SAT(x_flat,5,40) + 3.0 * SAT(x_pct,100,60)/100`，sup = 20 | **永不退化** |
| 5 | movement_speed | 0.1 | 靴子 / `/attribute`（原版无物品来源，equipment_max = 0） | **`TOTAL`** | `(0.1 + SAT(x_flat,0.4,60)) * (1 + SAT(x_pct,200,80)/100)`，sup = 1.5 | 有效（base 0.1） |
| 6 | knockback_resistance | 0.0 | **下界合金护甲（ArmorItem L85-89，ADD_VALUE）= 0.4** | **`DESIGN_BASELINE`**（T6 改） | `0.4 + SAT(x_flat,0.35,40) + 0.20 * SAT(x_pct,100,60)/100`，sup = **0.95 < 1.0** | **永不退化，且永不免疫** |
| 7 | health_regen | 0.0 | **无** | **`DESIGN_BASELINE`** | `B_ref = 2.0 HP/s` | **有效**（2.0 * V_pct/100） |
| 8 | damage_reduction | 0.0 | **无** | **`DESIGN_BASELINE`** | `B_ref = 40.0 百分点` | **有效** |
| 9 | crit_chance | 0.0 | **无** | **`DESIGN_BASELINE`** | `B_ref = 20.0 百分点` | **有效** |
| 10 | crit_damage | 0.0 | **无** | **`DESIGN_BASELINE`** | `B_ref = 50.0 百分点` | **有效** |
| 11 | lifesteal | 0.0 | **无** | **`DESIGN_BASELINE`** | `B_ref = 10.0 百分点` | **有效** |
| 12 | drop_rate | 0.0 | **无** | **`DESIGN_BASELINE`** | `B_ref = 50.0 百分点` | **有效** |
| 13 | xp_gain | 0.0 | **无** | **`DESIGN_BASELINE`** | `B_ref = 50.0 百分点` | **有效** |

**推荐理由**：
- **3 个维度（attack_damage / max_health / movement_speed）用 `TOTAL`**：它们的 `equipment_max` 相对 `HARD_MAX` 可忽略（9/2048、0/1024、0/1.5），TOTAL 的「放大身上总量」语义既能成立又不会撞上限。
- **其余 10 个维度用 `DESIGN_BASELINE`**：`armor / armor_toughness / knockback_resistance` 是因为**装备已吃掉大部分余量**（T6 修正，见 `1.16.3`）；
  7 个自定义属性维度是因为**没有任何外部来源**，用 `TOTAL` 会让「只投 percent」变成纯粹的浪费，违反公理 A3（点不浪费）。
  ⇒ 统一改用 `DESIGN_BASELINE` 后，percent 从第 1 点起就有确定收益，且 `sup` 完全可证。

### 1.14.5 DESIGN_BASELINE 对安全性证明的影响（`1.5` 的增补行）

`DESIGN_BASELINE` 把「乘法」换成「加法」，因此 `sup` 从 `flatCap * (1 + pctCap/100)` 变为 `equipment_max + flatCap + B_ref * pctCap / 100`
（**T6 补上装备项**；本表 7 个自定义维度的 `equipment_max = 0`，所以数值不变）：

| # | 维度 | flatCap | pctCap | B_ref | percent 最大贡献 | **新 sup** | HARD_MAX | 结论 |
|---|---|---|---|---|---|---|---|---|
| 7 | health_regen | 5 HP/s | 200 | 2.0 | 4.0 HP/s | **无上限（LINEAR）** | 属性范围 1.0E6 | **T19 取消成长限制** |
| 8 | damage_reduction | 60 | 50 | 40.0 | 20.0 | **80.0 %** | 90 % | ✔ 80 < 90（比原方案 90 更保守） |
| 9 | crit_chance | 50 | 100 | 20.0 | 20.0 | **70.0 %** | 100 % | ✔ 70 < 100（比原方案更保守） |
| 10 | crit_damage | 无限 | 无限 | 50.0 | 无限 | **无限** | 无 | ✔ 真无上限（本维度本就无天花板） |
| 11 | lifesteal | 20 | 100 | 10.0 | 10.0 | **30.0 %** | 60 % | ✔ 30 < 60 |
| 12 | drop_rate | 150 | 500 | 50.0 | 250.0 | **400.0 %**（= 5 倍） | 1000 % | ✔ 400 < 1000 |
| 13 | xp_gain | 100 | 500 | 50.0 | 250.0 | **350.0 %**（= 4.5 倍） | 1000 % | ✔ 350 < 1000 |

> **注意**：`DESIGN_BASELINE` 让第 8 / 9 / 11 维度的 sup **比原方案更保守**（90→80、100→70、40→30），因此 `1.13`「不破坏游戏」的结论仍成立且余量更大。
> `1.5` 表中这 7 行的 sup 值**以本表为准**；`1.5` 保留原乘法口径作为 `TOTAL` 模式下的上界。
> 六个原版属性维度（1-6）不变，仍为 `TOTAL`，sup 与 `1.5` 完全一致（护甲 30 / 韧性 20 / 移速 1.5 / 击退 0.9）。

### 1.14.6 退化情形的玩家体验处置（lead 要求 2）

| 场景 | 处置 |
|---|---|
| 维度 3 / 4 / 6（`TOTAL`）**裸装且 flat=0** 时玩家想投 percent | **不禁止**（穿上装备后立刻生效，禁止反而造成困惑）。GUI 的 percent 行显示灰色提示：`「当前基数为 0（未穿装备且未投数值点），此加点在穿上装备后才会生效」`；tooltip 给出当前基数与服务端算出的实际贡献值。 |
| 维度 7-13（`DESIGN_BASELINE`） | 基数恒为 `B_ref > 0`，**不存在退化**。GUI 直接显示「每点约 +X.X%」。 |
| 管理员把某个自定义维度强制改成 `TOTAL` | 服务端 `RpgService.spend` 检测到 `basis == TOTAL` 且该维度 `hasExternalBasis() == false` 且 `points[flat] == 0` 时**拒绝本次加点**并回 `FB_ZERO_BASIS`，附提示「该维度的百分比作用于『数值点 + 装备』的总量，当前基数为 0，请先投入数值点」。这样 A3（点不浪费）由**服务端强制保证**，而不是只靠 UI 提示。 |
| 已投入的 percent 因装备脱下而失效 | **不回退点数**（点数已转化成 modifier，脱下装备只是让基数变 0）。GUI 显示「基数 0 ⇒ 当前贡献 0」，玩家可自行 `/rpg respec` 免费洗点。 |

### 1.14.7 BL-1 / BL-2 的明确回答（verifier 要求「二选一」）

| 编号 | 问题 | **裁决** | 精确公式 | 证据 |
|---|---|---|---|---|
| **BL-1** | percent 叠加是线性还是复利？ | **线性（加算），绝不复利** | `final = B * (1 + SUM(pct_i) / 100)`。本 mod 每个维度只有**一条** percent 线 ⇒ `SUM = V_pct`。与其他 mod 的 percent 也是**相加**，不是相乘。 | `calculateValue` L154-156：`d1 += d0 * amount` ⇒ 多个 ADD_MULTIPLIED_BASE 在乘之前先求和 |
| **BL-2** | flat 与 percent 的运算顺序？ | **先加后乘**：`(B + flat) * (1 + pct/100)`，**不是** `B*(1+pct/100) + flat` | `d0 = base + SUM(ADD_VALUE)`（含 flat）先算完，再乘 `(1 + SUM(ADD_MULTIPLIED_BASE))` | `calculateValue` L148-156 的顺序 |

> 三处必须一致：**配置语义、本文公式、`RpgMath.statValue()` 的实现**。verifier 可直接用 1.14.9 的数值样例对拍。

### 1.14.8 取整规则（verifier BL-5 的「取整规则」部分）

**总原则：曲线层与属性层一律不取整（全程 `double`），只在「点使用处」按各自物理语义取整。**

| 维度 | 内部表示 | 取整 / 使用规则 |
|---|---|---|
| 1 attack_damage | `double` modifier | 不取整（原版 `AttributeInstance` 即 double） |
| 2 max_health | `double` modifier | 不取整；血条显示沿用原版 |
| 3 armor / 4 armor_toughness | `double` modifier | 不取整；原版 `sanitizeValue` 最后钳到 [0,30] / [0,20] |
| 5 movement_speed | `double` modifier | 不取整 |
| 6 knockback_resistance | `double` modifier | 不取整；原版钳到 [0,1] |
| 7 health_regen | `double` HP/s | 每 tick 累积 `hps/20`，**累积到 `>= 1.0` 才 `heal(floor(acc))`**，余数留到下 tick（见第 7 节）。显示保留 2 位小数。 |
| 8 damage_reduction | `double` 百分点 | 不取整；`D' = D * (1 - R/100)` 用 `float` 计算。显示保留 1 位小数。 |
| 9 crit_chance | `double` 百分点 | **概率判定**：`player.getRandom().nextDouble() * 100.0 < critChance`。显示保留 1 位小数。 |
| 10 crit_damage | `double` 百分点 | 不取整；`multiplier = 1.5f + (float)(critDamage / 100.0)`。显示保留 1 位小数。 |
| 11 lifesteal | `double` 百分点 | 回血量先进累积器，**累积到 `>= 1.0` 才 `heal`**（与维度 7 共用 `RegenAccumulator`）。 |
| 12 drop_rate | `double` 倍率 | **随机取整（STOCHASTIC，默认）**：`e = oldCount * m; n = floor(e); if (rng.nextDouble() < e - n) n++`。备选 `CEIL` / `FLOOR`，配置 `lines.drop_rate.rounding`。 |
| 13 xp_gain | `double` 百分点 | `granted = Math.round(rawXp * mult)`；结果 `< 0` 时钳为 0。 |

**取整只允许出现在上表标注的位置**；任何在 `RpgMath` 内部对中间值取整的实现都视为缺陷（会让 verifier 的对拍产生歧义）。

### 1.14.9 给 verifier 的数值对拍样例（可直接写判定）

```
// ===== TOTAL 模式（T6 修正后只剩 3 个维度）：final = (base + V_flat) * (1 + V_pct/100) =====
attack_damage  (flat=  0, pct=100) = (1.0  + 0     ) * (1 + 300.0000/100) = 4.0000     // 两条线都是 LINEAR
max_health     (flat=  0, pct=100) = (20.0 + 0     ) * (1 + 200.0000/100) = 60.0000    // 两条线都是 LINEAR
movement_speed (flat=100, pct=100) = (0.1  + 0.2500) * (1 + 111.1111/100) = 0.7389

// ===== DESIGN_BASELINE 模式（T6 修正后共 10 个维度）：final = EQUIP + V_flat + B_ref * V_pct / 100 =====
// --- T6 新增的三条「小余量」线（新 cap 见 1.16.3；EQUIP 为全套下界合金）---
armor            EQUIP=20  B_ref=4.0  (flat=  0, pct=  0) = 20 + 0        + 0                    = 20.0000
armor            EQUIP=20  B_ref=4.0  (flat= 20, pct= 20) = 20 + 2.0000   + 4* 25.0000/100 =  1.0000 -> 23.0000
armor            EQUIP=20  B_ref=4.0  (flat=200, pct=200) = 20 + 5.0000   + 4* 76.9231/100 =  3.0769 -> 28.0769  (< 30，未钳制)
armor            EQUIP= 0  B_ref=4.0  (flat=200, pct=200) =  0 + 5.0000   + 3.0769              =  8.0769  (裸装)
armor_toughness  EQUIP=12  B_ref=3.0  (flat=200, pct=200) = 12 + 4.1667   + 3* 76.9231/100 =  2.3077 -> 18.4744  (< 20，未钳制)
knockback_resist EQUIP=0.4 B_ref=0.20 (flat=100, pct=100) = 0.4+ 0.2500   + 0.20*62.5000/100 = 0.1250 ->  0.7750
knockback_resist EQUIP=0.4 B_ref=0.20 (flat=200, pct=200) = 0.4+ 0.2917   + 0.20*76.9231/100 = 0.1538 ->  0.8455  (< 1.0，永不免疫)
// --- T5 的 7 个自定义维度（cap 未变，EQUIP = 0）---
crit_chance      B_ref=20  (flat=  0, pct= 80) = 0        + 20 *  50.0000/100 = 10.0000 百分点
crit_chance      B_ref=20  (flat=  0, pct=200) = 0        + 20 *  71.4286/100 = 14.2857 百分点
crit_chance      B_ref=20  (flat= 50, pct= 80) = 22.7273  + 20 *  50.0000/100 = 32.7273 百分点
crit_damage      B_ref=50  (flat=  0, pct= 50) = 0        + 50 * 100.0000/100 = 50.0000 百分点 (倍率 1.5 -> 2.0)
xp_gain          B_ref=50  (flat=  0, pct=100) = 0        + 50 * 250.0000/100 = 125.0000 百分点 (+125%)
xp_gain          B_ref=50  (flat=100, pct=100) = 62.5000  + 125.0000          = 187.5000 百分点
damage_reduction B_ref=40  (flat= 50, pct= 80) = 23.0769  + 40 *  25.0000/100 = 33.0769 百分点
lifesteal        B_ref=10  (flat=  0, pct= 80) = 0        + 10 *  50.0000/100 =  5.0000 百分点
drop_rate        B_ref=50  (flat=  0, pct=200) = 0        + 50 * 333.3333/100 = 166.6667 百分点 (+166.67%)
health_regen     B_ref=2.0 (flat=  0, pct= 60) = 0        + 2.0 * 100.0000/100 =  2.0000 HP/s
```

> ⚠ **本表在 T6 之后以 `1.16.3` 的新 cap 为准**：armor / armor_toughness / knockback_resistance 三条线**已从 TOTAL 改为 DESIGN_BASELINE**，
> 上面已按新口径重写（旧版把它们列在 TOTAL 块里，结论是错的：真实 sup 分别是 70 / 44 / 1.7，见 `1.16.2`）。其余 10 条线的数值未变。
> 上表中所有 `V_flat / V_pct` 都取自 `1.4` 的「收益曲线预览」表（half 已按 1 点/级重调后的值），verifier 可逐位对拍。
> `movement_speed (100, 100)` 的 0.7389 = `(0.1 + 0.25) * 2.111111`。
> **注意**：`movement_speed` 的 `sup = (0.1 + 0.4) * (1 + 200/100) = 1.5`，但因为 SAT 严格小于 cap，**实际值永远小于 1.5** ——
> **T19 更新**：`movement_speed.hard_cap_attr` 的默认值已改为 **`0 = 关闭`**（不再有 1.5 的软上限）。
> 验收点改为「拉满后属性值不被本 mod 截断；移速极高时会被**原版服务端**的 `moved too quickly` 回拉——那是原版机制，不是本 mod 的限制」。

## 1.15 【T5 裁决】护甲维度的真实收益边界（verifier B-1 正面回应）

> verifier 指出「`CombatRules` 里 `clamp(armor - damage/f, armor*0.2, 20)` 使护甲收益在 20 后大幅递减」。
> **这个方向完全正确，我确认并把它写进设计。这是本次裁决里 verifier 完全正确的一条。**

### 1.15.1 两道独立的钳制（必须分清）

```java
// 第一道：属性层。Attributes.ARMOR = new RangedAttribute("attribute.name.generic.armor", 0.0, 0.0, 30.0)
//    -> AttributeInstance.calculateValue() 最后一步 sanitizeValue 把属性值钳到 [0, 30]
// 第二道：伤害公式层。CombatRules.getDamageAfterAbsorb(...)
public static float getDamageAfterAbsorb(LivingEntity entity, float damage, DamageSource src,
                                         float armorValue, float armorToughness) {
    float f  = 2.0F + armorToughness / 4.0F;                                  // 韧性决定「护甲被伤害侵蚀」的速度
    float f1 = Mth.clamp(armorValue - damage / f, armorValue * 0.2F, 20.0F);  // 有效护甲：上限 20，下限 armor*0.2
    float f2 = f1 / 25.0F;                                                    // 20/25 = 0.8 -> 最多 80% 减伤
    ...
    return damage * (1.0F - f3);
}
```

⇒ **属性 20 点以上不会让「单次小伤害」的减伤超过 80%**（`f1` 被钳到 20，`20/25 = 80%`）。
⇒ **但属性 20 到 30 并非无效**：`f1` 的**下限**是 `armorValue * 0.2`，属性越高，被高伤害侵蚀后的有效护甲下限越高。

### 1.15.2 实测计算表（直接代入 `MC` 公式，供 verifier 对拍）

减伤率 = `clamp( clamp(A - D/(2 + T/4), A*0.2, 20), 0, 25 ) / 25`

| 装备 / 加点组合 | A | T | D=5 | D=10 | D=20 | D=40 | D=80 | D=160 | D=300 |
|---|---|---|---|---|---|---|---|---|---|
| 皮革套 | 10 | 0 | 30.0% | 20.0% | 8.0% | 8.0% | 8.0% | 8.0% | 8.0% |
| 铁 / 钻石套 | 20 | 0 | 70.0% | 60.0% | 40.0% | 16.0% | 16.0% | 16.0% | 16.0% |
| 钻石套（带韧性） | 20 | 8 | 75.0% | 70.0% | 60.0% | 40.0% | 16.0% | 16.0% | 16.0% |
| 本 mod 护甲拉满 | 30 | 0 | 80.0% | 80.0% | 80.0% | 40.0% | 24.0% | 24.0% | 24.0% |
| **护甲 + 韧性双拉满** | **30** | **20** | 80.0% | 80.0% | 80.0% | **80.0%** | **74.3%** | 28.6% | 24.0% |
| 中期（护甲 24 韧性 12） | 24 | 12 | 80.0% | 80.0% | 80.0% | 64.0% | 32.0% | 19.2% | 19.2% |

**结论（对 verifier 的直接回答）**：
1. **「护甲越高越硬」到属性 20 点为止**（对单次伤害不超过约 70 点的情况）。20 点以上若不带韧性，只在高伤害区间有很小的下限收益（24% vs 16%）。
2. **真正让「护甲 30 + 韧性 20」质变的是 `armor_toughness`**：`f = 2 + T/4` 从 2 涨到 7，护甲被伤害侵蚀的速度降到约 1/3.5。在 40 到 80 点伤害区间，减伤从 16% 提到 80% / 74.3% —— **4 倍以上的差距**。
3. ⇒ **`armor_toughness` 从「锦上添花」升格为核心生存维度**，与 `armor` 共同构成「减伤 80%」的物理层。
4. ⇒ `damage_reduction` 是在这 80% 之上**再乘一层**（最高 80%，见 1.14.5）：两层串联后最坏情况 `D' = D * 0.2 * 0.2 = D * 0.04`（**96% 总减伤，但不是无敌**）。

### 1.15.3 设计上的三个明确处置

| # | 处置 | 说明 |
|---|---|---|
| 1 | **明确承认并文档化**：`1.5` 表第 3 行加注「sup=30 是**属性上限**，不是**减伤上限**；减伤上限恒为 80%」。 | 避免「数值 +100% 但体感无变化」的落差。 |
| 2 | **GUI 显示「当前减伤百分比」而不是「护甲点」** | percent 行的 tooltip 直接显示 `CombatRules` 算出的当前减伤率（对 10 / 40 / 100 三档参考伤害各给一个值），玩家立刻能看到「再加 1 点有没有用」。 |
| 3 | **`armor_toughness` 的 half_points 从 60 下调到 40** | 它的真实价值被 1.15.2 证明高于原估计，让它在更早点数就产生体感；cap 保持 10 不变，`1.5` 的 sup=20 不变。 |

## 1.16 【T6 / BL-9 修复】sup 必须含装备项 —— 修正后的完整安全性证明

> **verifier 的 BL-9 成立且严重，我确认。** `1.5` 的 `sup = (base + sup_flat) * (1 + sup_pct/100)` **漏了装备项**，而我自己在 `1.14.1` 刚证明了 TOTAL 模式的被乘数是 `base + 装备 + flat` —— 两处自相矛盾，后者是错的。
> 本节是修正后的**唯一权威**安全性证明；`1.5` 的公式**仅在 equipment_max = 0 时有效**，其余以本节为准。

> 🔴 **T17 覆盖声明（2026-10-08）：本节的「配平」结论已全部作废，只有取证部分仍然有效。**
>
> | 小节 | T17 状态 |
> |---|---|
> | `1.16.1` equipment_max 逐维度取证 | **仍然有效**（下界合金 20/12/0.4、下界合金斧 9.0）。它现在服务于「装备到底加了多少」的测试断言与 `equipmentValue` 诊断，**不再服务于安全性证明**。 |
> | `1.16.2` 13 行 sup 验算 | **只剩第 8 行成立**，且数值改为 `0 + 60 + 40*75/100 = 90 = hardMax`（**取等号**，渐近逼近、永不达到）。其余 12 行因为改成 LINEAR，`sup` 是无穷，**不等式不再成立，也不再声称成立**。 |
> | `1.16.3` armor / armor_toughness / knockback_resistance 的 cap 重配平 | **已删除**。这三个维度改成 LINEAR，没有 `cap`，安全证明无从谈起——这正是用户要的。 |
> | `1.16.4` 「击退抗性永不完全免疫」 | **已放弃**。击退抗性改成 `FIXED = 1.0`，**设计上就是要完全免疫**（超过 1.0 无意义）。 |
> | `1.16.5` 「被钳制」可观测性 | **仍然有效，并且被 T17 加强了**：GUI 判据从 `isClamped`（只认原版截断）换成 `isEffectivelyCapped = isClamped \|\| isCappedByModCeiling`，这样暴击率被 100 封顶时也会出现「!」。 |
> | `1.16.6` 第三方 mod 溢出边界 | **结论反转**：AttributeFix 这类抬高属性范围的 mod 现在会被**主动跟随**（`LimitMode.LIVE` 运行时读 `getMaxValue()`），不再是需要防御的溢出。 |
> | `1.16.7` / `1.16.8` / `1.16.9` | **不受影响**。 |

### 1.16.1 equipment_max 逐维度取证

**定义**：`equipment_max(dim)` = 原版 1.21.1 **终局标准装备**在该属性上提供的 ADD_VALUE 总量（玩家不投任何点时）。

**证据 A：下界合金护甲的三个属性（`MC\net\minecraft\world\item\ArmorMaterials.java` L70-76）**

```java
public static final Holder<ArmorMaterial> NETHERITE = register("netherite", Util.make(new EnumMap<>(ArmorItem.Type.class), p -> {
    p.put(ArmorItem.Type.BOOTS,      3);
    p.put(ArmorItem.Type.LEGGINGS,   6);
    p.put(ArmorItem.Type.CHESTPLATE, 8);
    p.put(ArmorItem.Type.HELMET,     3);
    p.put(ArmorItem.Type.BODY,      11);      // BODY = 狼铠，玩家不穿
}), 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0F, 0.1F, () -> Ingredient.of(Items.NETHERITE_INGOT));
//                                     toughness = 3.0F ^^^^   knockbackResistance = 0.1F ^^^^
```

**证据 B：`ArmorItem` 把 toughness / knockbackResistance 按「每件」写入（L80-89）** ⇒ 四件套要乘 4：

| 属性 | 每件 | 四件合计 = **equipment_max** |
|---|---|---|
| ARMOR | 头 3 + 胸 8 + 腿 6 + 靴 3 | **20** |
| ARMOR_TOUGHNESS | 3.0 x 4 | **12** |
| KNOCKBACK_RESISTANCE | 0.1 x 4 | **0.4** |

**证据 C：武器攻击力（`Items.java` L1022/L1033 + `SwordItem` L47 + `TridentItem` L45 + `MaceItem` L44）**

| 武器 | 传入的 baseline | `Tiers.NETHERITE.getAttackDamageBonus()` | modifier = baseline + bonus |
|---|---|---|---|
| 下界合金斧 | `AxeItem.createAttributes(Tiers.NETHERITE, 5.0F, -3.0F)`（Items L1033） | 4.0（`Tiers.java` L17） | **9.0** ← 最大 |
| 三叉戟 | `TridentItem.createAttributes()` L45 | — | 8.0 |
| 下界合金剑 | `SwordItem.createAttributes(Tiers.NETHERITE, 3, -2.4F)`（Items L1022） | 4.0 | 7.0 |
| 锤 | `MaceItem` L44 | — | 5.0 |

**全维度 equipment_max 表（本设计采用值 + 依据）**

| # | 维度 | equipment_max | 依据 | 取值口径 |
|---|---|---|---|---|
| 1 | attack_damage | **9.0** | 下界合金斧（上表） | 当前版本最大武器 |
| 2 | max_health | **0.0** | 原版 1.21.1 无任何物品 / 附魔给 `MAX_HEALTH` 加 modifier | 当前版本无来源 |
| 3 | armor | **20.0** | 全套下界合金 | 当前版本最大护甲 |
| 4 | armor_toughness | **12.0** | 全套下界合金 | 当前版本最大韧性 |
| 5 | movement_speed | **0.0** | 原版无物品给 `MOVEMENT_SPEED` 加 modifier（迅捷药水走 MobEffect，不入属性） | 当前版本无来源 |
| 6 | knockback_resistance | **0.4** | 全套下界合金 | 当前版本最大击退抗性 |
| 7-13 | 7 个自定义属性 | **0.0** | 只有本 mod 会写这 7 个属性 | 无外部来源 |

> **口径说明（lead 要求 1）**：上表取「**当前版本 1.21.1 的最大值**」，不是「含未来内容的保守值」。
> 理由：(a) 1.21.1 是锁定版本，不会有新内容；(b) 第三方 mod 可能提供更高数值 —— 这种溢出由 1.16.5 的
> 「computed vs effective 可观测性」显式暴露，并由 1.16.6 定义处置边界。**不把未知的第三方数值塞进原版证明里，否则证明无从收敛。**

### 1.16.2 修正后的 sup 定义与逐维度代入验算（lead 要求 2）

**新的 sup 公式（唯一）**：

```
sup(dim) = equipment_max(dim) + sup_flat(dim) + pct_max_contribution(dim)

其中  sup_flat(dim)             = 该维度 flat 线的 cap（SATURATING）或「无限」（LINEAR）
      pct_max_contribution(dim) =
          DESIGN_BASELINE 模式:  B_ref * pctCap / 100
          TOTAL           模式:  (base + equipment_max + sup_flat) * pctCap / 100
      TOTAL 模式下 base 也已计入（1.14.1 的 d0 = base + SUM ADD_VALUE）
```

**13 维度完整代入验算表**（每行写出不等式左边三项与右边 HARD_MAX）：

| # | 维度 | equipment_max | base | basis | sup_flat | pctCap | pct 最大贡献 | **sup 代入** | HARD_MAX | 不等式 | 结论 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | attack_damage | 9.0 | 1.0 | TOTAL | 无限 | 无限 | 无限 | `(1+9+inf)x(1+inf)=inf` | 2048 | inf > 2048 | 见 1.16.2 注 A |
| 2 | max_health | 0.0 | 20.0 | TOTAL | 无限 | 无限 | 无限 | `(20+0+inf)x(1+inf)=inf` | 1024 | inf > 1024 | 见 1.16.2 注 A |
| 3 | armor | **20.0** | 0.0 | **DESIGN_BASELINE** | **6.0** | 100 | `4 x 100/100 = 4` | `20 + 6 + 4 = **30**` | 30 | 30 <= 30 OK（SAT 严格 => < 30） | 无丢弃 |
| 4 | armor_toughness | **12.0** | 0.0 | **DESIGN_BASELINE** | **5.0** | 100 | `3 x 100/100 = 3` | `12 + 5 + 3 = **20**` | 20 | 20 <= 20 OK（严格 => < 20） | 无丢弃 |
| 5 | movement_speed | 0.0 | 0.1 | TOTAL | 0.4 | 200 | `(0.1+0+0.4)x2 = 1.0` | `0.1 + 0.4 + 1.0 = **1.5**` | 1.5 | 1.5 <= 1.5 OK（严格 => < 1.5） | 无丢弃 |
| 6 | knockback_resistance | **0.4** | 0.0 | **DESIGN_BASELINE** | **0.35** | 100 | `0.2 x 100/100 = 0.2` | `0.4 + 0.35 + 0.2 = **0.95**` | 1.0 | 0.95 < 1.0 **严格小于** | **恢复「永不完全免疫」保证** |
| 7 | health_regen | 0.0 | 0.0 | DESIGN_BASELINE | 5.0 | 200 | `2.0 x 200/100 = 4.0` | `0 + 5 + 4 = **9.0 HP/s**` | 20 | 9 < 20 OK | OK |
| 8 | damage_reduction | 0.0 | 0.0 | DESIGN_BASELINE | 60.0 | 50 | `40 x 50/100 = 20` | `0 + 60 + 20 = **80 %**` | 90 | 80 < 90 OK | OK |
| 9 | crit_chance | 0.0 | 0.0 | DESIGN_BASELINE | 50.0 | 100 | `20 x 100/100 = 20` | `0 + 50 + 20 = **70 %**` | 100 | 70 < 100 OK | OK |
| 10 | crit_damage | 0.0 | 0.0 | DESIGN_BASELINE | 无限 | 无限 | 无限 | `0 + inf + inf = inf` | 无 | — | 本维度无天花板 |
| 11 | lifesteal | 0.0 | 0.0 | DESIGN_BASELINE | 20.0 | 100 | `10 x 100/100 = 10` | `0 + 20 + 10 = **30 %**` | 60 | 30 < 60 OK | OK |
| 12 | drop_rate | 0.0 | 0.0 | DESIGN_BASELINE | 150.0 | 500 | `50 x 500/100 = 250` | `0 + 150 + 250 = **400 %**` | 1000 | 400 < 1000 OK | OK |
| 13 | xp_gain | 0.0 | 0.0 | DESIGN_BASELINE | 100.0 | 500 | `50 x 500/100 = 250` | `0 + 100 + 250 = **350 %**` | 1000 | 350 < 1000 OK | OK |

**注 A（维度 1 / 2）**：它们的 sup 是 ∞，但被原版 `RangedAttribute` 钳制在 2048 / 1024。
这是 `1.6` **已明确告知、用户已知**的既有结论，**不是本次 BL-9 遗漏的部分**（BL-9 只涉及装备项未计入）。
两条线走 `LINEAR` 且 `equipment_max` 为 9 / 0，远小于 2048 / 1024，把装备项加进来**不改变结论**。

**结论（T5/T6 当时）**：修正后的 13 行中，**11 行 sup <= HARD_MAX 严格成立**（其中 9 行严格小于）；
2 行（attack_damage / max_health）受原版 RangedAttribute 钳制，且已在 `1.6` 披露。

> 🔴 **T17 之后的有效结论只有一行**：`damage_reduction: 0 + 60 + 40*75/100 = 90 <= 90`（**相等**，所以判定必须带 epsilon，
> 不能写成 `>=`，否则会误报）。上表其余 12 行请按「历史记录」阅读。
> **取消递减的代价已经如实记录**：除伤害减免外，**本 mod 不再对任何维度承诺「点不浪费」**。

### 1.16.3 三个「小余量」维度的重新配平（修复的核心）

**修复原则**：把「装备余量」`H = HARD_MAX − equipment_max` 在 flat 线与 percent 线之间**分配**，两者之和恰好等于 H（击退抗性额外留 0.05 余量）。

| 维度 | HARD_MAX | equipment_max | 余量 H | 旧 flatCap | **旧 sup（含装备）** | **新 flatCap** | **新 B_ref / pctCap** | **新 pct 贡献** | **新 sup** | 结果 |
|---|---|---|---|---|---|---|---|---|---|---|
| armor | 30 | 20 | 10 | 15 | **70**（理论值的 57% 被丢弃） | **6.0** | **4.0 / 100** | 4.0 | **30.0** | 恰好用完余量，零丢弃 |
| armor_toughness | 20 | 12 | 8 | 10 | **44**（55% 被丢弃） | **5.0** | **3.0 / 100** | 3.0 | **20.0** | 恰好用完余量，零丢弃 |
| knockback_resistance | 1.0 | 0.4 | 0.6 | 0.45 | **1.7**（钳到 1.0 = 完全免疫） | **0.35** | **0.20 / 100** | 0.20 | **0.95** | 留 0.05 余量，**永不免疫** |

> **对 lead 方案的独立验算**：lead 给的 `flatCap=6 / B_ref=8 / pctCap=50`（armor）、`5 / 6 / 50`（toughness）、`0.35 / 0.5 / 40`（KB），
> 我逐条代回，**三条全部成立**（sup = 30 / 20 / 0.95）。
> 我在其基础上做了一处**等价但语义更清晰**的改写：把 `pctCap` 统一成 **100**，把差额挪到 `B_ref`（8→4、6→3、0.5→0.2）。
> **percent 贡献值完全相同**（4 / 3 / 0.2），但配置读起来是「percent 线最多把 `B_ref` 全额加上去（最多 +100%）」，
> 比「最多 +50%」更直观，也与其他 7 个自定义维度的 `pctCap` 口径统一。
> **若你更想要 lead 的原始写法，只改 `B_ref` 与 `pctCap` 两个配置即可，sup 不变。**

**新的 `half_points`**：这三条线的 flat 与 percent 都用 `half = 40 / 60`（配合「1 点/级」经济）。

**新配平后的数值预览**（含全套下界合金装备）：

| 维度 | 装备 | 20+20 点 | 60+60 点 | 200+200 点 | 裸装 200+200 点 | sup |
|---|---|---|---|---|---|---|
| armor | 20 | 23.0000 | 25.6000 | **28.0769** | 8.0769 | 30 |
| armor_toughness | 12 | 14.4167 | 16.5000 | **18.4744** | 6.4744 | 20 |
| knockback_resistance | 0.4 | 0.5667 | 0.7100 | **0.8455** | 0.4455 | 0.95 |

⇒ **三点重要含义**：
1. 穿下界合金时，**投满 200+200 点仍未触发任何钳制**（28.08 < 30 / 18.47 < 20 / 0.8455 < 1.0）⇒ **A3「点不浪费」在这三条线上真正成立**。
2. 但这三条线的**成长空间本来就不大**（装备已占掉 67% / 60% / 40% 的余量）—— 这是**原版属性区间的硬约束**，不是本 mod 的选择。
   因此**防御成长的主战场是 `damage_reduction`（80% 余量）与 `armor_toughness` 对高伤害区间的杠杆作用**（`1.15.2`）。
3. 裸装玩家的上限显著低于旧方案（armor 8.08 vs 旧 15.23）—— 这是**修正的代价**，也是正确的：护甲本来就该主要来自护甲装备。

### 1.16.4 击退抗性：采用方案 (a)，恢复「永不完全免疫」保证（lead 要求 3）

**裁决：采用 (a) —— 压低 cap 使 `sup < 1.0` 严格成立**，不走「承认可以完全免疫」那条路。

**理由**：
1. `1` 的公理 **A2** 明确要求「效果函数严格小于天花板」。若击退抗性能到 1.0，A2 在本维度失效，整个框架出现例外，后续维护会被这个例外拖垮。
2. 「完全免疫击退」会让玩家无法被击退进陷阱 / 岩浆，属于**机制破坏**，不只是数值问题。
3. 代价很小：只需把 flatCap 从 0.45 压到 0.35、percent 贡献压到 0.20。

**修正后的保证（写死，必须与实现一致）**：

```
穿全套下界合金（equipment_max = 0.4）+ 任意加点：
    final_knockback_resistance < 0.95
=>  被击退时至少保留 5% 的原始击退量（sup 口径）
   实测 200+200 点：final = 0.8455 => 保留 15.45% 击退
裸装 + 200+200 点：final = 0.4455 => 保留 55.45% 击退
```

> **`LivingKnockBackEvent` 的兜底（可选加固）**：若管理员通过 `/rpg debug override` 或第三方 mod 把属性推到 1.0，
> 可在 `LivingKnockBackEvent` 里加一道 `knockback_resistance.hard_floor_ratio = 0.05` 的运行时守卫
> （把 `setStrength(float)` 的结果下限钳到原值的 5%）。**默认关闭**，因为它会与外 mod 的击退逻辑打架；
> 但 `/rpg stats` 的 `knockback_resistance.effective` 一旦达到 1.0，服务端会 warn 一次。

### 1.16.5 「被钳制」的可观测性（lead 要求 4 / verifier 硬需求）

**问题**：`RangedAttribute.sanitizeValue` 是 `Mth.clamp`，**静默丢弃、无任何日志**（`1.6` 已确认）。
若不做可观测，玩家与 verifier 都无法区分「加了没用」与「加了但被钳掉」。

**三处输出（全部必须实现）**：

| 位置 | 输出 | 用途 |
|---|---|---|
| `/rpg stats` 与 `/rpg debug stats` | 每个维度输出**三行**：`<dim>.computed=<我们算出的含装备理论值>`、`<dim>.effective=<AttributeInstance.getValue() 的实际值>`、`<dim>.clamped=<true 或 false>` | verifier 判定「是否发生静默丢弃」的唯一依据 |
| GUI | 当 `clamped == true` 时该维度行加红色感叹号 + tooltip：`「已达原版上限（30.0），继续加点不会再有收益」` | 玩家可观测 |
| 服务端日志 | 每个玩家每个维度**首次**发生 `clamped` 时 `LOGGER.info` 一次（带 UUID、维度、computed、effective），之后静默 | 线上可诊断，不刷屏 |

**`clamped` 的判定**：

```java
double computed  = RpgMath.assembleWithEquipment(stat, points, cfg, equipmentValue);  // 含装备的理论值
double effective = player.getAttributeValue(stat.attributeHolder());                  // 经 sanitizeValue 后的实际值
boolean clamped  = Math.abs(effective - computed) > 1.0E-6;                           // 容差 1e-6
```

> **注意 `effective` 的含义**：它包含**所有来源**（装备 + 我们 + 其他 mod）。因此 `computed` 的比较基准必须是
> `RpgMath.assembleWithEquipment()` 输出的「含装备的完整理论值」，而不是「我们贡献的那一部分」。
> `RpgMath` 必须提供这个重载，`equipmentValue` 由 `RpgMath.equipmentMax(stat)` 提供（原版口径，见 1.16.1）。
> 新增配置 `stats.report_effective`（默认 `true`）控制是否输出 `effective` 行。

**与 verifier 的 `M5` 「装备状态双跑」矩阵的自洽性**：裸装 / 全套下界合金两种状态下，
`computed` 会因为装备不同而不同，但 `sup` 相同 ⇒ **两种状态下 `clamped` 都必须为 `false`**。这正是 cap 配平正确的判定标准。

### 1.16.6 第三方装备 / mod 的溢出边界（诚实说明）

`equipment_max` 只覆盖**原版**。若有第三方 mod 提供 +15 护甲，则 `computed = 20 + 15 + 6 + 4 = 45 > 30` ⇒ **会被钳制**。

| 情形 | 处置 |
|---|---|
| 原版装备 + 本 mod 加点 | **保证不钳制**（1.16.2 的证明） |
| 第三方 mod 提供属性 | 可能钳制；由 1.16.5 的 `computed / effective / clamped` 三者**显式暴露**，并在 GUI 与日志提示 |
| 管理员手动调大 `cap` | 同上 |
| 是否需要为此在 mod 内加硬上限 | **不需要**（原版 `sanitizeValue` 已经是硬上限）。我们只保证「可观测」，不重复加钳 |

### 1.16.7 NaN / Infinity 防线（回应 verifier 4(b)，并更正其前提）

**先更正一处前提**：verifier 说「7 个自定义维度**不是 attribute**，没有 `sanitizeValue` 这层保护」——**这个前提不成立**。
本设计里 7 个自定义维度**都注册在 `Registries.ATTRIBUTE` 上**（`3.2`：`health_regen` 与 `crit_damage` 用 `RangedAttribute`，
其余 5 个用 `PercentageAttribute`，而 `PercentageAttribute extends RangedAttribute`）。
⇒ **它们同样有 `sanitizeValue`**，NaN 会被映射为 `minValue = 0.0` ⇒ 原版属性的「NaN 归一」保护对 7 个自定义维度**同样有效**
（证据：API-NOTES `1.1 `RangedAttribute.sanitizeValue`、`1.4 `PercentageAttribute`）。

**但 verifier 指出的风险仍然真实，只是位置不同**：真正没有保护的是**在属性体系之外计算的普通 double** ——
`V_pct`、`B_ref * V_pct / 100`、经验倍率 `mult`、掉落倍率 `m`、暴击倍率。这些一旦 NaN 会直接传播（NaN 伤害、NaN 回血）。

**四道 NaN 防线（必须实现）**：

| # | 位置 | 防线 |
|---|---|---|
| 1 | `LineCfg` 构造（配置读取） | 若 `per_point / cap / half_points / gamma / basis_value` 任一不满足 `Double.isFinite(v)` ⇒ 用默认值替换并 `LOGGER.error`（TOML 允许 `nan` 与 `inf`，`defineInRange` 的区间校验对 NaN 不可靠） |
| 2 | `RpgMath.lineValue()` | 入口 `if (points <= 0 或 !cfg.enabled()) return 0.0;`；出口 `return Double.isFinite(v) ? v : 0.0;` |
| 3 | `RpgMath.assemble()` 与 `assembleWithEquipment()` | 返回值统一走 `sanitize(double v, double fallback)`：`!Double.isFinite(v) ? fallback : Math.max(0, v)` |
| 4 | `XpService.grant()` 与 `CombatEvents` 与掉落逻辑 | `mult`、`m`、`dr`、`critMult` 使用前逐个钳：`Math.max(0, Double.isFinite(x) ? x : fallback)`；经验 / 回血结果再 `Math.max(0, ...)` |

**验收点（verifier 可直接测）**：把 `rpgleveling-common.toml` 里某个 `half_points` 手改成 `nan` 或 `0`，
启动后 mod **必须**记一条 error 并用默认值继续运行，**不得**出现 NaN 血条 / 负伤害 / NaN 经验。

### 1.16.8 玩家 base 不等于 Attribute 默认值（回应 verifier 4(a)）

**verifier 完全正确，这是极易踩的坑**：

| 属性 | `Attributes` 里的默认值 | `Player.createAttributes()` 的实际 base | 差距 |
|---|---|---|---|
| `ATTACK_DAMAGE` | **2.0** | **1.0** | 2 倍 |
| `MOVEMENT_SPEED` | **0.7** | **0.1** | **7 倍** |
| `MAX_HEALTH` | 20.0 | 20.0 | 相同（巧合） |

**硬规则（写进 code review 清单）**：

| # | 规则 |
|---|---|
| R-BASE-1 | `RpgMath` **禁止**调用 `Attribute.getDefaultValue()`。百分比基数一律取 `Stat.playerBase`（编译期常量表，来源 `Player.createAttributes()` L228-231）。 |
| R-BASE-2 | `RpgMath.assembleWithEquipment()` 的 `base` 参数由 `Stat.playerBase` 提供，**不是**从 `Attribute` 读。 |
| R-BASE-3 | 开发期（`!FMLEnvironment.production`）在玩家首次登录时断言：`player.getAttribute(ATTACK_DAMAGE).getBaseValue() == Stat.ATTACK_DAMAGE.playerBase`，不符则 `LOGGER.error`（能同时抓到「另一个 mod 改了 base」） |
| R-BASE-4 | verifier 验收点：`/attribute @s minecraft:generic.attack_damage base get` 在玩家身上**必须**返回 **1.0**；`movement_speed base get` **必须**返回 **0.1**。返回 2.0 / 0.7 即为 FAIL。 |

### 1.16.9 负伤害陷阱的守卫测试（回应 verifier 4(c)）

verifier 发现的 `CombatRules` L18 陷阱（`armorValue > 100` 时 `Mth.clamp(v, armorValue*0.2, 20)` 的 min > max，
使 `f1 > 25`、`f3 > 1`、`damage*(1-f3) < 0` **变成打人回血**，且无武器路径不受 `[0,1]` 钳制）**本 mod 不会触及**：
`Attributes.ARMOR` 的 `RangedAttribute` max = 30 ⇒ `armorValue <= 30` ⇒ `armorValue*0.2 <= 6 <= 20`，min <= max 恒成立。

**但仍加两条守卫**：
1. `1.15.1` 引用的外层 `clamp(..., 0, 25)` 保留（虽然 A <= 30 时永不生效，但它是这一区间的唯一防线）。
2. **守卫测试（verifier 建议，采纳）**：`/attribute @s minecraft:generic.armor base set 200` + `/damage` ⇒ 断言伤害**不为负**。
   这是测**原版行为**的测试，用来证明「我们从未进入该区间」，同时把它变成一条可复现的回归用例。

---

## 1.17 【T17】取消成长上限：曲线策略、自适应上限与配置迁移

> 本节是 T17 的权威裁决记录。它**覆盖** `1.4`（逐维度表已重写）、`1.5` / `1.16`（安全性证明只剩伤害减免一行）、
> `1.7`（移速自设上限删除）里与之冲突的所有描述。

### 1.17.1 用户需求（原话）

1. 用户装了「属性修复」(AttributeFix)，希望**取消护甲等维度的成长上限**。
2. **减伤最大值 90%**。
3. **其余成长限制全部取消**（不再有收益递减）。
4. 实现方式：**运行时自适应** —— 上限读属性真实的 max；装了 AttributeFix 就自动放开，没装就沿用原版上限。
5. 暴击率 / 击退抗性超过 100% 无意义 ⇒ **点数照投、效果封顶 100% / 1.0，界面标出被钳制**。
6. 改成线性时**保持前期手感**：每点收益 = 原曲线的初始斜率 `cap / half`。
7. 减伤采用**渐近上限**：无限接近 90% 但永不达到。

### 1.17.2 曲线策略：24 条 LINEAR + 2 条 SATURATING

- `StatLine.VALUES` 里现在**只有 `DAMAGE_REDUCTION_FLAT` / `DAMAGE_REDUCTION_PCT` 的 `curve` 是 `SATURATING`**，其余 24 条一律 `LINEAR`。
- 每条被转换的线满足 `per_point = 原 cap / 原 half_points`（@1.4` 表里逐个给出）。
  这条规则由两个**互相独立**的测试守着：`RpgMathSelfCheck`（纯 JVM，对 18 组 (cap, half) 重算斜率）
  与 `CurvePolicySuite.everyLinearLineKeepsTheOldInitialSlope`（GameTest，逐条比对 `StatLine` 的发布默认值）。
- `StatLine.verifySafetyProof()` 在类加载时**强制**：「除 `damage_reduction` 外任何一条线是 `SATURATING`」⇒ 直接抛
  `IllegalStateException`，模组拒绝加载。这是一道 fail-fast 回归护栏（变异测试 M2 验证过）。
- 配置项 `general.strict_convergence = false` 的旧语义（把所有线强制变 LINEAR）保留不变，但**现在默认值本来就是 LINEAR**，
  所以它已经不再改变大多数线的行为。

### 1.17.3 诚实声明：取消递减之后，除减伤外不再有安全性证明

**这是用户明确选择的结果，不是实现缺陷，也不是文档没跟上。**

- 旧设计用 `sup = equipment_max + flatCap + B_ref*pctCap/100 <= hardMax` 证明「没有任何一点会被丢弃」。
  该证明**只对 SATURATING 成立**，因为 `SAT(x)` 恒小于 `cap`。
- 现在 24 条线是 LINEAR，`x -> 无穷` 时 `sup -> 无穷`，**证明在数学上不可能成立**，我们也不再声称它成立。
- 后果：这些维度会一直涨到**属性自身的 `maxValue`**（LIVE）或本 mod 的功能上限（FIXED），越过之后继续加点**没有收益**。
- 补偿手段是**可观测性**（`1.17.6`），不是继续假装安全。
- **唯一保留的证明**：`damage_reduction: 0 + 60 + 40*75/100 = 90 = hardMax`。注意这是**取等号**，
  判定必须写 `sup <= hardMax + 1e-9`，写成 `>=` 会误报（`StatLine.verifySafetyProof` 与 `RpgMathSelfCheck` 都按此实现）。

### 1.17.4 自适应上限：`Stat.LimitMode`

`Stat.hardMax` 从**字段**改成**方法** `Stat.hardMax()`：

```java
public enum LimitMode { LIVE, FIXED }

public double hardMax() {
    if (limitMode == LimitMode.LIVE) {
        Attribute attribute = carrier.holder().value();
        if (attribute instanceof RangedAttribute ranged) return ranged.getMaxValue();
    }
    return fixedMax;
}
```

**为什么必须是方法而不是构造期常量**：(a) 属性注册晚于枚举常量构造，
在构造函数里读 `getMaxValue()` 会踩初始化顺序；(b) 唯一有意义的语义就是「**调用时**的属性真实范围」，
AttributeFix 抬高 `RangedAttribute.maxValue` 之后，本 mod 不写一行代码就跟着放开。

| 维度 | 模式 | 上限 | 理由 |
|---|---|---|---|
| attack_damage / max_health / armor / armor_toughness / movement_speed | **LIVE** | 属性 max（原版 2048 / 1024 / 30 / 20 / 1024） | 用户就是要放开这些 |
| health_regen / lifesteal / drop_rate / xp_gain | **LIVE** | 本 mod 注册的属性 max = 1.0E6 | 自定义属性，范围本来就很宽 |
| crit_damage | **LIVE** | 1.0E9 | 只放大伤害数字，不改变机制 |
| knockback_resistance | **FIXED = 1.0** | 1.0 | 1.0 已经是完全免疫，超过无意义 |
| crit_chance | **FIXED = 100** | 100 | 概率不可能超过 100% |
| damage_reduction | **FIXED = 90** | 90 | 渐近 90%，永不达到 |

> `StatLine.verifySafetyProof()` 还会断言这三个 `FIXED` 维度**必须**保持 `FIXED` 且值分别为 90 / 100 / 1.0。
> 把它们误改成 `LIVE` 会让暴击率突破 100% 或击退抗性超过免疫，语义被破坏。

### 1.17.5 减伤为什么取 cap = 75

百分线的基数是 `DESIGN_BASELINE`，贡献 = `B_ref × V_pct / 100`，其中 `B_ref(damage_reduction) = 40.0`（代码取证：`StatLine.defBasisValue()`）。
旧 `cap = 50` 时上限是 `60 + 40*50/100 = 80%@；用户要 90%，所以：

`@
flat 线渐近 60  +  百分线贡献 40 × 75/100 = 30   ⇒   合计渐近 90   ✓
`@

两条线都是 `SATURATING`，所以**严格小于 90**，玩家至少承受 10% 伤害 —— 「永不免疫」的数学保证仍然成立。
10 000 点时两条线合计 `89.2857`（`damageReductionStaysStrictlyBelowNinety` 用例断言）。

### 1.17.6 功能上限的可观测性（需求 5）

原实现只用 `DerivedStats.isClamped()` 判断「是否被钳制」，它比较的是 `rawAttributeValue` 与 `AttributeInstance.getValue()`，
**只能发现原版 `sanitizeValue` 造成的截断**。而本 mod 的 7 个自定义属性注册范围是 1.0E6，
所以暴击率堆到 166% 时属性里就是 166，`isClamped` 会是 `false`，界面不会出现「!」。

T17 补上了第二个判据：

| 方法 | 含义 |
|---|---|
| `DerivedStats.isClamped` | 原版/属性范围截断了值（属性里存的和算出来的不一样） |
| `DerivedStats.isCappedByModCeiling` | **本 mod 的 FIXED 功能上限**在起作用（属性值 > `hardMax()`） |
| `DerivedStats.isEffectivelyCapped` | 上面两者取或 —— **GUI 行首「!」的唯一判据** |

界面（`RpgScreen`）与 `/rpg stats` 的 `<dim>.clamped` 语义因此在 T17 之后仍然诚实：
「继续加点不会再涨」这件事，无论原因是原版天花板还是本 mod 的功能上限，都会被标出来。

### 1.17.7 移动速度：两个 1.5 都删掉了

移速上原本有**两处**本 mod 自设的 1.5：

1. `Stat.MOVEMENT_SPEED` 的 `hardMax = 1.5` ⇒ T17 改成 `LimitMode.LIVE`（属性 max = 原版 1024）。
2. 配置 `movement_speed.hard_cap_attr = 1.5` + `AttributeApplier.clampRuntimeHardCaps()` 的运行时硬钳
   ⇒ T17 把默认值改成 **`0 = 关闭`**，`AttributeApplier` 在关闭时**主动移除**旧的 `runtime_speed_cap` 修饰符。
   想恢复本地兜底的管理员把它设成任意正数即可。

> ⚠ **必须告知玩家**：删掉本 mod 的限制后，**原版**的 `moved too quickly!` 检查成为事实上的上限。
> 它不读 `MOVEMENT_SPEED`、用固定阈值（约 10 格/tick ≈ 46 倍原版），触发后**只把你回拉并记一条 warn，不踢人**。
> **这是原版机制，不是本 mod 的限制。** 见 `1.7`。

### 1.17.8 配置迁移（铁律：改默认值 ≠ 用户那里改了）

**NeoForge 不会用新默认值覆盖已存在的配置文件**，它只往旧文件里追加缺失的键。
所以 T17 改的是 `StatLine` 的默认值，**对已经生成过 `config/rpgleveling-common.toml` 的实例完全无效** ——
26 条线的 `curve / cap / half_points / per_point` 会原样保留旧值，直接用起来会「改了跟没改一样」。

**处置**：用户实例的 `config/rpgleveling-common.toml` 已备份为
`rpgleveling-common.toml.bak-2026-10-08`（SHA-256 `DF356A35...D5F0`，与原件逐字节一致）后**删除**，
下次启动会用新默认值重新生成。开发者工作区 `run/config/rpgleveling-common.toml` 同样处理。

> ⚠ **附带现象（无害）**：LINEAR 线的 `half_points` 默认值是 `0.0`，而配置区间下限是 `1.0E-6`，
> 所以**首次**生成配置时 NeoForge 会为每条 LINEAR 线打印一条
> `Incorrect key lines.<x>.half_points was corrected from 0.0 to its default, 1.0E-6` 的 WARN
> 并把它写进文件。第二次启动不再出现。这是 WARN 不是 ERROR；`half_points` 对 LINEAR 线没有任何作用。

### 1.17.9 T17 验证摘要

| 验证 | 结果 |
|---|---|
| `gradlew clean build` | BUILD SUCCESSFUL |
| `gradlew verifyMath` | 471/471 passed |
| `gradlew runGameTestServer` | **49/49 passed**（含新增 `CurvePolicySuite` 6 例） |
| `gradlew runServer` 冷启动 | `Done (0.658s)`，**0 ERROR** |
| 重新生成的配置 | 26 段逐段与 `1.4` 表比对，**0 mismatch** |
| 变异 M1（`ARMOR_FLAT.per_point` 0.15→0.05） | **3 个用例红**（含新增 LINEAR 用例） |
| 变异 M2（`ARMOR_FLAT` 改回 `SATURATING`） | 类加载期 `IllegalStateException` ⇒ 模组拒绝加载（fail-fast 护栏） |
| 变异 M3（`ARMOR_FLAT` 改 `SUBLINEAR_POW`，绕过护栏） | **3 个用例红** |

> ⚠ **一个必须记住的验证陷阱**：变异 M2 让模组在加载期崩溃、**一个用例都没跑**，
> 但 Gradle 仍然报 `BUILD SUCCESSFUL`（runGameTestServer 退出码 0）。
> 因此「runGameTestServer 通过」的判据必须是日志里的 `All 49 required tests passed`，
> **不能**只看退出码或 `BUILD SUCCESSFUL`。

## 2. 26 条加点线的统一表驱动结构

> 目标：**13 维度 × {flat, percent} 不写 26 段重复代码**。做法是把「线的元数据」放进枚举，配置、加成计算、GUI、指令、网络全部**循环遍历**这个枚举。

### 2.1 枚举定义（唯一的真相源）

`com.rpgleveling.api.StatLine`

```java
package com.rpgleveling.api;

/** 13 个加点维度。承载决定了效果如何落到游戏里。 */
public enum Stat {
    ATTACK_DAMAGE     ("attack_damage",        Carrier.VANILLA_ATTACK_DAMAGE,         1.0),
    MAX_HEALTH        ("max_health",           Carrier.VANILLA_MAX_HEALTH,           20.0),
    ARMOR             ("armor",                Carrier.VANILLA_ARMOR,                 0.0),
    ARMOR_TOUGHNESS   ("armor_toughness",      Carrier.VANILLA_ARMOR_TOUGHNESS,       0.0),
    MOVEMENT_SPEED    ("movement_speed",       Carrier.VANILLA_MOVEMENT_SPEED,        0.1),
    KNOCKBACK_RESIST  ("knockback_resistance", Carrier.VANILLA_KNOCKBACK_RESISTANCE,  0.0),
    HEALTH_REGEN      ("health_regen",         Carrier.CUSTOM_HEALTH_REGEN,           0.0),
    DAMAGE_REDUCTION  ("damage_reduction",     Carrier.CUSTOM_DAMAGE_REDUCTION,       0.0),
    CRIT_CHANCE       ("crit_chance",          Carrier.CUSTOM_CRIT_CHANCE,            0.0),
    CRIT_DAMAGE       ("crit_damage",          Carrier.CUSTOM_CRIT_DAMAGE,            0.0),
    LIFESTEAL         ("lifesteal",            Carrier.CUSTOM_LIFESTEAL,              0.0),
    DROP_RATE         ("drop_rate",            Carrier.CUSTOM_DROP_RATE,              0.0),
    XP_GAIN           ("xp_gain",              Carrier.CUSTOM_XP_GAIN,                0.0);

    public final String id;
    public final Carrier carrier;
    public final double playerBase;    // 玩家实体上的真实 base（证据 Player.createAttributes L228-231）
    public static final Stat[] VALUES = values();
    public static final int COUNT = VALUES.length;             // 13

    /**
     * 该维度 percent 线的默认 basis 模式（T5 + T6 裁决，见 1.14.4 与 1.16.3）。
     * TOTAL 只保留给「装备不占余量或 base 已够大」的 3 个维度；
     * 其余 10 个维度一律 DESIGN_BASELINE，以避免装备吃掉余量后 percent 静默失效。
     */
    public Basis defaultBasis() {
        return switch (this) {
            case ATTACK_DAMAGE, MAX_HEALTH, MOVEMENT_SPEED -> Basis.TOTAL;   // equipment_max 相对 HARD_MAX 可忽略
            default -> Basis.DESIGN_BASELINE;   // armor / armor_toughness / knockback_resistance / 7 个自定义
        };
    }

    /** 该维度是否存在「玩家之外」的 ADD_VALUE 来源。仅用于文案与诊断，不再决定 basis。 */
    public boolean hasExternalBasis() {
        return this == ARMOR || this == ARMOR_TOUGHNESS || this == KNOCKBACK_RESIST
            || this == ATTACK_DAMAGE || this == MAX_HEALTH || this == MOVEMENT_SPEED;
    }
}

/** 每条线的单元数据：曲线 + 默认参数。全部来自第 1.4 节的表。 */
public enum StatLine {
    // 参数：stat, mode, curve, perPoint, cap, halfPoints, basis, basisValue
    // basis/basisValue 仅 percent 线有意义；下方 26 行的 basis 由 StatLine.defBasisOf(stat) 统一给出（见 1.14.4）
    //                        stat,               mode,     curve,          perPoint, cap,   halfPoints
    ATTACK_DAMAGE_FLAT     (Stat.ATTACK_DAMAGE,    Mode.FLAT, Curve.LINEAR,        0.5,    0.0,     0.0),
    ATTACK_DAMAGE_PCT      (Stat.ATTACK_DAMAGE,    Mode.PCT,  Curve.LINEAR,        3.0,    0.0,     0.0),
    MAX_HEALTH_FLAT        (Stat.MAX_HEALTH,       Mode.FLAT, Curve.LINEAR,        1.0,    0.0,     0.0),
    MAX_HEALTH_PCT         (Stat.MAX_HEALTH,       Mode.PCT,  Curve.LINEAR,        2.0,    0.0,     0.0),
    ARMOR_FLAT             (Stat.ARMOR,            Mode.FLAT, Curve.SATURATING,    0.0,    6.0,    40.0),   // T6：余量 10 = 6 + 4
    ARMOR_PCT              (Stat.ARMOR,            Mode.PCT,  Curve.SATURATING,    0.0,  100.0,    60.0),   // T6：B_ref 4.0 -> 最大 +4 护甲
    ARMOR_TOUGHNESS_FLAT   (Stat.ARMOR_TOUGHNESS,  Mode.FLAT, Curve.SATURATING,    0.0,    5.0,    40.0),   // T6：余量 8 = 5 + 3
    ARMOR_TOUGHNESS_PCT    (Stat.ARMOR_TOUGHNESS,  Mode.PCT,  Curve.SATURATING,    0.0,  100.0,    60.0),   // T6：B_ref 3.0 -> 最大 +3 韧性
    MOVEMENT_SPEED_FLAT    (Stat.MOVEMENT_SPEED,   Mode.FLAT, Curve.SATURATING,    0.0,    0.4,    60.0),
    MOVEMENT_SPEED_PCT     (Stat.MOVEMENT_SPEED,   Mode.PCT,  Curve.SATURATING,    0.0,  200.0,    80.0),
    KNOCKBACK_RESIST_FLAT  (Stat.KNOCKBACK_RESIST, Mode.FLAT, Curve.SATURATING,    0.0,   0.35,    40.0),   // T6：余量 0.6，只用 0.35+0.20=0.55
    KNOCKBACK_RESIST_PCT   (Stat.KNOCKBACK_RESIST, Mode.PCT,  Curve.SATURATING,    0.0,  100.0,    60.0),   // T6：B_ref 0.20 -> 最大 +0.20
    HEALTH_REGEN_FLAT      (Stat.HEALTH_REGEN,     Mode.FLAT, Curve.SATURATING,    0.0,    5.0,    40.0),
    HEALTH_REGEN_PCT       (Stat.HEALTH_REGEN,     Mode.PCT,  Curve.SATURATING,    0.0,  200.0,    60.0),
    DAMAGE_REDUCTION_FLAT  (Stat.DAMAGE_REDUCTION, Mode.FLAT, Curve.SATURATING,    0.0,   60.0,    80.0),
    DAMAGE_REDUCTION_PCT   (Stat.DAMAGE_REDUCTION, Mode.PCT,  Curve.SATURATING,    0.0,   50.0,    80.0),
    CRIT_CHANCE_FLAT       (Stat.CRIT_CHANCE,      Mode.FLAT, Curve.SATURATING,    0.0,   50.0,    60.0),
    CRIT_CHANCE_PCT        (Stat.CRIT_CHANCE,      Mode.PCT,  Curve.SATURATING,    0.0,  100.0,    80.0),
    CRIT_DAMAGE_FLAT       (Stat.CRIT_DAMAGE,      Mode.FLAT, Curve.LINEAR,        1.0,    0.0,     0.0),
    CRIT_DAMAGE_PCT        (Stat.CRIT_DAMAGE,      Mode.PCT,  Curve.LINEAR,        2.0,    0.0,     0.0),
    LIFESTEAL_FLAT         (Stat.LIFESTEAL,        Mode.FLAT, Curve.SATURATING,    0.0,   20.0,    60.0),
    LIFESTEAL_PCT          (Stat.LIFESTEAL,        Mode.PCT,  Curve.SATURATING,    0.0,  100.0,    80.0),
    DROP_RATE_FLAT         (Stat.DROP_RATE,        Mode.FLAT, Curve.SATURATING,    0.0,  150.0,    80.0),
    DROP_RATE_PCT          (Stat.DROP_RATE,        Mode.PCT,  Curve.SATURATING,    0.0,  500.0,   100.0),
    XP_GAIN_FLAT           (Stat.XP_GAIN,          Mode.FLAT, Curve.SATURATING,    0.0,  100.0,    60.0),
    XP_GAIN_PCT            (Stat.XP_GAIN,          Mode.PCT,  Curve.SATURATING,    0.0,  500.0,   100.0);
    // gamma 仅对 Curve.SUBLINEAR_POW 生效（默认 0.5 = sqrt）；加入第 4 个构造参数即可，不影响上文 26 行

    public final Stat stat;
    public final Mode mode;
    public final Curve curve;
    public final double defPerPoint, defCap, defHalfPoints;
    // basis / basisValue 不由构造参数给出，而是从 stat 派生（见下方 defBasis() / defBasisValue()），
    // 这样 26 行枚举保持 6 参数不变，避免重复配置与不一致。

    public static final StatLine[] VALUES = values();     // 26
    public static final int COUNT = VALUES.length;        // 26
    public int index() { return ordinal(); }
    public String id() { return stat.id + (mode == Mode.FLAT ? ".flat" : ".percent"); }
    public String langKey() { return "rpgleveling.line." + id(); }
    /** flat / TOTAL 模式下 percent 线使用的 modifier id —— 幂等去重的关键 */
    public ResourceLocation modifierId() {
        return ResourceLocation.fromNamespaceAndPath("rpgleveling", id().replace('.', '_'));
    }

    /** DESIGN_BASELINE 模式下 percent 线使用的**另一个**独立 modifier id（见 1.14.3），确保两种模式互不残留。 */
    public ResourceLocation pctBasisModifierId() {
        return ResourceLocation.fromNamespaceAndPath("rpgleveling", id().replace('.', '_') + "_pctbasis");
    }
    public static StatLine byIndex(int i) { return VALUES[Mth.clamp(i, 0, COUNT - 1)]; }

    /** percent 线的作用基数模式（T5 + T6 裁决，见 1.14.4 与 1.16.3）。 */
    public Basis defBasis() {
        if (mode == Mode.FLAT) return Basis.TOTAL;      // flat 线一律 ADD_VALUE，不使用 basis
        return stat.defaultBasis();
    }

    /** DESIGN_BASELINE 模式下的设计参考基数 B_ref（见 1.14.4 表）。TOTAL 模式恒返回 0。 */
    public double defBasisValue() {
        return switch (stat) {
            // --- T6 新增：三个「小余量」维度（见 1.16.3 的余量分配）---
            case ARMOR               ->  4.0;   // 护甲点；余量 10 = flatCap 6 + 4
            case ARMOR_TOUGHNESS     ->  3.0;   // 韧性点；余量  8 = flatCap 5 + 3
            case KNOCKBACK_RESIST    ->  0.20;  // 抗性比例；余量 0.6，只用 0.55 留安全边距
            // --- T5 的 7 个自定义维度 ---
            case HEALTH_REGEN        ->  2.0;   // HP/s
            case DAMAGE_REDUCTION    -> 40.0;   // 百分点
            case CRIT_CHANCE         -> 20.0;   // 百分点
            case CRIT_DAMAGE         -> 50.0;   // 百分点
            case LIFESTEAL           -> 10.0;   // 百分点
            case DROP_RATE           -> 50.0;   // 百分点
            case XP_GAIN             -> 50.0;   // 百分点
            // --- 3 个 TOTAL 维度不使用该值 ---
            default                  ->  0.0;
        };
    }
}

public enum Mode { FLAT, PERCENT }        // FLAT -> ADD_VALUE ; PERCENT -> ADD_MULTIPLIED_BASE

/** 三条曲线。默认 SATURATING；SUBLINEAR_POW 是 lead 建议的 sqrt/log 等价物（无上界，安全性自负）。 */
public enum Curve { LINEAR, SATURATING, SUBLINEAR_POW }

/** percent 线的作用基数模式（T5 裁决，见 1.14.3）。 */
public enum Basis {
    /** 原版语义：final = (base + SUM(所有 ADD_VALUE，含装备)) * (1 + V_pct/100) */
    TOTAL,
    /** 设计参考基数：final = base + V_flat + B_ref * V_pct / 100（percent 贡献用独立 ADD_VALUE 落地） */
    DESIGN_BASELINE
}
```

### 2.2 曲线求值（纯函数，可单测）

`com.rpgleveling.data.RpgMath`

```java
/** 单条线的加成值（已按曲线收敛）。points < 0 视为 0。 */
public static double lineValue(StatLine line, int points, LineCfg cfg) {
    if (points <= 0 || !cfg.enabled()) return 0.0;
    int x = (cfg.maxPoints() >= 0) ? Math.min(points, cfg.maxPoints()) : points;
    return switch (cfg.curve()) {
        case LINEAR     -> cfg.perPoint() * x;
        case SATURATING -> cfg.cap() * x / (x + cfg.halfPoints());
    };
}

/** 组装某个维度的最终数值（第 1.3 节的唯一公式）。 */
public static double statValue(Stat stat, int[] points, LineCfg[] cfgs) {
    double flat = lineValue(stat.line(Mode.FLAT),    points[stat.line(Mode.FLAT).index()],    cfgs[stat.line(Mode.FLAT).index()]);
    double pct  = lineValue(stat.line(Mode.PERCENT), points[stat.line(Mode.PERCENT).index()], cfgs[stat.line(Mode.PERCENT).index()]);
    double v    = (stat.playerBase + flat) * (1.0 + pct / 100.0);
    return Math.max(0.0, Math.min(v, stat.hardMax));   // hardMax 仅数值兜底，上限由 SAT cap 保证
}
```

`LineCfg` 是一个 record：`record LineCfg(boolean enabled, Curve curve, double perPoint, double cap, double halfPoints, int maxPoints)`。

### 2.3 配置表驱动生成（26 × 6 = 156 项一次循环搞定）

`com.rpgleveling.RpgConfig`（节选，完整见第 12 节）

```java
ModConfigSpec.Builder b = new ModConfigSpec.Builder();
b.comment("13 维度 x {flat, percent} = 26 条加点线").push("lines");
for (StatLine line : StatLine.values()) {
    b.push(line.id());                                  // 例: attack_damage.flat
    BooleanValue     enabled  = b.comment("是否启用该加点线").define("enabled", true);
    EnumValue<Curve> curve    = b.comment("曲线：LINEAR=线性(无上限)，SATURATING=饱和(收益递减，推荐)")
                                 .defineEnum("curve", line.curve);
    DoubleValue      perPoint = b.comment("线性曲线的每点增量").defineInRange("per_point",     line.defPerPoint,   -1.0E9, 1.0E9);
    DoubleValue      cap      = b.comment("饱和曲线的渐近上限(永远取不到)").defineInRange("cap", line.defCap,     -1.0E9, 1.0E9);
    DoubleValue      half     = b.comment("饱和曲线的半饱和点数").defineInRange("half_points", line.defHalfPoints,  1.0E-6, 1.0E9);
    IntValue         maxPts   = b.comment("该线点数上限，-1=无上限").defineInRange("max_points", -1, -1, 100_000_000);
    LINE_CFG.put(line, new LineCfg(enabled, curve, perPoint, cap, half, maxPts));
    b.pop();
}
b.pop();
COMMON_SPEC = b.build();
```

`LINE_CFG` = `EnumMap<StatLine, LineCfg>`，其中 `LineCfg` 持有 `ConfigValue`。
**新增一个维度只需要加 2 行枚举 + 1 个 `Carrier` 分支 + 3 行 lang，不需要改任何循环体。**

### 2.4 属性修饰符生成（26 条线一次循环）

`com.rpgleveling.stat.AttributeApplier`

```java
public static void apply(ServerPlayer player, PlayerRpgData data) {
    LineCfg[] cfgs = RpgConfig.snapshot();
    for (StatLine line : StatLine.VALUES) {
        LineCfg cfg = cfgs[line.index()];
        double  amount = RpgMath.lineValue(line, data.points(line), cfg);
        AttributeInstance inst = player.getAttribute(line.stat.attributeHolder());
        if (inst == null) continue;                                  // 防御式：属性未注册
        if (amount <= 0.0) { inst.removeModifier(line.modifierId()); continue; }
        if (line.mode == Mode.FLAT) {
            // flat 线：一律 ADD_VALUE
            inst.addOrUpdateTransientModifier(new AttributeModifier(line.modifierId(), amount, Operation.ADD_VALUE));
        } else if (cfg.basis() == Basis.TOTAL) {
            // percent 线 · TOTAL：原版语义，最终 = (base + SUM ADD_VALUE) * (1 + V_pct/100)
            inst.addOrUpdateTransientModifier(
                new AttributeModifier(line.modifierId(), amount / 100.0, Operation.ADD_MULTIPLIED_BASE));
            inst.removeModifier(line.pctBasisModifierId());          // 确保残留的另一种模式的 modifier 被清掉
        } else {
            // percent 线 · DESIGN_BASELINE：最终贡献 = B_ref * V_pct / 100，用独立的 ADD_VALUE 落地（见 1.14.3）
            double contribution = cfg.basisValue() * amount / 100.0;
            if (contribution <= 0.0) {
                inst.removeModifier(line.pctBasisModifierId());
            } else {
                inst.addOrUpdateTransientModifier(
                    new AttributeModifier(line.pctBasisModifierId(), contribution, Operation.ADD_VALUE));
            }
            inst.removeModifier(line.modifierId());                  // 确保残留的 ADD_MULTIPLIED_BASE 被清掉
        }
    }
    clampRuntimeHardCaps(player);   // 见第 1.7 / 1.8 节的运行时硬钳
}
```

**注意**：`PERCENT` 线的 `ADD_MULTIPLIED_BASE` 值必须是小数比例（`amount/100.0`），因为原版公式是 `d1 += d0 * amount`。

---

## 3. Attribute 注册（6 原版 + 7 自定义）

### 3.1 承载映射总表

| # | 维度 | 承载 | 是否需自定义 | 是否需要客户端同步 |
|---|---|---|---|---|
| 1 | 基础攻击伤害 | `Attributes.ATTACK_DAMAGE`（原版，base 1.0） | 否 | 原版已同步 |
| 2 | 基础生命值 | `Attributes.MAX_HEALTH`（原版，base 20.0，max 1024） | 否 | 原版已同步 |
| 3 | 基础防御力 | `Attributes.ARMOR`（原版，max 30） | 否 | 原版已同步 |
| 4 | 基础盔甲韧性 | `Attributes.ARMOR_TOUGHNESS`（原版，max 20） | 否 | 原版已同步 |
| 5 | 基础移动速度 | `Attributes.MOVEMENT_SPEED`（原版，`PercentageAttribute`，base 0.1） | 否 | 原版已同步 |
| 6 | 击退抗性 | `Attributes.KNOCKBACK_RESISTANCE`（原版，`PercentageAttribute`，max 1.0） | 否 | 原版已同步 |
| 7 | 生命恢复 | `rpgleveling:health_regen` | **是** | **需要**（GUI 展示） |
| 8 | 基础伤害减免 | `rpgleveling:damage_reduction` | **是** | **需要** |
| 9 | 暴击率 | `rpgleveling:crit_chance` | **是** | **需要** |
| 10 | 暴击伤害 | `rpgleveling:crit_damage` | **是** | **需要** |
| 11 | 生命偷取 | `rpgleveling:lifesteal` | **是** | **需要** |
| 12 | 掉落倍率 | `rpgleveling:drop_rate` | **是** | **需要** |
| 13 | 经验获取倍率 | `rpgleveling:xp_gain` | **是** | **需要** |

### 3.2 `RpgAttributes` 完整代码骨架

```java
package com.rpgleveling.registry;

public final class RpgAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, "rpgleveling");            // L113 签名

    /** HP/秒。RangedAttribute 的 max=1.0E6 只是形式值；真实收敛由 SAT cap=5 + pct cap=200 保证。 */
    public static final Holder<Attribute> HEALTH_REGEN = ATTRIBUTES.register("health_regen",
        () -> new RangedAttribute("attribute.rpgleveling.health_regen", 0.0, 0.0, 1.0E6)
                  .setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

    /** 减伤百分点。 */
    public static final Holder<Attribute> DAMAGE_REDUCTION = ATTRIBUTES.register("damage_reduction",
        () -> new PercentageAttribute("attribute.rpgleveling.damage_reduction", 0.0, 0.0, 1.0E6)
                  .setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

    public static final Holder<Attribute> CRIT_CHANCE = ATTRIBUTES.register("crit_chance",
        () -> new PercentageAttribute("attribute.rpgleveling.crit_chance", 0.0, 0.0, 1.0E6)
                  .setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

    /** 暴击伤害加成百分点（+50 表示暴击倍率 1.5 -> 2.0）。无天花板，用 RangedAttribute。 */
    public static final Holder<Attribute> CRIT_DAMAGE = ATTRIBUTES.register("crit_damage",
        () -> new RangedAttribute("attribute.rpgleveling.crit_damage", 0.0, 0.0, 1.0E9)
                  .setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

    public static final Holder<Attribute> LIFESTEAL = ATTRIBUTES.register("lifesteal",
        () -> new PercentageAttribute("attribute.rpgleveling.lifesteal", 0.0, 0.0, 1.0E6)
                  .setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

    public static final Holder<Attribute> DROP_RATE = ATTRIBUTES.register("drop_rate",
        () -> new PercentageAttribute("attribute.rpgleveling.drop_rate", 0.0, 0.0, 1.0E6)
                  .setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

    public static final Holder<Attribute> XP_GAIN = ATTRIBUTES.register("xp_gain",
        () -> new PercentageAttribute("attribute.rpgleveling.xp_gain", 0.0, 0.0, 1.0E6)
                  .setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

    public static final List<Holder<Attribute>> ALL_CUSTOM =
        List.of(HEALTH_REGEN, DAMAGE_REDUCTION, CRIT_CHANCE, CRIT_DAMAGE, LIFESTEAL, DROP_RATE, XP_GAIN);

    /** mod 总线事件：把 7 个自定义属性挂到 EntityType.PLAYER 上。 */
    @SubscribeEvent
    public static void onEntityAttributeModification(EntityAttributeModificationEvent e) {  // mod 总线
        for (Holder<Attribute> h : ALL_CUSTOM) {
            if (!e.has(EntityType.PLAYER, h)) e.add(EntityType.PLAYER, h);   // L53 / L43
        }
    }
}
```

**要点（全部有证据）**：
1. `DeferredRegister.create(Registries.ATTRIBUTE, "rpgleveling")` —— 注册表键 = `Registries.ATTRIBUTE`（API-NOTES 1.5）。
2. `RangedAttribute` 的 `descriptionId` 就是 lang key：`attribute.rpgleveling.health_regen` ⇒ 在 `zh_cn.json` 里写 `"attribute.rpgleveling.health_regen": "生命恢复"`。
3. **7 个自定义属性全部 `.setSyncable(true)`** —— 客户端才能通过原版 `ClientboundUpdateAttributesPacket` 读到有效值，GUI 才能显示真实数值（待运行验证 V2）。
4. `PercentageAttribute` 用于「值本身就是百分比」的 5 个属性，tooltip 自动显示 `+12.5%` 形式（API-NOTES 1.4）。
5. `setSentiment(POSITIVE)` 让 tooltip 用蓝色而不是红色（`Attribute.getMergedStyle`，API-NOTES 1.1）。
6. **必须**在 mod 总线上监听 `EntityAttributeModificationEvent`，否则 `player.getAttribute(RpgAttributes.CRIT_CHANCE)` 返回 `null`，整个加成就静默失效。
7. 给 `RangedAttribute` 的 max 填 `1.0E6` 是**形式值**：真实上限由 SAT 的 `cap` 保证（第 1.5 节）。填大是为了让高等级的 `flat * (1+pct)` 不被原版 `sanitizeValue` 提前钳制。

### 3.3 `Carrier` 枚举（承载方式）

```java
public enum Carrier {
    VANILLA_ATTACK_DAMAGE, VANILLA_MAX_HEALTH, VANILLA_ARMOR, VANILLA_ARMOR_TOUGHNESS,
    VANILLA_MOVEMENT_SPEED, VANILLA_KNOCKBACK_RESISTANCE,
    CUSTOM_HEALTH_REGEN, CUSTOM_DAMAGE_REDUCTION, CUSTOM_CRIT_CHANCE, CUSTOM_CRIT_DAMAGE,
    CUSTOM_LIFESTEAL, CUSTOM_DROP_RATE, CUSTOM_XP_GAIN;

    /** 只有 AttributeApplier 与 DerivedStats 读这两个方法，其他地方不直接碰 Attribute。 */
    public Holder<Attribute> attributeHolder() {
        return switch (this) {
            case VANILLA_ATTACK_DAMAGE          -> Attributes.ATTACK_DAMAGE;
            case VANILLA_MAX_HEALTH             -> Attributes.MAX_HEALTH;
            case VANILLA_ARMOR                  -> Attributes.ARMOR;
            case VANILLA_ARMOR_TOUGHNESS        -> Attributes.ARMOR_TOUGHNESS;
            case VANILLA_MOVEMENT_SPEED         -> Attributes.MOVEMENT_SPEED;
            case VANILLA_KNOCKBACK_RESISTANCE   -> Attributes.KNOCKBACK_RESISTANCE;
            case CUSTOM_HEALTH_REGEN            -> RpgAttributes.HEALTH_REGEN;
            case CUSTOM_DAMAGE_REDUCTION        -> RpgAttributes.DAMAGE_REDUCTION;
            case CUSTOM_CRIT_CHANCE             -> RpgAttributes.CRIT_CHANCE;
            case CUSTOM_CRIT_DAMAGE             -> RpgAttributes.CRIT_DAMAGE;
            case CUSTOM_LIFESTEAL               -> RpgAttributes.LIFESTEAL;
            case CUSTOM_DROP_RATE               -> RpgAttributes.DROP_RATE;
            case CUSTOM_XP_GAIN                 -> RpgAttributes.XP_GAIN;
        };
    }
}
```

---

## 4. 修饰符施加与去重

### 4.1 Operation 选择（唯一规则）

| 线 | `AttributeModifier.Operation` | amount 单位 |
|---|---|---|
| flat（`Mode.FLAT`） | `ADD_VALUE` | 维度原生单位（伤害点 / HP / 护甲点 / HP每秒 / 百分点 …） |
| percent（`Mode.PERCENT`） | `ADD_MULTIPLIED_BASE` | **小数比例**（属性值 `x` 百分点 ⇒ amount = `x/100`） |
| 任何情况 | **禁用 `ADD_MULTIPLIED_TOTAL`** | — |

**理由（API-NOTES 1.2）**：`ADD_MULTIPLIED_BASE` 的多个来源是 `(base + SUM flat) * (1 + SUM amt)`，**不会互相复利**；`ADD_MULTIPLIED_TOTAL` 是 `PROD (1+amt)`，多个来源会指数爆炸。

### 4.2 命名规范与去重

```
rpgleveling:<stat_id>_flat         例: rpgleveling:attack_damage_flat
rpgleveling:<stat_id>_percent      例: rpgleveling:attack_damage_percent
```

由 `StatLine.modifierId()` 生成（`id().replace('.', '_')`）。

**防重复叠加的三重保障**：
1. **固定 id**：26 条线的 26 个 id 是编译期常量，永远不会变。
2. **`addOrUpdateTransientModifier`**：按 id `put` 覆盖，重复调用不抛异常、不叠加（API-NOTES 1.6 的 L86-92）。**严禁用 `addTransientModifier`**（重复会抛 `IllegalArgumentException` 崩服）。
3. **不用 permanent modifier**：`addPermanentModifier` / `addOrReplacePermanentModifier` 会把 modifier 写进玩家 NBT（`AttributeInstance.save()` 只存 `permanentModifiers`），造成「磁盘一份 + Attachment 一份」的双写冲突。**全部用 transient（内存态），每次登录/重生/换维度/加点后重建。**

**数值为 0 时主动 `removeModifier(id)`**，避免留下 amount=0 的空 modifier 污染属性 tooltip。

---

## 5. 数据持久化

### 5.1 两个 Attachment 的注册

`com.rpgleveling.registry.RpgAttachments`

```java
public final class RpgAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "rpgleveling");   // L58 证据

    /** 玩家 RPG 数据。serializer + copyOnDeath => 死亡零惩罚 + End 返回保留。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerRpgData>> PLAYER_DATA =
        ATTACHMENT_TYPES.register("player_data",
            () -> AttachmentType.serializable(PlayerRpgData::new)      // L119/L132：INBTSerializable
                                .copyOnDeath()                          // L224：要求已设 serializer
                                .build());

    /** 刷怪笼 / 试炼刷怪笼生物的标记（防刷怪塔），不需要落盘。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> SPAWNER_MOB =
        ATTACHMENT_TYPES.register("spawner_mob",
            () -> AttachmentType.builder(() -> Boolean.FALSE).build());  // L95：无 serializer => 纯内存

    /** 首次行为奖励的已解锁 id 位图（见 8.7）。serializer + copyOnDeath => 死亡 / 换维度不丢图鉴。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DiscoveryData>> DISCOVERY =
        ATTACHMENT_TYPES.register("discovery",
            () -> AttachmentType.serializable(DiscoveryData::new)   // INBTSerializable<CompoundTag>
                                .copyOnDeath()                       // 图鉴属于玩家成就，死亡不掉
                                .build());
}
```

**`copyOnDeath()` 的语义（API-NOTES 第 6 节类 javadoc 原文）**：
> Serializable entity attachments are not copied on death by default (**but they are copied when returning from the end**).
> Serializable entity attachments can opt into copying on death via `Builder#copyOnDeath()`.

⇒ **一个 `copyOnDeath()` 同时覆盖「死亡重生」和「End 到 Overworld 的 PlayerEvent.Clone」两条路径** ⇒ 完美满足需求 7「死亡完全无惩罚」。
（`PlayerEvent.Clone` javadoc：「Either caused by death, or by traveling from the End to the overworld」，API-NOTES 2.1。）

⚠ `copyOnDeath()` 在 `serializer == null` 时抛 `IllegalStateException`（L225-227）⇒ 必须先 `serializable(...)`。

### 5.2 数据类

`com.rpgleveling.data.PlayerRpgData`（实现 `INBTSerializable<CompoundTag>`）

```java
public final class PlayerRpgData implements INBTSerializable<CompoundTag> {
    /** 唯一真相：累计获得的 RPG 经验。只增不减，long。 */
    private long totalXp = 0L;
    /** 26 条线各自投入的点数。索引 = StatLine.ordinal()。 */
    private final int[] points = new int[StatLine.COUNT];
    /** 洗点次数，仅统计用途。 */
    private int respecCount = 0;

    // ---- 以下全部是派生值，不落盘，由 totalXp / points 唯一确定 ----
    private transient int  cachedLevel = -1;
    private transient long cachedEarnedPoints = -1;
    private transient long cachedXpIntoLevel = -1;

    public int  level()          { ensureDerived(); return cachedLevel; }
    public long earnedPoints()   { ensureDerived(); return cachedEarnedPoints; }
    public long xpIntoLevel()    { ensureDerived(); return cachedXpIntoLevel; }
    public long xpForNextLevel() { return RpgMath.xpNeededForLevel(level() + 1); }
    public long spentPoints()    { int s = 0; for (int p : points) s += p; return s; }
    public long unspentPoints()  { return earnedPoints() - spentPoints(); }

    public int   points(StatLine l) { return points[l.index()]; }
    public int[] pointsView()       { return points; }          // 只读用途；外部不得直接改

    public void addTotalXp(long delta) {
        if (delta > 0) { totalXp = Math.min(Long.MAX_VALUE / 2, totalXp + delta); cachedLevel = -1; }
    }
    public void addPoints(StatLine l, int delta) {
        points[l.index()] = Math.max(0, points[l.index()] + delta);
    }
    public void respecAll() { java.util.Arrays.fill(points, 0); respecCount++; }

    private void ensureDerived() {
        if (cachedLevel < 0) {
            cachedLevel        = RpgMath.levelForXp(totalXp);
            cachedEarnedPoints = RpgMath.earnedPointsAtLevel(cachedLevel);
            cachedXpIntoLevel  = totalXp - RpgMath.cumulativeXpAtLevel(cachedLevel);
        }
    }

    @Override public CompoundTag serializeNBT(HolderLookup.Provider p) { /* totalXp, points[], respecCount */ }
    @Override public void deserializeNBT(HolderLookup.Provider p, CompoundTag tag) { /* 反向 + 校验长度 + cachedLevel = -1 */ }
}
```

**设计要点**：
- **只存 `totalXp` 与 `points[]`**，`level` / `earnedPoints` / `unspentPoints` **全部派生**。
  ⇒ 不可能出现「等级与经验不一致」「点数凭空多/少」的状态；**洗点 = `Arrays.fill(points, 0)`，自动恢复全部点数，零额外逻辑**。
- `level` 不落盘 ⇒ 改经验曲线后**旧存档自动按新曲线重算**，不会卡在旧等级。
- `deserializeNBT` 必须校验 `points.length == 26`（版本迁移），长度不符则整表清零并记 warn。

### 5.3 跨维度 / 跨存档 / 多人

| 场景 | 机制 | 结论 |
|---|---|---|
| 死亡重生 | `copyOnDeath()` | 数据保留 ✔ |
| End 到 Overworld | `copyOnDeath()`（javadoc 明示） | 数据保留 ✔ |
| Overworld 与 Nether 往返 | 玩家实体**不重建**，附件原地保留 | 数据保留 ✔ |
| 退出重进 | `serialize` / `deserialize` 走玩家 NBT | 数据保留 ✔ |
| 跨存档 | 数据在存档的玩家 .dat 里 | 按存档隔离（符合预期）✔ |
| 多人服务器 | 数据在服务端 `ServerPlayer` 上，客户端只有镜像 | 服务端权威 ✔ |

---

## 6. 生命周期：何时施加修饰符

`com.rpgleveling.event.PlayerLifecycleEvents`（游戏总线）

| # | 事件（1.21.1 准确类名） | 总线 | 动作 |
|---|---|---|---|
| 1 | `PlayerEvent.PlayerLoggedInEvent` | GAME | 延后 1 tick（`server.execute(...)`）后：`AttributeApplier.apply` + `RpgNet.syncTo(player)` |
| 2 | `PlayerEvent.Clone` | GAME | 从 `event.getOriginal()` 复制数据到 `event.getEntity()`（兜底 `copyOnDeath` 万一不生效，见 V3），然后 apply + sync |
| 3 | `PlayerEvent.PlayerRespawnEvent` | GAME | apply + sync |
| 4 | `PlayerEvent.PlayerChangedDimensionEvent` | GAME | apply + sync（属性随实体保留，但客户端要重收数据） |
| 5 | `PlayerEvent.PlayerLoggedOutEvent` | GAME | 清理该玩家的 transient 缓存（限流窗口、恢复累积器、lastAppliedMaxHealth） |
| 6 | `PlayerTickEvent.Post` | GAME | **兜底**：每 `general.reapply_interval_ticks`（默认 20）tick 调一次 `AttributeApplier.apply`（幂等，代价 = 26 次 `addOrUpdate`）；同时驱动生命恢复（第 7 节） |

### 6.1 为什么需要 tick 兜底

即使 1-5 全覆盖，仍可能漏掉：
- 其他 mod 用 `AttributeInstance.removeModifiers()` 清空属性；
- 配置文件在游戏内被重载（`ModConfigEvent.Reloading`）后 modifier 值没有跟着更新；
- 某些「重置玩家」的第三方操作。

因为 `addOrUpdateTransientModifier` 是**幂等**的（同 id 覆盖），20 tick 一次的兜底成本极低（26 次 map put），**推荐保留**。可通过 `general.reapply_interval_ticks = 0` 关闭。

### 6.2 MAX_HEALTH 变化的特殊处理（必须，否则掉血 / 白送血）

原版 `LivingEntity.setHealth` 会 `Mth.clamp(health, 0, getMaxHealth())`。因此：
- **上限提高时**：玩家当前血量不变 ⇒ 视觉上是「掉了一截」，体验差。
- **上限降低时**（洗点）：当前血量被自动钳到新上限 ⇒ 不会卡在非法血量。

处置（`AttributeApplier` 内）：

```java
// 只在「玩家主动加点导致上限变化」时补血；登录 / 重生 / 换维度时不补
double newMax = player.getAttributeValue(Attributes.MAX_HEALTH);
float  cur    = player.getHealth();
if (grantHealthOnIncrease && newMax > lastAppliedMaxHealth && lastAppliedMaxHealth > 0) {
    player.setHealth((float) Math.min(newMax, cur + (newMax - lastAppliedMaxHealth)));
}
lastAppliedMaxHealth = newMax;    // transient 字段，存在 RpgService 的 Player 缓存里
```

配置 `max_health.heal_on_increase`（默认 `true`）、`max_health.heal_mode` = `ADD_DELTA | KEEP_RATIO | NONE`（默认 `ADD_DELTA`）。
`lastAppliedMaxHealth` 是 **transient**，登录时置 `0`（表示「未知」）⇒ 登录 / 重生不会白送血量。✔

### 6.3 【verifier D-1】死亡重生后的属性保留与「防重复叠加」（本类 mod 的头号线上事故）

**风险**：NeoForge 的玩家属性在重生时会重建（`PlayerEvent.Clone` 换了一个新的 `ServerPlayer` 实体），如果实现上「在新实体上再加一遍 modifier」，
就会造成**数值翻倍**；反过来如果只在登录时施加，就会造成**重生后加成全部消失**。两者都是线上事故。

**三重防线**：

| 防线 | 机制 | 说明 |
|---|---|---|
| 1. 固定 modifier id + 幂等 API | 26 条线的 id 是编译期常量；一律用 `addOrUpdateTransientModifier`（同 id 覆盖），**严禁** `addTransientModifier`（重复会抛 `IllegalArgumentException`） | 即使被调用 100 次，同一属性上也只会有 1 个我们的 modifier |
| 2. 数据与 modifier 分离 | `PlayerRpgData` 用 `copyOnDeath()` 跟着玩家走；modifier 是**每次从点数全量重算**（不是累加） | 数值的唯一真相是 `points[26]`，modifier 只是它的投影 ⇒ 重生后重算得到完全相同的值 |
| 3. 开发期自检断言 | `AttributeApplier.apply()` 末尾在 `!FMLEnvironment.production` 时检查：每个受管属性上，namespace 为 `rpgleveling` 的 modifier 数量必须 `<= 2`（flat + pct 各一个）；超过则 `LOGGER.error` 并打印全部 id | 能立刻抓到「重复叠加」的实现回归 |

**verifier 的验收点（必须写进 L3 行为矩阵）**：

```
前置：/rpg debug setpoints @s attack_damage.flat 100  （或任意可观测维度）
断言 A：/rpg debug stats 的 attr.minecraft:generic.attack_damage 记为 V0
步骤 B：连续死亡重生 5 次（/kill 后立即重生，共 5 轮）
断言 B：每次重生后的 attr.* 与 V0 **逐位相等（容差 0）**，不是「近似相等」
断言 C：5 轮结束后，每个受管属性上 namespace=rpgleveling 的 modifier 数量仍为 1（flat）或 2（flat+pct）
步骤 D：下界→主世界 往返 3 次
断言 D：attr.* 与 V0 逐位相等
步骤 E：退出到主菜单再进世界
断言 E：attr.* 与 V0 逐位相等
```

> 注：`PlayerEvent.Clone` 的处理器里**必须**再复制一次数据（即使 `copyOnDeath()` 已生效），因为两者的执行顺序在 1.21.1 未在源码中明确规定（API-NOTES V3 待验证项）。
> 复制逻辑写成「取 `getOriginal()` 的数据，若新实体上已有数据则以原实体为准」，即**幂等覆盖**，重复执行不会累加。

### 6.4 【verifier D-2】经验倍率不得双重生效（硬不变量，可 grep 验证）

**风险**：若同时挂 `LivingExperienceDropEvent`（放大经验球）与 `PlayerXpEvent.XpChange`（放大吸收量），玩家实得经验会变成 `倍率²`。

**硬不变量（写进 code review 清单）**：

| 编号 | 不变量 | 验证方式 |
|---|---|---|
| **INV-1** | **本 mod 绝不注册任何 `net.neoforged.neoforge.event.entity.player.PlayerXpEvent` 的监听器。** RPG 经验只在 `XpService.grant()` 一处入账；原版经验球走原版逻辑，我们只在击杀时通过 `event.setDroppedExperience(...)` 影响**掉落量**，不再影响**吸收量**。 | `grep -rn "PlayerXpEvent" src/main/java/` **必须返回 0 行**（L1 静态验证，verifier 可直接跑） |
| **INV-2** | `xp_gain` 倍率只在 `XpService.grant()` 内出现一次。 | `grep -rn "XP_GAIN" src/main/java/` **只允许命中 `XpService.java` 一处** |
| **INV-3** | 击杀 XP 只入账一次：`LivingExperienceDropEvent` 处理器入账后**不再**在经验球拾取、`PlayerTickEvent` 或其他地方补账。 | 代码审计 + `/rpg debug stats` 的 `total_xp` 与击杀次数对拍（杀 N 只僵尸，增量 = N × 单只经验 × 倍率，误差 0） |

**为什么这样设计仍然自洽**：击杀时我们既入账 RPG 经验，又把倍率后的值写回 `setDroppedExperience` —— 玩家捡起经验球时原版会给他**原版经验**
（这一步是原版行为，不经过我们的代码），所以「原版经验」和「RPG 经验」是两条**独立**的线，各自只被放大一次。**不存在平方。**

---

## 7. 生命恢复（tick 驱动）

`com.rpgleveling.event.PlayerTickHandler`

```java
@SubscribeEvent
public static void onPlayerTick(PlayerTickEvent.Post event) {                 // 类名见 API-NOTES 2.1
    Player player = event.getEntity();
    if (player.level().isClientSide()) return;                                // javadoc 明确要求自查
    if (!(player instanceof ServerPlayer sp)) return;

    PlayerRpgData data = sp.getData(RpgAttachments.PLAYER_DATA);

    // --- 7.1 周期兜底重算修饰符 ---
    int interval = RpgConfig.reapplyIntervalTicks.get();
    if (interval > 0 && sp.tickCount % interval == 0) AttributeApplier.apply(sp, data);

    // --- 7.2 生命恢复 ---
    double hps = DerivedStats.healthRegenPerSecond(sp);                       // 读属性有效值
    hps = Math.min(hps, RpgConfig.regenHardCapHps.get());                     // 默认 20 HP/s（第 1.8 节）
    if (hps <= 0.0) return;
    if (RpgConfig.regenOnlyWhenHurt.get() && !sp.isHurt()) return;            // 与原版 FoodData 语义一致
    int needFood = RpgConfig.regenRequireFoodLevel.get();                     // 默认 0 = 不要求
    if (needFood > 0 && sp.getFoodData().getFoodLevel() < needFood) return;

    double perTick = hps / 20.0;
    double acc = RegenAccumulator.add(sp, perTick);                           // 每玩家累积器（transient）
    float  whole = (float) Math.floor(acc);
    if (whole >= 1.0F) {
        RegenAccumulator.subtract(sp, whole);
        sp.heal(whole);                                                       // LivingEntity.heal -> LivingHealEvent
    }
}
```

**要点**：
1. **必须判 `isClientSide()`** —— `PlayerTickEvent.Post` 双端都 fire（API-NOTES 2.1 javadoc 明确警告）。
2. **累积器保留小数**：`perTick` 可能是 `0.25`（5 HP/s ÷ 20），不能每 tick 都 `heal(0.25f)`（会产生大量 `LivingHealEvent` 且浮点漂移）。用 `double` 累积，攒够 1.0 才 `heal`，余数留到下 tick。
3. **与原版自然回血的关系**：**额外叠加，不冲突、不替换**。原版逻辑 100% 在 `FoodData.tick` 内（API-NOTES 第 4 节逐行证据），我们从不碰 `FoodData`。
4. `player.heal(float)` 会 fire `LivingHealEvent`（可被其他 mod 取消），这是**期望行为**（保持兼容）。
5. `heal` 内部已 `Mth.clamp` 到最大生命，不会溢出。
6. 使用 `sp.tickCount`（ServerPlayer 自己的计数）而不是全局 tick，避免多玩家相位一致导致同一 tick 集中开销。

---

## 8. 经验系统

### 8.1 单一入账函数（杜绝重复计数）

`com.rpgleveling.service.XpService`

```java
public enum XpSource { KILL, MINE, CRAFT, SMELT, FISH, TRADE, ADVANCEMENT, COMMAND, OTHER }

/**
 * 全 mod 唯一的 XP 入账入口。任何 XP 来源都必须走这里。
 * @param rawXp 未经倍率修正的基础经验（>= 0）
 * @return 实际入账的经验（已含 xp_gain 维度修正与限流）
 */
public static int grant(ServerPlayer player, XpSource src, int rawXp) {
    if (rawXp <= 0) return 0;
    PlayerRpgData data = player.getData(RpgAttachments.PLAYER_DATA);

    // 1) 经验获取倍率维度：statValue(XP_GAIN) 即 (0 + flat) * (1 + pct/100) - 100 的形式
    double mult = 1.0 + RpgMath.statValue(Stat.XP_GAIN, data.pointsView(), RpgConfig.snapshot()) / 100.0;
    long gained = Math.round(rawXp * mult);

    // 2) 滚动窗口限流（防刷怪塔，服务器推荐开启）
    int cap = RpgConfig.xpMinuteCap.get();                       // 默认 0 = 关闭
    if (cap > 0) gained = XpRateLimiter.admit(player, src, gained, cap);
    if (gained <= 0) return 0;

    // 3) 入账 + 等级刷新（原子提交）
    int oldLevel = data.level();
    data.addTotalXp(gained);
    int newLevel = data.level();
    if (newLevel != oldLevel) RpgService.onLevelUp(player, oldLevel, newLevel);

    RpgNet.syncTo(player);                                       // 推送等级 / 经验 / 点数
    return (int) gained;
}
```

**杜绝重复计数的硬规则**：
- **绝不**同时挂 `LivingExperienceDropEvent` 和 `PlayerXpEvent.XpChange` 来加经验（会变成 `mult` 平方）。
- **绝不**在经验球拾取时再加一次（击杀的 XP 已经在死亡事件里入账，见 8.2）。
- 每个来源事件都用各自独立的守卫（`isClientSide` / `getBreaker` 非空 / `recentlyHit` 等），见 8.2 表。

### 8.2 两类 XP 来源（lead 决策 B：击杀重复给，非击杀只给首次）

> **lead 锁定**：「挖矿 / 合成 / 钓鱼等原版行为 = **首次行为奖励**。每种方块 / 物品首次触发时给一次经验，之后不再给。
> 需要按玩家持久化一个已解锁 ID 集合。这样天然防挂机刷经验，同时兼作收集图鉴。」

| 类别 | 来源 | 事件（准确类名） | 给经验的条件 | 基础值 |
|---|---|---|---|---|
| **A 每次都给** | 击杀生物 | `LivingExperienceDropEvent` | 每次击杀（见下行守卫） | `event.getOriginalExperience()` |
| **B 仅首次** | 挖掘方块 | `BlockDropsEvent` | 该 `Block` 的 registry id **首次**被该玩家挖到 | `xp.discovery.reward.mine`（默认 10） |
| **B 仅首次** | 合成物品 | `PlayerEvent.ItemCraftedEvent` | 该结果 `Item` 的 registry id **首次**被该玩家合成 | `xp.discovery.reward.craft`（默认 15） |
| **B 仅首次** | 熔炼物品 | `PlayerEvent.ItemSmeltedEvent` | 该结果 `Item` 的 registry id **首次**被该玩家熔炼 | `xp.discovery.reward.smelt`（默认 8） |
| **B 仅首次** | 钓上物品 | `ItemFishedEvent` | 该 `Item` 的 registry id **首次**被该玩家钓到 | `xp.discovery.reward.fish`（默认 20） |
| **B 仅首次** | 与村民交易 | `TradeWithVillagerEvent` | 该交易配方（`profession` + 交易序号）**首次**被该玩家使用 | `xp.discovery.reward.trade`（默认 10） |
| **A 每次都给** | （可选）达成进度 | `AdvancementEvent.AdvancementEarnEvent` | 每次达成（进度本身天然只触发一次） | `xp.advancement.reward`，默认 0 = 关闭 |

**击杀来源的守卫（4 条，全部必须）**：
① `getAttackingPlayer() != null`（掉落伤害 / 纯自动化击杀不给）
② `getAttackingPlayer() != getEntity()`（**防自伤刷经验**）
③ 被击杀者若是 `SPAWNER_MOB` 标记的生物，且 `xp.count_spawner_mobs = false` ⇒ 不给
④ 击杀者必须是 `ServerPlayer`（`getAttackingPlayer()` 已保证）

**为什么非击杀改首次制是本次最关键的正确性改动**：
- 原方案「每次挖矿都给经验」在**效率 V + 信标 + 自动农场**下的产出是线性的、无上界的，配合 `drop_rate` 与 `xp_gain` 会指数放大 ⇒ 必须靠「每分钟上限」这种补丁压制，而补丁又会误伤正常玩家。
- 首次制把非击杀 XP 的总量**硬性上界**为 `Σ reward × (各注册表条目数)`：以原版 1.21.1 约 1000 方块 + 1400 物品计，**全部点亮也只有约 4 万 XP**（约等于 55 级），且**只可能发生一次**。
- ⇒ 非击杀 XP 变成「一次性收集奖励」，**天然不可刷**，同时给 `/rpg discovery` 提供了图鉴语义（见 8.7）。

**击杀的额外处理**：XpService 入账后，同时把倍率写回原版掉落，让经验球数量也体现倍率（视觉反馈一致）：

```java
int dropped = XpService.grant(killer, XpSource.KILL, event.getOriginalExperience());
event.setDroppedExperience(dropped);        // 经验球总量也随之放大
```

**因为 RPG 经验只在这一次入账，经验球被拾取时不再入账，所以不会双计。**

### 8.3 防刷手段（首要 3 条 + 次要保险 2 条）

| 优先级 | 手段 | 实现 | 配置 |
|---|---|---|---|
| **首要** | **0. 非击杀来源只给首次**（lead 决策 B） | 见 `8.7` 的 id 位图；同一方块 / 物品第二次起永不给经验 | `xp.discovery.enabled`（默认 `true`） |
| 首要 | 1. 刷怪笼生物不给经验 | `FinalizeSpawnEvent` 里 `if (e.getSpawnType().isSpawner()) mob.setData(SPAWNER_MOB, true)`（证据：`MobSpawnType.isSpawner()` L23）；击杀时检查该标记 | `xp.count_spawner_mobs`（默认 `false`） |
| 首要 | 2. 必须玩家亲手击杀 | `getAttackingPlayer() != null`（自动化击杀 / 纯掉落伤害不给经验） | 不可关（写死） |
| 次要保险 | 3. 滚动 60 秒 XP 上限 | 每玩家一个 `ArrayDeque<long[]>`（transient），窗口内累加超限则本 tick 不入账 | `xp.minute_cap`（默认 `0`；服务器推荐 `3000`） |
| 次要保险 | 4. 每 tick 每来源上限 | `xp.safety.max_per_tick.mine` / `.craft` / `.smelt` | 见 `12.3` |

> **重要**：手段 0 生效后，「自动农场 / 自动合成机 / 钓鱼机」对 XP 的贡献**恒为 0**（首次已解锁）。手段 3/4 因此从主要防线降级为**次要保险**，防的是 reward 配置被管理员改大后的极端情况。这是 lead 决策 B 带来的最大结构性收益。

**说明**：`SPAWNER_MOB` 用的是**无 serializer 的内存附件**，chunk 卸载后会丢失标记。这是**可接受的折中**（刷怪塔通常在加载范围内持续工作）；若需要 100% 可靠，M6 可改为在 `Level` 上挂一个 `SavedData` 记录 entityId 集合。

### 8.4 等级曲线（可直接编码）

**定义**（`RpgMath`，全部用 `StrictMath.log` 保证跨重启确定性）：

```java
// ===== lead 锁定值（决策 B），全部走配置 =====
public static final int    LINEAR_UNTIL_LEVEL = 200;    // general.xp.linear_until_level
public static final double LINEAR_BASE        = 100.0;  // general.xp.linear_base   (B0)
public static final double LINEAR_STEP        = 20.0;   // general.xp.linear_step   (S)
public static final double LOG_SCALE          = 0.15;   // general.xp.log_scale
public static final int    POINTS_PER_LEVEL   = 1;      // general.points.per_level  <- lead 锁定：每级 1 点

/** 线性段每级所需经验的闭式锚点：B0 + S*(L-1)。L=200 时 = 4080。 */
private static double linearNeed(int L) { return LINEAR_BASE + LINEAR_STEP * (L - 1); }

/** 对数段的锚点 = 线性段最后一级的需求，保证两段在 L=200 处连续。 */
private static double logAnchor() { return linearNeed(LINEAR_UNTIL_LEVEL); }   // = 4080

/** 从 L-1 级升到 L 级所需经验。L >= 1。
 *  线性段 (L <= 200): XP(L) = 100 + 20*(L-1)
 *  对数段 (L >  200): XP(L) = 4080 * (1 + 0.15 * ln(L - 199))        // L-199 == L-200+1
 */
public static long xpNeededForLevel(int L) {
    if (L <= 0) return 0L;
    if (L <= LINEAR_UNTIL_LEVEL) return Math.round(linearNeed(L));
    return Math.round(logAnchor() * (1.0 + LOG_SCALE * StrictMath.log((double) (L - LINEAR_UNTIL_LEVEL + 1))));
}

/** 从 0 级累计到 L 级所需的总经验。
 *  线性段闭式解：SUM(100 + 20(k-1), k=1..L) = 10*L^2 + 90*L      (L=200 时 = 418000)
 */
public static long cumulativeXpAtLevel(int L) {
    if (L <= 0) return 0L;
    if (L <= LINEAR_UNTIL_LEVEL) return (long) (10L * L * L + 90L * L);
    long sum = 10L * LINEAR_UNTIL_LEVEL * LINEAR_UNTIL_LEVEL + 90L * LINEAR_UNTIL_LEVEL;   // = 418000
    for (int k = LINEAR_UNTIL_LEVEL + 1; k <= L; k++) sum += xpNeededForLevel(k);
    return sum;
}

/** totalXp 到 level。线性段闭式解 O(1)，对数段增量推进。 */
public static int levelForXp(long totalXp) {
    if (totalXp <= 0) return 0;
    long linearCap = cumulativeXpAtLevel(LINEAR_UNTIL_LEVEL);              // 418000
    if (totalXp <= linearCap) {
        // 解 10L^2 + 90L <= xp  =>  L = floor((-9 + sqrt(81 + 0.4*xp)) / 2)
        return (int) Math.floor((-9.0 + StrictMath.sqrt(81.0 + 0.4 * totalXp)) / 2.0);
    }
    long acc = linearCap;
    int  L   = LINEAR_UNTIL_LEVEL;
    while (true) {
        long n = xpNeededForLevel(L + 1);
        if (acc + n > totalXp) return L;
        acc += n; L++;
    }
}

/** 升到 L 级累计获得的点数。lead 锁定「每级 1 点」=> 就是 L，无需累加。 */
public static long earnedPointsAtLevel(int L) { return Math.max(0L, L); }

/** 从 L-1 升到 L 时获得的点数。 */
public static int pointsForLevel(int L) { return L <= 0 ? 0 : POINTS_PER_LEVEL; }
```

**「每级 1 点」带来的三个简化（code 要注意）**：
1. `earnedPointsAtLevel(L) == L`，**不需要任何累加循环**，也不可能因曲线改动而算错。
2. `unspentPoints = level - SUM(points[0..25])`，洗点逻辑更简单。
3. 26 条线共享「每级 1 点」⇒ 等级 200 时总点数 200（平均 7.7 点/线），等级 500 时 500 点（平均 19 点/线）。这正是 `1.4` 把 `half_points` 下调到 40..100 的原因。

**性能**：`levelForXp` 在对数段是 `O(L - 200)`，L=1000 时 800 次迭代（每次一次 `StrictMath.log`），约 20 微秒。只在 `totalXp` 变化或打开 GUI 时调用，且 `PlayerRpgData` 有 `cachedLevel` 缓存。**不需要**上 lnGamma 之类的闭式解。
**确定性**：用 `StrictMath.log`（bit-exact）而不是 `Math.log`（允许 1 ulp 误差），避免 JVM 重启后边界等级漂移。
**连续性校验**：`xpNeededForLevel(200) = 100 + 20*199 = 4080`；`xpNeededForLevel(201) = round(4080*(1+0.15*ln2)) = 4504`。两段在 200 级处**平滑衔接，无跳变**。

### 8.5 曲线采样表（可直接用于验证）

**主采样表（lead 指定的 1 / 50 / 100 / 200 / 201 / 300 / 500，另补 1000 / 2000）**

| 等级 L | 本级所需经验 `need(L)` | 累计经验 `cumXp(L)` | 本级点数 | 累计点数 `earnedPoints(L)` |
|---|---|---|---|---|
| 1 | 100 | 100 | 1 | 1 |
| 50 | 1 080 | 29 500 | 1 | 50 |
| 100 | 2 080 | 109 000 | 1 | 100 |
| 200 | 4 080 | 418 000 | 1 | 200 |
| 201 | 4 504 | 422 504 | 1 | 201 |
| 300 | 6 904 | 1 051 430 | 1 | 300 |
| 500 | 7 573 | 2 511 411 | 1 | 500 |
| 1000 | 8 172 | 6 471 888 | 1 | 1 000 |
| 2000 | 8 668 | 14 924 943 | 1 | 2 000 |

**边界反查校验**（`levelForXp`）

| totalXp | level | 说明 |
|---|---|---|
| 0 | 0 | 新玩家 |
| 99 | 0 | 差 1 点到 1 级 |
| 100 | 1 | 正好升级（`need(1) = 100`） |
| 1 000 | 4 | `cumXp(4) = 10*16 + 90*4 = 520`；`cumXp(5) = 700` ⇒ 1000 落在 5 级区间内 ⇒ 4 级 |
| 29 500 | 50 | |
| 109 000 | 100 | |
| 417 999 | 199 | 差 1 点到 200 级 |
| 418 000 | **200** | 线性段终点（`10*200^2 + 90*200 = 418 000`） |
| 418 001 | 200 | |
| 1 051 430 | 300 | | 对数段 |
| 2 511 411 | 500 | | 对数段 |
| 6 471 888 | 1000 | | 对数段 |

**可读性校验**：`cumXp(200) = 10*200^2 + 90*200 = 400 000 + 18 000 = 418 000`（闭式解与逐项和一致）✔；`earnedPoints(L) = L` ✔。

> **与 lead 原文的一处数值差异（必须记录）**：lead 写「500 级约 7517」，但按 lead 给定的公式
> `XP(500) = 4080 * (1 + 0.15 * ln(500 - 199)) = 4080 * (1 + 0.15 * 5.707110) = 4080 * 1.8560665 = 7572.75 -> round = 7573`。
> **本设计以公式为准**（公式是可直接编码的契约；"7517" 与任何合理的取整方式都对不上，判断为笔误）。
> 若用户确实想要 7517，只需把 `general.xp.log_scale` 从 `0.15` 调到 `0.1486`（一处配置即可，符合「全部走配置表」的要求）。

### 8.6 洗点（免费）

`/rpg respec` 或 GUI 的「洗点」按钮：

```java
public static void respec(ServerPlayer player) {
    PlayerRpgData data = player.getData(RpgAttachments.PLAYER_DATA);
    data.respecAll();                                  // Arrays.fill(points, 0)
    AttributeApplier.apply(player, data);              // 26 条线全部归零 -> removeModifier
    // MAX_HEALTH 收回时血量会被原版 setHealth 自动钳到新上限，不需要特殊处理
    RpgNet.syncTo(player);
}
```

- **免费**：不扣任何东西（`general.respec.free = true`）。
- **无成本、无冷却**（`general.respec.cooldown_ticks = 0`）。
- 因为 `unspentPoints` 是派生的，洗点后瞬间全额返还，**不需要任何「返还计算」**。
- 保留 `totalXp` 与 `level` ✔（需求 7：死亡无惩罚；洗点也不掉级）。
- 可选 `general.respec.keep_history`：记录最近一次洗点前的 `points[]`，支持 `/rpg respec undo`（M6 可选）。

### 8.7 首次行为奖励（Discovery）—— 数据结构与性能设计

> 这是 lead 明确点名的「本次最容易踩坑的部分」：事件频率极高、需要去重集合、要考虑存档体积。以下给出可直接编码的方案。

#### 8.7.1 数据结构选择：注册表数值 id 位图（LongArray bitset）

**结论：用 `long[]` 位图，索引 = 注册表数值 id，而不是存 `ResourceLocation` 字符串集合。**

| 方案 | 1000 条已解锁时的 NBT 体积 | 查询复杂度 | 风险 |
|---|---|---|---|
| `Set<ResourceLocation>` 存字符串 | 约 30 KB（每条约 30 字节） | `O(1)` 哈希 | 存档膨胀；mod 加几百个物品就爆 |
| **`long[]` 位图（本方案）** | **约 128 字节**（1000 bit / 64） | **`O(1)` 位运算** | 数值 id 在 mod 列表变化时可能移位（见 8.7.4） |

```java
/** 每个 XpSource 一份位图 + 一份注册表规模快照。序列化为 CompoundTag。 */
public final class DiscoveryData implements INBTSerializable<CompoundTag> {
    private final EnumMap<XpSource, long[]> bits  = new EnumMap<>(XpSource.class);
    private final EnumMap<XpSource, Integer> sizes = new EnumMap<>(XpSource.class);
    private String modFingerprint = "";

    /** O(1) 位测试：热路径调用的唯一方法。 */
    public boolean isUnlocked(XpSource src, int registryId) {
        long[] a = bits.get(src);
        if (a == null || registryId < 0) return false;
        int w = registryId >>> 6;
        return w < a.length && (a[w] & (1L << (registryId & 63))) != 0L;
    }

    /** O(1) 置位。返回 true 表示「这次是新解锁」，应当给经验。 */
    public boolean unlock(XpSource src, int registryId, int registrySize, int maxBits) {
        int limit = Math.min(registrySize, maxBits);
        if (registryId < 0 || registryId >= limit) return false;      // 越界 / 超上限 -> 安全降级，不给经验
        long[] a = bits.computeIfAbsent(src, k -> new long[(limit + 63) >>> 6]);
        int w = registryId >>> 6;
        if (w >= a.length) return false;                              // 长度不匹配 -> 不扩容、不抛异常
        long mask = 1L << (registryId & 63);
        if ((a[w] & mask) != 0L) return false;                        // 已解锁
        a[w] |= mask;
        sizes.put(src, limit);
        return true;
    }

    /** 已解锁数量。O(位图字数)，只在指令 / 图鉴 UI 调用，tick 路径禁止调用。 */
    public int count(XpSource src) {
        long[] a = bits.get(src);
        if (a == null) return 0;
        int c = 0;
        for (long w : a) c += Long.bitCount(w);
        return c;
    }
    // serializeNBT / deserializeNBT：EnumMap<XpSource, long[]> 写成 CompoundTag 的 LongArray
}
```

#### 8.7.1b 【BL-7 裁决】「首次」的粒度定义与集合的有界性

| 来源 | **解锁粒度** | 取 id 的方式 | 每个粒度的条目数（原版 1.21.1 量级） | 是否会用 BlockState 变体炸开 |
|---|---|---|---|---|
| MINE | **每个 `Block`**（不是 `BlockState`） | `BuiltInRegistries.BLOCK.getId(state.getBlock())` | 约 1000 | **否**——同一方块的朝向 / 年龄 / 半砖形态共用一个 id（小麦 8 个生长阶段只算 1 条） |
| CRAFT | **每个结果 `Item`** | `BuiltInRegistries.ITEM.getId(stack.getItem())` | 约 1400 | 否 |
| SMELT | **每个结果 `Item`** | 同上 | 约 1400 | 否 |
| FISH | **每个 `Item`**（一次钓获可能含多个 stack ⇒ 逐个判定，循环上界 = 掉落件数，通常 1） | 同上 | 约 1400 | 否 |
| TRADE | **每条交易**：`profession.ordinal() * 1000 + offerIndex` 合成的稳定整数 | 无全局注册表 id，故用合成整数 | `professionCount * 1000`（原版 profession 约 15 ⇒ 15000） | 否 |

**集合必须有界 —— 这是 BL-7 的核心要求，本设计的答案：**

| 要求 | 本设计的答案 |
|---|---|
| **不是无界字符串集合** | ✅ 用 `long[]` **位图**，索引是注册表**数值 id**，不是 `Set<String>` / `Set<ResourceLocation>` |
| **硬性有界** | ✅ `xp.discovery.max_bits_per_source = 65536` ⇒ 每个来源最多 `65536/8 = 8192 字节`；5 个来源合计 **上限 40 KB/玩家** |
| **实际体积** | 原版量级下：方块位图 `ceil(1000/64) = 16` 个 long = **128 字节**；物品位图 `ceil(1400/64) = 22` 个 long = **176 字节**。5 个来源合计 **不到 1 KB/玩家** |
| **超出硬上限怎么办** | `unlock()` 返回 `false`（不解锁、不给经验），并 `LOGGER.warn` 一次（不刷屏）。**不会扩容、不会崩、不会无限增长** |
| **位图长度与注册表不匹配** | `w >= a.length` 边界检查 ⇒ 安全降级为 `false` |
| **反序列化长度校验** | 每个 `long[]` 长度必须等于 `ceil(min(size, maxBits)/64)`，不符则丢弃该来源位图并记 warn（宁可重给一次经验，不可崩档） |
| **mod 列表变化导致数值 id 移位** | 存 `modFingerprint`；不一致时**不做迁移**，沿用旧位图 —— 最坏结果是少数条目「看起来已解锁」而**少给**几点经验，绝不会多给。对数值 id 稳定性敏感的整合包可切 `storage = STRING_SET`（体积换正确性，此时才使用字符串集合，且同样受 `max_bits_per_source` 条数上限约束） |

#### 8.7.2 事件钩子（每次事件只有 1 次 O(1) 位测试，绝不遍历）

```java
// XpEvents.java —— 以挖矿为例（BlockDropsEvent 天然只在服务端 fire，getLevel() 返回 ServerLevel）
@SubscribeEvent
public static void onBlockDrops(BlockDropsEvent event) {
    if (!RpgConfig.discoveryEnabled.get()) return;                       // 总开关，默认 true
    if (!(event.getBreaker() instanceof ServerPlayer sp)) return;
    if (!RpgConfig.discoveryCreative.get() && sp.isCreative()) return;

    int id = BuiltInRegistries.BLOCK.getId(event.getState().getBlock());   // O(1) 数组下标访问
    DiscoveryData d = sp.getData(RpgAttachments.DISCOVERY);

    // >>> 关键：单次位测试。没有集合遍历，没有任何每 tick 扫描 <<<
    if (!d.unlock(XpSource.MINE, id, BuiltInRegistries.BLOCK.size(), RpgConfig.discoveryMaxBits.get())) return;

    XpService.grant(sp, XpSource.MINE, RpgConfig.discoveryRewardMine.get());
    if (RpgConfig.discoveryNotify.get()) {
        sp.displayClientMessage(Component.translatable("rpgleveling.discovery.new",
                event.getState().getBlock().getName(),
                d.count(XpSource.MINE), BuiltInRegistries.BLOCK.size()), true);      // 图鉴提示
    }
}
```

其余四个来源结构完全一致，只有 id 来源与 reward 不同：

| 来源 | 事件 | id 取自 | registrySize 取自 |
|---|---|---|---|
| MINE | `BlockDropsEvent` | `BuiltInRegistries.BLOCK.getId(event.getState().getBlock())` | `BuiltInRegistries.BLOCK.size()` |
| CRAFT | `PlayerEvent.ItemCraftedEvent` | `BuiltInRegistries.ITEM.getId(event.getCrafting().getItem())` | `BuiltInRegistries.ITEM.size()` |
| SMELT | `PlayerEvent.ItemSmeltedEvent` | `BuiltInRegistries.ITEM.getId(event.getSmelting().getItem())` | `BuiltInRegistries.ITEM.size()` |
| FISH | `ItemFishedEvent` | 遍历 `getDrops()` 里每个非空 stack 取 `ITEM.getId(...)`（一次钓获可解锁多种） | `BuiltInRegistries.ITEM.size()` |
| TRADE | `TradeWithVillagerEvent` | `profession.ordinal() * 1000 + offerIndex`（村民交易没有全局注册表 id，用稳定合成整数） | `professionCount * 1000` |

> **FISH 的例外说明**：`ItemFishedEvent` 一次可能给多个 stack，因此要对每个 stack 各做一次位测试；这是**上界为掉落件数**的循环（通常 1 件），不是全表扫描。

#### 8.7.3 如何避免「每 tick 全表扫描」（lead 点名的坑）

| 反模式（禁止） | 本方案的做法 |
|---|---|
| 每 tick 遍历已解锁集合做统计 | `count()` **只在** `/rpg discovery` 与图鉴 UI 里调用；tick 路径一次都不调 |
| 用 `Set.contains` + `Set.add` 去重 | 用 `long[]` 位图，`isUnlocked` / `unlock` 都是常数时间位运算 |
| 每次事件都整体重写附件触发脏标记 | 只在 `unlock` 返回 `true` 时才需要落盘；`DiscoveryData` **不做** `sync(..)`（纯服务端数据，客户端按需请求） |
| 每次事件都重建位图数组 | 位图按 `computeIfAbsent` 惰性分配，之后原地 `|=` |
| 为了输出统计而遍历整个注册表 | `/rpg discovery` 输出「已解锁数 / 注册表总数」，总数取 `registry.size()`（O(1)），不遍历 |

**为什么事件频率高也不会成为瓶颈**：`BlockDropsEvent` 在效率 V + 信标下每 tick 可能 fire 几十次，但每次只做
「`Registry.getId`（数组下标访问） + 一次位测试 + 一次位或」，合计约 10 ns 量级。**没有任何循环、没有分配、没有 IO。**

#### 8.7.4 存档体积与「mod 列表变化」的处理（必须明确的边界）

| 问题 | 处理 |
|---|---|
| 存档体积 | 位图长度 = `ceil(min(registrySize, max_bits) / 64)` 个 `long`。原版约 1000 方块 = 16 个 long = 128 字节；约 1400 物品 = 176 字节。**5 个来源合计不到 1 KB/玩家。** |
| 硬上限 | `xp.discovery.max_bits_per_source`（默认 `65536`）⇒ 单来源最多 8 KB。超出部分不解锁、不给经验，并只 `LOGGER.warn` 一次（不刷屏）。 |
| mod 列表变化导致数值 id 移位 | 位图按注册表数值 id 索引，注册表内容变化时 id 可能移位。处置：① 存 `modFingerprint`（已加载 modId 列表的哈希）；② 指纹不一致时**不做迁移**，直接沿用旧位图 —— 最坏结果是少数条目「看起来已解锁」而少给几点经验，**绝不会多给、不会崩**；③ 提供 `xp.discovery.storage = BITSET / STRING_SET`，对数值 id 稳定性不放心的整合包可切成字符串集合模式（体积换正确性）。 |
| 位图长度与当前注册表不匹配 | `unlock` 里做 `w >= a.length` 边界检查，返回 `false`（安全降级，不抛异常、不扩容）。 |
| 反序列化长度校验 | `deserializeNBT` 校验每个 `long[]` 长度等于 `ceil(min(size, maxBits)/64)`，不符则丢弃该来源的位图并记 warn（宁可重给一次经验，不可崩档）。 |

#### 8.7.5 为什么「首次制」把非击杀 XP 变成不可刷

- 总量上界 = `Σ_sources reward × min(registrySize, max_bits)`。以原版约 1000 方块 + 约 1400 物品计，
  `10×1000 + 15×1400 + 8×1400 + 20×1400 + 10×(professionCount×1000)` 的**绝对上界约 10 万 XP**，但其中「方块/物品」部分实际只有约 6 万 XP（约等于 63 级），**且一个玩家一辈子只能拿一次**。
- ⇒ 挂机农场、自动合成机、钓鱼机对 XP 的贡献**恒为 0**（首次已解锁，之后永不再给），无需任何「每分钟上限」补丁。
- ⇒ `8.3` 的「每 tick 上限」从**主要防线**降级为**次要保险**（防的是 reward 配置被改大后的极端情况）。

#### 8.7.6 配置项（全部走配置表）

| 配置键 | 默认 | 说明 |
|---|---|---|
| `xp.discovery.enabled` | `true` | 总开关（lead 要求可配置） |
| `xp.discovery.sources` | `[mine, craft, smelt, fish, trade]` | 哪些来源启用首次制 |
| `xp.discovery.reward.mine` | `10` | 首次挖到某方块的经验 |
| `xp.discovery.reward.craft` | `15` | 首次合成某物品 |
| `xp.discovery.reward.smelt` | `8` | 首次熔炼某物品 |
| `xp.discovery.reward.fish` | `20` | 首次钓到某物品 |
| `xp.discovery.reward.trade` | `10` | 首次使用某条交易 |
| `xp.discovery.max_bits_per_source` | `65536` | 位图硬上限（防存档膨胀） |
| `xp.discovery.storage` | `BITSET` | `BITSET` 紧凑 / `STRING_SET` 抗 id 移位 |
| `xp.discovery.notify` | `true` | 首次解锁时是否给 actionbar 图鉴提示 |
| `xp.discovery.creative` | `false` | 创造模式是否计入挖掘解锁 |

---

## 9. 网络同步

### 9.1 数据契约

| 方向 | Payload | 字段 | 频率 |
|---|---|---|---|
| S2C | `ClientboundRpgDataPayload` | `level, totalXp, xpIntoLevel, xpForNextLevel, earnedPoints, spentPoints, unspentPoints, int[26] points` | 登录 / 重生 / 换维度 / 等级变化 / 加点 / 洗点 / 客户端请求 |
| S2C | `ClientboundRpgFeedbackPayload` | `int code`（0=OK, 1=无点数, 2=非法参数, 3=限流） | 仅在操作失败时 |
| C2S | `ServerboundSpendPointPayload` | `int lineIndex, int amount`（amount 可负 = 退点） | 玩家点击 |
| C2S | `ServerboundRespecPayload` | 无字段 | 点击「洗点」 |
| C2S | `ServerboundRequestDataPayload` | 无字段 | 按 K 打开界面时若本地无数据 |

**服务端权威**：客户端**只**发「我要往第 N 条线加 M 点」和「我要洗点」，**绝不上报任何数值**（等级 / 经验 / 点数 / 属性值全由服务端算）。

### 9.2 注册

`com.rpgleveling.registry.RpgPayloads`（**mod 总线**）

```java
@SubscribeEvent
public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {         // mod 总线
    PayloadRegistrar r = event.registrar("1");                                     // L40，版本号
    r.playToClient(ClientboundRpgDataPayload.TYPE,     ClientboundRpgDataPayload.STREAM_CODEC,     ClientHandler::onData);
    r.playToClient(ClientboundRpgFeedbackPayload.TYPE, ClientboundRpgFeedbackPayload.STREAM_CODEC, ClientHandler::onFeedback);
    r.playToServer(ServerboundSpendPointPayload.TYPE,  ServerboundSpendPointPayload.STREAM_CODEC,  ServerHandler::onSpend);
    r.playToServer(ServerboundRespecPayload.TYPE,      ServerboundRespecPayload.STREAM_CODEC,      ServerHandler::onRespec);
    r.playToServer(ServerboundRequestDataPayload.TYPE, ServerboundRequestDataPayload.STREAM_CODEC, ServerHandler::onRequest);
}
```

**默认 handler 在主线程执行**（`MainThreadPayloadHandler` 包装，API-NOTES 第 7 节 javadoc）⇒ **不需要在 handler 里再 `enqueueWork`**。

### 9.3 数据包的 StreamCodec（必须手写，因为 composite 最多 6 元）

`StreamCodec.composite` 最大 6 个字段（API-NOTES 第 7 节证据：L208 五元 / L243 六元），而我们的数据包有 8 个字段 ⇒ 用 `StreamCodec.of`（L15）。

```java
public record ClientboundRpgDataPayload(
        int level, long totalXp, long xpIntoLevel, long xpForNextLevel,
        long earnedPoints, long spentPoints, long unspentPoints, int[] points)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundRpgDataPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("rpgleveling", "rpg_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRpgDataPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.level);
                buf.writeVarLong(p.totalXp);
                buf.writeVarLong(p.xpIntoLevel);
                buf.writeVarLong(p.xpForNextLevel);
                buf.writeVarLong(p.earnedPoints);
                buf.writeVarLong(p.spentPoints);
                buf.writeVarLong(p.unspentPoints);
                buf.writeVarIntArray(p.points);              // FriendlyByteBuf L304
            },
            buf -> new ClientboundRpgDataPayload(
                buf.readVarInt(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarIntArray()));

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```

**C2S 包用 `StreamCodec.composite` 即可（2 元 / 0 元）**：

```java
public record ServerboundSpendPointPayload(int lineIndex, int amount) implements CustomPacketPayload {
    public static final Type<ServerboundSpendPointPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("rpgleveling", "spend_point"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSpendPointPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, ServerboundSpendPointPayload::lineIndex,
                              ByteBufCodecs.VAR_INT, ServerboundSpendPointPayload::amount,
                              ServerboundSpendPointPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```

**注意**：`ByteBufCodecs.VAR_INT` 是 `StreamCodec<ByteBuf, Integer>`，而 `playToServer` 需要 `StreamCodec<? super RegistryFriendlyByteBuf, T>`。因 `RegistryFriendlyByteBuf` 继承链最终到 `ByteBuf`，泛型 `? super` 允许（NeoForge 自己的包就是这么写的）。若编译器报错，退化为 `StreamCodec.of` 手写。

### 9.4 客户端 handler 与数据镜像

`com.rpgleveling.client.ClientRpgData`（纯客户端只读镜像）

```java
public final class ClientRpgData {
    private static volatile ClientboundRpgDataPayload snapshot;      // 只读
    public static void accept(ClientboundRpgDataPayload p) { snapshot = p; }
    public static ClientboundRpgDataPayload get() { return snapshot; }
    public static boolean has() { return snapshot != null; }
    public static void clear() { snapshot = null; }                  // 断线时清空
}
```

**客户端永不修改属性、永不自算等级**。GUI 里显示的「当前数值」有两条来源：
1. **RPG 面板的等级 / 经验 / 点数** —— 来自 `ClientRpgData`（服务端推送）。
2. **属性有效值**（如当前攻击伤害）—— 直接读客户端 `LocalPlayer` 的 AttributeMap（7 个自定义属性已 `setSyncable(true)`，原版会推 `ClientboundUpdateAttributesPacket`）。

两条都**不是**客户端自算的，所以天然一致（待运行验证 V2）。

---

## 10. 加点界面（按 K 打开）

### 10.1 打开流程

```java
// com.rpgleveling.client.ClientEvents  （游戏总线，仅客户端）
@SubscribeEvent
public static void onClientTick(ClientTickEvent.Post event) {                  // 1.21.x 类名
    while (KeyBindings.OPEN_RPG.consumeClick()) {                              // KeyMapping L110
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null) {
            if (!ClientRpgData.has()) {
                PacketDistributor.sendToServer(new ServerboundRequestDataPayload());   // 先要数据
            }
            mc.setScreen(new RpgScreen());
        }
    }
}
```

`com.rpgleveling.client.KeyBindings`

```java
public static final KeyMapping OPEN_RPG = new KeyMapping(
        "key.rpgleveling.open",                       // lang key
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_K,
        "key.categories.rpgleveling");                // 1.21.1 的 category 是 String（L92）
```

注册（**mod 总线，仅客户端**）：

```java
@SubscribeEvent
public static void onRegisterKeys(RegisterKeyMappingsEvent event) { event.register(KeyBindings.OPEN_RPG); }
```

### 10.2 Screen 结构（不走 Menu，用纯 Screen）

> **决策理由**：加点不涉及物品栏交互，走 `AbstractContainerMenu` 需要额外注册 `MenuType` + `RegisterMenuScreensEvent`，且要把 26 个 int 同步进 `ContainerData`（`ContainerData` 是 int 通道，26 个值塞进去很别扭）。纯 `Screen` + 自定义 payload 更简单、更可控。

`com.rpgleveling.client.screen.RpgScreen extends Screen`

```java
public class RpgScreen extends Screen {
    private static final int ROW_H = 22, PANEL_W = 340, HEADER_H = 46, FOOTER_H = 28;
    private int scroll;
    private final List<StatRow> rows = new ArrayList<>(26);   // 13 维度 x {flat, percent}

    protected RpgScreen() { super(Component.translatable("gui.rpgleveling.title")); }   // Screen L99

    @Override protected void init() {                                                   // Screen L365
        rebuildRows();
        addRenderableWidget(Button.builder(Component.translatable("gui.rpgleveling.respec"), b ->
            PacketDistributor.sendToServer(new ServerboundRespecPayload())
        ).bounds(width / 2 - 60, height - 26, 120, 20).build());                        // Button.builder L22
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {  // L132
        renderBackground(g, mouseX, mouseY, partial);                                     // L377
        var d = ClientRpgData.get();
        if (d == null) { g.drawCenteredString(font, Component.translatable("gui.rpgleveling.syncing"),
                                              width / 2, height / 2, 0xFFFFFF); return; }
        // 头部：等级、经验条、可用点数
        g.drawString(font, Component.translatable("gui.rpgleveling.level", d.level()), 12, 10, 0xFFFFFF);   // L556
        g.fill(12, 24, 12 + 300, 30, 0xFF202020);                                            // L257
        g.fill(12, 24, 12 + (int) (300.0 * d.xpIntoLevel() / Math.max(1L, d.xpForNextLevel())), 30, 0xFF55FF55);
        g.drawString(font, Component.translatable("gui.rpgleveling.points", d.unspentPoints()), 12, 34, 0xFFFFD700);
        // 行列表
        for (StatRow r : rows) if (r.visible(scroll)) r.render(g, font, mouseX, mouseY);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Mth.clamp(scroll - (int) Math.signum(dy) * ROW_H * 2, 0, maxScroll()); return true;
    }
    @Override public boolean isPauseScreen() { return false; }
}
```

### 10.3 行布局（26 行 = 13 维度 × 2 模式）

每行显示（宽 340，高 22）：

```
[维度名]            [flat]    -[ 12 ]+   +1 +10     当前: +6.0
[维度名]            [percent] -[  4 ]+   +1 +10     当前: +12.5%
```

- 点「+1」/「+10」/「-1」⇒ 立刻发 `ServerboundSpendPointPayload(lineIndex, delta)` **并在本地乐观更新**（服务端的 `ClientboundRpgDataPayload` 会覆盖本地，UI 不会闪烁错值）。
- 鼠标悬停行 ⇒ `g.renderTooltip(font, tooltipLines, mouseX, mouseY)`（L1398），内容包含：
  - 该线当前投入点数、当前贡献值；
  - **收敛信息**：`SaturatingCurve.describe(cap, half, points)` 生成「已达上限的 X%，再加 N 点到 Y%」；
  - 说明文字（来自 lang）。

**tooltip 的收敛提示（本设计独有）**：因为曲线是饱和的，必须让玩家看到「收益在递减，但没有浪费」。用 1.2 节的反解公式：

```java
public static Component describe(double cap, double half, int points) {
    double curRatio = points / (points + half);                              // 0..1
    double target   = Math.min(0.99, curRatio + 0.05);
    double needPts  = half * target / (1.0 - target);                        // 反解
    return Component.translatable("gui.rpgleveling.curve.hint",
            String.format("%.1f", curRatio * 100.0),
            String.format("%.0f", Math.max(0.0, needPts - points)));
}
```

### 10.4 空 / 降级状态

| 情况 | 表现 |
|---|---|
| 未收到 `ClientRpgData` | 显示「正在同步…」，按钮全部 disabled；同时自动补发 `ServerboundRequestDataPayload` |
| 断线（`ClientPlayerNetworkEvent.LoggingOut`） | `ClientRpgData.clear()`；若屏幕还开着则 `onClose()` |
| 单人游戏 | 同一套 payload 路径（NeoForge 内存连接也走序列化，API-NOTES 第 7 节 javadoc） |
| `general.hud.enabled = true`（默认 false） | 在 `RegisterGuiLayersEvent` 里注册一个角落 HUD 小标签显示等级与可用点数（**不在需求内，默认关闭**） |

---

## 11. `/rpg` 指令与服务端校验

### 11.1 指令树

```
/rpg                                  -> 显示自己的等级 / 经验 / 可用点数（等同 /rpg info）
/rpg info [<player>]                  -> 查看自己或他人（他人需权限 2）
/rpg stats [<player>]                 -> **结构化测试面**：等级 + totalXp + 可用/已投点数 + 13 维度 x 2 模式的
                                         投入点数与**最终计算值**（见 11.4；这是 VERIFICATION.md 的硬性要求）
/rpg list [<player>]                  -> 同 /rpg stats 的可读版（逐行列出 26 条线）
/rpg spend <line> <amount>            -> 加点（amount 可负 = 退点），line 形如 <stat>.<flat|percent>
/rpg respec                           -> 免费洗点
/rpg discovery [<player>]             -> 首次行为奖励图鉴：各来源「已解锁 / 总数」+ 总收集进度（见 8.7）
/rpg debug stats [<player>]           -> 权限 2；/rpg stats 的机器可解析版（每行 key=value，便于脚本断言）
/rpg debug addpoints <player> <n>     -> 权限 2；直接灌注未分配点数（构造测试前置状态，不改 totalXp）
/rpg debug setlevel <player> <n>      -> 权限 2；直接设置等级（内部反解出对应 totalXp）
/rpg debug override <player> <dim> <value>|clear   -> 权限 2；绕过曲线强制某维度最终值（**仅内存，见 11.4.1**）
/rpg debug forcecrit <player> <true|false>         -> 权限 2；强制每次攻击必定暴击（测 crit_damage 用）
/rpg admin setlevel <player> <level>  -> 权限 2
/rpg admin addxp <player> <amount>    -> 权限 2
/rpg admin setpoints <player> <line> <points>   -> 权限 2
/rpg admin reset <player>             -> 权限 2（清零 totalXp + points）
```

`line` 用 `StringArgumentType.word()` + `.suggests(...)` 提供 26 个候选；用一个静态 `Map<String, StatLine>` 做 O(1) 查找（key 例：`attack_damage.flat`）。

### 11.2 注册

```java
@SubscribeEvent
public static void onRegisterCommands(RegisterCommandsEvent event) {                 // 游戏总线
    CommandDispatcher<CommandSourceStack> d = event.getDispatcher();                 // L36
    d.register(Commands.literal("rpg")                                               // Commands L419
        .executes(ctx -> RpgCommand.info(ctx.getSource(), ctx.getSource().getPlayerOrException()))
        .then(Commands.literal("info")  .then(Commands.argument("player", EntityArgument.player()) /* ... */ ))
        .then(Commands.literal("list")  /* ... */ )
        .then(Commands.literal("spend")
            .then(Commands.argument("line", StringArgumentType.word()).suggests(LINE_SUGGEST)
                .then(Commands.argument("amount", IntegerArgumentType.integer(-100000, 100000))
                    .executes(RpgCommand::spend))))
        .then(Commands.literal("respec")
            .executes(ctx -> RpgCommand.respec(ctx.getSource(), ctx.getSource().getPlayerOrException())))
        .then(Commands.literal("admin").requires(s -> s.hasPermission(2))            // L390
            .then(Commands.literal("setlevel")   /* ... */ )
            .then(Commands.literal("setpoints")  /* ... */ )
            .then(Commands.literal("addxp")      /* ... */ )
            .then(Commands.literal("reset")      /* ... */ )));
}
```

反馈统一用 `sendSuccess(Supplier<Component>, boolean)`（L480）+ `Component.translatable(...)`。

### 11.3 服务端校验与防作弊（唯一写入口：`RpgService`）

**所有写操作（payload / 指令 / 内部调用）都必须经过这四个函数，不接受任何旁路：**

```java
public final class RpgService {

    /** 加点 / 退点。唯一的点数变更入口。 */
    public static boolean spend(ServerPlayer player, int lineIndex, int amount) {
        // 1) 结构校验：lineIndex 必须落在 [0, 26)
        if (lineIndex < 0 || lineIndex >= StatLine.COUNT) { violation(player, "lineIndex"); return false; }
        // 2) 数量校验：单次绝对值 <= 100，禁止批量刷包
        if (amount == 0 || Math.abs(amount) > 100) { violation(player, "amount"); return false; }
        // 3) 频率校验：每 20 tick 最多 20 次（防连点脚本 / 刷包）
        if (!RateLimiter.admit(player, "spend", 20, 20)) { RpgNet.feedback(player, FB_RATE_LIMIT); return false; }

        PlayerRpgData data = player.getData(RpgAttachments.PLAYER_DATA);
        StatLine line = StatLine.byIndex(lineIndex);
        LineCfg  cfg  = RpgConfig.lineCfg(line);

        // 4) 语义校验：线启用、点数上限、可用点数、退点不能退到负数
        if (!cfg.enabled()) return false;
        int cur    = data.points(line);
        int target = cur + amount;
        if (target < 0) { RpgNet.feedback(player, FB_NO_POINTS); return false; }
        if (cfg.maxPoints() >= 0 && target > cfg.maxPoints()) { RpgNet.feedback(player, FB_LINE_CAP); return false; }
        long cost = target - cur;
        if (cost > 0 && cost > data.unspentPoints()) { RpgNet.feedback(player, FB_NO_POINTS); return false; }

        // 5) 提交（原子：改点数 -> 重算修饰符 -> 推送）
        data.addPoints(line, amount);
        AttributeApplier.apply(player, data);
        RpgNet.syncTo(player);
        return true;
    }

    /** 免费洗点。 */
    public static void respec(ServerPlayer player) { /* 见 8.6 */ }

    /** 等级变化钩子：只做推送与统计，不发放实体物品。 */
    public static void onLevelUp(ServerPlayer player, int oldLevel, int newLevel) { /* 音效 + 聊天提示 + sync */ }

    private static void violation(ServerPlayer player, String why) { /* 计数 + warn 日志，超阈值可选踢出 */ }
}
```

**防作弊清单**：

| 威胁 | 防御 |
|---|---|
| 客户端改包声称「我有 999 点」 | C2S 包**只有 lineIndex 与 amount**，所有数值服务端重算；带宽里根本没有「点数字段」可改 |
| 客户端改包声称「我 100 级」 | `totalXp` 只在服务端 Attachment 里；客户端发的任何包都不含经验 |
| 包放大 / 越界 | `lineIndex` 在 [0,26)、`amount` 绝对值不超过 100、`target >= 0`、`cost <= unspentPoints` 四重校验 |
| 连点脚本 / 刷包 | `RateLimiter` 每 20 tick 最多 20 次 spend |
| 洗点刷属性 | 洗点只做 `Arrays.fill(points,0)` + 重算，**不产生任何物品 / 经验** |
| 伪造他人身份 | handler 用 `IPayloadContext.player()`（**服务端拿到的就是真实 `ServerPlayer`**），绝不从包体读 UUID |
| 指令越权 | `requires(s -> s.hasPermission(2))`；`/rpg spend` 只作用于 `ctx.getSource().getPlayerOrException()` 自己 |
| 客户端自算等级（显示欺骗） | 客户端只渲染服务端推来的 `ClientRpgData`；属性值走原版同步通道 |
| Attachment 被客户端修改 | Attachment 只在服务端写；`setData` 只在 `RpgService` / `XpService` 内出现（code review 检查点） |

### 11.4 验证测试面（与 `docs/VERIFICATION.md` 对齐的硬性要求）

> `docs/VERIFICATION.md` `2` 对 architect / code 提出的硬性要求原文：
> 「为了让 26 条线可独立验证，mod 必须提供**服务端指令测试面**……没有这一层，"点数超发""洗点残留""上限收敛"等用例只能靠人肉点击，无法形成可信证据链。」
> 本节是该要求在本设计里的落地方式，**code 必须实现，不是可选项**。

`/rpg stats` / `/rpg debug stats` 的输出契约（服务端权威，绕开 mod 自身 UI）：

```
level=37
total_xp=27410
xp_into_level=410
xp_for_next_level=740
earned_points=111
spent_points=64
unspent_points=47
respec_count=0
line.attack_damage.flat.points=20
line.attack_damage.flat.value=10.0000
line.attack_damage.percent.points=8
line.attack_damage.percent.value=24.0000
line.max_health.flat.points=12
line.max_health.flat.value=12.0000
...（26 条线，每条 2 行，顺序 = StatLine.values() 顺序）

// ---- T6 新增：每个维度的「理论上限 vs 实际落值」（见 1.16.5）----
stat.armor.computed=28.0769          // RpgMath.assembleWithEquipment 算出的含装备理论值
stat.armor.effective=28.0769         // AttributeInstance.getValue() 的实际值
stat.armor.clamped=false             // computed 与 effective 的差 > 1e-6 时为 true
stat.knockback_resistance.computed=0.8455
stat.knockback_resistance.effective=0.8455
stat.knockback_resistance.clamped=false
...（13 个维度各 3 行；clamped=true 表示被原版 sanitizeValue 静默丢弃，是 FAIL 判定依据）
attr.minecraft:generic.attack_damage=26.6400
attr.rpgleveling:crit_chance=12.5000
attr.rpgleveling:damage_reduction=13.0435
...（13 个属性的**最终有效值**，直接读 AttributeInstance.getValue()）
discovery.mine.unlocked=42
discovery.mine.total=1053
discovery.craft.unlocked=17
discovery.craft.total=1420
discovery.smelt.unlocked=9
discovery.smelt.total=1420
discovery.fish.unlocked=3
discovery.fish.total=1420
discovery.trade.unlocked=5
discovery.trade.total=${professionCount * 1000}
```

**为什么必须同时输出 points 与 value 两组**：
- `points` 用于断言「加点是否被服务端正确记录」（防点数超发 / 丢失）。
- `value` 用于断言「曲线与组装公式是否与设计文档第 18 节完全一致」（防公式实现错误）。
- `attr.xxx` 用于断言「AttributeModifier 是否真的挂上了」（防 addOrUpdate 写错 id / 写错 Operation）。
- **三者互相独立**：只看 `points` 会把「点了但没生效」判成通过；只看 `attr` 会把「曲线算错但属性变大」判成通过。

**`/rpg debug setlevel <n>` 的实现要点**：等级是 `totalXp` 的派生值 ⇒ 必须**反解**出 `cumXp(n)` 再赋值，不能单独存一个 level 字段（否则破坏 `PlayerRpgData` 的单一真相原则）。
```java
data.setTotalXpExact(RpgMath.cumulativeXpAtLevel(n));   // 唯一允许直接写 totalXp 的调试入口（权限 2）
data.clearDerivedCache();
RpgService.recomputeAfterBulkChange(player);            // apply + sync
```

#### 11.4.1 调试专用指令：`/rpg debug override`（OP 2 级，**verifier C 的强需求**）

**问题**：verifier 指出「测 `crit_damage` 必须能把暴击率强制到 100%」。但在 `DESIGN_BASELINE` + `SATURATING` 下，暴击率的 `sup = 70%`，
**SAT 严格取不到 cap ⇒ 无论投多少点都到不了 100%**。这不是缺陷，而是 `1` 章的收敛设计的必然结果；但**测试需要 100%**。

**解决**：新增**仅 OP 可用、仅内存不落盘**的 override 指令，绕过曲线直接给一个维度的最终值：

```
/rpg debug override <player> <dim> <value>    -> 权限 2；把该维度的最终值强制为 <value>（跳过曲线与点数）
/rpg debug override <player> <dim> clear      -> 权限 2；清除该维度的 override
/rpg debug override <player> list             -> 权限 2；列出当前全部 override
/rpg debug forcecrit <player> <true|false>    -> 权限 2；强制/取消「每次攻击必定暴击」
```

实现要点：

| 要点 | 做法 |
|---|---|
| 存储 | `RpgService` 内一个 `Map<UUID, EnumMap<Stat, Double>>`，**纯 transient、不写任何 Attachment / NBT** |
| 生效点 | `DerivedStats` 读取维度值时，**先查 override，命中则直接返回 override 值**（曲线与点数被完全绕过） |
| 属性类维度 | override 命中时，用 `addOrUpdateTransientModifier` 写一个专用 id `rpgleveling:override_<stat>`，并把常规两条线的 modifier 暂时移除 |
| `forcecrit` | 在 `CriticalHitEvent` 里 `event.setCriticalHit(true)`（`CriticalHitEvent.java` L82），并在 `getDamageMultiplier()` 上叠加 `crit_damage` |
| 清理 | `PlayerEvent.PlayerLoggedOutEvent` 时清空该玩家的 override；`/rpg debug override clear` 手动清 |
| 防误用 | 启动时若发现任何 override 残留（不可能，因为不落盘），清空并 `LOGGER.warn`；`/rpg debug stats` 输出里带 `debug.override.count=N`，让 verifier 一眼看出是否在调试态 |

**用 `/rpg debug override` 测 `crit_damage` 的标准流程**：

```
/rpg debug override @s crit_chance 100      -> 必定暴击
/rpg debug override @s crit_damage 0        -> 倍率 1.5
对同一只满血僵尸记录伤害 D1（用 /damage 或普通攻击，注意固定武器与附魔）
/rpg debug override @s crit_damage 100      -> 倍率 1.5 + 100/100 = 2.5
再对同种僵尸记录伤害 D2
断言：D2 / D1 == 2.5 / 1.5 == 1.6667（容差 0.01，需排除护甲/附魔影响，建议用无甲目标或 /attribute 归零）
/rpg debug override @s crit_chance clear ; /rpg debug override @s crit_damage clear
```

**GameTest 支持（为 `VERIFICATION.md` 的 L2 层铺路）**：
- `build.gradle` 里保留下段（参考工程已有）：
```groovy
client { systemProperty 'forge.enabledGameTestNamespaces', project.mod_id }
server { systemProperty 'forge.enabledGameTestNamespaces', project.mod_id; argument '--nogui' }
gameTestServer { systemProperty 'forge.enabledGameTestNamespaces', project.mod_id }
```
- mod 侧需要让 `RpgService` / `XpService` 的入口只依赖 `ServerPlayer`（不依赖 `Minecraft` / 客户端类），这样 GameTest 里可以用 `FakePlayer` 直接调 `RpgService.spend()` `XpService.grant()` 并断言 `/rpg debug stats` 的同一份数据。
- **架构约束（重要）**：`RpgService` / `XpService` / `RpgMath` / `AttributeApplier` **禁止 import 任何 `net.minecraft.client.*`**。这是让 26 条线可无头验证的前提。

---

## 12. 配置（ModConfigSpec）

### 12.1 两个 spec

| Spec | `ModConfig.Type` | 内容 |
|---|---|---|
| `RpgConfig.COMMON_SPEC` | `COMMON` | 全局参数 + 26 条线参数（**服务端权威**） |
| `RpgConfig.CLIENT_SPEC` | `CLIENT` | UI 相关：面板透明度、是否显示收敛提示、HUD 开关（默认 false） |

注册（`@Mod` 构造器里）：

```java
public RpgLevelingMod(IEventBus modBus, ModContainer container) {
    RpgAttributes.ATTRIBUTES.register(modBus);
    RpgAttachments.ATTACHMENT_TYPES.register(modBus);
    modBus.addListener(RpgPayloads::onRegisterPayloads);
    modBus.addListener(RpgAttributes::onEntityAttributeModification);
    if (FMLEnvironment.dist.isClient()) com.rpgleveling.client.RpgLevelingClient.init(modBus);
    container.registerConfig(ModConfig.Type.COMMON, RpgConfig.COMMON_SPEC);           // ModContainer 签名
    container.registerConfig(ModConfig.Type.CLIENT, RpgConfig.CLIENT_SPEC);
}
```

### 12.2 全局段（`general`）

| 配置键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `general.max_level` | int | `-1` | 等级上限，-1 = 无上限 |
| `general.strict_convergence` | bool | `true` | true = 有天花板的线走 SAT（推荐）；false = 全部线性（真无上限，风险自负，见 1.13） |
| `general.reapply_interval_ticks` | int | `20` | 修饰符兜底重算周期，0 = 关闭 |
| `general.xp.linear_until_level` | int | `200` | 线性阶段结束等级 |
| `general.xp.linear_base` | double | `100.0` | 线性段截距 B0：`need(L) = B0 + S*(L-1)` |
| `general.xp.linear_step` | double | `20.0` | 线性段斜率 S |
| `general.xp.log_scale` | double | `0.15` | 对数段系数：`need(L) = anchor * (1 + log_scale * ln(L - until + 1))`，anchor = `need(until)` |
| `general.points.per_level` | int | **`1`** | **每级点数（lead 锁定 1 点，所有等级一致）** |
| `general.max_level` | int | `-1` | 等级上限，-1 = 无上限（保持"无上限"） |
| `general.respec.free` | bool | `true` | 免费洗点 |
| `general.respec.cooldown_ticks` | int | `0` | 洗点冷却 |
| `general.hud.enabled` | bool | `false` | 常驻 HUD（**不在需求内**） |

### 12.3 经验来源段（`xp`）

| 配置键 | 默认 | 说明 |
|---|---|---|
| **`xp.discovery.enabled`** | **`true`** | **首次行为奖励总开关（lead 决策 B）** |
| **`xp.discovery.sources`** | `[mine, craft, smelt, fish, trade]` | 启用首次制的来源清单 |
| **`xp.discovery.reward.mine`** | `10` | 首次挖到某方块 |
| **`xp.discovery.reward.craft`** | `15` | 首次合成某物品 |
| **`xp.discovery.reward.smelt`** | `8` | 首次熔炼某物品 |
| **`xp.discovery.reward.fish`** | `20` | 首次钓到某物品 |
| **`xp.discovery.reward.trade`** | `10` | 首次使用某条交易 |
| **`xp.discovery.max_bits_per_source`** | `65536` | 位图硬上限（防存档膨胀） |
| **`xp.discovery.storage`** | `BITSET` | `BITSET` / `STRING_SET` |
| **`xp.discovery.notify`** | `true` | 首次解锁的 actionbar 图鉴提示 |
| **`xp.discovery.creative`** | `false` | 创造模式是否计入挖掘解锁 |
| `xp.kill.reward_mode` | `VANILLA` | `VANILLA` = 用原版掉落值；`FIXED` = 用 `xp.kill.fixed_reward` |
| `xp.kill.fixed_reward` | `5` | `reward_mode = FIXED` 时每次击杀给多少 |
| `xp.count_spawner_mobs` | `false` | 刷怪笼生物是否给经验（防刷怪塔） |
| `xp.minute_cap` | `0` | 滚动 60 秒 XP 上限，0 = 关闭（**首次制生效后这是次要保险**，服务器推荐 3000） |
| `xp.safety.max_per_tick.mine` | `60` | 次要保险：每 tick 挖矿 XP 上限 |
| `xp.safety.max_per_tick.craft` | `40` | 次要保险 |
| `xp.safety.max_per_tick.smelt` | `20` | 次要保险 |
| `xp.advancement.enabled` | `false` | 进度是否给经验 |
| `xp.advancement.reward` | `0` | 每次达成给多少 |

### 12.4 26 条线的配置（表驱动，156 项一次循环生成）

路径规则：`lines.<stat_id>.<flat|percent>.<key>`，例：`lines.attack_damage.flat.per_point`。

每线 6 项：

| key | 类型 | 默认 | 说明 |
|---|---|---|---|
| `enabled` | bool | `true` | 是否启用该线 |
| `curve` | enum `Curve` | 见 1.4 表 | `LINEAR` / `SATURATING` |
| `per_point` | double | 见 1.4 表 | 线性曲线每点增量 |
| `cap` | double | 见 1.4 表 | 饱和曲线的渐近上限（永远取不到） |
| `half_points` | double | 见 1.4 表 | 半饱和点数（仅 `SATURATING` 生效） |
| `gamma` | double | `0.5` | 次线性指数（仅 `SUBLINEAR_POW` 生效；0.5 = sqrt，见 1.2） |
| `basis` | enum `Basis` | **见 `1.16.3`（T6 后的权威默认值）** | **仅 percent 线有效**：`TOTAL` = 原版语义（乘 base+装备+flat）；`DESIGN_BASELINE` = 加 `B_ref * V_pct/100` |
| `basis_value` | double | **T6 权威值：armor=`4.0` / armor_toughness=`3.0` / knockback_resistance=`0.20`**；其余 7 个自定义维度见 `1.14.4`；3 个 TOTAL 维度忽略 | **仅 `basis = DESIGN_BASELINE` 有效**：设计参考基数 `B_ref`（TOTAL 模式下被忽略）。见 `1.16.3` 的余量分配 |
| `max_points` | int | `-1` | 该线点数上限，-1 = 无上限 |

`general.strict_convergence = false` 时，`LineCfg.get()` 会把所有 `curve` 强制读成 `LINEAR`（**不修改配置文件本身**，只是运行时覆盖）。

### 12.5 其他维度专属配置

| 配置键 | 默认 | 说明 |
|---|---|---|
| `health_regen.hard_cap_hps` | **`0.0`** | **T19 起默认关闭**；>0 时才是运行时硬钳（第 1.8 节） |
| `health_regen.only_when_hurt` | `true` | 满血不回 |
| `health_regen.require_food_level` | `0` | 大于 0 则要求饱食度（18 与原版一致） |
| `damage_reduction.extra_bypass_damage_types` | `[]`（空，**只允许追加**） | 额外绕过减伤的伤害类型；`minecraft:generic_kill` 与 `minecraft:out_of_world` 是**硬编码、不可移除**的（见 `1.9` 结论 3.1） |
| `damage_reduction.applies_to_bypass_armor` | `true` | `BYPASSES_ARMOR` 类伤害是否仍吃我们的减伤（默认 true） |
| `movement_speed.hard_cap_attr` | **`0.0`** | **T19 起默认关闭**；>0 时才是运行时硬钳（第 1.7 节） |
| **`stats.report_effective`** | **`true`** | **T6 新增**：`/rpg stats` 是否输出 `<dim>.computed / .effective / .clamped` 三行（见 `1.16.5`）。关闭会让「静默丢点」不可观测，**不建议关** |
| **`knockback_resistance.hard_floor_ratio`** | **`0.0`（关闭）** | **T6 新增**：>0 时在 `LivingKnockBackEvent` 里把击退量下限钳到原值的该比例（`0.05` = 至少保留 5%）。默认关闭（会与外 mod 的击退逻辑打架），仅在属性被 override 或第三方推到 1.0 时才需要 |
| `max_health.heal_on_increase` | `true` | 上限提升时补血 |
| `max_health.heal_mode` | `ADD_DELTA` | `ADD_DELTA` / `KEEP_RATIO` / `NONE` |
| `lifesteal.hps_cap` | `20.0` | 吸血速率上限 |
| `lifesteal.include_thorns` | `false` | 反伤是否吸血 |
| `drop_rate.max_items_per_event` | `256` | 单次掉落事件最大实体数（防卡服） |
| `drop_rate.apply_to_blocks` | `true` | 是否作用于方块掉落 |

**配置重载**：监听 `ModConfigEvent.Reloading` 后遍历所有在线 `ServerPlayer` 调一次 `AttributeApplier.apply` + `RpgNet.syncTo`（否则玩家要重登才生效）。

---

## 13. 包结构与类清单（文件路径级）

```
MC_mod/
├── docs/
│   ├── DESIGN.md                        <- 本文
│   └── DESIGN-API-NOTES.md              <- API 取证附录
├── build.gradle                         (code 角色写域)
├── gradle.properties
├── settings.gradle
└── src/main/
    ├── java/com/rpgleveling/
    │   ├── RpgLevelingMod.java                        @Mod 主类：注册 DeferredRegister、mod 总线监听、registerConfig
    │   ├── RpgConfig.java                             ModConfigSpec：全局段 + 26 线表驱动生成 + LineCfg 快照
    │   ├── api/
    │   │   ├── Stat.java                              13 维度枚举（id / Carrier / playerBase / hardMax）
    │   │   ├── StatLine.java                          26 加点线枚举（stat / mode / curve / 默认参数 / modifierId）
    │   │   ├── Mode.java                              FLAT | PERCENT
    │   │   ├── Curve.java                             LINEAR | SATURATING
    │   │   └── Carrier.java                           13 种承载方式（6 原版属性 + 7 自定义属性）
    │   ├── registry/
    │   │   ├── RpgAttributes.java                     DeferredRegister<Attribute> + 7 个自定义属性 + EntityAttributeModificationEvent
    │   │   ├── RpgAttachments.java                    DeferredRegister<AttachmentType<?>> + PLAYER_DATA / SPAWNER_MOB
    │   │   ├── RpgPayloads.java                       RegisterPayloadHandlersEvent：注册 5 个 payload
    │   │   └── RpgKeyMappings.java                    (client) KeyMapping 常量 + RegisterKeyMappingsEvent
    │   ├── data/
    │   │   ├── PlayerRpgData.java                     玩家 RPG 数据（totalXp + int[26]），INBTSerializable
    │   │   ├── DiscoveryData.java                     首次行为奖励的 id 位图（long[]，见 8.7），INBTSerializable
    │   │   ├── RpgMath.java                           曲线求值 + 等级反查 + 点数公式（纯函数，可 JUnit 测）
    │   │   └── SaturatingCurve.java                   SAT 的反解 / tooltip 文案（1.2 节公式）
    │   ├── stat/
    │   │   ├── LineCfg.java                           record：配置到运行时的不可变快照
    │   │   ├── AttributeApplier.java                  把 26 条线的效果写成 AttributeModifier（幂等）
    │   │   └── DerivedStats.java                      读 13 个属性的有效值（供战斗 / 掉落 / 经验使用）
    │   ├── service/
    │   │   ├── RpgService.java                        服务端唯一权威：spend / respec / onLevelUp / reset
    │   │   ├── XpService.java                         唯一 XP 入账入口（8.1）
    │   │   ├── XpRateLimiter.java                     滚动 60 秒窗口限流 + 每 tick 上限
    │   │   ├── RateLimiter.java                       通用「每 N tick 最多 M 次」限流（防刷包）
    │   │   └── RegenAccumulator.java                  生命恢复的小数累积器（transient，按 UUID）
    │   ├── event/
    │   │   ├── PlayerLifecycleEvents.java             登录 / Clone / 重生 / 换维度 / 登出 -> apply + sync
    │   │   ├── PlayerTickHandler.java                 PlayerTickEvent.Post：兜底重算 + 生命恢复
    │   │   ├── XpEvents.java                          击杀（每次）+ 挖掘/合成/熔炼/钓鱼/交易（仅首次）-> XpService.grant
    │   │   ├── CombatEvents.java                      CriticalHitEvent / LivingDamageEvent.Pre & Post / LivingDropsEvent
    │   │   ├── MobSpawnEvents.java                    FinalizeSpawnEvent：刷怪笼生物打标记
    │   │   └── CommandEvents.java                     RegisterCommandsEvent -> /rpg
    │   ├── net/
    │   │   ├── ClientboundRpgDataPayload.java         S2C：等级 / 经验 / 点数（手写 StreamCodec.of）
    │   │   ├── ClientboundRpgFeedbackPayload.java     S2C：操作失败原因码
    │   │   ├── ServerboundSpendPointPayload.java      C2S：lineIndex + amount
    │   │   ├── ServerboundRespecPayload.java          C2S：空包
    │   │   ├── ServerboundRequestDataPayload.java     C2S：空包
    │   │   ├── ServerPayloadHandler.java              C2S handler，全部委托 RpgService（含全部校验）
    │   │   ├── ClientPayloadHandler.java              S2C handler，写入 ClientRpgData
    │   │   └── RpgNet.java                            syncTo(player) / feedback(player, code) 的发送封装
    │   ├── command/
    │   │   └── RpgCommand.java                        /rpg 及其子命令的 Brigadier 实现
    │   └── client/
    │       ├── RpgLevelingClient.java                 客户端初始化（注册 mod / 游戏总线监听）
    │       ├── KeyBindings.java                       OPEN_RPG KeyMapping 实例
    │       ├── ClientEvents.java                      ClientTickEvent.Post：consumeClick -> 开界面
    │       ├── ClientRpgData.java                     只读镜像 + 断线清理
    │       └── screen/
    │           ├── RpgScreen.java                     主界面（GuiGraphics 渲染 + 26 行 + 滚动 + 洗点按钮）
    │           ├── StatRow.java                       单行（+/- 按钮组 + tooltip）
    │           └── RpgTooltips.java                   tooltip 文案（含收敛提示）
    └── resources/
        ├── META-INF/neoforge.mods.toml                modId=rpgleveling, loaderVersion=[4,), mc=[1.21.1,1.22), neoforge=[21.1.0,)
        ├── pack.mcmeta                                pack_format=34
        └── assets/rpgleveling/
            ├── lang/en_us.json                        英文（26 条线名 + 26 条说明 + GUI + key + 7 个 attribute.name）
            └── lang/zh_cn.json                        中文
```

**类数量合计 36 个**（不含 lang）。**没有** Block / Item / BlockEntity / Menu / Recipe / LootTable —— 本 mod 是纯逻辑 mod，不需要 `data/` 目录。

### 13.1 一句话职责补充（关键类）

| 类 | 一句话职责 |
|---|---|
| `RpgMath` | 所有数值公式的**唯一**实现处；零 Minecraft 依赖（只 import `StrictMath`），可用纯 JUnit 单测覆盖 8.5 节全部采样点 |
| `StatLine` | 26 条线的元数据真相源；新增维度只改这里 + lang |
| `LineCfg` | 配置到运行时的**不可变快照**（每次 `/reload` 后重建），热路径不碰 `ConfigValue` |
| `AttributeApplier` | 唯一往 `AttributeInstance` 写 modifier 的地方；幂等；负责 MAX_HEALTH 补血与运行时硬钳 |
| `XpService` | 唯一 XP 入账处；倍率在这里且只在这里应用 |
| `RpgService` | 唯一写 `PlayerRpgData` 的地方；所有校验在这里 |
| `DerivedStats` | 读 13 个属性的**有效值**（含 `ADD_MULTIPLIED_BASE` 的结果），供战斗逻辑用；战斗代码不直接算曲线 |
| `RpgNet` | 唯一发包处；实现「变化后立即 sync」的一致性 |
| `SaturatingCurve` | 收敛曲线的反解与文案；GUI 与 `/rpg list` 共用 |

---

## 14. 注册表清单

| # | 注册表 | 注册键 / API | 内容 | 注册方式 | 总线 |
|---|---|---|---|---|---|
| 1 | `Registries.ATTRIBUTE` | `DeferredRegister.create(Registries.ATTRIBUTE, "rpgleveling")` | 7 个：`health_regen` `damage_reduction` `crit_chance` `crit_damage` `lifesteal` `drop_rate` `xp_gain` | `DeferredRegister.register(modBus)` | MOD |
| 2 | `NeoForgeRegistries.Keys.ATTACHMENT_TYPES` | `DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "rpgleveling")` | 3 个：`player_data`（serializable + copyOnDeath）、`discovery`（serializable + copyOnDeath）、`spawner_mob`（内存） | 同上 | MOD |
| 3 | `CustomPacketPayload` | `PayloadRegistrar` | 5 个：2 个 S2C + 3 个 C2S | `RegisterPayloadHandlersEvent` | MOD |
| 4 | `EntityType.PLAYER` 的属性表 | `EntityAttributeModificationEvent.add` | 7 个自定义属性 | 事件监听 | MOD |
| 5 | `KeyMapping` | `RegisterKeyMappingsEvent.register` | 1 个：`key.rpgleveling.open`（默认 K） | 事件监听（仅客户端） | MOD |
| 6 | `Commands` | `RegisterCommandsEvent.getDispatcher().register(...)` | `/rpg` 及 4 个子命令 + 4 个 admin 子命令 | 事件监听 | GAME |
| 7 | `Config` | `ModContainer.registerConfig` | `COMMON_SPEC` + `CLIENT_SPEC` | `@Mod` 构造器 | — |
| — | `CreativeModeTab` | — | **不需要**（本 mod 无物品） | — | — |
| — | `MenuType` | — | **不需要**（用纯 Screen） | — | — |
| — | `BlockEntityType` / `EntityType` / `Item` / `Block` | — | **不需要** | — | — |

---

## 15. 分里程碑实施顺序

> 每个里程碑都有**可执行的验收方式**，且都是「能跑起来看到结果」的，不是纸面检查。

### M1 —— 最小可跑通（骨架 + 1 个维度）

| 步骤 | 产出 | 验收方式 |
|---|---|---|
| 1.1 | 工程骨架：`build.gradle` / `gradle.properties` / `settings.gradle` / `neoforge.mods.toml` / `pack.mcmeta` / 主类 | `.\gradlew build` 成功产出 jar；`.\gradlew runClient` 能进主菜单，日志出现 modId `rpgleveling` |
| 1.2 | `Stat` / `StatLine` / `Mode` / `Curve` / `Carrier` / `RpgMath` / `SaturatingCurve` | 写 `RpgMathTest`（纯 JUnit，无 MC 依赖），断言 8.5 节**全部 9 个采样点 + 12 个反查点**逐位相等 |
| 1.3 | `RpgAttributes` 完整注册 7 个自定义属性 + `EntityAttributeModificationEvent` | `runClient` 后用临时指令打印 `player.getAttribute(RpgAttributes.CRIT_CHANCE)` 非 null |
| 1.4 | `PlayerRpgData` + `RpgAttachments.PLAYER_DATA` | 临时指令 `/rpg debug` 打印 `totalXp`；退出重进后值保留（验 `serialize` / `deserialize`） |
| 1.5 | `AttributeApplier` + `PlayerLifecycleEvents`（只处理登录） | 临时指令给 10 点 `attack_damage.flat`，`/attribute @s minecraft:generic.attack_damage get` 显示 base+5.0 |

**M1 完成判据**：能进游戏、属性被正确修改、数据能存盘、`RpgMathTest` 全绿。

### M2 —— 完整属性体系 + 生命周期 + 死亡零惩罚

| 步骤 | 产出 | 验收方式 |
|---|---|---|
| 2.1 | 7 个自定义属性的 lang（zh_cn / en_us） | 属性 tooltip 显示中文名（验证 V1） |
| 2.2 | `RpgConfig` 26 线表驱动生成（156 项） | 首次启动生成 `config/rpgleveling-common.toml`；文件里正好有 26 个 `[lines.*]` 段、每段 6 项 |
| 2.3 | `AttributeApplier` 全 26 线 + 运行时硬钳 + MAX_HEALTH 补血 | `/rpg admin setpoints` 给满点，`/attribute` 逐条查值，与 1.4 表一致 |
| 2.4 | 生命周期事件全覆盖（登录 / Clone / 重生 / 换维度 / 登出）+ 20 tick 兜底 | ① 死亡重生后 `/rpg` 显示等级与点数不变（验证 V3） ② 下界往返后属性仍在 ③ 用 `/attribute` 手工清空后 20 tick 内自动恢复 |
| 2.5 | 洗点 `RpgService.respec` | 加点后 `/rpg respec`，`/rpg` 显示可用点数回到 100%、所有属性回 base |
| 2.6 | `/rpg` `/rpg info` `/rpg list` `/rpg spend` `/rpg respec` `/rpg admin *` | 手动跑一遍全部子命令；越权测试：非 OP 执行 `/rpg admin setlevel` 被拒绝 |

**M2 完成判据**：13 维度 × 2 模式全部可通过指令加点并生效；死亡 / 换维度 / 洗点全部正确。**此时已经是一个功能完整的 mod**（差 UI）。

### M3 —— 经验系统

| 步骤 | 产出 | 验收方式 |
|---|---|---|
| 3.1 | `XpService.grant` + 聊天栏反馈（先不做 UI） | 击杀僵尸，聊天栏显示「+5 XP (5/20)」；等级到 1 时显示「等级提升」 |
| 3.2 | 击杀来源 `LivingExperienceDropEvent` + `MobSpawnEvents` 刷怪笼标记 | ① 杀自然刷新的僵尸给经验 ② 刷怪笼刷的僵尸**不给**经验（`xp.count_spawner_mobs=false`） ③ 自伤（`/damage`）不给经验 |
| 3.3 | 挖矿改为**首次制**：`BlockDropsEvent` + `DiscoveryData` 位图 | ① **第一次**挖到煤矿给 `xp.discovery.reward.mine`（默认 10） ② **第二次**挖同一种方块**给 0 经验**（这是本节最重要的断言） ③ 挖到第 200 种新方块时 `/rpg discovery` 显示 `mine.unlocked=200` ④ 创造模式不给（`xp.discovery.creative=false`） |
| 3.4 | 合成 / 熔炼 / 钓鱼 / 交易同样改首次制 | ① 第一次合成木板给 15；第二次给 0 ② 第一次熔炼铁锭给 8 ③ 第一次钓到鳕鱼给 20 ④ 第一次用某条交易给 10 ⑤ 四条线的 `/rpg discovery` 计数各 +1 且不重复增长 |
| 3.5 | `xp_gain` 维度介入 | `/rpg admin setpoints` 把 `xp_gain.flat` 拉高，验证**首次解锁**得到的经验按 `(0+flat)*(1+pct/100)` 放大 |
| 3.6 | **持久化与防刷验证**（`8.7.4`） | ① 退出重进后 `/rpg discovery` 计数不变、已解锁方块再挖仍给 0 ② 死亡重生后计数不变（`copyOnDeath`） ③ 下界往返后计数不变 ④ 用自动农场 / 漏斗自动合成跑 5 分钟，`totalXp` 增量恒为 0 |
| 3.7 | `XpRateLimiter` 次要保险 | `xp.minute_cap=100` 时连续击杀超过 100 XP 的部分不入账 |
| 3.8 | `earnedPointsAtLevel` 发放点数 | lead 锁定「每级 1 点」⇒ 升到 200 级应有 200 点、500 级 500 点（用 `/rpg debug setlevel` 快速验证） |

**M3 完成判据**：击杀每次给经验、其余 5 个来源**只在首次**给经验且重启 / 死亡 / 换维度后不重复给；倍率只在 `XpService` 生效一次；每级自动发 1 点。

### M4 —— 网络 + GUI

| 步骤 | 产出 | 验收方式 |
|---|---|---|
| 4.1 | 5 个 payload + `StreamCodec` + 注册 | 单人游戏里点击按钮，服务端日志出现 handler 调用（验证内存连接也走 payload） |
| 4.2 | `ServerPayloadHandler` 全部校验 | 用临时调试入口发非法包（`lineIndex=999`、`amount=100000`），确认被拒绝且不崩服 |
| 4.3 | `ClientRpgData` + 登录时 sync | 进入游戏后调试输出显示等级 / 点数与服务端 `/rpg` 一致（验证 V2 / V5） |
| 4.4 | `KeyBindings` + `ClientEvents` + `RegisterKeyMappingsEvent` | 按键设置里出现「RPG 加点」分类与「打开加点界面」（默认 K）；按 K 开界面、Esc 关 |
| 4.5 | `RpgScreen` 完整渲染（头部 + 26 行 + 滚动 + 洗点按钮） | 界面能开、能滚、数字正确；`/rpg list` 与界面显示逐条一致 |
| 4.6 | 点击加 / 减点（含 +10 / -1）+ 乐观更新 | 点 10 次 +1 后 `/rpg` 显示点数减少 10；服务端返回的数据覆盖本地显示无闪烁 |
| 4.7 | tooltip 含收敛提示 | 悬停一行显示「已达上限的 37.5%，再加 143 点到 42.5%」；数字与 `SaturatingCurve` 手算一致 |
| 4.8 | 断线 / 未同步的降级 | 断线时界面自动关闭；进服瞬间开界面显示「正在同步…」 |

**M4 完成判据**：按 K 开界面、随便点、数值与指令一致；非法包不崩服。

### M5 —— 13 维度全量战斗逻辑 + 防作弊硬化

| 步骤 | 产出 | 验收方式 |
|---|---|---|
| 5.1 | `crit_chance` / `crit_damage` 走 `CriticalHitEvent` | 加满暴击率后打怪几乎必暴；暴击倍率 = `1.5 + crit_damage/100`；**服务端只改一次**（验证 V4：观察暴击粒子） |
| 5.2 | `damage_reduction` 走 `LivingDamageEvent.Pre` | ① 减伤 90% 时受到 100 点伤害只掉 10 ② 与护甲叠加不重复：裸装 + 减伤 vs 铁装 + 减伤，两次伤害差 = 护甲管线贡献 |
| 5.3 | `lifesteal` 走 `LivingDamageEvent.Post` | ① 打 1 心僵尸吸血量按实际损失计算（不过度击杀吸血） ② `lifesteal.hps_cap` 生效 |
| 5.4 | `drop_rate` 走 `LivingDropsEvent` + `BlockDropsEvent` | 10 倍掉落时僵尸掉 20 腐肉；超过 64 会分裂成多个 ItemEntity；`max_items_per_event=8` 时被钳住 |
| 5.5 | `movement_speed` 硬钳 | 拉满移速后属性值 = 1.5；在服务器（非单机）里跑动不出现 `moved too quickly!` warn（验证 1.7 结论） |
| 5.6 | `knockback_resistance` 收敛 | 拉满后属性值不超过 0.9，被打仍然后退 |
| 5.7 | `health_regen` 完整实现 | ① HP/s 数值与 `/attribute` 一致 ② 小数累积正确（5 HP/s 时每 4 tick 回 1 点） ③ `only_when_hurt` 生效 ④ 与原版饱食度回血叠加不冲突（饿到 17 时原版停、自定义仍按配置） |
| 5.8 | `RateLimiter` + 违规计数 + 可选踢出 | 用循环发包脚本连续发 1000 个 spend 包，确认服务端限流、日志有 warn、游戏不卡 |
| 5.9 | `ModConfigEvent.Reloading` 热重载 | 改 `cap` 后 `/reload`，所有在线玩家的属性立即变化 |

**M5 完成判据**：13 个维度全部在真实战斗 / 挖掘中可观察；服务器日志无 `moved too quickly`；限流生效。

### M6 —— 打磨与可选扩展

| 步骤 | 产出 | 验收方式 |
|---|---|---|
| 6.1 | 26 条线的 lang 说明文案（zh / en）+ tooltip 完善 | 每条线都有中文名与一句说明，无 Missing localization 警告 |
| 6.2 | 等级提升音效 / 粒子 / 聊天提示 | 升级时有反馈 |
| 6.3 | `/rpg respec undo`（可选） | 洗点后可撤销一次 |
| 6.4 | （可选）`general.hud.enabled` 的 HUD 组件（`RegisterGuiLayersEvent`） | 默认关闭；开启后角落显示等级与可用点数 |
| 6.5 | （可选）突破原版 2048 / 1024 天花板的攻击伤害方案（改走 `LivingIncomingDamageEvent` 倍率） | 明确标注为**超出需求**，需用户二次确认 |
| 6.6 | 平衡性回归：按 8.5 采样表全量对拍 | `/rpg admin addxp` 到 1 / 50 / 100 / 200 / 300 / 500 / 1000 级，断言 level 与 earnedPoints 与表格一致 |

---

## 16. 风险与待用户拍板项

| # | 风险 / 待定 | 影响 | 我的默认处置 | 需要用户决定？ |
|---|---|---|---|---|
| R1 | 攻击伤害在原版 **2048**、生命值在 **1024** 处被硬钳 | 极端高等级时数值不再增长 | 接受并文档化；M6 提供可选突破方案 | 建议确认「接受」 |
| R2 | 「满暴」在原版无法精确达到（SAT 严格小于 cap） | 玩家永远差一点点到 100% 暴击 | 默认 SAT；`strict_convergence=false` 可改线性（但会溢出浪费） | 建议确认「可接受渐近」 |
| R3 | 移动速度上限 1.5（15 倍原版）是否够快 | 影响跑图爽感 | 默认 1.5；可通过 `movement_speed.flat.cap` 调到 0.5（sup 1.6） | 可调，建议先用默认值试玩 |
| R4 | 生命恢复硬钳 20 HP/s（满血 1 秒）是否过强 | 影响战斗难度 | 默认 20；服务器可降到 8 | 建议确认 |
| R5 | 刷怪塔标记用**内存**附件，chunk 卸载会丢 | 少数情况下刷怪笼生物会给经验 | 可接受；另有 `xp.minute_cap` 兜底 | 若严格禁止，M6 改用 `SavedData` |
| R6 | 首次行为奖励的 5 个 reward 数值（10 / 15 / 8 / 20 / 10）是**拍脑袋**的 | 影响升级速度手感 | 全部可配；建议 M3 结束后做一次实机试玩再定 | **建议用户在 M3 后一起调** |
| R7 | `xp.minute_cap` 默认 **0（关闭）** | 首次制生效后它只是次要保险，默认关闭风险已大幅降低 | 服务器推荐 3000 | 建议：单人关闭、服务器开 |
| R11 | **lead 锁定「每级 1 点」** ⟹ 26 条线分摊后每线平均点很少（L200 时 7.7 点/线） | 玩家可能觉得「点数太少、加不动」 | 已把 `1.4` 的 `half_points` 下调到 40..100 补偿（前 20 点即约 20%..30% cap） | **建议 M3 试玩后确认是否需要改 `per_level 或 `half_points** |
| R12 | lead 原文「500 级约 7517」与公式结果 **7573** 不符 | 若 verifier 按 7517 写判定会误判 FAIL | 本文以**公式为准**并在 `8.5` 显式记录差异；要 7517 只需把 `general.xp.log_scale` 从 0.15 调到 0.1486 | **请 verifier 以 `8.5` 的公式值为判定基准** |
| R13 | 首次制位图按**注册表数值 id**索引，mod 列表变化时 id 可能移位 | 最坏情况：少数条目「看起来已解锁」⇒ **少给几点经验**；不会多给、不会崩档 | 存 `modFingerprint` 检测；提供 `storage = STRING_SET` 备选 | 若整合包频繁增删 mod，建议切 `STRING_SET` |
| R14 | 首次制位图存档体积 | 原版约 1000 方块 + 1400 物品 ⇒ 5 个来源合计不到 1 KB/玩家 | `max_bits_per_source = 65536` 硬上限（单来源最多 8 KB） | 无需决定（有硬上限） |
| R15 | 首次制改变了「挖矿 / 合成」的经验手感（从每次给变成一次性） | 与部分玩家的直觉不同 | 完全可配：`xp.discovery.enabled = false` 可退回「每次给 + 每分钟上限」模式 | **建议 M3 试玩后确认手感** |
| **R16** | **T5 裁决**：verifier BL-4「4 维度 percent 恒为 0」**部分不成立** —— 装备是 ADD_VALUE 来源，会被 ADD_MULTIPLIED_BASE 一起放大（证据见 `1.14.1`） | 若按 verifier 原论断改动会把正确的实现改坏 | 已裁决：3 个原版维度保留 TOTAL；**7 个自定义维度改用 DESIGN_BASELINE**（percent 从第 1 点起有效） | **已裁决，无需拍板；但 coder 必须实现 basis 两种模式** |
| **R17** | **T5 裁决**：verifier B-1「护甲 20 点后收益递减」**完全正确** | 玩家会感到「数值涨了体感没变」 | 已文档化：属性 sup=30 但**减伤上限恒为 80%**；提供 `1.15.2` 实测表；GUI 改为显示真实减伤率 | 已裁决 |
| **R18** | `/kill` 必须仍能杀死玩家（verifier B-3 硬性验收项） | 若减伤覆盖 generic_kill，管理员无法处置玩家 | 已硬编码 `GENERIC_KILL` 与 `FELL_OUT_OF_WORLD` 永久绕过，配置**只能追加不能删除**；`1.9` 结论 3.1 给了实现与验收点 | 已裁决 |
| **R19** | BL-3：`damage_reduction` 的 flat 语义（α 百分点 vs β 固定点数） | 两者验收结果完全不同 | **锁定 α**（每点 +1 个百分点）；β 不实现；公式里的「固定吸收 A」已从设计删除 | 已裁决 |
| **R20** | BL-5 取整规则 | 取整位置不一致会让 verifier 对拍产生歧义 | 已给 `1.14.8` 逐维度取整表：曲线/属性层不取整，只在使用处按语义取整 | 已裁决 |
| **R21** | 工具链由 NeoGradle 改为 **ModDevGradle 2.0.81** | 影响 `build.gradle` 与 GameTest 任务写法 | 已在文档头更新声明；GameTest 任务名与系统属性不变，M5.11 验收方式不变 | 已定案（verifier canary） |
| **R22** | **【T6 / BL-9，本轮最严重】** `1.5` 的 sup 公式漏了装备项 ⇒ armor/toughness/KB 的真实 sup 是 **70 / 44 / 1.7**，远超 HARD_MAX 30 / 20 / 1.0；「永远保留 10% 击退」是**假保证** | 核心卖点「点不浪费」被击穿；玩家投几十点数值不动且无提示 | **已修**：`1.16` 给出含装备项的 sup 定义 + 13 维度代入验算；三条线重新配平（armor flat 15→6、B_ref 4.0；toughness flat 10→5、B_ref 3.0；KB flat 0.45→0.35、B_ref 0.20）⇒ 新 sup = 30 / 20 / **0.95**，全部严格满足；并改用 `DESIGN_BASELINE` | **已裁决；coder 必须按新 cap 重写三条线** |
| **R23** | 被 `sanitizeValue` 钳制时**静默无日志** ⇒ 玩家与 verifier 都无法判定 | 「点不浪费」无法兑现也无可验证 | **已修**：`1.16.5` 定义 `computed / effective / clamped` 三行输出 + GUI 红色感叹号 + 首次发生记一次日志；新增配置 `stats.report_effective` | 已裁决 |
| **R24** | 玩家 base（1.0 / 0.1）不等于 Attribute 默认值（2.0 / 0.7） | 取错会让攻击力差 2 倍、移速差 **7 倍** | `1.16.8` 给出 R-BASE-1..4 四条硬规则 + 开发期断言 + verifier 验收命令 | 已裁决 |
| **R25** | NaN 传播（verifier 前提有误：7 个自定义维度**是** Attribute，有 `sanitizeValue` 保护；真正裸奔的是属性体系外的普通 double） | NaN 伤害 / NaN 回血 | `1.16.7` 四道 NaN 防线 + 验收点（把 `half_points` 改成 `nan` 必须记 error 并继续运行） | 已裁决 |
| R8 | 暴击在客户端也 fire `CriticalHitEvent`，只改服务端可能粒子不同步 | 纯视觉 | 先服务端 only，M5 观察（V4） | 无（技术项） |
| R9 | 原版 MAX_HEALTH 上限提升时的补血策略（`ADD_DELTA`）可能被玩家利用来「加点回血」 | 轻微：可当免费治疗 | `max_health.heal_on_increase` 可关；也可改成 `KEEP_RATIO` | 建议保留 ADD_DELTA（手感好） |
| R10 | `.research/neoforge-sources` 研究缓存留在工作区 | 污染仓库 | 建议 `.gitignore` 加 `.research/`，或首次提交前删除 | 建议确认 |

---

## 17. 给 code 角色的实现次序建议（避免返工）

1. **先做 `RpgMath` + `SaturatingCurve` + 单测**（零 MC 依赖）—— 数值全对再往上盖，后面所有 UI / 战斗都依赖它。
2. **再做 `StatLine` 枚举** —— 它是配置、加成、GUI、指令的共同真相源；枚举定好，后面 4 个模块都是循环。
3. **`RpgConfig` 表驱动** —— 一旦 `StatLine` 定了，配置生成是 15 行循环。
4. **`AttributeApplier` 幂等** —— 全 mod 只有这一处写 `AttributeInstance`。
5. **`RpgService` 唯一写入口** —— 全 mod 只有这一处写 `PlayerRpgData`。
6. **`XpService` 唯一入账** —— 全 mod 只有这一处应用 `xp_gain` 倍率。
7. 以上五条是**架构约束**，code review 时逐条检查「有没有第二个地方做同一件事」。

---

## 18. 附：完整可验证公式速查

```
// ---- 曲线 ----
LINEAR(x)              = per_point * x
SAT(x, cap, half)      = cap * x / (x + half)                        // 严格小于 cap
inverseSAT(r, half)    = half * r / (1 - r)                          // r = 目标占 cap 的比例, r < 1

// ---- 维度数值（T6 修正：必须区分 basis 模式）----
TOTAL 模式（3 个维度）        : final = (playerBase + EQUIP + V_flat) * (1 + V_pct/100)
DESIGN_BASELINE 模式（10 个）  : final = playerBase + EQUIP + V_flat + B_ref * V_pct / 100
V_flat                 = LINEAR(x_flat)  或  SAT(x_flat, capFlat, halfFlat)
V_pct                  = LINEAR(x_pct)   或  SAT(x_pct,  capPct,  halfPct)
EQUIP                  = equipment_max(stat)   // 见 1.16.1：armor 20 / toughness 12 / KB 0.4 / attack 9 / 其余 0

// ---- sup（T6 修正：必须含装备项，见 1.16.2）----
sup(stat)              = EQUIP + sup_flat + pct_max_contribution
  DESIGN_BASELINE      : pct_max_contribution = B_ref * pctCap / 100
  TOTAL                : pct_max_contribution = (playerBase + EQUIP + sup_flat) * pctCap / 100
// 三条小余量维度的新 cap（见 1.16.3）
armor          : EQUIP 20 + flat 6 + 4.0*100/100 = 30   <= 30  OK
armor_toughness: EQUIP 12 + flat 5 + 3.0*100/100 = 20   <= 20  OK
knockback      : EQUIP 0.4+ flat 0.35+0.20*100/100= 0.95 <  1.0 OK（永不免疫）

// ---- AttributeModifier ----
flat    -> ADD_VALUE            amount = V_flat
percent -> ADD_MULTIPLIED_BASE  amount = V_pct / 100

// ---- 等级（lead 决策 B 锁定；全部走配置）----
need(L)   = 100 + 20 * (L - 1)                            , 1 <= L <= 200      // 100, 1080, 2080, 4080
need(L)   = round(4080 * (1 + 0.15 * ln(L - 199)))        , L > 200            // 4504 @201, 7573 @500
cumXp(L)  = 10 * L^2 + 90 * L                             , L <= 200           // = 418000 @ L=200
cumXp(L)  = 418000 + SUM(need(k), k = 201..L)             , L > 200
level(xp) = floor((-9 + sqrt(81 + 0.4 * xp)) / 2)         , xp <= 418000
level(xp) = 最大 L 使 cumXp(L) <= xp                       , xp > 418000
// 连续性: need(200) = 4080; need(201) = round(4080*(1+0.15*ln 2)) = 4504  无跳变

// ---- 点数（lead 锁定：每级 1 点）----
points(L) = 1                                             , 所有 L >= 1
earned(L) = L                                             , 所有 L >= 0
unspent   = level - SUM(points[0..25])

// ---- 首次行为奖励（lead 决策 B）----
unlock(src, registryId) : long[] 位图的 O(1) 位测试；返回 true 表示首次 -> 给 reward[src]
总量上界 = SUM_src reward[src] * min(registrySize[src], max_bits_per_source)
// 任何 id 第二次触发 -> 直接 return，零开销、零经验

// ---- 伤害管线（我们在第 9、12 步介入）----
 1 hurt -> 2 isInvulnerableTo(EntityInvulnerabilityCheckEvent)
 3 new DamageContainer -> 4 LivingIncomingDamageEvent -> 5 LivingShieldBlockEvent
 6 actuallyHurt
 7   ARMOR        = getDamageAfterArmorAbsorb(...)          // CombatRules, 有效护甲钳到 20
 8a  MOB_EFFECTS  = 抗性提升药水
 8b  ENCHANTMENTS = 保护附魔
 9  ** LivingDamageEvent.Pre **   <- 基础伤害减免（BL-3 裁决：纯 α 口径，无固定吸收项）
                                    若 source 命中 ALWAYS_BYPASS（generic_kill / out_of_world）-> 完全跳过
                                    否则 D' = D * (1 - R/100)，R ∈ [0, 90)  =>  D' > 0.10 * D > 0
10   ABSORPTION   = 伤害吸收
11   setHealth(getHealth() - newDamage)
12  ** LivingDamageEvent.Post ** <- 生命偷取 H = D_actual * LS/100, D_actual = getNewDamage()

// ---- 生命恢复 ----
regen_hps    = min(hard_cap_hps, statValue(HEALTH_REGEN))
per_tick     = regen_hps / 20
accumulator += per_tick ; 每满 1.0 就 heal(1.0) 并把 1.0 从累积器扣掉

// ---- 经验 ----
xpgain(raw) = round(raw * (1 + statValue(XP_GAIN)/100))
totalXp    += xpgain (经 rolling-60s 限流后)

// ---- percent 作用基数（T5 裁决，见 1.14）----
if (basis == TOTAL)           final = (base + SUM(所有 ADD_VALUE，含装备)) * (1 + V_pct/100)
if (basis == DESIGN_BASELINE) final = base + V_flat + B_ref * V_pct / 100
// 默认：6 个原版属性维度 = TOTAL；7 个自定义属性维度 = DESIGN_BASELINE

// ---- 取整（见 1.14.8；曲线层与属性层一律不取整）----
health_regen / lifesteal : 累积到 >= 1.0 才 heal(floor(acc))，余数留到下 tick
crit_chance              : rng.nextDouble() * 100.0 < critChance
crit_damage              : multiplier = 1.5f + (float)(critDamage / 100.0)
drop_rate                : e = n*m; base = floor(e); new = base + (rng.nextDouble() < e-base ? 1 : 0)
xp_gain                  : Math.round(raw * mult)，负值钳 0
// 恒定不变量（见 6.4）
INV-1: 全工程不得出现 PlayerXpEvent 监听器（grep 必须 0 命中）
INV-2: XP_GAIN 只在 XpService.java 出现一次

// 可观测性不变量（T6 新增，见 1.16.5）
INV-3: /rpg stats 必须对 13 个维度各输出 computed / effective / clamped 三行
INV-4: 原版装备 + 本 mod 加点时 clamped 必须恒为 false（1.16.2 的证明）
INV-5: RpgMath 不得调用 Attribute.getDefaultValue()（见 1.16.8 R-BASE-1）
```