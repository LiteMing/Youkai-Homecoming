package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

public record EraseEnemyDanmakuAction(
		NumberProvider radius,
		boolean sessionsOnly
) implements SpellAction {

	public static final Codec<EraseEnemyDanmakuAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			NumberProvider.CODEC.optionalFieldOf("radius", NumberProvider.constant(4)).forGetter(EraseEnemyDanmakuAction::radius),
			Codec.BOOL.optionalFieldOf("sessions_only", false).forGetter(EraseEnemyDanmakuAction::sessionsOnly)
	).apply(i, EraseEnemyDanmakuAction::new));

	@Override
	public void execute(SpellContext ctx) {
		double r = Math.max(0, radius.get(ctx));
		EnemyDanmakuEraser.erase(ctx.holder(), ctx.holder().center(), r, sessionsOnly);
	}

}
