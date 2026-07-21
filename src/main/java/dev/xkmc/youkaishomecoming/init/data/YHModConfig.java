package dev.xkmc.youkaishomecoming.init.data;

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
			laserRenderAdditive = builder.define("laserRenderAdditive", true);
			laserRenderInverted = builder.define("laserRenderInverted", true);
			laserTransparency = builder.defineInRange("laserTransparency", 0.5, 0, 1);
			adaptiveProjectileMesh = builder.comment("Adapt giant sphere and cylinder laser mesh detail to projectile visual size.")
					.define("adaptiveProjectileMesh", true);
			giantSphereBaseSegments = builder.comment("Base longitude segments for giant sphere danmaku when adaptive mesh is enabled.")
					.defineInRange("giantSphereBaseSegments", 16, 8, 32);
			giantSphereBaseRings = builder.comment("Base latitude rings for giant sphere danmaku when adaptive mesh is enabled.")
					.defineInRange("giantSphereBaseRings", 8, 4, 16);
			laserCylinderBaseSegments = builder.comment("Base side count for cylindrical laser rendering when adaptive mesh is enabled.")
					.defineInRange("laserCylinderBaseSegments", 12, 4, 24);
			farDanmakuFading = builder.defineInRange("farDanmakuFading", 0.5d, 0, 1);
			selfDanmakuFading = builder.defineInRange("selfDanmakuFading", 0.5d, 0, 1);
			fadingStart = builder.defineInRange("fadingStart", 8d, 0, 128);
			fadingEnd = builder.defineInRange("fadingEnd", 64d, 0, 128);
			powerInfoXAnchor = builder.defineInRange("powerInfoXAnchor", 1, -1, 1);
			powerInfoXOffset = builder.defineInRange("powerInfoXOffset", -8, -1000, 1000);
			powerInfoYAnchor = builder.defineInRange("powerInfoYAnchor", 0, -1, 1);
			powerInfoYOffset = builder.defineInRange("powerInfoYOffset", 0, -1000, 1000);

			builder.push("exposure_compat");
			{
				photoOverlayAlpha = builder.comment("Opacity of the photo thumbnail overlay (0=invisible, 1=opaque)")
						.defineInRange("photoOverlayAlpha", 0.85, 0, 1);
				photoOverlayScale = builder.comment("Scale of the photo thumbnail overlay")
						.defineInRange("photoOverlayScale", 0.25, 0.1, 1.0);
				photoOverlayCorner = builder.comment("Corner for photo overlay: 0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right")
						.defineInRange("photoOverlayCorner", 0, 0, 3);
				photoOverlayDuration = builder.comment("Duration (ticks) to display the photo overlay")
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

		// Exposure compat
		public final ForgeConfigSpec.IntValue exposureCameraCooldown;
		public final ForgeConfigSpec.BooleanValue exposureDeactivateAfterShot;

		// Auto-dodge buff (player pilot) — COMMON / youkaishomecoming-common.toml
		// Movement runs on local client; use matching values on multiplayer clients.
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

		Common(ForgeConfigSpec.Builder builder) {
			builder.push("spell_market");
			{
				spellMarketEnabled = builder.comment("Enable spell market browsing and server synchronization")
						.define("enabled", true);
				spellMarketUrl = builder.comment("Spell market API URL. Automatic imports require HTTPS")
						.define("url", "http://149.13.91.92/api/v1");
				spellMarketAutoSyncEnabled = builder.comment("Periodically synchronize configured exact tags")
						.define("auto_sync_enabled", false);
				spellMarketAutoSyncTags = builder.comment("Exact market tags synchronized by the dedicated server")
						.defineListAllowEmpty("auto_sync_tags", java.util.List.of(), o -> o instanceof String s && !s.isBlank());
				spellMarketPollMinutes = builder.comment("Minimum interval between automatic synchronizations")
						.defineInRange("poll_minutes", 30, 5, 1440);
				spellMarketMaxSpellsPerTag = builder.comment("Maximum number of managed spells imported for one tag")
						.defineInRange("max_spells_per_tag", 64, 1, 256);
			}
			builder.pop();
			builder.push("youkaifying_effect");
			{
				youkaifyingChance = builder.comment("Chance for flesh food to add Youkaifying effect for the first time")
						.defineInRange("youkaifyingChance", 0.2, 0, 1);
				youkaifyingConfusionTime = builder.comment("Confusion time when flesh food to add Youkaifying effect for the first time")
						.defineInRange("youkaifyingConfusionTime", 200, 0, 1000000);
				youkaifyingTime = builder.comment("Time for flesh food to add Youkaifying effect")
						.defineInRange("youkaifyingTime", 1200, 0, 1000000);
				youkaifyingThreshold = builder.comment("Threshold for Youkaifying effect to turn into Youkaified effect")
						.defineInRange("youkaifyingThreshold", 6000, 0, 1000000);
				youkaifiedDuration = builder.comment("Youkaified duration once reached")
						.defineInRange("youkaifiedDuration", 24000, 0, 1000000);
				youkaifiedProlongation = builder.comment("Time for flesh food to add Youkaified effect")
						.defineInRange("youkaifiedProlongation", 6000, 0, 1000000);
			}
			builder.pop();

			builder.push("food_effect");
			{
				breathingHealingFactor = builder.comment("Breathing Healing Factor")
						.defineInRange("breathingHealingFactor", 1.5, 1, 100);
				teaHealingPeriod = builder.comment("Tea Healing Interval")
						.defineInRange("teaHealingPeriod", 60, 0, 10000);
				udumbaraHealingPeriod = builder.comment("Udumbara effect Healing Interval")
						.defineInRange("udumbaraHealingPeriod", 60, 0, 10000);
				udumbaraDuration = builder.comment("Udumbara flowering duration")
						.defineInRange("udumbaraDuration", 200, 0, 100000);
				udumbaraFullMoonReduction = builder.comment("Udumbara full moon damage reduction")
						.defineInRange("udumbaraFullMoonReduction", 4, 0, 100);
				higiHealingPeriod = builder.comment("Higi Healing Interval")
						.defineInRange("higiHealingPeriod", 60, 0, 10000);
				fairyHealingFactor = builder.comment("Fairy Healing Factor")
						.defineInRange("fairyHealingFactor", 2d, 1, 100);
			}
			builder.pop();

			builder.push("suwako_hat");
			{
				frogEatCountForHat = builder.comment("Number of raiders with different types frogs need to eat in front of villager to drop Suwako hat")
						.defineInRange("frogEatCountForHat", 3, 1, 10);
				frogEatRaiderVillagerSightRange = builder.comment("Range for villagers with direct sight when frog eat raiders")
						.defineInRange("frogEatRaiderVillagerSightRange", 20, 1, 64);
				frogEatRaiderVillagerNoSightRange = builder.comment("Range for villagers without direct sight when frog eat raiders")
						.defineInRange("frogEatRaiderVillagerNoSightRange", 10, 1, 64);
			}
			builder.pop();

			builder.push("koishi_attack");
			{
				koishiAttackEnable = builder.comment("Enable koishi attack when player has youkaifying or youkaified effect")
						.define("koishiAttackEnable", true);
				koishiAttackCoolDown = builder.comment("Time in ticks for minimum time between koishi attacks")
						.defineInRange("koishiAttackCoolDown", 6000, 1, 1000000);
				koishiAttackChance = builder.comment("Chance every tick to do koishi attack")
						.defineInRange("koishiAttackChance", 0.001, 0, 1);
				koishiAttackDamage = builder.comment("Koishi attack damage")
						.defineInRange("koishiAttackDamage", 100, 0, 100000000);
				koishiAttackBlockCount = builder.comment("Number of times player needs to consecutively block Koishi attack to get hat")
						.defineInRange("koishiAttackBlockCount", 3, 0, 100);
			}
			builder.pop();

			builder.push("danmaku_battle");
			{
				danmakuMinPHPDamage = builder.comment("Minimum damage youkai danmaku will deal against non-player")
						.defineInRange("danmakuMinPHPDamage", 0.02, 0, 1);
				danmakuPlayerPHPDamage = builder.comment("Minimum damage youkai danmaku will deal against player")
						.defineInRange("danmakuPlayerPHPDamage", 0.1, 0, 1);
				danmakuHealOnHitTarget = builder.comment("When danmaku hits target, heal youkai health by percentage of max health")
						.defineInRange("danmakuHealOnHitTarget", 0.2, 0, 1);
				playerDanmakuCooldown = builder.comment("Player item cooldown for using danmaku")
						.defineInRange("playerDanmakuCooldown", 20, 5, 1000);
				playerLaserCooldown = builder.comment("Player item cooldown for using laser")
						.defineInRange("playerLaserCooldown", 80, 5, 1000);
				playerSpellCooldown = builder.comment("Player item cooldown for using spellcard")
						.defineInRange("playerSpellCooldown", 40, 5, 1000);
				playerLaserDuration = builder.comment("Player laser duration")
						.defineInRange("playerLaserDuration", 100, 5, 1000);
			invulFrameForDanmaku = builder.comment("Enable danmaku damage invulnerability frame against non-player non-youkai mobs.")
					.comment("It's always enabled against player and youkais")
					.define("invulFrameForDanmaku", true);
				danmakuBuffCostTicks = builder.comment("Buff duration (ticks) consumed per danmaku/laser shot when player has youkaified/fairy effect.")
					.comment("Set to 0 to disable buff consumption. Hat bonus bypasses this cost.")
					.defineInRange("danmakuBuffCostTicks", 40, 0, 10000);
				danmakuMaxResource = builder.comment("Max resource obtainable from danmaku battle")
						.defineInRange("danmakuMaxResource", 10, 4, 20);
				danmakuMaxPower = builder.comment("Max Power player can obtain from grazing")
						.defineInRange("danmakuMaxPower", 4, 1, 20);
				danmakuPowerBonus = builder.comment("Danmaku damage each level of power increase")
						.defineInRange("danmakuPowerBonus", 0.25, 0, 1);
				grazeEffectiveness = builder.comment("Multiplier for grazing")
						.defineInRange("grazeEffectiveness", 1d, 0, 10);
				missInvulTime = builder.comment("Danmaku invulnerability and disabled time when you take a hit")
						.defineInRange("missInvulTime", 60, 10, 100);
				bombInvulTime = builder.comment("Danmaku invulnerability and disabled time when you use a bomb")
						.defineInRange("bombInvulTime", 30, 10, 100);
				maxPowerLossOnMiss = builder.comment("Maximum loss of power when you take a hit")
						.defineInRange("maxPowerLossOnMiss", 1d, 0, 10);
				initialResource = builder.comment("Initial life and bomb when you initiate a danmaku battle")
						.comment("Also is the amount of bomb you get when you lose a life")
						.defineInRange("initialResource", 2, 0, 10);
				initialPower = builder.comment("Initial power when you initiate a danmaku battle")
						.defineInRange("initialPower", 1, 0, 10);
			}
			builder.pop();

			builder.push("rumia");
			{
				rumiaNaturalSpawn = builder.comment("If Rumia would spawn naturally around her nest if the first one goes too far. Does not affect structure spawn")
						.define("rumiaNaturalSpawn", true);
				exRumiaConversion = builder.comment("Enable Ex Rumia conversion when Rumia takes too high damage in one hit")
						.define("exRumiaConversion", true);
				rumiaDamageCap = builder.comment("Allow Rumia to cap incoming damage at a factor of max health")
						.define("rumiaDamageCap", true);
				rumiaNoTargetHealing = builder.comment("Enable Rumia healing when having no target")
						.define("rumiaNoTargetHealing", true);
				rumiaHairbandDrop = builder.comment("Enable Ex Rumia hairband drop")
						.define("rumiaHairbandDrop", true);
			}
			builder.pop();

			builder.push("reimu");
			{
				reimuSummonFlesh = builder.comment("Summon Reimu when player eats flesh in front of villagers")
						.define("reimuSummonFlesh", true);
				reimuSummonKill = builder.comment("Summon Reimu when player with youkaified/fying effect kills villager in front of other villagers")
						.define("reimuSummonKill", true);
				reimuSummonMoney = builder.comment("Summon Reimu when player throws emerald or gold into donation box")
						.define("reimuSummonMoney", true);
				reimuSummonCost = builder.comment("Cost of emerald/gold to summon Reimu")
						.defineInRange("reimuSummonCost", 8, 1, 100000);
				reimuHairbandFlightEnable = builder.comment("Enable creative flight on Reimu hairband")
						.define("reimuHairbandFlightEnable", true);
				reimuExtraDamageCoolDown = builder.comment("Enable non-danmaku extra damage cooldown on Reimu")
						.define("reimuExtraDamageCoolDown", true);
				reimuDamageReduction = builder.comment("Enable non-danmaku damage reduction on Reimu")
						.define("reimuDamageReduction", true);
				canReimuTeleportToOtherDimension = builder.comment("If Reimu can be teleported to other dimension")
						.define("canReimuTeleportToOtherDimension", false);
			}
			builder.pop();

			builder.push("cirno");
			{
				cirnoSpawn = builder.comment("Toggle for Cirno natural spawns")
						.define("cirnoSpawn", true);
				cirnoFairyDrop = builder.comment("Chance for fairy ice crystal to drop")
						.defineInRange("cirnoFairyDrop", 0.03, 0, 1);
				fairyAttackYoukaified = builder.comment("Fairies will actively attack players with youkaifying/ed effects")
						.define("fairyAttackYoukaified", true);
				fairySummonReinforcement = builder.comment("Chance for fairies to summon other fairies when killed by non-danmaku damage")
						.defineInRange("fairySummonReinforcement", 0.5, 0, 1);
			}
			builder.pop();

			builder.push("custom_spell");
			{
				customSpellMaxDuration = builder.comment("Max duration of custom spell allowed")
						.defineInRange("customSpellMaxDuration", 1, 60, 1000);
				ringSpellDanmakuPerItemCost = builder.comment("Ring Spell: Max number of bullet allowed per item cost")
						.defineInRange("ringSpellDanmakuPerItemCost", 32, 1, 1024);
				homingSpellDanmakuPerItemCost = builder.comment("Homing Spell: Max number of bullet allowed per item cost")
						.defineInRange("homingSpellDanmakuPerItemCost", 8, 1, 1024);
			}
			builder.pop();

			builder.push("spell_migration");
			{
				useLegacySpellCards = builder.comment("Fallback to legacy Java SpellCard classes instead of data-driven migrated versions.")
						.comment("Read at startup — restart required to apply.")
						.define("useLegacySpellCards", false);
			}
			builder.pop();

			builder.push("touhou_little_maid");
			{
				smallFairyReplacement = builder.comment("Replace Fairies from Touhou Little Maid with a neutral fairy")
						.define("smallFairyReplacement", false);
				smallFairySummonReinforcement = builder.comment("Chance for small fairies to summon other fairies when killed by non-danmaku damage")
						.defineInRange("smallFairySummonReinforcement", 0.25, 0, 1);
				smallFairySummonStrongFairy = builder.comment("Chance for small fairies to summon stronger fairies when they are set to summon reinforcements")
						.defineInRange("smallFairySummonStrongFairy", 0.1, 0, 1);
				smallFairyStrength = builder.comment("Small Fairy spellcard strength")
						.defineInRange("smallFairyStrength", 2, 0, 4);
			}
			builder.pop();

			builder.push("exposure_compat");
			{
				exposureCameraCooldown = builder.comment("Cooldown (ticks) applied to camera after photographing danmaku")
						.defineInRange("exposureCameraCooldown", 40, 0, 600);
				exposureDeactivateAfterShot = builder.comment("Whether to exit viewfinder after photographing danmaku")
						.define("exposureDeactivateAfterShot", true);
			}
			builder.pop();

			builder.push("auto_dodge");
			{
				builder.comment("Player AUTO_DODGE buff (amp 0=rescue, 1=assist, 2=takeover).",
						"Movement is applied on the local client; set the same values on multiplayer clients.");
				autoDodgeEnabled = builder.comment("Master switch for player auto-dodge buff logic")
						.define("enabled", true);
				autoDodgeScanRadius = builder.comment("Threat scan radius in blocks (world projectiles + client danmaku cache)")
						.defineInRange("scanRadius", 16.0, 4.0, 48.0);
				autoDodgeEmergencyCooldown = builder.comment("Cooldown ticks after a rescue (tier I) pulse")
						.defineInRange("emergencyCooldown", 4, 0, 40);
				autoDodgeRescueClearance = builder.comment("Tier I: only act when min clearance is at or below this")
						.defineInRange("rescueClearance", 1.25, 0.1, 8.0);
				autoDodgeInputPriority = builder.comment("Tier II: player input length above this prefers steering over pilot")
						.defineInRange("inputPriority", 0.25, 0.0, 2.0);
				autoDodgeAssistPilotWeight = builder.comment("Tier II: blend weight of pilot velocity when idle (0-1)")
						.defineInRange("assistPilotWeight", 0.65, 0.0, 1.0);
				autoDodgeAssistCurrentWeight = builder.comment("Tier II: blend weight of current velocity when idle (0-1)")
						.defineInRange("assistCurrentWeight", 0.35, 0.0, 1.0);
				autoDodgeAssistSpeedCap = builder.comment("Tier II: max horizontal speed while assisting")
						.defineInRange("assistSpeedCap", 0.28, 0.05, 1.5);
				autoDodgeTakeoverMinSpeed = builder.comment("Tier III: boost horizontal speed up to at least this when non-zero")
						.defineInRange("takeoverMinSpeed", 0.35, 0.05, 1.5);
				autoDodgeRescuePulseSpeed = builder.comment("Tier I fallback horizontal kick speed")
						.defineInRange("rescuePulseSpeed", 0.4, 0.05, 1.5);
				autoDodgeRescueJump = builder.comment("Tier I fallback upward impulse")
						.defineInRange("rescueJump", 0.2, 0.0, 1.0);
				autoDodgeTierIHighSpeed = builder.comment("Tier I pilot profile high speed")
						.defineInRange("tierIHighSpeed", 0.25, 0.05, 2.0);
				autoDodgeTierILowSpeed = builder.comment("Tier I pilot profile low speed")
						.defineInRange("tierILowSpeed", 0.12, 0.02, 1.0);
				autoDodgeTierIIHighSpeed = builder.comment("Tier II pilot profile high speed")
						.defineInRange("tierIIHighSpeed", 0.35, 0.05, 2.0);
				autoDodgeTierIILowSpeed = builder.comment("Tier II pilot profile low speed")
						.defineInRange("tierIILowSpeed", 0.16, 0.02, 1.0);
				autoDodgeTierIIIHighSpeed = builder.comment("Tier III pilot profile high speed")
						.defineInRange("tierIIIHighSpeed", 0.45, 0.05, 2.0);
				autoDodgeTierIIILowSpeed = builder.comment("Tier III pilot profile low speed")
						.defineInRange("tierIIILowSpeed", 0.2, 0.02, 1.0);
				autoDodgeThreatTopK = builder.comment("Max threats kept per tick after nearest-sort (Top-K)")
						.defineInRange("threatTopK", 80, 8, 256);
				autoDodgePredictHorizon = builder.comment("Prediction horizon in ticks")
						.defineInRange("predictHorizon", 16, 4, 40);
				autoDodgeDebugLogInterval = builder.comment("Log [AutoDodge] every N ticks (0 = off; rescue/takeover still log)")
						.defineInRange("debugLogInterval", 40, 0, 200);
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
