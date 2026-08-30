package dev.xkmc.youkaishomecoming.content.spell.runtime;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuBounceConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class SpellHitContext {

	public enum HitType {
		NONE,
		BLOCK,
		ENTITY
	}

	public enum HitDisposition {
		UNRESOLVED,
		CONTINUE,
		DISCARD,
		EXPIRE,
		BOUNCE
	}

	private final SimplifiedProjectile source;
	private final HitType hitType;
	private final Vec3 hitPosition;
	private final Vec3 hitNormal;
	private final Vec3 incomingVelocity;
	@Nullable
	private final Entity hitEntity;

	private HitDisposition disposition = HitDisposition.UNRESOLVED;
	@Nullable
	private DanmakuBounceConfig bounceConfig = null;

	public SpellHitContext(
			SimplifiedProjectile source,
			HitType hitType,
			Vec3 hitPosition,
			Vec3 hitNormal,
			Vec3 incomingVelocity,
			@Nullable Entity hitEntity
	) {
		this.source = source;
		this.hitType = hitType;
		this.hitPosition = hitPosition;
		this.hitNormal = hitNormal;
		this.incomingVelocity = incomingVelocity;
		this.hitEntity = hitEntity;
	}

	public SimplifiedProjectile source() { return source; }
	public HitType hitType() { return hitType; }
	public Vec3 hitPosition() { return hitPosition; }
	public Vec3 hitNormal() { return hitNormal; }
	public Vec3 incomingVelocity() { return incomingVelocity; }
	@Nullable
	public Entity hitEntity() { return hitEntity; }

	public HitDisposition disposition() { return disposition; }
	@Nullable
	public DanmakuBounceConfig bounceConfig() { return bounceConfig; }

	public boolean isTerminal() {
		return disposition != HitDisposition.UNRESOLVED;
	}

	public void resolve(HitDisposition disposition) {
		if (this.disposition == HitDisposition.UNRESOLVED) {
			this.disposition = disposition;
		}
	}

	public void resolveBounce(DanmakuBounceConfig config) {
		if (this.disposition == HitDisposition.UNRESOLVED) {
			this.disposition = HitDisposition.BOUNCE;
			this.bounceConfig = config.sanitize();
		}
	}
}
