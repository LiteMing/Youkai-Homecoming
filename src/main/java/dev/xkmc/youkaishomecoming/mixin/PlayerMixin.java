package dev.xkmc.youkaishomecoming.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.xkmc.youkaishomecoming.content.item.curio.hat.FlyingToken;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {

	protected PlayerMixin(EntityType<? extends LivingEntity> p_20966_, Level p_20967_) {
		super(p_20966_, p_20967_);
	}

	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void youkaishomecoming$freezeSpellMovement(Vec3 travelVector, CallbackInfo ci) {
		if (!((Object) this instanceof net.minecraft.server.level.ServerPlayer sp)) {
			return;
		}
		var trial = dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager.INSTANCE
				.getActiveTrial(sp);
		if (dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer.restrictsManualMovement(sp)
				|| trial != null && trial.restrictsAuthorMovement()) {
			sp.setDeltaMovement(Vec3.ZERO);
			ci.cancel();
		}
	}

	@WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"))
	public void youkaishomecoming$stopSliding(Player instance, Vec3 f4, Operation<Void> original) {
		if (!FlyingToken.flyTravel(instance, f4, original)) {
			original.call(instance, f4);
		}
	}

}
