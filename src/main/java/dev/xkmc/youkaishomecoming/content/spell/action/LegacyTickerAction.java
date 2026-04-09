package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.LivingCardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Bridge action that wraps a legacy ActualSpellCard as a SpellAction.
 * The legacy card's tick() is called each time execute() is invoked.
 */
public class LegacyTickerAction implements SpellAction {

	public static final Codec<LegacyTickerAction> CODEC = Codec.unit(LegacyTickerAction::new);
	private static final Set<ResourceLocation> WARNED_MISSING_FACTORY = ConcurrentHashMap.newKeySet();
	private static final Set<String> WARNED_PLAYERS = ConcurrentHashMap.newKeySet();

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
				ResourceLocation spellId = ctx.definition().id;
				if (WARNED_MISSING_FACTORY.add(spellId)) {
					YoukaisHomecoming.LOGGER.warn(
							"Encountered deserialized legacy_ticker action without a factory for spell {}. " +
							"It will no-op at runtime; prefer data-driven actions or LegacySpellBridge-registered definitions.",
							spellId);
				}
				ServerPlayer player = getNotifiablePlayer(ctx);
				if (player != null && WARNED_PLAYERS.add(spellId + "|" + player.getStringUUID())) {
					player.displayClientMessage(
							Component.literal("Broken spell data for " + spellId + "; reacquire or reload this spell.")
									.withStyle(ChatFormatting.RED),
							true);
				}
				return;
			}
		}
		card.tick(ctx.holder());
	}

	private static ServerPlayer getNotifiablePlayer(SpellContext ctx) {
		if (ctx.holder() instanceof LivingCardHolder holder && holder.shooter() instanceof ServerPlayer player) {
			return player;
		}
		return ctx.self() instanceof ServerPlayer player ? player : null;
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
