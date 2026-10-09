# COMBAT-ACCEPTANCE 报告（清单式）

> 冻结产物：`build/libs/evolvingblade-1.0.0.jar` — **149,652 字节 / 109 条目 /
> SHA256 `174F580D614717B4855FBD5DDD11C6174BF8BB04FDCA4F9B2CF0F20D5BB452AD`**（`clean build` 产物；扫描无 GameTest/夹具/NBT）。
> 日志：`verification/combat-acceptance-build.log`（JUnit）、`verification/combat-acceptance-final.log`（最终全绿），
> 以及失败过程原始日志 `combat-acceptance-run1..4.log`（全部保留）。

## 结果

| 项目 | 结果 |
|---|---|
| `gradlew clean build` | **BUILD SUCCESSFUL**，`EBTEST tests=83 succeeded=83 failed=0 aborted=0 skipped=0 containersFailed=0 -> exitCode=0` |
| `gradlew cleanGameTestWorld runGameTestServer` | **`All 63 required tests passed :)`** |

新增 `CombatAcceptanceGameTests`（12 条，全部真实实体 + 真实 `Player.attack`，单 `defaultBatch`）：

### 吸血（按"真实生命值差"测量，非 requestedDamage）
- 阶段 3：实际损失 L → 回血 = 5%·L（实测 L≈47.2 → 回血 2.36，容差 0.05 通过；未触上限）。
- 阶段 5：300 主伤害 → 5%·L 超过上限 → **恰好回 4**。
- 阶段 6：750 主伤害 → 10%·L 超过上限 → **恰好回 8**。
- 护甲：钻石胸甲把真实损失压到 L=1024 → 回血 = min(8, 10%·L)（断言按实际损失，不按请求伤害）。
- 完全吸收：把 `MAX_ABSORPTION` 抬高后 `setAbsorptionAmount(100000)`（源码确认 `setAbsorptionAmount` 会按该属性钳制）→ 真实损失 0 → **回血 0**。
- 取消 `LivingIncomingDamageEvent` → 伤害 0、回血 0；`LivingDamageEvent.Pre` 置 0 → 回血 0（两次都重新蓄力，避免"未满蓄力"造成假通过）。
- 未满蓄力 → 不回血；同一 tick 的**次要目标**（非 PRIMARY 目标）→ 不额外回血；本模组技能伤害 → 不回血。

### 斩 Boss 加成（真实 `Player.attack` 触发 + 超大生命夹具）
- 记录**真实伤害来源与原始数值**：`evolvingblade:boss_slayer`，阶段 5 = **200**（1%×100000=1000 被上限 200 截断），
  终阶 = **1000**（3%×100000=3000 被上限 1000 截断）；并断言总生命损失 > 加成值（确实进入生命）。
- **超大生命夹具** `LargeHealthFixtureHusk`：只覆写 `getAttributeValue(...)` 且在属性为 `MAX_HEALTH` 时返回夹具值
  （Codex 核实的映射源码：`getMaxHealth()` 是 final，但委托给非 final 的 `getAttributeValue(Holder)`）。
  未改全局 RangedAttribute、未覆写 hurt/setHealth/死亡流程、没有伪造掉血。
- 取消 `boss_slayer`（按 UUID + 伤害类型作用域）→ 加成不发生但主伤害仍在（记录到 1000 的原始尝试值）。
- **无敌**：GameTest 的 mock 玩家 `isCreative()` 被覆写为 true，而 NeoForge `Entity.isInvulnerableTo` 里有
  `!source.isCreativePlayer()`——创造玩家伤害无视无敌。因此用**非创造的真实 ServerPlayer**（`abilities.instabuild=false`）
  验证：无敌目标上 **0 个 `boss_slayer` 事件、生命不变**；解除无敌后另一名非创造玩家正常加成并真实掉血。
- **抗性**：Resistance II 下真实生命损失从 2476 → 明显更小（>0 且 < 基准）。
- **限速**：同一玩家+目标 20 tick 内第二次 `false`、不同玩家独立 `true`、目标死亡后 `false`。
- **不清 `invulnerableTime`**：主伤害后该字段仍 >0，加成调用后不减小（本模组从不写该字段）。

## 过程中发现并修正的问题（全部是测试自身的问题，没有产品缺陷需要改）

1. 阶段选错：阶段 5/6 需要 **Boss 门槛 6/12**（我最初只给了 1）→ 用的是阶段 3 的伤害。
2. 回血被玩家**满血上限**截断（tick 期间自然回复把血量顶到 20，看起来像"只回了 7.5"）→ 攻击前统一把玩家血量压到 4。
3. 同一 tick 的第二次攻击是**未满蓄力**（伤害 ×0.2）→ 需要重新 `charge(...)` 后再断言（否则会假通过/误判）。
4. `setAbsorptionAmount` 会按 `MAX_ABSORPTION` 属性钳制（源码确认）→ 夹具先抬高该属性。
5. 直接"两次攻击差值"会被暴击/蓄力差异污染 → 改为按**伤害类型**捕获 `boss_slayer` 的原始数值。
6. 无敌用例最初用创造 mock 玩家 → 被 NeoForge 的 `!isCreativePlayer()` 语义绕过；改用非创造 ServerPlayer。
7. Resistance V 是 100% 减伤（0 伤害）→ 改为 Resistance II。

**没有任何产品逻辑改动**（本轮没有发现需要修的生产缺陷）；`StageAttributes`/`applyBossBonus` 等保持冻结前的实现。

## 诚实边界

- 吸血"上限 4/8"的证明依赖阶段 5/6 的高伤害；上限值本身来自服务器配置。
- 超大生命是**dev-only 夹具**（覆写 `getAttributeValue` 的 MAX_HEALTH 分支），
  **不代表**与真实第三方 Boss 模组做过集成验证——真实模组未安装。
- 本任务**不包含** wave/burst 完整矩阵与重启/持久化场景（后续独立测试任务）。
- 未运行 GUI、未安装、未触碰任何实例；release jar 内无任何 dev 夹具。
