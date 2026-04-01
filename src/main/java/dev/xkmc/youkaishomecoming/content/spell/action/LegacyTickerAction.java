package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;

import java.util.function.Supplier;

/**
 * Bridge action that wraps a legacy ActualSpellCard as a SpellAction.
 * The legacy card's tick() is called each time execute() is invoked.
 */
public class LegacyTickerAction implements SpellAction {

	public static final Codec<LegacyTickerAction> CODEC = Codec.unit(LegacyTickerAction::new);

	private Supplier<? extends SpellCard> factory;

	public LegacyTickerAction() {
	}

	public LegacyTickerAction(Supplier<? extends SpellCard> factory) {
		this.factory = factory;
	}

	@Override
	public void execute(SpellContext ctx) {
		if (factory == null) {
			return;
		}
		SpellCard card = ctx.runtime().getOrCreateState(this, factory);
		card.tick(ctx.holder());
	}

	public SpellCard getCard(SpellRuntime runtime) {
		return runtime.getState(this, SpellCard.class);
	}
}
