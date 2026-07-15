package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.definition.MoverConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.MoverConfigs;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Determines which editor parameters are overridden by a given MoverConfig.
 * Pure function: no side effects, no state.
 */
public final class MoverOverrideResolver {

	public enum OverriddenParam {
		SPEED,
		OFFSET_X,
		OFFSET_Y,
		OFFSET_Z
	}

	private static final Set<String> POSITION_CONTROLLING = Set.of(
			"polar", "bezier", "multi_bezier", "spline", "formula",
			"orbital", "translate", "zero"
	);

	private static final Set<String> ATTACHED_TYPES = Set.of(
			"attached", "attached_free_rot"
	);

	/**
	 * Returns the set of parameters overridden by the given mover config.
	 * Returns empty set if mover is absent or type is unrecognized.
	 */
	public static Set<OverriddenParam> resolve(Optional<MoverConfig> moverOpt) {
		if (moverOpt.isEmpty()) return Collections.emptySet();
		return resolveConfig(moverOpt.get());
	}

	private static Set<OverriddenParam> resolveConfig(MoverConfig config) {
		String type = MoverConfigs.getTypeId(config);
		if (type == null) return Collections.emptySet();

		// Composite: union of all segment overrides
		if (config instanceof MoverConfigs.CompositeMoverConfig composite) {
			EnumSet<OverriddenParam> result = EnumSet.noneOf(OverriddenParam.class);
			for (var segment : composite.segments()) {
				result.addAll(resolveConfig(segment.mover()));
			}
			return result;
		}

		// Layered: union of all layer overrides
		if (config instanceof MoverConfigs.LayeredMoverConfig layered) {
			EnumSet<OverriddenParam> result = EnumSet.noneOf(OverriddenParam.class);
			for (var layer : layered.layers()) {
				result.addAll(resolveConfig(layer));
			}
			return result;
		}

		// Attached types: override speed + origin offsets
		if (ATTACHED_TYPES.contains(type)) {
			return EnumSet.of(
					OverriddenParam.SPEED,
					OverriddenParam.OFFSET_X,
					OverriddenParam.OFFSET_Y,
					OverriddenParam.OFFSET_Z
			);
		}

		// Position-controlling: override speed only
		if (POSITION_CONTROLLING.contains(type)) {
			return EnumSet.of(OverriddenParam.SPEED);
		}

		// Non-overriding types (acceleration, deceleration, rotate, fixed_dir, space_rotation)
		// and unrecognized types: no override
		return Collections.emptySet();
	}

	/**
	 * Convenience: checks if a specific parameter label is overridden.
	 * Maps editor row labels to OverriddenParam enum values.
	 * Supports both full labels ("Offset X") and abbreviated labels ("Off X") used in the editor.
	 */
	public static boolean isLabelOverridden(String label, Set<OverriddenParam> overrides) {
		return switch (label) {
			case "Speed" -> overrides.contains(OverriddenParam.SPEED);
			case "Offset X", "Off X" -> overrides.contains(OverriddenParam.OFFSET_X);
			case "Offset Y", "Off Y" -> overrides.contains(OverriddenParam.OFFSET_Y);
			case "Offset Z", "Off Z" -> overrides.contains(OverriddenParam.OFFSET_Z);
			default -> false;
		};
	}

	/**
	 * Returns a human-readable tooltip for the override reason.
	 */
	public static String getTooltip(Optional<MoverConfig> moverOpt) {
		if (moverOpt.isEmpty()) return "";
		String type = MoverConfigs.getTypeId(moverOpt.get());
		if (type == null) return "";
		return "Overridden by " + type + " mover";
	}
}
