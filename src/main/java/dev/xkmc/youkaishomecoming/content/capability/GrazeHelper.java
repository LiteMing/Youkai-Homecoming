package dev.xkmc.youkaishomecoming.content.capability;

import dev.xkmc.fastprojectileapi.entity.GrazingEntity;
import dev.xkmc.l2library.util.raytrace.RayTraceUtil;
import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.compat.curios.CuriosManager;
import dev.xkmc.youkaishomecoming.content.effect.BeatenEffect;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.item.danmaku.ISpellItem;
import dev.xkmc.youkaishomecoming.events.DanmakuGrazeEvent;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

public class GrazeHelper {

	public static final int SPELL_TARGET_RANGE = 64;

	public static int globalInvulTime = 0;
	public static int globalForbidTime = 0;

	public static void graze(Player entity, GrazingEntity e) {
		var graze = GrazeCapability.HOLDER.get(entity);
		if (graze.isInvul()) return;
		if (MinecraftForge.EVENT_BUS.post(new DanmakuGrazeEvent(entity, e)))
			return;
		if (graze.graze() && entity instanceof ServerPlayer sp) {
			YoukaisHomecoming.HANDLER.toClientPlayer(new GrazeToClient().set(0), sp);
		}
	}

	@Nullable
	public static LivingEntity getTarget(Player player) {
		return GrazeCapability.HOLDER.get(player).findAny(player).orElse(null);
	}

	@Nullable
	public static LivingEntity resolveSpellTarget(Player player) {
		LivingEntity target = RayTraceUtil.serverGetTarget(player);
		if (target != null && target.isAlive() && target.level() == player.level()) {
			addSession(player, target);
			return target;
		}
		return getTarget(player);
	}

	public static Vec3 getAimDirection(Player player) {
		return RayTraceUtil.getRayTerm(Vec3.ZERO, player.getXRot(), player.getYRot(), 1);
	}

	public static Vec3 getAimTarget(Player player, Vec3 origin) {
		return origin.add(getAimDirection(player).scale(SPELL_TARGET_RANGE));
	}

	public static boolean isManualCombatMode() {
		return YHModConfig.COMMON.manualDanmakuCombat.get();
	}

	public static boolean hasSpellCard(Player player) {
		if (isSpellStack(player.getMainHandItem()) || isSpellStack(player.getOffhandItem())) {
			return true;
		}
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isSpellStack(inv.getItem(i))) {
				return true;
			}
		}
		return CuriosManager.hasAnySpellItem(player);
	}

	public static boolean isSpellStack(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof ISpellItem;
	}

	/**
	 * Shift+RMB toggle for players. Requires a spell card when entering in manual mode.
	 * Exit clears combat state without wiping life/bomb/power.
	 */
	public static boolean tryToggleManualCombat(Player player) {
		if (player.level().isClientSide()) return false;
		if (!(player instanceof ServerPlayer sp)) return false;
		if (!isManualCombatMode()) return false;
		var cap = GrazeCapability.HOLDER.get(sp);
		if (cap.isForcedDanmakuCombat()) {
			cap.clearCombatState(true);
			sp.displayClientMessage(YHLangData.STG_EXIT.get(), true);
			return true;
		}
		if (!hasSpellCard(sp)) {
			sp.displayClientMessage(YHLangData.STG_NEED_SPELL.get(), true);
			return false;
		}
		cap.setForcedDanmakuCombat(true, false);
		cap.sync();
		sp.displayClientMessage(YHLangData.STG_ENTER.get(), true);
		return true;
	}

	public static void addSession(Player player, LivingEntity target) {
		if (player.level().isClientSide()) return;
		var cap = GrazeCapability.HOLDER.get(player);
		if (target instanceof YoukaiEntity e) {
			if (isManualCombatMode() && !cap.isInDanmakuCombat()) return;
			if (cap.isInSession(e.getUUID())) return;
			cap.initSession(e);
			return;
		}
		if (target instanceof Player opponent) {
			enterPvpSpellDuel(player, opponent);
		}
	}

	/**
	 * PvP: spell targeting pulls both players into STG when they each have a spell card.
	 * Opponent without spell cards is left out (HP damage path).
	 */
	public static void enterPvpSpellDuel(Player attacker, Player opponent) {
		if (attacker.level().isClientSide()) return;
		if (attacker == opponent || attacker.level() != opponent.level()) return;
		var atkCap = GrazeCapability.HOLDER.get(attacker);
		var defCap = GrazeCapability.HOLDER.get(opponent);

		if (isManualCombatMode()) {
			if (!atkCap.isForcedDanmakuCombat() && !hasSpellCard(attacker)) return;
			if (!atkCap.isForcedDanmakuCombat()) {
				atkCap.setForcedDanmakuCombat(true, false);
			}
			if (hasSpellCard(opponent) || defCap.isForcedDanmakuCombat()) {
				if (!defCap.isForcedDanmakuCombat()) {
					defCap.setForcedDanmakuCombat(true, false);
				}
				atkCap.addPlayerOpponent(opponent);
				defCap.addPlayerOpponent(attacker);
				defCap.sync();
			}
			atkCap.sync();
			return;
		}

		atkCap.addPlayerOpponent(opponent);
		defCap.addPlayerOpponent(attacker);
		atkCap.sync();
		defCap.sync();
	}

	public static boolean forbidDanmaku(Player player) {
		var cap = GrazeCapability.HOLDER.get(player);
		if (cap.isInvul() || cap.isWeak()) {
			return true;
		}
		// while releasing a spell card, other danmaku (and other spell cards)
		// cannot be used
		return dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer.hasActiveProxy(player);
	}

	/**
	 * Spell-card-only gate used by ISpellItem implementations. Normal danmaku
	 * and laser shots remain available during certification so the player can
	 * break the certification target; bombs and other spell cards do not.
	 */
	public static boolean forbidSpellCardWithMessage(Player player) {
		if (forbidDanmakuWithMessage(player)) {
			return true;
		}
		if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
			var trial = dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager.INSTANCE
					.getActiveTrial(sp);
			if (trial != null && trial.isActive()) {
				trial.onPlayerCastsOtherSpell();
				sp.displayClientMessage(dev.xkmc.youkaishomecoming.init.data.YHLangData.SPELL_NO_DANMAKU_NOW.get(), true);
				return true;
			}
		}
		return false;
	}

	/** Like {@link #forbidDanmaku} but tells the player why (red action bar). */
	public static boolean forbidDanmakuWithMessage(Player player) {
		if (!forbidDanmaku(player)) {
			return false;
		}
		if (!player.level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) {
			sp.displayClientMessage(
					dev.xkmc.youkaishomecoming.init.data.YHLangData.SPELL_NO_DANMAKU_NOW.get(), true);
		}
		return true;
	}

	public static void onDanmakuKill(Player player, YoukaiEntity e) {
		onDanmakuKill(player, e, player.damageSources().playerAttack(player));
	}

	public static void onDanmakuKill(Player player, YoukaiEntity e, DamageSource source) {
		tryDanmakuDefeat(player, e, source);
	}

	public static boolean tryDanmakuDefeat(Player player, YoukaiEntity e, DamageSource source) {
		var cap = GrazeCapability.HOLDER.get(player);
		boolean danmakuVictory = cap.isInSession(e.getUUID());
		if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
			var snap = cap.snapshotOpponents();
			net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
					new dev.xkmc.youkaishomecoming.compat.stg.event.StgCombatEvent.Victory(
							sp, e, snap.ids(), snap.entities()));
			// Victory before session removal so listeners still see this Youkai in the snapshot.
		}
		boolean defeated = danmakuVictory && BeatenEffect.tryApplyDanmakuDefeat(e);
		if (defeated) {
			e.dropDanmakuDefeatLoot(player, source);
		}
		cap.stopSession(e.getUUID(),
				dev.xkmc.youkaishomecoming.compat.stg.event.StgCombatEvent.SessionEndReason.VICTORY);
		return defeated;
	}

	public static int getInitialResource(Player player) {
		return YHModConfig.COMMON.initialResource.get() +
				(int) player.getAttributeValue(YHAttributes.INITIAL_RESOURCE.get());
	}

	public static int getInitialPower(Player player) {
		return YHModConfig.COMMON.initialPower.get() +
				(int) player.getAttributeValue(YHAttributes.INITIAL_POWER.get());
	}

	public static int getMaxPower(Player player) {
		return YHModConfig.COMMON.danmakuMaxPower.get() +
				(int) player.getAttributeValue(YHAttributes.MAX_POWER.get());
	}

	public static double getGrazeEffectiveness(Player player) {
		return YHModConfig.COMMON.grazeEffectiveness.get() +
				player.getAttributeValue(YHAttributes.GRAZE_EFFECTIVENESS.get());
	}

	public static int getMaxResource(Player player) {
		return YHModConfig.COMMON.danmakuMaxResource.get() +
				(int) player.getAttributeValue(YHAttributes.MAX_RESOURCE.get());
	}

	public static float getHitBoxDelta(Player player) {
		return (float) player.getAttributeValue(YHAttributes.HITBOX.get());
	}

	@SerialClass
	public static class GrazeToClient extends SerialPacketBase {

		@SerialClass.SerialField
		public int type;

		@Override
		public void handle(NetworkEvent.Context context) {
			if (type == 0)
				ClientCapHandler.playGraze();
			else ClientCapHandler.playMiss();
		}

		public GrazeToClient set(int i) {
			type = i;
			return this;
		}
	}

}
