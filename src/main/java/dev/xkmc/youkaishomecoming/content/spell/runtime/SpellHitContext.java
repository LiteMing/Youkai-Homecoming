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
		BOUNCE,
		HOLD
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
	private int holdTicks = 0;
	@Nullable
	private java.util.List<dev.xkmc.youkaishomecoming.content.spell.action.SpellAction> deferredBody = null;

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
	public int holdTicks() { return holdTicks; }
	@Nullable
	public java.util.List<dev.xkmc.youkaishomecoming.content.spell.action.SpellAction> deferredBody() { return deferredBody; }

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

	public void resolveHold(int holdTicks, java.util.List<dev.xkmc.youkaishomecoming.content.spell.action.SpellAction> body) {
		if (this.disposition == HitDisposition.UNRESOLVED) {
			this.disposition = HitDisposition.HOLD;
			this.holdTicks = Math.max(1, holdTicks);
			this.deferredBody = body;
		}
	}

	/**
	 * Called by the runtime scheduler when holdTicks expire.
	 * Resets disposition from HOLD to UNRESOLVED so that actions in deferredBody
	 * (like BounceAction, ContinueSourceAction, ExpireSourceAction) can resolve the final outcome.
	 */
	public boolean beginResume() {
		if (this.disposition != HitDisposition.HOLD) {
			return false;
		}
		this.disposition = HitDisposition.UNRESOLVED;
		this.holdTicks = 0;
		return true;
	}
}
