package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

/** Pauses this spell runtime's ordinary onTick clock without changing caster movement or invulnerability. */
public record FreezeOnTickAction(NumberProvider duration) implements SpellAction {

	public static final Codec<FreezeOnTickAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			NumberProvider.CODEC.fieldOf("duration").forGetter(FreezeOnTickAction::duration)
	).apply(i, FreezeOnTickAction::new));

	@Override
	public void execute(SpellContext ctx) {
		long gameTime;
		if (ctx.holder() instanceof PreviewCardHolder preview) {
			gameTime = preview.getYsmPresentationTime();
		} else {
			if (ctx.self() == null || ctx.self().level().isClientSide()) return;
			gameTime = ctx.self().level().getGameTime();
		}
		ctx.runtime().setOnTickFreeze(gameTime, durationTicks(ctx));
	}

	private int durationTicks(SpellContext ctx) {
		double value = duration.get(ctx);
		return Double.isFinite(value) ? Math.max(0, (int) value) : 0;
	}
}
