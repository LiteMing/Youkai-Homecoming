package dev.xkmc.youkaishomecoming.content.spell.physics;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.youkaishomecoming.content.spell.mover.DanmakuMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.MoverInfo;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.phys.Vec3;

@SerialClass
public final class HitHoldMover extends DanmakuMover {

	@SerialClass.SerialField
	private Vec3 holdRot = Vec3.ZERO;

	public HitHoldMover() {}

	public HitHoldMover(Vec3 lookDir) {
		this.holdRot = ProjectileMovement.of(lookDir).rot();
	}

	@Override
	public ProjectileMovement move(MoverInfo info) {
		return new ProjectileMovement(Vec3.ZERO, holdRot);
	}
}
