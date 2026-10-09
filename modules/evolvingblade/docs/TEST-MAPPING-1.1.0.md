# 测试覆盖映射（1.1.0 增量）

> 这是 `docs/TEST-MAPPING.md`（1.0.0 基线）的**增量**部分：
> 只列 1.1.0 新增/改动的覆盖点，基线条目一条都没有删除或弱化。
> 数字以独立执行的日志为准（见 `verification/v2/`）。

## 0. 实际执行结果

| 套件 | 命令 | 结果 |
|---|---|---|
| JUnit 5（纯 JVM） | `gradlew ebTest` | **106 / 106**（基线 83 条全部保留；+14 `EffectiveDamageTest`、+9 `AbilityGeometryTest`） |
| 无头服务器集成 | `gradlew cleanGameTestWorld runGameTestServer` | **115 条**（基线 97 + `BurstSphereGameTests` 6 + `ThirdPartyDamageGameTests` 12）；结果以 Codex 在原工程路径的日志为准，见 `verification/v2/DSH-RECEIPT-ROUND2.md` |

新增 `@Test` 全部在
`src/test/java/com/evolvingblade/combat/EffectiveDamageTest.java`（14 条）
与 `src/test/java/com/evolvingblade/combat/AbilityGeometryTest.java`（9 条）。

## 1. 第三方加伤：真实结算 vs 显示

| 验收要求 | 测试方法 | 类型 |
|---|---|---|
| 面板**不再**把配置值当成全部伤害；阶段基础与持剑有效攻击分开 | `ThirdPartyDamageGameTests.snapshotExposesBothBasesOnlyWhileHolding` | GameTest |
| 未持剑时不得借用别的武器加成（下发 NaN） | `ThirdPartyDamageGameTests.snapshotExposesBothBasesOnlyWhileHolding` | GameTest |
| 事件式第三方（`ItemAttributeModifierEvent`）真的走到玩家属性且不被抹掉 | `ThirdPartyDamageGameTests.itemAttributeEventBonusSurvivesReconcile`（**nonce 标记 + 按测试注册/注销**，不再全局污染） | GameTest |
| 数据组件式第三方倍率只被原版管线应用**一次** | `ThirdPartyDamageGameTests.dataComponentMultiplierAppliesExactlyOnce` | GameTest |
| 同 vanilla 基线 id 被第三方合法改写时，我们**只读不写**，增量完整保留 | `ThirdPartyDamageGameTests.vanillaReservedBaselineIdIsNeverWrittenByUs`、`thirdPartyBaselineIncreaseIsPreservedNotCancelled` | GameTest |
| 首次切剑（装备 tick 还没跑）总量正确，且补偿只生效一次 | `ThirdPartyDamageGameTests.firstSwingWithoutEquipmentTickIsExact` | GameTest |
| 上一把武器（+5）已装备、同 tick 切剑 → 首击必须是完整阶段总值 | `CombatAcceptanceGameTests.immediateAttackAfterSwapToSwordUsesFullStageTotal`（基线用例，未弱化） | GameTest |
| 升级后立即正确、不累加、不覆盖第三方 | `ThirdPartyDamageGameTests.stageUpgradeKeepsThirdPartyAddAndIsImmediate` | GameTest |
| 交换/卸下后**本玩家**没有残留（按玩家查询，不用全局计数） | `ThirdPartyDamageGameTests.swappingAwayLeavesNoResidue` | GameTest |
| 三种原版标准算子（`ADD_VALUE` / `ADD_MULTIPLIED_BASE` / `ADD_MULTIPLIED_TOTAL`）在阶段曲线上正确叠加 | `AttributeMathGameTests.stageTotalThenThirdPartyAddAndMultiplierStack`、`speedMultiplierAppliesNormallyOnTopOfStageCurve` | GameTest（基线，保留） |

> 关于已安装的 `rpgleveling-1.0.0.jar`：Codex 独立 `javap` 实证它的
> `AttributeApplier` 用的是 `Mode.FLAT` → `ADD_VALUE`、`Basis.TOTAL` → **`ADD_MULTIPLIED_BASE`**、
> 设计基线 → `ADD_VALUE`。本模组**不绑定**任何具体第三方模组，只保证自己的差值不动
> base / 第三方 ADD，因此三种算子都能自然叠加。上表用三种算子分别覆盖。
> **DSH 没有把第三方模组装进游戏实测**，仅做字节码层面的只读分析。
>
> 真实伤害与真实生命损失覆盖：
> `ThirdPartyDamageGameTests.standardAttributeBonusAndMultiplierChangeRealAbilityDamage`
> 在模板内（玩家与目标相距 3 格、LOS 断言通过）验证
> 阶段 4 有效 133 → 剑气真实掉血 133、再 ×1.5 → 199.5 → 爆发真实掉血 299.25；
> 之所以用阶段 4 而不是终阶，是因为原版把 `ATTACK_DAMAGE` 钳在 2048。
| 反复协调/升级不会累加；缺席补偿**幂等**（不能读一次就当第三方改动而丢掉） | `AttributeMathGameTests.repeatedReconcileAndEquipNeverAccumulate`、`ThirdPartyDamageGameTests.repeatedReconcileKeepsMissingSlotCompensation` | GameTest |
| 自定义属性 base 不被重置 | `AttributeMathGameTests.customBaseValueIsPreservedAndStageStillRelative` | GameTest（基线，保留） |
| 阶段 0 只移除本模组修饰符、保留原版基线；阶段增量与缺席补偿**分开记账** | `AttributeMathGameTests.stageZeroRemovesOnlyOurModifierAndKeepsBaseline`、`ThirdPartyDamageGameTests.stageZeroKeepsCompensationSeparateFromStageDelta` | GameTest |
| 快照编解码必须保留 1.1.0 新字段 | `PersistenceAcceptanceGameTests.stateSnapshotCodecRoundTripIsBoundedAndComplete` | GameTest（已扩展断言） |

## 2. 技能按有效攻击取值

| 验收要求 | 测试方法 | 类型 |
|---|---|---|
| 技能伤害 = 系数 × 服务端有效攻击（系数是比例、不是阶段天花板） | `EffectiveDamageTest.inheritsEffectiveAttackWithoutStageCeiling`、`ThirdPartyDamageGameTests.abilityDamageTracksEffectiveAttackWithoutStageCeiling` | JUnit + GameTest |
| 标准属性 flat 加伤与乘法加伤必须体现在**真实生命损失**上 | `ThirdPartyDamageGameTests.standardAttributeBonusAndMultiplierChangeRealAbilityDamage` | GameTest |
| 关闭继承后完全回到 1.0.0 口径 | `EffectiveDamageTest.inheritanceOffIgnoresEffectiveAttack` | JUnit |
| 非法有效攻击/系数/阶段值绝不产生 NaN 伤害 | `EffectiveDamageTest.illegalEffectiveAttackFallsBack`、`illegalInputsDegradeToZero` | JUnit |
| 只有防御性绝对上限（9999 有效攻击也不会溢出） | `EffectiveDamageTest.absoluteCapApplies` | JUnit |
| 服务器把 stage0 从 7 改成 12：八阶段+攻速整体生效 | `ThirdPartyDamageGameTests.customStageZeroConfigurationShiftsAllStages` | GameTest |
| 原版 ATTACK_DAMAGE 上限 2048 是事实、未被本模组修改 | `ThirdPartyDamageGameTests.snapshotExposesBothBasesOnlyWhileHolding`（反射读 `RangedAttribute#getMaxValue`） | GameTest |
| 技能不触发吸血/弑王递归、不绕过取消与免疫 | `WaveBurstAcceptanceGameTests.skillCancellationAndInvulnerableTimeRespected`、`CombatAcceptanceGameTests`（基线，保留） | GameTest |

## 3. 爆发三维球

| 验收要求 | 测试方法 | 类型 |
|---|---|---|
| 正上方 / 正下方 / 空中斜上都必须命中，空中球外必须 miss | `BurstSphereGameTests.burstHitsDirectlyAboveAndBelowAndAirborne` | GameTest |
| 贴边大型包围盒（中心在球外、最近点进球）必须命中；球外必须 miss | `BurstSphereGameTests.burstHitsLargeBoxWhoseCornerEntersTheSphere`、`burstHitsBoxNearestPointInsideSphereEvenWhenCentreIsOutside` | GameTest |
| 三轴最近点严格 `<= r²`：贴边命中、多一点不命中、不是 X/Z 圆柱（**每条都带手算距离²**） | `EffectiveDamageTest.boundaryIsInclusive`、`largeBoxCornerInside`、`notACylinder`、`ThirdPartyDamageGameTests.sphereAndEffectiveDamageBoundaries` | JUnit + GameTest |
| 距离平方确实按三轴计算 | `EffectiveDamageTest.distanceIsThreeDimensional`、`directlyAboveAndBelow` | JUnit |
| 非法球心/半径/包围盒一律不命中 | `EffectiveDamageTest.illegalInputsNeverHit` | JUnit |
| 墙体（LOS）在三维下依然阻挡，且**实测 LOS 线路**证明不是地板造成的假象 | `BurstSphereGameTests.burstRespectsWallsInThreeDimensions` | GameTest |
| `cap 32` 在三维球下精确 | `BurstSphereGameTests.burstStillCapsAtThirtyTwoInThreeDimensions`、`WaveBurstAcceptanceGameTests.burstFinalHitsExactlyThirtyTwoAndKeepsBlocks` | GameTest |
| 队友 / 玩家 / 所有驯服生物排除（含正上方） | `BurstSphereGameTests.burstExclusionsHoldInThreeDimensions`、`WaveBurstAcceptanceGameTests.burstExcludesPlayersAlliesAndAllKindsOfPets` | GameTest |
| 真实半径 6 / 终阶 8，终端伤害 180 / 2250 | `WaveBurstAcceptanceGameTests.burstRealHealthDamageRadiusAndCooldown`、`configuredNumbersMatchSpec` | GameTest（基线，保留） |
| 不改变方块（不是爆炸） | `WaveBurstAcceptanceGameTests.burstFinalHitsExactlyThirtyTwoAndKeepsBlocks` | GameTest（基线，保留） |

## 4. 视觉

| 验收要求 | 验证方式 |
|---|---|
| 单次施法每个观察者最多 1 个包 | 代码结构：`AbilityVisuals` 每个效果只调用一次 `AbilityEffectPayload.broadcast`；客户端粒子用 `Level.addParticle`（本地，不联网） |
| 有界：活动效果 ≤ 12、每 tick 粒子 ≤ 24、单效果预算、寿命上限 | `EBAbilityClient` 的常量与 `tick()`；最少画质降采样 |
| 只在同维度且 ≤ 64 格广播 | `AbilityEffectPayload.broadcast` 的距离过滤 |
| 刀光 reach 与真实判定一致 | `AbilityVisuals.wave(..., reach, ...)` 由被墙截断后的 `reach` 驱动 |

> 视觉**观感**属于客户端渲染，无法用 GameTest 断言；本工程没有 GUI 实测，已列入
> 回执的“需人工确认清单”，由 Codex 独立复验。

## 5. 资产

| 验收要求 | 验证方式 |
|---|---|
| 八张母版齐备 | `art/v2/masters/stage0..7.png`（`masters-manifest.json` 记录 SHA-256 与可见包围盒） |
| 导出工具可复现 | `tools/ExportSwordArtwork.java`（纯 JDK，输出 128×128 RGBA + 接触表 + `EXPORT-REPORT.md`） |
| 文本通道备用导出器（沙箱受限时用） | `tools/DumpArtworkBase64.java` |
| alpha 硬阈值与 `art/v2/READY.md` 一致（128） | 两个工具的 `ALPHA_HARD_THRESHOLD = 128`；母版审计同阈值 |
| 工程资源接入后模型属性仍为八阶段 | 模型 JSON 引用同名文件，未改任何 JSON 或代码路径 |

## 6. 特效几何与预算（新增）

| 验收要求 | 测试方法 | 类型 |
|---|---|---|
| 球壳在 14–20 tick 内扩张到**真实半径**、之后不再长大 | `AbilityGeometryTest.burstReachesFullRadiusWithinLifetime` | JUnit |
| 寿命末仍在渲染（不会提前停在很小半径） | `AbilityGeometryTest.stillRenderableAtEndOfLife` | JUnit |
| 非法半径/进度不产生 NaN 顶点 | `AbilityGeometryTest.illegalRadiusDegradesToZero` | JUnit |
| 三组倾角落在不同平面、环点到中心距离恒等于半径 | `AbilityGeometryTest.ringPointsStayOnTheSphereOfRadius`、`threeTiltGroupsAreDistinctPlanes` | JUnit |
| 弯月两端收、中间最宽 | `AbilityGeometryTest.crescentTapersAtBothEnds` | JUnit |
| **每帧顶点硬预算 8192**、多效果共享同一笔预算 | `AbilityGeometryTest.vertexBudgetNeverOverflows`、`vertexBudgetClampsRequests`、`twelveBurstsShareOneFrameBudget` | JUnit |
| 刀光可见路径 = 真实被墙截断的 reach | 服务器把截断后的 reach 作为 `magnitude` 下发（`AbilityVisuals.wave`），客户端从眼睛位置起画 | 代码级 + 待真机 |
| 吸血轨迹飞到施法者真实位置（不再只飞 1 格） | `AbilityEffectPayload.of(..., normalizeDirection=false)` + `EBAbilityClient.buildLifesteal` 用真实长度 | 代码级 + 待真机 |
| 施法事件只为已协商频道的连接发送 | `AbilityEffectPayload.broadcast` 的 `hasChannel(TYPE)` 判断 | 代码级（GameTest 不再抛 `may not be sent`） |
