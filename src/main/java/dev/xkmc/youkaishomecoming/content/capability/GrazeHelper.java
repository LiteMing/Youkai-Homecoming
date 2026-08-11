package dev.xkmc.youkaishomecoming.content.capability;

import dev.xkmc.fastprojectileapi.entity.GrazingEntity;
import dev.xkmc.l2library.util.raytrace.RayTraceUtil;
import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.compat.curios.CuriosManager;
import dev.xkmc.youkaishomecoming.content.effect.BeatenEffect;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.ISpellItem;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity;
import dev.xkmc.youkaishomecoming.events.DanmakuGrazeEvent;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHAttributes;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

public class GrazeHelper {

	public static final int SPELL_TARGET_RANGE = 64;

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
		if (isValidPlayerSpellTarget(player, target)) {
			addSession(player, target);
			return target;
		}
		target = getTarget(player);
		return isValidPlayerSpellTarget(player, target) ? target : null;
	}

	public static boolean isValidPlayerSpellTarget(Player player, @Nullable LivingEntity target) {
		if (target == null || target == player || !target.isAlive() || target.level() != player.level()
				|| player.isAlliedTo(target)) {
			return false;
		}
		if (target instanceof ShooterEntity shooter) {
			var owner = shooter.getOwner();
			return owner != player && (owner == null || !player.isAlliedTo(owner));
		}
		if (target instanceof YoukaiEntity || target instanceof Player) {
			return true;
		}
		if (target instanceof Enemy && !(target instanceof NeutralMob)) {
			return true;
		}
		return target instanceof Mob mob && mob.getTarget() == player;
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
		return !findSpellCard(player).isEmpty();
	}

	public static boolean isSpellStack(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof ISpellItem spell && spell.isCastReady(stack);
	}

	/**
	 * Shift+RMB toggle for players. Requires a spell card when entering in manual mode.
	 * Exit clears combat state without wiping life/bomb/power.
	 */
	public static boolean tryToggleManualCombat(Player player) {
		if (player.level().isClientSide()) return false;
		if (!(player instanceof ServerPlayer sp)) return false;
		if (!isManualCombatMode()) return false;
		if (forbidDanmaku(sp)) {
			return false;
		}
		var trial = dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager.INSTANCE
				.getActiveTrial(sp);
		if (trial != null && trial.isActive()) {
			return false;
		}
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
		if (attacker == opponent || attacker.level() != opponent.level() || attacker.isAlliedTo(opponent)) return;
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
		if (player.hasEffect(YHEffects.BEATEN.get()) || cap.isInvul() || cap.isWeak()) {
			return true;
		}
		// while releasing a spell card, other danmaku (and other spell cards)
		// cannot be used
		return cap.isPlayerSpellActive() ||
				dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer.hasActiveSpell(player);
	}

	/**
	 * Casts the spell card selected with vanilla projectile ordering: offhand,
	 * main hand, then inventory slots. No card means no bomb activation.
	 */
	public static boolean tryCastBombSpell(ServerPlayer player) {
		if (forbidDanmaku(player)) return false;
		ItemStack stack = findSpellCard(player);
		if (!(stack.getItem() instanceof ISpellItem spell)) return false;
		if (player.getCooldowns().isOnCooldown(stack.getItem())) return false;
		// The spell payment router owns Bomb deduction. This keeps duration-based
		// and script-replaced payment providers authoritative and avoids double cost.
		return spell.castSpell(stack, player, !player.getAbilities().instabuild, true);
	}

	/**
	 * Select a spell card using vanilla projectile order, with Curios as an
	 * explicit fallback for script-driven and accessory-held cards.
	 */
	public static ItemStack findSpellCard(Player player) {
		ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
		if (isAvailableSpellStack(player, offhand)) return offhand;
		ItemStack mainhand = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (isAvailableSpellStack(player, mainhand)) return mainhand;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.items.size(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (isAvailableSpellStack(player, stack)) return stack;
		}
		ItemStack curios = CuriosManager.findFirstSpellItem(player);
		return isAvailableSpellStack(player, curios) ? curios : ItemStack.EMPTY;
	}

	private static boolean isAvailableSpellStack(Player player, ItemStack stack) {
		if (!isSpellStack(stack)) return false;
		String cardKey = spellCardKey(stack);
		return !GrazeCapability.HOLDER.get(player).isSpellCardUnavailable(cardKey);
	}

	public static String spellCardKey(ItemStack stack) {
		String certificateId = CertifiedSpellValidator.getCertificateId(stack);
		if (certificateId != null) return "certificate:" + certificateId;
		if (stack.getItem() instanceof DynamicSpellItem) {
			var spellId = DynamicSpellItem.getSpellId(stack);
			if (spellId != null) return "spell:" + spellId;
		}
		return "item:" + BuiltInRegistries.ITEM.getKey(stack.getItem());
	}

	public static boolean tryForceCloseSpell(ServerPlayer player, ItemStack stack) {
		if (!SpellContainer.hasActiveSpell(player)) return false;
		return SpellContainer.forceCloseActiveSpell(player, spellCardKey(stack));
	}

	public static boolean isPlayerSpellBeingCast(Player player) {
		return GrazeCapability.HOLDER.get(player).isPlayerSpellActive()
				|| SpellContainer.hasActiveSpell(player);
	}

	/** Cast a specific card supplied by an integration or script. */
	public static boolean castSpell(ServerPlayer player, ItemStack stack) {
		if (forbidSpellCardWithMessage(player)) return false;
		if (!(stack.getItem() instanceof ISpellItem spell)) return false;
		if (player.getCooldowns().isOnCooldown(stack.getItem())) return false;
		return spell.castSpell(stack, player, !player.getAbilities().instabuild, true);
	}

	/** Cast the first available card using the same selection order as Bomb. */
	public static boolean castSpell(ServerPlayer player) {
		return castSpell(player, findSpellCard(player));
	}

	/** Syncs the active cast immediately and grants duration-bound protection in STG. */
	public static void onPlayerSpellCast(ServerPlayer player) {
		GrazeCapability.HOLDER.get(player).startPlayerSpell();
	}

	/**
	 * Spell-card-only gate used by ISpellItem implementations. Normal danmaku
	 * and laser shots remain available during certification so the player can
	 * break the certification target; bombs and other spell cards do not.
	 */
	public static boolean forbidSpellCardWithMessage(Player player) {
		if (forbidDanmaku(player)) {
			return true;
		}
		if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
			var trial = dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager.INSTANCE
					.getActiveTrial(sp);
			if (trial != null && trial.isActive()) {
				trial.onPlayerCastsOtherSpell();
				return true;
			}
		}
		return false;
	}

	/** Compatibility wrapper retained for item entry points; intentionally silent. */
	public static boolean forbidDanmakuWithMessage(Player player) {
		return forbidDanmaku(player);
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
