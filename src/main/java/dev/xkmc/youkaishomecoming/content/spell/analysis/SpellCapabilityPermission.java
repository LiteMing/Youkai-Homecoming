package dev.xkmc.youkaishomecoming.content.spell.analysis;

import java.util.Locale;

/**
 * Administrator-facing numeric names for spell capability policies.
 *
 * <p>The level is an authorization label, not a second execution system:
 * {@code base} and {@code hook} both use the existing ALLOW policy. Hook is
 * reserved for the callback capabilities so scripts can address that group
 * without inventing another policy enum.</p>
 */
public enum SpellCapabilityPermission {

	FORBID(0, "forbid", SpellCapabilityPolicy.DENY),
	BASE(1, "base", SpellCapabilityPolicy.ALLOW),
	HOOK(2, "hook", SpellCapabilityPolicy.ALLOW),
	EXPERIMENTAL(3, "experimental", SpellCapabilityPolicy.EXPERIMENTAL),
	OP(4, "op", SpellCapabilityPolicy.OP_ONLY);

	private final int level;
	private final String id;
	private final SpellCapabilityPolicy policy;

	SpellCapabilityPermission(int level, String id, SpellCapabilityPolicy policy) {
		this.level = level;
		this.id = id;
		this.policy = policy;
	}

	public int level() {
		return level;
	}

	public String id() {
		return id;
	}

	public SpellCapabilityPolicy policy() {
		return policy;
	}

	public static SpellCapabilityPermission byLevel(int level) {
		for (SpellCapabilityPermission permission : values()) {
			if (permission.level == level) return permission;
		}
		throw new IllegalArgumentException("permission level must be between 0 and 4");
	}

	public static SpellCapabilityPermission parse(String value) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("permission is missing");
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		try {
			return byLevel(Integer.parseInt(normalized));
		} catch (NumberFormatException ignored) {
			for (SpellCapabilityPermission permission : values()) {
				if (permission.id.equals(normalized)
						|| (permission == EXPERIMENTAL && normalized.equals("exp"))
						|| (permission == OP && normalized.equals("op_only"))) return permission;
			}
		}
		throw new IllegalArgumentException("unknown permission: " + value);
	}
}
