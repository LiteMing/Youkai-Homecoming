package dev.xkmc.youkaishomecoming.compat.stg.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Public STG / danmaku combat lifecycle events for external mods (CreatureChat, KubeJS, etc.).
 *
 * <p>These are <strong>not cancelable</strong>. Pre-defeat intervention remains on
 * {@link dev.xkmc.youkaishomecoming.events.DanmakuLastHitEvent} (cancelable last-life absorb).
 * Use {@link Defeat} / {@link Victory} for settlement dialogue and score hooks.</p>
 */
public abstract class StgCombatEvent extends PlayerEvent {

	private final List<UUID> opponentIds;
	private final List<LivingEntity> opponents;

	protected StgCombatEvent(ServerPlayer player, List<UUID> opponentIds, List<LivingEntity> opponents) {
		super(player);
		this.opponentIds = List.copyOf(Objects.requireNonNull(opponentIds, "opponentIds"));
		this.opponents = List.copyOf(Objects.requireNonNull(opponents, "opponents"));
	}

	@Override
	public ServerPlayer getEntity() {
		return (ServerPlayer) super.getEntity();
	}

	/** UUID snapshot of Youkai sessions and player opponents at event time. */
	public List<UUID> getOpponentIds() {
		return opponentIds;
	}

	/**
	 * Loaded living opponents at event time (subset of {@link #getOpponentIds()} that were
	 * resolvable in the server level). Prefer this for dialogue / targeting.
	 */
	public List<LivingEntity> getOpponents() {
		return opponents;
	}

	/**
	 * Fired when a Youkai STG session opens for the player (first time this Youkai enters
	 * the player's active session map).
	 */
	public static final class SessionStart extends StgCombatEvent {
		private final LivingEntity opponent;

		public SessionStart(ServerPlayer player, LivingEntity opponent,
				List<UUID> opponentIds, List<LivingEntity> opponents) {
			super(player, opponentIds, opponents);
			this.opponent = Objects.requireNonNull(opponent, "opponent");
		}

		public LivingEntity getOpponent() {
			return opponent;
		}
	}

	/**
	 * Fired when one Youkai session ends without a full player defeat
	 * (e.g. Youkai progress cleared / session removed while player still has lives).
	 */
	public static final class SessionEnd extends StgCombatEvent {
		private final UUID sessionId;
		@Nullable
		private final LivingEntity opponent;
		private final SessionEndReason reason;

		public SessionEnd(ServerPlayer player, UUID sessionId, @Nullable LivingEntity opponent,
				SessionEndReason reason, List<UUID> opponentIds, List<LivingEntity> opponents) {
			super(player, opponentIds, opponents);
			this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
			this.opponent = opponent;
			this.reason = Objects.requireNonNull(reason, "reason");
		}

		public UUID getSessionId() {
			return sessionId;
		}

		@Nullable
		public LivingEntity getOpponent() {
			return opponent;
		}

		public SessionEndReason getReason() {
			return reason;
		}
	}

	/**
	 * Player lost STG combat (no remaining life shards). Combat state is already being cleared.
	 * Not cancelable — use {@link dev.xkmc.youkaishomecoming.events.DanmakuLastHitEvent} to prevent defeat.
	 */
	public static final class Defeat extends StgCombatEvent {
		@Nullable
		private final LivingEntity fatalSource;

		public Defeat(ServerPlayer player, @Nullable LivingEntity fatalSource,
				List<UUID> opponentIds, List<LivingEntity> opponents) {
			super(player, opponentIds, opponents);
			this.fatalSource = fatalSource;
		}

		/** Entity that delivered the last-life hit, if known. */
		@Nullable
		public LivingEntity getFatalSource() {
			return fatalSource;
		}
	}

	/**
	 * Player cleared a Youkai via combat progress (danmaku / combat HP) while in an STG session.
	 * Fired from {@link dev.xkmc.youkaishomecoming.content.capability.GrazeHelper#onDanmakuKill}.
	 */
	public static final class Victory extends StgCombatEvent {
		private final LivingEntity defeated;

		public Victory(ServerPlayer player, LivingEntity defeated,
				List<UUID> opponentIds, List<LivingEntity> opponents) {
			super(player, opponentIds, opponents);
			this.defeated = Objects.requireNonNull(defeated, "defeated");
		}

		public LivingEntity getDefeated() {
			return defeated;
		}
	}

	public enum SessionEndReason {
		/** Youkai removed / progress cleared (player win path). */
		VICTORY,
		/** Session dropped without a kill (distance, target list, manual clear, etc.). */
		CLEARED
	}
}
