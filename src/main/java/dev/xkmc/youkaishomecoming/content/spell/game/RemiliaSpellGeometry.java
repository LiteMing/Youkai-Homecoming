package dev.xkmc.youkaishomecoming.content.spell.game;

import dev.xkmc.youkaishomecoming.content.spell.definition.*;

import java.util.Objects;

/** Remilia's shared world-axis offsets and laser frames, independent of mod registration. */
public final class RemiliaSpellGeometry {
	private RemiliaSpellGeometry() {}

	public static OriginConfig midpoint() {
		return new OriginConfig(OriginConfig.OriginMode.CASTER,
				number("(target_x - caster_x) / 2"), number("(target_y - caster_y) / 2"),
				number("(target_z - caster_z) / 2"), NumberProvider.constant(0));
	}

	public static OriginConfig towardTarget(NumberProvider fraction, NumberProvider jitter) {
		return new OriginConfig(OriginConfig.OriginMode.CASTER,
				new NumberProviders.Add(new NumberProviders.Mul(number("target_x - caster_x"), fraction), jitter),
				new NumberProviders.Add(new NumberProviders.Mul(number("target_y - caster_y"), fraction), jitter),
				new NumberProviders.Add(new NumberProviders.Mul(number("target_z - caster_z"), fraction), jitter),
				NumberProvider.constant(0));
	}

	public static OriginConfig laserOrigin(boolean branch) {
		return new OriginConfig(OriginConfig.OriginMode.CASTER, NumberProvider.constant(0),
				branch ? number("sin_deg($laser_pitch) * $laser_length") : NumberProvider.constant(0),
				branch ? number("cos_deg($laser_pitch) * $laser_length") : NumberProvider.constant(0), number("$laser_yaw"));
	}

	public static GroupRotation laserRotation(boolean branch) {
		return new GroupRotation(number(branch ? "$laser_pitch + 90" : "$laser_pitch"),
				NumberProvider.constant(0), NumberProvider.constant(0));
	}

	public static NumberProvider number(String expression) {
		return Objects.requireNonNull(NumberExprParser.parse(expression), expression);
	}
}
