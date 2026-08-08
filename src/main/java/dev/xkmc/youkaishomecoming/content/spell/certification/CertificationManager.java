package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global certification trial registry (design doc D11): one active trial per
 * player (MVP fixed at 1), server-wide concurrency cap from config. A new quote
 * invalidates the player's previous quote.
 */
public final class CertificationManager {

	public static final CertificationManager INSTANCE = new CertificationManager();

	private final Map<UUID, CertificationController> activeTrials = new HashMap<>();
	private final Map<UUID, CertificationQuote> pendingQuotes = new HashMap<>();
	private final Map<String, SpellDefinition> quoteDefinitions = new HashMap<>();

	private CertificationManager() {
	}

	public CertificationQuote getQuote(ServerPlayer player) {
		return pendingQuotes.get(player.getUUID());
	}

	public CertificationQuote setQuote(ServerPlayer player, CertificationQuote quote, SpellDefinition definition) {
		quoteDefinitions.put(quote.quoteId(), definition);
		return pendingQuotes.put(player.getUUID(), quote);
	}

	public SpellDefinition getQuoteDefinition(String quoteId) {
		return quoteDefinitions.get(quoteId);
	}

	public boolean hasActiveTrial(ServerPlayer player) {
		return activeTrials.containsKey(player.getUUID());
	}

	public CertificationController getActiveTrial(ServerPlayer player) {
		return activeTrials.get(player.getUUID());
	}

	/** Registers the trial; returns false when the player or server cap is reached. */
	public boolean register(ServerPlayer player, CertificationController controller) {
		if (activeTrials.containsKey(player.getUUID())) return false;
		if (activeTrials.size() >= YHModConfig.COMMON.certificationMaxConcurrentTrials.get()) return false;
		activeTrials.put(player.getUUID(), controller);
		return true;
	}

	public void remove(UUID playerId) {
		CertificationQuote quote = pendingQuotes.remove(playerId);
		if (quote != null) quoteDefinitions.remove(quote.quoteId());
		activeTrials.remove(playerId);
	}

	public int activeCount() {
		return activeTrials.size();
	}
}
