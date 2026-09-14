package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicies;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapability;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellRankConfig;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import net.minecraft.world.entity.player.Player;

/** KubeJS-facing runtime configuration for certification capability policies and rank budgets. */
public final class YHSpellConfig {

	private YHSpellConfig() {
	}

	public static void setCapabilityPolicy(String capabilityId, String policy) {
		SpellCapabilityPolicies.setPolicy(capabilityId, policy);
	}

	public static String getCapabilityPolicy(String capabilityId) {
		var capability = SpellCapability.byId(SpellCapability.normalize(capabilityId));
		return SpellCapabilityPolicies.currentPolicy(capability).name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Sets the administrator-facing 0..4 permission level for one capability. */
	public static void setCapabilityPermission(String capabilityId, int level) {
		SpellCapabilityPolicies.setPermission(capabilityId, level);
	}

	/** Returns the effective 0..4 permission level for one capability. */
	public static int getCapabilityPermission(String capabilityId) {
		SpellCapability capability = SpellCapability.byId(SpellCapability.normalize(capabilityId));
		return SpellCapabilityPolicies.currentPermission(capability).level();
	}

	/** Applies a 0..4 permission level to a stable default capability group. */
	public static void setCapabilityGroupPermission(String group, int level) {
		var capabilities = SpellCapabilityPolicies.capabilitiesInGroup(group);
		for (SpellCapability capability : capabilities) {
			if (level == 2 && !SpellCapabilityPolicies.isHookCapability(capability)) {
				throw new IllegalArgumentException("permission level 2 (hook) only applies to the hook group");
			}
			if (level == 1 && SpellCapabilityPolicies.isHookCapability(capability)) {
				throw new IllegalArgumentException("permission level 1 (base) cannot be assigned to the hook group");
			}
		}
		for (SpellCapability capability : capabilities) SpellCapabilityPolicies.setPermission(capability, level);
	}

	public static void resetCapabilityPermission(String capabilityId) {
		SpellCapability capability = SpellCapability.byId(SpellCapability.normalize(capabilityId));
		SpellCapabilityPolicies.resetPolicy(capability);
	}

	/** Grants a persistent player override. The normal server command remains the audit-friendly path. */
	public static void setPlayerPermission(Player player, int level) {
		if (player == null) throw new IllegalArgumentException("player is missing");
		GrazeCapability.HOLDER.get(player).setSpellPermissionOverride(level);
	}

	public static int getPlayerPermission(Player player) {
		if (player == null) throw new IllegalArgumentException("player is missing");
		return GrazeCapability.HOLDER.get(player).getSpellPermissionLevel();
	}

	public static void resetPlayerPermission(Player player) {
		if (player == null) throw new IllegalArgumentException("player is missing");
		GrazeCapability.HOLDER.get(player).clearSpellPermissionOverride();
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
