package dev.xkmc.youkaishomecoming.content.spell.analysis;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-server developer switch for non-spell exploration.
 *
 * <p>The switch is deliberately runtime-only. It is never read from client
 * state or spell JSON and is cleared when the server stops.</p>
 */
public final class NonSpellLimiterBypass {
	private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();

	private NonSpellLimiterBypass() {
	}

	public static boolean isEnabled(@Nullable ServerPlayer player) {
		return player != null && ENABLED.contains(player.getUUID());
	}

	public static void set(ServerPlayer player, boolean enabled) {
		if (enabled) {
			ENABLED.add(player.getUUID());
		} else {
			ENABLED.remove(player.getUUID());
		}
	}

	public static void clear() {
		ENABLED.clear();
	}
}
