package dev.xkmc.youkaishomecoming.content.spell.analysis;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Default capability policies (design doc §11 table).
 * <p>
 * Certification uses {@link #defaultCertification()}; the market profile keeps its
 * historical behavior: only run_command / force_spell / fire_spell are banned
 * (see SpellAnalyzer#isMarketBanned), everything else stays allowed to avoid
 * market import regressions.
 */
public final class SpellCapabilityPolicies {

	private SpellCapabilityPolicies() {
	}

	private static final Map<SpellCapability, SpellCapabilityPolicy> CERTIFICATION_DEFAULTS = new EnumMap<>(SpellCapability.class);

	/** Runtime overrides (Phase 6 commands / scripts); queried on every certification
	 * start and cast so policy changes apply immediately. */
	private static final Map<SpellCapability, SpellCapabilityPolicy> OVERRIDES = new EnumMap<>(SpellCapability.class);

	static {
		put(SpellCapability.BASE_FIRE, SpellCapabilityPolicy.ALLOW);
		put(SpellCapability.HOOK_ON_EXPIRY, SpellCapabilityPolicy.ALLOW);
		put(SpellCapability.HOOK_ON_TRAIL, SpellCapabilityPolicy.ALLOW);
		put(SpellCapability.HOOK_ON_HIT, SpellCapabilityPolicy.ALLOW);
		// Player-facing experimental nodes: unlockable within a draft budget.
		put(SpellCapability.BOSS_ON_DAMAGE, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.TELEPORT, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.ERASE_ENEMY_DANMAKU, SpellCapabilityPolicy.OP_ONLY);
		put(SpellCapability.CLEAR_SCREEN, SpellCapabilityPolicy.ALLOW);
		// TARGET/ABSOLUTE origins are common in built-in spells; certification keeps
		// them allowed — wrong-place spawning is neutralized by the active-threat
		// discount (D6) and point-blank spawning is a legitimate danmaku challenge.
		put(SpellCapability.ORIGIN_TARGET, SpellCapabilityPolicy.ALLOW);
		put(SpellCapability.ORIGIN_ABSOLUTE, SpellCapabilityPolicy.ALLOW);
		// Confined target selection is symmetric in certification combat and may be
		// granted by a draft budget. Entity flags and force/fire spell remain
		// operator-only boss-authoring capabilities.
		put(SpellCapability.CONFINED_TARGET, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.SET_ENTITY_FLAG, SpellCapabilityPolicy.OP_ONLY);
		put(SpellCapability.FORCE_PHASE, SpellCapabilityPolicy.OP_ONLY);
		put(SpellCapability.FORCE_SPELL, SpellCapabilityPolicy.OP_ONLY);
		put(SpellCapability.FIRE_SPELL, SpellCapabilityPolicy.OP_ONLY);
		put(SpellCapability.LEGACY_TICKER, SpellCapabilityPolicy.DENY);
		put(SpellCapability.RUN_COMMAND, SpellCapabilityPolicy.OP_ONLY);
		put(SpellCapability.SET_SPELL_CIRCLE, SpellCapabilityPolicy.ALLOW);
		put(SpellCapability.SHOW_SPELL_TITLE, SpellCapabilityPolicy.ALLOW);
		put(SpellCapability.YSM_RENDER, SpellCapabilityPolicy.ALLOW);
		// Undecodable salvaged fragments. Denied unconditionally — see setPolicy.
		put(SpellCapability.BROKEN_NODE, SpellCapabilityPolicy.DENY);
	}

	private static void put(SpellCapability cap, SpellCapabilityPolicy policy) {
		CERTIFICATION_DEFAULTS.put(cap, policy);
	}

	/** Default certification policies; unknown capabilities default to DENY. */
	public static Map<SpellCapability, SpellCapabilityPolicy> defaultCertification() {
		return Map.copyOf(CERTIFICATION_DEFAULTS);
	}

	public static SpellCapabilityPolicy defaultPolicy(SpellCapability cap) {
		if (cap == null) return SpellCapabilityPolicy.DENY;
		return CERTIFICATION_DEFAULTS.getOrDefault(cap, SpellCapabilityPolicy.DENY);
	}

	/** Current effective policy: runtime override first, then the default table. */
	public static SpellCapabilityPolicy currentPolicy(SpellCapability cap) {
		if (cap == null) return SpellCapabilityPolicy.DENY;
		return OVERRIDES.getOrDefault(cap, defaultPolicy(cap));
	}

	/** Returns the capabilities currently classified as draft-unlockable. */
	public static Set<SpellCapability> experimentalCapabilities() {
		EnumSet<SpellCapability> result = EnumSet.noneOf(SpellCapability.class);
		for (SpellCapability capability : SpellCapability.values()) {
			if (currentPolicy(capability) == SpellCapabilityPolicy.EXPERIMENTAL) {
				result.add(capability);
			}
		}
		return Set.copyOf(result);
	}

	/** Parses the stable script/command policy id. */
	public static SpellCapabilityPolicy parsePolicy(String policyName) {
		if (policyName == null) throw new IllegalArgumentException("policy is missing");
		return switch (policyName.toLowerCase(java.util.Locale.ROOT)) {
			case "allow" -> SpellCapabilityPolicy.ALLOW;
			case "experimental", "exp" -> SpellCapabilityPolicy.EXPERIMENTAL;
			case "deny" -> SpellCapabilityPolicy.DENY;
			case "op_only", "op-only", "op" -> SpellCapabilityPolicy.OP_ONLY;
			default -> throw new IllegalArgumentException("unknown policy: " + policyName);
		};
	}

	public static void setPolicy(String capabilityId, String policyName) {
		setPolicy(SpellCapability.byId(SpellCapability.normalize(capabilityId)), parsePolicy(policyName));
	}

	public static void setPolicy(SpellCapability cap, SpellCapabilityPolicy policy) {
		// A node we could not decode has no defined behaviour, so no script or
		// command may promote it out of DENY.
		if (cap == SpellCapability.BROKEN_NODE) {
			return;
		}
		if (policy == null || policy == defaultPolicy(cap)) {
			OVERRIDES.remove(cap);
		} else {
			OVERRIDES.put(cap, policy);
		}
	}

	public static void clearOverrides() {
		OVERRIDES.clear();
	}
}
