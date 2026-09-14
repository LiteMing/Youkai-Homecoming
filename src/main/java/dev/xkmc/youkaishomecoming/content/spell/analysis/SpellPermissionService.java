package dev.xkmc.youkaishomecoming.content.spell.analysis;

import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
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
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return override >= 0 ? clamp(override) : capability.getSpellPermissionLevel();
		}
		boolean operator = serverPlayer.hasPermissions(2);
		boolean progression = YHModConfig.COMMON.spellPermissionCardProgression.get();
		boolean readAdvancements = override < 0 && !operator && progression;
		return resolveLevel(override, operator, progression,
				readAdvancements && hasAdvancement(serverPlayer, "spellcard_tier6"),
				readAdvancements && hasAdvancement(serverPlayer, "spellcard_tier12"));
	}

	static int resolveLevel(int override, boolean operator, boolean progression,
			boolean tier6, boolean tier12) {
		if (override >= 0) return clamp(override);
		if (operator) return SpellCapabilityPermission.OP.level();
		if (progression && tier12) return SpellCapabilityPermission.EXPERIMENTAL.level();
		if (progression && tier6) return SpellCapabilityPermission.HOOK.level();
		return SpellCapabilityPermission.BASE.level();
	}

	public static boolean canCast(Player player) {
		return effectiveLevel(player) > SpellCapabilityPermission.FORBID.level();
	}

	/** Called before payment, cooldowns or proxy creation, including direct API casts. */
	public static boolean canCastWithMessage(Player player) {
		if (canCast(player)) return true;
		if (player instanceof ServerPlayer) {
			player.displayClientMessage(YHLangData.SPELL_PERMISSION_FORBIDDEN.get(), true);
		}
		return false;
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
