# PERSISTENCE-ACCEPTANCE 报告

> 本轮含**一处真实生产缺陷修复**：多部位实体（原版末影龙）被攻击时，主手上下文记录的是 part 的 UUID，
> 而原版受伤事件发生在**父实体**上 → 吸血 / 斩 Boss 加成 / 剑来源（击杀回血）全部失效。
> 日志：`verification/persistence-run1.log`（首轮失败）、`persistence-build.log`、`persistence-final.log`（全绿）。

## 1. 生产修复：part → 父实体的身份归一化

**缺陷（Codex 源码复核）**：`EBCombatHandler.onAttackEntity` 存 `event.getTarget().getUUID()`；
`Player.attack(dragon.head/body)` 的目标是 `EnderDragonPart`，但 `LivingDamageEvent` 的受害者是 `EnderDragon`
→ `primary` 判定失败 → 满蓄力吸血、斩 Boss 加成、`SwordHit`（击杀回血来源）都不生效。

**修复**（最小改动，只加一个工具方法 + 一处调用）：
```java
static Entity attackIdentity(Entity target) {
    if (target instanceof net.neoforged.neoforge.entity.PartEntity<?> part) {
        Entity parent = part.getParent();
        if (parent != null) return parent;   // 归一化到真正承受伤害的实体
    }
    return target;                            // 非 part 实体原样返回（语义不变）
}
```
- 使用 NeoForge 的**通用** `PartEntity#getParent()`，不硬编码任何具体 Boss 类型；
- 严格保留原有语义：同 tick、`is_player_attack`、直击实体为玩家、满蓄力、取消即丢弃；
- 不触碰龙的阶段/血量；技能伤害仍走 `isAbilitySource` 早退（无递归加成）。

**回归**（`MultipartAttackGameTests`，全部通过）：
- `attackingDragonBodyPartDrivesParentLifestealBonusAndSwordHit`：真实 `player.attack(parts[1])` →
  父龙真实掉血 **77.5**（原版 body 1/4+1 规则）、**回血 3.8 = 5%（阶段 5，上限 4）的真实损失**、
  `SWORD_HIT` 记在**父龙**上、`BOSS_RATE` 以**父龙 UUID** 为键、`boss_slayer` 事件发生在父龙上、
  龙保持存活且**不在死亡阶段**（未强制阶段/未 setHealth）。
- `attackingDragonHeadIsAlsoNormalisedToTheParent`：头部攻击按满伤害结算（阶段 5 = 300 > 200 血，可能致命），
  因此用**父龙上的剑来源**证明归一化；父龙真实掉血。
- `attackIdentityHelperIsBoundedAndSafe`：非 part 实体必须原样返回。

## 2. 新增测试（17 条，全部通过）

### 三模式 × 八阶段（真实 attachment 路径）
- `allThreeModesEightStagesThroughRealAttachments`：对 3 个模式 × 阶段 1..7，用 `GrowthService.forceMode` +
  `setCounters` 生成合法计数，断言 attachment 里**真实算出的** stage 与"双门槛同时满足"的期望一致，
  且确实 ≥ 目标阶段（不是只测纯 `StageCalculator`）。
- `pointGateAndBossGateAreBothRequired`：终阶两个门槛都满足 → 终阶；**成长点差 1** 或 **Boss 差 1** → 都不进终阶。
- `finalAttackCurveIsIdenticalAcrossAllThreeModes`：三种模式到达终阶后**真实属性**都是 1500 / 2.4。

### 死亡克隆 / 末地返回（真实 Neo 复制路径）
- `deathCloneAndEndReturnCopyRealAttachments`：`newPlayer.restoreFrom(old, false)`（死亡，copyOnDeath）与
  `(old, true)`（末地返回，全部可序列化）两条真实路径都保留成长点/击杀数/模式/赠剑状态/**两个冷却 deadline**/
  `BOSS_RATE` 表；且**不会多发一把剑**（复制后的玩家背包 0 把剑、`requestGift` 保持 GRANTED、
  `chooseMode` 被拒、管理员 `forceMode` 也不发剑）。

### 权威快照与 C2S 意图
- `stateSnapshotCodecRoundTripIsBoundedAndComplete`：快照经 `RegistryFriendlyByteBuf` 编解码往返后，
  标量全等、**3×8 = 24 条模式门槛**、当前模式 8 条门槛、**能力数组恰好 18 个**全部保留，`stagePoints(mode,7)` 可读。
- `chooseIntentOnlyCarriesAModeAndDuplicatesAreRejected`：`ChooseMode` 载荷往返只带一个模式；
  非法 id 被严格解析拒绝；`chooseMode` 首次成功、重复被拒且模式锁定。

### Boss 机制缺口
- `nullBarIsOrdinaryUnlessClassNameSaysBoss`：**null 的 ServerBossEvent 字段**仍按普通（500 血）；
  类名含 Boss 时仍为 Boss。
- `autoDetectThresholdBoundaryIsInclusiveAtTwoHundred`：**199 血 + 非空 bar → 普通**；**200 血 → Boss**。
- `serverBossEventSubtypeFieldIsInspected`：字段类型是 `ServerBossEvent` 的**子类**也被识别。
- `inheritedClassNameBossWithoutBarIsBoss`：**继承来的**类名含 Boss、无 bar → Boss；普通夹具仍普通。
- `forcedExcludeBeatsDevOnlyBossTag`：dev-only `c:bosses` 标签里的 `minecraft:stray` 被判 Boss，
  而**强制排除优先于标签**（同一实体被判普通）。
- `reasonQueriesArePopulatedAndDistinct`：bar 分支报 `SERVER_BOSS_EVENT_FIELD`、类名分支报
  `CLASS_NAME_CONTAINS_BOSS`、普通夹具报非 Boss 原因。
- `perLifeDedupMarkerIsPerEntity` + `taggingDoesNotChangeHostilityClassification`：每条命标记写在实体自身、
  不跨实体泄漏；标签只影响 Boss 判定、不改变"普通敌对"分类。

## 3. 复用既有已接受的覆盖（本轮不重复造）

| 要求 | 既有方法（未改动，仍通过） |
|---|---|
| 死亡取消 / 长时间尸体 / 每命一次 | `CodexDeathRegressionGameTests.revivedNondragonCannotRetainAnOldClaimForUnownedDeath`、`syntheticAliveEventCannotClaimLaterEnvironmentalDeath`、`readingFalseCreditMarkerCannotSuppressRealKill`；`KillCreditGameTests` 的取消/延迟/重复通知用例 |
| 凋灵 / 末影龙真实死亡入账 | 既有击杀入账 GameTest 套件（`defaultwitherisbossandcreditsexactly100once`、`flyingdragonlethalattackcreditsonce`、`immediatekilledremovalbossiscredited` 等） |
| 反射失败安全 | `BossEventProbeTest`（纯 JVM，覆盖反射失败/空字段跳过） |
| 赠剑满背包/创造模式/重复请求 | 既有赠剑 GameTest（pending/GRANTED 与"绝不发第二把"） |
| 同一物品栈 7→0 交换 | `ImmediateEquipGameTests.sameStackUsesEachHolderStageWithoutDoubleCounting` |
| 快照冷却与主世界时钟 | `SyncClockGameTests` 三条 |

## 4. 最终实测

| 项目 | 结果 |
|---|---|
| `gradlew clean build` | **BUILD SUCCESSFUL**；`EBTEST tests=83 succeeded=83 failed=0 aborted=0 skipped=0 containersFailed=0 -> exitCode=0` |
| `gradlew cleanGameTestWorld runGameTestServer` | **`All 97 required tests passed :)`**（上一轮 80，本轮 +17） |
| 冻结 jar | `build/libs/evolvingblade-1.0.0.jar` — **151,811 字节 / 110 条目 / SHA256 `A801BF7DF154D054BA8D5DB101405B6F251A9997245043DE2255A9124A69F405`**（最终 `clean build` + `runGameTestServer` 后冻结；扫描 `unexpected=[]`，无 GameTest/夹具/NBT） |

## 5. 边界（诚实说明）

- **两进程重启**由 Codex 通过 Sky 的"保存世界→退出完整客户端→重启→加载"独立验证；
  本工程**没有**伪造独立 dev server 或把 JsonOps 往返当作重启证据。
- 多部位只覆盖**原版末影龙**；其它模组的 multipart 实体只保证机制通用（走 `PartEntity#getParent()`），
  **未安装任何第三方模组实测**。
- GUI 视觉（仪表盘提示条带等）不在本任务范围，由 Codex 实机复验。
- `FixtureBossWolf` / `LargeHealthFixtureHusk` / 各 `Fixture*Husk` 都是 **gametest 源集**夹具，
  发布 jar 内不含（已用 ZipFile 扫描确认）。
