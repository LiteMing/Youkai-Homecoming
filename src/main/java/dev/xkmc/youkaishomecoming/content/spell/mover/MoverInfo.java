package dev.xkmc.youkaishomecoming.content.spell.mover;

import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.Vec3;

public record MoverInfo(int tick, Vec3 prevPos, Vec3 prevVel, MoverOwner self, OwnerInfo ownerInfo) {

	public record OwnerInfo(@Nullable Vec3 ownerPos, @Nullable Vec3 ownerForward) {
	}

	public MoverInfo offsetTime(int i) {
		return new MoverInfo(tick + i, prevPos, prevVel, self, ownerInfo);
	}

}
