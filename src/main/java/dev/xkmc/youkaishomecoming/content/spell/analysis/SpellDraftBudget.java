package dev.xkmc.youkaishomecoming.content.spell.analysis;

import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Immutable limits carried by an unfinished spell-card base and frozen into a
 * certification quote. The server-wide certification config remains the final
 * ceiling; this budget can only narrow it.
 */
public record SpellDraftBudget(
		int freeNodeCount,
		int maxSpawnPerTick,
		int maxPeakAlive,
		long maxProjectileTicks,
		long maxHookExecutions,
		int teleportGrants,
		int eraseEnemyDanmakuGrants,
		int clearScreenGrants,
		int bossOnDamageGrants,
		int confinedTargetGrants,
		int experimentalFireGrants,
		int originTargetGrants,
		int longLifetimeGrants,
		int targetCoordinateGrants,
		int trackingMoverGrants,
		/** Compatibility for pre-budget {@code yh_op_quota} cards/certificates. */
		int legacyExperimentalQuota
) {

	public static final String TAG_ROOT = "yh_spell_budget";
	private static final String TAG_FREE_NODES = "free_node_count";
	private static final String TAG_MAX_SPAWN = "max_spawn_per_tick";
	private static final String TAG_MAX_PEAK = "max_peak_alive";
	private static final String TAG_MAX_PROJECTILE_TICKS = "max_projectile_ticks";
	private static final String TAG_MAX_HOOKS = "max_hook_executions";
	private static final String TAG_EXPERIMENTAL = "experimental_grants";
	private static final String TAG_ORIGIN_TARGET = "origin_target";
	private static final String TAG_LONG_LIFETIME = "long_lifetime";
	private static final String TAG_TARGET_COORDINATE = "target_coordinate";
	private static final String TAG_TRACKING_MOVER = "tracking_mover";

	public static final int DEFAULT_FREE_NODE_COUNT = 5;
	/** Historical ordinary-card per-tick budget for tier 1 (128 * coefficient). */
	public static final int DEFAULT_MAX_SPAWN_PER_TICK = 128;
	public static final int DEFAULT_MAX_PEAK_ALIVE = 10_000;
	public static final long DEFAULT_MAX_PROJECTILE_TICKS = 100_000_000L;
	public static final long DEFAULT_MAX_HOOK_EXECUTIONS = 1_000_000L;
	public static final long DEFAULT_EXCESS_NODE_COST_UNITS = 20L;

	public SpellDraftBudget {
		freeNodeCount = Math.max(0, freeNodeCount);
		maxSpawnPerTick = Math.max(1, maxSpawnPerTick);
		maxPeakAlive = Math.max(1, maxPeakAlive);
		maxProjectileTicks = Math.max(1, maxProjectileTicks);
		maxHookExecutions = Math.max(1, maxHookExecutions);
		teleportGrants = Math.max(0, teleportGrants);
		eraseEnemyDanmakuGrants = Math.max(0, eraseEnemyDanmakuGrants);
		clearScreenGrants = Math.max(0, clearScreenGrants);
		bossOnDamageGrants = Math.max(0, bossOnDamageGrants);
		confinedTargetGrants = Math.max(0, confinedTargetGrants);
		experimentalFireGrants = Math.max(0, experimentalFireGrants);
		originTargetGrants = Math.max(0, originTargetGrants);
		longLifetimeGrants = Math.max(0, longLifetimeGrants);
		targetCoordinateGrants = Math.max(0, targetCoordinateGrants);
		trackingMoverGrants = Math.max(0, trackingMoverGrants);
		legacyExperimentalQuota = Math.max(0, legacyExperimentalQuota);
	}

	/** Compatibility constructor for callers and NBT written before per-capability grants. */
	public SpellDraftBudget(int freeNodeCount, int maxSpawnPerTick, int maxPeakAlive,
			long maxProjectileTicks, long maxHookExecutions, int teleportGrants,
			int eraseEnemyDanmakuGrants, int clearScreenGrants, int bossOnDamageGrants,
			int confinedTargetGrants, int experimentalFireGrants, int legacyExperimentalQuota) {
		this(freeNodeCount, maxSpawnPerTick, maxPeakAlive, maxProjectileTicks, maxHookExecutions,
				teleportGrants, eraseEnemyDanmakuGrants, clearScreenGrants, bossOnDamageGrants,
				confinedTargetGrants, experimentalFireGrants, experimentalFireGrants,
				experimentalFireGrants, experimentalFireGrants, experimentalFireGrants,
				legacyExperimentalQuota);
	}

	public static SpellDraftBudget defaults() {
		try {
			var common = YHModConfig.COMMON;
			double multiplier = common.spellDraftBudgetMultiplier.get();
			return new SpellDraftBudget(
					common.spellDraftFreeNodeCount.get(),
					(int) Math.min(Integer.MAX_VALUE, SpellBudgetScaling.scale(DEFAULT_MAX_SPAWN_PER_TICK, multiplier)),
					(int) Math.min(Integer.MAX_VALUE, SpellBudgetScaling.scale(DEFAULT_MAX_PEAK_ALIVE, multiplier)),
					SpellBudgetScaling.scale(DEFAULT_MAX_PROJECTILE_TICKS, multiplier),
					SpellBudgetScaling.scale(DEFAULT_MAX_HOOK_EXECUTIONS, multiplier),
					0, 0, 0, 0, 0, 0, 0);
		} catch (Exception ignored) {
			return defaultFallback();
		}
	}

	public static SpellDraftBudget defaultFallback() {
		return new SpellDraftBudget(DEFAULT_FREE_NODE_COUNT,
				DEFAULT_MAX_SPAWN_PER_TICK, DEFAULT_MAX_PEAK_ALIVE,
				DEFAULT_MAX_PROJECTILE_TICKS, DEFAULT_MAX_HOOK_EXECUTIONS,
				0, 0, 0, 0, 0, 0, 0);
	}

	public static SpellDraftBudget legacy(int quota) {
		SpellDraftBudget base = defaults();
		return new SpellDraftBudget(base.freeNodeCount, base.maxSpawnPerTick,
				base.maxPeakAlive, base.maxProjectileTicks, base.maxHookExecutions,
				0, 0, 0, 0, 0, 0, quota);
	}

	public static SpellDraftBudget read(CompoundTag itemTag, int legacyQuota) {
		if (itemTag == null || !itemTag.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
			return legacyQuota > 0 ? legacy(legacyQuota) : defaults();
		}
		CompoundTag tag = itemTag.getCompound(TAG_ROOT);
		SpellDraftBudget fallback = defaults();
		CompoundTag exp = tag.contains(TAG_EXPERIMENTAL, Tag.TAG_COMPOUND)
				? tag.getCompound(TAG_EXPERIMENTAL) : new CompoundTag();
		int experimentalFire = readInt(exp, SpellCapability.EXPERIMENTAL_FIRE.id(), 0);
		return new SpellDraftBudget(
				readInt(tag, TAG_FREE_NODES, fallback.freeNodeCount),
				readInt(tag, TAG_MAX_SPAWN, fallback.maxSpawnPerTick),
				readInt(tag, TAG_MAX_PEAK, fallback.maxPeakAlive),
				readLong(tag, TAG_MAX_PROJECTILE_TICKS, fallback.maxProjectileTicks),
				readLong(tag, TAG_MAX_HOOKS, fallback.maxHookExecutions),
				readInt(exp, SpellCapability.TELEPORT.id(), 0),
				readInt(exp, SpellCapability.ERASE_ENEMY_DANMAKU.id(), 0),
				readInt(exp, SpellCapability.CLEAR_SCREEN.id(), 0),
				readInt(exp, SpellCapability.BOSS_ON_DAMAGE.id(), 0),
				readInt(exp, SpellCapability.CONFINED_TARGET.id(), 0),
				experimentalFire,
				readInt(exp, TAG_ORIGIN_TARGET, experimentalFire),
				readInt(exp, TAG_LONG_LIFETIME, experimentalFire),
				readInt(exp, TAG_TARGET_COORDINATE, experimentalFire),
				readInt(exp, TAG_TRACKING_MOVER, experimentalFire),
				legacyQuota);
	}

	public void write(CompoundTag itemTag) {
		CompoundTag tag = new CompoundTag();
		tag.putInt(TAG_FREE_NODES, freeNodeCount);
		tag.putInt(TAG_MAX_SPAWN, maxSpawnPerTick);
		tag.putInt(TAG_MAX_PEAK, maxPeakAlive);
		tag.putLong(TAG_MAX_PROJECTILE_TICKS, maxProjectileTicks);
		tag.putLong(TAG_MAX_HOOKS, maxHookExecutions);
		CompoundTag exp = new CompoundTag();
		exp.putInt(SpellCapability.TELEPORT.id(), teleportGrants);
		exp.putInt(SpellCapability.ERASE_ENEMY_DANMAKU.id(), eraseEnemyDanmakuGrants);
		exp.putInt(SpellCapability.CLEAR_SCREEN.id(), clearScreenGrants);
		exp.putInt(SpellCapability.BOSS_ON_DAMAGE.id(), bossOnDamageGrants);
		exp.putInt(SpellCapability.CONFINED_TARGET.id(), confinedTargetGrants);
		exp.putInt(SpellCapability.EXPERIMENTAL_FIRE.id(), experimentalFireGrants);
		exp.putInt(TAG_ORIGIN_TARGET, originTargetGrants);
		exp.putInt(TAG_LONG_LIFETIME, longLifetimeGrants);
		exp.putInt(TAG_TARGET_COORDINATE, targetCoordinateGrants);
		exp.putInt(TAG_TRACKING_MOVER, trackingMoverGrants);
		tag.put(TAG_EXPERIMENTAL, exp);
		itemTag.put(TAG_ROOT, tag);
	}

	public SpellDraftBudget expandedForBoss(SpellAnalysis analysis, SpecialNodeCounter.Summary nodes) {
		return new SpellDraftBudget(
				Math.max(freeNodeCount, nodes.ordinaryNodes()),
				Math.max(maxSpawnPerTick, analysis.maxSpawnPerTick()),
				Math.max(maxPeakAlive, analysis.peakAliveUpperBound()),
				Math.max(maxProjectileTicks, analysis.projectileTicks()),
				Math.max(maxHookExecutions, analysis.hookExecutionUpperBound()),
				Math.max(teleportGrants, nodes.experimentalCount(SpellCapability.TELEPORT)),
				Math.max(eraseEnemyDanmakuGrants, nodes.experimentalCount(SpellCapability.ERASE_ENEMY_DANMAKU)),
				Math.max(clearScreenGrants, nodes.experimentalCount(SpellCapability.CLEAR_SCREEN)),
				Math.max(bossOnDamageGrants, nodes.experimentalCount(SpellCapability.BOSS_ON_DAMAGE)),
				Math.max(confinedTargetGrants, nodes.experimentalCount(SpellCapability.CONFINED_TARGET)),
				Math.max(experimentalFireGrants, nodes.experimentalCount(SpellCapability.EXPERIMENTAL_FIRE)),
				Math.max(originTargetGrants, nodes.experimentalCount(SpellCapability.ORIGIN_TARGET)),
				Math.max(longLifetimeGrants, nodes.experimentalCount(SpellCapability.LONG_LIFETIME)),
				Math.max(targetCoordinateGrants, nodes.experimentalCount(SpellCapability.TARGET_COORDINATE)),
				Math.max(trackingMoverGrants, nodes.experimentalCount(SpellCapability.TRACKING_MOVER)),
				0);
	}

	public int experimentalGrant(SpellCapability capability) {
		return switch (capability) {
			case TELEPORT -> teleportGrants;
			case ERASE_ENEMY_DANMAKU -> eraseEnemyDanmakuGrants;
			case CLEAR_SCREEN -> clearScreenGrants;
			case BOSS_ON_DAMAGE -> bossOnDamageGrants;
			case CONFINED_TARGET -> confinedTargetGrants;
			case EXPERIMENTAL_FIRE -> experimentalFireGrants;
			case ORIGIN_TARGET -> originTargetGrants;
			case LONG_LIFETIME -> longLifetimeGrants;
			case TARGET_COORDINATE -> targetCoordinateGrants;
			case TRACKING_MOVER -> trackingMoverGrants;
			case SIZED_PROJECTILE -> 0;
			default -> 0;
		};
	}

	public boolean permitsExperimental(SpecialNodeCounter.Summary nodes) {
		if (legacyExperimentalQuota > 0) {
			return nodes.experimentalNodes() <= legacyExperimentalQuota;
		}
		for (SpellCapability capability : SpellCapabilityPolicies.experimentalCapabilities()) {
			if (nodes.experimentalCount(capability) > experimentalGrant(capability)) return false;
		}
		return true;
	}

	public int excessNodes(SpecialNodeCounter.Summary nodes) {
		return Math.max(0, nodes.ordinaryNodes() - freeNodeCount);
	}

	public long nodeCostUnits(SpecialNodeCounter.Summary nodes) {
		long perNode;
		try {
			perNode = YHModConfig.COMMON.spellDraftExcessNodeCostUnits.get();
		} catch (Exception ignored) {
			perNode = DEFAULT_EXCESS_NODE_COST_UNITS;
		}
		int excess = excessNodes(nodes);
		return excess > Long.MAX_VALUE / Math.max(1L, perNode)
				? Long.MAX_VALUE : excess * perNode;
	}

	/**
	 * Returns the effective danmaku-per-tick ceiling. Ordinary cards retain the
	 * historical tier budget (128 * tier coefficient); only non-spells use the
	 * newer power-scaled formula.
	 */
	public int maxSpawnPerTickForPower(SpellCardRank rank, double power) {
		return maxSpawnPerTick;
	}

	public int maxSpawnPerTickForPower(SpellCardRank rank, double power, boolean nonSpell) {
		if (!nonSpell) return maxSpawnPerTick;
		SpellCardRank resolved = rank == null ? SpellCardRank.fromBudget(this) : rank;
		return resolved.danmakuPerTick(power);
	}

	/** Convenience form for UI callers that only have the frozen budget. */
	public int maxSpawnPerTickForPower(double power) {
		return maxSpawnPerTickForPower(SpellCardRank.fromBudget(this), power, false);
	}

	public void validatePerformance(SpellAnalysis analysis, SpellAnalysisLimits global) {
		validatePerformance(analysis, global, null, 0);
	}

	/** Validate against the power-scaled rank ceiling used by a real player. */
	public void validatePerformance(SpellAnalysis analysis, SpellAnalysisLimits global,
			SpellCardRank rank, double power) {
		int spawnLimit = Math.min(maxSpawnPerTickForPower(rank, power), global.maxSpawnPerTick());
		int peakLimit = Math.min(maxPeakAlive, global.maxPeakAlive());
		long projectileLimit = Math.min(maxProjectileTicks, global.maxProjectileTicks());
		long hookLimit = Math.min(maxHookExecutions, global.maxHookExecutions());
		if (analysis.maxSpawnPerTick() > spawnLimit) {
			throw new SpellAnalysisException("Certification rejected: maxSpawnPerTick "
					+ analysis.maxSpawnPerTick() + " exceeds draft limit " + spawnLimit);
		}
		if (analysis.peakAliveUpperBound() > peakLimit) {
			throw new SpellAnalysisException("Certification rejected: maxPeakAlive "
					+ analysis.peakAliveUpperBound() + " exceeds draft limit " + peakLimit);
		}
		if (analysis.projectileTicks() > projectileLimit) {
			throw new SpellAnalysisException("Certification rejected: maxProjectileTicks "
					+ analysis.projectileTicks() + " exceeds draft limit " + projectileLimit);
		}
		if (analysis.hookExecutionUpperBound() > hookLimit) {
			throw new SpellAnalysisException("Certification rejected: maxHookExecutions "
					+ analysis.hookExecutionUpperBound() + " exceeds draft limit " + hookLimit);
		}
	}

	private static int readInt(CompoundTag tag, String key, int fallback) {
		return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
	}

	private static long readLong(CompoundTag tag, String key, long fallback) {
		return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : fallback;
	}
}
