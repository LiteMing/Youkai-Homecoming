package dev.xkmc.youkaishomecoming.content.spell.runtime;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuBounceConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
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
	private final Vec3 movementStart;
	private final Vec3 hitPosition;
	private final Vec3 movementEnd;
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
	@Nullable
	private HoldResumeContext holdResumeContext = null;

	/** Transient server-side references required to resume a held projectile. */
	public record HoldResumeContext(
			@Nullable CardHolder holder,
			@Nullable SpellRuntime runtime,
			@Nullable SpellDefinition definition
	) {
		public boolean isUsable() {
			return holder != null && runtime != null && definition != null;
		}
	}

	public SpellHitContext(
			SimplifiedProjectile source,
			HitType hitType,
			Vec3 hitPosition,
			Vec3 hitNormal,
			Vec3 incomingVelocity,
			@Nullable Entity hitEntity
	) {
		this(source, hitType, hitPosition, hitPosition, hitPosition.add(incomingVelocity), hitNormal, incomingVelocity, hitEntity);
	}

	public SpellHitContext(
			SimplifiedProjectile source,
			HitType hitType,
			Vec3 movementStart,
			Vec3 hitPosition,
			Vec3 movementEnd,
			Vec3 hitNormal,
			Vec3 incomingVelocity,
			@Nullable Entity hitEntity
	) {
		this.source = source;
		this.hitType = hitType;
		this.movementStart = movementStart;
		this.hitPosition = hitPosition;
		this.movementEnd = movementEnd;
		this.hitNormal = hitNormal;
		this.incomingVelocity = incomingVelocity;
		this.hitEntity = hitEntity;
	}

	public SimplifiedProjectile source() { return source; }
	public HitType hitType() { return hitType; }
	public Vec3 movementStart() { return movementStart; }
	public Vec3 hitPosition() { return hitPosition; }
	public Vec3 movementEnd() { return movementEnd; }
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
	@Nullable
	public HoldResumeContext holdResumeContext() { return holdResumeContext; }

	public boolean isTerminal() {
		return disposition != HitDisposition.UNRESOLVED;
	}

	public void resolve(HitDisposition disposition) {
		this.disposition = disposition;
		if (disposition != HitDisposition.BOUNCE) {
			this.bounceConfig = null;
		}
		if (disposition != HitDisposition.HOLD) {
			this.holdTicks = 0;
			this.deferredBody = null;
			this.holdResumeContext = null;
		}
	}

	public void resolveBounce(DanmakuBounceConfig config) {
		this.disposition = HitDisposition.BOUNCE;
		this.bounceConfig = config.sanitize();
		this.holdTicks = 0;
		this.deferredBody = null;
		this.holdResumeContext = null;
	}

	public void resolveHold(int holdTicks, java.util.List<dev.xkmc.youkaishomecoming.content.spell.action.SpellAction> body) {
		resolveHold(holdTicks, body, null, null, null);
	}

	public void resolveHold(
			int holdTicks,
			java.util.List<dev.xkmc.youkaishomecoming.content.spell.action.SpellAction> body,
			@Nullable CardHolder holder,
			@Nullable SpellRuntime runtime,
			@Nullable SpellDefinition definition
	) {
		this.disposition = HitDisposition.HOLD;
		this.holdTicks = Math.max(1, holdTicks);
		this.deferredBody = body;
		this.bounceConfig = null;
		this.holdResumeContext = new HoldResumeContext(holder, runtime, definition);
	}

	/** Clears the transient callback references after a hold has been resumed. */
	public void clearHoldResumeContext() {
		this.holdResumeContext = null;
	}

	/**
	 * Called by the runtime scheduler when holdTicks expire.
	 * Resets disposition from HOLD to UNRESOLVED, takes the deferredBody, and clears internal reference.
	 */
	@Nullable
	public java.util.List<dev.xkmc.youkaishomecoming.content.spell.action.SpellAction> beginResumeAndTakeBody() {
		if (this.disposition != HitDisposition.HOLD) {
			return null;
		}
		this.disposition = HitDisposition.UNRESOLVED;
		this.holdTicks = 0;
		var body = this.deferredBody;
		this.deferredBody = null;
		return body;
	}
}
