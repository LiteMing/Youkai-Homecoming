package dev.xkmc.youkaishomecoming.content.spell.analysis;

import java.util.Locale;

/**
 * Stable, registry-independent capability IDs for spell analysis.
 * Policies (SpellCapabilityPolicy) and scripts must reference these IDs, never Java class names.
 */
public enum SpellCapability {

	BASE_FIRE("base_fire"),
	EXPERIMENTAL_FIRE("experimental_fire"),
	HOOK_ON_EXPIRY("hook_on_expiry"),
	HOOK_ON_TRAIL("hook_on_trail"),
	HOOK_ON_HIT("hook_on_hit"),
	BOSS_ON_DAMAGE("boss_on_damage"),
	ORIGIN_TARGET("origin_target"),
	ORIGIN_ABSOLUTE("origin_absolute"),
	CONFINED_TARGET("confine_target"),
	TELEPORT("teleport"),
	ERASE_ENEMY_DANMAKU("erase_enemy_danmaku"),
	CLEAR_SCREEN("clear_screen"),
	SET_ENTITY_FLAG("set_entity_flag"),
	FORCE_PHASE("force_phase"),
	FORCE_SPELL("force_spell"),
	FIRE_SPELL("fire_spell"),
	LEGACY_TICKER("legacy_ticker"),
	RUN_COMMAND("run_command"),
	SET_SPELL_CIRCLE("set_spell_circle"),
	SHOW_SPELL_TITLE("show_spell_title"),
	YSM_RENDER("ysm_render"),
	/** A JSON fragment the editor salvaged but could not decode. Always denied. */
	BROKEN_NODE("broken_node");

	private final String id;

	SpellCapability(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	@Override
	public String toString() {
		return id;
	}

	public static SpellCapability byId(String id) {
		for (var cap : values()) {
			if (cap.id.equals(id)) return cap;
		}
		throw new IllegalArgumentException("Unknown spell capability: " + id);
	}

	public static String normalize(String id) {
		return id.toLowerCase(Locale.ROOT).replace('-', '_');
	}
}
