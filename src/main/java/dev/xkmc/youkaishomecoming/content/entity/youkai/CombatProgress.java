package dev.xkmc.youkaishomecoming.content.entity.youkai;

import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.world.entity.LivingEntity;

@SerialClass
public class CombatProgress {

	@SerialClass.SerialField
	public float maxProgress;
	@SerialClass.SerialField
	public float progress;
	@SerialClass.SerialField
	public float oldProgress;
	private float syncedMaxProgress;

	public void init(YoukaiEntity e) {
		if (maxProgress <= 0) maxProgress = e.getMaxHealth();
		if (progress <= 0) progress = maxProgress;
	}

	public float getMaxProgress(float def) {
		return maxProgress <= 0 ? def : maxProgress;
	}

	public float getProgress() {
		return progress;
	}

	/** Spell health has its own maximum; it may exceed the entity's base health. */
	public float clampToMaximum(float amount, float defaultMax) {
		return Math.max(0, Math.min(amount, getMaxProgress(defaultMax)));
	}

	/** Vanilla DATA_HEALTH_ID is a bounded projection, not the authoritative spell HP. */
	public static float vanillaHealth(float progress, float vanillaMaxHealth) {
		return Math.max(0, Math.min(progress, vanillaMaxHealth));
	}

	public void set(LivingEntity e, float amount) {
		progress = amount;
		if (!e.level().isClientSide() && (progress != oldProgress || maxProgress != syncedMaxProgress)) {
			oldProgress = progress;
			syncedMaxProgress = maxProgress;
			YoukaisHomecoming.HANDLER.toTrackingPlayers(new CombatToClient(e.getId(), this), e);
		}
	}

	public void loadFrom(CombatProgress progress) {
		this.maxProgress = progress.maxProgress;
		this.progress = progress.progress;
	}

}
