package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpecialNodeCounter;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicy;

/** Shared policy markers used by the action tree and action picker. */
final class SpellEditorNodeLabels {

	private SpellEditorNodeLabels() {
	}

	static String actionMarker(SpellAction action) {
		// Salvaged fragments are denied too, but call them out separately so a
		// broken node is not mistaken for a deliberately banned one.
		if (unwrap(action) instanceof SpellActions.BrokenAction) return "[X] ⚠ ";
		SpellCapabilityPolicy policy = SpecialNodeCounter.policy(action);
		if (policy == SpellCapabilityPolicy.EXPERIMENTAL) return "[EXP] ";
		if (policy == SpellCapabilityPolicy.OP_ONLY) return "[OP] ";
		if (policy == SpellCapabilityPolicy.DENY) return "[X] ";
		return "";
	}

	private static SpellAction unwrap(SpellAction action) {
		while (action instanceof SpellActions.DisabledAction disabled) {
			action = disabled.inner();
		}
		return action;
	}

	static String sectionMarker(String section) {
		return "damage".equals(section) ? "[EXP] " : "";
	}

	static String branchMarker(String branch) {
		return switch (branch) {
			case "onExpiry", "onTrail", "onHitEntity", "onHitBlock" -> "[ADV] ";
			default -> "";
		};
	}
}
