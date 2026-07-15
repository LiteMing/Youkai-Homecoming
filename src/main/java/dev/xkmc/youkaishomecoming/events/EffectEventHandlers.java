package dev.xkmc.youkaishomecoming.events;

import dev.xkmc.l2library.base.effects.EffectBuilder;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.entity.reimu.MaidenEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import dev.xkmc.youkaishomecoming.init.registrate.YHItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EffectEventHandlers {

	public static boolean isYoukai(LivingEntity e) {
		return
				e.hasEffect(YHEffects.YOUKAIFYING.get()) ||
						e.hasEffect(YHEffects.YOUKAIFIED.get());
	}

	public static boolean isCharacter(LivingEntity e) {
		return e instanceof YoukaiEntity ||
				e.hasEffect(YHEffects.YOUKAIFYING.get()) ||
				e.hasEffect(YHEffects.YOUKAIFIED.get()) ||
				e.hasEffect(YHEffects.FAIRY.get());
	}

	public static boolean isFullCharacter(LivingEntity e) {
		return e instanceof YoukaiEntity ||
				e.hasEffect(YHEffects.YOUKAIFIED.get()) ||
				e.hasEffect(YHEffects.FAIRY.get());
	}

	/**
	 * Whether a player can participate in danmaku combat (graze, bomb, life, power, session).
	 * Returns true if the player has youkaified/fairy effect, OR is already in an active combat session.
	 * This allows players without effects to enter danmaku combat as long as they only use danmaku damage.
	 */
	public static boolean canDanmakuCombat(LivingEntity e) {
		if (isFullCharacter(e)) return true;
		if (e instanceof Player player) {
			var cap = GrazeCapability.HOLDER.get(player);
			return cap.isInDanmakuCombat();
		}
		return false;
	}

	/**
	 * Consume the cost for shooting a danmaku/laser.
	 * Returns true if the item should NOT be consumed (buff or hat absorbed the cost).
	 * <p>
	 * Priority:
	 * 1. Hat with matching color -> no item cost, no buff cost (handled externally via half CD)
	 * 2. Youkaified/Fairy buff -> consume buff duration as "mana", no item cost
	 * 3. No effect -> consume item
	 *
	 * @param player the player shooting
	 * @return true if item should be preserved (buff cost was consumed or hat bonus applies)
	 */
	public static boolean consumeDanmakuBuffCost(Player player) {
		int cost = YHModConfig.COMMON.danmakuBuffCostTicks.get();
		if (cost <= 0) return isFullCharacter(player);

		// Try youkaified effect first
		var youkaified = player.getEffect(YHEffects.YOUKAIFIED.get());
		if (youkaified != null) {
			if (tryConsumeEffectDuration(youkaified, cost)) return true;
			player.removeEffect(YHEffects.YOUKAIFIED.get());
			return false;
		}

		// Try fairy effect
		var fairy = player.getEffect(YHEffects.FAIRY.get());
		if (fairy != null) {
			if (tryConsumeEffectDuration(fairy, cost)) return true;
			player.removeEffect(YHEffects.FAIRY.get());
			return false;
		}

		// Try youkaifying (partial transformation) effect
		var youkaifying = player.getEffect(YHEffects.YOUKAIFYING.get());
		if (youkaifying != null) {
			if (tryConsumeEffectDuration(youkaifying, cost)) return true;
			player.removeEffect(YHEffects.YOUKAIFYING.get());
			return false;
		}

		// No buff available -> item will be consumed
		return false;
	}

	/**
	 * Try to consume duration from an effect instance.
	 * Infinite duration (-1) is never consumed (free cost).
	 * @return true if duration was consumed (or infinite), false if not enough remaining
	 */
	private static boolean tryConsumeEffectDuration(MobEffectInstance effect, int cost) {
		int remaining = effect.getDuration();
		// Infinite duration: no cost
		if (remaining < 0) return true;
		if (remaining > cost) {
			new EffectBuilder(effect).setDuration(remaining - cost);
			return true;
		}
		return false;
	}

	@SubscribeEvent
	public static void onSleep(PlayerSleepInBedEvent event) {
		if (event.getEntity().hasEffect(YHEffects.SOBER.get())) {
			event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
		}
	}

	public static void disableKoishi(Player player) {
		boolean flag = false;
		var hat = YHItems.KOISHI_HAT.get();
		if (player.hasEffect(YHEffects.UNCONSCIOUS.get())) {
			player.removeEffect(YHEffects.UNCONSCIOUS.get());
			flag = true;
		}
		if (player.getCooldowns().isOnCooldown(hat)) {
			flag = true;
		}
		if (flag) {
			player.getCooldowns().addCooldown(hat, 200);
		}
	}

	@SubscribeEvent
	public static void onAttack(LivingAttackEvent event) {
		if (event.getSource().getEntity() instanceof LivingEntity le) {
			if (le instanceof Player player) {
				disableKoishi(player);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onHeal(LivingHealEvent event) {
		float amount = event.getAmount();
		if (event.getEntity().hasEffect(YHEffects.BREATHING.get())) {
			amount *= YHModConfig.COMMON.breathingHealingFactor.get();
		}
		if (event.getEntity().hasEffect(YHEffects.FAIRY.get())) {
			amount *= YHModConfig.COMMON.fairyHealingFactor.get();
		}
		event.setAmount(amount);
	}

	@SubscribeEvent
	public static void onTick(LivingEvent.LivingTickEvent event) {
		var e = event.getEntity();
		if (e.hasEffect(YHEffects.THICK.get()) && e.hasEffect(MobEffects.WITHER)) {
			e.removeEffect(MobEffects.WITHER);
		}
		if (e.hasEffect(YHEffects.SMOOTHING.get()) && e.hasEffect(MobEffects.POISON)) {
			e.removeEffect(MobEffects.POISON);
		}
		if (e.hasEffect(YHEffects.REFRESHING.get()) && e.isOnFire()) {
			e.clearFire();
		}
		if (e.getLastHurtMob() instanceof MaidenEntity ||
				e.getLastHurtByMob() instanceof MaidenEntity) {
			removeKoishi(e);
		}
	}

	public static void removeKoishi(LivingEntity le) {
		if (le instanceof Player player) {
			if (player.hasEffect(YHEffects.UNCONSCIOUS.get())) {
				player.removeEffect(YHEffects.UNCONSCIOUS.get());
			}
			var hat = YHItems.KOISHI_HAT.get();
			if (player.getItemBySlot(EquipmentSlot.HEAD).is(hat)) {
				if (player.getCooldowns().getCooldownPercent(hat, 0) < 0.5)
					player.getCooldowns().addCooldown(hat, 200);
			}
		}
	}

	@SubscribeEvent
	public static void onEffectTest(MobEffectEvent.Applicable event) {
		if (event.getEffectInstance().getEffect() == MobEffects.WITHER) {
			if (event.getEntity().hasEffect(YHEffects.SMOOTHING.get())) {
				event.setResult(Event.Result.DENY);
			}
		}
		if (event.getEffectInstance().getEffect() == MobEffects.POISON) {
			if (event.getEntity().hasEffect(YHEffects.SMOOTHING.get())) {
				event.setResult(Event.Result.DENY);
			}
		}
		if (event.getEffectInstance().getEffect() == YHEffects.YOUKAIFYING.get()) {
			if (event.getEntity().hasEffect(YHEffects.SOBER.get()) ||
					event.getEntity().hasEffect(YHEffects.FAIRY.get()) ||
					event.getEntity().hasEffect(YHEffects.YOUKAIFIED.get())) {
				event.setResult(Event.Result.DENY);
			}
		}
		if (event.getEffectInstance().getEffect() == YHEffects.YOUKAIFIED.get()) {
			if (event.getEntity().hasEffect(YHEffects.SOBER.get()) ||
					event.getEntity().hasEffect(YHEffects.FAIRY.get())) {
				event.setResult(Event.Result.DENY);
			}
		}
		if (event.getEffectInstance().getEffect() == YHEffects.FAIRY.get()) {
			if (event.getEntity().hasEffect(YHEffects.YOUKAIFYING.get()) ||
					event.getEntity().hasEffect(YHEffects.YOUKAIFIED.get())) {
				event.setResult(Event.Result.DENY);
			}
		}
	}

	public static MobEffectInstance onEat(LivingEntity user, MobEffectInstance ins) {
		var builder = new EffectBuilder(ins);
		int dur = ins.getDuration();
		var enjoy = user.getEffect(YHEffects.ENJOYABLE.get());
		if (enjoy != null && ins.getEffect().isBeneficial()) {
			int lv = enjoy.getAmplifier() + 1;
			builder.setDuration((int) (dur * (1 + 0.2 * lv)));
		}
		return ins;
	}

}
