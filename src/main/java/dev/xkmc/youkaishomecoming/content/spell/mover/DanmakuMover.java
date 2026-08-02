package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.l2serial.serialization.SerialClass;

@SerialClass
public abstract class DanmakuMover {

	/**
	 * Captures any live entity state needed by {@link #move(MoverInfo)}.
	 * Called on the game thread before parallel projectile planning.
	 */
	public void prepare(MoverOwner owner) {
	}

	public abstract ProjectileMovement move(MoverInfo info);

}
