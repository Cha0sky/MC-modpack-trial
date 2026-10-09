# 测试方法对照表（交付说明附件）

> 本文把**验收要求**逐条映射到**实际存在并通过的测试方法**，便于独立复验时按需定位。
> 数字以独立日志为准：
> - JUnit 5（纯 JVM，`gradlew ebTest`）：**83/83**
> - 无头服务器集成（`gradlew cleanGameTestWorld runGameTestServer`，单批次全新世界）：**97/97**
> - 最终源独立复跑：`verification/release-final-independent.log`（83 JUnit / 97 GameTest 全绿）；
>   JarAudit：8 张贴图 / 配方 / 无夹具 / Java 65
> - 真实客户端观感与真机流程由 Codex 复验，证据截图在 `verification/ui-*.png`，
>   完整记录见 **`verification/FINAL-VERIFICATION.md`**；本工程**未运行 GUI**，也不把别人的复验写成本工程的测试。
>
> 所有 dev-only 夹具（`Fixture*`、`dev-only` 标签）都只存在于 `src/gametest`，发布 jar 内已用 ZipFile 扫描确认不存在。
> 交付物：`build/libs/evolvingblade-1.0.0.jar` — **151,792 字节 / 110 条目 /
> SHA256 `8244FD6281B071D38F6818D8EFE45DB6F689FFF0437A5C909422EB62F1803CD5`**。

## 1. 成长 / 门槛 / 计数

| 验收要求 | 测试方法 |
|---|---|
| 三模式八阶段边界、双门槛同时满足 | `PersistenceAcceptanceGameTests.allThreeModesEightStagesThroughRealAttachments` |
| 成长点差 1 或 Boss 差 1 都不得升阶 | `PersistenceAcceptanceGameTests.pointGateAndBossGateAreBothRequired` |
| 终阶强度三模式一致 | `PersistenceAcceptanceGameTests.finalAttackCurveIsIdenticalAcrossAllThreeModes` |
| 门槛/成长点纯数学 | `StageCalculatorTest`、`GrowthDataTest`、`GrowthModeTest`、`GrowthSettingsTest`（JUnit） |
| 普通敌对与 Boss 不重复计数、Boss 独占 100 点 | `BossCompatGameTests.twoRealBossInstancesCountTwiceAndBossPointsAreExclusive`、`KillCreditGameTests.defaultWitherIsBossAndCreditsExactly100Once` |
| 计数饱和/边界 | `GrowthServiceBoundaryTest`（JUnit） |

## 2. Boss 分类与兼容

| 验收要求 | 测试方法 |
|---|---|
| 500 血真实 Husk 仍普通（高血量 ≠ Boss） | `BossCompatGameTests.highHealthRealHuskStaysOrdinary`、`GrowthPersistenceGameTests.wardenAndHighHealthHusksAreOrdinary` |
| 非空 bar 字段（自身/继承/子类类型） | `BossCompatGameTests.serverBossEventBranchesAreRecognized`、`BossMechanismGapGameTests.serverBossEventSubtypeFieldIsInspected` |
| 类名包含 Boss（含继承） | `BossCompatGameTests.classNameBossBranchAndOrdinaryFixture`、`BossMechanismGapGameTests.inheritedClassNameBossWithoutBarIsBoss` |
| null bar 字段 → 普通（除非类名含 Boss） | `BossMechanismGapGameTests.nullBarIsOrdinaryUnlessClassNameSaysBoss` |
| 阈值边界 199 普通 / 200 Boss（含等于） | `BossMechanismGapGameTests.autoDetectThresholdBoundaryIsInclusiveAtTwoHundred` |
| 强制排除优先于包含/自动/标签 | `BossCompatGameTests.forcedExcludeOutranksIncludeAndAutoDetect`、`BossMechanismGapGameTests.forcedExcludeBeatsDevOnlyBossTag` |
| dev-only `c:bosses` 标签生效且不泄漏 | `BossCompatGameTests.devOnlyCommonBossesTagIsHonoured` |
| reason 查询可用 | `BossMechanismGapGameTests.reasonQueriesArePopulatedAndDistinct` |
| 反射失败安全（不崩、跳过） | `BossEventProbeTest`（JUnit） |
| 纯内核规则表 | `BossClassifierCoreTest`（JUnit） |

## 3. 击杀入账 / 死亡语义

| 验收要求 | 测试方法 |
|---|---|
| 终局死亡只入账一次 | `KillCreditGameTests.terminalDeathCreditsExactlyOnce` |
| 长时间尸体不重复入账 | `KillCreditGameTests.duplicateCorpseAfterManyKillsIsNotCredited` |
| 低优先级取消 / 同优先级最末取消 | `KillCreditGameTests.cancelledDeathAtLowPriorityIsNotCredited`、`lateLowestCancelWithZeroHealthIsNotCredited` |
| 活体合成事件不认领后续死亡 | `KillCreditGameTests.liveEntitySyntheticDeathIsNotCredited`、`CodexDeathRegressionGameTests.syntheticAliveEventCannotClaimLaterEnvironmentalDeath` |
| 复活后不得冒领旧候选 | `CodexDeathRegressionGameTests.revivedNonDragonCannotRetainAnOldClaimForUnownedDeath` |
| 环境死亡不给成长 | `KillCreditGameTests.environmentalDeathIsNotCredited` |
| `hasData` 误读不会压制真实击杀 | `CodexDeathRegressionGameTests.readingFalseCreditMarkerCannotSuppressRealKill` |
| 每条命标记写在实体自身 | `BossMechanismGapGameTests.perLifeDedupMarkerIsPerEntity` |
| 凋灵 / 末影龙真实入账 | `KillCreditGameTests.defaultWitherIsBossAndCreditsExactly100Once`、`flyingDragonLethalAttackCreditsOnce`、`immediateKilledRemovalBossIsCredited` |
| 决策表纯逻辑 | `DeathDecisionTest`、`PendingDeathQueueTest`（JUnit） |

## 4. 剑来源 / 击杀回血 / 吸血

| 验收要求 | 测试方法 |
|---|---|
| 用剑击杀回血；弓补刀不给 | `SourceFactGameTests.swordLethalHitGivesKillHeal`、`swordNonlethalThenBowKillGivesNoKillHeal` |
| 技能击杀算剑来源 | `SourceFactGameTests.abilityLethalHitGivesKillHeal` |
| 取消的一击不留来源 | `SourceFactGameTests.cancelledSwordHitLeavesNoSourceFact` |
| FakePlayer 不给成长/回血 | `SourceFactGameTests.fakePlayerSwordKillGivesNoGrowthOrHeal` |
| 阶段 3：真实生命损失 5%（护甲/吸收/全吸收） | `CombatAcceptanceGameTests.lifestealStage3IsFivePercentOfActualHealthLoss`、`lifestealUsesRealPostArmorAndPostAbsorptionLoss` |
| 上限 4 / 8 | `CombatAcceptanceGameTests.lifestealStage5IsCappedAtFour`、`lifestealStage6IsTenPercentCappedAtEight` |
| 未满蓄力 / 次要目标 / 技能不吸血 | `CombatAcceptanceGameTests.underchargedSecondaryAndSkillNeverLifesteal` |
| bonus 不计入吸血、防递归 | `CombatAcceptanceGameTests.lifestealIgnoresBonusDamageAndNeverRecurses` |
| 满蓄力上下文同 tick 有效 | `SourceFactGameTests.underchargedFollowUpGivesNoStaleLifesteal` |

## 5. 斩 Boss 加成

| 验收要求 | 测试方法 |
|---|---|
| 1% / 3% 与上限 200 / 1000 | `CombatAcceptanceGameTests.bossBonusStage5IsOnePercentBelowAndAtTheCap`、`bossBonusFinalStageIsThreePercentBelowAndAtTheCap` |
| 穿甲但不吃主伤护甲 | `CombatAcceptanceGameTests.bossBonusBypassesArmorButMainDamageDoesNot` |
| 每玩家+目标 20 tick 限速、真实时钟 | `CombatAcceptanceGameTests.bossBonusRateLimitUsesRealWorldTicksAndIsPerPlayer` |
| 取消 / Pre 清零 / 无敌 | `CombatAcceptanceGameTests.bossBonusCancelPreZeroAndInvulnerabilityAreHonoured` |
| 抗性（不同玩家/目标消除限速混淆） | `CombatAcceptanceGameTests.bossBonusObeysResistanceWithIndependentPlayerAndTarget` |
| 不写 `invulnerableTime` | `CombatAcceptanceGameTests.mainAndBonusNeverClearInvulnerableTime` |

## 6. 剑气 / 爆发

| 验收要求 | 测试方法 |
|---|---|
| 阶段 2 伤害 24、背后目标不中、射程 23/25 边界、冷却 160 | `WaveBurstAcceptanceGameTests.waveMidStageBehindTargetRangeBoundaryAndCooldown` |
| 终阶 1500、最近 3 个命中（乱序生成）、冷却 80 | `WaveBurstAcceptanceGameTests.waveFinalHitsExactlyThreeNearestRegardlessOfSpawnOrder` |
| 墙体阻挡（含墙角） | `WaveBurstAcceptanceGameTests.waveWallBlocksIncludingNearCorner`、`StageAndWaveGameTests.waveStopsAtWallAndNeverHitsWhatIsBehindIt` |
| 阶段 0/1 未解锁不消耗冷却 | `WaveBurstAcceptanceGameTests.waveLockedAtStageZeroAndOne` |
| 换栈不重置冷却 | `WaveBurstAcceptanceGameTests.waveCooldownIsPerPlayerNotPerItemStack` |
| 真实生命差 24 / 1500 | `WaveBurstAcceptanceGameTests.waveRealHealthDamageOnLivingVictim` |
| 爆发 180 / 2250、半径 6/8、冷却 400/200 | `WaveBurstAcceptanceGameTests.burstRealHealthDamageRadiusAndCooldown` |
| ≥33 目标恰好 32 个命中、半径外安全、方块不变 | `WaveBurstAcceptanceGameTests.burstFinalHitsExactlyThirtyTwoAndKeepsBlocks` |
| 排除玩家/队友/各类宠物（含 owner=null 已驯服） | `WaveBurstAcceptanceGameTests.burstExcludesPlayersAlliesAndAllKindsOfPets` |
| 墙体阻挡 / 未解锁 | `WaveBurstAcceptanceGameTests.burstWallBlocksAndLockedStagesCannotCast` |
| 取消技能伤害、不清 `invulnerableTime` | `WaveBurstAcceptanceGameTests.skillCancellationAndInvulnerableTimeRespected` |
| 真实冷却边界（真实世界 tick） | `WaveBurstAcceptanceGameTests.waveCooldownDeadlineUsesRealWorldTicks`、`burstCooldownDeadlineUsesRealWorldTicks` |
| 末影龙父实体只命中一次 | `WaveBurstAcceptanceGameTests.waveHitsDragonParentExactlyOnce` |
| 配置数值对照 | `WaveBurstAcceptanceGameTests.configuredNumbersMatchSpec` |

## 7. 属性 / 装备 / 多部位

| 验收要求 | 测试方法 |
|---|---|
| 换剑后第一次攻击就是完整阶段总值（无装备 tick） | `ImmediateEquipGameTests.immediateAttackAfterSwapToSwordUsesFullStageTotal` |
| 同一把剑跟随持有者阶段（7→0） | `ImmediateEquipGameTests.sameStackUsesEachHolderStageWithoutDoubleCounting`、`StageAndWaveGameTests.sameStackUsesHolderStageAndUnequipRemovesOnlyOurModifier` |
| 伪造阶段组件不改变强度 | `ImmediateEquipGameTests.forgedStageComponentNeverChangesStrength` |
| 新造剑跟随持有者 | `ImmediateEquipGameTests.freshCraftAdoptsCurrentOwnerStage` |
| 阶段差值代数、第三方叠加、base 不被重置 | `AttributeMathGameTests.*`（5 条） |
| 末影龙 part → 父实体归一化（吸血/bonus/剑来源） | `MultipartAttackGameTests.attackingDragonBodyPartDrivesParentLifestealBonusAndSwordHit`、`attackingDragonHeadIsAlsoNormalisedToTheParent`、`attackIdentityHelperIsBoundedAndSafe` |

## 8. 赠剑 / 选择 / 同步 / 持久化

| 验收要求 | 测试方法 |
|---|---|
| 背包满（生存/创造）保持待发放 | `GiftModeGameTests.fullSurvivalInventoryKeepsGiftPending`、`fullCreativeInventoryKeepsGiftPending` |
| 未选模式拒绝 | `GiftModeGameTests.nullModeIsRejected` |
| 锁定 + 只发一把剑 + 管理员不乱发 | `GrowthPersistenceGameTests.chooseLocksAndGiftIsDeliveredExactlyOnce` |
| 重复选择被拒（生产路径） | `PersistenceAcceptanceGameTests.chooseIntentOnlyCarriesAModeAndDuplicatesAreRejected` |
| 计数变化立刻同步 + 立刻协调属性 | `SyncClockGameTests.counterChangeReconcilesHeldSwordStatsImmediately`、`ordinaryKillUpdatesSnapshotCountersImmediately` |
| 冷却按主世界时钟（含换维度） | `SyncClockGameTests.snapshotCooldownUsesOverworldClockAcrossDimensions` |
| 附件 NBT 往返 / 换维度保留 | `GrowthPersistenceGameTests.attachmentNbtRoundTripPreservesCountersCooldownsAndRateLimit`、`dimensionChangeRetainsGrowthData` |
| 死亡克隆 / 末地返回复制（含冷却与 BOSS_RATE） | `PersistenceAcceptanceGameTests.deathCloneAndEndReturnCopyRealAttachments` |
| 权威快照编解码有界完整 | `PersistenceAcceptanceGameTests.stateSnapshotCodecRoundTripIsBoundedAndComplete` |
| 附件/编解码纯逻辑 | `GiftDeliveryTest`、`BossRateCodecTest`、`CombatPhase2Test`（JUnit） |

## 9. 夹具自检（防止"结构没加载/跨区块不 tick"）

| 验收要求 | 测试方法 |
|---|---|
| `empty` 实际 9×5×9 且中心已加载可 tick | `FixtureGuardGameTests.emptyFixtureCentreIsLoadedAndEntityTicking`、`CodexFixtureGeometryGameTests.emptyHasActualNineByFiveByNineBounds` |
| `boss_arena` 实际 48×32×48 且中心已加载可 tick | `FixtureGuardGameTests.bossArenaFixtureCentreIsLoadedAndEntityTicking`、`CodexFixtureGeometryGameTests.bossArenaHasActualFortyEightByThirtyTwoByFortyEightBounds` |

## 10. 由 Codex 在真实客户端 / 真实游戏进程上复验（不属于本工程自动化测试）

**已由 Codex 完成的外部验证**（实际记录见 `verification/FINAL-VERIFICATION.md`）：

- 最终 jar 在**中文 1920×1080 与 854×480** 两个尺寸验证通过；**英文**面板此前用同一份 GUI 源码在两个尺寸验证过，
  最终只有 `hint` 文案变化。
- 854×480 中英文选择页可读全部 8 阶段门槛（英文可滚动）、中文二次确认、取消不锁定不赠送、八阶段贴图、
  G 面板、首次只发一把剑、阶段 2 剑气实机击杀 8 格外 Husk（ordinary 30→31、growth 30→31）。
- **爆发粒子**截图：`verification/ui-burst-ring-sky.jpg`。
- **真冷启动**（`verification/client-restart-verification.json`）：进程 17980 → 50736；
  同世界重载后 NBT 的 `growth / mode / kills / gift / wave_ready_at / burst_ready_at` 完全相等、剑 1 → 1；
  保存前 burst 剩余 214 tick（10.7 秒），保存/加载期间世界前进 330 tick，故再次打开面板时已合理就绪
  （**不声称**冷却仍在倒计时）。
- **外部安装**：最终 jar 安装到 `D:/PCL 正式版 2.10.6 (1)/.minecraft/versions/mod test/mods/evolvingblade-1.0.0.jar`；
  原 27 个 jar 全部保留；35 模组加载正常；新建 `EvolvingBlade Install QA` 世界，STANDARD + 二次确认锁定、
  获剑、G 面板 Standard / 0 点 / 0 普通 / 0 Boss / GRANTED / 7,1.6 与 50 门槛；未打开原有两个世界。
  最终复审通过：原模组及存档基线 115 个文件 SHA 全部一致，原 `options.txt` 已逐字节恢复；见 `verification/instance-originals-verification.json`、`options-backup.json`。

**仍未验证**（不要写成已完成）：

- **第三方 Boss 模组逐个战斗实测**——未做（只验证了识别机制与阈值边界）。
- **真实联网多人会话**——未做（只做了同一 SERVER 内多个 `ServerPlayer` 的隔离测试）。
- **常规生产 dedicated server 单独启动**——未做（实际启动的是真实 SERVER 发行版 `forgeserverdev` / GameTestServer，97 用例全绿）。
- 1920×1080 与 854×480 之外的其它分辨率——未逐一验证。
- 因此不得声称"已在原存档测试过"或"第三方兼容已全面验证"。
