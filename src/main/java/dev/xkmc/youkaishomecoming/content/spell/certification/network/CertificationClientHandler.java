package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache of certification state projections (design doc D12). The
 * client renders the certification HUD / spell circle purely from this cache;
 * it never declares success or failure itself.
 */
public final class CertificationClientHandler {

	public record ClientState(CertificationState state, int elapsedTicks, int targetTicks,
							  int healthTotal, int healthLeft,
							  @Nullable String failReason) {

		public boolean active() {
			return state == CertificationState.PREPARE || state == CertificationState.ACTIVE;
		}

		public double progress() {
			return targetTicks <= 0 ? 0 : Math.min(1.0, (double) elapsedTicks / targetTicks);
		}
	}

	private static final Map<Integer, ClientState> STATES = new HashMap<>();
	@Nullable
	private static ClientState myState;

	private CertificationClientHandler() {
	}

	public static void acceptState(int entityId, String state, int elapsedTicks, int targetTicks,
								   int healthTotal, int healthLeft,
								   @Nullable String failReason, boolean mine) {
		CertificationState parsed;
		try {
			parsed = CertificationState.valueOf(state);
		} catch (IllegalArgumentException e) {
			parsed = CertificationState.DRAFT;
		}
		ClientState clientState = new ClientState(parsed, elapsedTicks, targetTicks,
				healthTotal, healthLeft, failReason);
		if (mine) {
			myState = clientState;
		} else {
			STATES.put(entityId, clientState);
		}
	}

	/** The author's own certification state (D4: alpha fade disabled while active). */
	@Nullable
	public static ClientState getMyState() {
		return myState;
	}

	/** True while the author's own trial is preparing or active (alpha pinned to 1, D4). */
	public static boolean inMyTrial() {
		return myState != null && myState.active();
	}

	@Nullable
	public static ClientState getState(int entityId) {
		return STATES.get(entityId);
	}

	@Nullable
	public static ClientState getStateFor(Level level, Entity entity) {
		return getState(entity.getId());
	}

	public static void clear(int entityId) {
		STATES.remove(entityId);
	}

	// ------------------------------------------------------------ quote cache

	@Nullable
	private static CertificationQuoteToClient pendingQuote;

	public static void acceptQuote(CertificationQuoteToClient quote) {
		pendingQuote = quote;
	}

	@Nullable
	public static CertificationQuoteToClient getPendingQuote() {
		return pendingQuote;
	}

	public static void clearPendingQuote() {
		pendingQuote = null;
	}

	// ------------------------------------------------------------ reward

	private static String lastRewardHash = "";
	private static String lastRewardName = "";

	public static void acceptReward(String definitionHash, String spellName) {
		lastRewardHash = definitionHash;
		lastRewardName = spellName;
	}

	public static String lastRewardHash() {
		return lastRewardHash;
	}

	public static String lastRewardName() {
		return lastRewardName;
	}
}
