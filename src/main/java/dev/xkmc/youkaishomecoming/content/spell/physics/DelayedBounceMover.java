package dev.xkmc.youkaishomecoming.content.spell.physics;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.youkaishomecoming.content.spell.mover.DanmakuMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.MoverInfo;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.phys.Vec3;

@SerialClass
public final class DelayedBounceMover extends DanmakuMover {

	@SerialClass.SerialField
	private int delayTicks;
	@SerialClass.SerialField
	private int startTick = -1;
	@SerialClass.SerialField
	private Vec3 launchVel = Vec3.ZERO;

	public DelayedBounceMover() {}

	public DelayedBounceMover(int delayTicks, Vec3 launchVel) {
		this.delayTicks = Math.max(1, delayTicks);
		this.launchVel = launchVel;
	}

	@Override
	public ProjectileMovement move(MoverInfo info) {
		if (startTick < 0) {
			startTick = info.tick();
		}
		int elapsed = info.tick() - startTick;
		if (elapsed < delayTicks) {
			// Zero movement during delay period
			return new ProjectileMovement(Vec3.ZERO, ProjectileMovement.of(launchVel).rot());
		}
		// Delay finished: fly with launch velocity
		return new ProjectileMovement(launchVel, ProjectileMovement.of(launchVel).rot());
	}
}
