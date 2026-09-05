package dev.xkmc.youkaishomecoming.content.capability;

import java.util.Collection;
import java.util.UUID;

/** Pure policy table for player spell targeting and defeated-player protection. */
public final class PlayerDanmakuPolicy {

	private PlayerDanmakuPolicy() {
	}

	public enum TargetDisposition {
		FRIENDLY, NEUTRAL, HOSTILE
	}

	public static TargetDisposition classifyTarget(boolean player, boolean playerHasTeam,
			boolean markedHostile, boolean yhYoukai, boolean enemy, boolean neutralMob) {
		if (player) {
			return playerHasTeam ? TargetDisposition.HOSTILE : TargetDisposition.NEUTRAL;
		}
		if (markedHostile) {
			return TargetDisposition.HOSTILE;
		}
		if (yhYoukai) {
			return TargetDisposition.NEUTRAL;
		}
		if (enemy) {
			return TargetDisposition.HOSTILE;
		}
		if (neutralMob) {
			return TargetDisposition.NEUTRAL;
		}
		return TargetDisposition.FRIENDLY;
	}

	public static boolean isUntargetedTarget(TargetDisposition disposition, boolean engaged) {
		return disposition == TargetDisposition.HOSTILE || engaged;
	}

	public static boolean canReceiveDanmaku(boolean beaten) {
		return !beaten;
	}

	public static boolean hasForeignSession(Collection<UUID> sessionIds, UUID certificationEntityId) {
		return sessionIds.stream().anyMatch(id -> !id.equals(certificationEntityId));
	}

}
