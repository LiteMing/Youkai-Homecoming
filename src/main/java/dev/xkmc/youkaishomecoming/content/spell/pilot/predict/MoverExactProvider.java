package dev.xkmc.youkaishomecoming.content.spell.pilot.predict;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity;
import dev.xkmc.youkaishomecoming.content.spell.mover.*;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * T1 exact prediction for pure-function movers.
 * <p>
 * C2 whitelist checklist (all must be no for admission):
 * <ol>
 *   <li>Does {@code pos()}/{@code move()} write any member fields?</li>
 *   <li>Does it read {@code info.self()} outside the TargetPosMover rot-fallback?</li>
 *   <li>Does it depend on mutable objects other than frozen {@code ownerInfo}?</li>
 * </ol>
 * Strategy: TargetPosMover subclasses call {@link TargetPosMover#pos(MoverInfo)} directly
 * (bypass {@code move()} rot fallback that reads {@code info.self()});
 * ZeroMover/RotateMover are position-constant pure tick functions.
 * Attached, Composite, Layered and any failed checklist → T3, never clone.
 */
public class MoverExactProvider implements ThreatProvider {

	/**
	 * TargetPosMover classes that pass C2 checklist.
	 * Rect/Polar/Bezier/MultiBezier/Spline/Formula: closed-form of tick only.
	 * Orbital/Translate: may read frozen ownerInfo snapshot; owner motion frozen (acceptable).
	 */
	private static final Set<Class<?>> WHITELISTED_TARGET_POS = Set.of(
			RectMover.class,
			PolarMover.class,
			BezierMover.class,
			MultiBezierMover.class,
			SplineMover.class,
			FormulaMover.class,
			OrbitalMover.class,
			TranslateMover.class
	);

	@Override
	public boolean supports(Entity entity) {
		if (entity == null) return false;
		// Static / mover-less lasers still need T1 segment geometry (not T3 point)
		if (entity instanceof YHBaseLaserEntity) return true;
		DanmakuMover mover = getMover(entity);
		return mover != null && isPredictable(mover);
	}

	@Override
	@Nullable
	public Threat capture(Entity entity, int horizon) {
		if (entity == null || horizon <= 0) return null;
		DanmakuMover mover = getMover(entity);
		boolean isLaser = entity instanceof YHBaseLaserEntity;

		// Laser without predictable mover: still emit segment frames from current pose
		if (isLaser && (mover == null || !isPredictable(mover))) {
			return captureStaticLaser((YHBaseLaserEntity) entity, horizon);
		}
		if (mover == null || !isPredictable(mover)) return null;

		float damage = entity instanceof ItemDanmakuEntity ide ? ide.damage
				: entity instanceof YHBaseLaserEntity le ? le.damage : 0;
		float hitRadius = entity instanceof ItemDanmakuEntity ide ? (float) (ide.getBbWidth() / 2)
				: entity instanceof YHBaseLaserEntity le ? le.getEffectiveHitRadius() : 0;
		float laserLength = isLaser ? (float) ((YHBaseLaserEntity) entity).getLength() : 0f;

		// Laser ray anchor = pos + BbHeight/2 (BaseLaser.java:31)
		Vec3 anchorPos = isLaser
				? entity.position().add(0, entity.getBbHeight() / 2, 0)
				: entity.position();

		MoverInfo.OwnerInfo ownerInfo = snapshotOwnerInfo(entity);
		MoverOwner moverOwner = entity instanceof MoverOwner mo ? mo : null;
		MoverInfo baseInfo = new MoverInfo(
				entity.tickCount, anchorPos, entity.getDeltaMovement(),
				moverOwner, ownerInfo
		);

		ThreatSemantic semantic = entity instanceof IYHDanmaku ? ThreatSemantic.DANMAKU : ThreatSemantic.VANILLA;
		int currentTick = entity.tickCount;
		ThreatFrame[] frames = new ThreatFrame[horizon];

		// Laser ray origin is always entity feet + BbHeight/2 (same offset every tick)
		double laserYOff = isLaser ? entity.getBbHeight() / 2.0 : 0;

		if (mover instanceof TargetPosMover tpm) {
			Vec3 orient0 = isLaser ? unitDirection(entity) : null;
			frames[0] = frame(anchorPos, orient0, hitRadius, laserLength, isLaser, currentTick, entity);
			for (int i = 1; i < horizon; i++) {
				MoverInfo futureInfo = baseInfo.offsetTime(i);
				// C2: direct pos() — never move() (avoids info.self().rot() fallback)
				// TargetPosMover.pos is entity-path position; lift lasers to ray anchor every frame
				Vec3 futurePos = tpm.pos(futureInfo);
				if (isLaser) {
					futurePos = futurePos.add(0, laserYOff, 0);
				}
				Vec3 orient = null;
				if (isLaser) {
					Vec3 prev = frames[i - 1].position();
					Vec3 delta = futurePos.subtract(prev);
					orient = delta.lengthSqr() > 1e-8 ? delta.normalize() : frames[i - 1].orientation();
					if (orient == null) orient = unitDirection(entity);
				}
				frames[i] = frame(futurePos, orient, hitRadius, laserLength, isLaser, currentTick + i, entity);
			}
		} else if (mover instanceof ZeroMover || mover instanceof RotateMover) {
			// Position constant; RotateMover orientation is pure function of info.tick()
			Vec3 orient0 = isLaser ? rotToDirection(mover.move(baseInfo).rot(), entity) : null;
			if (orient0 == null && isLaser) orient0 = unitDirection(entity);
			frames[0] = frame(anchorPos, orient0, hitRadius, laserLength, isLaser, currentTick, entity);
			for (int i = 1; i < horizon; i++) {
				MoverInfo futureInfo = baseInfo.offsetTime(i);
				ProjectileMovement move = mover.move(futureInfo);
				Vec3 orient = isLaser ? rotToDirection(move.rot(), entity) : null;
				frames[i] = frame(anchorPos, orient, hitRadius, laserLength, isLaser, currentTick + i, entity);
			}
		} else {
			return null;
		}

		return new Threat(entity.getId(), frames, semantic, entity, damage);
	}

	/** Laser with null/unpredictable mover: freeze pose, keep segment length + hit window. */
	private static Threat captureStaticLaser(YHBaseLaserEntity laser, int horizon) {
		Vec3 anchor = laser.position().add(0, laser.getBbHeight() / 2, 0);
		Vec3 orient = unitDirection(laser);
		float hitRadius = laser.getEffectiveHitRadius();
		float length = (float) laser.getLength();
		int t0 = laser.tickCount;
		ThreatFrame[] frames = new ThreatFrame[horizon];
		for (int i = 0; i < horizon; i++) {
			frames[i] = new ThreatFrame(anchor, orient, hitRadius, length, laser.isHitWindowOpen(t0 + i));
		}
		return new Threat(laser.getId(), frames, ThreatSemantic.DANMAKU, laser, laser.damage);
	}

	private static ThreatFrame frame(Vec3 pos, @Nullable Vec3 orient, float hitRadius, float laserLength,
	                                 boolean isLaser, int absTick, Entity entity) {
		boolean active = !isLaser || isLaserActive(entity, absTick);
		float len = isLaser ? laserLength : 0f;
		return new ThreatFrame(pos, orient, hitRadius, len, active);
	}

	private static boolean isLaserActive(Entity entity, int absTick) {
		if (entity instanceof YHBaseLaserEntity laser) {
			return laser.isHitWindowOpen(absTick);
		}
		return true;
	}

	@Nullable
	private static DanmakuMover getMover(Entity entity) {
		if (entity instanceof ItemDanmakuEntity ide && ide.mover != null) {
			return ide.mover;
		}
		if (entity instanceof ItemLaserEntity ile && ile.mover != null) {
			return ile.mover;
		}
		return null;
	}

	private static boolean isPredictable(DanmakuMover mover) {
		if (mover instanceof TargetPosMover) {
			return WHITELISTED_TARGET_POS.contains(mover.getClass());
		}
		// ZeroMover / RotateMover: no field writes, no self read, tick-pure
		return mover instanceof ZeroMover || mover instanceof RotateMover;
	}

	private static Vec3 unitDirection(Entity entity) {
		if (entity instanceof SimplifiedProjectile sp) {
			return rotToDirection(sp.rot(), entity);
		}
		return entity.getLookAngle();
	}

	/** Convert pitch/yaw rot vector (ProjectileMovement.rot) to unit look direction. */
	private static Vec3 rotToDirection(Vec3 rot, Entity fallback) {
		if (rot == null || rot.equals(Vec3.ZERO)) {
			return fallback.getLookAngle();
		}
		return Vec3.directionFromRotation(
				(float) (rot.x * Mth.RAD_TO_DEG),
				(float) (rot.y * Mth.RAD_TO_DEG)
		);
	}

	private static MoverInfo.OwnerInfo snapshotOwnerInfo(Entity entity) {
		Entity owner = null;
		if (entity instanceof SimplifiedProjectile sp) {
			owner = sp.getOwner();
		}
		if (owner instanceof CardHolder holder) {
			return new MoverInfo.OwnerInfo(holder.center(), holder.forward());
		}
		if (owner instanceof LivingEntity living) {
			Vec3 pos = living.position().add(0, living.getBbHeight() / 2, 0);
			return new MoverInfo.OwnerInfo(pos, living.getForward());
		}
		return new MoverInfo.OwnerInfo(null, null);
	}
}
