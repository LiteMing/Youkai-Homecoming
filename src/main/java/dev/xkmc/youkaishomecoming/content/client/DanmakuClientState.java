package dev.xkmc.youkaishomecoming.content.client;

import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Client-only projection of the local player's danmaku visibility state. */
public final class DanmakuClientState {
	private DanmakuClientState() {
	}

	public static boolean isLocalPlayerSuppressed() {
		Player player = Minecraft.getInstance().player;
		if (player == null) return false;
		var cap = GrazeCapability.HOLDER.get(player);
		return cap.isPlayerSpellActive() || cap.isInvul() || cap.isWeak();
	}
}
