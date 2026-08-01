package dev.xkmc.youkaishomecoming.content.client.beaten;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

final class BeatenVisual {

	enum Kind {
		DEFEAT(32),
		LANDING(14);

		final int lifetime;

		Kind(int lifetime) {
			this.lifetime = lifetime;
		}
	}

	final ClientLevel level;
	final int entityId;
	final Vec3 origin;
	final float scale;
	final float seed;
	final int startTick;
	final Kind kind;

	BeatenVisual(ClientLevel level, int entityId, Vec3 origin, float scale, float seed, int startTick, Kind kind) {
		this.level = level;
		this.entityId = entityId;
		this.origin = origin;
		this.scale = scale;
		this.seed = seed;
		this.startTick = startTick;
		this.kind = kind;
	}

	float age(int tick, float partialTick) {
		return tick - startTick + partialTick;
	}
}
