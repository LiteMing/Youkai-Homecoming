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

	static {
		put(SpellCapability.BASE_FIRE, SpellCapabilityPolicy.ALLOW);
		put(SpellCapability.HOOK_ON_EXPIRY, SpellCapabilityPolicy.ALLOW);
		put(SpellCapability.HOOK_ON_TRAIL, SpellCapabilityPolicy.ALLOW);
		put(SpellCapability.HOOK_ON_HIT, SpellCapabilityPolicy.ALLOW);
		put(SpellCapability.BOSS_ON_DAMAGE, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.ORIGIN_TARGET, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.ORIGIN_ABSOLUTE, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.CONFINED_TARGET, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.TELEPORT, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.ERASE_ENEMY_DANMAKU, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.CLEAR_SCREEN, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.SET_ENTITY_FLAG, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.FORCE_SPELL, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.FIRE_SPELL, SpellCapabilityPolicy.EXPERIMENTAL);
		put(SpellCapability.LEGACY_TICKER, SpellCapabilityPolicy.DENY);
		put(SpellCapability.RUN_COMMAND, SpellCapabilityPolicy.OP_ONLY);
		put(SpellCapability.SET_SPELL_CIRCLE, SpellCapabilityPolicy.ALLOW);
		put(SpellCapability.SHOW_SPELL_TITLE, SpellCapabilityPolicy.ALLOW);
		put(SpellCapability.YSM_RENDER, SpellCapabilityPolicy.ALLOW);
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
}
