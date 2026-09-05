package dev.xkmc.youkaishomecoming.content.spell.runtime;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable, transient snapshot of the projectile that caused a trail, hit, or
 * expiry callback.  It is deliberately not serialised into projectile NBT: it
 * is only the callback's current server-side execution context.
 *
 * <p>The old {@code TrailCardHolder} API is still used for backwards
 * compatibility.  New data-driven actions can use this object through
 * {@code NumberProviders.CallbackValue} and callback-aware origin/aim modes.
 */
public record ProjectileCallbackContext(
		Kind kind,
		@Nullable SimplifiedProjectile source,
		Vec3 sourcePosition,
		Vec3 sourceVelocity,
		Vec3 sourceDirection,
		double sourceSpeed,
		double sourceSize,
		double sourceSpread,
		double sourceLifetime,
		double sourceAge,
		double sourceRemainingLifetime,
		DanmakuColor sourceColor,
		Vec3 movementStart,
		Vec3 movementEnd,
		Vec3 position,
		@Nullable Vec3 hitPosition,
		@Nullable Vec3 hitNormal,
		@Nullable Entity hitEntity,
		@Nullable Vec3 laserStart,
		@Nullable Vec3 laserEnd,
		@Nullable Vec3 laserClippedEnd
) {

	public enum Kind {
		TRAIL,
		HIT_ENTITY,
		HIT_BLOCK,
		EXPIRY
	}

	public ProjectileCallbackContext {
		kind = kind == null ? Kind.TRAIL : kind;
		sourcePosition = safe(sourcePosition);
		sourceVelocity = safe(sourceVelocity);
		sourceDirection = normalized(sourceDirection, sourceVelocity);
		sourceSpeed = Double.isFinite(sourceSpeed) && sourceSpeed >= 0
				? sourceSpeed : sourceVelocity.length();
		sourceSize = finite(sourceSize, 1.0);
		sourceSpread = finite(sourceSpread, 0.0);
		sourceLifetime = Math.max(0.0, finite(sourceLifetime, 0.0));
		sourceAge = Math.max(0.0, finite(sourceAge, 0.0));
		sourceRemainingLifetime = Math.max(0.0, finite(sourceRemainingLifetime,
				Math.max(0.0, sourceLifetime - sourceAge)));
		sourceColor = sourceColor == null ? DanmakuColor.WHITE : sourceColor;
		movementStart = safe(movementStart);
		movementEnd = safe(movementEnd);
		position = safe(position);
	}

	/** Builds a point-projectile trail/expiry snapshot. */
	public static ProjectileCallbackContext point(
			Kind kind,
			@Nullable SimplifiedProjectile source,
			Vec3 position,
			Vec3 velocity,
			Vec3 movementStart,
			Vec3 movementEnd,
			@Nullable Vec3 hitPosition,
			@Nullable Vec3 hitNormal,
			@Nullable Entity hitEntity) {
		SourceMetadata metadata = metadata(source);
		Vec3 sourcePosition = source == null ? position : source.position();
		return new ProjectileCallbackContext(kind, source, sourcePosition, velocity,
				velocity, velocity.length(), metadata.size(), metadata.spread(), metadata.lifetime(),
				metadata.age(), metadata.remainingLifetime(), metadata.color(), movementStart, movementEnd, position,
				hitPosition, hitNormal, hitEntity, null, null, null);
	}

	/** Builds a point-projectile hit snapshot from the existing hit context. */
	public static ProjectileCallbackContext fromHit(
			SpellHitContext hit, Kind kind, @Nullable Vec3 directionOverride) {
		var captured = hit.callbackContext().orElse(null);
		if (captured != null && captured.kind() == kind) {
			return captured;
		}
		Vec3 velocity = hit.incomingVelocity();
		Vec3 direction = directionOverride == null ? velocity : directionOverride;
		Vec3 sourcePosition = hit.source() == null ? hit.movementStart() : hit.source().position();
		SourceMetadata metadata = metadata(hit.source());
		return new ProjectileCallbackContext(kind, hit.source(), sourcePosition, velocity,
				direction, velocity.length(), metadata.size(), metadata.spread(), metadata.lifetime(),
				metadata.age(), metadata.remainingLifetime(), metadata.color(),
				hit.movementStart(), hit.movementEnd(), hit.hitPosition(),
				hit.hitPosition(), hit.hitNormal(), hit.hitEntity(), null, null, null);
	}

	/** Builds a laser snapshot.  {@code clippedEnd} is the wall-clipped endpoint. */
	public static ProjectileCallbackContext laser(
			Kind kind,
			@Nullable SimplifiedProjectile source,
			Vec3 anchor,
			Vec3 velocity,
			Vec3 movementStart,
			Vec3 movementEnd,
			Vec3 direction,
			double speed,
			Vec3 laserStart,
			Vec3 laserEnd,
			Vec3 clippedEnd,
			@Nullable Vec3 hitPosition,
			@Nullable Vec3 hitNormal,
			@Nullable Entity hitEntity) {
		SourceMetadata metadata = metadata(source);
		return new ProjectileCallbackContext(kind, source, anchor, velocity, direction, speed,
				metadata.size(), metadata.spread(), metadata.lifetime(), metadata.age(),
				metadata.remainingLifetime(), metadata.color(), movementStart, movementEnd,
				hitPosition == null ? anchor : hitPosition,
				hitPosition, hitNormal, hitEntity,
				laserStart, laserEnd, clippedEnd);
	}

	/**
	 * Re-labels a hit snapshot as an expiry callback without losing the geometry
	 * that caused the expiry. In particular, laser start/end/clipped-end remain
	 * the values captured at collision time.
	 */
	public ProjectileCallbackContext asExpiry(Vec3 expiryPosition, Vec3 velocity) {
		Vec3 resolvedVelocity = safe(velocity);
		Vec3 resolvedDirection = laserStart == null ? resolvedVelocity : sourceDirection;
		Vec3 resolvedSourcePosition = source == null ? sourcePosition : source.position();
		return new ProjectileCallbackContext(Kind.EXPIRY, source, resolvedSourcePosition,
				resolvedVelocity, resolvedDirection, resolvedVelocity.length(), sourceSize,
				sourceSpread, sourceLifetime, sourceAge, sourceRemainingLifetime, sourceColor,
				movementStart, movementEnd, safe(expiryPosition), hitPosition, hitNormal,
				hitEntity, laserStart, laserEnd, laserClippedEnd);
	}

	private static Vec3 safe(@Nullable Vec3 value) {
		return value == null ? Vec3.ZERO : value;
	}

	private static double finite(double value, double fallback) {
		return Double.isFinite(value) ? value : fallback;
	}

	private static SourceMetadata metadata(@Nullable SimplifiedProjectile source) {
		if (source instanceof IYHDanmaku danmaku) {
			double lifetime = danmaku.callbackSourceLifetime();
			double age = source.tickCount;
			return new SourceMetadata(danmaku.callbackSourceSize(), danmaku.callbackSourceSpread(), lifetime,
					age, Math.max(0.0, lifetime - age), danmaku.callbackSourceColor());
		}
		return new SourceMetadata(1.0, 0.0, 0.0, source == null ? 0.0 : source.tickCount,
				0.0, DanmakuColor.WHITE);
	}

	private record SourceMetadata(double size, double spread, double lifetime, double age,
			double remainingLifetime, DanmakuColor color) {
	}

	private static Vec3 normalized(@Nullable Vec3 direction, Vec3 velocity) {
		Vec3 candidate = direction == null ? velocity : direction;
		return candidate.lengthSqr() > 1.0e-12 ? candidate.normalize() : new Vec3(0, 0, 1);
	}
}
