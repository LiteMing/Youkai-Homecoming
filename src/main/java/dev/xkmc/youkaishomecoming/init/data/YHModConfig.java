package dev.xkmc.youkaishomecoming.init.data;

import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisLimits;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

public class YHModConfig {

	public static class Client {

		public final ForgeConfigSpec.BooleanValue laserRenderAdditive;
		public final ForgeConfigSpec.BooleanValue laserRenderInverted;
		public final ForgeConfigSpec.DoubleValue laserTransparency;
		public final ForgeConfigSpec.BooleanValue adaptiveProjectileMesh;
		public final ForgeConfigSpec.IntValue giantSphereBaseSegments;
		public final ForgeConfigSpec.IntValue giantSphereBaseRings;
		public final ForgeConfigSpec.IntValue laserCylinderBaseSegments;
		public final ForgeConfigSpec.DoubleValue farDanmakuFading;
		public final ForgeConfigSpec.DoubleValue selfDanmakuFading;
		public final ForgeConfigSpec.DoubleValue fadingStart;
		public final ForgeConfigSpec.DoubleValue fadingEnd;
		public final ForgeConfigSpec.IntValue powerInfoXAnchor;
		public final ForgeConfigSpec.IntValue powerInfoXOffset;
		public final ForgeConfigSpec.IntValue powerInfoYAnchor;
		public final ForgeConfigSpec.IntValue powerInfoYOffset;

		// Exposure compat: photo overlay display
		public final ForgeConfigSpec.DoubleValue photoOverlayAlpha;
		public final ForgeConfigSpec.DoubleValue photoOverlayScale;
		public final ForgeConfigSpec.IntValue photoOverlayCorner;
		public final ForgeConfigSpec.IntValue photoOverlayDuration;

		Client(ForgeConfigSpec.Builder builder) {
			laserRenderAdditive = builder.comment("Whether laser rendering uses additive blending (brighter)")
					.translation("config.youkaishomecoming.client.laserRenderAdditive")
					.define("laserRenderAdditive", true);
			laserRenderInverted = builder.comment("Whether to invert the laser rendering blend mode")
					.translation("config.youkaishomecoming.client.laserRenderInverted")
					.define("laserRenderInverted", true);
			laserTransparency = builder.comment("Transparency of laser rendering.")
					.translation("config.youkaishomecoming.client.laserTransparency")
					.defineInRange("laserTransparency", 0.5, 0, 1);
			adaptiveProjectileMesh = builder.comment("Adapt giant sphere and cylinder laser mesh detail to projectile visual size.")
					.translation("config.youkaishomecoming.client.adaptiveProjectileMesh")
					.define("adaptiveProjectileMesh", true);
			giantSphereBaseSegments = builder.comment("Base longitude segments for giant sphere danmaku when adaptive mesh is enabled.")
					.translation("config.youkaishomecoming.client.giantSphereBaseSegments")
					.defineInRange("giantSphereBaseSegments", 16, 8, 32);
			giantSphereBaseRings = builder.comment("Base latitude rings for giant sphere danmaku when adaptive mesh is enabled.")
					.translation("config.youkaishomecoming.client.giantSphereBaseRings")
					.defineInRange("giantSphereBaseRings", 8, 4, 16);
			laserCylinderBaseSegments = builder.comment("Base side count for cylindrical laser rendering when adaptive mesh is enabled.")
					.translation("config.youkaishomecoming.client.laserCylinderBaseSegments")
					.defineInRange("laserCylinderBaseSegments", 12, 4, 24);
			farDanmakuFading = builder.comment("Fade factor for distant danmaku.")
					.translation("config.youkaishomecoming.client.farDanmakuFading")
					.defineInRange("farDanmakuFading", 0.5d, 0, 1);
			selfDanmakuFading = builder.comment("Fade factor for self danmaku.")
					.translation("config.youkaishomecoming.client.selfDanmakuFading")
					.defineInRange("selfDanmakuFading", 0.5d, 0, 1);
			fadingStart = builder.comment("Distance where distant danmaku fading begins.")
					.translation("config.youkaishomecoming.client.fadingStart")
					.defineInRange("fadingStart", 8d, 0, 128);
			fadingEnd = builder.comment("Distance where distant danmaku fading completes.")
					.translation("config.youkaishomecoming.client.fadingEnd")
					.defineInRange("fadingEnd", 64d, 0, 128);
			powerInfoXAnchor = builder.comment("Horizontal anchor of the power info overlay.")
					.translation("config.youkaishomecoming.client.powerInfoXAnchor")
					.defineInRange("powerInfoXAnchor", 1, -1, 1);
			powerInfoXOffset = builder.comment("Horizontal offset of the power info overlay.")
					.translation("config.youkaishomecoming.client.powerInfoXOffset")
					.defineInRange("powerInfoXOffset", -8, -1000, 1000);
			powerInfoYAnchor = builder.comment("Vertical anchor of the power info overlay.")
					.translation("config.youkaishomecoming.client.powerInfoYAnchor")
					.defineInRange("powerInfoYAnchor", 0, -1, 1);
			powerInfoYOffset = builder.comment("Vertical offset of the power info overlay.")
					.translation("config.youkaishomecoming.client.powerInfoYOffset")
					.defineInRange("powerInfoYOffset", 0, -1000, 1000);

			builder.translation("config.youkaishomecoming.client.exposure_compat").push("exposure_compat");
			{
				photoOverlayAlpha = builder.comment("Opacity of the photo thumbnail overlay (0=invisible, 1=opaque)")
						.translation("config.youkaishomecoming.client.exposure_compat.photoOverlayAlpha")
						.defineInRange("photoOverlayAlpha", 0.85, 0, 1);
				photoOverlayScale = builder.comment("Scale of the photo thumbnail overlay")
						.translation("config.youkaishomecoming.client.exposure_compat.photoOverlayScale")
						.defineInRange("photoOverlayScale", 0.25, 0.1, 1.0);
				photoOverlayCorner = builder.comment("Corner for photo overlay: 0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right")
						.translation("config.youkaishomecoming.client.exposure_compat.photoOverlayCorner")
						.defineInRange("photoOverlayCorner", 0, 0, 3);
				photoOverlayDuration = builder.comment("Duration (ticks) to display the photo overlay")
						.translation("config.youkaishomecoming.client.exposure_compat.photoOverlayDuration")
						.defineInRange("photoOverlayDuration", 80, 20, 600);
			}
			builder.pop();
		}

	}

	public static class Common {
		public final ForgeConfigSpec.BooleanValue spellMarketEnabled;
		public final ForgeConfigSpec.ConfigValue<String> spellMarketUrl;
		public final ForgeConfigSpec.BooleanValue spellMarketAutoSyncEnabled;
		public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> spellMarketAutoSyncTags;
		public final ForgeConfigSpec.IntValue spellMarketPollMinutes;
		public final ForgeConfigSpec.IntValue spellMarketMaxSpellsPerTag;

		// Certification analysis hard limits (SpellAnalyzer CERTIFICATION profile, Phase 0)
		public final ForgeConfigSpec.IntValue certificationMaxSpawnPerTick;
		public final ForgeConfigSpec.IntValue certificationMaxPeakAlive;
		public final ForgeConfigSpec.LongValue certificationMaxProjectileTicks;
		public final ForgeConfigSpec.LongValue certificationMaxHookExecutions;

		// Certification gameplay (Phase 2)
		public final ForgeConfigSpec.BooleanValue certificationEnabled;
		public final ForgeConfigSpec.IntValue certificationMinDurationTicks;
		public final ForgeConfigSpec.IntValue certificationMaxDurationTicks;
		public final ForgeConfigSpec.ConfigValue<java.util.List<? extends Integer>> certificationDurationPresets;
		public final ForgeConfigSpec.IntValue certificationMinArenaHalfSize;
		public final ForgeConfigSpec.IntValue certificationMaxArenaHalfSize;
		public final ForgeConfigSpec.ConfigValue<java.util.List<? extends Integer>> certificationArenaPresets;
		public final ForgeConfigSpec.IntValue certificationCountdownTicks;
		public final ForgeConfigSpec.DoubleValue certificationRequiredActiveThreatRatio;
		public final ForgeConfigSpec.ConfigValue<String> certificationMovementPolicy;
		public final ForgeConfigSpec.DoubleValue certificationMaxDisplacementPerTick;
		public final ForgeConfigSpec.BooleanValue certificationEnemyRandomMovementEnabled;
		public final ForgeConfigSpec.IntValue certificationEnemyWaypointMinTicks;
		public final ForgeConfigSpec.IntValue certificationEnemyWaypointMaxTicks;
		public final ForgeConfigSpec.DoubleValue certificationEnemyMaxSpeed;
		public final ForgeConfigSpec.DoubleValue certificationEnemyAcceleration;
		public final ForgeConfigSpec.DoubleValue certificationEnemyBoundaryMargin;
		public final ForgeConfigSpec.DoubleValue certificationEnemyMinimumTravelDistance;
		public final ForgeConfigSpec.IntValue certificationMaxConcurrentTrials;
		public final ForgeConfigSpec.IntValue certificationMaxTrialsPerPlayer;
		public final ForgeConfigSpec.LongValue certificationStartCostUnits;
		public final ForgeConfigSpec.DoubleValue certificationRefundOnFailure;
		public final ForgeConfigSpec.DoubleValue certificationMinProofMultiplier;
		public final ForgeConfigSpec.ConfigValue<String> certificationStartPaymentProvider;
		public final ForgeConfigSpec.BooleanValue certificationIssueFeeEnabled;
		public final ForgeConfigSpec.BooleanValue certificationPublicRendering;
		public final ForgeConfigSpec.IntValue certificationRewardOwnerLockTicks;
		public final ForgeConfigSpec.BooleanValue certificationRewardNeverDespawn;

		public final ForgeConfigSpec.IntValue youkaifyingTime;
		public final ForgeConfigSpec.DoubleValue youkaifyingChance;
		public final ForgeConfigSpec.IntValue youkaifyingConfusionTime;
		public final ForgeConfigSpec.IntValue youkaifyingThreshold;
		public final ForgeConfigSpec.IntValue youkaifiedDuration;
		public final ForgeConfigSpec.IntValue youkaifiedProlongation;

		public final ForgeConfigSpec.DoubleValue breathingHealingFactor;
		public final ForgeConfigSpec.IntValue teaHealingPeriod;
		public final ForgeConfigSpec.IntValue udumbaraDuration;
		public final ForgeConfigSpec.IntValue udumbaraHealingPeriod;
		public final ForgeConfigSpec.IntValue udumbaraFullMoonReduction;
		public final ForgeConfigSpec.IntValue higiHealingPeriod;
		public final ForgeConfigSpec.DoubleValue fairyHealingFactor;

		public final ForgeConfigSpec.IntValue frogEatCountForHat;
		public final ForgeConfigSpec.IntValue frogEatRaiderVillagerSightRange;
		public final ForgeConfigSpec.IntValue frogEatRaiderVillagerNoSightRange;
		public final ForgeConfigSpec.BooleanValue koishiAttackEnable;
		public final ForgeConfigSpec.IntValue koishiAttackCoolDown;
		public final ForgeConfigSpec.DoubleValue koishiAttackChance;
		public final ForgeConfigSpec.IntValue koishiAttackDamage;
		public final ForgeConfigSpec.IntValue koishiAttackBlockCount;

		public final ForgeConfigSpec.DoubleValue danmakuMinPHPDamage;
		public final ForgeConfigSpec.DoubleValue danmakuPlayerPHPDamage;
		public final ForgeConfigSpec.DoubleValue danmakuHealOnHitTarget;
		public final ForgeConfigSpec.IntValue playerDanmakuCooldown;
		public final ForgeConfigSpec.IntValue playerLaserCooldown;
		public final ForgeConfigSpec.IntValue playerSpellCooldown;
		public final ForgeConfigSpec.IntValue playerLaserDuration;
		public final ForgeConfigSpec.IntValue spellBombCost;
		public final ForgeConfigSpec.IntValue spellXpCost;
		public final ForgeConfigSpec.BooleanValue lifePaymentEnabled;
		public final ForgeConfigSpec.BooleanValue invulFrameForDanmaku;
		public final ForgeConfigSpec.IntValue danmakuBuffCostTicks;

		public final ForgeConfigSpec.BooleanValue rumiaNaturalSpawn;
		public final ForgeConfigSpec.BooleanValue exRumiaConversion;
		public final ForgeConfigSpec.BooleanValue rumiaDamageCap;
		public final ForgeConfigSpec.BooleanValue rumiaNoTargetHealing;
		public final ForgeConfigSpec.BooleanValue rumiaHairbandDrop;

		public final ForgeConfigSpec.BooleanValue reimuSummonFlesh;
		public final ForgeConfigSpec.BooleanValue reimuSummonKill;
		public final ForgeConfigSpec.BooleanValue reimuSummonMoney;
		public final ForgeConfigSpec.IntValue reimuSummonCost;
		public final ForgeConfigSpec.BooleanValue reimuHairbandFlightEnable;
		public final ForgeConfigSpec.BooleanValue reimuExtraDamageCoolDown;
		public final ForgeConfigSpec.BooleanValue reimuDamageReduction;
		public final ForgeConfigSpec.BooleanValue canReimuTeleportToOtherDimension;

		public final ForgeConfigSpec.BooleanValue cirnoSpawn;
		public final ForgeConfigSpec.DoubleValue cirnoFairyDrop;
		public final ForgeConfigSpec.BooleanValue fairyAttackYoukaified;
		public final ForgeConfigSpec.DoubleValue fairySummonReinforcement;

		public final ForgeConfigSpec.IntValue customSpellMaxDuration;
		public final ForgeConfigSpec.IntValue ringSpellDanmakuPerItemCost;
		public final ForgeConfigSpec.IntValue homingSpellDanmakuPerItemCost;

		public final ForgeConfigSpec.BooleanValue useLegacySpellCards;

		public final ForgeConfigSpec.BooleanValue smallFairyReplacement;
		public final ForgeConfigSpec.BooleanValue smallFairyCanBeBeaten;
		public final ForgeConfigSpec.DoubleValue smallFairySummonReinforcement;
		public final ForgeConfigSpec.DoubleValue smallFairySummonStrongFairy;
		public final ForgeConfigSpec.IntValue smallFairyStrength;

		public final ForgeConfigSpec.IntValue danmakuMaxResource;
		public final ForgeConfigSpec.IntValue danmakuMaxPower;
		public final ForgeConfigSpec.DoubleValue danmakuPowerBonus;
		public final ForgeConfigSpec.DoubleValue grazeEffectiveness;
		public final ForgeConfigSpec.IntValue missInvulTime;
		public final ForgeConfigSpec.IntValue bombInvulTime;
		public final ForgeConfigSpec.DoubleValue maxPowerLossOnMiss;
		public final ForgeConfigSpec.IntValue initialResource;
		public final ForgeConfigSpec.IntValue initialPower;
		public final ForgeConfigSpec.BooleanValue applyBeatenOnDefeat;
		public final ForgeConfigSpec.IntValue beatenDurationTicks;
		public final ForgeConfigSpec.BooleanValue manualDanmakuCombat;

		// Exposure compat
		public final ForgeConfigSpec.IntValue exposureCameraCooldown;
		public final ForgeConfigSpec.BooleanValue exposureDeactivateAfterShot;

		// Shared autonomous dodge — COMMON / youkaishomecoming-common.toml
		public final ForgeConfigSpec.BooleanValue autoDodgeEnabled;
		public final ForgeConfigSpec.DoubleValue autoDodgeScanRadius;
		public final ForgeConfigSpec.IntValue autoDodgeEmergencyCooldown;
		public final ForgeConfigSpec.DoubleValue autoDodgeRescueClearance;
		public final ForgeConfigSpec.DoubleValue autoDodgeInputPriority;
		public final ForgeConfigSpec.DoubleValue autoDodgeAssistPilotWeight;
		public final ForgeConfigSpec.DoubleValue autoDodgeAssistCurrentWeight;
		public final ForgeConfigSpec.DoubleValue autoDodgeAssistSpeedCap;
		public final ForgeConfigSpec.DoubleValue autoDodgeTakeoverMinSpeed;
		public final ForgeConfigSpec.DoubleValue autoDodgeRescuePulseSpeed;
		public final ForgeConfigSpec.DoubleValue autoDodgeRescueJump;
		public final ForgeConfigSpec.DoubleValue autoDodgeTierIHighSpeed;
		public final ForgeConfigSpec.DoubleValue autoDodgeTierILowSpeed;
		public final ForgeConfigSpec.DoubleValue autoDodgeTierIIHighSpeed;
		public final ForgeConfigSpec.DoubleValue autoDodgeTierIILowSpeed;
		public final ForgeConfigSpec.DoubleValue autoDodgeTierIIIHighSpeed;
		public final ForgeConfigSpec.DoubleValue autoDodgeTierIIILowSpeed;
		public final ForgeConfigSpec.IntValue autoDodgeThreatTopK;
		public final ForgeConfigSpec.IntValue autoDodgePredictHorizon;
		public final ForgeConfigSpec.IntValue autoDodgeDebugLogInterval;
		public final ForgeConfigSpec.DoubleValue autoDodgeWallClearanceRadius;
		public final ForgeConfigSpec.DoubleValue autoDodgeWallClearanceGain;
		public final ForgeConfigSpec.DoubleValue autoDodgeWallClearanceDangerDist;
		public final ForgeConfigSpec.DoubleValue autoDodgeWallClearanceSafeDist;
		public final ForgeConfigSpec.DoubleValue previewPilotArenaHalf;
		public final ForgeConfigSpec.BooleanValue youkaiAutoDodgeEnabled;
		public final ForgeConfigSpec.IntValue youkaiAutoDodgeTickInterval;
		public final ForgeConfigSpec.DoubleValue youkaiAutoDodgeScanRadius;
		public final ForgeConfigSpec.DoubleValue youkaiAutoDodgeHighSpeed;
		public final ForgeConfigSpec.DoubleValue youkaiAutoDodgeLowSpeed;
		public final ForgeConfigSpec.DoubleValue youkaiAutoDodgeMaxSpeed;
		public final ForgeConfigSpec.IntValue youkaiAutoDodgeThreatTopK;
		public final ForgeConfigSpec.IntValue youkaiAutoDodgePredictHorizon;
		public final ForgeConfigSpec.DoubleValue youkaiAutoDodgeWallClearanceRadius;
		public final ForgeConfigSpec.DoubleValue youkaiAutoDodgeWallClearanceGain;
		public final ForgeConfigSpec.DoubleValue youkaiAutoDodgeWallClearanceDangerDist;
		public final ForgeConfigSpec.DoubleValue youkaiAutoDodgeWallClearanceSafeDist;

		Common(ForgeConfigSpec.Builder builder) {
			builder.translation("config.youkaishomecoming.common.spell_market").push("spell_market");
			{
				spellMarketEnabled = builder.comment("Enable spell market browsing and server synchronization")
						.translation("config.youkaishomecoming.common.spell_market.enabled")
						.define("enabled", true);
				spellMarketUrl = builder.comment("Spell market API URL. Automatic imports require HTTPS")
						.translation("config.youkaishomecoming.common.spell_market.url")
						.define("url", "http://149.13.91.92/api/v1");
				spellMarketAutoSyncEnabled = builder.comment("Periodically synchronize configured exact tags")
						.translation("config.youkaishomecoming.common.spell_market.auto_sync_enabled")
						.define("auto_sync_enabled", false);
				spellMarketAutoSyncTags = builder.comment("Exact market tags synchronized by the dedicated server")
						.translation("config.youkaishomecoming.common.spell_market.auto_sync_tags")
						.defineListAllowEmpty("auto_sync_tags", java.util.List.of(), o -> o instanceof String s && !s.isBlank());
				spellMarketPollMinutes = builder.comment("Minimum interval between automatic synchronizations")
						.translation("config.youkaishomecoming.common.spell_market.poll_minutes")
						.defineInRange("poll_minutes", 30, 5, 1440);
				spellMarketMaxSpellsPerTag = builder.comment("Maximum number of managed spells imported for one tag")
						.translation("config.youkaishomecoming.common.spell_market.max_spells_per_tag")
						.defineInRange("max_spells_per_tag", 64, 1, 256);
			}
			builder.pop();
			builder.translation("config.youkaishomecoming.common.certification").push("certification");
			{
				certificationMaxSpawnPerTick = builder.comment("Max projectiles any single server tick may spawn during certification")
						.translation("config.youkaishomecoming.common.certification.maxSpawnPerTick")
						.defineInRange("maxSpawnPerTick", SpellAnalysisLimits.DEFAULT_MAX_SPAWN_PER_TICK, 1, 100000);
				certificationMaxPeakAlive = builder.comment("Conservative concurrent-alive projectile upper bound during certification")
						.translation("config.youkaishomecoming.common.certification.maxPeakAlive")
						.defineInRange("maxPeakAlive", SpellAnalysisLimits.DEFAULT_MAX_PEAK_ALIVE, 1, 1000000);
				certificationMaxProjectileTicks = builder.comment("Conservative total projectile-tick upper bound during certification")
						.translation("config.youkaishomecoming.common.certification.maxProjectileTicks")
						.defineInRange("maxProjectileTicks", SpellAnalysisLimits.DEFAULT_MAX_PROJECTILE_TICKS, 1L, 100_000_000_000L);
				certificationMaxHookExecutions = builder.comment("Conservative total hook execution upper bound during certification")
						.translation("config.youkaishomecoming.common.certification.maxHookExecutions")
						.defineInRange("maxHookExecutions", SpellAnalysisLimits.DEFAULT_MAX_HOOK_EXECUTIONS, 1L, 100_000_000_000L);
				certificationEnabled = builder.comment("Master switch for the survival spell certification system")
						.translation("config.youkaishomecoming.common.certification.enabled")
						.define("enabled", true);
				certificationMinDurationTicks = builder.comment("Minimum selectable certification duration in ticks")
						.translation("config.youkaishomecoming.common.certification.minDurationTicks")
						.defineInRange("minDurationTicks", 600, 100, 6000);
				certificationMaxDurationTicks = builder.comment("Maximum selectable certification duration in ticks")
						.translation("config.youkaishomecoming.common.certification.maxDurationTicks")
						.defineInRange("maxDurationTicks", 6000, 600, 60000);
				certificationDurationPresets = builder.comment("Preset certification durations in ticks (editor quick pick)")
						.translation("config.youkaishomecoming.common.certification.durationPresets")
						.defineListAllowEmpty("durationPresets", java.util.List.of(600, 1200, 2400), o -> o instanceof Integer);
				certificationMinArenaHalfSize = builder.comment("Minimum certification arena half size in blocks")
						.translation("config.youkaishomecoming.common.certification.minArenaHalfSize")
						.defineInRange("minArenaHalfSize", 6, 4, 32);
				certificationMaxArenaHalfSize = builder.comment("Maximum certification arena half size in blocks")
						.translation("config.youkaishomecoming.common.certification.maxArenaHalfSize")
						.defineInRange("maxArenaHalfSize", 64, 8, 256);
				certificationArenaPresets = builder.comment("Preset arena half sizes in blocks (editor quick pick)")
						.translation("config.youkaishomecoming.common.certification.arenaPresets")
						.defineListAllowEmpty("arenaPresets", java.util.List.of(6, 8, 12, 16), o -> o instanceof Integer);
				certificationCountdownTicks = builder.comment("PREPARE countdown ticks before ACTIVE begins")
						.translation("config.youkaishomecoming.common.certification.countdownTicks")
						.defineInRange("countdownTicks", 100, 20, 600);
				certificationRequiredActiveThreatRatio = builder.comment("Fraction of ACTIVE ticks that must carry active threat for full duration discount")
						.translation("config.youkaishomecoming.common.certification.requiredActiveThreatRatio")
						.defineInRange("requiredActiveThreatRatio", 0.6, 0.0, 1.0);
				certificationMovementPolicy = builder.comment("Movement policy: CANONICAL (fixed speeds/judge box) or MODPACK (legal equipment and other-mod movement)")
						.translation("config.youkaishomecoming.common.certification.movementPolicy")
						.define("movementPolicy", "CANONICAL");
				certificationMaxDisplacementPerTick = builder.comment("Max allowed player displacement per tick in blocks (illegal move / teleport protection)")
						.translation("config.youkaishomecoming.common.certification.maxDisplacementPerTick")
						.defineInRange("maxDisplacementPerTick", 8.0, 1.0, 64.0);
				certificationEnemyRandomMovementEnabled = builder.comment("Enable server-authoritative bounded random waypoint movement for the certification enemy")
						.translation("config.youkaishomecoming.common.certification.enemyRandomMovementEnabled")
						.define("enemyRandomMovementEnabled", true);
				certificationEnemyWaypointMinTicks = builder.comment("Minimum dwell ticks per waypoint")
						.translation("config.youkaishomecoming.common.certification.enemyWaypointMinTicks")
						.defineInRange("enemyWaypointMinTicks", 40, 5, 600);
				certificationEnemyWaypointMaxTicks = builder.comment("Maximum dwell ticks per waypoint")
						.translation("config.youkaishomecoming.common.certification.enemyWaypointMaxTicks")
						.defineInRange("enemyWaypointMaxTicks", 120, 10, 1200);
				certificationEnemyMaxSpeed = builder.comment("Certification enemy max speed in blocks/tick")
						.translation("config.youkaishomecoming.common.certification.enemyMaxSpeed")
						.defineInRange("enemyMaxSpeed", 0.5, 0.05, 4.0);
				certificationEnemyAcceleration = builder.comment("Certification enemy acceleration lerp factor (0..1 per tick)")
						.translation("config.youkaishomecoming.common.certification.enemyAcceleration")
						.defineInRange("enemyAcceleration", 0.05, 0.01, 1.0);
				certificationEnemyBoundaryMargin = builder.comment("Waypoint safety margin from the arena walls in blocks")
						.translation("config.youkaishomecoming.common.certification.enemyBoundaryMargin")
						.defineInRange("enemyBoundaryMargin", 2.0, 0.0, 16.0);
				certificationEnemyMinimumTravelDistance = builder.comment("Minimum distance between consecutive waypoints in blocks")
						.translation("config.youkaishomecoming.common.certification.enemyMinimumTravelDistance")
						.defineInRange("enemyMinimumTravelDistance", 6.0, 0.0, 32.0);
				certificationMaxConcurrentTrials = builder.comment("Server-wide maximum concurrent certification trials")
						.translation("config.youkaishomecoming.common.certification.maxConcurrentTrials")
						.defineInRange("maxConcurrentTrials", 3, 1, 64);
				certificationMaxTrialsPerPlayer = builder.comment("Max active trials per player (MVP fixed at 1)")
						.translation("config.youkaishomecoming.common.certification.maxTrialsPerPlayer")
						.defineInRange("maxTrialsPerPlayer", 1, 1, 8);
				certificationStartCostUnits = builder.comment("Base certification start fee in abstract cost units")
						.translation("config.youkaishomecoming.common.certification.startCostUnits")
						.defineInRange("startCostUnits", 100L, 0L, 1_000_000L);
				certificationRefundOnFailure = builder.comment("Refund ratio of the start fee for normal No-Hit failure and manual abort (SYSTEM_ERROR always refunds in full)")
						.translation("config.youkaishomecoming.common.certification.refundOnFailure")
						.defineInRange("refundOnFailure", 0.5, 0.0, 1.0);
				certificationMinProofMultiplier = builder.comment("Floor for the proof discount multiplier (design doc §13)")
						.translation("config.youkaishomecoming.common.certification.minProofMultiplier")
						.defineInRange("minProofMultiplier", 0.45, 0.1, 1.0);
				certificationStartPaymentProvider = builder.comment("Payment provider id for the certification start fee")
						.translation("config.youkaishomecoming.common.certification.startPaymentProvider")
						.define("startPaymentProvider", "youkaishomecoming:points");
				certificationIssueFeeEnabled = builder.comment("Whether the certified spell issuance fee is deducted on success (otherwise free)")
						.translation("config.youkaishomecoming.common.certification.issueFeeEnabled")
						.define("issueFeeEnabled", false);
				certificationPublicRendering = builder.comment("Whether other players can spectate certification trials")
						.translation("config.youkaishomecoming.common.certification.publicRendering")
						.define("publicRendering", true);
				certificationRewardOwnerLockTicks = builder.comment("Ticks the reward item stays locked to the creator")
						.translation("config.youkaishomecoming.common.certification.rewardOwnerLockTicks")
						.defineInRange("rewardOwnerLockTicks", 600, 0, 12000);
				certificationRewardNeverDespawn = builder.comment("Certified spell reward items never despawn naturally")
						.translation("config.youkaishomecoming.common.certification.rewardNeverDespawn")
						.define("rewardNeverDespawn", true);
			}
			builder.pop();
			builder.translation("config.youkaishomecoming.common.youkaifying_effect").push("youkaifying_effect");
			{
				youkaifyingChance = builder.comment("Chance for flesh food to add Youkaifying effect for the first time")
						.translation("config.youkaishomecoming.common.youkaifying_effect.youkaifyingChance")
						.defineInRange("youkaifyingChance", 0.2, 0, 1);
				youkaifyingConfusionTime = builder.comment("Confusion time when flesh food to add Youkaifying effect for the first time")
						.translation("config.youkaishomecoming.common.youkaifying_effect.youkaifyingConfusionTime")
						.defineInRange("youkaifyingConfusionTime", 200, 0, 1000000);
				youkaifyingTime = builder.comment("Time for flesh food to add Youkaifying effect")
						.translation("config.youkaishomecoming.common.youkaifying_effect.youkaifyingTime")
						.defineInRange("youkaifyingTime", 1200, 0, 1000000);
				youkaifyingThreshold = builder.comment("Threshold for Youkaifying effect to turn into Youkaified effect")
						.translation("config.youkaishomecoming.common.youkaifying_effect.youkaifyingThreshold")
						.defineInRange("youkaifyingThreshold", 6000, 0, 1000000);
				youkaifiedDuration = builder.comment("Youkaified duration once reached")
						.translation("config.youkaishomecoming.common.youkaifying_effect.youkaifiedDuration")
						.defineInRange("youkaifiedDuration", 24000, 0, 1000000);
				youkaifiedProlongation = builder.comment("Time for flesh food to add Youkaified effect")
						.translation("config.youkaishomecoming.common.youkaifying_effect.youkaifiedProlongation")
						.defineInRange("youkaifiedProlongation", 6000, 0, 1000000);
			}
			builder.pop();

			builder.translation("config.youkaishomecoming.common.food_effect").push("food_effect");
			{
				breathingHealingFactor = builder.comment("Breathing Healing Factor")
						.translation("config.youkaishomecoming.common.food_effect.breathingHealingFactor")
						.defineInRange("breathingHealingFactor", 1.5, 1, 100);
				teaHealingPeriod = builder.comment("Tea Healing Interval")
						.translation("config.youkaishomecoming.common.food_effect.teaHealingPeriod")
						.defineInRange("teaHealingPeriod", 60, 0, 10000);
				udumbaraHealingPeriod = builder.comment("Udumbara effect Healing Interval")
						.translation("config.youkaishomecoming.common.food_effect.udumbaraHealingPeriod")
						.defineInRange("udumbaraHealingPeriod", 60, 0, 10000);
				udumbaraDuration = builder.comment("Udumbara flowering duration")
						.translation("config.youkaishomecoming.common.food_effect.udumbaraDuration")
						.defineInRange("udumbaraDuration", 200, 0, 100000);
				udumbaraFullMoonReduction = builder.comment("Udumbara full moon damage reduction")
						.translation("config.youkaishomecoming.common.food_effect.udumbaraFullMoonReduction")
						.defineInRange("udumbaraFullMoonReduction", 4, 0, 100);
				higiHealingPeriod = builder.comment("Higi Healing Interval")
						.translation("config.youkaishomecoming.common.food_effect.higiHealingPeriod")
						.defineInRange("higiHealingPeriod", 60, 0, 10000);
				fairyHealingFactor = builder.comment("Fairy Healing Factor")
						.translation("config.youkaishomecoming.common.food_effect.fairyHealingFactor")
						.defineInRange("fairyHealingFactor", 2d, 1, 100);
			}
			builder.pop();

			builder.translation("config.youkaishomecoming.common.suwako_hat").push("suwako_hat");
			{
				frogEatCountForHat = builder.comment("Number of raiders with different types frogs need to eat in front of villager to drop Suwako hat")
						.translation("config.youkaishomecoming.common.suwako_hat.frogEatCountForHat")
						.defineInRange("frogEatCountForHat", 3, 1, 10);
				frogEatRaiderVillagerSightRange = builder.comment("Range for villagers with direct sight when frog eat raiders")
						.translation("config.youkaishomecoming.common.suwako_hat.frogEatRaiderVillagerSightRange")
						.defineInRange("frogEatRaiderVillagerSightRange", 20, 1, 64);
				frogEatRaiderVillagerNoSightRange = builder.comment("Range for villagers without direct sight when frog eat raiders")
						.translation("config.youkaishomecoming.common.suwako_hat.frogEatRaiderVillagerNoSightRange")
						.defineInRange("frogEatRaiderVillagerNoSightRange", 10, 1, 64);
			}
			builder.pop();

			builder.translation("config.youkaishomecoming.common.koishi_attack").push("koishi_attack");
			{
				koishiAttackEnable = builder.comment("Enable koishi attack when player has youkaifying or youkaified effect")
						.translation("config.youkaishomecoming.common.koishi_attack.koishiAttackEnable")
						.define("koishiAttackEnable", true);
				koishiAttackCoolDown = builder.comment("Time in ticks for minimum time between koishi attacks")
						.translation("config.youkaishomecoming.common.koishi_attack.koishiAttackCoolDown")
						.defineInRange("koishiAttackCoolDown", 6000, 1, 1000000);
				koishiAttackChance = builder.comment("Chance every tick to do koishi attack")
						.translation("config.youkaishomecoming.common.koishi_attack.koishiAttackChance")
						.defineInRange("koishiAttackChance", 0.001, 0, 1);
				koishiAttackDamage = builder.comment("Koishi attack damage")
						.translation("config.youkaishomecoming.common.koishi_attack.koishiAttackDamage")
						.defineInRange("koishiAttackDamage", 100, 0, 100000000);
				koishiAttackBlockCount = builder.comment("Number of times player needs to consecutively block Koishi attack to get hat")
						.translation("config.youkaishomecoming.common.koishi_attack.koishiAttackBlockCount")
						.defineInRange("koishiAttackBlockCount", 3, 0, 100);
			}
			builder.pop();

			builder.translation("config.youkaishomecoming.common.danmaku_battle").push("danmaku_battle");
			{
				danmakuMinPHPDamage = builder.comment("Minimum damage youkai danmaku will deal against non-player")
						.translation("config.youkaishomecoming.common.danmaku_battle.danmakuMinPHPDamage")
						.defineInRange("danmakuMinPHPDamage", 0.02, 0, 1);
				danmakuPlayerPHPDamage = builder.comment("Minimum damage youkai danmaku will deal against player")
						.translation("config.youkaishomecoming.common.danmaku_battle.danmakuPlayerPHPDamage")
						.defineInRange("danmakuPlayerPHPDamage", 0.1, 0, 1);
				danmakuHealOnHitTarget = builder.comment("When danmaku hits target, heal youkai health by percentage of max health")
						.translation("config.youkaishomecoming.common.danmaku_battle.danmakuHealOnHitTarget")
						.defineInRange("danmakuHealOnHitTarget", 0.2, 0, 1);
				playerDanmakuCooldown = builder.comment("Player item cooldown for using danmaku")
						.translation("config.youkaishomecoming.common.danmaku_battle.playerDanmakuCooldown")
						.defineInRange("playerDanmakuCooldown", 20, 5, 1000);
				playerLaserCooldown = builder.comment("Player item cooldown for using laser")
						.translation("config.youkaishomecoming.common.danmaku_battle.playerLaserCooldown")
						.defineInRange("playerLaserCooldown", 80, 5, 1000);
				playerSpellCooldown = builder.comment("Player item cooldown for using spellcard")
						.translation("config.youkaishomecoming.common.danmaku_battle.playerSpellCooldown")
						.defineInRange("playerSpellCooldown", 40, 5, 1000);
				spellBombCost = builder.comment("Bomb cost to cast a spellcard inside STG danmaku combat")
						.translation("config.youkaishomecoming.common.danmaku_battle.spellBombCost")
						.defineInRange("spellBombCost", 1, 0, 20);
				spellXpCost = builder.comment("XP levels cost to cast a spellcard outside STG danmaku combat")
						.translation("config.youkaishomecoming.common.danmaku_battle.spellXpCost")
						.defineInRange("spellXpCost", 5, 0, 100);
				lifePaymentEnabled = builder.comment("Allow the life payment provider (high-cost overload casting). Never deducted silently by default")
						.translation("config.youkaishomecoming.common.danmaku_battle.lifePaymentEnabled")
						.define("lifePaymentEnabled", false);
				playerLaserDuration = builder.comment("Player laser duration")
						.translation("config.youkaishomecoming.common.danmaku_battle.playerLaserDuration")
						.defineInRange("playerLaserDuration", 100, 5, 1000);
			invulFrameForDanmaku = builder.comment("Enable danmaku damage invulnerability frame against non-player non-youkai mobs.")
					.comment("It's always enabled against player and youkais")
					.translation("config.youkaishomecoming.common.danmaku_battle.invulFrameForDanmaku")
					.define("invulFrameForDanmaku", true);
				danmakuBuffCostTicks = builder.comment("Buff duration (ticks) consumed per danmaku/laser shot when player has youkaified/fairy effect.")
					.comment("Set to 0 to disable buff consumption. Hat bonus bypasses this cost.")
					.translation("config.youkaishomecoming.common.danmaku_battle.danmakuBuffCostTicks")
					.defineInRange("danmakuBuffCostTicks", 40, 0, 10000);
				danmakuMaxResource = builder.comment("Max resource obtainable from danmaku battle")
						.translation("config.youkaishomecoming.common.danmaku_battle.danmakuMaxResource")
						.defineInRange("danmakuMaxResource", 10, 4, 20);
				danmakuMaxPower = builder.comment("Max Power player can obtain from grazing")
						.translation("config.youkaishomecoming.common.danmaku_battle.danmakuMaxPower")
						.defineInRange("danmakuMaxPower", 4, 1, 20);
				danmakuPowerBonus = builder.comment("Danmaku damage each level of power increase")
						.translation("config.youkaishomecoming.common.danmaku_battle.danmakuPowerBonus")
						.defineInRange("danmakuPowerBonus", 0.25, 0, 1);
				grazeEffectiveness = builder.comment("Multiplier for grazing")
						.translation("config.youkaishomecoming.common.danmaku_battle.grazeEffectiveness")
						.defineInRange("grazeEffectiveness", 1d, 0, 10);
				missInvulTime = builder.comment("Danmaku invulnerability and disabled time when you take a hit")
						.translation("config.youkaishomecoming.common.danmaku_battle.missInvulTime")
						.defineInRange("missInvulTime", 60, 10, 100);
				bombInvulTime = builder.comment("Danmaku invulnerability and disabled time when you use a bomb")
						.translation("config.youkaishomecoming.common.danmaku_battle.bombInvulTime")
						.defineInRange("bombInvulTime", 30, 10, 100);
				maxPowerLossOnMiss = builder.comment("Maximum loss of power when you take a hit")
						.translation("config.youkaishomecoming.common.danmaku_battle.maxPowerLossOnMiss")
						.defineInRange("maxPowerLossOnMiss", 1d, 0, 10);
				initialResource = builder.comment("Initial life and bomb when you initiate a danmaku battle")
						.comment("Also is the amount of bomb you get when you lose a life")
						.translation("config.youkaishomecoming.common.danmaku_battle.initialResource")
						.defineInRange("initialResource", 2, 0, 10);
				initialPower = builder.comment("Initial power when you initiate a danmaku battle")
						.translation("config.youkaishomecoming.common.danmaku_battle.initialPower")
						.defineInRange("initialPower", 1, 0, 10);
				applyBeatenOnDefeat = builder.comment("Apply the Beaten effect when the player loses the last life in danmaku combat")
						.comment("Disabled by default; enable for pack-style defeat penalty")
						.translation("config.youkaishomecoming.common.danmaku_battle.applyBeatenOnDefeat")
						.define("applyBeatenOnDefeat", false);
				beatenDurationTicks = builder.comment("Duration in ticks of the Beaten effect applied on danmaku defeat")
						.comment("Only used when applyBeatenOnDefeat is true. 1500 ticks = 75 seconds")
						.translation("config.youkaishomecoming.common.danmaku_battle.beatenDurationTicks")
						.defineInRange("beatenDurationTicks", 1500, 1, 1000000);
				manualDanmakuCombat = builder.comment("When true (default), players must enable STG combat manually (Shift+RMB spell card)")
						.comment("and are not auto-entered by enemy danmaku. When false, restores legacy auto-entry behavior.")
						.translation("config.youkaishomecoming.common.danmaku_battle.manualDanmakuCombat")
						.define("manualDanmakuCombat", true);
			}
			builder.pop();

			builder.translation("config.youkaishomecoming.common.rumia").push("rumia");
			{
				rumiaNaturalSpawn = builder.comment("If Rumia would spawn naturally around her nest if the first one goes too far. Does not affect structure spawn")
						.translation("config.youkaishomecoming.common.rumia.rumiaNaturalSpawn")
						.define("rumiaNaturalSpawn", true);
				exRumiaConversion = builder.comment("Enable Ex Rumia conversion when Rumia takes too high damage in one hit")
						.translation("config.youkaishomecoming.common.rumia.exRumiaConversion")
						.define("exRumiaConversion", true);
				rumiaDamageCap = builder.comment("Allow Rumia to cap incoming damage at a factor of max health")
						.translation("config.youkaishomecoming.common.rumia.rumiaDamageCap")
						.define("rumiaDamageCap", true);
				rumiaNoTargetHealing = builder.comment("Enable Rumia healing when having no target")
						.translation("config.youkaishomecoming.common.rumia.rumiaNoTargetHealing")
						.define("rumiaNoTargetHealing", true);
				rumiaHairbandDrop = builder.comment("Enable Ex Rumia hairband drop")
						.translation("config.youkaishomecoming.common.rumia.rumiaHairbandDrop")
						.define("rumiaHairbandDrop", true);
			}
			builder.pop();

			builder.translation("config.youkaishomecoming.common.reimu").push("reimu");
			{
				reimuSummonFlesh = builder.comment("Summon Reimu when player eats flesh in front of villagers")
						.translation("config.youkaishomecoming.common.reimu.reimuSummonFlesh")
						.define("reimuSummonFlesh", true);
				reimuSummonKill = builder.comment("Summon Reimu when player with youkaified/fying effect kills villager in front of other villagers")
						.translation("config.youkaishomecoming.common.reimu.reimuSummonKill")
						.define("reimuSummonKill", true);
				reimuSummonMoney = builder.comment("Summon Reimu when player throws emerald or gold into donation box")
						.translation("config.youkaishomecoming.common.reimu.reimuSummonMoney")
						.define("reimuSummonMoney", true);
				reimuSummonCost = builder.comment("Cost of emerald/gold to summon Reimu")
						.translation("config.youkaishomecoming.common.reimu.reimuSummonCost")
						.defineInRange("reimuSummonCost", 8, 1, 100000);
				reimuHairbandFlightEnable = builder.comment("Enable creative flight on Reimu hairband")
						.translation("config.youkaishomecoming.common.reimu.reimuHairbandFlightEnable")
						.define("reimuHairbandFlightEnable", true);
				reimuExtraDamageCoolDown = builder.comment("Enable non-danmaku extra damage cooldown on Reimu")
						.translation("config.youkaishomecoming.common.reimu.reimuExtraDamageCoolDown")
						.define("reimuExtraDamageCoolDown", true);
				reimuDamageReduction = builder.comment("Enable non-danmaku damage reduction on Reimu")
						.translation("config.youkaishomecoming.common.reimu.reimuDamageReduction")
						.define("reimuDamageReduction", true);
				canReimuTeleportToOtherDimension = builder.comment("If Reimu can be teleported to other dimension")
						.translation("config.youkaishomecoming.common.reimu.canReimuTeleportToOtherDimension")
						.define("canReimuTeleportToOtherDimension", false);
			}
			builder.pop();

			builder.translation("config.youkaishomecoming.common.cirno").push("cirno");
			{
				cirnoSpawn = builder.comment("Toggle for Cirno natural spawns")
						.translation("config.youkaishomecoming.common.cirno.cirnoSpawn")
						.define("cirnoSpawn", true);
				cirnoFairyDrop = builder.comment("Chance for fairy ice crystal to drop")
						.translation("config.youkaishomecoming.common.cirno.cirnoFairyDrop")
						.defineInRange("cirnoFairyDrop", 0.03, 0, 1);
				fairyAttackYoukaified = builder.comment("Fairies will actively attack players with youkaifying/ed effects")
						.translation("config.youkaishomecoming.common.cirno.fairyAttackYoukaified")
						.define("fairyAttackYoukaified", true);
				fairySummonReinforcement = builder.comment("Chance for fairies to summon other fairies when killed by non-danmaku damage")
						.translation("config.youkaishomecoming.common.cirno.fairySummonReinforcement")
						.defineInRange("fairySummonReinforcement", 0.5, 0, 1);
			}
			builder.pop();

			builder.translation("config.youkaishomecoming.common.custom_spell").push("custom_spell");
			{
				customSpellMaxDuration = builder.comment("Max duration of custom spell allowed")
						.translation("config.youkaishomecoming.common.custom_spell.customSpellMaxDuration")
						.defineInRange("customSpellMaxDuration", 60, 60, 1000);
				ringSpellDanmakuPerItemCost = builder.comment("Ring Spell: Max number of bullet allowed per item cost")
						.translation("config.youkaishomecoming.common.custom_spell.ringSpellDanmakuPerItemCost")
						.defineInRange("ringSpellDanmakuPerItemCost", 32, 1, 1024);
				homingSpellDanmakuPerItemCost = builder.comment("Homing Spell: Max number of bullet allowed per item cost")
						.translation("config.youkaishomecoming.common.custom_spell.homingSpellDanmakuPerItemCost")
						.defineInRange("homingSpellDanmakuPerItemCost", 8, 1, 1024);
			}
			builder.pop();

			builder.translation("config.youkaishomecoming.common.spell_migration").push("spell_migration");
			{
				useLegacySpellCards = builder.comment("Fallback to legacy Java SpellCard classes instead of data-driven migrated versions.")
						.comment("Read at startup — restart required to apply.")
						.translation("config.youkaishomecoming.common.spell_migration.useLegacySpellCards")
						.define("useLegacySpellCards", false);
			}
			builder.pop();

			builder.translation("config.youkaishomecoming.common.touhou_little_maid").push("touhou_little_maid");
			{
				smallFairyReplacement = builder.comment("Replace Fairies from Touhou Little Maid with a neutral fairy")
						.translation("config.youkaishomecoming.common.touhou_little_maid.smallFairyReplacement")
						.define("smallFairyReplacement", false);
				smallFairyCanBeBeaten = builder.comment("Allow small fairies to enter the Beaten state instead of dying")
						.translation("config.youkaishomecoming.common.touhou_little_maid.smallFairyCanBeBeaten")
						.define("smallFairyCanBeBeaten", false);
				smallFairySummonReinforcement = builder.comment("Chance for small fairies to summon other fairies when killed by non-danmaku damage")
						.translation("config.youkaishomecoming.common.touhou_little_maid.smallFairySummonReinforcement")
						.defineInRange("smallFairySummonReinforcement", 0.25, 0, 1);
				smallFairySummonStrongFairy = builder.comment("Chance for small fairies to summon stronger fairies when they are set to summon reinforcements")
						.translation("config.youkaishomecoming.common.touhou_little_maid.smallFairySummonStrongFairy")
						.defineInRange("smallFairySummonStrongFairy", 0.1, 0, 1);
				smallFairyStrength = builder.comment("Small Fairy spellcard strength")
						.translation("config.youkaishomecoming.common.touhou_little_maid.smallFairyStrength")
						.defineInRange("smallFairyStrength", 2, 0, 4);
			}
			builder.pop();

			builder.translation("config.youkaishomecoming.common.exposure_compat").push("exposure_compat");
			{
				exposureCameraCooldown = builder.comment("Cooldown (ticks) applied to camera after photographing danmaku")
						.translation("config.youkaishomecoming.common.exposure_compat.exposureCameraCooldown")
						.defineInRange("exposureCameraCooldown", 40, 0, 600);
				exposureDeactivateAfterShot = builder.comment("Whether to exit viewfinder after photographing danmaku")
						.translation("config.youkaishomecoming.common.exposure_compat.exposureDeactivateAfterShot")
						.define("exposureDeactivateAfterShot", true);
			}
			builder.pop();

			builder.translation("config.youkaishomecoming.common.auto_dodge").push("auto_dodge");
			{
				builder.comment("Autonomous dodge shared by preview, players and live youkai entities.",
						"Player movement is local-client authoritative; keep client values aligned on multiplayer.");
				autoDodgeEnabled = builder.comment("Master switch for player auto-dodge buff logic")
						.translation("config.youkaishomecoming.common.auto_dodge.enabled")
						.define("enabled", true);
				autoDodgeScanRadius = builder.comment("Threat scan radius in blocks (world projectiles + client danmaku cache)")
						.translation("config.youkaishomecoming.common.auto_dodge.scanRadius")
						.defineInRange("scanRadius", 16.0, 4.0, 48.0);
				autoDodgeEmergencyCooldown = builder.comment("Cooldown ticks after a rescue (tier I) pulse")
						.translation("config.youkaishomecoming.common.auto_dodge.emergencyCooldown")
						.defineInRange("emergencyCooldown", 4, 0, 40);
				autoDodgeRescueClearance = builder.comment("Tier I: only act when min clearance is at or below this")
						.translation("config.youkaishomecoming.common.auto_dodge.rescueClearance")
						.defineInRange("rescueClearance", 1.25, 0.1, 8.0);
				autoDodgeInputPriority = builder.comment("Tier II: player input length above this prefers steering over pilot")
						.translation("config.youkaishomecoming.common.auto_dodge.inputPriority")
						.defineInRange("inputPriority", 0.25, 0.0, 2.0);
				autoDodgeAssistPilotWeight = builder.comment("Tier II: blend weight of pilot velocity when idle (0-1)")
						.translation("config.youkaishomecoming.common.auto_dodge.assistPilotWeight")
						.defineInRange("assistPilotWeight", 0.65, 0.0, 1.0);
				autoDodgeAssistCurrentWeight = builder.comment("Tier II: blend weight of current velocity when idle (0-1)")
						.translation("config.youkaishomecoming.common.auto_dodge.assistCurrentWeight")
						.defineInRange("assistCurrentWeight", 0.35, 0.0, 1.0);
				autoDodgeAssistSpeedCap = builder.comment("Tier II: max horizontal speed while assisting")
						.translation("config.youkaishomecoming.common.auto_dodge.assistSpeedCap")
						.defineInRange("assistSpeedCap", 0.28, 0.05, 1.5);
				autoDodgeTakeoverMinSpeed = builder.comment("Tier III: boost horizontal speed up to at least this when non-zero")
						.translation("config.youkaishomecoming.common.auto_dodge.takeoverMinSpeed")
						.defineInRange("takeoverMinSpeed", 0.35, 0.05, 1.5);
				autoDodgeRescuePulseSpeed = builder.comment("Tier I fallback horizontal kick speed")
						.translation("config.youkaishomecoming.common.auto_dodge.rescuePulseSpeed")
						.defineInRange("rescuePulseSpeed", 0.4, 0.05, 1.5);
				autoDodgeRescueJump = builder.comment("Tier I fallback upward impulse")
						.translation("config.youkaishomecoming.common.auto_dodge.rescueJump")
						.defineInRange("rescueJump", 0.2, 0.0, 1.0);
				autoDodgeTierIHighSpeed = builder.comment("Tier I pilot profile high speed")
						.translation("config.youkaishomecoming.common.auto_dodge.tierIHighSpeed")
						.defineInRange("tierIHighSpeed", 0.25, 0.05, 2.0);
				autoDodgeTierILowSpeed = builder.comment("Tier I pilot profile low speed")
						.translation("config.youkaishomecoming.common.auto_dodge.tierILowSpeed")
						.defineInRange("tierILowSpeed", 0.12, 0.02, 1.0);
				autoDodgeTierIIHighSpeed = builder.comment("Tier II pilot profile high speed")
						.translation("config.youkaishomecoming.common.auto_dodge.tierIIHighSpeed")
						.defineInRange("tierIIHighSpeed", 0.35, 0.05, 2.0);
				autoDodgeTierIILowSpeed = builder.comment("Tier II pilot profile low speed")
						.translation("config.youkaishomecoming.common.auto_dodge.tierIILowSpeed")
						.defineInRange("tierIILowSpeed", 0.16, 0.02, 1.0);
				autoDodgeTierIIIHighSpeed = builder.comment("Tier III pilot profile high speed")
						.translation("config.youkaishomecoming.common.auto_dodge.tierIIIHighSpeed")
						.defineInRange("tierIIIHighSpeed", 0.45, 0.05, 2.0);
				autoDodgeTierIIILowSpeed = builder.comment("Tier III pilot profile low speed")
						.translation("config.youkaishomecoming.common.auto_dodge.tierIIILowSpeed")
						.defineInRange("tierIIILowSpeed", 0.2, 0.02, 1.0);
				autoDodgeThreatTopK = builder.comment("Max threats kept per tick after nearest-sort (Top-K)")
						.translation("config.youkaishomecoming.common.auto_dodge.threatTopK")
						.defineInRange("threatTopK", 80, 8, 256);
				autoDodgePredictHorizon = builder.comment("Prediction horizon in ticks")
						.translation("config.youkaishomecoming.common.auto_dodge.predictHorizon")
						.defineInRange("predictHorizon", 16, 4, 40);
				autoDodgeDebugLogInterval = builder.comment("Log [AutoDodge] every N ticks (0 = off; rescue/takeover still log)")
						.translation("config.youkaishomecoming.common.auto_dodge.debugLogInterval")
						.defineInRange("debugLogInterval", 40, 0, 200);
				autoDodgeWallClearanceRadius = builder.comment(
								"Soft wall clearance probe radius in blocks (0 = off).",
								"When safe from bullets, pilot prefers staying this far from solids to keep escape room.")
						.translation("config.youkaishomecoming.common.auto_dodge.wallClearanceRadius")
						.defineInRange("wallClearanceRadius", 1.5, 0.0, 8.0);
				autoDodgeWallClearanceGain = builder.comment(
								"Max soft wall repulsion when fully safe (threat clearance >= wallClearanceSafeDist).",
								"Does not override necessary bullet dodge; hard collisions still blocked.")
						.translation("config.youkaishomecoming.common.auto_dodge.wallClearanceGain")
						.defineInRange("wallClearanceGain", 0.75, 0.0, 10.0);
				autoDodgeWallClearanceDangerDist = builder.comment(
								"Threat clearance at or below this → wall force fully off (dodge bullets first).")
						.translation("config.youkaishomecoming.common.auto_dodge.wallClearanceDangerDist")
						.defineInRange("wallClearanceDangerDist", 0.85, 0.05, 8.0);
				autoDodgeWallClearanceSafeDist = builder.comment(
								"Threat clearance at or above this → full wall force (claim free space).",
								"Between danger and safe: linear ramp.")
						.translation("config.youkaishomecoming.common.auto_dodge.wallClearanceSafeDist")
						.defineInRange("wallClearanceSafeDist", 2.5, 0.1, 16.0);
				previewPilotArenaHalf = builder.comment("Preview pilot arena half-size in blocks")
						.translation("config.youkaishomecoming.common.auto_dodge.previewArenaHalfSize")
						.defineInRange("previewArenaHalfSize", 12.0, 2.0, 64.0);

				builder.comment("Server-side autonomous dodge for live YoukaiEntity instances.");
			youkaiAutoDodgeEnabled = builder.comment("Enable the server-side pilot for youkai with the Auto Dodge effect")
						.translation("config.youkaishomecoming.common.auto_dodge.youkaiEnabled")
						.define("youkaiEnabled", true);
				youkaiAutoDodgeTickInterval = builder.comment("Ticks between full youkai threat scans; the last dodge velocity is held between scans")
						.translation("config.youkaishomecoming.common.auto_dodge.youkaiTickInterval")
						.defineInRange("youkaiTickInterval", 2, 1, 20);
				youkaiAutoDodgeScanRadius = builder.comment("Youkai threat scan radius in blocks")
						.translation("config.youkaishomecoming.common.auto_dodge.youkaiScanRadius")
						.defineInRange("youkaiScanRadius", 16.0, 4.0, 48.0);
				youkaiAutoDodgeHighSpeed = builder.comment("Youkai pilot high speed")
						.translation("config.youkaishomecoming.common.auto_dodge.youkaiHighSpeed")
						.defineInRange("youkaiHighSpeed", 0.3, 0.05, 2.0);
				youkaiAutoDodgeLowSpeed = builder.comment("Youkai pilot low speed")
						.translation("config.youkaishomecoming.common.auto_dodge.youkaiLowSpeed")
						.defineInRange("youkaiLowSpeed", 0.14, 0.02, 1.0);
				youkaiAutoDodgeMaxSpeed = builder.comment("Hard cap for pilot-applied youkai velocity")
						.translation("config.youkaishomecoming.common.auto_dodge.youkaiMaxSpeed")
						.defineInRange("youkaiMaxSpeed", 0.5, 0.05, 2.0);
				youkaiAutoDodgeThreatTopK = builder.comment("Max threats retained per youkai scan")
						.translation("config.youkaishomecoming.common.auto_dodge.youkaiThreatTopK")
						.defineInRange("youkaiThreatTopK", 32, 4, 128);
				youkaiAutoDodgePredictHorizon = builder.comment("Youkai prediction horizon in ticks")
						.translation("config.youkaishomecoming.common.auto_dodge.youkaiPredictHorizon")
						.defineInRange("youkaiPredictHorizon", 8, 2, 30);
				youkaiAutoDodgeWallClearanceRadius = builder.comment("Youkai soft wall-clearance probe radius (0 = off)")
						.translation("config.youkaishomecoming.common.auto_dodge.youkaiWallClearanceRadius")
						.defineInRange("youkaiWallClearanceRadius", 1.5, 0.0, 8.0);
				youkaiAutoDodgeWallClearanceGain = builder.comment("Youkai soft wall-repulsion gain")
						.translation("config.youkaishomecoming.common.auto_dodge.youkaiWallClearanceGain")
						.defineInRange("youkaiWallClearanceGain", 0.75, 0.0, 10.0);
				youkaiAutoDodgeWallClearanceDangerDist = builder.comment("Youkai threat clearance below which wall bias is disabled")
						.translation("config.youkaishomecoming.common.auto_dodge.youkaiWallClearanceDangerDist")
						.defineInRange("youkaiWallClearanceDangerDist", 0.85, 0.05, 8.0);
				youkaiAutoDodgeWallClearanceSafeDist = builder.comment("Youkai threat clearance at which full wall bias is applied")
						.translation("config.youkaishomecoming.common.auto_dodge.youkaiWallClearanceSafeDist")
						.defineInRange("youkaiWallClearanceSafeDist", 2.5, 0.1, 16.0);
			}
			builder.pop();
		}

	}

	public static final ForgeConfigSpec CLIENT_SPEC;
	public static final Client CLIENT;

	public static final ForgeConfigSpec COMMON_SPEC;
	public static final Common COMMON;

	static {
		final Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
		CLIENT_SPEC = client.getRight();
		CLIENT = client.getLeft();

		final Pair<Common, ForgeConfigSpec> common = new ForgeConfigSpec.Builder().configure(Common::new);
		COMMON_SPEC = common.getRight();
		COMMON = common.getLeft();
	}

	/**
	 * Registers any relevant listeners for config
	 */
	public static void init() {
		ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
	}


}
