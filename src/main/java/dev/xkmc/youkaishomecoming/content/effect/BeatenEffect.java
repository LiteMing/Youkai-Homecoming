package dev.xkmc.youkaishomecoming.content.effect;

import dev.xkmc.l2library.util.math.MathHelper;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber
public class BeatenEffect extends MobEffect {

	/** Players regain normal walking during the final 30 seconds of the effect. */
	public static final int PLAYER_WALK_GRACE_TICKS = 30 * 20;

	private static boolean rebuildingBeaten;

	public BeatenEffect(MobEffectCategory category, int color) {
		super(category, color);
		String uuid = MathHelper.getUUIDFromString("beaten").toString();
		addAttributeModifier(Attributes.MAX_HEALTH, uuid, -0.5, AttributeModifier.Operation.MULTIPLY_BASE);
	}

	public static boolean shouldForcePlayerCrawl(Player player) {
		MobEffectInstance effect = player.getEffect(YHEffects.BEATEN.get());
		return effect != null && effect.getDuration() >= PLAYER_WALK_GRACE_TICKS;
	}

	@Override
	public void applyEffectTick(LivingEntity entity, int amplifier) {
		MobEffectInstance beatenEffect = entity.getEffect(YHEffects.BEATEN.get());
		if (beatenEffect == null) return;

		// SWIMMING supplies the crawl-sized hitbox; the false flag distinguishes crawling from swimming.
		entity.setSwimming(false);
		if (entity instanceof YoukaiEntity youkai) {
			youkai.tickBeatenState();
		} else if (entity instanceof Player player) {
			if (player instanceof ServerPlayer sp) {
				SpellContainer.clearForBeaten(sp);
			}
			// mayfly is permission owned by the game mode or a flight provider. Clearing it here
			// outlives the effect and can permanently revoke flight, so only stop active flight.
			var abilities = player.getAbilities();
			if (abilities.flying) {
				abilities.flying = false;
				player.onUpdateAbilities();
			}
			if (player.getHealth() >= 5) {
				player.setHealth(player.getMaxHealth() / 2);
			}
			player.getCapability(GrazeCapability.CAPABILITY).ifPresent(cap -> cap.setWeak(beatenEffect.getDuration()));
			player.setSprinting(false);
			player.setForcedPose(shouldForcePlayerCrawl(player) ? Pose.SWIMMING : null);
		} else if (entity instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.setDeltaMovement(0, 0, 0);
			mob.setSprinting(false);
			mob.setAggressive(false);
			mob.setPose(Pose.SWIMMING);
			mob.setNoAi(true);
		}
	}

	public static void applyDanmakuDefeat(YoukaiEntity entity) {
		tryApplyDanmakuDefeat(entity);
	}

	public static boolean tryApplyDanmakuDefeat(YoukaiEntity entity) {
		if (!entity.canEnterBeatenState()) return false;
		int duration = YHModConfig.COMMON.beatenDurationTicks.get();
		MobEffectInstance current = entity.getEffect(YHEffects.BEATEN.get());
		if (current == null || current.getDuration() < duration) {
			entity.addEffect(new MobEffectInstance(YHEffects.BEATEN.get(), duration, 0));
		}
		if (entity.hasEffect(YHEffects.BEATEN.get())) {
			entity.setCombatProgress(entity.getMaxHealth());
			entity.validateData();
			entity.beginDanmakuDefeat();
			return true;
		}
		return false;
	}

	@Override
	public boolean isDurationEffectTick(int duration, int amplifier) {
		return true;
	}

	@Override
	public List<ItemStack> getCurativeItems() {
		return List.of();
	}

	/**
	 * Stop player-owned spell output as soon as beaten is applied, including
	 * effects added by commands or integrations outside the normal STG defeat
	 * path. The tick handler below remains a defensive fallback for restored
	 * worlds and already-active effects.
	 */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onMobEffectAdded(MobEffectEvent.Added event) {
		MobEffectInstance effect = event.getEffectInstance();
		if (effect == null || effect.getEffect() != YHEffects.BEATEN.get()) return;
		if (event.getEntity() instanceof ServerPlayer sp) {
			SpellContainer.clearForBeaten(sp);
		}
	}

	@SubscribeEvent
	public static void onLivingHeal(LivingHealEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity instanceof Player player) {
			MobEffectInstance beatenEffect = player.getEffect(YHEffects.BEATEN.get());
			if (beatenEffect != null) {
				float healAmount = event.getAmount();
				int durationReduction = (int) (healAmount * 20);
				int newDuration = Math.max(0, beatenEffect.getDuration() - durationReduction);
			if (newDuration <= 0) {
				player.removeEffect(YHEffects.BEATEN.get());
			} else {
				// rebuild the instance in place: remove+add would fire MobEffectEvent.Remove and
				// restoreState() would clear the forced pose for one tick, making the player flash
				// standing up on every heal. The effect data is only flushed to clients once per tick,
				// so the client never observes the rebuild — only the pose flash must be suppressed.
				rebuildingBeaten = true;
				try {
					player.removeEffect(YHEffects.BEATEN.get());
					player.addEffect(new MobEffectInstance(
							YHEffects.BEATEN.get(), newDuration, beatenEffect.getAmplifier(),
							beatenEffect.isAmbient(), beatenEffect.isVisible(), beatenEffect.showIcon()
					));
				} finally {
					rebuildingBeaten = false;
				}
			}
			}
			if (player.getHealth() >= 0.5 * player.getMaxHealth() && player.hasEffect(YHEffects.BEATEN.get())) {
				event.setAmount(0);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onMobEffectRemoved(MobEffectEvent.Remove event) {
		if (event.getEffect() == YHEffects.BEATEN.get() && !rebuildingBeaten) {
			restoreState(event.getEntity());
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onMobEffectExpired(MobEffectEvent.Expired event) {
		MobEffectInstance effect = event.getEffectInstance();
		if (effect != null && effect.getEffect() == YHEffects.BEATEN.get() && !rebuildingBeaten) {
			restoreState(event.getEntity());
		}
	}

	private static void restoreState(LivingEntity entity) {
		entity.setSwimming(false);
		if (entity instanceof Player player) {
			player.setForcedPose(null);
			player.getCapability(GrazeCapability.CAPABILITY).ifPresent(cap -> cap.setWeak(0));
		}
		if (entity instanceof YoukaiEntity youkai) {
			youkai.queueBeatenRecovery();
		} else if (entity instanceof Mob mob) {
			mob.setNoAi(false);
			mob.setPose(Pose.STANDING);
		}
	}
}
