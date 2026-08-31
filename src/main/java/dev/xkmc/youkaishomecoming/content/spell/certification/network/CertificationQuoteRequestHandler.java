package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationQuote;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationService;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisException;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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
		ItemStack draft = CertificationService.findUnfinishedDraft(player, definition.id);
		if (draft.isEmpty()) {
			player.displayClientMessage(YHLangData.CERT_QUOTE_FAIL.get(
					YHLangData.CERT_QUOTE_MISSING_DRAFT.get()), false);
			return;
		}
		CertificationQuote quote;
		try {
			definition = dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem
					.applyDraftTraits(draft, definition);
			dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry.register(definition);
			if (!dev.xkmc.youkaishomecoming.content.spell.runtime.CustomSpellStorage
					.saveSpell(player.server, definition)) {
				throw new IllegalStateException("Failed to persist draft card traits");
			}
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
		} catch (RuntimeException e) {
			YoukaisHomecoming.LOGGER.error("Certification quote failed for {}", definition.id, e);
			player.displayClientMessage(YHLangData.CERT_QUOTE_FAIL.get(
					YHLangData.CERT_QUOTE_INTERNAL_ERROR.get()), false);
			return;
		}
		CertificationManager.INSTANCE.setQuote(player, quote, definition);
		YoukaisHomecoming.HANDLER.toClientPlayer(
				new CertificationQuoteToClient(quote), player);
	}
}
