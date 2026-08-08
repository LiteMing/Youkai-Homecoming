package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationQuote;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationService;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side quote handling: analyze (certification profile), hash, cost and
 * cache the quote + definition keyed by quoteId (design doc §5.2, §18).
 */
public final class CertificationQuoteRequestHandler {

	private CertificationQuoteRequestHandler() {
	}

	public static void accept(ServerPlayer player, SpellDefinition definition,
							  int requestedDurationTicks, double requestedHalfSize) {
		if (!dev.xkmc.youkaishomecoming.init.data.YHModConfig.COMMON.certificationEnabled.get()) {
			player.displayClientMessage(YHLangData.CERT_DISABLED.get(), false);
			return;
		}
		CertificationQuote quote;
		try {
			quote = CertificationService.quote(player, definition, requestedDurationTicks, requestedHalfSize);
		} catch (IllegalArgumentException e) {
			player.displayClientMessage(YHLangData.CERT_QUOTE_FAIL.get(e.getMessage()), false);
			return;
		}
		CertificationManager.INSTANCE.setQuote(player, quote, definition);
		YoukaisHomecoming.HANDLER.toClientPlayer(
				new CertificationQuoteToClient(quote), player);
	}
}
