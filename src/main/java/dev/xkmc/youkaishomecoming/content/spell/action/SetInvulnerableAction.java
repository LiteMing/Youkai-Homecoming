package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

/** Timed boss protection for declarations; never grants invulnerability to a player or a proxy owner. */
public record SetInvulnerableAction(NumberProvider duration) implements SpellAction {

	public static final Codec<SetInvulnerableAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			NumberProvider.CODEC.fieldOf("duration").forGetter(SetInvulnerableAction::duration)
	).apply(i, SetInvulnerableAction::new));

	@Override
	public void execute(SpellContext ctx) {
		if (ctx.holder() instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			ctx.runtime().setCasterInvulnerability(preview.getYsmPresentationTime(), durationTicks(ctx));
			return;
		}
		if (!(ctx.self() instanceof YoukaiEntity youkai) || youkai.level().isClientSide()) return;
		var runtime = youkai.getSpellRuntime();
		if (runtime == null) return;
		// A child pattern also protects the same boss, with lifetime owned by its
		// main spell. Replacing or resetting that spell drops the protection.
		runtime.setCasterInvulnerability(youkai.level().getGameTime(), durationTicks(ctx));
	}

	private int durationTicks(SpellContext ctx) {
		double value = duration.get(ctx);
		return Double.isFinite(value) ? Math.max(0, (int) value) : 0;
	}
}
