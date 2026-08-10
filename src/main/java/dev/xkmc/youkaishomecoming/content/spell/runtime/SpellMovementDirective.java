package dev.xkmc.youkaishomecoming.content.spell.runtime;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;

/**
 * Per-tick caster movement selected by a spell action.
 * RANDOM leaves player input unrestricted; certification maps it to its
 * server-authoritative bounded random movement.
 */
public record SpellMovementDirective(Mode mode, Vec3 displacement) {

	private static final SpellMovementDirective RANDOM = new SpellMovementDirective(Mode.RANDOM, Vec3.ZERO);

	public enum Mode implements StringRepresentable {
		RANDOM("random"),
		NONE("none"),
		RELATIVE("relative"),
		ABSOLUTE("absolute");

		public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

		private final String name;

		Mode(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static SpellMovementDirective random() {
		return RANDOM;
	}

	public boolean restrictsManualMovement() {
		return mode != Mode.RANDOM;
	}
}
