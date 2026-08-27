package dev.xkmc.youkaishomecoming.content.spell.analysis;

import java.util.EnumMap;
import java.util.Map;

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
		// Player-facing experimental nodes: unlockable within a boss-draft's
		// special-node quota. on_damage, teleport, and the screen/erase nodes are
		// legitimate certification tactics (the player attacks the certification
		// enemy with danmaku and may clear their own mechanics).
		put(SpellCapability.BOSS_ON_DAMAGE, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.TELEPORT, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.ERASE_ENEMY_DANMAKU, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.CLEAR_SCREEN, SpellCapabilityPolicy.EXPERIMENTAL);
		// TARGET/ABSOLUTE origins are common in built-in spells; certification keeps
		// them allowed — wrong-place spawning is neutralized by the active-threat
		// discount (D6) and point-blank spawning is a legitimate danmaku challenge.
		put(SpellCapability.ORIGIN_TARGET, SpellCapabilityPolicy.ALLOW);
		put(SpellCapability.ORIGIN_ABSOLUTE, SpellCapabilityPolicy.ALLOW);
		// Boss-authoring freedom nodes: confine, entity flags and force/fire
		// spell are OP_ONLY — players must not bypass limits with them, they exist
		// for boss spell creators only.
		put(SpellCapability.CONFINED_TARGET, SpellCapabilityPolicy.OP_ONLY);
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
