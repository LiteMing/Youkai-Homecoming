package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.phys.Vec3;

@SerialClass
public class AttachedFreeRotMover extends TargetPosMover {

	@Override
	public Vec3 pos(MoverInfo info) {
		Vec3 ownerPos = info.ownerInfo() == null ? null : info.ownerInfo().ownerPos();
		return ownerPos == null ? info.prevPos() : ownerPos;
	}

	@Override
	public ProjectileMovement move(MoverInfo info) {
		Vec3 rot = info.self().rot();
		Vec3 ownerForward = info.ownerInfo() == null ? null : info.ownerInfo().ownerForward();
		if (ownerForward != null && ownerForward.lengthSqr() > 1e-8) {
			rot = ProjectileMovement.of(ownerForward).rot();
		}
		return new ProjectileMovement(pos(info).subtract(info.prevPos()), rot);
	}

}
