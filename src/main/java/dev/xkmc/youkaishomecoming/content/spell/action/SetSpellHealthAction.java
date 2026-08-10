package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.util.StringRepresentable;

/**
 * Declares one boss spell-card health segment and its optional countdown.
 * This is an operator-only node: player certification and market imports reject it.
 */
public record SetSpellHealthAction(Mode mode, NumberProvider health,
									NumberProvider duration) implements SpellAction {

	public enum Mode implements StringRepresentable {
		SET("set"), CLEAR("clear");

		public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
		private final String serializedName;

		Mode(String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return serializedName;
		}
	}

	public static final Codec<SetSpellHealthAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Mode.CODEC.optionalFieldOf("mode", Mode.SET).forGetter(SetSpellHealthAction::mode),
			NumberProvider.CODEC.optionalFieldOf("health", NumberProvider.constant(100))
					.forGetter(SetSpellHealthAction::health),
			NumberProvider.CODEC.optionalFieldOf("duration", NumberProvider.constant(1200))
					.forGetter(SetSpellHealthAction::duration)
	).apply(i, SetSpellHealthAction::new));

	@Override
	public void execute(SpellContext ctx) {
		if (ctx.host() == null || !ctx.host().isBossHost()) {
			return;
		}
		if (mode == Mode.CLEAR) {
			ctx.runtime().clearSpellHealth();
			ctx.host().syncSpellState();
			return;
		}
		int maxHealth = clamp(health.get(ctx), 1, 1_000_000);
		int durationTicks = clamp(duration.get(ctx), 0, 1_000_000);
		ctx.runtime().setSpellHealth(maxHealth, durationTicks);
		if (ctx.self() instanceof YoukaiEntity youkai && youkai.combatProgress != null) {
			youkai.combatProgress.maxProgress = maxHealth;
			youkai.setCombatProgress(maxHealth);
			youkai.validateData();
		}
		ctx.host().syncSpellState();
	}

	private static int clamp(double value, int min, int max) {
		if (!Double.isFinite(value)) return min;
		return Math.max(min, Math.min(max, (int) Math.round(value)));
	}
}
