package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.youkaishomecoming.content.entity.youkai.GeneralYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static dev.xkmc.youkaishomecoming.compat.ysm.YsmModelProfile.Trigger;

/** Samples authority; never changes combat, physics, pose, AI or damage. */
@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID)
public final class YsmPresentationRuntime {

	private YsmPresentationRuntime() { }

	public static void tick(YsmRenderOverrideTarget target) {
		if (!(target instanceof LivingEntity entity) || entity.level().isClientSide()) return;
		Trigger state;
		boolean beaten = entity instanceof YoukaiEntity youkai && youkai.isBeaten() || entity.hasEffect(YHEffects.BEATEN.get());
		if (beaten) {
			int phase = entity instanceof YoukaiEntity youkai ? youkai.getBeatenPhase() : YoukaiEntity.BEATEN_PRONE;
			state = switch (phase) {
				case YoukaiEntity.BEATEN_DEFEAT -> Trigger.DEFEAT;
				case YoukaiEntity.BEATEN_FALLING -> Trigger.FALLING;
				default -> Trigger.PRONE;
			};
		} else if (entity.isNoGravity() || entity instanceof YoukaiEntity youkai && youkai.isFlying()) {
			state = Trigger.FLY;
		} else {
			double speed = YHModConfig.COMMON.modelPresentationWalkSpeed.get();
			double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
			double movement = Math.max(dx * dx + dz * dz, entity.getDeltaMovement().horizontalDistanceSqr());
			state = entity.onGround() && movement > speed * speed ? Trigger.WALK : Trigger.IDLE;
		}
		boolean combat = !beaten && entity instanceof GeneralYoukaiEntity youkai && youkai.getTarget() != null;
		var current = target.getYsmSignals();
		var next = current.advance(state, combat, target.getYsmPresentationTime());
		if (next != current) target.setYsmSignals(next);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void hurt(LivingDamageEvent event) {
		if (event.getAmount() <= 0 || event.getEntity().level().isClientSide() || !(event.getEntity() instanceof YsmRenderOverrideTarget target)) return;
		target.setYsmSignals(target.getYsmSignals().hurt(target.getYsmPresentationTime()));
	}
}
