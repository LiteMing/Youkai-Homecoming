package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;

import java.util.function.Supplier;

/**
 * Bridge action that wraps a legacy ActualSpellCard as a SpellAction.
 * The legacy card's tick() is called each time execute() is invoked.
 */
public class LegacyTickerAction implements SpellAction {

	public static final Codec<LegacyTickerAction> CODEC = Codec.unit(LegacyTickerAction::new);

	private Supplier<? extends SpellCard> factory;
	private SpellCard card;

	public LegacyTickerAction() {
	}

	public LegacyTickerAction(Supplier<? extends SpellCard> factory) {
		this.factory = factory;
	}

	@Override
	public void execute(SpellContext ctx) {
		if (card == null) {
			if (factory != null) {
				card = factory.get();
			} else {
				return;
			}
		}
		card.tick(ctx.holder());
	}

	public void reset() {
		if (card != null) {
			card.reset();
		}
	}

	public SpellCard getCard() {
		return card;
	}

	/** False after JSON decode (Codec.unit drops the factory). */
	public boolean isBound() {
		return factory != null || card != null;
	}
}
