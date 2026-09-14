package dev.xkmc.youkaishomecoming.content.spell.analysis;

import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Resolves the effective player level used by server-side spell execution. */
public final class SpellPermissionService {

	private SpellPermissionService() {
	}

	public static int effectiveLevel(@Nullable Player player) {
		if (player == null) return 4;
		GrazeCapability capability = GrazeCapability.HOLDER.get(player);
		int override = capability.getSpellPermissionOverride();
		if (override >= 0) return clamp(override);
		if (player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2)) return 4;
		if (!(player instanceof ServerPlayer serverPlayer)) return capability.getSpellPermissionLevel();
		if (YHModConfig.COMMON.spellPermissionCardProgression.get()) {
			if (hasAdvancement(serverPlayer, "spellcard_tier12")) return 3;
			if (hasAdvancement(serverPlayer, "spellcard_tier6")) return 2;
			return 1;
		}
		return 1;
	}

	private static boolean hasAdvancement(ServerPlayer player, String path) {
		var advancement = player.server.getAdvancements().getAdvancement(
				new ResourceLocation("youkaishomecoming", "main/" + path));
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	/** Returns true when at least one action is unavailable at the supplied level. */
	public static boolean hasUnavailableCapabilities(dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition definition,
			int level) {
		return SpellCapabilityPolicies.hasUnavailableCapabilities(definition, level);
	}

	public static int clamp(int level) {
		return Math.max(0, Math.min(4, level));
	}

}
