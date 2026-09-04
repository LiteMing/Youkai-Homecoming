package dev.xkmc.youkaishomecoming.compat.stg.control;

import dev.xkmc.youkaishomecoming.compat.stg.YHStgApi;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-owned, memory-only state for classic planar controls. */
public final class ClassicControlService {

	private static final Set<UUID> ENABLED = new HashSet<>();
	private static final Set<UUID> HINTED = new HashSet<>();

	private ClassicControlService() {
	}

	public static boolean isEnabled(ServerPlayer player) {
		return ENABLED.contains(player.getUUID());
	}

	public static boolean setEnabled(ServerPlayer player, boolean enabled) {
		if (enabled && !YHStgApi.isInDanmakuSession(player)) {
			sync(player, false);
			player.displayClientMessage(YHLangData.CLASSIC_CONTROL_COMBAT_ONLY.get(), true);
			return false;
		}
		if (enabled) {
			ENABLED.add(player.getUUID());
			HINTED.add(player.getUUID());
		} else {
			ENABLED.remove(player.getUUID());
			if (YHStgApi.isInDanmakuSession(player)) HINTED.add(player.getUUID());
			else HINTED.remove(player.getUUID());
		}
		sync(player, enabled);
		player.displayClientMessage((enabled
				? YHLangData.CLASSIC_CONTROL_ENABLED
				: YHLangData.CLASSIC_CONTROL_DISABLED).get(), true);
		return true;
	}

	public static void tick(ServerPlayer player) {
		boolean inCombat = player.isAlive() && YHStgApi.isInDanmakuSession(player);
		if (isEnabled(player) && !inCombat) {
			setEnabled(player, false);
			return;
		}
		if (!inCombat) {
			HINTED.remove(player.getUUID());
		} else if (!isEnabled(player) && HINTED.add(player.getUUID())) {
			player.displayClientMessage(YHLangData.CLASSIC_CONTROL_AVAILABLE.get(), true);
		}
	}

	/** Removes transient state without presenting a toggle message. */
	public static void reset(ServerPlayer player) {
		boolean removed = ENABLED.remove(player.getUUID());
		HINTED.remove(player.getUUID());
		if (removed || GrazeCapability.HOLDER.get(player).getActiveNonSpellCardKey() != null) {
			SpellContainer.clearActiveNonSpell(player);
		}
		sync(player, false);
	}

	public static void clearAll() {
		ENABLED.clear();
		HINTED.clear();
	}

	public static void handleInput(ServerPlayer player, int action) {
		if (action == ClassicControlRequestToServer.TOGGLE_MODE) {
			setEnabled(player, !isEnabled(player));
			return;
		}
		if (action == ClassicControlRequestToServer.NON_SPELL_OFF) {
			SpellContainer.clearActiveNonSpell(player);
			return;
		}
		if (!YHStgApi.isInDanmakuSession(player)) return;
		switch (action) {
			case ClassicControlRequestToServer.NON_SPELL_ON -> enableNonSpell(player);
			case ClassicControlRequestToServer.CAST_NEXT_SPELL -> {
				if (!YHStgApi.tryManualBomb(player)) {
					player.displayClientMessage(YHLangData.CLASSIC_CONTROL_NO_SPELL.get(), true);
				}
			}
			default -> {
			}
		}
	}

	private static void enableNonSpell(ServerPlayer player) {
		if (GrazeCapability.HOLDER.get(player).getActiveNonSpellCardKey() != null) return;
		ItemStack stack = GrazeHelper.findNonSpell(player);
		if (!(stack.getItem() instanceof DynamicSpellItem spell)) {
			player.displayClientMessage(YHLangData.CLASSIC_CONTROL_NO_NON_SPELL.get(), true);
			return;
		}
		// This is a held fire state, so item cooldown must not gate or outlive a key edge.
		spell.castSpell(stack, player, !player.getAbilities().instabuild, false);
	}

	private static void sync(ServerPlayer player, boolean enabled) {
		YoukaisHomecoming.HANDLER.toClientPlayer(new ClassicControlSyncToClient(enabled), player);
	}
}
