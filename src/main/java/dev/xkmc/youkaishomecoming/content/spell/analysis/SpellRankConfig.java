package dev.xkmc.youkaishomecoming.content.spell.analysis;

import java.util.EnumMap;
import java.util.Map;

/** Runtime-overridable draft budgets for the fixed visual rank identifiers. */
public final class SpellRankConfig {

	private static final Map<SpellCardRank, Settings> OVERRIDES = new EnumMap<>(SpellCardRank.class);

	private SpellRankConfig() {
	}

	public record Settings(
			int freeNodeCount,
			int maxSpawnPerTick,
			int maxPeakAlive,
			long maxProjectileTicks,
			long maxHookExecutions,
			int defaultExperimentalGrants,
			Map<SpellCapability, Integer> experimentalGrants
	) {
		public Settings {
			freeNodeCount = Math.max(0, freeNodeCount);
			maxSpawnPerTick = Math.max(1, maxSpawnPerTick);
			maxPeakAlive = Math.max(1, maxPeakAlive);
			maxProjectileTicks = Math.max(1, maxProjectileTicks);
			maxHookExecutions = Math.max(1, maxHookExecutions);
			defaultExperimentalGrants = Math.max(0, defaultExperimentalGrants);
			EnumMap<SpellCapability, Integer> copy = new EnumMap<>(SpellCapability.class);
			if (experimentalGrants != null) {
				for (var entry : experimentalGrants.entrySet()) {
					if (entry.getKey() != null) copy.put(entry.getKey(), Math.max(0, entry.getValue()));
				}
			}
			experimentalGrants = Map.copyOf(copy);
		}

		public int experimentalGrant(SpellCapability capability) {
			return experimentalGrants.getOrDefault(capability, defaultExperimentalGrants);
		}

		private Settings withLimits(int freeNodes, int spawn, int peak, long projectileTicks, long hooks) {
			return new Settings(freeNodes, spawn, peak, projectileTicks, hooks,
					defaultExperimentalGrants, experimentalGrants);
		}

		private Settings withDefaultExperimentalGrants(int grants) {
			return new Settings(freeNodeCount, maxSpawnPerTick, maxPeakAlive,
					maxProjectileTicks, maxHookExecutions, grants, experimentalGrants);
		}

		private Settings withExperimentalGrant(SpellCapability capability, int grants) {
			EnumMap<SpellCapability, Integer> copy = new EnumMap<>(SpellCapability.class);
			copy.putAll(experimentalGrants);
			copy.put(capability, Math.max(0, grants));
			return new Settings(freeNodeCount, maxSpawnPerTick, maxPeakAlive,
					maxProjectileTicks, maxHookExecutions, defaultExperimentalGrants, copy);
		}
	}

	public static Settings current(SpellCardRank rank) {
		if (rank == null) return defaults(SpellCardRank.LESSER_WISDOM);
		synchronized (OVERRIDES) {
			return OVERRIDES.getOrDefault(rank, defaults(rank));
		}
	}

	public static void setLimits(String rankId, int freeNodes, int maxSpawnPerTick,
			int maxPeakAlive, long maxProjectileTicks, long maxHookExecutions) {
		SpellCardRank rank = requireRank(rankId);
		synchronized (OVERRIDES) {
			OVERRIDES.put(rank, current(rank).withLimits(freeNodes, maxSpawnPerTick, maxPeakAlive,
					maxProjectileTicks, maxHookExecutions));
		}
	}

	public static void setDefaultExperimentalGrants(String rankId, int grants) {
		SpellCardRank rank = requireRank(rankId);
		synchronized (OVERRIDES) {
			OVERRIDES.put(rank, current(rank).withDefaultExperimentalGrants(grants));
		}
	}

	public static void setExperimentalGrant(String rankId, String capabilityId, int grants) {
		SpellCardRank rank = requireRank(rankId);
		SpellCapability capability = SpellCapability.byId(SpellCapability.normalize(capabilityId));
		synchronized (OVERRIDES) {
			OVERRIDES.put(rank, current(rank).withExperimentalGrant(capability, grants));
		}
	}

	public static void clearOverrides() {
		synchronized (OVERRIDES) {
			OVERRIDES.clear();
		}
	}

	private static Settings defaults(SpellCardRank rank) {
		return new Settings(rank.defaultFreeNodeCount(), rank.defaultMaxSpawnPerTick(),
				rank.defaultMaxPeakAlive(), rank.defaultMaxProjectileTicks(),
				rank.defaultMaxHookExecutions(), rank.defaultExperimentalGrants(), Map.of());
	}

	private static SpellCardRank requireRank(String rankId) {
		if (rankId == null || rankId.isBlank()) throw new IllegalArgumentException("rank is missing");
		for (SpellCardRank rank : SpellCardRank.values()) {
			if (rank.getSerializedName().equalsIgnoreCase(rankId) || rank.name().equalsIgnoreCase(rankId)) {
				return rank;
			}
		}
		throw new IllegalArgumentException("unknown spell rank: " + rankId);
	}
}
