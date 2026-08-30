package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext;

public record DiscardSourceAction() implements SpellAction {
	public static final Codec<DiscardSourceAction> CODEC = Codec.unit(DiscardSourceAction::new);

	@Override
	public void execute(SpellContext ctx) {
		ctx.hitContext().ifPresent(hit -> hit.resolve(SpellHitContext.HitDisposition.DISCARD));
	}
}
