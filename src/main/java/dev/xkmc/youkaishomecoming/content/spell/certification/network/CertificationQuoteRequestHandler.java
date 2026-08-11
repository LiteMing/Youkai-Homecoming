package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationQuote;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationService;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisException;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side quote handling: analyze (certification profile), hash, cost and
 * cache the quote + definition keyed by quoteId. The player must hold an
 * unfinished card bound to the definition, but authorship is irrelevant; the
 * analyzer and health-plan validator decide whether it is certifiable.
 */
public final class CertificationQuoteRequestHandler {

	private CertificationQuoteRequestHandler() {
	}

	public static void accept(ServerPlayer player, SpellDefinition definition) {
		if (!dev.xkmc.youkaishomecoming.init.data.YHModConfig.COMMON.certificationEnabled.get()) {
			player.displayClientMessage(YHLangData.CERT_DISABLED.get(), false);
			return;
		}
		if (!CertificationService.hasUnfinishedDraft(player, definition.id)) {
			player.displayClientMessage(YHLangData.CERT_QUOTE_FAIL.get(
					YHLangData.CERT_QUOTE_MISSING_DRAFT.get()), false);
			return;
		}
		CertificationQuote quote;
		try {
			quote = CertificationService.quote(player, definition);
		} catch (SpellAnalysisException e) {
			YoukaisHomecoming.LOGGER.info("Certification quote rejected by spell analysis for {}: {}",
					definition.id, e.getMessage());
			player.displayClientMessage(YHLangData.CERT_QUOTE_FAIL.get(
					YHLangData.CERT_QUOTE_ANALYSIS_REJECTED.get()), false);
			return;
		} catch (IllegalArgumentException e) {
			YoukaisHomecoming.LOGGER.info("Certification quote rejected by spell-health plan for {}: {}",
					definition.id, e.getMessage());
			player.displayClientMessage(YHLangData.CERT_QUOTE_FAIL.get(
					YHLangData.CERT_QUOTE_INVALID_HEALTH_PLAN.get()), false);
			return;
		}
		CertificationManager.INSTANCE.setQuote(player, quote, definition);
		YoukaisHomecoming.HANDLER.toClientPlayer(
				new CertificationQuoteToClient(quote), player);
	}
}
