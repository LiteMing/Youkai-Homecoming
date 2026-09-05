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
		CertificationQuote quote = pendingQuotes.get(player.getUUID());
		if (quote == null) return null;
		long age = player.level().getGameTime() - quote.issuedAtGameTime();
		if (age < 0 || age > quoteTtlTicks()) {
			pendingQuotes.remove(player.getUUID());
			quoteDefinitions.remove(quote.quoteId());
			return null;
		}
		return quote;
	}

	public CertificationQuote setQuote(ServerPlayer player, CertificationQuote quote, SpellDefinition definition) {
		purgeExpired(player.level().getGameTime());
		CertificationQuote previous = pendingQuotes.get(player.getUUID());
		if (previous != null) quoteDefinitions.remove(previous.quoteId());
		quoteDefinitions.put(quote.quoteId(), quote.healthPlan().rootDefinition());
		return pendingQuotes.put(player.getUUID(), quote);
	}

	public SpellDefinition getQuoteDefinition(String quoteId) {
		return quoteDefinitions.get(quoteId);
	}

	public boolean hasActiveTrial(ServerPlayer player) {
		return getActiveTrial(player) != null;
	}

	public CertificationController getActiveTrial(ServerPlayer player) {
		CertificationController controller = activeTrials.get(player.getUUID());
		if (controller != null && controller.author() != player) {
			activeTrials.remove(player.getUUID(), controller);
			return null;
		}
		return controller;
	}

	/** Registers the trial; returns false when the player or server cap is reached. */
	public boolean register(ServerPlayer player, CertificationController controller) {
		if (hasActiveTrial(player)) return false;
		if (activeTrials.size() >= YHModConfig.COMMON.certificationMaxConcurrentTrials.get()) return false;
		activeTrials.put(player.getUUID(), controller);
		return true;
	}

	public void remove(UUID playerId) {
		clearQuote(playerId);
		activeTrials.remove(playerId);
	}

	/** Removes a completed controller without clearing a newer trial for the same player. */
	public void remove(UUID playerId, CertificationController controller) {
		if (activeTrials.remove(playerId, controller)) {
			clearQuote(playerId);
		}
	}

	/** Clears process-local state when a new logical server starts. */
	public void reset() {
		activeTrials.clear();
		pendingQuotes.clear();
		quoteDefinitions.clear();
	}

	/** Drops a pending quote without touching an active controller. */
	public void clearQuote(UUID playerId) {
		CertificationQuote quote = pendingQuotes.remove(playerId);
		if (quote != null) quoteDefinitions.remove(quote.quoteId());
	}

	private void purgeExpired(long now) {
		var iterator = pendingQuotes.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			long age = now - entry.getValue().issuedAtGameTime();
			if (age < 0 || age > quoteTtlTicks()) {
				quoteDefinitions.remove(entry.getValue().quoteId());
				iterator.remove();
			}
		}
	}

	private static long quoteTtlTicks() {
		return 1200;
	}

	public int activeCount() {
		return activeTrials.size();
	}
}
