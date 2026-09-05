package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicies;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellRankConfig;

/** KubeJS-facing runtime configuration for certification capability policies and rank budgets. */
public final class YHSpellConfig {

	private YHSpellConfig() {
	}

	public static void setCapabilityPolicy(String capabilityId, String policy) {
		SpellCapabilityPolicies.setPolicy(capabilityId, policy);
	}

	public static String getCapabilityPolicy(String capabilityId) {
		var capability = dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapability
				.byId(dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapability.normalize(capabilityId));
		return SpellCapabilityPolicies.currentPolicy(capability).name().toLowerCase(java.util.Locale.ROOT);
	}

	public static void resetCapabilityPolicies() {
		SpellCapabilityPolicies.clearOverrides();
	}

	public static void setRankLimits(String rankId, int freeNodes, int maxSpawnPerTick,
			int maxPeakAlive, long maxProjectileTicks, long maxHookExecutions) {
		SpellRankConfig.setLimits(rankId, freeNodes, maxSpawnPerTick, maxPeakAlive,
				maxProjectileTicks, maxHookExecutions);
	}

	public static void setRankDefaultExperimentalGrants(String rankId, int grants) {
		SpellRankConfig.setDefaultExperimentalGrants(rankId, grants);
	}

	public static void setRankExperimentalGrant(String rankId, String capabilityId, int grants) {
		SpellRankConfig.setExperimentalGrant(rankId, capabilityId, grants);
	}

	public static void resetRankBudgets() {
		SpellRankConfig.clearOverrides();
	}
}
