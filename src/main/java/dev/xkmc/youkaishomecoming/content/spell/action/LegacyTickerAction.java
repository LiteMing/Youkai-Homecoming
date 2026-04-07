package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;

import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridge action that wraps a legacy ActualSpellCard as a SpellAction.
 * The legacy card's tick() is called each time execute() is invoked.
 */
public class LegacyTickerAction implements SpellAction {

	public static final Codec<LegacyTickerAction> CODEC = Codec.unit(LegacyTickerAction::new);
	private static final AtomicBoolean WARNED_MISSING_FACTORY = new AtomicBoolean(false);

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
				if (WARNED_MISSING_FACTORY.compareAndSet(false, true)) {
					YoukaisHomecoming.LOGGER.warn(
							"Encountered deserialized legacy_ticker action without a factory. " +
							"It will no-op at runtime; prefer data-driven actions or LegacySpellBridge-registered definitions.");
				}
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
}
