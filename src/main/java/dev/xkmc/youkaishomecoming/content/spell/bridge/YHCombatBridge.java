package dev.xkmc.youkaishomecoming.content.spell.bridge;

import dev.xkmc.youkaishomecoming.compat.api.API;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.events.DanmakuBattleExitEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class YHCombatBridge {

	@API
	public static boolean enterDanmakuBattle(Entity participant, @Nullable Entity target) {
		if (participant.level().isClientSide()) {
			return false;
		}
		if (!(participant instanceof Player player) || !(target instanceof LivingEntity living)) {
			return false;
		}
		boolean before = target instanceof YoukaiEntity youkai &&
				GrazeCapability.HOLDER.get(player).isInSession(youkai.getUUID());
		GrazeHelper.addSession(player, living);
		return target instanceof YoukaiEntity youkai &&
				!before && GrazeCapability.HOLDER.get(player).isInSession(youkai.getUUID());
	}

	@API
	public static boolean exitDanmakuBattle(Entity participant, @Nullable Entity target) {
		if (participant.level().isClientSide()) {
			return false;
		}
		if (!(participant instanceof Player player)) {
			return false;
		}
		var cap = GrazeCapability.HOLDER.get(player);
		if (target instanceof YoukaiEntity youkai) {
			boolean before = cap.isInSession(youkai.getUUID());
			cap.stopSession(youkai.getUUID(), DanmakuBattleExitEvent.Reason.MANUAL);
			return before && !cap.isInSession(youkai.getUUID());
		}
		boolean before = cap.isInSession();
		cap.stopAllSessions(DanmakuBattleExitEvent.Reason.MANUAL);
		return before && !cap.isInSession();
	}

	@API
	public static boolean clearDanmaku(Entity source, @Nullable Entity actor) {
		if (source.level().isClientSide()) {
			return false;
		}
		Player player = actor instanceof Player p ? p : null;
		if (source instanceof Player owner) {
			return GrazeCapability.HOLDER.get(owner).eraseSessionDanmaku();
		}
		if (source instanceof YoukaiEntity youkai) {
			youkai.eraseAllDanmaku(player);
			return true;
		}
		if (source instanceof DanmakuProxyEntity proxy) {
			proxy.eraseAllDanmaku(player);
			return true;
		}
		return false;
	}

	@API
	public static boolean isInDanmakuBattle(Entity participant) {
		if (participant instanceof Player player) {
			return GrazeCapability.HOLDER.get(player).isInSession();
		}
		if (participant instanceof YoukaiEntity youkai) {
			return youkai.getTarget() != null;
		}
		if (participant instanceof DanmakuProxyEntity proxy) {
			return proxy.targetEntity() != null;
		}
		return false;
	}

	@API
	public static @Nullable LivingEntity getBattleTarget(Entity participant) {
		if (participant instanceof Player player) {
			return GrazeHelper.getTarget(player);
		}
		if (participant instanceof YoukaiEntity youkai) {
			return youkai.getTarget();
		}
		if (participant instanceof DanmakuProxyEntity proxy) {
			return proxy.targetEntity();
		}
		return null;
	}
}
